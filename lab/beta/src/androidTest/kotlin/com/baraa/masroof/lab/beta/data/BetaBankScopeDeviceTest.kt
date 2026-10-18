package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.verification.BetaBankScopeChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BetaBankScopeDeviceTest {
    @Test fun pauseResumeAndRestoreKeepHistory() = BetaBankScopeChecks.pauseResumeAndRestoreKeepHistory(ApplicationProvider.getApplicationContext<Context>())
}
