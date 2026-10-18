package com.baraa.masroof.lab.beta

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.verification.BetaOnboardingChecks
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class BetaOnboardingTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Test fun progressPersistsWithoutReadingOrPosting() = BetaOnboardingChecks.progressPersistsWithoutReadingOrPosting(context)
    @Test fun existingAcknowledgementDoesNotRestartSetup() = BetaOnboardingChecks.existingAcknowledgementDoesNotRestartSetup(context)
}
