package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.ParseStatus
import java.time.Instant

data class PrepaidCardCurrencyOverview(
    val currency: Currency,
    val topUps: Money,
    val spending: Money,
    val refunds: Money,
    val netMovement: SignedMoneyAmount,
    val observedBalance: Money?,
    val balanceReceivedAt: Instant?,
)

data class PrepaidCardOverview(
    val key: String,
    val bank: Bank,
    val last4: String,
    val displayLabel: String,
    val currencies: List<PrepaidCardCurrencyOverview>,
    val transactionIds: Set<String>,
    val unconvertedFundingCount: Int = 0,
)

/** Native-currency movements, not an invented opening balance or an estimated bank debit. */
object PrepaidCardOverviewBuilder {
    fun build(context: DashboardProjectionContext): List<PrepaidCardOverview> = with(context) {
        cardRegistry.filter { it.ownership == OwnershipStatus.OWNED && it.cardType == CardType.PREPAID }.map { card ->
            val container = requireNotNull(FinancialContainerIdFactory.cardId(card.bank, card.last4))
            val rows = transactions.filter { it.sourceContainerId == container || it.destinationContainerId == container }
                .distinctBy { it.id }
            val observations = parsedRecords.filter {
                it.event.cardRef?.bank == card.bank && it.event.cardRef?.last4 == card.last4 &&
                    it.event.parseStatus in setOf(ParseStatus.SUCCESS, ParseStatus.NON_FINANCIAL) &&
                    it.details.availableBalance != null
            }.mapNotNull { record ->
                rawSmsById[record.event.rawSmsId]?.receivedAt?.takeIf { it < periodEndExclusive }
                    ?.let { Triple(requireNotNull(record.details.availableBalance), it, record.event.id) }
            }
            val currencies = (rows.map { it.amount.currency } + observations.map { it.first.currency }).distinct().sortedBy { it.name }
            PrepaidCardOverview(
                key = CardTransactionInvolvementResolver.cardKey(card.bank.id, card.last4),
                bank = card.bank,
                last4 = card.last4,
                displayLabel = RegistryDisplayLabels.cardLabel(card),
                transactionIds = rows.mapTo(linkedSetOf()) { it.id },
                unconvertedFundingCount = rows.count {
                    it.type == FinancialTransactionType.SELF_TRANSFER && it.destinationContainerId == container &&
                        it.amount.currency != primaryCurrency && it.id !in sarEquivalents
                },
                currencies = currencies.map { currency ->
                    val matching = rows.filter { it.amount.currency == currency }
                    fun total(predicate: (com.baraa.masroof.domain.model.FinancialTransaction) -> Boolean) = matching
                        .filter(predicate).fold(Money.zero(currency)) { sum, row -> sum + row.amount }
                    val incoming = total { it.destinationContainerId == container }
                    val outgoing = total { it.sourceContainerId == container }
                    val observation = observations.filter { it.first.currency == currency }
                        .maxWithOrNull(compareBy<Triple<Money, Instant, String>> { it.second }.thenBy { it.third })
                    PrepaidCardCurrencyOverview(
                        currency = currency,
                        topUps = total { it.type == FinancialTransactionType.SELF_TRANSFER && it.destinationContainerId == container },
                        spending = total { it.type != FinancialTransactionType.SELF_TRANSFER && it.sourceContainerId == container },
                        refunds = total { it.type == FinancialTransactionType.REFUND && it.destinationContainerId == container },
                        netMovement = SignedMoneyAmount.difference(incoming, outgoing),
                        observedBalance = observation?.first,
                        balanceReceivedAt = observation?.second,
                    )
                },
            )
        }
    }
}
