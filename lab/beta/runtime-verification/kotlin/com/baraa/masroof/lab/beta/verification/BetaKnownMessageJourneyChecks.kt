package com.baraa.masroof.lab.beta.verification

import android.content.Context
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.dashboard.*
import com.baraa.masroof.lab.finance.dashboard.producer.DashboardSection
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import org.junit.*
import org.junit.Assert.*
import java.time.*

class BetaKnownMessageJourneyChecks(private val context: Context): AutoCloseable {
    private val bank=BankId("BANK_ALJAZIRA")
    private lateinit var beta: BetaContainer
    private val at=Instant.parse("2026-10-18T12:00:00Z")
    private fun account(suffix: String)=OwnedAsset(AssetId(Environment.BETA,bank,"account-$suffix"),AssetKind.ACCOUNT,suffix)
    private val query=DashboardQuery(Environment.BETA,Instant.parse("2026-10-01T00:00:00Z"),Instant.parse("2026-11-01T00:00:00Z"),ZoneId.of("Asia/Riyadh"),Currency.SAR)
    init {open()}
    private fun open() {BetaContainer.resetOffline(context);beta=BetaContainer(context);beta.acknowledgeStart();beta.setBankEnabled(bank,true)}
    override fun close() {beta.close();BetaContainer.resetOffline(context)}
    private fun capture(id: String,body: String): String=(beta.importEvidence(EvidenceRequest("journey-$id",EvidenceOrigin.EXPLICIT_SAMPLE),listOf(EvidenceMessage(id,"AlJazira",body,at,ReceiptTimeSource.USER_SUPPLIED))).single() as EvidenceResult.Stored).summary.id
    private fun key(id: String)=PostingKey(Environment.BETA,"evidence:$id")
    fun aSavedMineDecisionIsReusedWhenALaterMessageProvesCardKind() {
        capture("unknown-kind", "POS Purchase\nCard: 4521\nAt: TEST STORE\nAmount: 10 SAR\nDate: 2026-10-18 10:00")
        beta.decideOwnership(beta.ownershipSnapshot().candidates.single(),true)
        assertNull(beta.ownershipSnapshot().candidates.single().asset)
        capture("known-kind", "POS Purchase\nCard: 4521\nAt: TEST STORE\nAmount: 11 SAR\nAvailable balance: 200 SAR\nDate: 2026-10-18 11:00")
        beta.processAutomaticEvidence()
        val candidate=beta.ownershipSnapshot().candidates.single()
        assertEquals(com.baraa.masroof.lab.beta.application.BetaOwnershipStatus.OWNED,candidate.status)
        val card=requireNotNull(candidate.asset)
        assertEquals(AssetKind.CREDIT_CARD,card.kind)
        beta.saveManualAsset(com.baraa.masroof.lab.beta.application.ManualAssetDraft(card.id,card.kind,card.last4.orEmpty(),
            AssetId(Environment.BETA,bank,"new-facility"),CreditCardRole.PRIMARY,true,true,card,true))
        beta.processAutomaticEvidence()
        assertEquals(2,beta.dashboard(query).snapshot.transactions.size)
        assertTrue(beta.reviewInboxPage().rows.isEmpty())
    }
    fun repeatedOwnershipReviewsAreGroupedAndOneConfirmationPostsAllMessages() {
        repeat(205) { capture("same-account-$it", "Cash withdrawal\nFrom account: 1234\nAmount: ${it+1} SAR\nDate: 2026-10-18 12:00") }
        beta.processAutomaticEvidence()
        assertEquals(1,beta.reviewInboxPage().rows.size)
        assertEquals(1,beta.evidenceInboxCounts().needsReview)
        assertNull(beta.reviewInboxPage().next)
        val candidates=beta.ownershipSnapshot().candidates
        assertEquals(1,candidates.size)
        beta.decideOwnership(candidates.single(),true)
        beta.processAutomaticEvidence()
        assertEquals(205,beta.dashboard(query).snapshot.transactions.size)
        assertTrue(beta.reviewInboxPage().rows.isEmpty())
        assertEquals(0,beta.evidenceInboxCounts().needsReview)
    }
    fun transactionClassificationAndIgnoredRestoreKeepOneLedgerRowAndCorrectDirection() {
        beta.registerAsset(account("1234"))
        val id=capture("correction", "Cash withdrawal\nFrom account: 1234\nAmount: 42 SAR\nDate: 2026-10-18 12:00")
        beta.processAutomaticEvidence()
        val token=requireNotNull(beta.transactionActionDetail(key(id).transactionId)).token
        assertTrue(beta.reclassifyTransaction(token,"refund",FinancialType.REFUND))
        assertTrue(beta.reclassifyTransaction(token,"refund-again",FinancialType.REFUND))
        assertFalse(beta.reclassifyTransaction(token,"stale",FinancialType.INCOME))
        assertEquals(FinancialType.REFUND,beta.financialPosting(key(id))!!.command.type)
        assertEquals("42",beta.dashboard(query).snapshot.accounts.single().flow!!.totalIn.amount.stripTrailingZeros().toPlainString())
        val current=requireNotNull(beta.transactionActionDetail(key(id).transactionId)).token
        val result=beta.saveReview(current,"ignore",com.baraa.masroof.lab.finance.corrections.FinancialDecision.SuppressAsNonFinancial)
        assertTrue(result is com.baraa.masroof.lab.beta.application.corrections.BetaCorrectionResult.Applied)
        assertTrue(beta.dashboard(query).snapshot.transactions.isEmpty())
        assertTrue(beta.reviewInboxPage().rows.isEmpty())
        assertEquals(id,beta.reviewInboxPage(ignored=true).rows.single().id)
        beta.resolveReviewAsNonFinancial(id,restore=true)
        assertEquals(FinancialType.REFUND,beta.financialPosting(key(id))!!.command.type)
        assertEquals(1,beta.dashboard(query).snapshot.transactions.size)
        assertTrue(beta.reviewInboxPage(ignored=true).rows.isEmpty())
        beta.processAutomaticEvidence()
        assertEquals(FinancialType.REFUND,beta.financialPosting(key(id))!!.command.type)
    }
    fun capturedUnprocessedEvidenceIsOperationalWorkRatherThanHumanReview() {
        val id=requireNotNull(beta.captureLive(EvidenceMessage("technical-pending","AlJazira","شراء عبر نقاط البيع\nبطاقة: 1234\nمبلغ: SAR 25\nلدى: STORE",at,ReceiptTimeSource.USER_SUPPLIED)))
        assertEquals(1,beta.evidenceInboxCounts().processing)
        assertEquals(0,beta.evidenceInboxCounts().needsReview)
        assertTrue(beta.reviewInboxPage().rows.isEmpty())
        assertEquals(1,beta.reviewInboxPage().processing)
        assertNotNull(beta.evidenceSummary(id))
    }
    fun knownLoanInstallmentsCreateOneOwnedLoanPerBankAndType() {
        beta.registerAsset(account("3001"))
        val ids=listOf("شخصي","سيارة","عقاري").mapIndexed {index,label ->
            capture("loan-$index","خصم: قسط تمويل\nمن: 3001\nالقسط: SAR 3,036.11\nالمبلغ المتبقي: SAR 33,397.25\nلـ: تمويل $label\nفي: 2026-10-18 01:10")
        }
        beta.processAutomaticEvidence()
        assertEquals(3,beta.assets().count {it.kind==AssetKind.LOAN})
        ids.forEach {assertEquals(FinancialType.LOAN_REPAYMENT,beta.financialPosting(key(it))?.classification?.effectiveType)}
        assertTrue(beta.reviewInboxPage().rows.isEmpty())
        val snapshot=beta.dashboard(query).snapshot
        assertEquals(setOf("PERSONAL","AUTO","MORTGAGE"),snapshot.loans.map {it.type}.toSet())
        assertTrue(snapshot.loans.all {it.remaining?.amount?.compareTo(java.math.BigDecimal("33397.25"))==0})
        beta.processAutomaticEvidence();beta.close();beta=BetaContainer(context);beta.processAutomaticEvidence()
        assertEquals(3,beta.assets().count {it.kind==AssetKind.LOAN})
        assertEquals(3,beta.dashboard(query).snapshot.transactions.size)
    }
    fun loansNeedOwnedSourceAndAnUnambiguousType() {
        capture("unowned-loan","خصم: قسط تمويل\nمن: 3001\nالقسط: SAR 20\nلـ: تمويل شخصي\nفي: 2026-10-18 01:10")
        beta.processAutomaticEvidence()
        assertTrue(beta.assets().none {it.kind==AssetKind.LOAN})
        beta.registerAsset(account("3001"))
        val unknown=capture("unknown-loan","خصم: قسط تمويل\nمن: 3001\nالقسط: SAR 21\nلـ: تمويل\nفي: 2026-10-18 02:10")
        beta.processAutomaticEvidence()
        assertNull(beta.financialPosting(key(unknown)))
        assertEquals("CONFIRM_LOAN",beta.evidenceSummary(unknown).financialReviewReason)
    }
    fun durableSmsReceiptIsUsedOnlyWhenBankTimeIsAbsent() {
        beta.registerAsset(account("1234"))
        val body="Cash withdrawal\nFrom account: 1234\nAmount: 23 SAR"
        val manual=capture("manual-no-time",body)
        assertEquals(EvidenceDisposition.REVIEW,beta.evidenceSummary(manual).disposition)
        val result=beta.importEvidence(EvidenceRequest("durable-time",EvidenceOrigin.EXPLICIT_HISTORY,setOf(bank)),
            listOf(EvidenceMessage("receipt-no-time","AlJazira",body,at.plusSeconds(600),ReceiptTimeSource.PROVIDER_TIMESTAMP)))
        val id=(result.single() as EvidenceResult.Stored).summary.id
        beta.processAutomaticEvidence()
        // The sample is deliberately retained; an identical body at another time is a separate receipt.
        assertEquals(at.plusSeconds(600),beta.financialPosting(key(id))?.command?.time?.instant)
        assertEquals(EvidenceDisposition.INTERPRETED,beta.evidenceSummary(id).disposition)
        assertNull(beta.financialPosting(key(manual)))
    }
    fun clearExternalTransfersAndSalaryReachAccountAndDashboardWithoutApproval() {
        beta.registerAsset(account("1234"))
        val incoming=capture("in","حوالة واردة: محلية\nعبر: بنك الرياض\nمبلغ: SAR 100\nإلى: 1234\nاسم المرسل: TEST_COMPANY\nالبنك المرسل: بنك الرياض\nفي: 2026-10-18 09:12")
        val outgoing=capture("out","عملية حوالة مالية صادرة مقبولة\nخصمت من حساب: 1234\nالى: TEST_BENEFICIARY\nمبلغ العملية: 30 SAR\nالمعرف البديل \\الايبان : 0593\n[البنك العربي الوطني]\nفي: 2026-10-18 12:26\nرقم المعاملة: TEST_REFERENCE_1")
        beta.processAutomaticEvidence()
        assertEquals(FinancialType.EXTERNAL_TRANSFER_IN,beta.financialPosting(key(incoming))?.command?.type)
        assertEquals(FinancialType.EXTERNAL_TRANSFER_OUT,beta.financialPosting(key(outgoing))?.command?.type)
        val read=beta.dashboard(query)
        assertEquals(2,read.snapshot.transactions.size)
        assertEquals("70",read.snapshot.fleetFlow!!.cashPosition.amount.stripTrailingZeros().toPlainString())
        assertTrue(beta.reviewInboxPage().rows.isEmpty())
    }
    fun aPendingUnknownMessageDoesNotHideConfirmedAccountMovements() {
        beta.registerAsset(account("1234"))
        capture("cash","Cash withdrawal\nFrom account: 1234\nAmount: 42 SAR\nDate: 2026-10-18 12:00")
        capture("unknown","Unknown banking layout")
        beta.processAutomaticEvidence()
        val read=beta.dashboard(query)
        assertTrue(DashboardSection.CURRENT_LEDGER in read.unavailableSections)
        assertEquals(1,read.snapshot.transactions.size)
        assertEquals("42",read.snapshot.accounts.single().flow!!.totalOut.amount.stripTrailingZeros().toPlainString())
        assertNotNull(read.snapshot.analysis)
        assertNull(read.snapshot.commitments)
        assertEquals(1,beta.reviewInboxPage().rows.size)
    }
    fun debitAccountLinkAndSpendingAreRecoveredFromOwnedStructuredRoles() {
        capture("mada","شراء عبر نقاط البيع\nبطاقة مدى: 5678\nلدى: TEST SHOP\nبمبلغ: SAR 25\nخصمت من حساب: 1234\nفي: 2026-10-18 12:00")
        beta.ownershipSnapshot().candidates.forEach {beta.decideOwnership(it,true)}
        beta.processAutomaticEvidence()
        val read=beta.dashboard(query)
        assertEquals(2,beta.assets().size)
        assertEquals(1,read.snapshot.debitCards.size)
        assertEquals(account("1234").last4,read.snapshot.accounts.single().last4)
        assertEquals(1,read.snapshot.transactions.single().debitSpendingCards.size)
        assertEquals("25",read.snapshot.accounts.single().flow!!.totalOut.amount.stripTrailingZeros().toPlainString())
        val displayed=kotlinx.coroutines.runBlocking {
            com.baraa.masroof.lab.beta.application.BetaDashboardWorkflow({q,p -> beta.dashboard(q,p)},Clock.fixed(at,ZoneOffset.UTC)).load(YearMonth.of(2026,9))
        }
        assertEquals("25",displayed.snapshot.debitCards.single().spending!!.amount.stripTrailingZeros().toPlainString())
        assertEquals("25",displayed.snapshot.totals!!.debitSalarySpending!!.amount.stripTrailingZeros().toPlainString())
    }
    fun complementaryOwnedTransferNoticesProduceOneStableSelfTransfer() {
        beta.registerAsset(account("1234"));beta.registerAsset(account("5678"))
        val first=capture("own-out","حوالة صادرة الى حسابك الجاري\nمن: 1234\nمبلغ: SAR 50\nإلى: 5678\nفي: 2026-10-18 12:00")
        beta.processAutomaticEvidence()
        assertEquals(FinancialType.SELF_TRANSFER,beta.financialPosting(key(first))?.command?.type)
        val second=capture("own-in","حوالة واردة داخلية\nمبلغ: SAR 50\nإلى: 5678\nاسم المرسل: TEST\nرقم حساب المرسل: 1234\nالبنك المرسل: بنك الجزيرة\nفي: 2026-10-18 12:00")
        beta.processAutomaticEvidence()
        val read=beta.dashboard(query)
        assertEquals(1,read.snapshot.transactions.size)
        assertEquals(setOf(first,second),beta.financialPosting(key(first))?.command?.evidenceIds)
        assertEquals(0,read.snapshot.fleetFlow!!.externalOut.amount.signum())
        assertEquals(0,read.snapshot.fleetFlow!!.cashPosition.amount.signum())
        beta.processAutomaticEvidence()
        assertEquals(1,beta.dashboard(query).snapshot.transactions.size)
        BetaContainer(context).use {reopened -> assertEquals(1,reopened.dashboard(query).snapshot.transactions.size)}
    }
    fun extraSameDirectionTransferIsAmbiguousAndNeverDoubleCounts() {
        beta.registerAsset(account("1234"));beta.registerAsset(account("5678"))
        val body="حوالة صادرة الى حسابك الجاري\nمن: 1234\nمبلغ: SAR 50\nإلى: 5678\nفي: 2026-10-18 12:00"
        capture("one",body)
        beta.processAutomaticEvidence()
        capture("two",body.replace("12:00","12:01"))
        beta.processAutomaticEvidence()
        assertTrue(beta.dashboard(query).snapshot.transactions.isEmpty())
        // Replaying after a new import must not discard the unresolved collision.
        capture("notice","تم تسجيل الدخول")
        beta.processAutomaticEvidence()
        assertTrue(beta.dashboard(query).snapshot.transactions.isEmpty())
    }
    fun embeddedLiveIntakeRequiresExplicitLabInitializationAndResetDisablesIt() {
        val embedded=object: android.content.ContextWrapper(context) {
            override fun getPackageName()="com.baraa.masroof"
        }
        beta.close();BetaContainer.resetOffline(context)
        val before=context.databaseList().filter {it in BetaEnvironment.forbiddenDatabaseNames}.toSet()
        assertFalse(com.baraa.masroof.lab.beta.application.sms.BetaLiveSms.enabled(embedded))
        beta=BetaContainer(context);beta.beginOnboarding()
        assertTrue(com.baraa.masroof.lab.beta.application.sms.BetaLiveSms.enabled(embedded))
        assertEquals(before,context.databaseList().filter {it in BetaEnvironment.forbiddenDatabaseNames}.toSet())
        beta.reset()
        assertFalse(com.baraa.masroof.lab.beta.application.sms.BetaLiveSms.enabled(embedded))
    }

