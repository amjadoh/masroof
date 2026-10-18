package com.baraa.masroof.lab.beta.application

import com.baraa.masroof.lab.beta.application.evidence.EvidenceRoute
import com.baraa.masroof.lab.beta.application.evidence.RouteKind
import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.value.*
import org.json.JSONObject
import java.util.UUID

/** Observation only. Neither this value nor its opaque proposal identity establishes ownership. */
data class BetaAssetProposal(val id: AssetId, val kind: AssetKind, val last4: String, val evidenceId: String, val revision: String, val confirmedAssetId: AssetId? = null) {
    override fun toString() = "BetaAssetProposal(redacted)"
}
data class BetaDiscoveryPage(val proposals: List<BetaAssetProposal>, val afterId: String?, val needsReview: Int)

/** Reads the persisted archive, never reparses SMS. Partial financial parsing may still identify an asset. */
object BetaAssetDiscovery {
    fun proposals(id: String, route: EvidenceRoute, archive: String, requireFieldProof: Boolean = true, body: String? = null): List<BetaAssetProposal> = try {
        if (route.kind != RouteKind.MATCHED) emptyList() else decode(id, route.candidates.single(), JSONObject(archive), requireFieldProof, body)
    } catch (_: Exception) { emptyList() } // Unsupported/malformed archives remain in evidence review.

    private fun decode(id: String, bank: BankId, json: JSONObject, requireFieldProof: Boolean, body: String?): List<BetaAssetProposal> {
        require(json.get("archiveVersion") == 1 && json.get("contractVersion") == InterpretationVersion.CURRENT_CONTRACT)
        require(json.getString("evidenceId") == id && json.getString("bank") == bank.value)
        require(BetaBankCatalog.current.contains(bank))
        val status = ParseStatus.valueOf(json.getString("status"))
        if (status in setOf(ParseStatus.INVALID, ParseStatus.UNSUPPORTED)) return emptyList()
        val blockers = json.getJSONArray("blockers")
        val allowed = setOf(Blocker.VALIDATION_ERROR.name) // Only typed amount-only findings below may coexist with identity.
        if ((0 until blockers.length()).any { blockers.getString(it) !in allowed }) return emptyList()
        val diagnostics = json.getJSONArray("diagnostics")
        if ((0 until diagnostics.length()).any {
                val code = diagnostics.getJSONObject(it).getString("code")
                code.contains("SECURITY") || code.contains("OTP") || code.contains("UNEXECUTED") || code.contains("CONFLICT")
            }) return emptyList()
        val amountOnlyCodes = setOf("AMOUNT_REQUIRED", "MISSING_AMOUNT", "MISSING_CURRENCY")
        if (blockers.length() > 0 && (diagnostics.length() == 0 || (0 until diagnostics.length()).any {
                diagnostics.getJSONObject(it).getString("code") !in amountOnlyCodes })) return emptyList()
        val spans = json.getJSONArray("evidence")
        val current = com.baraa.masroof.lab.beta.application.evidence.InterpretationArchive.isCurrent(json)
        fun role(field: String) = if (!current) field else when (field) {
            "SOURCE_ACCOUNT" -> "source"; "DESTINATION_ACCOUNT" -> "destination"; "CARD" -> "card"
            "observation.card.credit" -> "card.credit"; "observation.card.debit" -> "card.debit"
            "observation.card.statement" -> "card.statement"; else -> field
        }
        val fields = (0 until spans.length()).mapNotNull { index -> spans.getJSONObject(index).let {
            if (it.getInt("start") >= 0 && it.getInt("end") > it.getInt("start") && it.getInt("end") <= (body?.length ?: 16_000) && it.getString("ruleId").isNotBlank()) role(it.getString("field")) else null
        } }.toSet()
        fun proven(vararg roles: String) = !requireFieldProof || roles.any { it in fields }
        fun provenSuffix(number: String?, vararg roles: String): Boolean {
            if (!requireFieldProof) return true
            if (!proven(*roles)) return false
            if (body == null) return true // Pure contract tests; storage always supplies its immutable captured body.
            return (0 until spans.length()).any { index -> spans.getJSONObject(index).let { span ->
                val start = span.getInt("start"); val end = span.getInt("end")
                role(span.getString("field")) in roles && start >= 0 && end > start && end <= body.length &&
                    normalizeManualAssetSuffix(body.substring(start, end).trimStart('*', 'x', 'X')) == number
            } }
        }
        val f = json.getJSONObject("facts")
        val family = MessageFamily.valueOf(f.getString("family"))
        if (family in setOf(MessageFamily.OTP, MessageFamily.NON_FINANCIAL, MessageFamily.UNKNOWN)) return emptyList()
        val execution = f.text("executionState")
        if (execution != null && execution != "EXECUTED") return emptyList()
        fun suffix(value: String?) = value?.takeIf { it.matches(Regex("[0-9]{4}")) }
        fun reference(field: String): String? {
            if (f.isNull(field)) return null
            val value = f.getJSONObject(field)
            val number = suffix(value.text("number"))
            return if (number != null && provenSuffix(number, if (field == "sourceAccount") "source" else "destination") && value.text("bank") == bank.value) number else null
        }
        fun proposal(role: String, kind: AssetKind, last4: String) = BetaAssetProposal(
            AssetId(Environment.BETA, bank, UUID.nameUUIDFromBytes(("beta-proposal-v1|${bank.value}|$id|$role").toByteArray(Charsets.UTF_8)).toString()), kind, last4, id, java.security.MessageDigest.getInstance("SHA-256").digest(json.toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
        return buildList {
            // Incoming source and outgoing destination are counterparties, even for intra-bank transfers.
            if (family != MessageFamily.TRANSFER_IN) reference("sourceAccount")?.let { add(proposal("sourceAccount", AssetKind.ACCOUNT, it)) }
            if (family == MessageFamily.TRANSFER_IN) reference("destinationAccount")?.let { add(proposal("destinationAccount", AssetKind.ACCOUNT, it)) }
            suffix(f.text("debitSourceAccountLast4"))?.takeIf { provenSuffix(it, "source", "debitSourceAccountLast4") && family in setOf(MessageFamily.PURCHASE, MessageFamily.REFUND, MessageFamily.WITHDRAWAL) }
                ?.let { if (none { p -> p.kind == AssetKind.ACCOUNT && p.last4 == it }) add(proposal("debitSourceAccount", AssetKind.ACCOUNT, it)) }
            if (!f.isNull("card")) {
                val card = f.getJSONObject("card")
                val channel = f.text("cardChannel")
                val kind = when (channel) { "DEBIT" -> AssetKind.DEBIT_CARD; "CREDIT", "STATEMENT" -> AssetKind.CREDIT_CARD; else -> null }
                // A card number alone, OTP digits, IBAN, balance, or transaction reference cannot supply a card kind.
                val channelProven = when (channel) {
                    "DEBIT" -> proven("card.debit")
                    "CREDIT" -> proven("card.credit", "balance") && (!requireFieldProof || "card.debit" !in fields)
                    "STATEMENT" -> proven("card.statement") && (!requireFieldProof || "card.debit" !in fields)
                    else -> false
                }
                if (kind != null && proven("card") && channelProven && card.text("bank") == bank.value) suffix(card.text("number"))?.takeIf { provenSuffix(it, "card") }?.let { add(proposal("card", kind, it)) }
            }
        }
    }
    private fun JSONObject.text(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return (get(key) as? String) ?: throw IllegalArgumentException("Invalid archived field type")
    }
}
