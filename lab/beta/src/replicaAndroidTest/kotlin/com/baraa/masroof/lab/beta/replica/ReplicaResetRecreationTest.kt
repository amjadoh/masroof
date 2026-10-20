package com.baraa.masroof.lab.beta.replica

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.replica.MainActivity
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReplicaResetRecreationTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun activityRecreationWaitsForResetAndOpensFreshWelcome()=runBlocking<Unit> {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val host=instrumentation.targetContext
        LabResetWorkflow.reset(host)
        val c=LabRuntime.container(host);c.awaitPostStartupBackgroundWorkForTesting()
        c.appLocaleRepository.setLanguageTag("en")
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val lease=scope.launch {c.databaseAccessGate.withAccess {entered.complete(Unit);release.await()}}
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.waitUntil(15_000) {compose.onAllNodesWithText("LAB").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
            entered.await()
            val reset=scope.async {LabResetWorkflow.reset(host)}
            withTimeout(10_000) {while(!LabRuntime.isResetting) delay(10)}
            scenario.onActivity {it.recreate()}
            withTimeout(15_000) {
                var waiting=false
                while(!waiting) {
                    instrumentation.runOnMainSync {waiting=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any {it is LabLaunchActivity}}
                    if(!waiting) delay(20)
                }
            }
            release.complete(Unit);lease.join();reset.await()
            val scoped=LabStorageContext(host)
            val localeContext=com.baraa.masroof.lab.replica.presentation.locale.AppLocaleContext.wrap(scoped,com.baraa.masroof.lab.replica.presentation.locale.AppLocaleContext.readStoredLanguageTag(scoped))
            compose.waitUntil(20_000) {compose.onAllNodesWithText(localeContext.getString(R.string.lab_replica_onboarding_welcome_title)).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
            assertFalse(LabRuntime.isResetting)
            assertTrue(LabRuntime.container(host).financialTransactionRepository.listAll().isEmpty())
        } finally {
            release.complete(Unit);scope.cancel();scenario.close()
            instrumentation.runOnMainSync {ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().filter {it is MainActivity || it is LabLaunchActivity}.forEach {it.finish()}}
            LabResetWorkflow.reset(host)
        }
    }
}
