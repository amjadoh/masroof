package com.baraa.masroof.application.baseline

import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.presentation.navigation.HomeDestination
import com.baraa.masroof.presentation.navigation.SettingsDestination
import com.baraa.masroof.presentation.navigation.encode
import com.baraa.masroof.presentation.onboarding.OnboardingStep
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M0 lock for the production baseline at 5bb7e3b276b3905177482979b236362ad5b2f1df.
 *
 * Layer sizes are reported, not frozen as a growth ceiling. Golden ledger bytes are
 * hashed so an expectation change has to be explicit. Device startup and bulk SMS
 * import stay unset until measured on a device.
 */
class M0ProductionBaselineTest {
    @Test
    fun productionTree_reportsLayerCountsWithoutFreezingGrowth() {
        val files = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("expected a non-empty production tree", files.isNotEmpty())
        val byTop = files.groupingBy { it.relativeTo(sourceRoot).invariantSeparatorsPath.substringBefore('/') }
            .eachCount()
        println(
            "M0_PRODUCTION_TREE total=${files.size} " +
                byTop.entries.sortedBy { it.key }.joinToString(" ") { "${it.key}=${it.value}" },
        )
        assertTrue(byTop.getValue("application") > 0)
        assertTrue(byTop.getValue("presentation") > 0)
        assertTrue(byTop.getValue("domain") > 0)
        assertTrue(byTop.getValue("data") > 0)
        assertTrue(byTop.getValue("bank") > 0)
        assertFalse(files.any { it.invariantSeparatorsPath.contains("/application/beta/") })
        assertFalse(files.any { it.invariantSeparatorsPath.contains("/presentation/beta/") })
    }

    @Test
    fun processingAndReleaseEntrypoints_exist() {
        listOf(
            "sms/receiver/IncomingSmsReceiver.kt",
            "application/sms/LiveSmsIntake.kt",
            "application/ingestion/CaptureBankSmsUseCase.kt",
            "application/sms/LiveSmsWorkScheduler.kt",
            "application/sms/LiveSmsProcessingWorker.kt",
            "application/ingestion/ProcessStoredSmsUseCase.kt",
            "application/sms/HistoricalSmsScanner.kt",
            "application/sms/HistoricalSmsBatchProcessor.kt",
            "application/ingestion/ProcessRawSmsUseCase.kt",
            "application/transaction/ReconciliationCompletionPolicy.kt",
            "parsing/finalize/ParseFinalizer.kt",
            "bank/BankSmsRegistry.kt",
            "application/dashboard/DashboardProjectionBuilder.kt",
            "application/update/ApkInstaller.kt",
            "application/update/UpdateChecker.kt",
        ).forEach { relative ->
            assertTrue(relative, File(sourceRoot, relative).isFile)
        }
    }

    @Test
    fun roomV18Identity_isTheBaseline() {
        assertEquals(18, MasroofDatabase.VERSION)
        assertEquals("masroof.db", MasroofDatabase.NAME)
        assertEquals("71f1193767b3aa12bcb6cc6e7ce77147", MasroofDatabase.IDENTITY_HASH)

    }

