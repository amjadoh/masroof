package com.baraa.masroof.lab.beta.verification

import android.content.Context
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.application.*
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.time.Instant
import org.junit.Assert.*

object BetaOwnershipChecks {
    private val bank = BankId("BANK_ALJAZIRA")
    private val at = Instant.parse("2026-10-18T12:00:00Z")
    private fun capture(beta: BetaContainer, namespace: String, count: Int = 1, suffix: String = "1234", offset: Int = 0) =
        beta.importEvidence(EvidenceRequest(namespace, EvidenceOrigin.EXPLICIT_SAMPLE), (1..count).map {
            EvidenceMessage("$it", "AlJazira", "Cash withdrawal\nFrom account: $suffix\nAmount: ${it + offset} SAR\nDate: 2026-10-18 12:00",
                at.plusSeconds(it.toLong() + offset), ReceiptTimeSource.USER_SUPPLIED)
        }).map { (it as EvidenceResult.Stored).summary.id }
    private inline fun isolated(context: Context, block: (BetaContainer) -> Unit) {
        BetaContainer.resetOffline(context)
        try { BetaContainer(context).use { beta -> beta.beginOnboarding(); beta.setBankEnabled(bank, true)
            beta.advanceOnboarding(BetaOnboardingStep.PERMISSION); block(beta) } }
        finally { BetaContainer.resetOffline(context) }
    }
    private fun finishOwnership(beta: BetaContainer) {
        beta.advanceOnboarding(BetaOnboardingStep.IMPORT)
        beta.advanceOnboarding(BetaOnboardingStep.ASSETS)
        beta.processAutomaticEvidence()
    }

    fun wholeImportIsOneCandidateAndOneDecisionPostsAll(context: Context) = isolated(context) { beta ->
        val ids = capture(beta, "grouped", 65)
        val candidate = beta.ownershipSnapshot().candidates.single()
        assertEquals(BetaOwnershipStatus.UNKNOWN, candidate.status)
        assertEquals("1234", candidate.reference.suffix)
        beta.advanceOnboarding(BetaOnboardingStep.IMPORT)
        assertThrows(IllegalStateException::class.java) { beta.advanceOnboarding(BetaOnboardingStep.ASSETS) }
        beta.decideOwnership(candidate, true)
        beta.processAutomaticEvidence() // No money before finishing the ownership step.
        assertTrue(ids.all { beta.financialPosting(PostingKey(Environment.BETA, "evidence:$it")) == null })
        assertEquals(1, beta.assets().size)
        assertEquals(0, beta.ownershipSnapshot().unresolvedCount)
        beta.advanceOnboarding(BetaOnboardingStep.ASSETS); beta.processAutomaticEvidence()
        assertTrue(ids.all { beta.financialPosting(PostingKey(Environment.BETA, "evidence:$it")) != null })
        val next = capture(beta, "later", suffix = "1234", offset = 100).single()
        assertEquals(BetaOwnershipStatus.OWNED, beta.ownershipSnapshot().candidates.single().status)
        beta.processAutomaticEvidence()
        assertNotNull(beta.financialPosting(PostingKey(Environment.BETA, "evidence:$next")))
        assertEquals(1, beta.assets().size)
        BetaContainer(context).use { reopened ->
            assertEquals(BetaOwnershipStatus.OWNED, reopened.ownershipSnapshot().candidates.single().status)
            reopened.processAutomaticEvidence()
            assertEquals(1, reopened.financialHistory(PostingKey(Environment.BETA, "evidence:${ids.first()}")).size)
        }
    }

    fun externalDecisionPersistsAndDoesNotBecomeReviewForEveryMessage(context: Context) = isolated(context) { beta ->
        val ids = capture(beta, "external", 30)
        beta.decideOwnership(beta.ownershipSnapshot().candidates.single(), false)
        assertTrue(beta.assets().isEmpty())
        finishOwnership(beta)
        assertTrue(ids.all { beta.financialPosting(PostingKey(Environment.BETA, "evidence:$it")) == null })
        assertEquals(0, beta.evidenceInboxCounts().needsReview)
        assertEquals(0, beta.evidenceInboxCounts().needsOwnership)
        assertTrue(beta.evidencePage(needsReviewOnly = true).isEmpty())
        capture(beta, "external-later")
        beta.processAutomaticEvidence()
        assertEquals(BetaOwnershipStatus.EXTERNAL, beta.ownershipSnapshot().candidates.single().status)
        assertEquals(0, beta.evidenceInboxCounts().needsReview)
        BetaContainer(context).use { assertEquals(BetaOwnershipStatus.EXTERNAL, it.ownershipSnapshot().candidates.single().status) }
    }

