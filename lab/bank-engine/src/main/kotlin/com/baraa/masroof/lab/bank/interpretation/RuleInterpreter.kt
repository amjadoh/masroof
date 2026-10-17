package com.baraa.masroof.lab.bank.interpretation

import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.finance.value.Money
import java.security.MessageDigest
import java.time.LocalDateTime

/** Independent M6 rules-only implementation. No legacy, persistence, clock fallback or posting. */
class RuleInterpreter(schema: BankRuleSchema) : BankInterpreter {
    private val snapshot = schema.copy(rules = schema.rules.map { it.copy(
        phrases = it.phrases.toList(), excludes = it.excludes.toList(),
        labels = it.labels.mapValues { entry -> entry.value.toList() },
        detailLabels = it.detailLabels.mapValues { entry -> entry.value.toList() },
        observations = it.observations.mapValues { entry -> entry.value.toList() }) },
        network = schema.network.copy(bankAliases = schema.network.bankAliases.mapValues { it.value.toList() },
            sourceBankLabels = schema.network.sourceBankLabels.toList(),
            destinationBankLabels = schema.network.destinationBankLabels.toList(),
            networkLabels = schema.network.networkLabels.toList()))
    // Content identity prevents changing literals under an unchanged declared revision.
    val version = InterpretationVersion(1, VERSION, "literal-v${snapshot.schema}-r${snapshot.revision}-" +
        MessageDigest.getInstance("SHA-256").digest(snapshot.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) })

    override fun interpret(input: MessageInput, catalog: BankCatalog): Interpretation {
        val banks = catalog.candidates(input.sender)
        val blockers = linkedSetOf<Blocker>()
        val diagnostics = mutableListOf<InterpretationDiagnostic>()
        val evidence = mutableListOf<Evidence>()
        fun problem(code: String, blocker: Blocker = Blocker.VALIDATION_ERROR) {
            blockers += blocker
            diagnostics += InterpretationDiagnostic(code, code, DiagnosticSeverity.WARNING)
        }
        fun result(facts: Facts = Facts(MessageFamily.UNKNOWN), status: ParseStatus = ParseStatus.REVIEW_REQUIRED): Interpretation {
            if (facts.family == MessageFamily.UNKNOWN) blockers += Blocker.UNKNOWN_FAMILY
            return Interpretation(input.evidenceId, version, banks.singleOrNull(), facts, status,
                null, evidence.toList(), blockers.toSet(), diagnostics.toList())
        }
        if (banks.size != 1) {
            problem("SENDER_NOT_UNIQUE", if (banks.isEmpty()) Blocker.UNKNOWN_BANK else Blocker.AMBIGUOUS_BANK)
            return result()
        }
        if (input.body.length > MAX_BODY) { problem("MESSAGE_TOO_LONG"); return result() }
        val source = SourceText(input.body)
        fun contains(term: String) = source.phrase(term).containsMatchIn(source.text)
        // Security/non-execution gates are built in; a custom bank rule cannot exclude them.
        val security = SECURITY.flatMap { term -> source.phrase(term).findAll(source.text).toList() }
        if (security.isNotEmpty()) {
            security.forEach { evidence += source.evidence("security.otp", "family", it.range) }
            return result(Facts(MessageFamily.OTP), ParseStatus.NON_FINANCIAL)
        }
        val validSchema = runCatching { snapshot.validate() }.isSuccess
        // Later M6 increments implement the remaining closed grammar. Never ignore configuration.
        if (!validSchema || snapshot.network != NetworkRules() || snapshot.rules.any {
                it.observations.isNotEmpty() ||
                    (it.phrases + it.excludes + it.labels.values.flatten() + it.detailLabels.values.flatten()).any { term -> SourceText(term).text.none(Char::isLetterOrDigit) }
            }) {
            problem("UNSUPPORTED_RULE_CAPABILITY", Blocker.UNSUPPORTED_RULE_SCHEMA); return result()
        }
        val matching = snapshot.rules.filter { rule -> rule.phrases.any(::contains) && rule.excludes.none(::contains) }
        matching.forEach { rule -> rule.phrases.forEach { term -> source.phrase(term).findAll(source.text).forEach {
            evidence += source.evidence(rule.id, "family", it.range)
        } } }
        if (matching.isEmpty()) return result()
        if (matching.map { it.family to it.direction }.distinct().size != 1) {
            problem("FAMILY_OR_DIRECTION_CONFLICT", Blocker.CONFLICTING_FACTS); return result()
        }
        val family = matching.first().family
        val direction = matching.first().direction
        if (source.text.lineSequence().any { it.trimStart().let { line -> line.startsWith(">") || line.startsWith("```") || line.startsWith('"') || line.startsWith('«') || line.startsWith('“') } })
            problem("QUOTED_MESSAGE_CONTEXT")
        if (BLOCKED.any(::contains)) problem("EXECUTION_NOT_PROVEN")
        if (CONTEXT.any(::contains) && family !in setOf(MessageFamily.NON_FINANCIAL, MessageFamily.BALANCE_NOTICE))
            problem("MOVEMENT_NOTICE_CONFLICT", Blocker.CONFLICTING_FACTS)
        if (UNSUPPORTED_MOVEMENTS.any(::contains)) problem("UNSUPPORTED_MOVEMENT_CONTEXT", Blocker.UNSUPPORTED_RULE_SCHEMA)
        val supported = family in SUPPORTED
        if (!supported) problem("FAMILY_NOT_IMPLEMENTED", Blocker.UNSUPPORTED_RULE_SCHEMA)
        val expected = if (family == MessageFamily.REFUND) MoneyDirection.INCOMING else MoneyDirection.OUTGOING
        if (family !in INFORMATIONAL && direction != expected) problem("UNSAFE_DIRECTION", Blocker.CONFLICTING_FACTS)

        fun labels(field: ExtractionField) = matching.flatMap { it.labels[field].orEmpty() }.distinct()
        data class Hit<T>(val value: T, val range: IntRange, val field: ExtractionField)
        val details = DetailExtractor(source, matching.flatMap { it.detailLabels.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.flatten().distinct() }, evidence, ::problem).read()
        val secondaryRanges = details.reserved.toMutableList()
        fun overlaps(range: IntRange) = secondaryRanges.any { range.first <= it.last && it.first <= range.last }
        // Reserve all known secondary roles even when the selected rule does not expose them.
        SECONDARY.forEach { label ->
            Regex("(?m)^ *" + source.phrase(label).pattern + "[ \\t]*:?[ \\t]*" + MoneyGrammar.VALUE).findAll(source.text).forEach { secondaryRanges += it.range }
        }
        fun money(field: ExtractionField): Money? {
            val hits = labels(field).flatMap { label ->
                val pattern = Regex("(?m)^ *" + source.phrase(label).pattern + "[ \\t]*:?[ \\t]*" + MoneyGrammar.VALUE)
                val matches = pattern.findAll(source.text).toList()
                Regex("(?m)^ *" + source.phrase(label).pattern).findAll(source.text).forEach { mention ->
                    if ((field != ExtractionField.AMOUNT || !overlaps(mention.range)) && matches.none { it.range.first == mention.range.first })
                        problem("MALFORMED_${field.name}")
                }
                matches.mapNotNull { match ->
                    if (field == ExtractionField.AMOUNT && overlaps(match.range)) return@mapNotNull null
                    val value = MoneyGrammar.read(match)
                    if (value == null) { problem("INVALID_MONEY"); null }
                    else Hit(value, match.range, field)
                }
            }
            hits.forEach { evidence += source.evidence("extract.money", field.name, it.range) }
            if (hits.map { it.value }.distinct().size > 1) { problem("CONFLICTING_${field.name}", Blocker.CONFLICTING_FACTS); return null }
            return hits.firstOrNull()?.value
        }
        fun identifier(field: ExtractionField): String? {
            val hits = labels(field).flatMap { label ->
                Regex("(?m)^ *" + source.phrase(label).pattern + "[ \\t]*:?[ \\t]*([*x]*[0-9]{4})(?![\\p{L}\\p{N}_])")
                    .findAll(source.text).map { Hit(it.groupValues[1].takeLast(4), it.groups[1]!!.range, field) }.toList()
            }
            hits.forEach { evidence += source.evidence("extract.identifier", field.name, it.range) }
            if (hits.map { it.value }.distinct().size > 1) { problem("CONFLICTING_${field.name}", Blocker.CONFLICTING_FACTS); return null }
            return hits.firstOrNull()?.value
        }
        val balance = money(ExtractionField.AVAILABLE_BALANCE)
        val amount = if (family in INFORMATIONAL) null else money(ExtractionField.AMOUNT)
        val card = identifier(ExtractionField.CARD)
        val account = identifier(ExtractionField.SOURCE_ACCOUNT)
        val destination = identifier(ExtractionField.DESTINATION_ACCOUNT)
        val merchants = labels(ExtractionField.MERCHANT).flatMap { label ->
            Regex("(?m)^ *" + source.phrase(label).pattern + "[ \\t]*:[ \\t]*([^\\n\\r]+)").findAll(source.text).map { match ->
                val range = match.groups[1]!!.range
                evidence += source.evidence("extract.merchant", "MERCHANT", range)
                source.original(range).trim()
            }.toList()
        }.distinct()
        if (merchants.size > 1) problem("CONFLICTING_MERCHANT", Blocker.CONFLICTING_FACTS)
        val times = TIME.findAll(source.text).mapNotNull { match ->
            val lineStart = source.text.lastIndexOf('\n', match.range.first).let { if (it < 0) 0 else it + 1 }
            val prefix = source.text.substring(lineStart, match.range.first).trim().removeSuffix(":").trim()
            if (prefix !in TIME_LABELS) {
                problem("UNBOUND_TIME_ROLE"); return@mapNotNull null
            }
            evidence += source.evidence("extract.time", "occurredAtLocal", match.range)
            runCatching { LocalDateTime.parse(match.value.replace(' ', 'T').replace('t', 'T')) }.getOrElse { problem("INVALID_TIME"); null }
        }.distinct().toList()
        if (times.size > 1) problem("CONFLICTING_TIME", Blocker.CONFLICTING_FACTS)
        if (family !in INFORMATIONAL) {
            if (amount == null || amount.amount.signum() <= 0) problem("MISSING_POSITIVE_PRINCIPAL")
            if (times.size != 1) problem("MISSING_TRANSACTION_TIME")
            if (family in setOf(MessageFamily.PURCHASE, MessageFamily.REFUND) && card == null) problem("MISSING_CARD_ROLE")
            if (family in setOf(MessageFamily.WITHDRAWAL, MessageFamily.CARD_PAYMENT, MessageFamily.BILL_PAYMENT, MessageFamily.FINANCING_INSTALLMENT) && account == null) problem("MISSING_SOURCE_ROLE")
        }
        if (family == MessageFamily.CARD_PAYMENT && card == null) problem("MISSING_CARD_ROLE")
        if (family == MessageFamily.BILL_PAYMENT && (details.biller == null || details.billerCode == null)) problem("MISSING_BILLER_ROLE")
        if (family == MessageFamily.FINANCING_INSTALLMENT && details.counterparty == null) problem("MISSING_FINANCING_ROLE")
        if (details.foreign != null && amount != null && details.foreign.currency == amount.currency && details.foreign != amount)
            problem("CONFLICTING_FOREIGN_PRINCIPAL", Blocker.CONFLICTING_FACTS)
        // One original source range cannot authorize two different field roles.
        val roleEvidence = evidence.filter { it.field != "family" }
        if (roleEvidence.any { a -> roleEvidence.any { b -> a.field != b.field && a.start < b.end && b.start < a.end } })
            problem("FIELD_ROLE_CONFLICT", Blocker.CONFLICTING_FACTS)
        val facts = Facts(family, amount, if (family in INFORMATIONAL) null else direction,
            sourceAccount = account?.let { AccountReference(banks.single(), it) },
            destinationAccount = destination?.let { AccountReference(null, it) },
            card = card?.let { CardReference(banks.single(), it) }, merchant = merchants.singleOrNull(),
            availableBalance = balance, occurredAtLocal = times.singleOrNull(), outstandingBalance = details.due,
            labeledForeignAmount = details.foreign, internationalFee = details.fee, exchangeRate = details.rate,
            paymentDueDate = details.dueDate, transactionReference = details.reference, counterparty = details.counterparty,
            biller = details.biller, billerCode = details.billerCode,
            executionState = if (family in INFORMATIONAL || blockers.isNotEmpty()) null else "EXECUTED")
        return result(facts, when {
            blockers.isNotEmpty() -> ParseStatus.REVIEW_REQUIRED
            family in INFORMATIONAL -> ParseStatus.NON_FINANCIAL
            else -> ParseStatus.SUCCESS
        })
    }

    companion object {
        const val VERSION = "bank-rules-independent-v2"
        private const val MAX_BODY = 16000
        private val TIME = Regex("(?<![0-9])[0-9]{4}-[0-9]{2}-[0-9]{2}[t ][0-9]{2}:[0-9]{2}(?::[0-9]{2})?(?![0-9:])")
        private val TIME_LABELS = setOf("", "في", "on", "date", "time", "transaction time", "تاريخ العملية")
        private val SECURITY = listOf("otp", "one time password", "one-time password", "password", "passcode", "pin", "verification code", "security code", "authentication code", "رمز التحقق", "رمز تحقق", "رمز التوثيق", "رمز تاكيد", "رمز لمرة واحدة", "كلمة المرور", "رمز سري")
        private val BLOCKED = listOf("declined", "rejected", "unsuccessful", "failed", "pending", "request", "cancelled", "canceled", "not", "never", "لم", "لن", "لا", "غير", "رفض", "مرفوض", "مرفوضة", "طلب", "قيد التنفيذ", "تعذر", "الغاء")
        private val CONTEXT = listOf("statement", "كشف حساب", "beneficiary added", "اضافة مستفيد", "advertisement", "offer", "عرض", "minimum payment", "payment due date", "تاريخ الاستحقاق")
        private val UNSUPPORTED_MOVEMENTS = listOf("incoming transfer", "outgoing transfer", "fund transfer", "حوالة", "تحويل")
        private val SECONDARY = listOf("available balance", "balance", "الرصيد المتاح", "الرصيد", "due amount", "outstanding balance", "المبلغ المستحق", "fee amount", "مبلغ الرسوم", "tax amount", "مبلغ الضريبة", "minimum amount", "minimum payment", "الحد الادني للسداد", "foreign amount", "المبلغ الاجنبي")
        private val INFORMATIONAL = setOf(MessageFamily.NON_FINANCIAL, MessageFamily.BALANCE_NOTICE)
        private val SUPPORTED = INFORMATIONAL + setOf(MessageFamily.PURCHASE, MessageFamily.REFUND, MessageFamily.WITHDRAWAL, MessageFamily.FEE, MessageFamily.CARD_PAYMENT, MessageFamily.BILL_PAYMENT, MessageFamily.FINANCING_INSTALLMENT)
    }
}
