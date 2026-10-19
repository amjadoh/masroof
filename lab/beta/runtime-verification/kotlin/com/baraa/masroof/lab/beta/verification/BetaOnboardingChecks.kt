package com.baraa.masroof.lab.beta.verification

import android.content.Context
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.application.*
import com.baraa.masroof.lab.finance.value.BankId
import org.junit.Assert.*

object BetaOnboardingChecks {
    fun progressPersistsWithoutReadingOrPosting(context: Context) {
        BetaContainer.resetOffline(context)
        try {
            BetaContainer(context).use { beta ->
                assertEquals(BetaStartState.NEW, beta.startState())
                beta.beginOnboarding()
                assertEquals(BetaOnboardingStep.BANK, beta.onboardingStep())
                assertThrows(IllegalStateException::class.java) { beta.advanceOnboarding(BetaOnboardingStep.BANK) }
                beta.setBankEnabled(BankId("BANK_ALJAZIRA"), true)
                beta.advanceOnboarding(BetaOnboardingStep.BANK)
                beta.advanceOnboarding(BetaOnboardingStep.BANK) // Same committed action is idempotent.
                assertEquals(BetaOnboardingStep.IMPORT, beta.onboardingStep())
                assertNull(beta.historySession()); assertTrue(beta.evidencePage().isEmpty()); assertTrue(beta.assets().isEmpty())
                beta.backup()
                beta.advanceOnboarding(BetaOnboardingStep.IMPORT)
                assertEquals(BetaOnboardingStep.ASSETS, beta.onboardingStep())
                assertTrue(beta.restore())
                assertEquals(BetaOnboardingStep.IMPORT, beta.onboardingStep())
            }
            BetaContainer(context).use { beta ->
                assertEquals(BetaOnboardingStep.IMPORT, beta.onboardingStep())
                beta.advanceOnboarding(BetaOnboardingStep.IMPORT)
                beta.advanceOnboarding(BetaOnboardingStep.ASSETS)
                beta.advanceOnboarding(BetaOnboardingStep.REVIEW)
                assertEquals(BetaOnboardingStep.COMPLETE, beta.onboardingStep())
                assertNull(beta.historySession()); assertTrue(beta.assets().isEmpty()); assertTrue(beta.evidencePage().isEmpty())
            }
        } finally { BetaContainer.resetOffline(context) }
    }

    fun existingAcknowledgementDoesNotRestartSetup(context: Context) {
        BetaContainer.resetOffline(context)
        try {
            BetaContainer(context).use { beta ->
                beta.putMarker("retained", "existing Beta data")
                beta.acknowledgeStart()
            }
            BetaContainer(context).use { beta ->
                assertEquals(BetaOnboardingStep.COMPLETE, beta.onboardingStep())
                assertEquals(1, beta.markerCount())
                assertThrows(IllegalStateException::class.java) { beta.beginOnboarding() }
                assertEquals(1, beta.markerCount())
                beta.resumeOnboarding()
                assertEquals(BetaOnboardingStep.BANK, beta.onboardingStep())
                assertEquals(1, beta.markerCount())
                beta.setBankEnabled(BankId("BANK_ALJAZIRA"), true)
                beta.advanceOnboarding(BetaOnboardingStep.BANK)
                beta.resumeOnboarding() // In-progress setup is never rewound.
                assertEquals(BetaOnboardingStep.IMPORT, beta.onboardingStep())
                beta.advanceOnboarding(BetaOnboardingStep.IMPORT)
                beta.advanceOnboarding(BetaOnboardingStep.ASSETS)
                beta.advanceOnboarding(BetaOnboardingStep.REVIEW)
                beta.resumeOnboarding()
                assertEquals(BetaOnboardingStep.IMPORT, beta.onboardingStep())
                assertEquals(1, beta.markerCount())
                assertNull(beta.historySession())
                assertTrue(beta.evidencePage().isEmpty())
            }
            context.getSharedPreferences(BetaEnvironment.PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString("beta_onboarding_v1", "FUTURE_STEP").commit()
            BetaContainer(context).use { beta ->
                assertThrows(IllegalArgumentException::class.java) { beta.onboardingStep() }
                assertEquals(1, beta.markerCount())
            }
        } finally { BetaContainer.resetOffline(context) }
    }
}
