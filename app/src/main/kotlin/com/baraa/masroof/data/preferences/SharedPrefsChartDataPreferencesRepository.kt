package com.baraa.masroof.data.preferences

import android.content.SharedPreferences
import com.baraa.masroof.application.dashboard.ChartDataPreferences
import com.baraa.masroof.application.dashboard.ChartDataPreferencesRepository
import kotlinx.serialization.json.Json

class SharedPrefsChartDataPreferencesRepository(private val prefs: SharedPreferences) : ChartDataPreferencesRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    override fun load(): ChartDataPreferences = prefs.getString(KEY, null)?.let { raw ->
        runCatching { json.decodeFromString(ChartDataPreferences.serializer(), raw) }.getOrNull()
    } ?: ChartDataPreferences()
    override fun save(value: ChartDataPreferences) {
        prefs.edit().putString(KEY, json.encodeToString(ChartDataPreferences.serializer(), value)).apply()
    }
    companion object { const val PREFS_NAME = "chart_data_preferences"; private const val KEY = "choices_json" }
}
