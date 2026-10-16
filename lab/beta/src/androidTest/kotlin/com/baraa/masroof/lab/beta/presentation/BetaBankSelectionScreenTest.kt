package com.baraa.masroof.lab.beta.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.baraa.masroof.lab.beta.application.*
import com.baraa.masroof.lab.finance.value.BankId
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BetaBankSelectionScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun addPauseResumeKeepsBetaBadgeAndReturnAvailable() {
        var returned = false
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            var state by remember { mutableStateOf<BankSelectionState?>(null) }
            MaterialTheme {
                BetaShell(0, false, listOf(BetaBankChoice(BankId("BANK_ALJAZIRA"), state)),
                    onSetBankEnabled = { _, enabled -> changes += enabled; state = if (enabled) BankSelectionState.ENABLED else BankSelectionState.DISABLED },
                    onReturn = { returned = true }, onReset = {}, onBackup = {}, onRestore = {})
            }
        }
        compose.onNodeWithTag("beta-badge").assertIsDisplayed()
        compose.onNodeWithTag("beta-return").assertIsDisplayed()
        repeat(3) {
            compose.onNodeWithTag("beta-bank-action-BANK_ALJAZIRA").performScrollTo().performClick()
            compose.onNodeWithTag("beta-badge").assertIsDisplayed()
            compose.onNodeWithTag("beta-return").assertIsDisplayed()
        }
        assertEquals(listOf(true, false, true), changes)
        compose.onNodeWithTag("beta-return").performClick()
        assertTrue(returned)
    }
    @Test fun maintenanceAndSelectionAreDisabledWhileAnActionIsRunning() {
        compose.setContent {
            MaterialTheme {
                BetaShell(0, false, listOf(BetaBankChoice(BankId("BANK_ALJAZIRA"), BankSelectionState.ENABLED)),
                    busy = true, onReturn = {}, onReset = {}, onBackup = {}, onRestore = {})
            }
        }
        listOf("beta-bank-action-BANK_ALJAZIRA", "beta-backup", "beta-restore", "beta-reset").forEach {
            compose.onNodeWithTag(it).performScrollTo().assertIsNotEnabled()
        }
        compose.onNodeWithTag("beta-return").assertIsDisplayed().assertIsEnabled()
    }
}