    fun registryCollisionsNeedAnExplicitIdentityAndNeverMerge(context: Context) = isolated(context) { beta ->
        val ids = capture(beta, "collision", 2)
        val first = OwnedAsset(AssetId(Environment.BETA, bank, "first"), AssetKind.ACCOUNT, "1234")
        beta.registerAsset(first); beta.registerAsset(first.copy(id = first.id.copy(localId = "second")))
        val candidate = beta.ownershipSnapshot().candidates.single()
        assertEquals(BetaOwnershipStatus.UNKNOWN, candidate.status)
        assertThrows(IllegalStateException::class.java) { beta.decideOwnership(candidate, true) }
        beta.decideOwnership(candidate, true, first.id)
        assertEquals(2, beta.assets().size)
        finishOwnership(beta)
        assertTrue(ids.all { beta.financialPosting(PostingKey(Environment.BETA, "evidence:$it"))?.classification?.input?.source ==
            com.baraa.masroof.lab.finance.classification.FinancialEndpoint.Owned(first.id) })
    }

    fun staleDecisionAndSecurityEvidenceCannotCreateOwnership(context: Context) = isolated(context) { beta ->
        capture(beta, "stale")
        val old = beta.ownershipSnapshot().candidates.single()
        capture(beta, "additional", count = 2)
        assertThrows(IllegalStateException::class.java) { beta.decideOwnership(old, true) }
        beta.importEvidence(EvidenceRequest("otp", EvidenceOrigin.EXPLICIT_SAMPLE), listOf(
            EvidenceMessage("otp", "AlJazira", "OTP: 5678. Do not share this code.", at, ReceiptTimeSource.USER_SUPPLIED)))
        assertEquals(1, beta.ownershipSnapshot().candidates.size)
        assertTrue(beta.assets().isEmpty())
    }

    fun unknownCardOwnershipDoesNotGuessTypeAndExplicitConfigurationCanPost(context: Context) = isolated(context) { beta ->
        val id = (beta.importEvidence(EvidenceRequest("unknown-card", EvidenceOrigin.EXPLICIT_SAMPLE), listOf(
            EvidenceMessage("one", "AlJazira", "POS Purchase\nCard: 5678\nAmount: 42 SAR\nMerchant: Synthetic shop\nDate: 2026-10-18 12:00", at, ReceiptTimeSource.USER_SUPPLIED)
        )).single() as EvidenceResult.Stored).summary.id
        val candidate = beta.ownershipSnapshot().candidates.single()
        assertEquals(BetaOwnershipKind.CARD, candidate.reference.kind)
        beta.decideOwnership(candidate, true)
        assertTrue(beta.assets().isEmpty()) // Ownership alone cannot invent credit/debit or a facility.
        val account = OwnedAsset(AssetId(Environment.BETA, bank, "configured-account"), AssetKind.ACCOUNT, "1234")
        beta.registerAsset(account)
        beta.saveManualAsset(ManualAssetDraft(AssetId(Environment.BETA, bank, "configured-card"), AssetKind.DEBIT_CARD, "5678",
            account.id, ownedConfirmed = true))
        finishOwnership(beta)
        assertNotNull(beta.financialPosting(PostingKey(Environment.BETA, "evidence:$id")))
    }

