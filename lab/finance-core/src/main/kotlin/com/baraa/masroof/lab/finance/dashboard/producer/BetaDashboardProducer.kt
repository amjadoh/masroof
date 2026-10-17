package com.baraa.masroof.lab.finance.dashboard.producer

import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.dashboard.*
import com.baraa.masroof.lab.finance.matching.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.projection.*
import com.baraa.masroof.lab.finance.value.*
import java.math.BigDecimal
import java.util.Collections

enum class DashboardSection { ANALYSIS, TRENDS, MERCHANT_TOTALS, COMMITMENTS, RUNTIME_SOURCE, CURRENT_LEDGER, CONFIRMED_REGISTRY }
/** Selected-period money coverage only; this is deliberately NOT the complete AnalysisSummary. */
data class DashboardFinancialSummary(val metrics: Map<SummaryMetric, SignedAmount?>, val transactionCount: Int,
    val reviewRequiredCount: Int, val excludedOtherCurrencyCount: Int) {
    init {
        require(metrics.keys == SummaryMetric.entries.toSet())
        require(metrics.values.filterNotNull().all { it.currency == Currency.SAR })
        require(transactionCount >= 0 && reviewRequiredCount >= 0 && excludedOtherCurrencyCount >= 0)
    }
}
data class DashboardProduction(val snapshot: DashboardSnapshot, val unavailableSections: Set<DashboardSection>,
    val financialSummary: DashboardFinancialSummary?)

/** Read-only orchestration: one source call, then only pure immutable projection. */
class BetaDashboardProducer(private val source: DashboardReadSource) : DashboardReader {
    override suspend fun read(query: DashboardQuery): DashboardSnapshot {
        val result = readWithCoverage(query)
        require(DashboardSection.CURRENT_LEDGER !in result.unavailableSections && DashboardSection.CONFIRMED_REGISTRY !in result.unavailableSections) {
            "The existing snapshot cannot represent incomplete row coverage"
        }
        return result.snapshot
    }
    suspend fun readWithCoverage(query: DashboardQuery): DashboardProduction {
        val batch = source.load(query)
        require(batch.query == query) { "Source query does not match requested scope" }
        return BetaDashboardBuilder.build(batch)
    }
}

