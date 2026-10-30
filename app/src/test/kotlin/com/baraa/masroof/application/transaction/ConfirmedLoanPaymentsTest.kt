package com.baraa.masroof.application.transaction

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.application.AppContainer
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.*
import com.baraa.masroof.domain.rules.FinancialImpactCalculator
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.sms.hash.SmsBodyHasher
import com.baraa.masroof.testsupport.TestStorageContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConfirmedLoanPaymentsTest {
    @Test fun confirmedBillerKeepsFundingAndBillsSeparate() = runBlocking {
        val context = TestStorageContext(ApplicationProvider.getApplicationContext<Context>(), "loan")
        val c = AppContainer(context)
        try {
            val sourceBank = Bank("BANK_SNB")
            val otherBank = Bank("BANK_SAB")
            val loan = LoanReference(Bank("BANK_EMIRATES_NBD"), LoanType.PERSONAL)
            val cash = AccountReference(otherBank, "6789")
            val firstCard = CardReference(sourceBank, "4321")
            val secondCard = CardReference(otherBank, "7654")
            for (card in listOf(firstCard, secondCard)) {
                c.ownershipConfirmationService.confirmCardOwned(card)
                c.cardRegistryRepository.configure(card, CardConfiguration(null, CardType.CREDIT, CardRole.STANDALONE))
            }
            c.ownershipConfirmationService.confirmAccountOwned(cash)
            c.ownershipConfirmationService.confirmLoanOwned(loan)

            suspend fun seed(id: String, code: String, card: CardReference? = firstCard,
                account: AccountReference? = null, amount: String = "37.50"): ParsedEvent {
                val bank = card?.bank ?: account!!.bank
                val body = "SYNTHETIC TEST $id"
                c.rawSmsRepository.insertIfAbsent(RawSms(id, "TEST SENDER", body, Instant.parse("2026-10-13T10:30:00Z"), id, SmsBodyHasher.sha256Hex(body)))
                val event = ParsedEvent("event-$id", id, bank, MessageFamily.BILL_PAYMENT, MoneyDirection.OUTGOING,
                    Money.of(amount, Currency.SAR), null, account, null, card, null, null,
                    Instant.parse("2026-10-13T10:30:00Z"), null, Confidence(0.99), ParseStatus.SUCCESS)
                c.parsedEventRepository.save(event, ParsedEventDetails(biller = code))
                val report = c.transactionReconciliationService.reconcileAffectedRawSmsIds(listOf(id))
                assertEquals(0, report.summary.failed)
                return event
            }

            val original = seed("existing", "999")
            seed("telecom", "001")
            seed("electricity", "002")
            val before = c.financialTransactionRepository.findByRawSmsId("existing")!!
            assertEquals(FinancialTransactionType.BILL_PAYMENT, before.type)
            val workflow = c.confirmedLoanPaymentsWorkflow
            val preview = workflow.preview(loan, "999")
            assertEquals(1, preview.transactionCount)
            assertEquals(listOf(Money.of("37.50", Currency.SAR)), preview.totals)
            workflow.confirm(preview)
            val after = c.financialTransactionRepository.findByRawSmsId("existing")!!
            assertEquals(before.copy(type = FinancialTransactionType.LOAN_REPAYMENT,
                destinationContainerId = FinancialContainerIdFactory.loanId(loan.bank, loan.loanType)), after)
            assertEquals(original, c.parsedEventRepository.findByRawSmsId("existing")!!.event)
            assertFalse(FinancialImpactCalculator.forType(after.type).countsAsExpense)

            val bankSms = "سداد فاتورة\nبطاقة ائتمانية **4321\nمبلغ 13.45 SAR\nمفوتر 999\nفي 13/10/26 12:30\nالرصيد المتبقي 876.55 SAR"
            c.processRawSmsUseCase.ingest(RawSms("parsed-loan-payment", "SNB-AlAhli", bankSms,
                Instant.parse("2026-10-13T10:30:00Z"), "parsed-loan-payment", SmsBodyHasher.sha256Hex(bankSms)))
            assertEquals(FinancialTransactionType.LOAN_REPAYMENT,
                c.financialTransactionRepository.findByRawSmsId("parsed-loan-payment")?.type)

            seed("different-card", "999", secondCard, amount = "48.75")
            seed("from-account", "999", card = null, account = cash, amount = "59.25")
            for (id in listOf("different-card", "from-account")) {
                val tx = c.financialTransactionRepository.findByRawSmsId(id)!!
                assertEquals(FinancialTransactionType.LOAN_REPAYMENT, tx.type)
                assertEquals(FinancialContainerIdFactory.loanId(loan.bank, loan.loanType), tx.destinationContainerId)
            }
            assertEquals(FinancialContainerIdFactory.cardId(secondCard), c.financialTransactionRepository.findByRawSmsId("different-card")!!.sourceContainerId)
            assertEquals(FinancialContainerIdFactory.accountId(cash), c.financialTransactionRepository.findByRawSmsId("from-account")!!.sourceContainerId)
            for (id in listOf("telecom", "electricity")) {
                val tx = c.financialTransactionRepository.findByRawSmsId(id)!!
                assertEquals(FinancialTransactionType.BILL_PAYMENT, tx.type)
                assertTrue(FinancialImpactCalculator.forType(tx.type).countsAsExpense)
            }
            val unknown = CardReference(otherBank, "9999")
            seed("unknown-funding", "999", unknown)
            assertNull(c.financialTransactionRepository.findByRawSmsId("unknown-funding"))
            assertTrue(c.processingRetryRepository.listRetryableRawSmsIds().isEmpty())

            // Simulate interruption after durable confirmation but before the database commit.
            c.financialTransactionRepository.update(before)
            workflow.recoverConfirmedHistory()
            assertEquals(after, c.financialTransactionRepository.findByRawSmsId("existing"))
            workflow.recoverConfirmedHistory()
            assertEquals(6, c.financialTransactionRepository.listAll().size)
            val configuration = c.messagePatternsWorkflow.load()
            assertEquals(configuration, MessagePatternConfigurationCodec.decode(MessagePatternConfigurationCodec.encode(configuration)))
            assertEquals(setOf(before.id), configuration.confirmedLoanPayments.single().confirmedTransactionIds)
            assertEquals("999", configuration.confirmedLoanPayments.single().billerCode)
        } finally { c.close() }
    }
}
