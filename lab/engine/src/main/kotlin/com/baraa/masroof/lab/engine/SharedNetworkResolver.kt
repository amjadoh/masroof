package com.baraa.masroof.lab.engine

import com.baraa.masroof.lab.copy.core.text.ArabicTextFolding
import java.util.Locale

internal data class NetworkEvidence(val label: String, val range: IntRange, val identity: String)
internal data class NetworkResolution(val network: String, val errors: List<String>, val evidence: List<NetworkEvidence>)

/** Exact aliases + explicit role lines. Knows no bank IDs, financial treatment or asset ownership. */
internal object SharedNetworkResolver {
    fun resolve(text: String, localBankId: String, incoming: Boolean, config: NetworkRuleConfig,
                intraPhrases: List<String>, interPhrases: List<String>, transferPhrases: List<String>): NetworkResolution {
        val errors = mutableListOf<String>()
        val evidence = mutableListOf<NetworkEvidence>()
        val networks = linkedSetOf<String>()
        val identities = linkedSetOf<String>()
        val aliases = config.bankAliases.mapValues { (_, names) -> names.map(::key).toSet() }
        val labels = if (incoming) config.sourceBankLabels else config.destinationBankLabels
        val aliasOwners = mutableMapOf<String, MutableSet<String>>()
        aliases.forEach { (id, names) -> names.forEach { aliasOwners.getOrPut(it) { linkedSetOf() } += id } }
        val rolePatterns = labels.map { it to Regex("^" + Regex.escape(key(it)) + "[ \\t]*[:：][ \\t]*(?<name>.*)$") }
        val networkPatterns = config.networkLabels.map { Regex("^" + Regex.escape(key(it)) + "[ \\t]*[:：]") }
        val titlePatterns = transferPhrases.map { Regex("^" + Regex.escape(key(it)) + "(?![\\p{L}\\p{N}_])") }
        fun literalPatterns(terms: List<String>) = terms.map { it to Regex("(?<![\\p{L}\\p{N}_])" + Regex.escape(key(it)) + "(?![\\p{L}\\p{N}_])") }
        val intraPatterns = literalPatterns(intraPhrases)
        val interPatterns = literalPatterns(interPhrases)

        fun name(value: String, label: String, range: IntRange) {
            val candidates = aliasOwners[key(value)].orEmpty()
            when {
                candidates.isEmpty() -> errors += "NETWORK_UNKNOWN_BANK_NAME"
                candidates.size > 1 -> errors += "NETWORK_AMBIGUOUS_BANK_ALIAS"
                localBankId !in aliases -> errors += "NETWORK_LOCAL_IDENTITY_UNCONFIGURED"
                else -> {
                    val identity = candidates.single()
                    identities += identity
                    evidence += NetworkEvidence(label, range, identity)
                    networks += if (identity == localBankId) "INTRA_BANK" else "INTER_BANK"
                }
            }
        }

        var offset = 0
        var fenced = false
        var quoted = false
        var quoteBlock = false
        val titleStart = text.indexOfFirst { !it.isWhitespace() }
        text.lineSequence().forEach { line ->
            val leading = line.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: line.length
            val trimmed = line.trim()
            val start = offset + leading
            offset += line.length + 1
            if (trimmed.startsWith("```")) { fenced = !fenced; return@forEach }
            if (fenced) return@forEach
            if (quoted) {
                if (trimmed.endsWith('"') || trimmed.endsWith('”') || trimmed.endsWith('»') || trimmed.endsWith('\'')) quoted = false
                return@forEach
            }
            if (trimmed.isEmpty()) { quoteBlock = false; return@forEach }
            if (trimmed in QUOTE_INTRODUCERS) {
                quoteBlock = true; return@forEach
            }
            if (quoteBlock || trimmed.startsWith('>')) return@forEach
            if (trimmed.first() in QUOTE_STARTS) {
                quoted = !(trimmed.length > 1 && trimmed.last() in QUOTE_ENDS)
                return@forEach
            }
            // Inline quotes cannot supply network evidence either. Skipping their
            // entire line is conservative; it never turns a quoted bank into ours.
            if (trimmed.any { it in QUOTE_CHARS }) return@forEach
            val networkLine = networkPatterns.any { it.containsMatchIn(trimmed) }
            val transferTitle = start == titleStart && titlePatterns.any { it.containsMatchIn(trimmed) }
            val unrelatedValueLine = !networkLine && !transferTitle && VALUE_LINE.containsMatchIn(trimmed)
            val negated = !unrelatedValueLine && NEGATION.containsMatchIn(trimmed)
            fun explicit(patterns: List<Pair<String, Regex>>, network: String) = patterns.forEach { (phrase, pattern) ->
                pattern.findAll(trimmed).forEach matchLoop@{ match ->
                    if (unrelatedValueLine) return@matchLoop
                    if (negated) errors += "NETWORK_NEGATED_OBSERVATION" else if (networkLine || transferTitle) {
                        networks += network
                        evidence += NetworkEvidence(phrase, (start + match.range.first)..(start + match.range.last), network)
                    }
                }
            }
            if (networkLine || transferTitle || negated) {
                explicit(intraPatterns, "INTRA_BANK")
                explicit(interPatterns, "INTER_BANK")
            }
            rolePatterns.forEach labelLoop@{ (label, pattern) ->
                val match = pattern.matchEntire(trimmed) ?: return@labelLoop
                val group = match.groups["name"]!!
                name(group.value, label, (start + group.range.first)..(start + group.range.last))
            }
            if (!incoming && config.standaloneDestinationBrackets) {
                val match = BRACKETED_BANK.matchEntire(trimmed)
                if (match != null) {
                    val group = match.groups["name"]!!
                    name(group.value, "standalone_destination_bank", (start + group.range.first)..(start + group.range.last))
                }
            }
        }
        // Two distinct external banks are conflicting identity evidence even though
        // both individually suggest INTER_BANK. No registration-order tie-break.
        if (identities.size > 1) errors += "NETWORK_CONFLICTING_BANK_NAMES"
        if (networks.size > 1) errors += "NETWORK_CONFLICTING_OBSERVATIONS"
        return NetworkResolution(if (errors.isEmpty()) networks.singleOrNull() ?: "UNKNOWN" else "UNKNOWN", errors.distinct(), evidence)
    }

    private fun key(value: String) = ArabicTextFolding.foldForComparison(java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC))
        .trim().replace(WHITESPACE, " ").lowercase(Locale.ROOT)

    private val NEGATION = Regex("(?<![\\p{L}\\p{N}_])(?:not|no|ليس|ليست|غير|لم)(?![\\p{L}\\p{N}_])")
    private val VALUE_LINE = Regex("^[^:：]{1,100}[:：]")
    private val BRACKETED_BANK = Regex("^\\[(?<name>[^\\[\\]]{1,100})]$")
    private val WHITESPACE = Regex("\\s+")
    private val QUOTE_STARTS = setOf('"', '“', '«', '\'')
    private val QUOTE_ENDS = setOf('"', '”', '»', '\'')
    private val QUOTE_CHARS = QUOTE_STARTS + QUOTE_ENDS
    private val QUOTE_INTRODUCERS = setOf("quote:", "quoted text:", "اقتباس:", "نص مقتبس:")
}
