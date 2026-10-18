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
        assertEquals(TrendScope.PURCHASES, workflow.readHomeScope())
        workflow.saveHomeScope(TrendScope.OVERALL)
        workflow.save(chosen)
        assertEquals(chosen, BetaDashboardViewPreferencesWorkflow(context).read())
        val prefs = context.getSharedPreferences(BetaEnvironment.PREFS_NAME, Context.MODE_PRIVATE)
        assertTrue(prefs.all.values.all { it is String })
        BetaContainer(context).use { beta ->
            beta.putMarker("synthetic-view-preferences", "isolated backup fixture")
            beta.backup(); workflow.save(BetaDashboardViewPreferences()); workflow.saveHomeScope(TrendScope.PURCHASES); assertTrue(beta.restore())
        }
        assertEquals(chosen, BetaDashboardViewPreferencesWorkflow(context).read())
        assertEquals(TrendScope.OVERALL, workflow.readHomeScope())
        prefs.edit().putString("dashboard_view_scope", "REMOVED_ENUM").commit()
        assertEquals(BetaDashboardViewPreferences(), workflow.read())
        BetaContainer.resetOffline(context)
    }
    @Test fun homeLayoutIsAtomicAndBackedUp(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        try {
            val workflow = BetaDashboardViewPreferencesWorkflow(context)
            val original = workflow.readHomeLayout()
            val draft = original.toggle(BetaHomeSection.HERO).move(BetaHomeSection.TRANSACTIONS, -1).copy(averageVisible = false)
            assertEquals(original, workflow.readHomeLayout()) // An unsaved draft never changes stored state.
            assertTrue(workflow.saveHomeLayout(original, draft))
            assertEquals(draft, BetaDashboardViewPreferencesWorkflow(context).readHomeLayout())
            assertFalse(workflow.saveHomeLayout(original, original)) // Another open editor is stale.
            BetaContainer(context).use { beta ->
                beta.putMarker("home-layout", "retained"); beta.backup()
                assertTrue(workflow.saveHomeLayout(draft, original)); assertTrue(beta.restore())
            }
            assertEquals(draft, workflow.readHomeLayout())
            context.getSharedPreferences(BetaEnvironment.PREFS_NAME, Context.MODE_PRIVATE).edit().putString("dashboard_home_layout", "unknown-layout").commit()
            assertEquals(BetaHomeLayout(), workflow.readHomeLayout())
            assertTrue(workflow.saveHomeLayout(BetaHomeLayout(), draft)) // Explicit Apply repairs only display preferences.
            assertEquals(BetaHomeSection.entries.size, draft.sections.map { it.section }.distinct().size)
            assertEquals(original, BetaHomeLayout.decode(original.encode()))
            assertThrows(IllegalArgumentException::class.java) { BetaHomeLayout.decode("v1|true|true|HERO:true") }
        } finally { BetaContainer.resetOffline(context) }
    }

}
