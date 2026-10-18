package com.baraa.masroof.lab.beta

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.verification.BetaLearningChecks
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)

class BetaLearningTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Test fun labelsSurvive() = BetaLearningChecks.labelsSurvive(context)
    @Test fun reparseDoesNotPromote() = BetaLearningChecks.reparseDoesNotPromote(context)
    @Test fun unsafeLabelsRejected() = BetaLearningChecks.unsafeLabelsRejected(context)
    @Test fun expandedRolesKeepEvidenceAndMoneyIndependent() = BetaLearningChecks.expandedRolesKeepEvidenceAndMoneyIndependent(context)

}
