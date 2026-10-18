package com.baraa.masroof.lab.beta.interpretation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.verification.IndependentEngineChecks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class IndependentBetaRuntimeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Test fun currentOnlySurvivesRestore() = IndependentEngineChecks.currentOnlyAndDurable(context)
    @Test fun unsafeCannotPost() = IndependentEngineChecks.incompleteAndSecurityNeverPost(context)
    @Test fun historyStaysInert() = IndependentEngineChecks.historicalRowsRemainInert(context)
}