    @Test
    fun toolchainAndSideloadUpdate_matchTheReviewedBuild() {
        val versions = File("../gradle/libs.versions.toml").readText()
        // M1 updates the reviewed toolchain; financial and storage baseline locks stay unchanged.
        assertEquals("2.4.21", version(versions, "kotlin"))
        assertEquals("9.5.0", version(versions, "agp"))
        assertEquals("2.8.5", version(versions, "room"))
        assertEquals("2.12.0", version(versions, "workmanager"))
        assertEquals("2026.09.00", version(versions, "composeBom"))
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("applicationId = \"com.baraa.masroof\""))
        assertTrue(gradle.contains("compileSdk = 37"))
        assertTrue(gradle.contains("targetSdk = 36"))
        assertTrue(gradle.contains("minSdk = 26"))
        assertTrue(gradle.contains("project(\":bank-engine\")"))
        assertFalse(gradle.contains("project(\":lab:android\")"))
        assertFalse(gradle.contains("parsing-lab"))
        val release = File("../.github/workflows/release.yml").readText()
        assertTrue(release.contains("assembleRelease"))
        assertTrue(release.contains("-PappVersionCode="))
        assertTrue(release.contains("RELEASE_KEYSTORE_BASE64"))
        assertTrue(release.contains("storePassword=\${RELEASE_KEYSTORE_PASSWORD}"))
        assertFalse(release.contains("storePassword=\""))
        val gitignore = File("../.gitignore").readText()
        assertTrue(gitignore.contains("keystore.properties"))
        assertTrue(gitignore.contains("*.keystore"))
        // Local release signing is supported; the configuration must remain gitignored.
        val installer = File(sourceRoot, "application/update/ApkInstaller.kt").readText()
        assertTrue(installer.contains("application/vnd.android.package-archive"))
        assertTrue(installer.contains("Intent.ACTION_VIEW"))
        val checker = File(sourceRoot, "application/update/UpdateChecker.kt").readText()
        assertTrue(checker.contains("manifest.versionCode > installedVersionCode"))
    }

    @Test
    fun userPaths_selectBanksThenDiscoverAndReview() {
        assertEquals(
            listOf("WELCOME", "BANKS", "PERMISSION", "IMPORT_DATE", "IMPORTING", "OWNERSHIP", "FINALIZE", "HOME"),
            OnboardingStep.entries.map { it.name },
        )
        assertEquals("my_accounts", SettingsDestination.MyAccounts.encode())
        assertEquals("my_cards", SettingsDestination.MyCards.encode())
        assertEquals("about", SettingsDestination.About.encode())
        assertTrue(HomeDestination.entries.any { it.name == "Review" })
        assertFalse(File(sourceRoot, "presentation/navigation/SettingsDestination.kt").readText().contains("add_card"))
        assertFalse(File(sourceRoot, "presentation/navigation/AppNavigation.kt").readText().contains("Beta"))
    }

    @Test
    fun goldenLedger_hashesAndActiveScenarios_stayFixed() {
        val root = goldenRoot()
        val files = root.listFiles { file -> file.isFile && file.extension == "json" }.orEmpty().sortedBy { it.name }
        assertEquals(GOLDEN_SHA256.keys.sorted(), files.map { it.name })
        files.forEach { file ->
            assertEquals(file.name, GOLDEN_SHA256.getValue(file.name), sha256(file))
            val text = file.readText()
            assertTrue(text.contains("\"status\": \"ACTIVE\"") || text.contains("\"status\":\"ACTIVE\""))
        }
        assertEquals(
            listOf(
                "live_inbox_twin",
                "m1_two_day_same_amount",
                "m5_cross_bank_suffix",
                "m6_account_refund",
                "m8_asof_fx",
                "one_intra_pair",
                "repeated_separate_notices",
            ),
            files.map { file ->
                Regex(""""id"\s*:\s*"([^"]+)"""").find(file.readText())!!.groupValues[1]
            },
        )
        val oracle = File("src/test/kotlin/com/baraa/masroof/application/dashboard/GoldenLedgerOracleTest.kt").readText()
        val characterization = File(
            "src/test/kotlin/com/baraa/masroof/application/dashboard/DashboardFixtureCharacterizationTest.kt",
        ).readText()
        assertTrue(oracle.contains("fun oneIntraPair_matchesOracle_acrossReprocessing"))
        assertTrue(oracle.contains("fun accountRefund_matchesOracle_acrossReprocessing"))
        assertTrue(oracle.contains("fun asOfFx_matchesOracle_acrossReprocessing"))
        assertTrue(characterization.contains("fun scopedEvidence_matchesWholeHistoryProjection_forFixtureCorpus"))
        assertTrue(characterization.contains("fun scopedEvidence_matchesWholeHistoryProjection_acrossLongHistory"))
    }

    @Test
    fun knownIgnoredTests_areAnEmptyBaseline() {
        val annotation = Regex("""(?m)^\s*@Ignore\b""")
        val ignored = listOf(File("src/test"), File("src/androidTest"))
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .filter { annotation.containsMatchIn(it.readText()) }
        assertEquals(emptyList<File>(), ignored)
        assertNull(M0DeviceBaseline.startupP50Millis)
        assertNull(M0DeviceBaseline.importTenThousandSmsP50Millis)
    }

    private fun version(toml: String, key: String): String =
        Regex("""(?m)^$key = "([^"]+)"""").find(toml)?.groupValues?.get(1)
            ?: error("Missing version $key")

    private fun goldenRoot(): File = listOf(
        File("src/test/resources/testdata/golden_ledger"),
        File("app/src/test/resources/testdata/golden_ledger"),
    ).first { it.isDirectory }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private companion object {
        val sourceRoot = File("src/main/kotlin/com/baraa/masroof")

        val GOLDEN_SHA256 = mapOf(
            "active_live_inbox_twin.json" to "2d4f2b205b2a59c7b63cf43e5c6b96713d0132ae544d6b8dc6fff09c0520f72b",
            "active_m1_two_day_same_amount.json" to "e3b8546fcba4d7afd9188d37304c86c55f68ef1b73f1f128237ea03f4fa59297",
            "active_m5_cross_bank_suffix.json" to "ba094159302f73b3b0d0e2e6b01a712e92dfb4f54080f5dbb59671525d4efb57",
            "active_m6_account_refund.json" to "1a4ef4d7098b64f946456d12c47a24adcf5346d491876c5608433f9531ecb2bc",
            "active_m8_asof_fx.json" to "e082efdf7bfb76dfa5fd075db38cfa923069261f788a264b43962fe25524d813",
            "active_one_intra_pair.json" to "e464804ff448411e5c6140a84b7e8a8ea88a8b76c345d0daddf2c549cce4deb3",
            "active_repeated_separate_notices.json" to "67c1e1a0c2d1d4e14fa1d54147aa7aff6b448397504c16c9e47d91ab949d3966",
        )
    }
}

/**
 * Device samples are recorded only after an on-device run. Null means unmeasured.
 */
object M0DeviceBaseline {
    val startupP50Millis: Long? = null
    val importTenThousandSmsP50Millis: Long? = null
}
