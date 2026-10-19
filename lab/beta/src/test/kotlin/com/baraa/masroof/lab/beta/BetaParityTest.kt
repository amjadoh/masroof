package com.baraa.masroof.lab.beta

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.verification.BetaParityChecks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class BetaParityTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Test fun freshSchema()=BetaParityChecks.freshSchemaHasSixteenTablesAndQueryableFinancialFacts(context)
    @Test fun cardsOnlySetup()=BetaParityChecks.ownedCardsCanFinishSetupAndCreateOnlyOneConfigurationTask(context)
    @Test fun liveAndHistory()=BetaParityChecks.liveThenHistoricalReceiptHasOneIdentityAndOnePosting(context)
    @Test fun scopedHistory()=BetaParityChecks.oldHistoryDoesNotConsumeTheSelectedPeriodReadBudget(context)
}