object BetaDashboardBuilder {
    fun build(batch: DashboardReadBatch): DashboardProduction {
        require(batch.query.environment == Environment.BETA && batch.query.currency == Currency.SAR) { "This increment supports Beta SAR reads only" }
        val assets = batch.assets.toList().sortedWith(compareBy({ it.id.bank.value }, { it.id.localId }))
        require(assets.all { it.id.environment == batch.query.environment })
        require(assets.map { it.id }.distinct().size == assets.size)
        require(assets.none { it.kind == AssetKind.CREDIT_CARD && it.parentId == null }) {
            "The existing snapshot cannot represent a credit card without a confirmed facility"
        }
        val registry = assets.associateBy { it.id }
        assets.forEach { asset -> asset.parentId?.let { parent ->
            val expected = if (asset.kind == AssetKind.DEBIT_CARD) AssetKind.ACCOUNT else AssetKind.CREDIT_FACILITY
            require(registry[parent]?.kind == expected) { "Dangling or wrong-kind confirmed parent" }
        } }
        val facts = batch.assetFacts.toList().associateUnique { it.id }
        require(facts.keys.all { it in registry })
        facts.values.forEach { fact ->
            val kind = registry.getValue(fact.id).kind
            require(fact.accountCategory == null || kind == AssetKind.ACCOUNT)
            require(fact.balance == null || kind in setOf(AssetKind.ACCOUNT, AssetKind.CREDIT_CARD))
            require(fact.statement == null || kind == AssetKind.CREDIT_FACILITY)
            require((fact.loanType == null && fact.loanRemaining == null && fact.loanInstallment == null) || kind == AssetKind.LOAN)
            require(fact.loanType == null || fact.loanType.isNotBlank())
            val dates = listOfNotNull(fact.balance?.asOf, fact.statement?.issuedAt, fact.loanRemaining?.asOf, fact.loanInstallment?.asOf)
            require(dates.all { it <= batch.revision.asOf && it <= batch.query.until }) { "Observation is newer than its read scope" }
        }
        val selected = DisplayPeriod(batch.query.from, batch.query.until, batch.query.zone, PeriodKind.SELECTED, null)
        val statements = batch.periods.statements.toMap()
        require(statements.keys.all { registry[it]?.kind == AssetKind.CREDIT_FACILITY })
        val periods = listOf(selected) + listOfNotNull(batch.periods.salary, batch.periods.calendar) + statements.values
        require(batch.periods.salary == null || batch.periods.salary.kind == PeriodKind.SALARY)
        require(batch.periods.calendar == null || batch.periods.calendar.kind == PeriodKind.CALENDAR_MONTH)
        require(statements.values.all { it.kind == PeriodKind.STATEMENT })
        require(periods.all { it.zone == batch.query.zone })
        val rawRows = batch.rows.toList()
        require(rawRows.map { it.posting.command.key }.distinct().size == rawRows.size) { "Only one current revision per stable key" }
        require(rawRows.all { row ->
            val input = row.posting.classification.input
            row.posting.command.key.environment == batch.query.environment && input.key.environment == batch.query.environment &&
                input.assets.keys.all { it.environment == batch.query.environment } &&
                row.posting.observations.all { it.input.key.environment == batch.query.environment }
        })
        val rows = rawRows.filter { it.active }.map { validateCurrent(it.posting, registry) }
            .sortedBy { it.command.key.transactionId }
        require(rows.all { row -> periods.any { contains(it, row.command.time.instant) } }) { "Unbounded ledger rows supplied" }
        val claimed = rows.flatMap { it.command.evidenceIds }
        require(claimed.distinct().size == claimed.size) { "Evidence claimed by multiple active postings" }
        val proofs = rows.mapNotNull { it.matchIdentity }
        require(proofs.distinct().size == proofs.size) { "Transfer proof claimed by multiple active postings" }
        val byKey = rows.associateBy { it.command.key }
        val details = batch.postingFacts.toList().associateUnique { it.key }
        details.values.forEach { fact ->
            val input = byKey[fact.key]?.classification?.input
            require(input != null && fact.revision == input.revision && fact.evidenceId in input.evidenceIds)
        }
        val fees = batch.fees.toList().associateUnique { it.key }
        fees.values.forEach { fee ->
            val input = byKey[fee.key]?.classification?.input
            require(input != null && fee.revision == input.revision && fee.evidenceId in input.evidenceIds)
            require(fee.amount.currency == Currency.SAR)
        }
        val context = ProjectionContext(batch.coverage, rows, fees)
        val queryProjection = context.period(selected)
        val salary = batch.periods.salary?.let(context::period)
        val calendar = batch.periods.calendar?.let(context::period)
        val statementProjections = statements.mapValues { context.period(it.value) }
        val accountRows = assets.filter { it.kind == AssetKind.ACCOUNT }.map { asset ->
            val fact = facts[asset.id]
            AccountSummary(asset.id, fact?.balance?.amount, fact?.label, fact?.accountCategory ?: AccountCategory.OTHER,
                fact?.balance?.asOf, queryProjection?.let { flow(it, asset.id) })
        }
        val debitRows = assets.filter { it.kind == AssetKind.DEBIT_CARD }.map { asset ->
            DebitSpending(asset.id, salary?.let { debitNet(it, asset) }, asset.parentId,
                facts[asset.id]?.label, asset.last4, facts[asset.id]?.network, batch.periods.salary)
        }
        val facilities = assets.filter { it.kind == AssetKind.CREDIT_FACILITY }.map { facility ->
            val statement = facts[facility.id]?.statement
            val members = assets.filter { it.kind == AssetKind.CREDIT_CARD && it.parentId == facility.id }.map { card ->
                val fact = facts[card.id]
                CreditCardSummary(card.id, card.creditCardRole, fact?.label, card.last4,
                    batch.periods.calendar?.let { PeriodSpending(it, calendar?.let { projection -> creditNet(projection, card) }) },
                    batch.periods.salary?.let { PeriodSpending(it, salary?.let { projection -> creditNet(projection, card) }) },
                    statements[facility.id]?.let { PeriodSpending(it, statementProjections[facility.id]?.let { projection -> creditNet(projection, card) }) },
                    fact?.balance?.amount, fact?.balance?.asOf)
            }
            FacilitySummary(facility.id, statement?.due, statement?.dueDate, statement?.issuedAt,
                salary?.let { facilityNet(it, facility.id) }, statementProjections[facility.id]?.let { facilityNet(it, facility.id) }, freezeList(members))
        }
        val loans = assets.filter { it.kind == AssetKind.LOAN }.map { loan ->
            val fact = facts[loan.id]
            LoanSummary(loan.id, fact?.loanType ?: "UNKNOWN", fact?.label.orEmpty(), fact?.loanRemaining?.amount,
                fact?.loanRemaining?.asOf, fact?.loanInstallment?.amount, fact?.loanInstallment?.asOf,
                salary?.let { loanPayment(it, loan.id) }, batch.periods.salary)
        }
        val inPeriod = rows.filter { contains(selected, it.command.time.instant) }
        val financialSummary = if (batch.coverage.completeLedger && batch.coverage.completeRegistry && batch.coverage.reviewRequiredCount != null) {
            DashboardFinancialSummary(freezeMap(SummaryMetric.entries.associateWith { metric ->
                queryProjection?.let { signed(it.sumOfMetric(metric)) }
            }), inPeriod.size, batch.coverage.reviewRequiredCount,
                inPeriod.count { it.command.amount.currency != Currency.SAR &&
                    PersistedSarProjection.project(it.classification.input, fees[it.command.key]) is SarRowProjection.Unavailable })
        } else null
        val fleet = queryProjection?.let { flow(it, null) }
        val completeRegistry = batch.coverage.completeRegistry
        val totals = PortfolioTotals(Currency.SAR, fleet?.externalIn, fleet?.externalOut, fleet?.cashPosition, fleet?.remaining,
            if (completeRegistry && assets.none { it.kind == AssetKind.CREDIT_CARD && it.parentId == null }) sumKnownMoney(facilities.map { it.statementDue }) else null,
            salary?.let { sumKnownSigned(facilities.map { it.salarySpending }) },
            salary?.let { sumKnownSigned(debitRows.map { it.spending }) },
            if (completeRegistry) sumKnownMoney(loans.map { it.remaining }) else null,
            salary?.let { projection -> signed(loans.fold(BigDecimal.ZERO) { sum, loan -> sum + loanPayment(projection, loan.id).amount }) })
        val transactions = inPeriod.map { row -> transaction(row, details[row.command.key], fees[row.command.key]) }
        val snapshot = DashboardSnapshot(batch.query, freezeList(accountRows), freezeList(debitRows), freezeList(facilities), freezeList(loans),
            null, null, freezeList(transactions), totals, batch.revision, fleet)
        val unavailable = setOf(DashboardSection.ANALYSIS, DashboardSection.TRENDS, DashboardSection.MERCHANT_TOTALS,
            DashboardSection.COMMITMENTS) +
            (if (!batch.coverage.atomicRuntimeSource) setOf(DashboardSection.RUNTIME_SOURCE) else emptySet()) +
            (if (!batch.coverage.completeLedger) setOf(DashboardSection.CURRENT_LEDGER) else emptySet()) +
            (if (!batch.coverage.completeRegistry) setOf(DashboardSection.CONFIRMED_REGISTRY) else emptySet())
        return DashboardProduction(snapshot, freezeSet(unavailable), financialSummary)
    }