    fun ignoringUnknownEvidenceIsExplicitReversibleAndDoesNotTeachTheEngine() {
        val id=capture("unknown-review","Unknown message")
        beta.processAutomaticEvidence()
        assertEquals(id,beta.reviewInboxPage().rows.single().id)
        beta.resolveReviewAsNonFinancial(id)
        beta.processAutomaticEvidence()
        assertTrue(beta.reviewInboxPage().rows.isEmpty())
        assertTrue(beta.reviewInboxPage(ignored=true).rows.single().dismissed)
        assertEquals(0,beta.evidenceInboxCounts().needsReview)
        assertTrue(beta.dashboard(query).snapshot.transactions.isEmpty())
        beta.resolveReviewAsNonFinancial(id,restore=true)
        beta.processAutomaticEvidence()
        assertEquals(id,beta.reviewInboxPage().rows.single().id)
        assertNull(beta.financialPosting(key(id)))
    }

    fun ambiguousCounterpartyCannotBecomeAnExternalTransfer() {
        beta.registerAsset(account("1234"));beta.registerAsset(account("5678"))
        beta.registerAsset(account("5678").copy(id=account("5678").id.copy(localId="collision")))
        val id=capture("ambiguous-owned","حوالة صادرة الى حسابك الجاري\nمن: 1234\nمبلغ: SAR 50\nإلى: 5678\nفي: 2026-10-18 12:00")
        beta.processAutomaticEvidence()
        assertNull(beta.financialPosting(key(id)))
        assertEquals("AMBIGUOUS_OWNER",beta.evidenceSummary(id).financialReviewReason)
    }

}
