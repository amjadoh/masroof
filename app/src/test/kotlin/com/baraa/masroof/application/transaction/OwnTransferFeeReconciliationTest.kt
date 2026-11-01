package com.baraa.masroof.application.transaction

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.application.patterns.OwnTransferReadingTest
import com.baraa.masroof.bank.InterpretationDraftMapper
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomCardRegistryRepository
import com.baraa.masroof.data.repository.RoomFinancialTransactionRepository
import com.baraa.masroof.data.repository.RoomParsedEventRepository
import com.baraa.masroof.data.repository.RoomRawSmsRepository
import com.baraa.masroof.data.repository.RoomReviewRepository
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.assembly.TransactionAssembler
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.ownership.OwnershipResolver
import com.baraa.masroof.domain.repository.NoOpLoanRegistryRepository
import com.baraa.masroof.engine.bank.MessageInput
import com.baraa.masroof.engine.bank.patterns.PatternFactInterpreter
import com.baraa.masroof.finance.value.BankId
import com.baraa.masroof.parsing.model.ParseResult
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.sms.hash.SmsBodyHasher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Fictional own-account transfers between two banks. Each becomes one SELF_TRANSFER
 * of the received amount; the fee the sender paid is a separate FEE row on its account.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OwnTransferFeeReconciliationTest {
    private lateinit var db: MasroofDatabase
    private lateinit var rawRepo: RoomRawSmsRepository
    private lateinit var parsedRepo: RoomParsedEventRepository
    private lateinit var ftRepo: RoomFinancialTransactionRepository
    private lateinit var reconciliation: TransactionReconciliationService
    private val zoneId = ZoneId.of("Asia/Riyadh")
    private val stcAccount = "account:BANK_STC:6123"
    private val snbAccount = "account:BANK_SNB:4321"

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MasroofDatabase::class.java).allowMainThreadQueries().build()
        rawRepo = RoomRawSmsRepository(db.rawSmsDao())
        parsedRepo = RoomParsedEventRepository(db.parsedEventDao())
        ftRepo = RoomFinancialTransactionRepository(db.financialTransactionDao(), db.parsedEventDao())
        val accounts = RoomAccountRegistryRepository.from(db)
        val cards = RoomCardRegistryRepository.from(db)
        val confirmation = OwnershipConfirmationService(accounts, cards, NoOpLoanRegistryRepository)
        confirmation.confirmAccountOwned(AccountReference(STC, "6123"))
        confirmation.confirmAccountOwned(AccountReference(SNB, "4321"))
        reconciliation = TransactionReconciliationService(
            parsedEventRepository = parsedRepo,
            rawSmsRepository = rawRepo,
            financialTransactionRepository = ftRepo,
            ownershipResolver = OwnershipResolver(accounts, cards, NoOpLoanRegistryRepository),
            reviewRepository = RoomReviewRepository(db.reviewItemDao()),
            zoneId = zoneId,
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun stcToSnbHealsThePostedIncomingLegAndRecordsTheStatedFee() = runBlocking {
        snbLeg("snb-in", MessageFamily.TRANSFER_IN, "150.00", null, AccountReference(SNB, "4321"),
            LocalDateTime.parse("2026-05-15T09:35:00"), Instant.parse("2026-05-15T06:35:06Z"))
        reconciliation.reconcileStoredEvents()
        assertEquals(listOf(FinancialTransactionType.EXTERNAL_TRANSFER_IN), ftRepo.listAll().map { it.type })

        stcLeg("stc-out", "حوالة صادرة (سريع)\n150.00 ر.س\nإلى فلان الاختبار\nإلى البنك الأهلي السعودي \nحساب *123\nرسوم 0.58 ر.س\n15/05/26 09:32\nمرجع *AB12",
            Instant.parse("2026-05-15T06:33:00Z"))
        assertEquals(0, reconciliation.reconcileStoredEvents().failed)

        val transfer = assertSelfTransferWithFee(stcAccount, snbAccount, "150.00", "0.58", setOf("snb-in", "stc-out"))
        // Repeated maintenance keeps the same two rows.
        assertEquals(0, reconciliation.repairLegacyTransfersDetailed().summary.failed)
        assertEquals(0, reconciliation.reconcileStoredEvents().failed)
        assertEquals(setOf(transfer.id, TransactionAssembler.feeCompanionId(transfer.id)), ftRepo.listAll().map { it.id }.toSet())

        // Removing the transfer evidence removes its fee with it.
        ftRepo.unlinkRawSms("snb-in")
        ftRepo.unlinkRawSms("stc-out")
        assertTrue(ftRepo.listAll().isEmpty())
    }

    @Test
    fun snbToStcPairsAnIncludedFeeAndIgnoresThePlaceholderDate() = runBlocking {
        snbLeg("snb-out", MessageFamily.TRANSFER_OUT, "2500.50", AccountReference(SNB, "4321"), AccountReference(Bank.UNKNOWN, "6123"),
            LocalDateTime.parse("2026-06-23T14:38:00"), Instant.parse("2026-06-23T11:38:38Z"))
        reconciliation.reconcileStoredEvents()
        assertEquals(listOf(FinancialTransactionType.EXTERNAL_TRANSFER_OUT), ftRepo.listAll().map { it.type })

        stcLeg("stc-in", "حوالة واردة (سريع)\n2500.00 رس\nمن فلان الاختبار\nمن بنك NCBK\nحساب *123\n01-01-1970 03:00\nمرجع *GH78",
            Instant.parse("2026-06-23T11:39:00Z"))
        assertEquals(0, reconciliation.reconcileStoredEvents().failed)

        val transfer = assertSelfTransferWithFee(snbAccount, stcAccount, "2500.00", "0.50", setOf("snb-out", "stc-in"))
        assertEquals(2026, transfer.occurredAt.atZone(zoneId).year)
        assertEquals(0, reconciliation.repairLegacyTransfersDetailed().summary.failed)
        assertEquals(2, ftRepo.listAll().size)
    }

    @Test
    fun aDifferenceAboveATransferFeeIsNotOneTransfer() = runBlocking {
        snbLeg("snb-out", MessageFamily.TRANSFER_OUT, "2510.00", AccountReference(SNB, "4321"), AccountReference(Bank.UNKNOWN, "6123"),
            LocalDateTime.parse("2026-06-23T14:38:00"), Instant.parse("2026-06-23T11:38:38Z"))
        stcLeg("stc-in", "حوالة واردة (سريع)\n2500.00 رس\nمن فلان الاختبار\nمن بنك NCBK\nحساب *123\n23-06-2026 14:39\nمرجع *GH78",
            Instant.parse("2026-06-23T11:39:00Z"))
        reconciliation.reconcileStoredEvents()
        assertEquals(setOf(FinancialTransactionType.EXTERNAL_TRANSFER_OUT, FinancialTransactionType.EXTERNAL_TRANSFER_IN),
            ftRepo.listAll().map { it.type }.toSet())
        assertTrue(ftRepo.listAll().none { it.type == FinancialTransactionType.FEE })
    }

    @Test
    fun aNamedBankWithAnotherAmountDoesNotPair() = runBlocking {
        snbLeg("snb-in", MessageFamily.TRANSFER_IN, "149.42", null, AccountReference(SNB, "4321"),
            LocalDateTime.parse("2026-05-15T09:35:00"), Instant.parse("2026-05-15T06:35:06Z"))
        stcLeg("stc-out", "حوالة صادرة (سريع)\n150.00 ر.س\nإلى فلان الاختبار\nإلى البنك الأهلي السعودي \nحساب *123\nرسوم 0.58 ر.س\n15/05/26 09:32\nمرجع *AB12",
            Instant.parse("2026-05-15T06:33:00Z"))
        reconciliation.reconcileStoredEvents()
        assertTrue(ftRepo.listAll().none { it.type == FinancialTransactionType.SELF_TRANSFER })
        // An unmatched outgoing transfer still records the fee its bank stated.
        val outgoing = ftRepo.findByRawSmsId("stc-out")!!
        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_OUT, outgoing.type)
        val fee = ftRepo.getById(TransactionAssembler.feeCompanionId(outgoing.id))!!
        assertEquals(Money.of("0.58", Currency.SAR), fee.amount)
        assertEquals(stcAccount, fee.sourceContainerId)
    }

    private suspend fun assertSelfTransferWithFee(source: String, destination: String, amount: String, fee: String,
        rawSmsIds: Set<String>): FinancialTransaction {
        val rows = ftRepo.listAll()
        assertEquals(rows.toString(), 2, rows.size)
        val transfer = rows.single { it.type == FinancialTransactionType.SELF_TRANSFER }
        assertEquals(Money.of(amount, Currency.SAR), transfer.amount)
        assertEquals(source, transfer.sourceContainerId)
        assertEquals(destination, transfer.destinationContainerId)
        assertEquals(rawSmsIds, ftRepo.listRawSmsIds(transfer.id).toSet())
        val feeRow = rows.single { it.type == FinancialTransactionType.FEE }
        assertEquals(TransactionAssembler.feeCompanionId(transfer.id), feeRow.id)
        assertEquals(Money.of(fee, Currency.SAR), feeRow.amount)
        assertEquals(source, feeRow.sourceContainerId)
        assertEquals(null, feeRow.destinationContainerId)
        assertEquals(transfer.occurredAt, feeRow.occurredAt)
        assertTrue(ftRepo.listRawSmsIds(feeRow.id).isEmpty())
        return transfer
    }

    private suspend fun stcLeg(id: String, body: String, receivedAt: Instant) {
        val input = MessageInput(id, "STC Bank", body, receivedAt, durableSmsReceipt = true)
        val interpretation = PatternFactInterpreter(OwnTransferReadingTest.configured()).interpretRouted(input, BankId(STC.id))
        val result = InterpretationDraftMapper().map(input, interpretation, STC) as ParseResult.Success
        assertEquals(ParseStatus.SUCCESS, result.event.parseStatus)
        insertRaw(id, "STC Bank", body, receivedAt)
        parsedRepo.save(result.event, result.details)
    }

    /** Stored facts of a leg the receiving bank already posted; it is not reparsed here. */
    private suspend fun snbLeg(id: String, family: MessageFamily, amount: String, source: AccountReference?,
        destination: AccountReference?, local: LocalDateTime, receivedAt: Instant) {
        insertRaw(id, "SNB-AlAhli", "fictional $id", receivedAt)
        parsedRepo.save(
            ParsedEvent(
                id = "evt-$id", rawSmsId = id, bank = SNB, messageFamily = family,
                direction = if (family == MessageFamily.TRANSFER_IN) MoneyDirection.INCOMING else MoneyDirection.OUTGOING,
                amount = Money.of(amount, Currency.SAR), purchaseChannel = null,
                sourceAccountRef = source, destinationAccountRef = destination, cardRef = null,
                merchant = null, counterparty = null, occurredAt = null, bankNetworkType = BankNetworkType.INTER_BANK,
                confidence = Confidence(1.0, emptyList()), parseStatus = ParseStatus.SUCCESS,
            ),
            ParsedEventDetails(occurredAtLocal = local),
        )
    }

    private suspend fun insertRaw(id: String, sender: String, body: String, receivedAt: Instant) {
        rawRepo.insertIfAbsent(RawSms(id, sender, body, receivedAt, id, SmsBodyHasher.sha256Hex(body)))
    }

    private companion object {
        val STC = Bank("BANK_STC")
        val SNB = Bank("BANK_SNB")
    }
}