    private fun validateCurrent(stored: StoredFinancialPosting, registry: Map<AssetId, OwnedAsset>): StoredFinancialPosting {
        val prepared = if (stored.matchIdentity == null) FinancialPostingRules.prepare(stored.classification.input) else {
            val identity = stored.matchIdentity
            val proof = when (identity.kind) {
                "BANK_REFERENCE" -> TransferMatchProof.BankReference(ConfirmedBankTransferReference(requireNotNull(identity.issuer), identity.reference))
                "USER_PAIR" -> {
                    require(identity.issuer == null)
                    val out = stored.observations.single { it.direction == TransferDirection.OUTBOUND }.input
                    val incoming = stored.observations.single { it.direction == TransferDirection.INBOUND }.input
                    TransferMatchProof.UserPair(identity.reference, out.key, out.revision, out.evidenceIds,
                        incoming.key, incoming.revision, incoming.evidenceIds)
                }
                else -> error("Unsupported stored transfer proof")
            }
            val match = SelfTransferMatcher.match(stored.observations, proof)
            require(match is TransferMatchResult.Matched)
            FinancialPostingRules.prepare(match.transfer)
        }
        require(prepared is PostingPreparation.Ready && prepared.posting == stored) { "Stored financial payload/gate is inconsistent" }
        val candidate = prepared.posting
        (listOf(candidate.classification.input) + candidate.observations.map { it.input }).forEach { input ->
            val endpoints = listOfNotNull((input.source as? FinancialEndpoint.Owned)?.id,
                (input.destination as? FinancialEndpoint.Owned)?.id, input.financingLoan).toSet()
            val dependencies = endpoints + endpoints.mapNotNull { input.assets[it]?.parentId }
            require(dependencies.all { id -> input.assets[id]?.let { it.id == id && registry[id] == it } == true }) {
                "Current confirmed registry differs from active posting dependencies"
            }
        }
        return candidate
    }

