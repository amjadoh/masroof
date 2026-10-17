package com.baraa.masroof.lab.beta.data

import com.baraa.masroof.lab.beta.application.corrections.BetaLedgerActivity
import com.baraa.masroof.lab.finance.corrections.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.security.MessageDigest
import java.util.Collections
import org.json.JSONArray
import org.json.JSONObject

internal data class PersistedBetaCorrection(val overlay: DecisionOverlay, val activity: BetaLedgerActivity,
    val transition: Long, val reasons: Set<ReviewReason>)
internal data class BetaCorrectionOperation(val observation: CorrectionObservation, val request: CorrectionRequest?, val expectedCorrection: Long)

/** Explicit versions/checksums catch accidental corruption, not maliciously authenticated backups. */
internal object BetaCorrectionCodec {
    fun encode(value: PersistedBetaCorrection): String {
        val state = CorrectionSnapshots.export(value.overlay)
        return envelope("BETA_CORRECTION_1", obj("key" to key(state.key), "correction" to state.correctionRevision.toString(),
            "interpretation" to state.interpretationRevision.toString(), "original" to BetaLedgerCodec.encodeInput(state.original),
            "latest" to observation(state.latestObservation), "safetyHold" to state.safetyHold, "metadata" to metadata(state.metadata),
            "history" to JSONArray(state.history.map { request(it) }), "financial" to state.financial?.let { BetaLedgerCodec.encodeInput(it) },
            "suppressed" to state.suppressed, "activity" to value.activity.name, "transition" to value.transition.toString(),
            "reasons" to JSONArray(value.reasons.map { it.name }.sorted()), "seed" to state.seedPosting?.let { BetaLedgerCodec.encode(it) }))
    }

    fun decode(payload: String): PersistedBetaCorrection {
        val root = body(payload, "BETA_CORRECTION_1")
        root.fields("key", "correction", "interpretation", "original", "latest", "safetyHold", "metadata", "history", "financial", "suppressed", "activity", "transition", "reasons", "seed")
        val history = root.array("history").objects().map { request(it) }
        val state = CorrectionOverlaySnapshot(key(root.objectValue("key")), root.long("correction"), root.long("interpretation"),
            BetaLedgerCodec.decodeInput(root.text("original")), observation(root.objectValue("latest")), root.bool("safetyHold"),
            metadata(root.objectValue("metadata")), history, root.optionalText("financial")?.let { BetaLedgerCodec.decodeInput(it) }, root.bool("suppressed"),
            root.optionalText("seed")?.let { BetaLedgerCodec.decode(it) })
        val overlay = CorrectionSnapshots.restore(state)
        val activity = BetaLedgerActivity.valueOf(root.text("activity"))
        val reasons = root.array("reasons").strings().map { ReviewReason.valueOf(it) }.toSet()
        check(reasons.size == root.array("reasons").length())
        val transition = root.long("transition"); check(transition > 0)
        check(when (activity) {
            BetaLedgerActivity.ACTIVE -> !state.suppressed && !state.safetyHold && reasons.isEmpty()
            BetaLedgerActivity.SUPPRESSED -> state.suppressed && !state.safetyHold && reasons.isEmpty()
            BetaLedgerActivity.REVIEW -> reasons.isNotEmpty()
        }) { "Inconsistent Beta correction activity" }
        return PersistedBetaCorrection(overlay, activity, transition, Collections.unmodifiableSet(LinkedHashSet(reasons)))
    }

    fun encodeOperation(value: BetaCorrectionOperation): String = envelope("BETA_CORRECTION_OPERATION_1",
        obj("observation" to observation(value.observation), "request" to value.request?.let { request(it) }, "expectedCorrection" to value.expectedCorrection.toString()))
    fun decodeOperation(payload: String): BetaCorrectionOperation {
        val root = body(payload, "BETA_CORRECTION_OPERATION_1"); root.fields("observation", "request", "expectedCorrection")
        return BetaCorrectionOperation(observation(root.objectValue("observation")), if (root.isNull("request")) null else request(root.objectValue("request")), root.long("expectedCorrection"))
    }

