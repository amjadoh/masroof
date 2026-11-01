package com.baraa.masroof.instrumentation

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.ui.dashboard.*
import com.baraa.masroof.ui.theme.*
import java.io.File
import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Pure synthetic renderers: no AppContainer, database, SMS or financial writes. */
class SharedHomeSectionsParityTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val window = TestRule { base, description -> object : Statement() {
        override fun evaluate() {
            Assume.assumeTrue("Emulator display override only", Build.HARDWARE in setOf("ranchu", "goldfish"))
            val original = command("wm size").lineSequence().firstOrNull { it.startsWith("Override size:") }
                ?.substringAfter(':')?.trim() ?: "reset"
            try {
                command(if (description.methodName.contains("Fold")) "wm size 1800x2400" else "wm size 1080x2400")
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                base.evaluate()
            } finally { command("wm size $original") }
        }
    } }
    @get:Rule val rule: TestRule = RuleChain.outerRule(window).around(compose)
    @Test fun arabicLightPhone() = verify("ar", false, false)
    @Test fun arabicDarkPhone() = verify("ar", true, false)
    @Test fun englishLightPhone() = verify("en", false, false)
    @Test fun englishDarkPhone() = verify("en", true, false)
    @Test fun arabicLightFold() = verify("ar", false, true)
    @Test fun arabicDarkFold() = verify("ar", true, true)
    @Test fun englishLightFold() = verify("en", false, true)
    @Test fun englishDarkFold() = verify("en", true, true)

    private fun verify(language: String, dark: Boolean, fold: Boolean) {
        var original by mutableStateOf(true)
        var section by mutableStateOf("metric")
        var clicks by mutableIntStateOf(0)
        val fontScale = if (fold) 1.3f else 1f
        compose.waitUntil(10000) { (compose.activity.resources.configuration.screenWidthDp >= 600) == fold }
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList(Locale.forLanguageTag(language))); setLayoutDirection(Locale.forLanguageTag(language)) }
            val context = LocalContext.current.createConfigurationContext(config)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalConfiguration provides config, LocalContext provides context,
                LocalLayoutDirection provides if (language == "ar") androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
                LocalDensity provides Density(density.density, fontScale)) {
                MasroofTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxWidth().testTag("m9-home-fixture"), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.padding(MasroofSpacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
                            val title = if (language == "ar") "إجمالي المصاريف" else "Total expenses"
                            val value = if (language == "ar") "−1,234.50 ر.س" else "−1,234.50 SAR"
                            val action = if (language == "ar") "عرض الكل" else "View all"
                            when (section) {
                                "header" -> if (original) com.baraa.masroof.instrumentation.m9reference.MasroofAppBar(if (language == "ar") "مصروف" else "Masroof")
                                    else MasroofAppBar(if (language == "ar") "مصروف" else "Masroof")
                                "metric", "unknown" -> if (original) com.baraa.masroof.instrumentation.m9reference.MasroofMetric(title, if (section == "unknown") "—" else value, MaterialTheme.colorScheme.primary, guidance = title)
                                    else MasroofMetric(title, if (section == "unknown") "—" else value, MaterialTheme.colorScheme.primary, guidance = title)
                                "period" -> if (original) com.baraa.masroof.instrumentation.m9reference.MasroofPeriodPill("1 Oct – 31 Oct", {}, {}, onCustomize = { clicks++ }, customizeLabel = action)
                                    else MasroofPeriodPill("1 Oct – 31 Oct", {}, {}, onCustomize = { clicks++ }, customizeLabel = action)
                                "heading" -> if (original) com.baraa.masroof.instrumentation.m9reference.SectionHeader(title, MasroofIcons.analysis, onViewAll = { clicks++ }, viewAllLabel = action)
                                    else SectionHeader(title, MasroofIcons.analysis, onViewAll = { clicks++ }, viewAllLabel = action)
                                "tabs" -> if (original) com.baraa.masroof.instrumentation.m9reference.MasroofChartScopeTabs(listOf(title, action), 0) { clicks++ }
                                    else MasroofChartScopeTabs(listOf(title, action), 0) { clicks++ }
                                "merchant" -> if (original) com.baraa.masroof.instrumentation.m9reference.MasroofRankedBarRow(title, value, .65f, subtitle = action, style = MasroofHorizontalBarStyle.Outflow)
                                    else MasroofRankedBarRow(title, value, .65f, subtitle = action, style = MasroofHorizontalBarStyle.Outflow)
                                "commitment" -> {
                                    val amount = com.baraa.masroof.core.money.Money.of(BigDecimal("123.45"), com.baraa.masroof.core.money.Currency.SAR)
                                    val overview = com.baraa.masroof.application.dashboard.CommitmentsOverview(listOf(
                                        com.baraa.masroof.application.dashboard.CommitmentDashboardRow("fixture", com.baraa.masroof.application.dashboard.CommitmentDashboardSource.CREDIT_CARD,
                                            title, amount, java.time.LocalDate.of(2026,10,25), com.baraa.masroof.application.dashboard.CommitmentPaymentStatus.UNPAID)), amount, amount, amount, amount.currency)
                                    if (original) com.baraa.masroof.instrumentation.m9reference.CommitmentsSection(overview, onOpenCredit = { clicks++ })
                                    else com.baraa.masroof.presentation.dashboard.CommitmentsSection(overview, onOpenCredit = { clicks++ })
                                }
                                "transaction" -> {
                                    val row = com.baraa.masroof.presentation.dashboard.TransactionPreviewUi("fixture", title,
                                        com.baraa.masroof.core.money.Money.of(BigDecimal("123.45"), com.baraa.masroof.core.money.Currency.SAR),
                                        java.time.LocalDate.of(2026,10,18), "", "", com.baraa.masroof.domain.model.FinancialTransactionType.EXPENSE,
                                        com.baraa.masroof.domain.model.FinancialTransactionType.EXPENSE, com.baraa.masroof.presentation.dashboard.TransactionDirectionUi.OUTWARD, null, searchText = title)
                                    if (original) com.baraa.masroof.instrumentation.m9reference.TransactionPreviewRow(row, onClick = { clicks++ })
                                    else com.baraa.masroof.presentation.dashboard.TransactionPreviewRow(row, onClick = { clicks++ })
                                }
                            }
                        }
                    }
                }
            }
        }
        val prefix = "m9-home-$language-${if (dark) "dark" else "light"}-${if (fold) "fold" else "phone"}"
        listOf("header", "metric", "unknown", "period", "heading", "tabs", "merchant", "commitment", "transaction").forEach { selected ->
            compose.runOnIdle { original = true; section = selected }
            compose.waitForIdle()
            val before = compose.onNodeWithTag("m9-home-fixture").captureToImage().asAndroidBitmap()
            capture("$prefix-$selected-before", before, language, dark, fontScale)
            compose.runOnIdle { original = false }
            compose.waitForIdle()
            val after = compose.onNodeWithTag("m9-home-fixture").captureToImage().asAndroidBitmap()
            capture("$prefix-$selected-shared", after, language, dark, fontScale)
            assertEquals(before.width, after.width); assertEquals(before.height, after.height)
            val a = IntArray(before.width * before.height); val b = IntArray(after.width * after.height)
            before.getPixels(a, 0, before.width, 0, 0, before.width, before.height)
            after.getPixels(b, 0, after.width, 0, 0, after.width, after.height)
            assertEquals("$prefix $selected extraction pixels", 0, a.indices.count { a[it] != b[it] })
        }
        compose.onNodeWithText(if (language == "ar") "إجمالي المصاريف" else "Total expenses").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    private fun command(value: String) = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(value)).use { it.readBytes().toString(Charsets.UTF_8) }
    private fun capture(name: String, bitmap: Bitmap, language: String, dark: Boolean, fontScale: Float) {
        val context = compose.activity
        val directory = File(context.getExternalFilesDir(null), "m9-home-parity").apply { check(isDirectory || mkdirs()) }
        val png = File(directory, "$name.png")
        png.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val metadata = File(directory, "$name.txt")
        metadata.writeText("language=$language\ndark=$dark\nfontScale=$fontScale\napi=${Build.VERSION.SDK_INT}\n${context.resources.configuration}\n" + compose.onRoot().printToString())
        command("mkdir -p /sdcard/beta-dashboard-screenshots")
        command("cp ${png.absolutePath} /sdcard/beta-dashboard-screenshots/$name.png")
        command("cp ${metadata.absolutePath} /sdcard/beta-dashboard-screenshots/$name.txt")
        assertEquals(metadata.readText(), command("cat /sdcard/beta-dashboard-screenshots/$name.txt"))
    }
}
