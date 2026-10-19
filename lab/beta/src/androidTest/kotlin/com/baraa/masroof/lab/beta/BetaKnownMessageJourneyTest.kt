package com.baraa.masroof.lab.beta

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.verification.BetaKnownMessageJourneyChecks
import org.junit.Test

class BetaKnownMessageJourneyTest {
    private fun verify(block: (BetaKnownMessageJourneyChecks)->Unit) =
        BetaKnownMessageJourneyChecks(ApplicationProvider.getApplicationContext<Context>()).use(block)
    @Test fun capturedUnprocessedEvidenceIsOperationalWorkRatherThanHumanReview() = verify {it.capturedUnprocessedEvidenceIsOperationalWorkRatherThanHumanReview()}
    @Test fun clearExternalTransfersAndSalaryReachAccountAndDashboardWithoutApproval() = verify {it.clearExternalTransfersAndSalaryReachAccountAndDashboardWithoutApproval()}
    @Test fun aPendingUnknownMessageDoesNotHideConfirmedAccountMovements() = verify {it.aPendingUnknownMessageDoesNotHideConfirmedAccountMovements()}
    @Test fun debitAccountLinkAndSpendingAreRecoveredFromOwnedStructuredRoles() = verify {it.debitAccountLinkAndSpendingAreRecoveredFromOwnedStructuredRoles()}
    @Test fun complementaryOwnedTransferNoticesProduceOneStableSelfTransfer() = verify {it.complementaryOwnedTransferNoticesProduceOneStableSelfTransfer()}
    @Test fun extraSameDirectionTransferIsAmbiguousAndNeverDoubleCounts() = verify {it.extraSameDirectionTransferIsAmbiguousAndNeverDoubleCounts()}
    @Test fun embeddedLiveIntakeRequiresExplicitLabInitializationAndResetDisablesIt() = verify {it.embeddedLiveIntakeRequiresExplicitLabInitializationAndResetDisablesIt()}
    @Test fun ignoringUnknownEvidenceIsExplicitReversibleAndDoesNotTeachTheEngine() = verify {it.ignoringUnknownEvidenceIsExplicitReversibleAndDoesNotTeachTheEngine()}
    @Test fun ambiguousCounterpartyCannotBecomeAnExternalTransfer() = verify {it.ambiguousCounterpartyCannotBecomeAnExternalTransfer()}
}
