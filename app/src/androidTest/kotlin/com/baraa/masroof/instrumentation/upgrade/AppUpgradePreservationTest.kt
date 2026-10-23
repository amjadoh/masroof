package com.baraa.masroof.instrumentation.upgrade

import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.application.theme.ThemeMode
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.LoanReference
import com.baraa.masroof.domain.model.LoanType
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.domain.model.UserCorrection
import com.baraa.masroof.sms.hash.SmsBodyHasher
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Two invocations around an actual APK update, without uninstall or clearPackageData. */
class AppUpgradePreservationTest {
    private val host = InstrumentationRegistry.getInstrumentation().targetContext
    private val snapshot get() = File(host.filesDir, "production-upgrade-baseline.txt")
    private val container get() = (host.applicationContext as MasroofApplication).container

    @Test fun seedBeforeUpdate() = runBlocking {
        check(host.packageName == "com.baraa.masroof")
        container.awaitPostStartupBackgroundWorkForTesting()
        container.onboardingOwnershipWorkflow.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        container.onboardingOwnershipWorkflow.confirmCardOwned(CardReference(Bank.BANK_ALJAZIRA, "7271"))
        repeat(20) { index ->
            val body = "شراء عبر الانترنت\nبطاقة ائتمانية: 7271\nمبلغ: SAR ${10 + index}\nلدى: SYNTHETIC_SHOP\nفي: 2026-10-21 12:00"
            val raw = RawSms("upgrade-$index", "AlJazira", body, Instant.parse("2026-10-21T09:01:00Z"), "upgrade-$index", SmsBodyHasher.sha256Hex(body))
            container.processRawSmsUseCase.ingest(raw)
        }
        assertEquals(20, container.financialTransactionRepository.listAll().size)
        val sources = container.financialTransactionRepository.listAll().sortedBy { it.id }.take(2)
        for (source in sources) container.dashboardCommitmentsWorkflow.createFromTransaction(source.id)
        val commitments = container.settingsCommitmentsWorkflow.listAll().sortedBy { it.id }
        assertEquals(2, commitments.size)
        container.settingsCommitmentsWorkflow.toggleActive(commitments.first().id)
        container.loanRegistryRepository.setOwnership(LoanReference(Bank.BANK_ALJAZIRA, LoanType.PERSONAL), OwnershipStatus.OWNED)
        container.userCorrectionRepository.save(UserCorrection("preserved-correction", "upgrade-0", null, null,
            "My merchant", null, Instant.parse("2026-10-21T10:00:00Z")))
        container.themePreferencesRepository.setThemeMode(ThemeMode.DARK)
        container.appLocaleRepository.setLanguageTag("en")
        host.getSharedPreferences("theme_prefs", 0).edit().putString("upgrade-probe", "preserved").commit()
        snapshot.writeText(fingerprint())
    }

    @Test fun verifyAfterUpdate() = runBlocking {
        container.awaitPostStartupBackgroundWorkForTesting()
        assertEquals(snapshot.readText(), fingerprint())
        assertEquals(2, container.commitmentRepository.listAll().size)
        assertEquals(1, container.commitmentRepository.listAll().count { !it.active })
        assertEquals(OwnershipStatus.OWNED, container.loanRegistryRepository.resolve(LoanReference(Bank.BANK_ALJAZIRA, LoanType.PERSONAL)))
        assertEquals(ThemeMode.DARK, container.themePreferencesRepository.getThemeMode())
        assertEquals("en", container.appLocaleRepository.getLanguageTag())
        assertEquals("preserved", host.getSharedPreferences("theme_prefs", 0).getString("upgrade-probe", null))
        assertEquals("masroof.db", host.getDatabasePath("masroof.db").name)
        assertTrue(host.getDatabasePath("masroof.db").isFile)
    }

    private suspend fun fingerprint(): String = buildString {
        appendLine(container.themePreferencesRepository.getThemeMode())
        appendLine(container.appLocaleRepository.getLanguageTag())
        appendLine(container.rawSmsRepository.listIdsByReceivedAt().sorted())
        appendLine(container.parsedEventRepository.listAll().sortedBy { it.event.id })
        appendLine(container.financialTransactionRepository.listAll().sortedBy { it.id })
        appendLine(container.accountRegistryRepository.listAll().sortedBy { it.id })
        appendLine(container.cardRegistryRepository.listAll().sortedBy { it.id })
        appendLine(container.loanRegistryRepository.listAll().sortedBy { it.id })
        appendLine(container.commitmentRepository.listAll().sortedBy { it.id })
        appendLine(container.userCorrectionRepository.listForRawSmsId("upgrade-0"))
    }
}