    private fun transaction(row: StoredFinancialPosting, details: DashboardPostingFacts?, fee: LinkedInternationalFee?): TransactionFacts {
        val input = row.classification.input
        val endpoints = listOfNotNull((input.source as? FinancialEndpoint.Owned)?.id, (input.destination as? FinancialEndpoint.Owned)?.id)
        val assets = endpoints.map(input.assets::getValue)
        val accounts = assets.filter { it.kind == AssetKind.ACCOUNT }.map { it.id } +
            assets.filter { it.kind == AssetKind.DEBIT_CARD }.mapNotNull { it.parentId }
        val cards = assets.filter { it.kind in setOf(AssetKind.DEBIT_CARD, AssetKind.CREDIT_CARD) }.map { it.id }
        val debit = if (row.classification.effectiveType in setOf(FinancialType.EXPENSE, FinancialType.CASH_WITHDRAWAL, FinancialType.REFUND)) {
            assets.filter { it.kind == AssetKind.DEBIT_CARD }.map { it.id }
        } else emptyList()
        val loan = if (row.classification.effectiveType == FinancialType.LOAN_REPAYMENT) input.financingLoan else null
        val loans = assets.filter { it.kind == AssetKind.LOAN }.map { it.id } + listOfNotNull(loan)
        val sar = if (input.amount.currency == Currency.SAR) null else (PersistedSarProjection.project(input) as? SarRowProjection.Available)?.sarAmount
        return TransactionFacts(input.key, input.revision, input.time, input.storedType, row.classification.effectiveType, input.amount,
            details?.merchant, details?.counterparty, assets.firstOrNull { it.id in cards }?.last4,
            freezeSet(accounts.toSet()), freezeSet(cards.toSet()), freezeSet(debit.toSet()), freezeSet(loans.toSet()),
            categories(row, assets), input.appliedFx, sar, fee?.amount, freezeSet(input.evidenceIds),
            (input.source as? FinancialEndpoint.Owned)?.id, (input.destination as? FinancialEndpoint.Owned)?.id, details?.categoryId)
    }

    private fun categories(row: StoredFinancialPosting, endpoints: List<OwnedAsset>): Set<ChartCategory> {
        val type = row.classification.effectiveType
        val category = when (type) {
            FinancialType.EXPENSE -> when (endpoints.firstOrNull()?.kind) {
                AssetKind.CREDIT_CARD, AssetKind.CREDIT_FACILITY -> ChartCategory.CREDIT_PURCHASES
                AssetKind.DEBIT_CARD -> ChartCategory.MADA_PURCHASES
                else -> ChartCategory.ACCOUNT_PURCHASES
            }
            FinancialType.FEE -> ChartCategory.FEES
            FinancialType.BILL_PAYMENT -> ChartCategory.BILLS
            FinancialType.CASH_WITHDRAWAL -> ChartCategory.CASH_WITHDRAWALS
            FinancialType.EXTERNAL_TRANSFER_IN, FinancialType.EXTERNAL_TRANSFER_OUT -> ChartCategory.EXTERNAL_TRANSFERS
            FinancialType.CREDIT_CARD_PAYMENT -> ChartCategory.CREDIT_SETTLEMENTS
            FinancialType.LOAN_REPAYMENT -> ChartCategory.LOAN_REPAYMENTS
            FinancialType.SELF_TRANSFER -> ChartCategory.INTERNAL_TRANSFERS
            FinancialType.REFUND -> ChartCategory.UNATTRIBUTED_REFUNDS
            FinancialType.INCOME -> null
            FinancialType.ADJUSTMENT, FinancialType.UNKNOWN -> error("Unresolved financial type cannot be an active posting")
        }
        return freezeSet(setOfNotNull(category))
    }
}

