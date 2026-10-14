package com.baraa.masroof.lab.engine.evidence

import com.baraa.masroof.lab.engine.*
import com.baraa.masroof.lab.copy.core.text.ArabicTextFolding
import com.baraa.masroof.lab.copy.core.money.Currency
import com.baraa.masroof.lab.copy.core.money.Money
import com.baraa.masroof.lab.copy.domain.model.*
import com.baraa.masroof.lab.copy.domain.rules.OtpMessageHeuristics
import com.baraa.masroof.lab.copy.parsing.model.*
import com.baraa.masroof.lab.copy.parsing.finalize.ParseFinalizer
import com.baraa.masroof.lab.copy.parsing.validator.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime

/** Experimental interpretation; no ownership resolution, external calls or financial writes. */
class EvidenceLabEngine(private val clock: Clock = Clock.systemUTC()) {
    companion object { const val VERSION = "evidence-1" }
    private data class Text(val original: String) {
        val indices = mutableListOf<Int>()
        val comparison = buildString {
            original.forEachIndexed { index, char ->
                val digit = Character.digit(char, 10)
                val normalized = when { digit >= 0 -> digit.toString(); char == '٫' -> "."; char == '٬' -> ","; else -> char.toString() }
                ArabicTextFolding.foldForComparison(normalized).lowercase(java.util.Locale.ROOT).forEach {
                    append(it); this@Text.indices.add(index)
                }
            }
        }
        fun start(i: Int) = indices[i]
        fun end(i: Int) = indices[i] + 1
        fun display(range: IntRange) = original.substring(start(range.first), end(range.last))
    }
    private data class Hit(val rule: ConceptRule, val label: String, val range: IntRange)
    private fun phrase(value: String): Regex = Regex("(?<![\\p{L}\\p{N}])(?:و)?" +
        Regex.escape(ArabicTextFolding.foldForComparison(value).lowercase(java.util.Locale.ROOT)) + "(?![\\p{L}\\p{N}])")

