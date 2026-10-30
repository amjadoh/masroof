package com.baraa.masroof.instrumentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baraa.masroof.application.transaction.LoanPaymentPreview
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.LoanType
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.engine.bank.patterns.ConfirmedLoanPayment
import com.baraa.masroof.presentation.locale.AppLocaleContext
import com.baraa.masroof.presentation.settings.*
import com.baraa.masroof.ui.theme.MasroofTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoanPaymentSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun linkingRequiresPreviewAndConfirmationAndPreservesLeadingZeros() {
        val loan = ManagedLoanUi("test-loan", Bank("BANK_EMIRATES_NBD"), LoanType.PERSONAL, OwnershipStatus.OWNED, "Test loan")
        val state = mutableStateOf(SettingsUiState(loading = false, loanPaymentTarget = loan))
        var previewed: String? = null
        var confirmed = false
        compose.setContent {
            val localized = AppLocaleContext.wrap(LocalContext.current, "en")
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration) {
                MasroofTheme {
                    SettingsLoanPaymentSheet(state.value, {}, { code ->
                        previewed = code
                        state.value = state.value.copy(loanPaymentPreview = LoanPaymentPreview(0,
                            ConfirmedLoanPayment(code, loan.bank.id, loan.loanType.name), emptyList(), emptyList(), 0))
                    }, { confirmed = true }, { state.value = state.value.copy(loanPaymentPreview = null) })
                }
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("009")
        compose.onNodeWithTag("sheet-apply").performClick()
        compose.runOnIdle { assertEquals("009", previewed); assertFalse(confirmed) }
        compose.onNodeWithText("Test loan", substring = true).assertExists()
        compose.onNodeWithTag("sheet-apply").performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }
}