    fun externallyOwnedPartialMessagesStayOutOfFinancialReview(context: Context) = isolated(context) { beta ->
        beta.importEvidence(EvidenceRequest("partial-external", EvidenceOrigin.EXPLICIT_SAMPLE), listOf(
            EvidenceMessage("one", "AlJazira", "Cash withdrawal\nFrom account: 1234\nDate: 2026-10-18 12:00", at, ReceiptTimeSource.USER_SUPPLIED)))
        beta.decideOwnership(beta.ownershipSnapshot().candidates.single(), false)
        finishOwnership(beta)
        assertEquals(0, beta.evidenceInboxCounts().needsReview)
        assertTrue(beta.evidencePage(needsReviewOnly = true).isEmpty())
    }

    fun debitLinkUsesProvedAccountAfterBothOwnershipDecisions(context: Context) = isolated(context) { beta ->
        val id = (beta.importEvidence(EvidenceRequest("debit-link", EvidenceOrigin.EXPLICIT_SAMPLE), listOf(
            EvidenceMessage("one", "AlJazira", "POS Purchase\nCard: 5678\nCard type: debit\nFrom account: 1234\nAmount: 42 SAR\nDate: 2026-10-18 12:00", at, ReceiptTimeSource.USER_SUPPLIED)
        )).single() as EvidenceResult.Stored).summary.id
        val card = beta.ownershipSnapshot().candidates.single { it.reference.kind == BetaOwnershipKind.CARD }
        beta.decideOwnership(card, true)
        assertNull(beta.assets().single().parentId)
        beta.decideOwnership(beta.ownershipSnapshot().candidates.single { it.reference.kind == BetaOwnershipKind.ACCOUNT }, true)
        val owned = beta.assets()
        assertEquals(owned.single { it.kind == AssetKind.ACCOUNT }.id, owned.single { it.kind == AssetKind.DEBIT_CARD }.parentId)
        finishOwnership(beta)
        assertNotNull(beta.financialPosting(PostingKey(Environment.BETA, "evidence:$id")))
    }

    fun changingOwnershipHidesAutomaticEffectButPreservesAuditAndManualAuthority(context: Context) = isolated(context) { beta ->
        val id = capture(beta, "change").single()
        beta.decideOwnership(beta.ownershipSnapshot().candidates.single(), true)
        finishOwnership(beta)
        val key = PostingKey(Environment.BETA, "evidence:$id")
        assertNotNull(beta.financialPosting(key))
        beta.decideOwnership(beta.ownershipSnapshot().candidates.single(), false)
        beta.processAutomaticEvidence()
        assertNull(beta.financialPosting(key))
        assertEquals(1, beta.financialHistory(key).size)
        val q = com.baraa.masroof.lab.finance.dashboard.DashboardQuery(Environment.BETA, at.minusSeconds(86400), at.plusSeconds(86400), java.time.ZoneId.of("Asia/Riyadh"), Currency.SAR)
        val dashboard = beta.dashboard(q)
        assertTrue(dashboard.snapshot.transactions.isEmpty())
        assertEquals(0, requireNotNull(dashboard.financialSummary).reviewRequiredCount)
        beta.decideOwnership(beta.ownershipSnapshot().candidates.single(), true)
        beta.processAutomaticEvidence()
        assertNotNull(beta.financialPosting(key))
        assertEquals(1, beta.financialHistory(key).size)
        val preview = requireNotNull(beta.financialApprovalPreview(id))
        val owner = beta.assets().single()
        val request = BetaFinancialApproval(id, preview.item.archiveRevision, preview.item.postingRevision,
            FinancialType.FEE, owner.id, null, expectedRegistryRevision = preview.registryRevision)
        assertTrue(beta.approveEvidenceFinancial(request) is BetaFinancialAdoptionResult.Posted)
        beta.decideOwnership(beta.ownershipSnapshot().candidates.single(), false)
        beta.processAutomaticEvidence()
        assertNull(beta.financialPosting(key)) // Changed ownership holds the effect, not the user's saved decision.
        assertEquals(FinancialType.FEE, requireNotNull(beta.financialAuditPosting(key)).classification.effectiveType)
        assertEquals(CorrectionAuthority.USER_FINANCIAL_TYPE, requireNotNull(beta.financialAuditPosting(key)).classification.input.authority)
        assertEquals("OWNERSHIP_CHANGED", beta.evidenceSummary(id).financialReviewReason)
    }

