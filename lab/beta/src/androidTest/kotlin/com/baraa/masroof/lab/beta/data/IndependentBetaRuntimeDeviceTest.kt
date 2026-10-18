package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baraa.masroof.lab.beta.verification.IndependentEngineChecks
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IndependentBetaRuntimeDeviceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Test fun currentOnlySurvivesRestore() = IndependentEngineChecks.currentOnlyAndDurable(context)
    @Test fun unsafeCannotPost() = IndependentEngineChecks.incompleteAndSecurityNeverPost(context)
    @Test fun historyStaysInert() = IndependentEngineChecks.historicalRowsRemainInert(context)
}