    private fun observation(value: CorrectionObservation) = obj("input" to BetaLedgerCodec.encodeInput(value.input), "safety" to value.safety.name)
    private fun observation(value: JSONObject): CorrectionObservation {
        value.fields("input", "safety")
        return CorrectionObservation(BetaLedgerCodec.decodeInput(value.text("input")), ObservationSafety.valueOf(value.text("safety")))
    }
    private fun request(value: CorrectionRequest): JSONObject {
        val decision = when (val choice = value.decision) {
            FinancialDecision.Keep -> obj("kind" to "KEEP")
            FinancialDecision.SuppressAsNonFinancial -> obj("kind" to "SUPPRESS")
            is FinancialDecision.EditFacts -> obj("kind" to "FACTS", "time" to choice.time?.instant?.toString(),
                "zone" to choice.time?.zone?.id, "source" to choice.source?.let(::endpoint),
                "destination" to choice.destination?.let(::endpoint))
            is FinancialDecision.ResolveFinancialType -> obj("kind" to "TYPE", "type" to choice.type.name)
            is FinancialDecision.ConfirmFamilyOrAmount -> obj("kind" to "CONFIRM", "type" to choice.type?.name,
                "amount" to choice.amount?.amount?.toString(), "currency" to choice.amount?.currency?.name)
        }
        return obj("decisionId" to value.decisionId, "key" to key(value.key), "correction" to value.correctionRevision.toString(),
            "interpretation" to value.expectedInterpretation.toString(), "evidence" to JSONArray(value.expectedEvidenceIds.sorted()),
            "decision" to decision, "metadata" to metadata(value.metadata), "expectedCorrection" to value.expectedCorrectionRevision.toString())
    }
    private fun request(value: JSONObject): CorrectionRequest {
        value.fields("decisionId", "key", "correction", "interpretation", "evidence", "decision", "metadata", "expectedCorrection")
        val decision = value.objectValue("decision")
        val choice = when (decision.text("kind")) {
            "KEEP" -> { decision.fields("kind"); FinancialDecision.Keep }
            "SUPPRESS" -> { decision.fields("kind"); FinancialDecision.SuppressAsNonFinancial }
            "TYPE" -> { decision.fields("kind", "type"); FinancialDecision.ResolveFinancialType(FinancialType.valueOf(decision.text("type"))) }
            "FACTS" -> {
                decision.fields("kind", "time", "zone", "source", "destination")
                val time = decision.optionalText("time"); val zone = decision.optionalText("zone")
                check((time == null) == (zone == null))
                FinancialDecision.EditFacts(time?.let { TransactionTime(java.time.Instant.parse(it), java.time.ZoneId.of(requireNotNull(zone))) },
                    if (decision.isNull("source")) null else endpoint(decision.objectValue("source")),
                    if (decision.isNull("destination")) null else endpoint(decision.objectValue("destination")))
            }
            "CONFIRM" -> {
                decision.fields("kind", "type", "amount", "currency")
                val amount = decision.optionalText("amount"); val currency = decision.optionalText("currency")
                check((amount == null) == (currency == null))
                FinancialDecision.ConfirmFamilyOrAmount(decision.optionalText("type")?.let { FinancialType.valueOf(it) },
                    amount?.let { Money.of(it, Currency.valueOf(requireNotNull(currency))) })
            }
            else -> error("Unknown Beta correction decision")
        }
        val evidence = value.array("evidence").strings(); check(evidence.distinct().size == evidence.size)
        return CorrectionRequest(value.text("decisionId"), key(value.objectValue("key")), value.long("correction"), value.long("interpretation"),
            Collections.unmodifiableSet(LinkedHashSet(evidence)), choice, metadata(value.objectValue("metadata")), value.long("expectedCorrection"))
    }
    private fun endpoint(value: com.baraa.masroof.lab.finance.classification.FinancialEndpoint): JSONObject = when (value) {
        com.baraa.masroof.lab.finance.classification.FinancialEndpoint.External -> obj("kind" to "EXTERNAL")
        com.baraa.masroof.lab.finance.classification.FinancialEndpoint.Unresolved -> obj("kind" to "UNRESOLVED")
        is com.baraa.masroof.lab.finance.classification.FinancialEndpoint.Owned -> obj("kind" to "OWNED", "environment" to value.id.environment.name, "bank" to value.id.bank.value, "id" to value.id.localId)
    }
    private fun endpoint(value: JSONObject): com.baraa.masroof.lab.finance.classification.FinancialEndpoint = when (value.text("kind")) {
        "EXTERNAL" -> { value.fields("kind"); com.baraa.masroof.lab.finance.classification.FinancialEndpoint.External }
        "UNRESOLVED" -> { value.fields("kind"); com.baraa.masroof.lab.finance.classification.FinancialEndpoint.Unresolved }
        "OWNED" -> { value.fields("kind", "environment", "bank", "id"); com.baraa.masroof.lab.finance.classification.FinancialEndpoint.Owned(com.baraa.masroof.lab.finance.asset.AssetId(Environment.valueOf(value.text("environment")), BankId(value.text("bank")), value.text("id"))) }
        else -> error("Unknown endpoint")
    }
    private fun key(value: PostingKey) = obj("environment" to value.environment.name, "id" to value.transactionId)
    private fun key(value: JSONObject): PostingKey {
        value.fields("environment", "id")
        return PostingKey(Environment.valueOf(value.text("environment")), value.text("id")).also { check(it.environment == Environment.BETA) }
    }
    private fun metadata(value: Map<String, String>) = JSONObject().apply { value.toSortedMap().forEach { (key, text) -> put(key, text) } }
    private fun metadata(value: JSONObject): Map<String, String> = Collections.unmodifiableMap(
        value.keys().asSequence().associateWith { value.text(it) }.also { check(it.keys.all { key -> key in setOf("merchant", "counterparty", "note") }) })
    private fun envelope(version: String, value: JSONObject): String {
        val data = value.toString()
        return obj("version" to version, "checksum" to hash(data), "body" to data).toString()
    }
    private fun body(payload: String, version: String): JSONObject {
        val envelope = JSONObject(payload); envelope.fields("version", "checksum", "body")
        check(envelope.text("version") == version) { "Unsupported Beta correction payload" }
        val data = envelope.text("body"); check(hash(data) == envelope.text("checksum")) { "Corrupt Beta correction payload" }
        return JSONObject(data)
    }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun obj(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) } }
    private fun JSONObject.text(key: String) = get(key) as String
    private fun JSONObject.optionalText(key: String): String? = if (isNull(key)) null else text(key)
    private fun JSONObject.long(key: String) = text(key).toLong().also { check(it >= 0) }
    private fun JSONObject.bool(key: String) = get(key) as Boolean
    private fun JSONObject.objectValue(key: String) = get(key) as JSONObject
    private fun JSONObject.array(key: String) = get(key) as JSONArray
    private fun JSONObject.fields(vararg expected: String) { check(keys().asSequence().toSet() == expected.toSet()) }
    private fun JSONArray.strings() = List(length()) { get(it) as String }
    private fun JSONArray.objects() = List(length()) { get(it) as JSONObject }
}
