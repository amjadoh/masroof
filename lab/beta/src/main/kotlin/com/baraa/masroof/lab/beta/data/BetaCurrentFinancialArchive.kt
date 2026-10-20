package com.baraa.masroof.lab.beta.data

import com.baraa.masroof.lab.beta.application.evidence.InterpretationArchive
import org.json.JSONObject

/** One read boundary for money and persisted observations. Historical comparisons remain on disk,
 * but cannot become new financial authority. Reads never invoke an interpreter or change data. */
internal object BetaCurrentFinancialArchive {
    fun read(outcome: BetaEvidenceOutcomeEntity, body: String, financial: Boolean = true,
        durableReceipt: java.time.Instant? = null): JSONObject {
        require(outcome.baselineJson == "{}")
        val json = JSONObject(outcome.candidateJson)
        require(InterpretationArchive.isCurrent(json) && json.getString("evidenceId") == outcome.rawId)
        require(json.getString("bank") == "BANK_ALJAZIRA")
        val route = JSONObject(outcome.route)
        require(route.getString("kind") == "MATCHED" && route.getJSONArray("candidates").let {
            it.length() == 1 && it.getString(0) == json.getString("bank") })
        val spans = json.getJSONArray("evidence")
        val fields = (0 until spans.length()).map { index -> spans.getJSONObject(index).let {
            val start = it.getInt("start"); val end = it.getInt("end")
            require(it.getString("ruleId").isNotBlank() && start >= 0 && end > start && end <= body.length)
            require(!(start > 0 && body[start].isLowSurrogate() && body[start - 1].isHighSurrogate()))
            require(!(end < body.length && body[end].isLowSurrogate() && body[end - 1].isHighSurrogate()))
            require(body.substring(start, end).isNotBlank())
            it.getString("field")
        } }.toSet()
        require("family" in fields)
        if (financial) {
            require(json.getString("status") == "SUCCESS")
            val receiptBound = durableReceipt != null && json.getJSONObject("facts").optString("occurredAt")==durableReceipt.toString() &&
                json.getJSONArray("diagnostics").let {d -> (0 until d.length()).any {
                    d.getJSONObject(it).optString("code")=="DURABLE_RECEIPT_TIME" && d.getJSONObject(it).optString("severity")=="INFO"}}
            require("AMOUNT" in fields && ("occurredAtLocal" in fields || receiptBound))
        }
        return json
    }
}

/** A user-supplied sample date cannot acquire durable SMS authority by matching JSON. */
internal fun BetaRawEvidenceEntity.durableReceiptTime(): java.time.Instant? =
    if(origin in setOf("LIVE","EXPLICIT_HISTORY") && timeSource in setOf("PROVIDER_TIMESTAMP","SERVICE_CENTER_TIMESTAMP","DEVICE_FALLBACK"))
        java.time.Instant.parse(receivedAt) else null
