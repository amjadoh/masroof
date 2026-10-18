package com.baraa.masroof.lab.beta.application.evidence

import androidx.room.withTransaction
import com.baraa.masroof.lab.beta.data.*
import com.baraa.masroof.lab.bank.MessageFamily
import com.baraa.masroof.lab.finance.value.Currency
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Clock
import org.json.JSONObject

/** Single current archive plus separately versioned human labels, never a financial correction. */
class BetaInterpretationReviewWorkflow(private val database: BetaDatabase, private val clock: Clock = Clock.systemUTC()) {
    init { require(database.openHelper.databaseName == BetaDatabase.NAME) }

    suspend fun detail(id: String): BetaInterpretationReview? = database.withTransaction {
        val dao = database.evidenceDao()
        val raw = dao.raw(id) ?: return@withTransaction null
        val outcome = dao.outcome(id) ?: return@withTransaction null
        if (dao.retry(id) != null || outcome.baselineJson != "{}") return@withTransaction null
        val json = JSONObject(outcome.candidateJson)
        if (!InterpretationArchive.isCurrent(json) || json.getString("evidenceId") != id) return@withTransaction null
        val route = JSONObject(outcome.route)
        // Sender identity remains the existing persisted route's responsibility.
        if (route.getString("kind") != RouteKind.MATCHED.name || route.getJSONArray("candidates").length() != 1 ||
            route.getJSONArray("candidates").getString(0) != json.optString("bank")) return@withTransaction null
        val spans = json.getJSONArray("evidence")
        val original = (0 until spans.length()).map { index -> spans.getJSONObject(index).let {
            BetaOriginalSpan(it.getString("field"), it.getString("ruleId"), it.getInt("start"), it.getInt("end"))
        } }
        if (original.any { it.ruleId.isBlank() || !validRange(raw.body, it.start, it.end) }) return@withTransaction null
        val facts = json.getJSONObject("facts")
        val amount = facts.optJSONObject("amount")
        val observed = buildMap {
            put(BetaLabelField.FAMILY, facts.getString("family"))
            amount?.let { put(BetaLabelField.AMOUNT, it.getString("amount")); put(BetaLabelField.CURRENCY, it.getString("currency")) }
            listOf(BetaLabelField.SOURCE_ACCOUNT to "sourceAccount", BetaLabelField.DESTINATION_ACCOUNT to "destinationAccount", BetaLabelField.CARD to "card").forEach { (field, key) ->
                facts.optJSONObject(key)?.optString("number")?.takeIf { it.isNotBlank() }?.let { put(field, it) }
            }
        }
        val labels = database.learningDao().history(id).map { BetaTrustedLabel(it.revision, BetaLabelField.valueOf(it.field),
            it.value, it.start, it.end, it.originalText, it.archiveHash, it.interpretationAttempt) }
        val hash = MessageDigest.getInstance("SHA-256").digest(outcome.candidateJson.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        BetaInterpretationReview(BetaInterpretationToken(id, hash, outcome.attempts, labels.lastOrNull()?.revision ?: 0), raw.body,
            json.getString("engineVersion"), json.getString("rulesVersion"), json.getString("status"), observed, original, labels)
    }

    suspend fun save(draft: BetaLabelDraft): BetaTrustedLabel = database.withTransaction {
        require(draft.confirmed) { "Explicit human confirmation required" }
        require(draft.decisionId.isNotBlank() && draft.decisionId.length <= 100)
        val trimmed = draft.value.trim()
        val value = if (draft.field in setOf(BetaLabelField.FAMILY, BetaLabelField.CURRENCY)) trimmed else
            trimmed.map { char -> when (char) {
                in '\u0660'..'\u0669' -> '0' + (char - '\u0660')
                in '\u06f0'..'\u06f9' -> '0' + (char - '\u06f0')
                '\u066b' -> '.'
                else -> char
            } }.joinToString("")
        require(value.length in 1..100)
        when (draft.field) {
            BetaLabelField.FAMILY -> require(MessageFamily.entries.any { it.name == value })
            BetaLabelField.CURRENCY -> require(Currency.entries.any { it.name == value })
            BetaLabelField.AMOUNT -> require(value.matches(Regex("[0-9]{1,18}(\\.[0-9]{1,8})?")) && BigDecimal(value).signum() > 0)
            else -> require(value.matches(Regex("[0-9]{4}")))
        }
        val dao = database.learningDao()
        dao.decision(draft.decisionId)?.let { old ->
            require(old.rawId == draft.token.evidenceId && old.archiveHash == draft.token.archiveHash &&
                old.interpretationAttempt == draft.token.attempt && old.revision == Math.addExact(draft.token.labelRevision, 1) &&
                old.field == draft.field.name && old.value == value && old.start == draft.start && old.end == draft.end) { "Conflicting retry" }
            return@withTransaction old.toLabel()
        }
        val current = requireNotNull(detail(draft.token.evidenceId)) { "Current interpretation unavailable" }
        require(current.token == draft.token) { "Stale review; reopen" }
        require(validRange(current.body, draft.start, draft.end)) { "Select original evidence" }
        val original = current.body.substring(draft.start, draft.end)
        require(original.isNotBlank())
        val row = BetaLearningLabelEntity(draft.token.evidenceId, Math.addExact(draft.token.labelRevision, 1), draft.decisionId,
            draft.token.archiveHash, draft.token.attempt, current.engineVersion, current.rulesVersion,
            draft.field.name, value, draft.start, draft.end, original, "USER_EXPLICIT_ORIGINAL_SPAN_V1", clock.instant().toString())
        dao.append(row)
        row.toLabel()
    }

    private fun BetaLearningLabelEntity.toLabel() = BetaTrustedLabel(revision, BetaLabelField.valueOf(field), value, start, end,
        originalText, archiveHash, interpretationAttempt)

    private fun validRange(body: String, start: Int, end: Int): Boolean = start >= 0 && end > start && end <= body.length &&
        !(start > 0 && body[start].isLowSurrogate() && body[start - 1].isHighSurrogate()) &&
        !(end < body.length && body[end].isLowSurrogate() && body[end - 1].isHighSurrogate())
}
