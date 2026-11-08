package com.baraa.masroof.data.preferences

import android.content.SharedPreferences
import com.baraa.masroof.application.credit.CardCompanionProfile
import com.baraa.masroof.application.credit.CardCompanionProfileRepository
import kotlinx.serialization.json.Json

class SharedPrefsCardCompanionProfileRepository(
    private val prefs: SharedPreferences,
) : CardCompanionProfileRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun get(cardKey: String): CardCompanionProfile {
        val raw = prefs.getString(KEY_PREFIX + cardKey, null) ?: return CardCompanionProfile()
        // A damaged entry is surfaced as empty rather than crashing the card screen; it is not overwritten
        // until the user saves a new value.
        return runCatching { json.decodeFromString(CardCompanionProfile.serializer(), raw) }
            .getOrElse { CardCompanionProfile() }
    }

    override fun save(cardKey: String, profile: CardCompanionProfile) {
        prefs.edit()
            .putString(KEY_PREFIX + cardKey, json.encodeToString(CardCompanionProfile.serializer(), profile))
            .apply()
    }

    companion object {
        const val PREFS_NAME: String = "card_companion_prefs"
        private const val KEY_PREFIX: String = "card:"
    }
}
