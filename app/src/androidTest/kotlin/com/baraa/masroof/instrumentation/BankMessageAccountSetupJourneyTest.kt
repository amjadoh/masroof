package com.baraa.masroof.instrumentation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baraa.masroof.R
import com.baraa.masroof.application.AppContainer
import com.baraa.masroof.domain.model.*
import com.baraa.masroof.presentation.locale.AppLocaleContext
import com.baraa.masroof.presentation.patterns.BankMessageAccountSetupCard
import com.baraa.masroof.sms.hash.SmsBodyHasher
import com.baraa.masroof.testsupport.TestStorageContext
import com.baraa.masroof.testsupport.ScreenshotArtifactWriter
import com.baraa.masroof.ui.theme.MasroofTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class BankMessageAccountSetupJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun ar(id: Int) = AppLocaleContext.wrap(compose.activity, "ar").getString(id)
    private fun message(id: String, sender: String, body: String) = RawSms(id, sender, body,
        Instant.parse("2026-09-15T09:30:00Z"), id, SmsBodyHasher.sha256Hex(body))

    @Test fun cancelDoesNotBindAndSavedChoicePostsPendingAndFutureAmountsExactlyOnce() = runBlocking {
        val context = TestStorageContext(compose.activity, "barq-setup-${java.util.UUID.randomUUID()}")
        val c = AppContainer(context)
        try {
            val w = c.messagePatternsWorkflow
            val account = w.confirmPatternAccount("BANK_BARQ", "1234")
            fun sms(id: String, amount: String) = message(id, "barq app",
                "شراء نقاط البيع\nمدى$amount SAR\nالرصيد 900\nلدى TEST SHOP\n2026-09-15 12:30")
            c.processRawSmsUseCase.ingest(sms("first", "10"))
            c.processRawSmsUseCase.ingest(sms("second", "20"))
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
            val before = w.load().generation
            val generation = mutableStateOf(before)
            val dark = mutableStateOf(false)
            compose.setContent {
                val localized = AppLocaleContext.wrap(LocalContext.current, "ar")
                CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration,
                    androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                    MasroofTheme(darkTheme = dark.value) { Surface {
                        BankMessageAccountSetupCard(w, "BANK_BARQ", "برق", generation.value,
                            { generation.value = runBlocking { w.load().generation } })
                    } }
                }
            }
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("bank-message-setup-BANK_BARQ").assertIsEnabled() }.isSuccess }
            compose.onNodeWithTag("bank-message-setup-BANK_BARQ").performClick()
            compose.onNodeWithTag("bank-message-account-${account.id}").performScrollTo().performClick()
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            assertEquals(before, w.load().generation)
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
            compose.onNodeWithTag("bank-message-setup-BANK_BARQ").performClick()
            compose.onNodeWithTag("bank-message-account-${account.id}").assertIsOff()
            compose.onNodeWithTag("bank-message-account-${account.id}").performScrollTo().performClick()
            compose.waitForIdle()
            ScreenshotArtifactWriter.publish(compose.onRoot().captureToImage().asAndroidBitmap(), "bank-account", "arabic-light")
            compose.runOnIdle { dark.value = true }
            ScreenshotArtifactWriter.publish(compose.onRoot().captureToImage().asAndroidBitmap(), "bank-account", "arabic-dark")
            compose.onNodeWithText(ar(R.string.bank_message_account_save)).performClick()
            compose.waitUntil(20_000) { runBlocking { c.financialTransactionRepository.listAll().size == 2 } }
            c.processRawSmsUseCase.ingest(sms("future", "40"))
            c.processRawSmsUseCase.ingest(sms("future", "40"))
            val ledger = c.financialTransactionRepository.listAll()
            assertEquals(3, ledger.size)
            assertTrue(ledger.all { it.type == FinancialTransactionType.EXPENSE && it.sourceContainerId?.contains("1234") == true })
            assertEquals(0, "70".toBigDecimal().compareTo(ledger.sumOf { it.amount.amount }))
        } finally { c.close(); context.getDatabasePath("masroof.db").parentFile?.deleteRecursively() }
    }

    @Test fun confirmedPersonalLoanReadsInstallmentAndBalanceWithoutPostingReminders() = runBlocking {
        val context = TestStorageContext(compose.activity, "enbd-setup-${java.util.UUID.randomUUID()}")
        val c = AppContainer(context)
        try {
            val w = c.messagePatternsWorkflow
            val account = w.confirmPatternAccount("BANK_EMIRATES_NBD", "1234")
            val installment = message("installment", "EmiratesNBD", "خصم قسط تمويل\nPersonal Finance : نوع التمويل\n125 : القسط المستحق\n1500 : اجمالي المبلغ المتبقي\nXX12 : رقم الحساب\n2026-09-15 : التاريخ\n12:30 : الوقت")
            c.processRawSmsUseCase.ingest(installment)
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
            w.configureBankAccount(w.load().generation, "BANK_EMIRATES_NBD", account.id, "SAR", true)
            val ledger = c.financialTransactionRepository.listAll()
            assertEquals(1, ledger.size)
            assertEquals(FinancialTransactionType.LOAN_REPAYMENT, ledger.single().type)
            assertEquals("125.00", ledger.single().amount.amount.toPlainString())
            val parsed = requireNotNull(c.parsedEventRepository.findByRawSmsId("installment"))
            assertEquals("1500.00", parsed.details.outstandingBalance!!.amount.toPlainString())
            c.processRawSmsUseCase.ingest(message("reminder", "EmiratesNBD", "عزيزنا العميل، نود تذكيركم بإستحقاق قسط تمويلكم، يرجى تجاهل هذه الرسالة إذا كنت قد سددت بالفعل."))
            c.processStoredSmsUseCase.process("installment")
            assertEquals(ledger, c.financialTransactionRepository.listAll())
        } finally { c.close(); context.getDatabasePath("masroof.db").parentFile?.deleteRecursively() }
    }

    @Test fun crossBankTransferDebitsSourceAndCreditsDestinationExactlyOnceAfterRetryAndReopen() = runBlocking {
        val context = TestStorageContext(compose.activity, "self-transfer-${java.util.UUID.randomUUID()}")
        var c = AppContainer(context)
        try {
            val w = c.messagePatternsWorkflow
            w.confirmPatternAccount("BANK_SNB", "5678")
            val destination = w.confirmPatternAccount("BANK_BARQ", "1234")
            w.configureBankAccount(w.load().generation, "BANK_BARQ", destination.id, "SAR", false)
            val outgoing = message("owned-out", "SNB-AlAhli",
                "حوالة صادرة محلية\nمن:*5678\nإلى:*1234\nعبر:برق\nمبلغ:100 SAR\nفي:2026-09-15 12:30")
            val incoming = message("owned-in", "barq app",
                "حوالة واردة محلية\n100SAR\nمن TEST PERSON\nبنك:NCBKSARI\n2026-09-15 12:30")
            suspend fun assertAccountEffects() {
                val ledger = c.financialTransactionRepository.listAll()
                assertEquals(1, ledger.size)
                assertEquals(FinancialTransactionType.SELF_TRANSFER, ledger.single().type)
                assertEquals(2, ledger.single().linkedParsedEventIds.size)
                val period = com.baraa.masroof.domain.period.FinancialPeriod(
                    java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2026, 10, 1))
                val accounts = c.dashboardService.loadOverview(period).accountsFleet!!.accounts
                val source = accounts.single { it.maskedNumber == "5678" }
                val target = accounts.single { it.maskedNumber == "1234" }
                assertEquals("100.00", source.totalOut.amount.toPlainString())
                assertEquals("0.00", source.totalIn.amount.toPlainString())
                assertEquals("-100.00", source.cashPosition.amount.toPlainString())
                assertEquals("100.00", target.totalIn.amount.toPlainString())
                assertEquals("0.00", target.totalOut.amount.toPlainString())
                assertEquals("100.00", target.cashPosition.amount.toPlainString())
                assertEquals("0.00", source.externalOut.amount.toPlainString())
                assertEquals("0.00", target.externalIn.amount.toPlainString())
                assertEquals(0, source.cashPosition.amount.add(target.cashPosition.amount).signum())
            }
            c.processRawSmsUseCase.ingest(outgoing)
            c.processRawSmsUseCase.ingest(incoming)
            assertAccountEffects()
            c.processRawSmsUseCase.ingest(incoming)
            c.processStoredSmsUseCase.process("owned-out")
            assertAccountEffects()
            c.close()
            c = AppContainer(context)
            assertAccountEffects()
        } finally { c.close(); context.getDatabasePath("masroof.db").parentFile?.deleteRecursively() }
    }
    @Test fun negativeAvailableBalanceKeepsItsSignAfterRoomAndContainerReopen() = runBlocking {
        val context = TestStorageContext(compose.activity, "signed-balance-${java.util.UUID.randomUUID()}")
        var c = AppContainer(context)
        try {
            c.processRawSmsUseCase.ingest(message("negative-balance", "SNB-AlAhli",
                "شراء إنترنت\nمبلغ 25 SAR\nبطاقة ائتمانية **4321\nمن TEST SHOP\nفي 2026-09-15 12:30\nالصرف المتبقي -50 SAR"))
            val before = requireNotNull(c.parsedEventRepository.findByRawSmsId("negative-balance"))
            assertEquals(ParseStatus.SUCCESS, before.event.parseStatus)
            assertNull(before.details.availableBalance)
            assertEquals("50.00", before.details.negativeAvailableBalance!!.amount.toPlainString())
            val ledger = c.financialTransactionRepository.listAll()
            c.close()
            c = AppContainer(context)
            assertEquals(before, c.parsedEventRepository.findByRawSmsId("negative-balance"))
            assertEquals(ledger, c.financialTransactionRepository.listAll())
        } finally { c.close(); context.getDatabasePath("masroof.db").parentFile?.deleteRecursively() }
    }
}
