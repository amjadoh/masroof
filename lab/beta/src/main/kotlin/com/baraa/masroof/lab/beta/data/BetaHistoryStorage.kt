package com.baraa.masroof.lab.beta.data

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.baraa.masroof.lab.beta.application.history.*
import com.baraa.masroof.lab.finance.value.BankId
import org.json.JSONArray
import org.json.JSONObject

internal class PreferenceHistorySessionStore(private val preferences: SharedPreferences) : HistorySessionStore {
    override fun load(): HistorySession? = preferences.getString(KEY, null)?.let { text ->
        val j = JSONObject(text)
        check(j.getInt("version") == 1)
        val banks = j.getJSONArray("banks")
        HistorySession(j.getLong("from"), j.getLong("until"), (0 until banks.length()).map { BankId(banks.getString(it)) }.toSet(),
            j.getLong("date"), j.getLong("id"), j.getInt("scanned"), j.getInt("stored"), j.getInt("excluded"),
            j.getInt("conflicts"), j.getInt("retry"), j.getBoolean("finished")).also { session ->
            check(session.banks.all { bank -> com.baraa.masroof.lab.beta.application.BetaBankCatalog.current.contains(bank) })
        }
    }
    override fun save(session: HistorySession) {
        val json = JSONObject().put("version", 1).put("from", session.from).put("until", session.until)
            .put("banks", JSONArray(session.banks.map { it.value }.sorted())).put("date", session.afterDate).put("id", session.afterId)
            .put("scanned", session.scanned).put("stored", session.stored).put("excluded", session.excluded)
            .put("conflicts", session.conflicts).put("retry", session.retry).put("finished", session.finished)
        check(preferences.edit().putString(KEY, json.toString()).commit()) { "Beta import checkpoint failed" }
    }
    companion object { const val KEY = "beta_history_session_v1" }
}

/** Read-only inbox access happens only after the explicit user action and permission grant. */
internal class AndroidHistorySource(private val context: Context) : HistorySource {
    override fun read(session: HistorySession, limit: Int): List<HistoryRow> {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
        // Keyset predicate once per batch; consume at most limit rows and close the cursor.
        val cursor = context.contentResolver.query(Uri.parse("content://sms/inbox"), arrayOf("_id", "date", "address", "body"),
            "date >= ? AND date <= ? AND (date > ? OR (date = ? AND _id > ?))",
            arrayOf(session.from.toString(), session.until.toString(), session.afterDate.toString(), session.afterDate.toString(), session.afterId.toString()),
            "date ASC, _id ASC") ?: error("Beta inbox unavailable")
        return cursor.use { c -> buildList {
            while (c.moveToNext() && size < limit) add(HistoryRow(c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), c.getString(3).orEmpty()))
        } }
    }
}