    fun parse(banks: List<BankProfile>, knowledge: EvidenceKnowledge, sender: String, body: String,
        sampleId: String, receivedAt: Instant, selectedBankId: String? = null): EvidenceAnalysis {
        knowledge.validate()
        require(banks.size <= 100)
        if (body.isBlank() || body.length > 16_000) return EvidenceAnalysis(outcome = LabOutcome("REVIEW_REQUIRED", trace = listOf("نص فارغ أو طويل")))
        val routes = banks.filter { bank -> bank.senders.any { RulePackValidation.senderKey(it) == RulePackValidation.senderKey(sender) } }
        if (routes.size != 1 || (selectedBankId != null && selectedBankId != routes.singleOrNull()?.id))
            return EvidenceAnalysis(outcome = LabOutcome("REVIEW_REQUIRED", trace = listOf("المرسل غير معروف أو متعارض")))
        val text = Text(body)
        val hits = knowledge.rules.asSequence().filter { r -> r.excludes.none { phrase(it).containsMatchIn(text.comparison) } }.flatMap { rule ->
            rule.phrases.asSequence().flatMap { label -> phrase(label).findAll(text.comparison).map { Hit(rule, label, it.range) } }
        }.take(513).toList()
        if (hits.size > 512) return EvidenceAnalysis(outcome = LabOutcome("REVIEW_REQUIRED", trace = listOf("تجاوز عدد الأدلة حد التجربة؛ لم يعتمد تفسير")))
        val tokens = EvidenceExtractor.extract(text.comparison).map { token -> token.copy(start = text.start(token.start), endExclusive = text.end(token.endExclusive - 1)) }
        val rawRelations = RelationResolver.resolve(text.comparison, hits.filter { it.rule.concept.startsWith("field.") }.map {
            RelationResolver.Label(it.rule, it.range)
        }).map { relation -> relation.copy(value = if (relation.role == "merchant") text.display(relation.start until relation.endExclusive).trim().trimEnd('.') else relation.value,
            start = text.start(relation.start), endExclusive = text.end(relation.endExclusive - 1)) }
        val requirements = knowledge.rules.associate { it.id to it.requiredRoles }
        val relations = rawRelations.filter { requirements[it.ruleId].orEmpty().isEmpty() }.toMutableList()
        // Bounded dependency expansion: a conditional relationship cannot justify itself.
        repeat(EvidenceDefaults.roles.size) {
            val present = relations.map { it.role }.toSet()
            relations += rawRelations.filter { it !in relations && requirements[it.ruleId].orEmpty().all(present::contains) }
        }
        val problems = mutableListOf<String>()
        fun unique(role: String): String? {
            val values = relations.filter { it.role == role }.map { it.value to it.currency }.distinct()
            if (values.size > 1) problems += "تعارض الحقل: $role"
            return values.singleOrNull()?.first
        }
        val extracted = EvidenceDefaults.roles.associateWith(::unique)
        val amountRelation = relations.filter { it.role == "amount" }.distinctBy { it.value to it.currency }.singleOrNull()
        fun insideMerchant(hit: Hit) = relations.any { relation -> relation.role == "merchant" &&
            relation.start <= text.start(hit.range.first) && relation.endExclusive >= text.end(hit.range.last) }
        val active = hits.filter { it.rule.requiredRoles.all { role -> extracted[role] != null } }.filterNot {
            (it.rule.concept.startsWith("card.") || it.rule.concept.startsWith("network.")) && insideMerchant(it)
        }
        val families = active.filter { it.rule.concept.startsWith("family.") }.groupBy { it.rule.concept.substringAfter('.') }
        val stateHits = active.filter { it.rule.concept.startsWith("state.") }.map { it.rule.concept.substringAfter('.') }.toSet()
        val execution = when {
            "REJECTED" in stateHits -> "REJECTED"
            "REQUESTED" in stateHits -> "REQUESTED"
            "PENDING" in stateHits -> "PENDING"
            families.keys.any { it in LabFamilies.financial } -> "EXECUTED"
            else -> "UNPROVEN"
        }
        val otp = OtpMessageHeuristics.isOtpMessage(text.comparison) || "OTP" in families
        val hypotheses = EventHypothesisBuilder.build(families.mapValues { (_, value) -> value.map { it.rule.id } }, execution, extracted)
        // Statement authority is evidence from both labels, even if its date grammar is unsupported.
        val dueContext = extracted["dueAmount"] != null && active.any { it.rule.concept == "field.dueDate" }
        val inactiveBeneficiary = phrase("المستفيد").containsMatchIn(text.comparison) &&
            listOf("غير نشط", "inactive").any { phrase(it).containsMatchIn(text.comparison) }
        val feeTitle = Regex("^\\s*(?:رسوم(?: حوالة| تحويل)?|transfer fee|fee charged)[ :：\\n]").containsMatchIn(text.comparison)
        val ambiguousRefund = families.keys == setOf("PURCHASE", "REFUND") && families.getValue("REFUND").all(::insideMerchant)
        val family = if (!otp && ambiguousRefund) "UNKNOWN" else
            EvidenceDecisionPolicy.choose(families.keys, otp, dueContext, inactiveBeneficiary, feeTitle)
        if (ambiguousRefund && !otp) problems += "لفظ الاسترداد داخل اسم التاجر؛ لم يثبت حدث استرداد مستقل"
        if (family == "UNKNOWN") problems += "التفسيرات متعارضة أو الأدلة غير كافية"
        if (execution in setOf("REJECTED", "REQUESTED", "PENDING")) problems += "حالة التنفيذ: $execution"
        if (family in LabFamilies.financial && amountRelation == null) problems += "لا يوجد مبلغ عملية واحد مرتبط بعملة واضحة"
        if (relations.any { it.role == "invalidDate" }) problems += "تاريخ غير صالح"
        if (relations.any { it.role == "dateOnly" }) problems += "ذُكر تاريخ دون وقت؛ لم يُفترض وقت للعملية"
        fun concept(prefix: String): String? {
            val values = active.filter { it.rule.concept.startsWith(prefix) }.map { it.rule.concept.substringAfter('.') }.distinct()
            if (values.size > 1) problems += "تعارض $prefix"
            return values.singleOrNull()
        }
        val kind = concept("card."); val network = concept("network.")
        val card = extracted["card"]
        if (family in LabFamilies.financial && card == null && active.any { it.rule.concept == "field.card" }) problems += "معرف البطاقة غير محدد"
        val financial = family in LabFamilies.financial
        val direction = family.takeIf { financial }?.let(LabFamilies::direction)
        val bank = Bank(routes.single().id)
        fun money(role: String): Money? {
            val relation = relations.filter { it.role == role }.distinctBy { it.value to it.currency }.singleOrNull() ?: return null
            return runCatching { Money.of(relation.value, Currency.valueOf(requireNotNull(relation.currency))) }.getOrNull()
        }
        val amount = money("amount")
        val amountCandidate = amount?.let { AmountCandidate(it, "evidence:amount", AmountSourceKind.TRANSACTION_AMOUNT) }
        val draft = ParsedEventDraft(rawSmsId = sampleId, bank = bank, messageFamily = MessageFamily.valueOf(family),
            direction = direction?.let(MoneyDirection::valueOf), amount = amount.takeIf { financial },
            cardRef = card?.let { CardReference(bank, it) },
            sourceAccountRef = extracted["source"]?.let { AccountReference(bank, it) },
            destinationAccountRef = extracted["destination"]?.let { AccountReference(bank, it) },
            merchant = extracted["merchant"], confidence = Confidence(0.0, listOf("evidence-based; score is not a probability")),
            parseStatus = ParseStatus.PARTIAL, amountCandidates = listOfNotNull(amountCandidate), selectedAmount = amountCandidate,
            details = ParsedEventDetails(availableBalance = money("balance"), outstandingBalance = money("dueAmount"),
                occurredAtLocal = extracted["occurredAtLocal"]?.let(LocalDateTime::parse)))
        val validator = object : ParsedEventValidator {
            override fun validate(draft: ParsedEventDraft) = ValidationResult(DefaultParsedEventValidator(
                policy = AutomaticUsePolicy(minFinancialConfidence = 0.0), clock = clock).validate(draft).findings +
                problems.distinct().map { ValidationFinding("LAB-EVIDENCE", it, ValidationSeverity.ERROR) })
        }
        val projected = LabResultProjection.project(ParseFinalizer(validator).finalize(draft, "lab-$sampleId-e${knowledge.version}"))
        // Informational authority must not hide a contradictory or unexecuted financial interpretation.
        val status = if (!otp && problems.isNotEmpty()) "REVIEW_REQUIRED" else projected.status
        val facts = projected.facts.copy(family = family, direction = direction,
            amount = extracted["amount"], currency = amountRelation?.currency, card = card,
            source = extracted["source"], destination = extracted["destination"], merchant = extracted["merchant"],
            balance = extracted["balance"], dueAmount = extracted["dueAmount"], ibanSuffix = extracted["ibanSuffix"],
            occurredAtLocal = extracted["occurredAtLocal"], executionState = if (otp) "UNPROVEN" else execution,
            cardKind = kind, network = network, feeAmount = extracted["feeAmount"], exchangeRate = extracted["exchangeRate"])
        val evidence = relations.map { FieldEvidence(it.role, it.ruleId, body.substring(it.start, it.endExclusive), it.value, it.start, it.endExclusive) } +
            active.filter { !it.rule.concept.startsWith("field.") }.map { hit -> FieldEvidence(hit.rule.concept, hit.rule.id, hit.label,
                text.display(hit.range), text.start(hit.range.first), text.end(hit.range.last)) }
        return EvidenceAnalysis(outcome = LabOutcome(status, facts,
            listOf("engine:$VERSION", "knowledge:${knowledge.version}", "bank:${bank.id}", "receivedAt:$receivedAt") +
                problems.distinct() + projected.trace + hypotheses.map { "${it.family}: ${it.support.joinToString()} ${it.blockers.joinToString()}" },
            evidence.distinct(), body), tokens = tokens, relations = relations.distinct(), hypotheses = hypotheses)
    }
}

