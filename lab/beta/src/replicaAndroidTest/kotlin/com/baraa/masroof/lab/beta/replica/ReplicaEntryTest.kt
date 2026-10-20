package com.baraa.masroof.lab.beta.replica

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baraa.masroof.lab.replica.MainActivity
import com.baraa.masroof.lab.beta.R
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReplicaEntryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun resetErasesLabAndPreservesHostDatabasePreferencesAndFiles() {
        val host = InstrumentationRegistry.getInstrumentation().targetContext
        val scoped = LabStorageContext(host)
        compose.waitUntil(30_000) { compose.onAllNodesWithText("LAB").fetchSemanticsNodes().isNotEmpty() }
        // Synthetic host sentinels, never the user's installed production package.
        val db = host.openOrCreateDatabase("masroof.db",0,null)
        db.execSQL("CREATE TABLE IF NOT EXISTS isolation_sentinel (value TEXT)")
        db.execSQL("INSERT INTO isolation_sentinel VALUES ('production')")
        db.close()
        val before = host.getDatabasePath("masroof.db").readBytes()
        host.getSharedPreferences("isolation_sentinel",0).edit().putString("value","production").commit()
        scoped.getSharedPreferences("isolation_sentinel",0).edit().putString("value","lab").commit()
        val file = java.io.File(scoped.filesDir,"sentinel").apply { writeText("lab") }
        compose.activityRule.scenario.close()
        runBlocking { LabResetWorkflow.reset(host) }
        assertArrayEquals(before,host.getDatabasePath("masroof.db").readBytes())
        assertEquals("production",host.getSharedPreferences("isolation_sentinel",0).getString("value",null))
        assertNull(scoped.getSharedPreferences("isolation_sentinel",0).getString("value",null))
        assertFalse(scoped.getDatabasePath("masroof.db").exists())
        assertFalse(file.exists())
    }
    @Test fun opensTheProductionWelcomeScreen() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(compose.activity.getString(R.string.lab_replica_onboarding_welcome_title)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(compose.activity.getString(R.string.lab_replica_onboarding_welcome_title)).assertIsDisplayed()
        compose.onNodeWithText("LAB").assertIsDisplayed()
    }
}
