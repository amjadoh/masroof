package com.baraa.masroof.lab.beta.verification

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.BetaEnvironment
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.corrections.FinancialDecision
import com.baraa.masroof.lab.finance.dashboard.DashboardQuery
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.*
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

/** Test-source-only fixtures: no provider/inbox access, production context or authored parser result. */
object SyntheticFinancialHistory {
    val query = DashboardQuery(Environment.BETA, Instant.parse("2017-01-01T00:00:00Z"),
        Instant.parse("2027-01-01T00:00:00Z"), ZoneId.of("Asia/Riyadh"), Currency.SAR)
    val rate = BigDecimal("3.756957")
    private val bank = BankId("BANK_ALJAZIRA")
    private val facility = OwnedAsset(AssetId(Environment.BETA, bank, "rehearsal-facility"), AssetKind.CREDIT_FACILITY)
    private val card = OwnedAsset(AssetId(Environment.BETA, bank, "rehearsal-card"), AssetKind.CREDIT_CARD, "7271", facility.id, CreditCardRole.PRIMARY)

    fun seed(beta: BetaContainer, count: Int): JSONObject {
        require(count in 40..2_000)
        beta.registerAsset(facility); beta.registerAsset(card)
        beta.registerAsset(OwnedAsset(AssetId(Environment.BETA, bank, "rehearsal-account"), AssetKind.ACCOUNT, "1234"))
        beta.registerAsset(OwnedAsset(AssetId(Environment.BETA, bank, "rehearsal-loan"), AssetKind.LOAN))
        beta.setBankEnabled(bank, true)
        val ids = mutableListOf<String>()
        repeat((count + 49) / 50) { batch ->
            val messages = (batch * 50 until minOf(count, batch * 50 + 50)).map { index ->
                val local = LocalDateTime.of(2018 + index % 8, 1 + index % 12, 1 + index % 27, 13, index % 60)
                val usd = index % 2 == 0
                val money = if (usd) "USD" else "SAR"
                val body = "شراء عبر الانترنت\nبطاقة ائتمانية: 7271\nلدى: Synthetic Merchant ${index % 17}\n" +
                    "بمبلغ: $money ${10 + index % 37}.00\nفي: ${local.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}\nالدولة: USA\n" +
                    (if (usd) "رسوم العمليات الدولية: SAR 1.99\nسعر الصرف: 3.756957\n" else "") +
                    "الرصيد المتاح: SAR 16958.89\nإجمالي المبلغ المستحق: SAR 2694.32"
                EvidenceMessage("synthetic-$index", "AlJazira", body, local.atZone(query.zone).toInstant(), ReceiptTimeSource.USER_SUPPLIED)
            }
            beta.importEvidence(EvidenceRequest("m12-financial", EvidenceOrigin.EXPLICIT_SAMPLE), messages).forEach { result ->
                check(result is EvidenceResult.Stored)
                val id = result.summary.id
                val preview = checkNotNull(beta.financialApprovalPreview(id))
                val request = checkNotNull(betaApprovalDraft(preview, "EXPENSE", "asset:${card.id.localId}", "external", null))
                check(beta.approveEvidenceFinancial(request) is BetaFinancialAdoptionResult.Posted)
                ids += id
            }
        }
        ids.forEachIndexed { index, id ->
            val key = PostingKey(Environment.BETA, "evidence:$id")
            if (index % 20 == 0) {
                val detail = checkNotNull(beta.reviewDetail(key))
                check(beta.saveReview(detail.token, "synthetic-time-$index", FinancialDecision.EditFacts(
                    time = detail.observation.input.time.copy(instant = detail.observation.input.time.instant.plusSeconds(60))))
                    is com.baraa.masroof.lab.beta.application.corrections.BetaCorrectionResult.Applied)
            }
            if (index % 40 == 39) {
                val detail = checkNotNull(beta.reviewDetail(key))
                check(beta.saveReview(detail.token, "synthetic-suppress-$index", FinancialDecision.SuppressAsNonFinancial)
                    is com.baraa.masroof.lab.beta.application.corrections.BetaCorrectionResult.Applied)
            }
        }
        beta.setTheme("DARK"); beta.putPreferenceProbe("synthetic=financial\nثماني سنوات")
        beta.putMarker("upgrade-seed", "Synthetic financial rehearsal only")
        val snapshot = beta.dashboard(query).snapshot
        check(snapshot.transactions.size == count - count / 40)
        check(snapshot.transactions.filter { it.amount.currency == Currency.USD }.all { it.appliedFx?.rateToSar == rate })
        check(beta.correctionHistory(PostingKey(Environment.BETA, "evidence:${ids.first()}")).isNotEmpty())
        val totals = JSONObject()
        Currency.entries.forEach { currency ->
            val amount = snapshot.transactions.filter { it.amount.currency == currency }.fold(BigDecimal.ZERO) { sum, row -> sum + row.amount.amount }
            if (amount.signum() != 0) totals.put(currency.name, amount.toPlainString())
        }
        return JSONObject().put("financialCount", count).put("activeCount", snapshot.transactions.size)
            .put("ids", JSONArray(ids)).put("nativeTotals", totals).put("firstYear", 2018).put("lastYear", 2025)
    }

