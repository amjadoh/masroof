package com.baraa.masroof.instrumentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.presentation.locale.AppLocaleContext
import com.baraa.masroof.presentation.settings.*
import com.baraa.masroof.ui.theme.MasroofTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalAccountSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun anotherHoldersAccountCanBeRenamedAndOpenedWithoutChangingOwnership() {
        val account = ManagedAccountUi("external-test", Bank("BANK_SNB"), "2345", OwnershipStatus.EXTERNAL, "Family member")
        val state = mutableStateOf(SettingsUiState(loading = false, stoppedAccounts = listOf(account)))
        var opened: ManagedAccountUi? = null
        var ownershipChanged = false
        compose.setContent {
            val localized = AppLocaleContext.wrap(LocalContext.current, "en")
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration) {
                MasroofTheme {
                    SettingsBankAccountsScreen(account.bank, state.value, {},
                        onConfirmOwned = { ownershipChanged = true }, onMarkExternal = {}, onRequestStopTracking = {},
                        onResumeTracking = { ownershipChanged = true }, onDismissStopConfirm = {}, onConfirmStopTracking = {},
                        onRenameAccount = { state.value = state.value.copy(renameAccountTarget = it) },
                        onDismissRenameAccount = { state.value = state.value.copy(renameAccountTarget = null) },
                        onSaveAccountName = { name -> state.value = state.value.copy(
                            stoppedAccounts = listOf(account.copy(displayName = name)), renameAccountTarget = null,
                        ) },
                        onPickAccountType = {}, onDismissAccountType = {}, onSelectAccountType = {},
                        onViewExternalSpending = { opened = it },
                    )
                }
            }
        }
        fun label(id: Int) = AppLocaleContext.wrap(compose.activity, "en").getString(id)
        compose.onNodeWithText(label(R.string.settings_external_accounts)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.settings_external_account_is_mine)).assertExists()
        compose.onNodeWithText(label(R.string.settings_action_rename)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("اسم اختياري")
        compose.onNodeWithText(label(R.string.settings_save)).performClick()
        compose.onNodeWithText("اسم اختياري", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(label(R.string.settings_external_spending)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("اسم اختياري", opened?.displayName)
            assertEquals(OwnershipStatus.EXTERNAL, opened?.ownership)
            assertFalse(ownershipChanged)
        }
    }
}