private class ProjectionContext(val coverage: DashboardCoverage, val rows: List<StoredFinancialPosting>, val fees: Map<PostingKey, LinkedInternationalFee>) {
    fun period(period: DisplayPeriod): List<SarRowProjection.Available>? {
        if (!coverage.completeLedger || !coverage.completeRegistry) return null
        val selected = rows.filter { contains(period, it.command.time.instant) }
        if (!coverage.completeFeeFacts && selected.any { it.command.amount.currency != Currency.SAR &&
                it.command.type != FinancialType.REFUND && it.command.appliedFx?.source == "SMS" }) return null
        val projected = selected.map { PersistedSarProjection.project(it.classification.input, fees[it.command.key]) }
        return if (projected.any { it is SarRowProjection.Unavailable }) null else projected.filterIsInstance<SarRowProjection.Available>()
    }
}

private fun List<SarRowProjection.Available>.sumOfMetric(metric: SummaryMetric) = fold(BigDecimal.ZERO) { sum, row -> sum + row.metrics.getValue(metric).amount }
private fun flow(rows: List<SarRowProjection.Available>, account: AssetId?): AccountFlow {
    val movements = rows.flatMap { it.accountMovements }.filter { account == null || it.account == account }
    val buckets = FlowBucket.entries.associateWith { bucket -> Money.of(movements.filter { it.bucket == bucket }
        .fold(BigDecimal.ZERO) { sum, movement -> sum + movement.amount.amount }, Currency.SAR) }
    fun sum(vararg keys: FlowBucket) = keys.fold(BigDecimal.ZERO) { total, key -> total + buckets.getValue(key).amount }
    val externalIn = sum(FlowBucket.SALARY, FlowBucket.OTHER_INCOME, FlowBucket.EXTERNAL_IN, FlowBucket.ACCOUNT_REFUNDS)
    val externalOut = sum(FlowBucket.EXTERNAL_OUT, FlowBucket.CREDIT_SETTLEMENT, FlowBucket.CASH_WITHDRAWAL,
        FlowBucket.BILLS, FlowBucket.POS_PURCHASES, FlowBucket.FEES, FlowBucket.LOAN)
    val totalIn = externalIn + sum(FlowBucket.INTERNAL_IN)
    val totalOut = externalOut + sum(FlowBucket.INTERNAL_OUT)
    return AccountFlow(Currency.SAR, freezeMap(buckets), Money.of(totalIn, Currency.SAR), Money.of(totalOut, Currency.SAR),
        Money.of(externalIn, Currency.SAR), Money.of(externalOut, Currency.SAR), signed(externalIn - externalOut), signed(totalIn - totalOut))
}
private fun cardNet(rows: List<SarRowProjection.Available>, id: AssetId) = signed(rows.fold(BigDecimal.ZERO) { sum, row ->
    val input = row.native.input
    val refund = row.native.effectiveType == FinancialType.REFUND
    val endpoint = (if (refund) input.destination else input.source) as? FinancialEndpoint.Owned
    val kind = input.assets[id]?.kind
    val accepted = when (kind) {
        AssetKind.DEBIT_CARD -> row.native.effectiveType in setOf(FinancialType.EXPENSE, FinancialType.CASH_WITHDRAWAL, FinancialType.REFUND)
        AssetKind.CREDIT_CARD -> row.native.effectiveType in setOf(FinancialType.EXPENSE, FinancialType.FEE, FinancialType.REFUND)
        else -> false
    }
    if (endpoint?.id == id && accepted) sum + (if (refund) row.sarAmount.amount.negate() else row.sarAmount.amount) else sum
})
private fun debitNet(rows: List<SarRowProjection.Available>, card: OwnedAsset): SignedAmount? {
    val unresolved = rows.any { row ->
        if (row.native.effectiveType !in setOf(FinancialType.EXPENSE, FinancialType.CASH_WITHDRAWAL, FinancialType.REFUND)) false
        else {
            val input = row.native.input
            val endpoint = (if (row.native.effectiveType == FinancialType.REFUND) input.destination else input.source) as? FinancialEndpoint.Owned
            val account = endpoint?.id?.let(input.assets::get)?.takeIf { it.kind == AssetKind.ACCOUNT }
            account != null && (card.parentId == account.id || (card.parentId == null && card.id.bank == account.id.bank))
        }
    }
    // An account-backed POS/ATM/refund may mention a Mada card in persisted involvement facts.
    // That exact role-bound card attribution is not part of this beginning's source contract.
    return if (unresolved) null else cardNet(rows, card.id)
}
private fun creditNet(rows: List<SarRowProjection.Available>, card: OwnedAsset): SignedAmount? {
    val unresolved = rows.any { row ->
        if (row.creditFacility != card.parentId || row.native.effectiveType !in setOf(FinancialType.EXPENSE, FinancialType.FEE, FinancialType.REFUND)) false
        else {
            val input = row.native.input
            val endpoint = (if (row.native.effectiveType == FinancialType.REFUND) input.destination else input.source) as? FinancialEndpoint.Owned
            endpoint?.id?.let(input.assets::get)?.kind == AssetKind.CREDIT_FACILITY
        }
    }
    return if (unresolved) null else cardNet(rows, card.id)
}
private fun facilityNet(rows: List<SarRowProjection.Available>, id: AssetId): SignedAmount? {
    val included = rows.filter { it.creditFacility == id }
    val cardIds = linkedSetOf<AssetId>()
    for (row in included) {
        if (row.native.effectiveType !in setOf(FinancialType.EXPENSE, FinancialType.FEE, FinancialType.REFUND)) continue
        val input = row.native.input
        val endpoint = (if (row.native.effectiveType == FinancialType.REFUND) input.destination else input.source) as? FinancialEndpoint.Owned
        val asset = endpoint?.id?.let(input.assets::get)
        // The legacy facility period indicator sums member-card spending. A direct facility
        // posting has no proved member allocation and cannot silently become an observed zero.
        if (asset?.kind != AssetKind.CREDIT_CARD) return null
        cardIds += asset.id
    }
    return signed(cardIds.fold(BigDecimal.ZERO) { sum, card -> sum + cardNet(included, card).amount })
}
private fun loanPayment(rows: List<SarRowProjection.Available>, id: AssetId) = signed(rows.filter { it.loan == id }
    .fold(BigDecimal.ZERO) { sum, row -> sum + row.sarAmount.amount })
private fun sumKnownMoney(values: List<Money?>): Money? = if (values.any { it == null || it.currency != Currency.SAR }) null
    else Money.of(values.fold(BigDecimal.ZERO) { sum, money -> sum + requireNotNull(money).amount }, Currency.SAR)
private fun sumKnownSigned(values: List<SignedAmount?>): SignedAmount? = if (values.any { it == null || it.currency != Currency.SAR }) null
    else signed(values.fold(BigDecimal.ZERO) { sum, value -> sum + requireNotNull(value).amount })
private fun signed(value: BigDecimal) = SignedAmount(value, Currency.SAR)
private fun contains(period: DisplayPeriod, instant: java.time.Instant) = instant >= period.from && instant < period.until
private fun <T, K> List<T>.associateUnique(key: (T) -> K): Map<K, T> {
    require(map(key).distinct().size == size) { "Ambiguous duplicate persisted facts" }
    return associateBy(key)
}
private fun <T> freezeList(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
private fun <T> freezeSet(values: Set<T>): Set<T> = Collections.unmodifiableSet(LinkedHashSet(values))
private fun <K, V> freezeMap(values: Map<K, V>): Map<K, V> = Collections.unmodifiableMap(LinkedHashMap(values))