    fun verify(beta: BetaContainer, expected: JSONObject) {
        check(beta.assets().size == 4)
        val snapshot = beta.dashboard(query).snapshot
        check(snapshot.transactions.size == expected.getInt("activeCount"))
        val totals = expected.getJSONObject("nativeTotals")
        totals.keys().forEach { name ->
            val actual = snapshot.transactions.filter { it.amount.currency.name == name }.fold(BigDecimal.ZERO) { sum, row -> sum + row.amount.amount }
            check(actual.compareTo(totals.getString(name).toBigDecimal()) == 0)
        }
        val first = PostingKey(Environment.BETA, "evidence:${expected.getJSONArray("ids").getString(0)}")
        check(beta.financialPosting(first)?.command?.appliedFx?.rateToSar == rate)
        check(beta.financialHistory(first).size >= 2)
        check(beta.correctionHistory(first).isNotEmpty())
        check(beta.settings().theme == "DARK")
        check(beta.preferenceProbe() == "synthetic=financial\nثماني سنوات")
        check(beta.evidenceSummary(expected.getJSONArray("ids").getString(0)).attempts > 0)
    }

    /** Caller closes the Beta container first. Complete rows/columns, not just counts or balances. */
    fun fingerprint(context: Context): JSONObject {
        require(context.packageName in setOf("com.baraa.masroof.beta.upgrade.rehearsal", "com.baraa.masroof.lab.beta", "com.baraa.masroof.lab.beta.test"))
        val result = JSONObject()
        SQLiteDatabase.openDatabase(context.getDatabasePath(BetaEnvironment.DATABASE_NAME).absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            result.put("schema", db.version)
            db.rawQuery("PRAGMA integrity_check", null).use { check(it.moveToFirst() && it.getString(0) == "ok") }
            db.rawQuery("PRAGMA foreign_key_check", null).use { check(it.count == 0) }
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'beta_%' ORDER BY name", null).use { tables ->
                while (tables.moveToNext()) {
                    val table = tables.getString(0); check(table.matches(Regex("beta_[a-z_]+")))
                    val digest = MessageDigest.getInstance("SHA-256")
                    fun frame(value: ByteArray?) { digest.update(ByteBuffer.allocate(4).putInt(value?.size ?: -1).array()); if (value != null) digest.update(value) }
                    db.rawQuery("SELECT * FROM $table ORDER BY rowid", null).use { rows ->
                        rows.columnNames.forEach { frame(it.toByteArray()) }
                        while (rows.moveToNext()) repeat(rows.columnCount) { column ->
                            frame(if (rows.isNull(column)) null else if (rows.getType(column) == Cursor.FIELD_TYPE_BLOB) rows.getBlob(column) else rows.getString(column).toByteArray())
                        }
                        result.put(table, JSONObject().put("rows", rows.count).put("sha256", digest.digest().joinToString("") { "%02x".format(it) }))
                    }
                }
            }
        }
        val prefs = JSONObject()
        context.getSharedPreferences(BetaEnvironment.PREFS_NAME, Context.MODE_PRIVATE).all.toSortedMap().forEach { (key, value) -> prefs.put(key, value) }
        result.put("preferences", prefs)
        return result
    }
}
