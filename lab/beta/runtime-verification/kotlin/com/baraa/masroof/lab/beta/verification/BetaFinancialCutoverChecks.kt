package com.baraa.masroof.lab.beta.verification

import android.content.Context
import androidx.room.Room
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.beta.data.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.IncomeAttribution
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*

/** Real independent interpreter -> persisted original facts -> explicit financial approval.
 * Synthetic local/emulator evidence only, without production storage or device inbox reads. */
object BetaFinancialCutoverChecks {
    private val bank = BankId("BANK_ALJAZIRA")
    private val at = Instant.parse("2026-10-18T12:00:00Z")
    private val account = OwnedAsset(AssetId(Environment.BETA, bank, "account"), AssetKind.ACCOUNT, "1111")
    private val facility = OwnedAsset(AssetId(Environment.BETA, bank, "facility"), AssetKind.CREDIT_FACILITY)
    private val credit = OwnedAsset(AssetId(Environment.BETA, bank, "credit"), AssetKind.CREDIT_CARD, "2222", facility.id, CreditCardRole.PRIMARY)
    private val debit = OwnedAsset(AssetId(Environment.BETA, bank, "debit"), AssetKind.DEBIT_CARD, "3333", account.id)
    private val loan = OwnedAsset(AssetId(Environment.BETA, bank, "loan"), AssetKind.LOAN, "4444")
    private val purchase = "Credit card purchase\nCard: 2222\nAmount: USD 23\nExchange rate: 3.756957\nInternational fee: SAR 1.99\nDate: 2026-10-18 13:00"
    private val request = EvidenceRequest("m8-cutover", EvidenceOrigin.EXPLICIT_SAMPLE)
    private fun message(id: String, text: String) = EvidenceMessage(id, "AlJazira", text, at, ReceiptTimeSource.USER_SUPPLIED)
    private fun id(provider: String) = ProcessBetaEvidence.identity(request.namespace, provider)
    private fun seed(context: Context) {
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { beta ->
            beta.setBankEnabled(bank, true)
            listOf(account, facility, credit, debit, loan).forEach { beta.registerAsset(it) }
        }
    }
    private fun draft(beta: BetaContainer, provider: String, type: FinancialType, source: OwnedAsset?, destination: OwnedAsset?): BetaFinancialApproval {
        val preview = requireNotNull(beta.financialApprovalPreview(id(provider)))
        assertNull(preview.blockedReason)
        return requireNotNull(betaApprovalDraft(preview, type.name,
            source?.let { "asset:${it.id.localId}" } ?: "external", destination?.let { "asset:${it.id.localId}" } ?: "external",
            if (type == FinancialType.EXTERNAL_TRANSFER_IN) IncomeAttribution.OTHER.name else null))
    }
    fun familiesAndIdempotence(context: Context) {
        seed(context)
        data class Case(val name: String, val text: String, val type: FinancialType, val source: OwnedAsset?, val destination: OwnedAsset?)
        val cases = listOf(
            Case("credit", purchase, FinancialType.EXPENSE, credit, null),
            Case("mada", "Debit card purchase\nCard: 3333\nFrom account: 1111\nAmount: SAR 42\nDate: 2026-10-18 13:00", FinancialType.EXPENSE, debit, null),
            Case("settlement", "Card payment\nCard: 2222\nFrom account: 1111\nAmount: SAR 100\nDate: 2026-10-18 13:00", FinancialType.CREDIT_CARD_PAYMENT, account, credit),
            Case("refund", "Refund\nCard: 2222\nAmount: SAR 20\nDate: 2026-10-18 13:00", FinancialType.REFUND, null, credit),
            Case("bill", "Bill payment\nFrom account: 1111\nBiller: Synthetic Utilities\nAmount: SAR 50\nDate: 2026-10-18 13:00", FinancialType.BILL_PAYMENT, account, null),
            Case("loan", "Loan repayment\nFrom account: 1111\nCounterparty: Synthetic Finance\nAmount: SAR 500\nDate: 2026-10-18 13:00", FinancialType.LOAN_REPAYMENT, account, loan),
            Case("out", "Outgoing transfer\nFrom account: 1111\nTo account: 5555\nDestination bank: Riyad Bank\nAmount: SAR 60\nDate: 2026-10-18 13:00", FinancialType.EXTERNAL_TRANSFER_OUT, account, null),
            Case("in", "Incoming transfer\nFrom account: 5555\nTo account: 1111\nSource bank: Riyad Bank\nAmount: SAR 70\nDate: 2026-10-18 13:00", FinancialType.EXTERNAL_TRANSFER_IN, null, account))
        BetaContainer(context).use { beta ->
            for (case in cases) {
                beta.importEvidence(request, listOf(message(case.name, case.text)))
                val key = RoomBetaFinancialAdoption.key(id(case.name))
                assertNull(beta.financialPosting(key))
                val approval = draft(beta, case.name, case.type, case.source, case.destination)
                val first = beta.approveEvidenceFinancial(approval) as BetaFinancialAdoptionResult.Posted
                assertTrue(case.name, first.result is FinancialPostingResult.Applied)
                assertEquals(case.type, beta.financialPosting(key)!!.command.type)
                assertTrue((beta.approveEvidenceFinancial(approval) as BetaFinancialAdoptionResult.Posted).result is FinancialPostingResult.AlreadyApplied)
                assertEquals(1, beta.financialHistory(key).size)
            }
            val posting = beta.financialPosting(RoomBetaFinancialAdoption.key(id("credit")))!!
            assertEquals(Money.of("23", Currency.USD), posting.command.amount)
            assertEquals("SMS", posting.command.appliedFx!!.source)
            assertEquals(Instant.parse("2026-10-18T10:00:00Z"), posting.command.time.instant)
        }
        BetaContainer(context).use { beta -> cases.forEach { assertNotNull(beta.financialPosting(RoomBetaFinancialAdoption.key(id(it.name)))) } }
    }
    fun unsafeAndOriginalProof(context: Context) {
        seed(context)
        BetaContainer(context).use { beta ->
            listOf("otp" to "OTP: 123456\n$purchase", "pending" to purchase.replace("Credit card purchase", "Credit card purchase pending"),
                "rejected" to purchase.replace("Credit card purchase", "Credit card purchase declined")).forEach { (name, body) ->
                beta.importEvidence(request, listOf(message(name, body)))
                val preview = beta.financialApprovalPreview(id(name))!!
                assertNotNull(preview.blockedReason)
                val command = BetaFinancialApproval(id(name), preview.item.archiveRevision, null, FinancialType.EXPENSE, credit.id, null)
                assertTrue(beta.approveEvidenceFinancial(command) is BetaFinancialAdoptionResult.Review)
                assertNull(beta.financialPosting(command.let { RoomBetaFinancialAdoption.key(it.evidenceId) }))
            }
            beta.importEvidence(request, listOf(message("proof", purchase)))
        }
        val db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        try { runBlocking {
            val original = db.evidenceDao().outcome(id("proof"))!!
            val changes = listOf<(JSONObject) -> Unit>(
                { it.put("engineVersion", "historical-engine") },
                { it.put("rulesVersion", "outdated-rules") },
                { it.put("evidenceId", "another-evidence") },
                { it.getJSONArray("evidence").getJSONObject(0).put("end", purchase.length + 1) },
                { it.put("evidence", org.json.JSONArray()) })
            for (change in changes) {
                db.evidenceDao().outcome(original.copy(candidateJson = JSONObject(original.candidateJson).apply(change).toString()))
                val service = RoomBetaFinancialAdoption(db)
                assertNotNull(service.preview(original.rawId)!!.blockedReason)
                val row = service.reviewPage().single { it.evidenceId == original.rawId }
                assertTrue(service.approve(BetaFinancialApproval(original.rawId, row.archiveRevision, null, FinancialType.EXPENSE, credit.id, null)) is BetaFinancialAdoptionResult.Review)
                assertNull(RoomBetaFinancialLedger(db).readAudit(RoomBetaFinancialAdoption.key(original.rawId)))
            }
            db.evidenceDao().outcome(original)
        } } finally { db.close() }
    }
    fun currentArchiveAndLegacyPendingBinding(context: Context) {
        seed(context)
        lateinit var stale: BetaFinancialApproval
        BetaContainer(context).use { beta ->
            beta.importEvidence(request, listOf(message("revision", purchase)))
            stale = draft(beta, "revision", FinancialType.EXPENSE, credit, null)
        }
        val db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        try { runBlocking {
            val old = db.evidenceDao().outcome(id("revision"))!!
            // Same attempt/route/empty historical slot but different current facts must invalidate intent.
            val changed = JSONObject(old.candidateJson).apply { getJSONObject("facts").getJSONObject("amount").put("amount", "24.00") }
            db.evidenceDao().outcome(old.copy(candidateJson = changed.toString()))
            assertEquals(BetaFinancialAdoptionResult.Review("STALE_ARCHIVE"), RoomBetaFinancialAdoption(db).approve(stale))
            assertNull(RoomBetaFinancialLedger(db).readAudit(RoomBetaFinancialAdoption.key(old.rawId)))
            assertTrue(RoomBetaFinancialAdoption(db).cancelApproval(stale))
            val historical = old.copy(baselineJson = old.candidateJson, candidateJson = "{}")
            db.evidenceDao().outcome(historical)
            val service = RoomBetaFinancialAdoption(db)
            val item = service.reviewPage().single()
            assertTrue(service.approve(stale.copy(archiveRevision = item.archiveRevision)) is BetaFinancialAdoptionResult.Review)
            assertEquals(historical, db.evidenceDao().outcome(old.rawId))
            assertNull(RoomBetaFinancialLedger(db).readAudit(RoomBetaFinancialAdoption.key(old.rawId)))
        } } finally { db.close() }
    }
}