    fun creditConfigurationIsExplicitAtomicAndSharedAcrossMessages(context: Context) = isolated(context) { beta ->
        val ids = beta.importEvidence(EvidenceRequest("credit-config", EvidenceOrigin.EXPLICIT_SAMPLE), (1..3).map {
            EvidenceMessage("$it", "AlJazira", "POS Purchase\nCard: 5678\nCard type: credit\nAmount: $it SAR\nAvailable balance: 100 SAR\nDate: 2026-10-18 12:00", at.plusSeconds(it.toLong()), ReceiptTimeSource.USER_SUPPLIED)
        }).map { (it as EvidenceResult.Stored).summary.id }
        beta.decideOwnership(beta.ownershipSnapshot().candidates.single(), true)
        val card = beta.assets().single()
        assertEquals(AssetKind.CREDIT_CARD, card.kind)
        assertNull(card.parentId); assertNull(card.creditCardRole)
        assertEquals(1, beta.ownershipSnapshot().configurationCount)
        beta.advanceOnboarding(BetaOnboardingStep.IMPORT)
        beta.advanceOnboarding(BetaOnboardingStep.ASSETS) // Card configuration does not block setup.
        val facility = AssetId(Environment.BETA, bank, "explicit-facility")
        val draft = ManualAssetDraft(card.id, card.kind, "5678", facility, CreditCardRole.PRIMARY,
            ownedConfirmed = true, editing = true, expectedOriginal = card, createCreditFacility = true)
        val configured = beta.saveManualAsset(draft)
        assertEquals(configured, beta.saveManualAsset(draft)) // Exact retry does not duplicate a facility.
        assertEquals(2, beta.assets().size)
        assertEquals(0, beta.ownershipSnapshot().configurationCount)
        beta.advanceOnboarding(BetaOnboardingStep.ASSETS); beta.processAutomaticEvidence()
        assertTrue(ids.all { beta.financialPosting(PostingKey(Environment.BETA, "evidence:$it")) != null })
    }

    fun conflictingKindsStayInReviewWithoutMergingOrBlockingOtherAccounts(context: Context) = isolated(context) { beta ->
        val accountId = capture(beta, "clear-account").single()
        beta.importEvidence(EvidenceRequest("kind-conflict", EvidenceOrigin.EXPLICIT_SAMPLE), listOf("credit", "debit").map { kind ->
            EvidenceMessage(kind, "AlJazira", "POS Purchase\nCard: 5678\nCard type: $kind\nAmount: 42 SAR\nDate: 2026-10-18 12:00", at, ReceiptTimeSource.USER_SUPPLIED) })
        val card = beta.ownershipSnapshot().candidates.single { it.reference.kind == BetaOwnershipKind.CARD }
        assertTrue(card.conflictingKinds)
        assertThrows(IllegalStateException::class.java) { beta.decideOwnership(card, true) }
        beta.deferOwnership(card)
        assertEquals(BetaOwnershipStatus.REVIEW, beta.ownershipSnapshot().candidates.single { it.reference.kind == BetaOwnershipKind.CARD }.status)
        beta.decideOwnership(beta.ownershipSnapshot().candidates.single { it.reference.kind == BetaOwnershipKind.ACCOUNT }, true)
        finishOwnership(beta)
        assertNotNull(beta.financialPosting(PostingKey(Environment.BETA, "evidence:$accountId")))
        assertEquals(1, beta.assets().size)
        assertEquals(2, beta.evidenceInboxCounts().needsReview)
    }

    fun decisionSurvivesBackupRestoreAndReset(context: Context) = isolated(context) { beta ->
        capture(beta, "backup")
        beta.decideOwnership(beta.ownershipSnapshot().candidates.single(), false)
        beta.backup()
        beta.decideOwnership(beta.ownershipSnapshot().candidates.single(), true)
        assertEquals(BetaOwnershipStatus.OWNED, beta.ownershipSnapshot().candidates.single().status)
        beta.restore()
        assertEquals(BetaOwnershipStatus.EXTERNAL, beta.ownershipSnapshot().candidates.single().status)
        beta.reset()
        assertTrue(beta.ownershipSnapshot().candidates.isEmpty())
    }
}
