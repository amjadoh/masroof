package com.baraa.masroof.application.credit

import com.baraa.masroof.application.dashboard.CardDueSource
import com.baraa.masroof.application.dashboard.CompanionDue
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.core.money.Currency
import java.math.BigDecimal

/** Dashboard adapter: each card's companion amount due from the facts the dashboard already read. */
fun CardCompanionWorkflow.asCardDueSource(): CardDueSource = CardDueSource { rows ->
    val facts = rows.associate { row ->
        CardDueSource.key(row) to CardCompanionWorkflow.SmsFacts(
            available = row.snapshot?.availableBalance,
            negativeAvailable = row.snapshot?.negativeAvailableBalance,
            availableAt = row.snapshot?.availableUpdatedAt,
            statementDueDate = row.snapshot?.dueDate,
        )
    }
    dues(rows.map { it.bank to it.last4 }, facts).mapValues { (_, due) ->
        CompanionDue(SignedMoneyAmount(BigDecimal.valueOf(due.amountHalalas, 2), Currency.SAR), due.payBy)
    }
}
