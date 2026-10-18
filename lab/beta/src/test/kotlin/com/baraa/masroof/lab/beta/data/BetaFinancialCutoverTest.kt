package com.baraa.masroof.lab.beta.data

import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.verification.BetaFinancialCutoverChecks
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(org.robolectric.RobolectricTestRunner::class) @org.robolectric.annotation.Config(sdk = [28])
class BetaFinancialCutoverTest {
    @Test fun currentFamiliesPostExactlyOnce() = BetaFinancialCutoverChecks.familiesAndIdempotence(ApplicationProvider.getApplicationContext())
    @Test fun unsafeRecordsAndBrokenOriginalProofStayReview() = BetaFinancialCutoverChecks.unsafeAndOriginalProof(ApplicationProvider.getApplicationContext())
    @Test fun currentFactsInvalidateOldIntentAndLegacyCannotAuthorize() = BetaFinancialCutoverChecks.currentArchiveAndLegacyPendingBinding(ApplicationProvider.getApplicationContext())
}
