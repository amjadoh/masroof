package com.baraa.masroof.application.transaction

import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.ids.TransactionIdFactory
import com.baraa.masroof.domain.matching.TransactionMatcher
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ReviewKind
import com.baraa.masroof.parsing.model.ParsedEventDetails
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReconciliationTransferHealingTest : ReconciliationTestFixture() {
    @Test
    fun duplicateOwnedToOwnedSmsLegs_absorbIntoSingleSelfTransfer() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3003"))
        persistEvent(
            smsId = "sms-out",
            event = event(
                id = "pe-out",
                rawSmsId = "sms-out",
                family = MessageFamily.TRANSFER_OUT,
                amount = money("4445.67"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3003"),
                network = BankNetworkType.INTRA_BANK,
                counterparty = "براء بخش",
            ),
        )
        persistEvent(
            smsId = "sms-in",
            event = event(
                id = "pe-in",
                rawSmsId = "sms-in",
                family = MessageFamily.TRANSFER_IN,
                amount = money("4445.67"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3003"),
                network = BankNetworkType.INTRA_BANK,
            ),
        )
        reconciliation.reconcileStoredEvents()
        assertEquals(1, ftRepo.listAll().size)
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.SELF_TRANSFER, tx.type)
        assertEquals(
            TransactionIdFactory.fromRawSmsIds(listOf("sms-in", "sms-out")),
            tx.id,
        )
        assertEquals(setOf("sms-in", "sms-out"), ftRepo.listRawSmsIds(tx.id).toSet())
    }

    @Test
    fun identicalSelfTransferOutLegs_staySeparateWithoutAMatcherPair() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3003"))
        val at = Instant.parse("2026-08-10T09:00:00Z")
        val amount = money("4445.67")
        persistEvent(
            smsId = "sms-out-a",
            at = at,
            event = event(
                id = "pe-out-a",
                rawSmsId = "sms-out-a",
                family = MessageFamily.TRANSFER_OUT,
                amount = amount,
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3003"),
                network = BankNetworkType.INTRA_BANK,
            ),
        )
        persistEvent(
            smsId = "sms-out-b",
            at = at,
            event = event(
                id = "pe-out-b",
                rawSmsId = "sms-out-b",
                family = MessageFamily.TRANSFER_OUT,
                amount = amount,
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3003"),
                network = BankNetworkType.INTRA_BANK,
            ),
        )

        reconciliation.reconcileStoredEvents()

        val posted = ftRepo.listAll()
        assertEquals(2, posted.size)
        assertTrue(posted.all { it.type == FinancialTransactionType.SELF_TRANSFER })
        assertEquals(amount, posted.first().amount)
        assertEquals(posted.first().sourceContainerId, posted.last().sourceContainerId)
        assertEquals(posted.first().destinationContainerId, posted.last().destinationContainerId)
        assertEquals(posted.first().occurredAt, posted.last().occurredAt)
        assertEquals(
            setOf(
                TransactionIdFactory.fromRawSmsIds(listOf("sms-out-a")),
                TransactionIdFactory.fromRawSmsIds(listOf("sms-out-b")),
            ),
            posted.map { it.id }.toSet(),
        )
        val firstId = posted.single { it.linkedParsedEventIds == listOf("pe-out-a") }.id
        val secondId = posted.single { it.linkedParsedEventIds == listOf("pe-out-b") }.id
        assertEquals(setOf("sms-out-a"), ftRepo.listRawSmsIds(firstId).toSet())
        assertEquals(setOf("sms-out-b"), ftRepo.listRawSmsIds(secondId).toSet())
    }

    @Test
    fun laterOppositeLeg_healsSingleEvidenceSelfTransferWhenMatcherAccepts() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3003"))
        val at = Instant.parse("2026-08-10T09:00:00Z")
        val amount = money("4445.67")
        val source = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        val destination = AccountReference(Bank.BANK_ALJAZIRA, "3003")
        persistEvent(
            smsId = "sms-heal-out",
            at = at,
            event = event(
                id = "pe-heal-out",
                rawSmsId = "sms-heal-out",
                family = MessageFamily.TRANSFER_OUT,
                amount = amount,
                source = source,
                destination = destination,
                network = BankNetworkType.INTRA_BANK,
            ),
        )
        reconciliation.reconcileStoredEvents()

        val posted = ftRepo.listAll().single()
        val singleLegId = TransactionIdFactory.fromRawSmsIds(listOf("sms-heal-out"))
        assertEquals(FinancialTransactionType.SELF_TRANSFER, posted.type)
        assertEquals(singleLegId, posted.id)
        assertEquals(listOf("pe-heal-out"), posted.linkedParsedEventIds)
        assertEquals(setOf("sms-heal-out"), ftRepo.listRawSmsIds(posted.id).toSet())

        persistEvent(
            smsId = "sms-heal-in",
            at = at.plusSeconds(60),
            event = event(
                id = "pe-heal-in",
                rawSmsId = "sms-heal-in",
                family = MessageFamily.TRANSFER_IN,
                amount = amount,
                source = source,
                destination = destination,
                network = BankNetworkType.INTRA_BANK,
            ),
        )
        reconciliation.reconcileStoredEvents()

        val healed = ftRepo.listAll().single()
        val pairId = TransactionIdFactory.fromRawSmsIds(listOf("sms-heal-in", "sms-heal-out"))
        assertEquals(FinancialTransactionType.SELF_TRANSFER, healed.type)
        assertEquals(pairId, healed.id)
        assertNotEquals(singleLegId, healed.id)
        assertEquals(setOf("sms-heal-in", "sms-heal-out"), ftRepo.listRawSmsIds(healed.id).toSet())
        assertEquals(listOf("pe-heal-in", "pe-heal-out"), healed.linkedParsedEventIds.sorted())
        assertEquals(healed.id, ftRepo.findByRawSmsId("sms-heal-out")?.id)
        assertEquals(healed.id, ftRepo.findByRawSmsId("sms-heal-in")?.id)
    }

    @Test
    fun similarAmountAccountTime_doesNotHealSingleEvidenceSelfTransfer() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3003"))
        val at = Instant.parse("2026-08-10T09:00:00Z")
        val amount = money("4445.67")
        val source = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        val destination = AccountReference(Bank.BANK_ALJAZIRA, "3003")
        persistEvent(
            smsId = "sms-tuple-out",
            at = at,
            event = event(
                id = "pe-tuple-out",
                rawSmsId = "sms-tuple-out",
                family = MessageFamily.TRANSFER_OUT,
                amount = amount,
                source = source,
                destination = destination,
                network = BankNetworkType.INTRA_BANK,
            ),
        )
        reconciliation.reconcileStoredEvents()
        val singleLegId = TransactionIdFactory.fromRawSmsIds(listOf("sms-tuple-out"))
        assertEquals(singleLegId, ftRepo.listAll().single().id)

        persistEvent(
            smsId = "sms-tuple-in",
            at = at,
            event = event(
                id = "pe-tuple-in",
                rawSmsId = "sms-tuple-in",
                family = MessageFamily.TRANSFER_IN,
                amount = amount,
                source = source,
                destination = destination,
                network = BankNetworkType.INTER_BANK,
            ),
        )
        reconciliation.reconcileStoredEvents()

        val posted = ftRepo.listAll()
        assertEquals(2, posted.size)
        assertTrue(posted.all { it.type == FinancialTransactionType.SELF_TRANSFER })
        assertTrue(posted.all { it.amount == amount })
        assertEquals(1, posted.map { it.sourceContainerId }.toSet().size)
        assertEquals(1, posted.map { it.destinationContainerId }.toSet().size)
        assertEquals(1, posted.map { it.occurredAt }.toSet().size)
        val original = posted.single { it.id == singleLegId }
        assertEquals(listOf("pe-tuple-out"), original.linkedParsedEventIds)
        assertEquals(setOf("sms-tuple-out"), ftRepo.listRawSmsIds(original.id).toSet())
        val incomingId = TransactionIdFactory.fromRawSmsIds(listOf("sms-tuple-in"))
        val incoming = posted.single { it.id == incomingId }
        assertEquals(listOf("pe-tuple-in"), incoming.linkedParsedEventIds)
        assertEquals(setOf("sms-tuple-in"), ftRepo.listRawSmsIds(incoming.id).toSet())
        val joinedId = TransactionIdFactory.fromRawSmsIds(listOf("sms-tuple-in", "sms-tuple-out"))
        assertNull(posted.find { it.id == joinedId })
    }

    @Test
    fun ambiguousOppositeLegs_doNotHealSingleEvidenceSelfTransfer() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3003"))
        val at = Instant.parse("2026-08-10T09:00:00Z")
        val amount = money("4445.67")
        val source = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        val destination = AccountReference(Bank.BANK_ALJAZIRA, "3003")
        persistEvent(
            smsId = "sms-amb-self-out",
            at = at,
            event = event(
                id = "pe-amb-self-out",
                rawSmsId = "sms-amb-self-out",
                family = MessageFamily.TRANSFER_OUT,
                amount = amount,
                source = source,
                destination = destination,
                network = BankNetworkType.INTRA_BANK,
            ),
        )
        reconciliation.reconcileStoredEvents()
        val singleLegId = TransactionIdFactory.fromRawSmsIds(listOf("sms-amb-self-out"))
        assertEquals(singleLegId, ftRepo.listAll().single().id)

        persistEvent(
            smsId = "sms-amb-self-in-a",
            at = at.plusSeconds(60),
            event = event(
                id = "pe-amb-self-in-a",
                rawSmsId = "sms-amb-self-in-a",
                family = MessageFamily.TRANSFER_IN,
                amount = amount,
                source = source,
                destination = destination,
                network = BankNetworkType.INTRA_BANK,
            ),
        )
        persistEvent(
            smsId = "sms-amb-self-in-b",
            at = at.plusSeconds(90),
            event = event(
                id = "pe-amb-self-in-b",
                rawSmsId = "sms-amb-self-in-b",
                family = MessageFamily.TRANSFER_IN,
                amount = amount,
                source = source,
                destination = destination,
                network = BankNetworkType.INTRA_BANK,
            ),
        )
        val report = reconciliation.reconcileStoredEventsDetailed()

        val original = ftRepo.findByRawSmsId("sms-amb-self-out")
        assertEquals(singleLegId, original?.id)
        assertEquals(FinancialTransactionType.SELF_TRANSFER, original?.type)
        assertEquals(setOf("sms-amb-self-out"), ftRepo.listRawSmsIds(singleLegId).toSet())
        assertNull(ftRepo.findByRawSmsId("sms-amb-self-in-a"))
        assertNull(ftRepo.findByRawSmsId("sms-amb-self-in-b"))
        assertEquals(
            setOf("sms-amb-self-in-a", "sms-amb-self-in-b"),
            report.reviewCandidates.map { it.rawSmsId }.toSet(),
        )
        assertTrue(report.reviewCandidates.all { it.kind == ReviewKind.PENDING_MATCH })
        assertTrue(report.reviewCandidates.all { it.reasons == listOf("transfer_pending_match") })
        assertNull(
            ftRepo.listAll().find {
                it.id == TransactionIdFactory.fromRawSmsIds(
                    listOf("sms-amb-self-in-a", "sms-amb-self-out"),
                )
            },
        )
    }

    @Test
    fun reconcileAffectedRawSmsIds_healsStaleExternalPairOutsideReceivedWindow() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        val occurredAt = LocalDateTime.parse("2026-08-01T12:00:00")
        val outAt = Instant.parse("2026-08-01T12:00:00Z")
        val inAt = outAt.plus(TransactionMatcher.TRANSFER_MATCH_WINDOW.multipliedBy(3))
        persistEvent(
            smsId = "sms-out-stale",
            at = outAt,
            details = ParsedEventDetails(occurredAtLocal = occurredAt),
            event = event(
                id = "pe-out-stale",
                rawSmsId = "sms-out-stale",
                family = MessageFamily.TRANSFER_OUT,
                amount = money("4445.67"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3003"),
                network = BankNetworkType.INTRA_BANK,
                counterparty = "براء بخش",
            ),
        )
        reconciliation.reconcileAffectedRawSmsIds(listOf("sms-out-stale"))
        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_OUT, ftRepo.listAll().single().type)

        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3003"))
        persistEvent(
            smsId = "sms-in-stale",
            at = inAt,
            details = ParsedEventDetails(occurredAtLocal = occurredAt.plusMinutes(2)),
            event = event(
                id = "pe-in-stale",
                rawSmsId = "sms-in-stale",
                family = MessageFamily.TRANSFER_IN,
                amount = money("4445.67"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3003"),
                network = BankNetworkType.INTRA_BANK,
            ),
        )
        reconciliation.reconcileAffectedRawSmsIds(listOf("sms-in-stale"))

        assertEquals(1, ftRepo.listAll().size)
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.SELF_TRANSFER, tx.type)
        assertEquals(setOf("sms-in-stale", "sms-out-stale"), ftRepo.listRawSmsIds(tx.id).toSet())
    }

    @Test
    fun laterReferenceCounterpart_upgradesExternalWithoutDuplicating() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        val occurredAt = LocalDateTime.parse("2026-08-10T12:00:00")
        persistEvent(
            smsId = "sms-ref-out",
            at = Instant.parse("2026-08-10T09:00:00Z"),
            details = ParsedEventDetails(occurredAtLocal = occurredAt, transactionReference = "REF-HEAL"),
            event = event(
                id = "pe-ref-out",
                rawSmsId = "sms-ref-out",
                family = MessageFamily.TRANSFER_OUT,
                amount = money("700.00"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3003"),
                network = BankNetworkType.INTER_BANK,
            ),
        )
        reconciliation.reconcileAffectedRawSmsIds(listOf("sms-ref-out"))
        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_OUT, ftRepo.listAll().single().type)

        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3003"))
        persistEvent(
            smsId = "sms-ref-in",
            at = Instant.parse("2026-08-10T09:02:00Z"),
            details = ParsedEventDetails(occurredAtLocal = occurredAt.plusMinutes(1), transactionReference = "REF-HEAL"),
            event = event(
                id = "pe-ref-in",
                rawSmsId = "sms-ref-in",
                family = MessageFamily.TRANSFER_IN,
                amount = money("700.00"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3003"),
                network = BankNetworkType.INTER_BANK,
            ),
        )
        reconciliation.reconcileAffectedRawSmsIds(listOf("sms-ref-in"))

        assertEquals(1, ftRepo.listAll().size)
        val healed = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.SELF_TRANSFER, healed.type)
        assertEquals(setOf("sms-ref-out", "sms-ref-in"), ftRepo.listRawSmsIds(healed.id).toSet())
        assertEquals(listOf("pe-ref-in", "pe-ref-out"), healed.linkedParsedEventIds.sorted())
    }

    @Test
    fun ambiguousReferenceCounterparts_areNotHealed() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3003"))
        val occurredAt = LocalDateTime.parse("2026-08-10T12:00:00")
        persistEvent(
            smsId = "sms-amb-out",
            at = Instant.parse("2026-08-10T09:00:00Z"),
            details = ParsedEventDetails(occurredAtLocal = occurredAt, transactionReference = "REF-AMB"),
            event = event(
                id = "pe-amb-out",
                rawSmsId = "sms-amb-out",
                family = MessageFamily.TRANSFER_OUT,
                amount = money("700.00"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                network = BankNetworkType.INTER_BANK,
            ),
        )
        reconciliation.reconcileStoredEvents()
        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_OUT, ftRepo.listAll().single().type)

        persistEvent(
            smsId = "sms-amb-in-a",
            at = Instant.parse("2026-08-10T09:01:00Z"),
            details = ParsedEventDetails(occurredAtLocal = occurredAt, transactionReference = "REF-AMB"),
            event = event(
                id = "pe-amb-in-a",
                rawSmsId = "sms-amb-in-a",
                family = MessageFamily.TRANSFER_IN,
                amount = money("700.00"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3003"),
                network = BankNetworkType.INTER_BANK,
            ),
        )
        persistEvent(
            smsId = "sms-amb-in-b",
            at = Instant.parse("2026-08-10T09:01:30Z"),
            details = ParsedEventDetails(occurredAtLocal = occurredAt.plusMinutes(1), transactionReference = "REF-AMB"),
            event = event(
                id = "pe-amb-in-b",
                rawSmsId = "sms-amb-in-b",
                family = MessageFamily.TRANSFER_IN,
                amount = money("700.00"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3003"),
                network = BankNetworkType.INTER_BANK,
            ),
        )
        reconciliation.reconcileStoredEvents()

        assertTrue(ftRepo.listAll().none { it.type == FinancialTransactionType.SELF_TRANSFER })
        val outgoing = ftRepo.findByRawSmsId("sms-amb-out")
        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_OUT, outgoing?.type)
        assertEquals(listOf("sms-amb-out"), outgoing?.let { ftRepo.listRawSmsIds(it.id) })
    }

    @Test
    fun outgoingUnknownWithoutCounterpart_postsExternalOut() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        persistEvent(
            smsId = "sms-unk",
            event = event(
                id = "pe-unk",
                rawSmsId = "sms-unk",
                family = MessageFamily.TRANSFER_OUT,
                amount = money("500.00"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                destination = AccountReference(Bank.UNKNOWN, "6810"),
                network = BankNetworkType.INTER_BANK,
                counterparty = "TEST_BENEFICIARY",
            ),
        )
        val summary = reconciliation.reconcileStoredEvents()
        assertEquals(1, ftRepo.listAll().size)
        assertEquals(0, summary.pendingMatch)
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_OUT, tx.type)
        assertNotEquals(FinancialTransactionType.EXPENSE, tx.type)
        assertEquals(FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"), tx.sourceContainerId)
        assertNull(tx.destinationContainerId)
        assertEquals("TEST_BENEFICIARY", tx.counterparty)
    }

    @Test
    fun incomingUnknownWithoutCounterpart_postsExternalIn() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        persistEvent(
            smsId = "sms-in-unk",
            event = event(
                id = "pe-in-unk",
                rawSmsId = "sms-in-unk",
                family = MessageFamily.TRANSFER_IN,
                amount = money("200.00"),
                source = AccountReference(Bank.UNKNOWN, "9999"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                network = BankNetworkType.INTER_BANK,
                counterparty = "TEST_COMPANY",
            ),
        )
        val summary = reconciliation.reconcileStoredEvents()
        assertEquals(0, summary.pendingMatch)
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_IN, tx.type)
        assertNotEquals(FinancialTransactionType.INCOME, tx.type)
        assertNull(tx.sourceContainerId)
        assertEquals(FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"), tx.destinationContainerId)
        assertEquals("TEST_COMPANY", tx.counterparty)
    }

    @Test
    fun batchPass_pairsReferenceBridgedLegs_likeOrderIndependentStoredPass() = runBlocking {
        val t = LocalDateTime.parse("2026-08-10T12:00:00")
        val own3001 = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        val own3003 = AccountReference(Bank.BANK_ALJAZIRA, "3003")

        suspend fun seed() {
            db.clearAllTables()
            confirmation.confirmAccountOwned(own3001)
            confirmation.confirmAccountOwned(own3003)
            persistEvent(
                smsId = "sms-r-out",
                event = event("pe-r-out", "sms-r-out", MessageFamily.TRANSFER_OUT, money("700.00"), source = own3001),
                details = ParsedEventDetails(occurredAtLocal = t, transactionReference = "REF-R"),
                at = Instant.parse("2026-08-10T09:00:00Z"),
            )
            persistEvent(
                smsId = "sms-r-in",
                event = event("pe-r-in", "sms-r-in", MessageFamily.TRANSFER_IN, money("700.00"), destination = own3003),
                details = ParsedEventDetails(occurredAtLocal = t, transactionReference = "REF-R"),
                at = Instant.parse("2026-08-10T09:00:30Z"),
            )
        }

        seed()
        reconciliation.reconcileStoredEvents()
        val storedPass = ftRepo.listAll()
        seed()
        reconciliation.reconcileAffectedRawSmsIds(rawRepo.listIdsByReceivedAt())
        val batchPass = ftRepo.listAll()

        assertEquals(storedPass, batchPass)
        val selfTransfer = batchPass.single()
        assertEquals(FinancialTransactionType.SELF_TRANSFER, selfTransfer.type)
        assertEquals(listOf("pe-r-in", "pe-r-out"), selfTransfer.linkedParsedEventIds.sorted())
    }

}
