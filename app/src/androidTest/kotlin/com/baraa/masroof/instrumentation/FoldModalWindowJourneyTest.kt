package com.baraa.masroof.instrumentation

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.MainActivity
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.R
import com.baraa.masroof.application.locale.AppLocalePreferences
import com.baraa.masroof.application.theme.ThemeMode
import com.baraa.masroof.presentation.locale.AppLocaleContext
import com.baraa.masroof.presentation.theme.MasroofSheetTokens
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Launches real MainActivity, including attachBaseContext, startup and production navigation. */
class FoldModalWindowJourneyTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val seed = object : TestRule {
        override fun apply(base: Statement, description: Description) = object : Statement() {
            override fun evaluate() {
                val args = InstrumentationRegistry.getArguments()
                val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MasroofApplication
                app.getSharedPreferences(AppLocalePreferences.PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(AppLocalePreferences.KEY_LANGUAGE_TAG, args.getString("language") ?: "ar").commit()
                app.container.themePreferencesRepository.setThemeMode(if (args.getString("dark") == "true") ThemeMode.DARK else ThemeMode.LIGHT)
                JourneySeeder.prepare("dashboard_showsPersistedTransactionAfterReload")
                val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
                fun command(value: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    automation.executeShellCommand(value)).use { it.readBytes().toString(Charsets.UTF_8) }
                val initialOverride = command("wm size").lineSequence()
                    .firstOrNull { it.startsWith("Override size:") }?.substringAfter(':')?.trim() ?: "reset"
                try { base.evaluate() } finally { command("wm size $initialOverride") }
            }
        }
    }
    @get:Rule val rule: TestRule = RuleChain.outerRule(seed).around(compose)
    private val language get() = InstrumentationRegistry.getArguments().getString("language") ?: "ar"
    private val appearance get() = if (InstrumentationRegistry.getArguments().getString("dark") == "true") "dark" else "light"
    private fun text(id: Int) = AppLocaleContext.wrap(compose.activity, language).getString(id)
    private fun exists(tag: String) = runCatching { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun awaitShell() {
        compose.waitUntil(30_000) { runCatching { compose.onAllNodesWithContentDescription(text(R.string.dashboard_open_settings)).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    }
    private fun clickText(value: String, withinList: Boolean = false) {
        val target = hasText(value) and hasClickAction() and
            if (withinList) hasAnyAncestor(hasScrollToIndexAction()) else SemanticsMatcher("any container") { true }
        try { compose.waitUntil(20_000) {
            if (compose.onAllNodes(target).fetchSemanticsNodes().isEmpty()) {
                // English labels can move the action beyond the lazy viewport on a folded screen.
                runCatching { compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(value)) }
            }
            compose.onAllNodes(target).fetchSemanticsNodes().isNotEmpty()
        } } catch (failure: Throwable) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes().indices.forEach { index ->
                println("CLICK '$value' root=$index ${compose.onAllNodes(isRoot())[index].printToString()}")
            }
            throw failure
        }
        val node = compose.onAllNodes(target).onFirst()
        runCatching { node.performScrollTo() }
        // Navigation is setup for the fold assertions. Invoke the identified action,
        // not a coordinate that can move while the destination finishes loading.
        // The sheet footer below still exercises actual touch after every resize.
        node.assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick) { it() }
    }
    private fun openSheet(kind: String) {
        if (kind == "chart" || kind == "layout") {
            val tag = if (kind == "chart") "chart-customize" else "dashboard-customize"
            compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        }
        when (kind) {
            "chart" -> compose.onNodeWithTag("chart-customize", useUnmergedTree = true).performScrollTo().performClick()
            "layout" -> compose.onNodeWithTag("dashboard-customize", useUnmergedTree = true).performScrollTo().performClick()
            "filter" -> {
                clickText(text(R.string.reference_all_types))
                clickText(text(R.string.transaction_list_filter_sheet_title))
            }
            else -> clickText(text(R.string.transaction_detail_edit_category))
        }
        compose.waitUntil(20_000) { exists("sheet-overlay") }
        compose.waitForIdle()
    }
    private fun resize(size: String) {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("wm size $size")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
        val expected = size.substringBefore('x').toInt()
        compose.waitUntil(30_000) { compose.activity.window.decorView.width == expected && exists("sheet-overlay") }
        compose.waitForIdle()
    }
    private fun verify(kind: String, profile: String) {
        // WindowManager layout follows Activity configuration delivery asynchronously.
        // Edge-to-edge Dialog and Activity content can consume different system-bar insets.
        // Cover all Activity content without exceeding the current full Android window.
        try { compose.waitUntil(30_000) {
            runCatching {
                val bounds = compose.onNodeWithTag("sheet-overlay").fetchSemanticsNode().boundsInRoot
                abs(bounds.width - compose.activity.window.decorView.width) <= 2f &&
                    bounds.height >= compose.activity.findViewById<android.view.View>(android.R.id.content).height - 2f &&
                    bounds.height <= compose.activity.window.decorView.height + 2f &&
                    listOf("sheet-actions", "sheet-close", "sheet-apply", "sheet-cancel").all { tag ->
                        val child = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                        child.width > 0 && child.height > 0 && child.left >= bounds.left &&
                            child.right <= bounds.right && child.top >= bounds.top && child.bottom <= bounds.bottom
                    }
            }.getOrDefault(false)
        } } catch (failure: Throwable) {
            capture("$kind-$language-$appearance-$profile-failure")
            println("WINDOW activity=${compose.activity.window.decorView.width}x${compose.activity.window.decorView.height} content=${compose.activity.findViewById<android.view.View>(android.R.id.content).height}")
            listOf("sheet-overlay", "masroof-bottom-sheet", "sheet-actions", "sheet-close", "sheet-apply", "sheet-body").forEach {
                println("WINDOW $it ${runCatching { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }}")
            }
            throw failure
        }
        val overlay = compose.onNodeWithTag("sheet-overlay").fetchSemanticsNode().boundsInRoot
        val scrim = compose.onNodeWithTag("sheet-scrim").fetchSemanticsNode().boundsInRoot
        val sheet = compose.onNodeWithTag("masroof-bottom-sheet").fetchSemanticsNode().boundsInRoot
        val width = compose.activity.window.decorView.width.toFloat()
        assertEquals("Locale-wrapped Activity metrics must follow its current window", width,
            compose.activity.resources.displayMetrics.widthPixels.toFloat(), 2f)
        assertEquals("Overlay must span actual Activity window", width, overlay.width, 2f)
        assertTrue("Overlay must cover all Activity content", overlay.height >=
            compose.activity.findViewById<android.view.View>(android.R.id.content).height - 2f)
        assertTrue("Overlay must fit within the current Android window",
            overlay.height <= compose.activity.window.decorView.height + 2f)
        assertEquals(overlay.left, scrim.left, 1f); assertEquals(overlay.right, scrim.right, 1f)
        assertEquals(overlay.top, scrim.top, 1f); assertEquals(overlay.bottom, scrim.bottom, 1f)
        assertTrue("Sheet is not centered in the full window", abs(sheet.center.x - overlay.center.x) <= 2f)
        val density = compose.activity.resources.displayMetrics.density
        assertTrue(sheet.width <= MasroofSheetTokens.maxWidth.value * density + 2f)
        // Compose 1.6's displayed predicate can retain a stale View clip after wm resize.
        // Require FULL containment in the measured Android window, and exercise real touch below.
        listOf("sheet-actions", "sheet-close", "sheet-apply", "sheet-cancel").forEach { tag ->
            val child = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag must fit completely inside the current window: $child / $overlay",
                child.width > 0 && child.height > 0 && child.left >= overlay.left && child.right <= overlay.right &&
                    child.top >= overlay.top && child.bottom <= overlay.bottom)
        }
        if (profile == "folded" || profile == "reopened") {
            compose.onNodeWithTag("sheet-actions").assertIsDisplayed()
            compose.onNodeWithTag("sheet-close").assertIsDisplayed()
            compose.onNodeWithTag("sheet-apply").assertIsDisplayed()
            compose.onNodeWithText(text(R.string.sheet_cancel)).assertIsDisplayed()
        }
        capture("$kind-$language-$appearance-$profile")
    }
    private fun capture(name: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(250)
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "fold-modal-window")
        dir.mkdirs()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun journey(kind: String) {
        awaitShell()
        if (kind == "filter" || kind == "reclassify") {
            clickText(text(R.string.approved_nav_transactions))
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText(text(R.string.transaction_list_title)).fetchSemanticsNodes().isNotEmpty()
            }
        }
        if (kind == "reclassify") {
            // The dashboard also contains this merchant. Wait for the destination,
            // then select the clickable transaction row inside its lazy list.
            clickText(JourneyFixtures.DASHBOARD_MERCHANT, withinList = true)
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText(text(R.string.transaction_detail_title)).fetchSemanticsNodes().isNotEmpty()
            }
        }
        openSheet(kind)
        verify(kind, "folded")
        resize("1600x2000"); verify(kind, "unfolded")
        resize("640x1440"); verify(kind, "refolded")
        // Verify the footer really receives a touch after folding, not just a semantics action.
        compose.onNodeWithTag("sheet-cancel").performTouchInput { click(center) }
        compose.waitUntil(10_000) { !exists("sheet-overlay") }
        openSheet(kind)
        resize("1200x960"); verify(kind, "short")
        // Real Activity recreation retains the open sheet, including its draft ownership.
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30_000) { exists("sheet-overlay") }
        verify(kind, "recreated")
        Espresso.pressBack(); compose.waitUntil(10_000) { !exists("sheet-overlay") }
        // Re-open after returning to the folded window, without a modal present.
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("wm size 640x1440")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
        compose.waitUntil(30_000) { compose.activity.window.decorView.width == 640 }
        openSheet(kind); verify(kind, "reopened")
        compose.onNodeWithTag("sheet-cancel").performTouchInput { click(center) }
        compose.waitUntil(10_000) { !exists("sheet-overlay") }
    }
    @Test fun chart_handlesFoldingThroughRealMainActivity() = journey("chart")
    @Test fun layout_handlesFoldingThroughRealMainActivity() = journey("layout")
    @Test fun filter_handlesFoldingThroughRealMainActivity() = journey("filter")
    @Test fun reclassify_handlesFoldingThroughRealMainActivity() = journey("reclassify")
}
