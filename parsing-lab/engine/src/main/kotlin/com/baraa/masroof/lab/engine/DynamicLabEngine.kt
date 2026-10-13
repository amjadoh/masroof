package com.baraa.masroof.lab.engine

import com.baraa.masroof.lab.copy.bank.aljazira.classification.AlJaziraClassification
import com.baraa.masroof.lab.copy.bank.aljazira.classification.AlJaziraClassificationResolver
import com.baraa.masroof.lab.copy.bank.aljazira.classification.AlJaziraClassificationRule
import com.baraa.masroof.lab.copy.core.money.Currency
import com.baraa.masroof.lab.copy.core.money.Money
import com.baraa.masroof.lab.copy.core.text.ArabicTextFolding
import com.baraa.masroof.lab.copy.domain.model.*
import com.baraa.masroof.lab.copy.domain.rules.OtpMessageHeuristics
import com.baraa.masroof.lab.copy.parsing.finalize.ParseFinalizer
import com.baraa.masroof.lab.copy.parsing.model.*
import com.baraa.masroof.lab.copy.parsing.normalizer.MessageNormalizer
import com.baraa.masroof.lab.copy.parsing.validator.*
import java.time.Instant

class DynamicLabEngine {
    fun parse(packs: List<RulePack>, sender: String, body: String, sampleId: String, receivedAt: Instant,
              selectedBankId: String? = null): LabOutcome {
        require(packs.size <= 100) { "عدد البنوك يتجاوز حد التجربة" }
        packs.forEach(RulePackValidation::check)
        if (body.length !in 1..16_000) return LabOutcome("REVIEW_REQUIRED", trace = listOf("النص فارغ أو يتجاوز حد التجربة"))
        val routes = packs.filter { pack -> pack.bank.senders.any { RulePackValidation.senderKey(it) == RulePackValidation.senderKey(sender) } }
        if (routes.size != 1) return LabOutcome("REVIEW_REQUIRED", trace = listOf(if (routes.isEmpty()) "مرسل غير معروف" else "تعارض: المرسل يطابق أكثر من بنك"))
        val pack = routes.single()
        if (selectedBankId != null && pack.bank.id != selectedBankId) {
            return LabOutcome("REVIEW_REQUIRED", trace = listOf("المرسل يتبع بنكًا مختلفًا عن بنك العينة المحدد"))
        }
        val normalized = MessageNormalizer().normalize(body)
        val text = normalized.comparisonBody
        fun contains(term: String) = text.contains(ArabicTextFolding.foldForComparison(term))
        val matches = pack.rules.filter { it.phrases.any(::contains) && it.excludes.none(::contains) }.sortedBy { it.id }
        val rules = pack.rules.map { r -> AlJaziraClassificationRule.of(
            id = r.id, specificity = LabFamilies.tier(r.family),
            matches = { r.phrases.any(::contains) && r.excludes.none(::contains) },
            classify = { AlJaziraClassification(MessageFamily.valueOf(r.family),
                if (r.family in LabFamilies.financial) MoneyDirection.valueOf(r.direction) else null,
                evidence = listOf("rule:${r.id}")) },
        ) } + AlJaziraClassificationRule.of("lab.security", 100,
            matches = OtpMessageHeuristics::isOtpMessage,
            classify = { AlJaziraClassification(MessageFamily.OTP, evidence = listOf("أولوية الرسائل الأمنية"), confidence = 1.0) })
        var classification = AlJaziraClassificationResolver(rules).resolve(text)
        val winning = matches.filter { it.family == classification.family.name }.sortedBy { it.id }
        // Same family does not imply the same extraction or direction. Never silently pick one schema.
        if (classification.family.name in LabFamilies.financial &&
            winning.map { it.copy(id = "same", phrases = emptyList(), excludes = emptyList()) }.distinct().size > 1) {
            classification = AlJaziraClassification(MessageFamily.UNKNOWN, evidence = listOf("تعارض إعدادات قواعد النوع نفسه"), confidence = 0.3)
        }
        val evidence = mutableListOf<FieldEvidence>()
        val errors = mutableListOf<String>()
        val indexMap = ArabicTextFolding.comparisonIndexMap(normalized.normalizedBody)
        fun record(field: String, rule: RuleSpec, label: String, group: MatchGroup, value: String) {
            val start = indexMap[group.range.first]
            val end = indexMap[group.range.last] + 1
            evidence += FieldEvidence(field, rule.id, label, value, start, end)
        }
        fun money(rule: RuleSpec, labels: List<String>, field: String): List<AmountCandidate> = labels.flatMap { label ->
            val pattern = Regex(Regex.escape(ArabicTextFolding.foldForComparison(label)) +
                "\\s*[:：]?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*(sar|usd|eur|gbp)(?![a-z])")
            pattern.findAll(text).map { m ->
                val value = Money.of(m.groupValues[1].replace(",", ""), Currency.valueOf(m.groupValues[2].uppercase()))
                record(field, rule, label, m.groups[1]!!, value.amount.toPlainString())
                AmountCandidate(value, label, if (field == "amount") AmountSourceKind.TRANSACTION_AMOUNT else AmountSourceKind.AVAILABLE_BALANCE)
            }.toList()
        }
        fun suffix(rule: RuleSpec, labels: List<String>, field: String): String? {
            val values = labels.flatMap { label ->
                val regex = Regex(Regex.escape(ArabicTextFolding.foldForComparison(label)) + "\\s*[:：]?\\s*[*•xX]*([0-9]+)(?![0-9.*•xX])")
                regex.findAll(text).mapNotNull { m ->
                    if (m.groupValues[1].length != 4) {
                        errors += "حقل $field يجب أن يكون آخر أربعة أرقام";null
                    } else {
                        record(field, rule, label, m.groups[1]!!, m.groupValues[1]);m.groupValues[1]
                    }
                }.toList()
            }.distinct()
            if (values.isEmpty() && labels.any(::contains)) errors += "وسم $field موجود دون معرف صالح"
            if (values.size > 1) errors += "تعارض حقل $field"
            return values.singleOrNull()
        }
        val rule = winning.firstOrNull().takeIf { classification.family.name in LabFamilies.financial }
        val amounts = rule?.let { money(it, it.amountLabels, "amount") }.orEmpty()
        val balances = rule?.let { money(it, it.balanceLabels, "balance") }.orEmpty()
        val selected = amounts.takeIf { it.map { a -> a.value }.distinct().size == 1 }?.firstOrNull()
        val card = rule?.let { suffix(it, it.cardLabels, "card") }
        val source = rule?.let { suffix(it, it.sourceLabels, "source") }
        val destination = rule?.let { suffix(it, it.destinationLabels, "destination") }
        val merchantValues = rule?.merchantLabels.orEmpty().flatMap { label ->
            val regex = Regex("(?:^|\\n)" + Regex.escape(ArabicTextFolding.foldForComparison(label)) + "[ \\t]*[:：]?[ \\t]*([^\\n]+)")
            regex.findAll(text).map { m ->
                val display = normalized.normalizedSlice(m.groups[1]!!.range).trim()
                record("merchant", rule!!, label, m.groups[1]!!, display);display
            }.toList()
        }.distinct()
        if (merchantValues.size > 1) errors += "تعارض التاجر"
        val bank = Bank(pack.bank.id)
        val draft = ParsedEventDraft(rawSmsId = sampleId, bank = bank, messageFamily = classification.family,
            direction = classification.direction, amount = selected?.value,
            cardRef = card?.let { CardReference(bank, it) },
            sourceAccountRef = source?.let { AccountReference(if (classification.family == MessageFamily.TRANSFER_IN) Bank.UNKNOWN else bank, it) },
            destinationAccountRef = destination?.let { AccountReference(if (classification.family == MessageFamily.TRANSFER_OUT) Bank.UNKNOWN else bank, it) },
            merchant = merchantValues.singleOrNull(), confidence = Confidence(classification.confidence, classification.evidence),
            parseStatus = ParseStatus.PARTIAL, amountCandidates = amounts + balances, selectedAmount = selected,
            details = ParsedEventDetails(availableBalance = balances.map { it.value }.distinct().singleOrNull()))
        if (classification.family.name in LabFamilies.financial && selected == null) errors += "مبلغ العملية غير محدد بدليل وحيد"
        if (balances.map { it.value }.distinct().size > 1) errors += "تعارض الرصيد"
        val validator = object : ParsedEventValidator {
            override fun validate(draft: ParsedEventDraft): ValidationResult = ValidationResult(
                DefaultParsedEventValidator().validate(draft).findings + errors.map {
                    ValidationFinding("LAB-EVIDENCE", it, ValidationSeverity.ERROR)
                })
        }
        val result = ParseFinalizer(validator).finalize(draft, "lab-$sampleId-v${pack.version}")
        val outcome = LabResultProjection.project(result)
        return outcome.copy(
            trace = listOf("bank:${pack.bank.id}", "rules:v${pack.version}", "receivedAt:$receivedAt") +
                classification.evidence + matches.map { "candidate:${it.id}" } + outcome.trace + errors,
            evidence = evidence, normalizedText = normalized.normalizedBody)
    }
}
