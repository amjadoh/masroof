package com.baraa.masroof.lab.bank.interpretation

import com.baraa.masroof.lab.bank.*

internal data class ObservedFacts(val card: CardChannel?, val purchase: PurchaseChannel?,
    val loan: LoanType?, val salary: Boolean?, val balanceMarker: Boolean)

/** Configured literal observations on title/typed role lines, never merchant/counterparty text. */
internal class ObservationExtractor(
    private val source: SourceText,
    private val configured: Map<String, List<String>>,
    private val family: MessageFamily,
    private val familyPhrases: List<String>,
    private val evidence: MutableList<Evidence>,
    private val problem: (String, Blocker) -> Unit,
) {
    private val headers = roleHeaders()
    internal companion object {
    fun roleHeaders() = mapOf(
        "observation.cardType" to listOf("card type", "نوع البطاقة"),
        "observation.purchaseChannel" to listOf("purchase channel", "channel", "قناة الشراء"),
        "observation.loanType" to listOf("loan type", "نوع التمويل"),
        "observation.incomeType" to listOf("income type", "نوع الدخل"),
    )
    }
    private fun fail(code: String) = problem(code, Blocker.CONFLICTING_FACTS)
    private fun key(value: String) = SourceText(value).text.trim().replace(Regex("[ \\t]+"), " ")

    fun read(): ObservedFacts {
        val reader = RoleLineReader(source, headers, evidence)
        val physical = evidence.filter { it.field in setOf("CARD", "SOURCE_ACCOUNT", "AVAILABLE_BALANCE", "counterparty") }.toList()
        val first = source.text.indexOfFirst(Char::isLetterOrDigit)
        val titleStart = if (first < 0) -1 else source.text.lastIndexOf('\n', first).let { it + 1 }
        fun scoped(range: IntRange, boundField: String?): Boolean {
            val start = source.text.lastIndexOf('\n', range.first).let { it + 1 }
            val end = source.text.indexOf('\n', range.last).let { if (it < 0) source.text.length else it }
            val line = source.text.substring(start, end).trim().removeSuffix(":")
            val title = start == titleStart && evidence.any { it.field == "family" &&
                it.start >= source.evidence("scope", "scope", start..(end - 1)).start &&
                it.end <= source.evidence("scope", "scope", start..(end - 1)).end } &&
                familyPhrases.any { source.phrase(it).containsMatchIn(line) }
            val lineRange = source.evidence("scope", "scope", start..(end - 1))
            val bound = boundField != null && physical.any { it.field == boundField && it.start >= lineRange.start && it.end <= lineRange.end }
            return title || bound
        }
        fun choose(keys: List<String>, header: String, allowed: Set<MessageFamily>, boundField: String? = null): String? {
            val found = linkedSetOf<String>()
            val typed = linkedSetOf<String>()
            reader.read(header).forEach { row ->
                val candidates = keys.filter { id -> configured[id].orEmpty().any { key(it) == key(row.normalized) } }
                if (candidates.size != 1) fail("UNKNOWN_OR_AMBIGUOUS_$header") else {
                    val id = candidates.single()
                    found += id
                    typed += id
                    evidence += source.evidence("extract.observation.role", "observation.$id", row.range)
                }
            }
            keys.forEach { id -> configured[id].orEmpty().forEach { phrase ->
                source.phrase(phrase).findAll(source.text).filter { scoped(it.range, boundField) }.forEach { match ->
                    found += id
                    evidence += source.evidence("extract.observation", "observation.$id", match.range)
                }
            } }
            val title = source.text.substring(titleStart.coerceAtLeast(0)).substringBefore('\n')
            val statementTitle = configured["card.statement"].orEmpty().any { source.phrase(it).containsMatchIn(title) }
            if (header == "observation.cardType" && statementTitle && family in setOf(MessageFamily.NON_FINANCIAL, MessageFamily.BALANCE_NOTICE) &&
                typed.size <= 1 && "card.statement" in found && "card.debit" !in found) found.remove("card.credit")
            if (found.size > 1) fail("CONFLICTING_$header")
            if (found.isNotEmpty() && family !in allowed) fail("UNRELATED_$header")
            return found.singleOrNull()?.takeIf { family in allowed }
        }
        val cards = setOf(MessageFamily.PURCHASE, MessageFamily.REFUND, MessageFamily.CARD_PAYMENT,
            MessageFamily.NON_FINANCIAL, MessageFamily.BALANCE_NOTICE)
        val card = when (choose(listOf("card.credit", "card.debit", "card.statement"), "observation.cardType", cards, "CARD")) {
            "card.credit" -> CardChannel.CREDIT; "card.debit" -> CardChannel.DEBIT; "card.statement" -> CardChannel.STATEMENT; else -> null
        }
        val purchase = when (choose(listOf("purchase.pos", "purchase.online"), "observation.purchaseChannel", setOf(MessageFamily.PURCHASE))) {
            "purchase.pos" -> PurchaseChannel.POS; "purchase.online" -> PurchaseChannel.ONLINE; else -> null
        }
        val loan = when (choose(listOf("loan.personal", "loan.auto", "loan.mortgage"), "observation.loanType", setOf(MessageFamily.FINANCING_INSTALLMENT), "counterparty")) {
            "loan.personal" -> LoanType.PERSONAL; "loan.auto" -> LoanType.AUTO; "loan.mortgage" -> LoanType.MORTGAGE; else -> null
        }
        val salary = choose(listOf("income.salary"), "observation.incomeType", setOf(MessageFamily.TRANSFER_IN))?.let { true }
        val balance = configured["card.available_balance"].orEmpty().any { phrase ->
            source.phrase(phrase).findAll(source.text).filter { scoped(it.range, "AVAILABLE_BALANCE") }.onEach {
                evidence += source.evidence("extract.observation", "observation.card.available_balance", it.range)
            }.toList().isNotEmpty()
        }
        if (balance && family !in cards) fail("UNRELATED_BALANCE_OBSERVATION")
        val creditBalance = card==null && balance && physical.any {it.field=="CARD"} &&
            configured["card.credit_balance"].orEmpty().any {phrase ->
                source.phrase(phrase).findAll(source.text).any {scoped(it.range,"AVAILABLE_BALANCE")}}
        if(creditBalance) physical.firstOrNull {it.field=="CARD"}?.let {
            evidence += it.copy(ruleId="extract.observation.credit_balance",field="observation.card.credit")
        }
        val debitSource = configured["card.debit_source"].orEmpty().any { phrase ->
            source.phrase(phrase).findAll(source.text).filter { scoped(it.range, "SOURCE_ACCOUNT") }.onEach {
                evidence += source.evidence("extract.observation.debit_source", "observation.card.debit", it.range)
            }.toList().isNotEmpty()
        }
        return ObservedFacts(if (debitSource) CardChannel.DEBIT else card ?: CardChannel.CREDIT.takeIf {creditBalance}, purchase, loan,
            salary ?: false.takeIf { family == MessageFamily.TRANSFER_IN }, balance)
    }
}
