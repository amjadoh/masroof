package com.baraa.masroof.lab.engine

import com.baraa.masroof.lab.copy.bank.aljazira.classification.AlJaziraClassification
import com.baraa.masroof.lab.copy.bank.aljazira.classification.AlJaziraClassificationRule
import com.baraa.masroof.lab.copy.bank.aljazira.classification.AlJaziraClassificationSpecificity
import com.baraa.masroof.lab.copy.domain.model.MessageFamily
import com.baraa.masroof.lab.copy.domain.model.MoneyDirection
import com.baraa.masroof.lab.copy.parsing.normalizer.comparisonRegex

/** Bank-independent context candidates, resolved with all shared-library candidates.
 * These are additive guards: editing a library cannot turn a conflicting notice into a movement.
 */
internal object SharedContextRules {
    private val dueDate = comparisonRegex("""تاريخ\s+الاستحقاق|(?:payment|statement)\s+due\s+date""")
    private val dueAmount = comparisonRegex("""المبلغ\s+المستحق|due\s+amount|outstanding\s+balance""")
    private val beneficiary = comparisonRegex("""المستفيد|\bbeneficiary\b""")
    private val inactiveStatus = comparisonRegex("""حالة\s*[:：]?\s*غير\s+نشط|\bstatus\s*[:：]?\s*inactive\b""")
    private val feeTitle = comparisonRegex("""^(?:رسوم\b|transfer\s+fee\b|fee\s+for\b)""")
    private val transfer = comparisonRegex("""حوالة\s+(?:صادرة|واردة)|\b(?:outgoing|incoming|fund)\s+transfer\b""")

    val rules = listOf(
        AlJaziraClassificationRule.of("shared.context.statement", AlJaziraClassificationSpecificity.STATEMENT,
            matches = { dueDate.containsMatchIn(it) && dueAmount.containsMatchIn(it) },
            classify = { AlJaziraClassification(MessageFamily.NON_FINANCIAL, evidence = listOf("statement_due_date_and_amount")) }),
        AlJaziraClassificationRule.of("shared.context.beneficiary_status", AlJaziraClassificationSpecificity.ACCOUNT_NOTICE,
            matches = { beneficiary.containsMatchIn(it) && inactiveStatus.containsMatchIn(it) },
            classify = { AlJaziraClassification(MessageFamily.NON_FINANCIAL, evidence = listOf("inactive_beneficiary_status")) }),
        AlJaziraClassificationRule.of("shared.context.titled_transfer_fee", AlJaziraClassificationSpecificity.MOVEMENT,
            matches = { text -> feeTitle.containsMatchIn(text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()) && transfer.containsMatchIn(text) },
            classify = { AlJaziraClassification(MessageFamily.FEE, MoneyDirection.OUTGOING, evidence = listOf("fee_title_with_transfer_wording")) }),
    )
}
