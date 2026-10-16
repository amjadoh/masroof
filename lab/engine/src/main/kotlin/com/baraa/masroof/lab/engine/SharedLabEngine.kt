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
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime

/** Local-only extraction. No bank-specific parsing, ownership inference or ledger writes. */
class SharedLabEngine(private val clock: Clock = Clock.systemUTC()) {
    companion object { const val VERSION = "shared-2" }

    fun parse(banks: List<BankProfile>, library: SharedRuleLibrary, sender: String, body: String,
              sampleId: String, receivedAt: Instant, selectedBankId: String? = null): LabOutcome {
        library.validate()
        require(banks.size <= 100)
        if (body.isBlank() || body.length > 16_000) return LabOutcome("REVIEW_REQUIRED", trace = listOf("نص فارغ أو طويل"))
        val routes = banks.filter { it.senders.any { s -> RulePackValidation.senderKey(s) == RulePackValidation.senderKey(sender) } }
        if (routes.size != 1 || (selectedBankId != null && routes.singleOrNull()?.id != selectedBankId)) {
            return LabOutcome("REVIEW_REQUIRED", trace = listOf("المرسل غير معروف أو تعريفه متعارض"))
        }
        val bank = Bank(routes.single().id)
        val sms = MessageNormalizer().normalize(body.replace('٬', ',').replace('٫', '.'))
        val text = sms.comparisonBody
        val index = ArabicTextFolding.comparisonIndexMap(sms.normalizedBody)
        val evidence = mutableListOf<FieldEvidence>()
        val errors = mutableListOf<String>()
        fun phrase(value: String) = Regex("(?<![\\p{L}\\p{N}])" +
            (if (value.firstOrNull()?.let { it in '\u0600'..'\u06ff' } == true) "(?:و)?" else "") +
            Regex.escape(ArabicTextFolding.foldForComparison(value)) + "(?![\\p{L}\\p{N}])")
        fun record(field: String, id: String, label: String, range: IntRange, value: String) {
            evidence += FieldEvidence(field, id, label, value, index[range.first], index[range.last] + 1)
        }
        fun matches(r: RuleSpec) = r.phrases.any { phrase(it).containsMatchIn(text) } && r.excludes.none { phrase(it).containsMatchIn(text) }
        val matching = library.rules.filter(::matches)
        val candidates = library.rules.map { r -> AlJaziraClassificationRule.of(r.id, LabFamilies.tier(r.family),
            matches = { matches(r) }, classify = { AlJaziraClassification(MessageFamily.valueOf(r.family),
                if (r.family in LabFamilies.financial) MoneyDirection.valueOf(r.direction) else null,
                evidence = listOf("shared-rule:${r.id}")) }) } + SharedContextRules.rules + AlJaziraClassificationRule.of("shared.security", 100,
            matches = OtpMessageHeuristics::isOtpMessage,
            classify = { AlJaziraClassification(MessageFamily.OTP, evidence = listOf("رمز تحقق — لا يمثل عملية منفذة")) })
        var classification = AlJaziraClassificationResolver(candidates).resolve(text)
        // A rejection/request is not proof of an executed movement, even if it quotes a purchase.
        val blocked = listOf("declined", "rejected", "unsuccessful", "pending", "payment request", "طلب دفع", "طلب سداد", "مرفوضة", "رفض العملية", "لم تتم", "تعذر", "قيد التنفيذ")
        if (classification.family != MessageFamily.OTP && blocked.any { phrase(it).containsMatchIn(text) }) {
            errors += "تنفيذ العملية غير مثبت: طلب أو رفض أو انتظار"
        }
        val winning = matching.filter { it.family == classification.family.name }
        if (winning.map { it.direction }.distinct().size > 1) {
            classification = AlJaziraClassification(MessageFamily.UNKNOWN, evidence = listOf("تعارض اتجاه العملية"))
        }
        matching.forEach { rule -> rule.phrases.forEach { label -> phrase(label).findAll(text).forEach {
            record("family", rule.id, label, it.range, rule.family)
        } } }
        fun labels(select: (RuleSpec) -> List<String>) = winning.flatMap(select).distinct()
        val financial = classification.family.name in LabFamilies.financial
        val numeric = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?"
        val currency = "(?:sar|usd|eur|gbp|ريال سعودي|ريال|ر\\.س\\.?)"
        val moneyPattern = "(?:(?<before>$currency)[ \\t]+(?<n1>$numeric)|(?<n2>$numeric)[ \\t]*(?<after>$currency))(?![\\p{L}\\p{N}.,])"
        val reserved = mutableListOf<IntRange>()
        fun money(field: String, terms: List<String>, kind: AmountSourceKind, reserve: Boolean = false): List<AmountCandidate> = terms.flatMap { label ->
            // The optional (0xx) marker is accepted only before an explicit currency and amount.
            val marker = "(?:\\(0[0-9]{2}\\)[ \\t]+(?=$currency))?"
            val re = Regex(phrase(label).pattern + "[ \\t]*[:：]?[ \\t]*" + marker + moneyPattern)
            re.findAll(text).mapNotNull { m ->
                if (reserved.any { m.range.first <= it.last && it.first <= m.range.last }) return@mapNotNull null
                val number = m.groups["n1"] ?: m.groups["n2"]!!
                val cur = m.groups["before"] ?: m.groups["after"]!!
                val currencyValue = if (cur.value.startsWith("ريال") || cur.value.startsWith("ر.س")) Currency.SAR else Currency.valueOf(cur.value.uppercase())
                val value = runCatching { Money.of(number.value.replace(",", ""), currencyValue) }.getOrNull()
                    ?: return@mapNotNull null
                if (number.value.length > 18) { errors += "مبلغ خارج حدود التجربة"; return@mapNotNull null }
                if (reserve) reserved += m.range
                record(field, "shared.money", label, number.range, value.amount.toPlainString())
                AmountCandidate(value, label, kind)
            }.toList()
        }
        val dues = if (financial) money("dueAmount", listOf("due amount", "outstanding balance", "المبلغ المستحق", "إجمالي المستحق"), AmountSourceKind.OUTSTANDING_BALANCE, true) else emptyList()
        val balances = if (financial) money("balance", labels { it.balanceLabels }, AmountSourceKind.AVAILABLE_BALANCE, true) else emptyList()
        // Amount is also a suffix of other financial labels; those values must never
        // become the principal purchase/transfer amount just because it is absent.
        if (financial && classification.family != MessageFamily.FEE) money("otherAmount",
            listOf("fee amount", "tax amount", "minimum amount", "minimum payment", "مبلغ الرسوم", "مبلغ الضريبة", "الحد الأدنى للسداد"),
            AmountSourceKind.OTHER, true)
        val amounts = if (financial) money("amount", labels { it.amountLabels }, AmountSourceKind.TRANSACTION_AMOUNT) else emptyList()
        fun singleMoney(values: List<AmountCandidate>, field: String): AmountCandidate? {
            if (values.map { it.value }.distinct().size > 1) errors += "تعارض $field"
            return values.firstOrNull().takeIf { values.map { it.value }.distinct().size == 1 }
        }
        val amount = singleMoney(amounts, "مبلغ العملية")
        val balance = singleMoney(balances, "الرصيد")
        val due = singleMoney(dues, "المستحقات")
        fun unique(values: List<String>, field: String): String? {
            val distinct = values.distinct()
            if (distinct.size > 1) errors += "تعارض $field"
            return distinct.singleOrNull()
        }
        fun suffix(field: String, terms: List<String>, card: Boolean = false): String? {
            if (!financial) return null
            val values = terms.flatMap { label ->
                val direct = Regex(phrase(label).pattern + "[ \\t]*[:：]?[ \\t]*(?:[x*•]+)?(?<digits>[0-9]{4})(?=[ \\t]*(?:$|\\n|[;,،]|بمبلغ|of\\b|amount\\b))")
                val parenthesized = Regex(phrase(label).pattern + (if (card) "[^\\d\\n():：]{0,45}" else "[ \\t]*[:：]?[ \\t]*") + "\\((?<digits>[0-9]{4})\\)")
                (direct.findAll(text) + parenthesized.findAll(text)).map { m ->
                    val digits = m.groups["digits"]!!
                    record(field, "shared.reference", label, digits.range, digits.value); digits.value
                }.toList()
            }
            return unique(values, field)
        }
        val card = suffix("card", labels { it.cardLabels }, true)
        val source = suffix("source", labels { it.sourceLabels })
        val destination = suffix("destination", labels { it.destinationLabels })
        val ibanSuffix = suffix("ibanSuffix", listOf("iban", "آيبان", "ايبان"))
        if (financial && card == null && labels { it.cardLabels }.any { phrase(it).containsMatchIn(text) }) {
            errors += "ذُكرت بطاقة دون آخر أربعة أرقام واضحة"
        }
        val merchants = if (financial && classification.family in setOf(MessageFamily.PURCHASE, MessageFamily.REFUND)) labels { it.merchantLabels }.flatMap { label ->
            val re = Regex(phrase(label).pattern + "[ \\t]*[:：]?[ \\t]*(?<name>[^\\n]+?)(?=[ \\t]+(?:في|on)[ \\t]*[:：]?[ \\t]*[0-9]{4}-|\\n|$)")
            re.findAll(text).mapNotNull { m ->
                val group = m.groups["name"]!!
                val value = sms.normalizedSlice(group.range).trim().trimEnd('.')
                if (value.isBlank()) null else { record("merchant", "shared.merchant", label, group.range, value); value }
            }.toList()
        } else emptyList()
        val merchant = unique(merchants, "التاجر")
        val dates = if (financial) Regex("(?:^|\\n|[ \\t])(?:on|في|بتاريخ|date)[ \\t]*[:：]?[ \\t]*(?<date>[0-9]{4}-[0-9]{2}-[0-9]{2}[ t][0-9]{2}:[0-9]{2}(?::[0-9]{2})?)(?![0-9:])").findAll(text).mapNotNull { m ->
            val group = m.groups["date"]!!
            val value = runCatching { LocalDateTime.parse(group.value.replace(' ', 'T').replace('t', 'T')) }.getOrNull()
            if (value == null) errors += "تاريخ العملية غير صالح" else record("occurredAtLocal", "shared.date", "date", group.range, value.toString())
            value?.toString()
        }.toList() else emptyList()
        val date = unique(dates, "التاريخ")?.let(LocalDateTime::parse)
        val draft = ParsedEventDraft(rawSmsId = sampleId, bank = bank, messageFamily = classification.family,
            direction = classification.direction, amount = amount?.value,
            cardRef = card?.let { CardReference(bank, it) },
            sourceAccountRef = source?.let { AccountReference(if (classification.family == MessageFamily.TRANSFER_IN) Bank.UNKNOWN else bank, it) },
            destinationAccountRef = destination?.let { AccountReference(if (classification.family == MessageFamily.TRANSFER_OUT) Bank.UNKNOWN else bank, it) },
            merchant = merchant, confidence = Confidence(classification.confidence, classification.evidence), parseStatus = ParseStatus.PARTIAL,
            amountCandidates = amounts + balances + dues, selectedAmount = amount,
            details = ParsedEventDetails(availableBalance = balance?.value, outstandingBalance = due?.value, occurredAtLocal = date))
        val validator = object : ParsedEventValidator {
            override fun validate(draft: ParsedEventDraft) = ValidationResult(DefaultParsedEventValidator(clock = clock).validate(draft).findings +
                errors.distinct().map { ValidationFinding("LAB-SHARED", it, ValidationSeverity.ERROR) })
        }
        val result = LabResultProjection.project(ParseFinalizer(validator).finalize(draft, "lab-$sampleId-${library.version}"))
        return result.copy(facts = result.facts.copy(balanceCurrency = balance?.value?.currency?.name, dueCurrency = due?.value?.currency?.name, dueAmount = due?.value?.amount?.toPlainString(), ibanSuffix = ibanSuffix, occurredAtLocal = date?.toString()),
            trace = listOf("engine:$VERSION", "library:v${library.version}", "bank:${bank.id}", "receivedAt:$receivedAt") +
                result.trace.map { if (it == "unrecognized_aljazira_format") "المكتبة المشتركة لم تتعرف على صيغة الرسالة" else it } + errors.distinct(),
            evidence = evidence.distinct(), normalizedText = sms.normalizedBody)
    }
}
