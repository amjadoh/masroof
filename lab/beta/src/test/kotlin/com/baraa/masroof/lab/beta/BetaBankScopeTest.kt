package com.baraa.masroof.lab.beta

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.verification.BetaBankScopeChecks
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class BetaBankScopeTest {
    @Test fun pauseResumeAndRestoreKeepHistory() = BetaBankScopeChecks.pauseResumeAndRestoreKeepHistory(ApplicationProvider.getApplicationContext<Context>())
}
