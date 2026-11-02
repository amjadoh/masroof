package com.baraa.masroof.data.preferences

import android.content.SharedPreferences
import com.baraa.masroof.application.dashboard.AccountOpeningBalance
import com.baraa.masroof.application.dashboard.AccountOpeningBalanceCodec
import com.baraa.masroof.application.dashboard.AccountOpeningBalanceStore

/** Confirmed opening balances live with the onboarding preferences, so backups carry them. */
class SharedPrefsAccountOpeningBalanceRepository(
    private val prefs: SharedPreferences,
) : AccountOpeningBalanceStore {
    override fun load(): List<AccountOpeningBalance> =
        AccountOpeningBalanceCodec.decodeOrEmpty(prefs.getString(KEY_OPENING_BALANCES, null))

    @Synchronized
    override fun save(balance: AccountOpeningBalance) {
        write(load().filterNot { it.key == balance.key } + balance)
    }

    @Synchronized
    override fun remove(bankId: String, maskedNumber: String) {
        write(load().filterNot { it.bankId == bankId && it.maskedNumber == maskedNumber })
    }

    private fun write(balances: List<AccountOpeningBalance>) {
        val editor = prefs.edit()
        if (balances.isEmpty()) editor.remove(KEY_OPENING_BALANCES)
        else editor.putString(KEY_OPENING_BALANCES, AccountOpeningBalanceCodec.encode(balances))
        check(editor.commit()) { "Opening balance was not saved" }
    }

    companion object {
        /** In [SharedPrefsOnboardingPreferencesRepository.PREFS_NAME]. */
        const val KEY_OPENING_BALANCES: String = "account_opening_balances"
    }
}
