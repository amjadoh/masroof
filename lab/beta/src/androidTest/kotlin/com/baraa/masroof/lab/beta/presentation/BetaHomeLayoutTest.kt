package com.baraa.masroof.lab.beta.presentation

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.application.*
import com.baraa.masroof.ui.theme.MasroofTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BetaHomeLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dismissDiscardsAndApplyPersistsOnlyTheChosenDraft() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        val workflow = BetaDashboardViewPreferencesWorkflow(context)
        var open by mutableStateOf(true)
        var saved by mutableStateOf(BetaHomeLayout())
        compose.setContent { MasroofTheme { if (open) BetaHomeLayoutSheet(saved, workflow, { open = false }, { saved = it; open = false }) } }
        fun toggleHero() {
            compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("home-layout-HERO"))).performClick()
        }
        try {
            toggleHero()
            compose.onNodeWithTag("dash-sheet-cancel").performClick()
            compose.waitUntil { !open }
            assertEquals(BetaHomeLayout(), runBlocking { workflow.readHomeLayout() })
            compose.runOnIdle { open = true }
            toggleHero()
            compose.onNodeWithTag("dash-sheet-apply").performClick()
            compose.waitUntil(10000) { !open }
            assertFalse(runBlocking { workflow.readHomeLayout() }.sections.single { it.section == BetaHomeSection.HERO }.visible)
            assertEquals(saved, runBlocking { workflow.readHomeLayout() })
        } finally { BetaContainer.resetOffline(context) }
    }
}