object EvidenceExtractor {
    private val number = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,6})?"
    private val currency = "(?:sar|usd|eur|gbp|ريال سعودي|ريال|ر\\.س\\.?)"
    val money = Regex("(?<![\\p{L}\\p{N}.,])(?:(?<before>$currency)[ \\t]*(?<n1>$number)|(?<n2>$number)[ \\t]*(?<after>$currency))(?![\\p{L}\\p{N}.,])")
    val date = Regex("(?<![0-9])(?<date>[0-9]{4}-[0-9]{2}-[0-9]{2}(?:[ t][0-9]{2}:[0-9]{2}(?::[0-9]{2})?)?)(?![0-9:])")
    val scalar = Regex("(?<![0-9.,])[0-9]+(?:\\.[0-9]{1,6})?(?![0-9.,])")
    fun extract(text: String): List<EvidenceToken> = buildList {
        money.findAll(text).forEach { m ->
            val n = m.groups["n1"] ?: m.groups["n2"]!!
            val c = (m.groups["before"] ?: m.groups["after"]!!).value
            add(EvidenceToken("MONEY", n.value.replace(",", ""), m.range.first, m.range.last + 1, if (c.startsWith("ر")) "SAR" else c.uppercase()))
        }
        date.findAll(text).forEach { add(EvidenceToken("DATE", it.value, it.range.first, it.range.last + 1)) }
        scalar.findAll(text).forEach { add(EvidenceToken("NUMBER", it.value, it.range.first, it.range.last + 1)) }
    }
}

