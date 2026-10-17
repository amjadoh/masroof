package com.baraa.masroof.lab.beta.presentation

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.ui.theme.*
import com.baraa.masroof.ui.components.MasroofCard
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** M2.UI.1 only: actual extracted card against the frozen pre-extraction renderer.
 * No databases, permissions, SMS, production container or financial calculations.
 */
class SharedUiExtractionParityTest {
    @get:Rule val compose = createComposeRule()
    @Test fun arabicLight() = verify("ar", false)
    @Test fun arabicDark() = verify("ar", true)
    @Test fun englishLight() = verify("en", false)
    @Test fun englishDarkLargeFont() = verify("en", true, 1.3f)

    private fun verify(language: String, dark: Boolean, fontScale: Float = 1f) {
        var original by mutableStateOf(true)
        var taps by mutableIntStateOf(0)
        val label = if (language == "ar") "الحساب التجريبي" else "Synthetic account"
        val action = if (language == "ar") "التفاصيل" else "Details"
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList(Locale.forLanguageTag(language))) }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(density.density, fontScale)) {
                MasroofTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxWidth().testTag("extraction-fixture"), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.padding(MasroofSpacing.screenHorizontal)) {
                            val content: @Composable ColumnScope.() -> Unit = {
                                Text(label, style = MaterialTheme.typography.titleMedium)
                                Text("1,234.50 SAR", style = MaterialTheme.typography.headlineSmall)
                                TextButton(onClick = { taps++ }, modifier = Modifier.testTag("parity-action")) { Text(action) }
                            }
                            if (original) OriginalProductionCard(content = content) else MasroofCard(content = content)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val name = "m2-$language-${if (dark) "dark" else "light"}"
        val before = compose.onNodeWithTag("extraction-fixture").captureToImage().asAndroidBitmap()
        capture(name + "-before", before, language, dark, fontScale)
        compose.runOnIdle { original = false }
        compose.waitForIdle()
        compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithText("1,234.50 SAR").assertIsDisplayed()
        val after = compose.onNodeWithTag("extraction-fixture").captureToImage().asAndroidBitmap()
        capture(name + "-shared", after, language, dark, fontScale)
        assertEquals(before.width, after.width); assertEquals(before.height, after.height)
        val a = IntArray(before.width * before.height); val b = IntArray(after.width * after.height)
        before.getPixels(a, 0, before.width, 0, 0, before.width, before.height)
        after.getPixels(b, 0, after.width, 0, 0, after.width, after.height)
        assertEquals("Extracted production card must preserve every pixel: $name", 0, a.indices.count { a[it] != b[it] })
        compose.onNodeWithTag("parity-action").performClick()
        compose.runOnIdle { assertEquals(1, taps) }
    }

    private fun capture(name: String, bitmap: Bitmap, language: String, dark: Boolean, fontScale: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.getExternalFilesDir(null), "m2-parity").apply { check(isDirectory || mkdirs()) }
        val file = File(directory, "$name.png")
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val metadata = File(directory, "$name.txt")
        metadata.writeText("language=$language\ndark=$dark\nfontScale=$fontScale\napi=${android.os.Build.VERSION.SDK_INT}\n" +
            "${context.resources.configuration}\n" + compose.onRoot().printToString())
        fun command(value: String) = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(value)).use { it.readBytes() }
        command("mkdir -p /sdcard/beta-dashboard-screenshots")
        command("cp ${file.absolutePath} /sdcard/beta-dashboard-screenshots/$name.png")
        command("cp ${metadata.absolutePath} /sdcard/beta-dashboard-screenshots/$name.txt")
        assertTrue(command("cat /sdcard/beta-dashboard-screenshots/$name.png").size > 8)
        assertEquals(metadata.readText(), command("cat /sdcard/beta-dashboard-screenshots/$name.txt").toString(Charsets.UTF_8))
    }
}

// Original MasroofCard from main bd21a352 (unchanged rendering body).
@Composable
private fun OriginalProductionCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(MasroofSpacing.cardInnerPadding + MasroofSpacing.borderWidth),
    content: @Composable ColumnScope.() -> Unit,
) {
    val extended = MasroofThemeExtras.extendedColors
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = MasroofCardShape,
        colors = CardDefaults.cardColors(
            containerColor = extended.cardSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = MasroofSpacing.none,
            pressedElevation = MasroofSpacing.none,
            focusedElevation = MasroofSpacing.none,
            hoveredElevation = MasroofSpacing.none,
            draggedElevation = MasroofSpacing.none,
            disabledElevation = MasroofSpacing.none,
        ),
        border = androidx.compose.foundation.BorderStroke(MasroofSpacing.borderWidth, extended.cardBorder),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content,
            )
        }
    }
}
