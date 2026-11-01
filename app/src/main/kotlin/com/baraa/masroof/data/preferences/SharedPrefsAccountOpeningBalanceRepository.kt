package com.baraa.masroof.data.preferences

import android.content.SharedPreferences
import com.baraa.masroof.application.dashboard.AccountOpeningBalance
import com.baraa.masroof.application.dashboard.AccountOpeningBalanceCodec
import com.baraa.masroof.application.dashboard.AccountOpeningBalanceSource

/** Confirmed opening balances live with the onboarding preferences, so backups carry them. */
class SharedPrefsAccountOpeningBalanceRepository(
    private val prefs: SharedPreferences,
) : AccountOpeningBalanceSource {
    override fun load(): List<AccountOpeningBalance> =
        AccountOpeningBalanceCodec.decodeOrEmpty(prefs.getString(KEY_OPENING_BALANCES, null))

    companion object {
        /** In [SharedPrefsOnboardingPreferencesRepository.PREFS_NAME]. */
        const val KEY_OPENING_BALANCES: String = "account_opening_balances"
    }
}
