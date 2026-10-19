package com.baraa.masroof.lab.beta

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import com.baraa.masroof.lab.beta.verification.BetaOwnershipChecks

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28])
class BetaOwnershipTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Test fun wholeImportIsOneCandidateAndOneDecisionPostsAll() = BetaOwnershipChecks.wholeImportIsOneCandidateAndOneDecisionPostsAll(context)
    @Test fun externalDecisionPersistsAndDoesNotBecomeReviewForEveryMessage() = BetaOwnershipChecks.externalDecisionPersistsAndDoesNotBecomeReviewForEveryMessage(context)
    @Test fun registryCollisionsNeedAnExplicitIdentityAndNeverMerge() = BetaOwnershipChecks.registryCollisionsNeedAnExplicitIdentityAndNeverMerge(context)
    @Test fun staleDecisionAndSecurityEvidenceCannotCreateOwnership() = BetaOwnershipChecks.staleDecisionAndSecurityEvidenceCannotCreateOwnership(context)
    @Test fun decisionSurvivesBackupRestoreAndReset() = BetaOwnershipChecks.decisionSurvivesBackupRestoreAndReset(context)
    @Test fun unknownCardOwnershipDoesNotGuessTypeAndExplicitConfigurationCanPost() = BetaOwnershipChecks.unknownCardOwnershipDoesNotGuessTypeAndExplicitConfigurationCanPost(context)
    @Test fun externallyOwnedPartialMessagesStayOutOfFinancialReview() = BetaOwnershipChecks.externallyOwnedPartialMessagesStayOutOfFinancialReview(context)
    @Test fun debitLinkUsesProvedAccountAfterBothOwnershipDecisions() = BetaOwnershipChecks.debitLinkUsesProvedAccountAfterBothOwnershipDecisions(context)
    @Test fun changingOwnershipHidesAutomaticEffectButPreservesAuditAndManualAuthority() = BetaOwnershipChecks.changingOwnershipHidesAutomaticEffectButPreservesAuditAndManualAuthority(context)
    @Test fun creditConfigurationIsExplicitAtomicAndSharedAcrossMessages() = BetaOwnershipChecks.creditConfigurationIsExplicitAtomicAndSharedAcrossMessages(context)
    @Test fun conflictingKindsStayInReviewWithoutMergingOrBlockingOtherAccounts() = BetaOwnershipChecks.conflictingKindsStayInReviewWithoutMergingOrBlockingOtherAccounts(context)
}