object RelationResolver {
    data class Label(val rule: ConceptRule, val range: IntRange)
    fun resolve(text: String, raw: List<Label>): List<EvidenceRelation> {
        // A generic "amount"/"date" inside a longer due/fee label must not steal its value.
        val labels = raw.filter { a -> raw.none { b -> b.range.first <= a.range.first && b.range.last >= a.range.last && b.range.count() > a.range.count() } }
        val relations = mutableListOf<EvidenceRelation>()
        labels.forEach { label ->
            val role = label.rule.concept.substringAfter('.')
            val start = label.range.last + 1
            val lineEnd = text.indexOf('\n', start).takeIf { it >= 0 } ?: text.length
            val limit = if (label.rule.nextLine && lineEnd < text.length) (text.indexOf('\n', lineEnd + 1).takeIf { it >= 0 } ?: text.length) else lineEnd
            val end = minOf(limit, start + label.rule.maxDistance)
            if (end <= start) return@forEach
            val tail = text.substring(start, end)
            fun add(value: String, range: IntRange, currency: String? = null, selectedRole: String = role) {
                relations += EvidenceRelation(selectedRole, value, start + range.first, start + range.last + 1, label.rule.id, currency)
            }
            when (role) {
                "amount", "balance", "dueAmount", "feeAmount", "otherAmount" -> {
                    val match = EvidenceExtractor.money.find(tail) ?: return@forEach
                    val gap = tail.substring(0, match.range.first)
                    if (!gap.matches(Regex("[ \\t\\r\\n:：]*(?:\\(0[0-9]{2}\\)[ \\t]*)?"))) return@forEach
                    val n = match.groups["n1"] ?: match.groups["n2"]!!
                    val c = (match.groups["before"] ?: match.groups["after"]!!).value
                    val value = n.value.replace(",", "").toBigDecimalOrNull() ?: return@forEach
                    if (value.scale() > 2 || value.precision() > 18) return@forEach
                    add(value.toPlainString(), match.range, if (c.startsWith("ر")) "SAR" else c.uppercase())
                }
                "exchangeRate" -> Regex("^[ \\t:：]*(?<n>[0-9]+(?:\\.[0-9]{1,6})?)(?![0-9.])").find(tail)?.groups?.get("n")?.let { add(it.value, it.range) }
                "card", "source", "destination", "ibanSuffix" -> {
                    val direct = Regex("^[ \\t:：]*(?:[x*•]+)?\\(?(?<n>[0-9]{4})\\)?(?![0-9.-])").find(tail)
                    val named = if (role == "card") Regex("^[^0-9\\n:：()]{0,45}\\((?<n>[0-9]{4})\\)").find(tail) else null
                    val group = (direct ?: named)?.groups?.get("n")
                    group?.let { digits ->
                        // A currency-bearing amount is not an account/card identifier at the same position.
                        if (EvidenceExtractor.money.findAll(tail).none { it.range.first <= digits.range.last && digits.range.first <= it.range.last })
                            add(digits.value, digits.range)
                    }
                }
                "occurredAtLocal", "dueDate" -> {
                    val match = EvidenceExtractor.date.find(tail) ?: return@forEach
                    if (!tail.substring(0, match.range.first).matches(Regex("[ \\t:：]*"))) return@forEach
                    if (role == "occurredAtLocal" && match.value.length == 10) {
                        val valid = runCatching { java.time.LocalDate.parse(match.value) }.isSuccess
                        add(match.value, match.range, selectedRole = if (valid) "dateOnly" else "invalidDate")
                        return@forEach
                    }
                    val value = runCatching { if (match.value.length == 10) java.time.LocalDate.parse(match.value).atStartOfDay()
                        else LocalDateTime.parse(match.value.replace(' ', 'T').replace('t', 'T')) }.getOrNull()
                    add(value?.toString() ?: match.value, match.range, selectedRole = if (value == null) "invalidDate" else role)
                }
                "merchant" -> {
                    val match = Regex("^[ \\t:：]*(?<name>.+?)(?=[ \\t]+(?:في|on)[ \\t:：]*[0-9]{4}-|$)").find(tail)
                    val group = match?.groups?.get("name")
                    if (group != null && group.value.isNotBlank() && !group.value.first().isDigit()) add(group.value.trim().trimEnd('.'), group.range)
                }
            }
        }
        return relations.distinct()
    }
}

