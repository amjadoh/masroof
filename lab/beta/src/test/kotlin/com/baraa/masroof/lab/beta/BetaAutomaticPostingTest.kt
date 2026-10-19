package com.baraa.masroof.lab.beta

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.verification.BetaAutomaticPostingChecks
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28])
class BetaAutomaticPostingTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Test fun confirmedOwnershipPostsOnceAcrossReopenAndNewCapture() = BetaAutomaticPostingChecks.confirmedOwnershipPostsOnceAcrossReopenAndNewCapture(context)
    @Test fun ambiguousOwnershipAndUnsafeMessagesStayUnposted() = BetaAutomaticPostingChecks.ambiguousOwnershipAndUnsafeMessagesStayUnposted(context)
    @Test fun explicitManualChoiceWinsAndNeverBecomesAutomatic() = BetaAutomaticPostingChecks.explicitManualChoiceWinsAndNeverBecomesAutomatic(context)
    @Test fun pendingManualRequestRemainsVisibleAndBlocksAutomaticPosting() = BetaAutomaticPostingChecks.pendingManualRequestRemainsVisibleAndBlocksAutomaticPosting(context)
    @Test fun duplicateCapturedMessagesStayInReview() = BetaAutomaticPostingChecks.duplicateCapturedMessagesStayInReview(context)
    @Test fun interruptedSweepRemainsRetryableAndAtomic() = BetaAutomaticPostingChecks.interruptedSweepRemainsRetryableAndAtomic(context)
}
