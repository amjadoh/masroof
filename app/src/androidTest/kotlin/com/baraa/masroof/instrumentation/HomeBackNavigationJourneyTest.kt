package com.baraa.masroof.instrumentation

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.MainActivity
import com.baraa.masroof.R
import com.baraa.masroof.application.locale.AppLocalePreferences
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.sms.hash.SmsBodyHasher
import com.baraa.masroof.testsupport.TestRuntime
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Android Back always returns to the previous visit, whatever opened the current screen. */
class HomeBackNavigationJourneyTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val seed = object : TestRule {
        override fun apply(base: Statement, description: Description) = object : Statement() {
            override fun evaluate() {
                val host = InstrumentationRegistry.getInstrumentation().targetContext
                runBlocking {
                    TestRuntime.reset(host)
                    host.getSharedPreferences(AppLocalePreferences.PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putString(AppLocalePreferences.KEY_LANGUAGE_TAG, "en").commit()
                    val container = TestRuntime.container(host)
                    container.awaitPostStartupBackgroundWorkForTesting()
                    container.appLocaleRepository.setLanguageTag("en")
                    container.onboardingPreferencesRepository.setOnboardingStarted(true)
                    container.onboardingPreferencesRepository.setOnboardingCompleted(true)
                    container.onboardingOwnershipWorkflow.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
                    repeat(3) { index ->
                        val at = LocalDateTime.now().minusMinutes(index + 1L).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        val body = "شراء من نقاط البيع\nبطاقة مدى: 2210\nخصمت من حساب: 3001\nلدى: Back Nav Shop ${'A' + index}\nبمبلغ: 12.00 SAR\nفي: $at"
                        val id = "back-nav-$index"
                        container.processRawSmsUseCase.ingest(RawSms(id, "AlJazira", body, Instant.now(), id, SmsBodyHasher.sha256Hex(body)))
                    }
                }
                try { base.evaluate() } finally { TestRuntime.close() }
            }
        }
    }

    @get:Rule val rule: TestRule = RuleChain.outerRule(seed).around(compose)

    private val patternsScreen = hasTestTag("pattern-open-${Bank.BANK_ALJAZIRA.id}-bank.purchase")

    @Test fun patternsThenNotificationsOrTab_backReturnsToPatterns() {
        awaitText(text(R.string.reference_shortcut_accounts))
        openPatterns()

        openNotifications()
        pressDeviceBack(compose)
        awaitNode(patternsScreen)

        // Another bottom tab is history too; back returns to the patterns screen, not Home.
        clickText(text(R.string.approved_nav_review))
        compose.onNode(patternsScreen).assertDoesNotExist()
        pressDeviceBack(compose)
        awaitNode(patternsScreen)

        // Home clears history, and Back on Home never closes the app.
        clickText(text(R.string.approved_nav_home))
        awaitText(text(R.string.reference_shortcut_accounts))
        pressDeviceBack(compose)
        compose.onNodeWithText(text(R.string.reference_shortcut_accounts)).assertIsDisplayed()
        assertFalse(compose.activity.isFinishing)
    }

    @Test fun transactionDetailThenNotifications_backReturnsToSameDetailThenUnwinds() {
        awaitText(text(R.string.reference_shortcut_accounts))
        clickText(text(R.string.reference_shortcut_accounts))
        clickText("3001", substring = true)
        val accountDetail = hasText(text(R.string.reference_account_detail_title)) and !hasClickAction()
        awaitNode(accountDetail)
        clickText("Back Nav Shop A")
        awaitText(text(R.string.transaction_detail_title))

        openNotifications()
        pressDeviceBack(compose)
        awaitText(text(R.string.transaction_detail_title))

        pressDeviceBack(compose)
        awaitNode(accountDetail)
        pressDeviceBack(compose)
        compose.onNodeWithText(text(R.string.dashboard_accounts_summary_screen_title)).assertIsDisplayed()
        pressDeviceBack(compose)
        awaitText(text(R.string.reference_shortcut_accounts))
    }

    private fun openPatterns() {
        clickText(text(R.string.approved_nav_settings))
        clickText(text(R.string.settings_banks_title))
        // The row shows the catalog display name, which stays Arabic in every locale.
        val bankRow = (hasText("بنك الجزيرة") or hasText(text(R.string.bank_aljazira))) and hasClickAction()
        awaitNode(bankRow)
        compose.onAllNodes(bankRow).onFirst().performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        clickText(text(R.string.settings_bank_teaching))
        awaitNode(patternsScreen)
    }

    private fun openNotifications() {
        compose.onNode(hasContentDescription("unread notification", substring = true) or
            hasContentDescription(text(R.string.notification_center_title))).performClick()
        awaitText(text(R.string.notification_center_title))
    }

    private fun text(id: Int): String = compose.activity.getString(id)

    private fun awaitNode(matcher: SemanticsMatcher) {
        compose.waitUntil(30_000) { compose.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }

    private fun awaitText(value: String) = awaitNode(hasText(value))

    private fun clickText(value: String, substring: Boolean = false) {
        val target = hasText(value, substring = substring) and hasClickAction()
        awaitNode(target)
        compose.onAllNodes(target).onFirst().performScrollTo().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
    }
}