object EventHypothesisBuilder {
    fun build(families: Map<String, List<String>>, state: String, roles: Map<String, String?>): List<EventHypothesis> =
        families.toSortedMap().map { (family, support) -> EventHypothesis(family, support.sorted(), buildList {
            if (family in LabFamilies.financial && roles["amount"] == null) add("مبلغ الحركة غير محسوم")
            if (family in LabFamilies.financial && state != "EXECUTED") add("التنفيذ غير مثبت: $state")
        }) }
}

object EvidenceDecisionPolicy {
    fun choose(families: Set<String>, otp: Boolean, due: Boolean, inactive: Boolean, feeTitle: Boolean): String {
        if (otp) return "OTP"
        val financial = families.filter { it in LabFamilies.financial }.toSet()
        if (financial.isEmpty()) return if (families.size == 1) families.single() else "UNKNOWN"
        if (inactive || due || feeTitle && financial.any { it != "FEE" }) return "UNKNOWN"
        if (families.any { it !in LabFamilies.financial }) return "UNKNOWN"
        // A refund explicitly reverses a quoted purchase. Embedded fee wording may accompany a transfer.
        if (financial == setOf("PURCHASE", "REFUND")) return "REFUND"
        if (financial.size == 2 && "FEE" in financial && financial.any { it == "TRANSFER_IN" || it == "TRANSFER_OUT" } && !feeTitle)
            return financial.single { it != "FEE" }
        return financial.singleOrNull() ?: "UNKNOWN"
    }
}
