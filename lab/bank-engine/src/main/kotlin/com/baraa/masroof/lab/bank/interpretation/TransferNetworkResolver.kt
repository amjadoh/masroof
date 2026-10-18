package com.baraa.masroof.lab.bank.interpretation

import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.finance.value.BankId

internal data class TransferNetwork(val kind: BankNetwork?, val source: BankId?, val destination: BankId?)

/** Resolves explicit roles against exact configured aliases. Never routes a sender or owns an asset. */
internal class TransferNetworkResolver(
    private val source: SourceText,
    private val rules: NetworkRules,
    private val observations: Map<String, List<String>>,
    private val evidence: MutableList<Evidence>,
    private val problem: (String, Blocker) -> Unit,
) {
    private var invalid = false
    private fun fail(code: String) { invalid = true; problem(code, Blocker.CONFLICTING_FACTS) }
    private fun key(value: String) = SourceText(value).text.trim().replace(Regex("[ \\t]+"), " ")
    private val owners = buildMap<String, Set<BankId>> {
        rules.bankAliases.forEach { (id, aliases) -> aliases.forEach { alias ->
            val name = key(alias)
            put(name, get(name).orEmpty() + BankId(id))
        } }
    }

    fun resolve(family: MessageFamily, local: BankId, familyPhrases: List<String>): TransferNetwork {
        val transfer = family in setOf(MessageFamily.TRANSFER_IN, MessageFamily.TRANSFER_OUT)
        val reader = RoleLineReader(source, mapOf("sourceBank" to rules.sourceBankLabels,
            "destinationBank" to rules.destinationBankLabels, "bankNetwork" to rules.networkLabels), evidence)
        fun bank(rows: List<RoleLine>): BankId? {
            val ids = rows.map { row ->
                val candidates = owners[key(row.normalized)].orEmpty()
                if (candidates.size != 1) { fail("NETWORK_ALIAS_NOT_UNIQUE"); null } else candidates.single()
            }.filterNotNull().distinct()
            if (ids.size > 1) fail("NETWORK_ROLE_CONFLICT")
            return ids.singleOrNull()
        }
        val sourceRows = reader.read("sourceBank")
        val destinationRows = reader.read("destinationBank").toMutableList()
        if (rules.standaloneDestinationBrackets) {
            val lines = source.text.lineSequence().toList()
            val first = lines.indexOfFirst { it.any(Char::isLetterOrDigit) }
            val next = if (first >= 0) (first + 1 until lines.size).firstOrNull { lines[it].isNotBlank() } else null
            var offset = 0
            lines.forEachIndexed { index, line ->
                val match = Regex("^ *\\[([^\\[\\]]+)] *$").matchEntire(line)
                if (match != null) {
                    val title = first >= 0 && familyPhrases.any { source.phrase(it).find(lines[first].trimStart())?.range?.first == 0 }
                    if (family != MessageFamily.TRANSFER_OUT || !title || index != next) fail("UNBOUND_DESTINATION_BRACKET")
                    else {
                        val range = offset..(offset + line.length - 1)
                        evidence += source.evidence("extract.network.bracket", "destinationBank", range)
                        destinationRows += RoleLine(match.groupValues[1], source.original((offset + match.groups[1]!!.range.first)..(offset + match.groups[1]!!.range.last)), range)
                    }
                }
                offset += line.length + 1
            }
        }
        val sourceBank = bank(sourceRows)
        val destinationBank = bank(destinationRows)
        val networks = linkedSetOf<BankNetwork>()
        val networkRows = reader.read("bankNetwork")
        networkRows.forEach { row ->
            val value = key(row.normalized)
            val intra = value in (observations["network.intra"].orEmpty().map(::key) + "intra_bank")
            val inter = value in (observations["network.inter"].orEmpty().map(::key) + "inter_bank")
            if (intra == inter) fail("NETWORK_MARKER_NOT_UNIQUE")
            else networks += if (intra) BankNetwork.INTRA_BANK else BankNetwork.INTER_BANK
        }
        if (!transfer) {
            if (sourceRows.isNotEmpty() || destinationRows.isNotEmpty() || networkRows.isNotEmpty()) fail("NETWORK_OUTSIDE_TRANSFER")
            return TransferNetwork(null, null, null)
        }
        if (family == MessageFamily.TRANSFER_OUT) {
            if (sourceBank != null && sourceBank != local) fail("OUTGOING_SOURCE_BANK_NOT_LOCAL")
            destinationBank?.let { networks += if (it == local) BankNetwork.INTRA_BANK else BankNetwork.INTER_BANK }
        } else {
            if (destinationBank != null && destinationBank != local) fail("INCOMING_DESTINATION_BANK_NOT_LOCAL")
            sourceBank?.let { networks += if (it == local) BankNetwork.INTRA_BANK else BankNetwork.INTER_BANK }
        }
        if (networks.size != 1) fail("NETWORK_MISSING_OR_CONFLICTING")
        return TransferNetwork(if (invalid) null else networks.singleOrNull(), sourceBank, destinationBank)
    }
}
