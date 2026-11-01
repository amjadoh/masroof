package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.application.locale.AppLocaleRepository
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.domain.repository.CardRegistryRepository
import com.baraa.masroof.domain.repository.LoanRegistryRepository
import com.baraa.masroof.domain.repository.CommitmentRepository
import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.domain.repository.ReviewRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Composes the dashboard read model: loads the shared [DashboardProjectionContext] once,
 * then delegates to the section projections (analysis, accounts, cards, commitments).
 * Business rules live in the section projections' specialist builders/calculators.
 */
class DashboardProjectionBuilder(
    private val financialTransactionRepository: FinancialTransactionRepository,
    private val reviewRepository: ReviewRepository,
    private val accountRegistryRepository: AccountRegistryRepository,
    private val cardRegistryRepository: CardRegistryRepository,
    private val loanRegistryRepository: LoanRegistryRepository,
    private val commitmentRepository: CommitmentRepository,
    private val appLocaleRepository: AppLocaleRepository,
    private val sarEquivalentResolver: TransactionSarEquivalentResolver,
    private val evidenceSource: DashboardEvidenceSource,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val primaryCurrency: Currency = Currency.SAR,
    private val openingBalances: AccountOpeningBalanceSource = AccountOpeningBalanceSource { emptyList() },
) {
    private val cardsProjection = CardsDashboardProjection(
        financialTransactionRepository = financialTransactionRepository,
        evidenceSource = evidenceSource,
        sarEquivalentResolver = sarEquivalentResolver,
    )
    private val commitmentsProjection = CommitmentsDashboardProjection(
        financialTransactionRepository = financialTransactionRepository,
        commitmentRepository = commitmentRepository,
        evidenceSource = evidenceSource,
        sarEquivalentResolver = sarEquivalentResolver,
    )

    /** [transactions] are the selected period's stored transactions. */
    suspend fun build(
        period: FinancialPeriod,
        transactions: List<FinancialTransaction>,
    ): DashboardOverview {
        val context = loadContext(period, transactions)
        val analysis = AnalysisDashboardProjection.build(context)
        val accounts = AccountsDashboardProjection.build(context)
        val cards = cardsProjection.build(context)
        val commitments = commitmentsProjection.build(context, cards.creditFacilities)
        val bankHierarchy = BankHierarchyBuilder.build(
            ownedAccounts = context.ownedAccounts,
            accountsFleet = accounts.accountsFleet,
            creditFacilities = cards.creditFacilities,
            loans = context.loans,
        )

        return DashboardOverview(
            period = period,
            isCurrentPeriod = period == FinancialPeriodPolicy.periodContaining(context.today),
            summary = analysis.summary,
            currentAccount = accounts.fleet,
            spendingSplit = accounts.spendingSplit,
            accountsFleet = accounts.accountsFleet,
            ownedAccountPeriodSummaries = accounts.perAccount,
            creditFacilities = cards.creditFacilities,
            loansOverview = commitments.loansOverview,
            commitmentsOverview = commitments.commitmentsOverview,
            merchantSpending = analysis.merchantSpending,
            dailySpendingTrend = analysis.dailySpendingTrend,
            spendingTrends = DashboardSpendingTrendsBuilder.build(context, accounts, cards),
            bankHierarchy = bankHierarchy,
            flowDetailGrouping = accounts.flowDetail,
            transactionAccountInvolvement = accounts.transactionAccountInvolvement,
            transactionCardInvolvement = cards.transactionCardInvolvement,
            transactionLoanInvolvement = commitments.transactionLoanInvolvement,
            transactionDebitSpendInvolvement = cards.transactionDebitSpendInvolvement,
            transactions = context.transactions,
            labSarEquivalents = context.sarEquivalents,
            transactionFacts = DashboardTransactionFactsBuilder.build(
                transactions = context.transactions,
                cardInvolvement = cards.transactionCardInvolvement,
                loanInvolvement = commitments.transactionLoanInvolvement,
            ),
            ownedAccountContainerIds = context.ownedAccountContainerIds,
            accountBalances = accountBalances(context),
        )
    }

    /**
     * Each confirmed opening plus the account's cash position since that day, with the same
     * evidence, rates and self-transfer rules as the period view. Independent of the period shown.
     */
    private suspend fun accountBalances(context: DashboardProjectionContext): List<AccountBalance> {
        val owned = context.ownedAccounts.associateBy { "${it.bank.id}:${it.maskedNumber}" }
        val openings = openingBalances.load().filter { it.key in owned }
        return openings.groupBy { it.date }.flatMap { (date, group) ->
            val transactions = financialTransactionRepository.listOccurredBetween(
                startInclusive = FinancialPeriodPolicy.toInclusiveStartInstant(date, zoneId),
                endExclusive = BALANCE_WINDOW_END,
            )
            val prepared = prepare(transactions, context.cardRegistry, BALANCE_WINDOW_END)
            val summaries = OwnedAccountPeriodSummaryCalculator.summarize(
                ownedAccounts = group.map { owned.getValue(it.key) },
                transactions = prepared.transactions,
                parsedRecords = prepared.evidence.parsedRecords,
                primaryCurrency = primaryCurrency,
                sarEquivalents = prepared.sarAmounts,
                debitCardScope = DebitCardScopeFactory.fromRegistry(
                    cards = context.cardRegistry,
                    parsedRecords = prepared.evidence.parsedRecords,
                    registryAccounts = context.ownedAccounts,
                ),
            )
            AccountBalanceCalculator.balances(group, summaries)
        }
    }

    private class Prepared(
        val evidence: DashboardEvidence,
        val transactions: List<FinancialTransaction>,
        val sarAmounts: Map<String, com.baraa.masroof.core.money.Money>,
    )

    private suspend fun prepare(
        transactions: List<FinancialTransaction>,
        cardRegistry: List<com.baraa.masroof.domain.model.CardRegistryEntry>,
        endExclusive: java.time.Instant,
    ): Prepared {
        val evidence = evidenceSource.load(
            transactions = transactions,
            registryCards = cardRegistry,
            periodEndExclusive = endExclusive,
        )
        val enrichedTransactions = TransactionDisplayEnricher.enrichMerchants(
            transactions = transactions,
            parsedRecords = evidence.parsedRecords,
        )
        val sarResolutions = sarEquivalentResolver.resolve(
            transactions = enrichedTransactions,
            parsedRecords = evidence.parsedRecords,
            rawSmsById = evidence.rawSmsById,
            primaryCurrency = primaryCurrency,
        )
        val displayedTransactions = SelfTransferDeduplicator.filter(
            transactions = AppliedExchangeRateSyncer.applyInMemory(enrichedTransactions, sarResolutions),
            parsedRecords = evidence.parsedRecords,
        )
        return Prepared(evidence, displayedTransactions, sarResolutions.sarAmounts())
    }

    private suspend fun loadContext(
        period: FinancialPeriod,
        transactions: List<FinancialTransaction>,
    ): DashboardProjectionContext {
        val cardRegistry = cardRegistryRepository.listAll()
        val periodEndExclusive = FinancialPeriodPolicy.toExclusiveEndInstant(period.endDateExclusive, zoneId)
        val prepared = prepare(transactions, cardRegistry, periodEndExclusive)
        val evidence = prepared.evidence
        val displayedTransactions = prepared.transactions
        val ownedAccounts = accountRegistryRepository.listAll()
            .filter { it.bank != Bank.UNKNOWN && it.ownership == OwnershipStatus.OWNED }
        return DashboardProjectionContext(
            period = period,
            periodEndExclusive = periodEndExclusive,
            today = LocalDate.now(clock),
            evidence = evidence,
            transactions = displayedTransactions,
            sarEquivalents = prepared.sarAmounts,
            reviewRequiredCount = reviewRepository.listRequired().size,
            ownedAccounts = ownedAccounts,
            ownedAccountContainerIds = ownedAccounts
                .mapNotNull { FinancialContainerIdFactory.accountId(it.bank, it.maskedNumber) }
                .toSet(),
            ownedAccountLast4s = CurrentAccountTransactionScope.ownedAccountLast4sFromMaskedNumbers(
                ownedAccounts.map { it.maskedNumber },
            ),
            cardRegistry = cardRegistry,
            loans = loanRegistryRepository.listAll(),
            debitCardScope = DebitCardScopeFactory.fromRegistry(
                cards = cardRegistry,
                parsedRecords = evidence.parsedRecords,
                registryAccounts = ownedAccounts,
            ),
            displayLocale = AppLocale.displayLocale(appLocaleRepository.getLanguageTag()),
            zoneId = zoneId,
            primaryCurrency = primaryCurrency,
        )
    }

    private companion object {
        /** Balances include every movement after their opening day, also future-dated ones. */
        val BALANCE_WINDOW_END: java.time.Instant = java.time.Instant.parse("9999-01-01T00:00:00Z")
    }
}
