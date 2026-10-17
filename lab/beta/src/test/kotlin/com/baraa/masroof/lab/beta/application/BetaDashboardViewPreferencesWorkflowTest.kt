package com.baraa.masroof.lab.beta.application

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.finance.asset.AssetId
import com.baraa.masroof.lab.finance.dashboard.TrendScope
import com.baraa.masroof.lab.finance.value.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class BetaDashboardViewPreferencesWorkflowTest {
    // Keep the Robolectric method component short: nested snapshot paths also reach native SQLite.
    @Test fun preferencesRestore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        val chosen = BetaDashboardViewPreferences(BetaChartChoice(TrendScope.ASSET, asset = AssetId(Environment.BETA, BankId("BANK_ALJAZIRA"), "account-a")), false)
        val workflow = BetaDashboardViewPreferencesWorkflow(context)
        workflow.save(chosen)
        assertEquals(chosen, BetaDashboardViewPreferencesWorkflow(context).read())
        val prefs = context.getSharedPreferences(BetaEnvironment.PREFS_NAME, Context.MODE_PRIVATE)
        assertTrue(prefs.all.values.all { it is String })
        BetaContainer(context).use { beta ->
            beta.putMarker("synthetic-view-preferences", "isolated backup fixture")
            beta.backup(); workflow.save(BetaDashboardViewPreferences()); assertTrue(beta.restore())
        }
        assertEquals(chosen, BetaDashboardViewPreferencesWorkflow(context).read())
        prefs.edit().putString("dashboard_view_scope", "REMOVED_ENUM").commit()
        assertEquals(BetaDashboardViewPreferences(), workflow.read())
        BetaContainer.resetOffline(context)
    }
}
