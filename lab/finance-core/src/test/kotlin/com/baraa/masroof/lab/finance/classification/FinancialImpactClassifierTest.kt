package com.baraa.masroof.lab.finance.classification

import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.dashboard.FlowBucket
import com.baraa.masroof.lab.finance.dashboard.SummaryMetric
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** Expectations characterized from MonthlyFinancialSummaryCalculator, MonthlyFinancialSummary,
 * AccountFlowClassifier, AccountOutflow.coreTotal and LoanRepaymentAttribution (read-only).
 */
class FinancialImpactClassifierTest {
    private val bank = BankId("BANK_ALJAZIRA")
    private fun id(value: String) = AssetId(Environment.BETA, bank, value)
    private val account = OwnedAsset(id("account"), AssetKind.ACCOUNT, "1234")
    private val otherAccount = OwnedAsset(id("other-account"), AssetKind.ACCOUNT, "1234")
    private val facility = OwnedAsset(id("facility"), AssetKind.CREDIT_FACILITY)
    private val credit = OwnedAsset(id("primary"), AssetKind.CREDIT_CARD, "1234", facility.id, CreditCardRole.PRIMARY)
    private val supplementary = OwnedAsset(id("supplementary"), AssetKind.CREDIT_CARD, "1234", facility.id, CreditCardRole.SUPPLEMENTARY)
    private val debit = OwnedAsset(id("debit"), AssetKind.DEBIT_CARD, "1234", account.id)
    private val loan = OwnedAsset(id("loan"), AssetKind.LOAN)
    private val registry = listOf(account, otherAccount, facility, credit, supplementary, debit, loan).associateBy { it.id }
    private val external = FinancialEndpoint.External
    private fun owned(asset: OwnedAsset) = FinancialEndpoint.Owned(asset.id)
    private fun input(type: FinancialType = FinancialType.EXPENSE,
        source: FinancialEndpoint = owned(account), destination: FinancialEndpoint = external,
    ) = FinancialClassificationInput(PostingKey(Environment.BETA, "tx-1"), PostingRevision(0, 1),
        type, Money.of("100", Currency.SAR), TransactionTime(Instant.parse("2026-10-16T09:00:00Z"),
            ZoneId.of("Asia/Riyadh")), source, destination, registry, setOf("evidence-1"), true,
        incomeAttribution = if (type in setOf(FinancialType.INCOME, FinancialType.EXTERNAL_TRANSFER_IN)) IncomeAttribution.OTHER else null)
    private fun classified(input: FinancialClassificationInput) =
        FinancialImpactClassifier.classify(input) as FinancialClassification.Classified
    private fun metric(result: FinancialClassification.Classified, key: SummaryMetric, expected: String) {
        assertEquals(key.name, 0, result.metrics.getValue(key).amount.compareTo(BigDecimal(expected)))
    }

    @Test fun legacyMetricMatrixKeepsPurchasesSettlementsAndActualAccountSpendingSeparate() {
        data class Case(val name: String, val input: FinancialClassificationInput,
            val gross: Int, val refunds: Int, val actual: Int, val credit: Int, val netCash: Int,
            val settlement: Settlement = Settlement.NONE)
        val cases = listOf(
            Case("account purchase", input(), 100, 0, 100, 0, -100),
            Case("debit purchase", input(source = owned(debit)), 100, 0, 100, 0, -100),
            Case("credit purchase", input(source = owned(credit)), 100, 0, 0, 100, -100),
            Case("supplementary purchase", input(source = owned(supplementary)), 100, 0, 0, 100, -100),
            Case("account refund", input(FinancialType.REFUND, external, owned(account)), 0, 100, 0, 0, 100),
            Case("debit refund", input(FinancialType.REFUND, external, owned(debit)), 0, 100, 0, 0, 100),
            Case("credit refund", input(FinancialType.REFUND, external, owned(credit)), 0, 100, 0, -100, 100),
            Case("credit settlement", input(FinancialType.CREDIT_CARD_PAYMENT, owned(account), owned(facility)), 0, 0, 100, 0, 0, Settlement.CREDIT),
            Case("loan repayment", input(FinancialType.LOAN_REPAYMENT, owned(account), owned(loan)), 0, 0, 100, 0, 0, Settlement.LOAN),
            Case("financing fee", input(FinancialType.FEE).copy(financingLoan = loan.id), 0, 0, 100, 0, 0, Settlement.LOAN),
            Case("fee", input(FinancialType.FEE), 100, 0, 100, 0, -100),
            Case("bill", input(FinancialType.BILL_PAYMENT), 100, 0, 100, 0, -100),
            Case("withdrawal", input(FinancialType.CASH_WITHDRAWAL), 0, 0, 100, 0, 0),
            Case("external out", input(FinancialType.EXTERNAL_TRANSFER_OUT), 0, 0, 100, 0, -100),
            Case("income", input(FinancialType.INCOME, external, owned(account)), 0, 0, 0, 0, 100),
            Case("external in", input(FinancialType.EXTERNAL_TRANSFER_IN, external, owned(account)), 0, 0, 0, 0, 100),
            Case("self transfer", input(FinancialType.SELF_TRANSFER, owned(account), owned(otherAccount)), 0, 0, 0, 0, 0),
        )
        cases.forEach { case ->
            val result = classified(case.input)
            assertEquals(case.name, SummaryMetric.entries.toSet(), result.metrics.keys)
            metric(result, SummaryMetric.SPENDING_GROSS, case.gross.toString())
            metric(result, SummaryMetric.REFUNDS, case.refunds.toString())
            metric(result, SummaryMetric.SPENDING_NET, (case.gross - case.refunds).toString())
            metric(result, SummaryMetric.ACTUAL_ACCOUNT_SPENDING, case.actual.toString())
            metric(result, SummaryMetric.CREDIT_PURCHASES, case.credit.toString())
            metric(result, SummaryMetric.NET_CASH_FLOW, case.netCash.toString())
            assertEquals(case.name, case.settlement, result.settlement)
            assertEquals(case.input, result.input)
        }
    }

    @Test fun creditPurchaseAndSettlementNeverDoubleCountMonthlyPurchaseOrAccountCash() {
        val purchase = classified(input(source = owned(credit)))
        val payment = classified(input(FinancialType.CREDIT_CARD_PAYMENT, owned(account), owned(credit)))
        assertTrue(purchase.accountMovements.isEmpty())
        assertEquals(listOf(AccountMovement(account.id, FlowBucket.CREDIT_SETTLEMENT, input().amount)), payment.accountMovements)
        metric(payment, SummaryMetric.SPENDING_GROSS, "0")
        metric(payment, SummaryMetric.CREDIT_SETTLEMENTS, "100")
        assertEquals(BigDecimal("100.00"), purchase.metrics.getValue(SummaryMetric.SPENDING_GROSS).amount +
            payment.metrics.getValue(SummaryMetric.SPENDING_GROSS).amount)
    }

    @Test fun debitRefundCreditsLinkedAccountAndCreditRefundOnlyReducesCardSpending() {
        val debitRefund = classified(input(FinancialType.REFUND, external, owned(debit)))
        assertEquals(listOf(AccountMovement(account.id, FlowBucket.ACCOUNT_REFUNDS, input().amount)), debitRefund.accountMovements)
        val creditRefund = classified(input(FinancialType.REFUND, external, owned(supplementary)))
        assertTrue(creditRefund.accountMovements.isEmpty())
        metric(creditRefund, SummaryMetric.SPENDING_NET, "-100")
        metric(creditRefund, SummaryMetric.CREDIT_PURCHASES, "-100")
    }

    @Test fun financingEvidenceDoesNotOverrideExplicitOtherFinancialType() {
        val input = input(FinancialType.BILL_PAYMENT).copy(financingLoan = loan.id)
        val result = classified(input)
        assertEquals(FinancialType.BILL_PAYMENT, result.effectiveType)
        assertEquals(Settlement.NONE, result.settlement)
        assertEquals(FlowBucket.BILLS, result.accountMovements.single().bucket)
        metric(result, SummaryMetric.SPENDING_GROSS, "100")
        // Even obsolete/unresolved financing attribution is irrelevant after an explicit other type.
        assertEquals(result.metrics, classified(input.copy(financingLoan = id("old-missing-loan"))).metrics)
    }

    @Test fun financingAttributionNeedsResolvedLoanAndRejectsConflictingTarget() {
        assertReview(input(FinancialType.FEE).copy(financingLoan = account.id), ReviewReason.VALIDATION)
        assertReview(input(FinancialType.FEE).copy(financingLoan = loan.id,
            assets = registry + (loan.id to loan.copy(id = id("different-loan")))) , ReviewReason.VALIDATION)
        assertReview(input(FinancialType.LOAN_REPAYMENT, destination = owned(loan))
            .copy(financingLoan = otherAccount.id), ReviewReason.VALIDATION)
    }

    @Test fun selfTransferKeepsOneIdentityAndBothAccountLegsWithoutPortfolioSpending() {
        val input = input(FinancialType.SELF_TRANSFER, owned(account), owned(otherAccount))
        val result = classified(input)
        assertEquals(input.key, result.input.key)
        assertEquals(listOf(AccountMovement(account.id, FlowBucket.INTERNAL_OUT, input.amount),
            AccountMovement(otherAccount.id, FlowBucket.INTERNAL_IN, input.amount)), result.accountMovements)
        metric(result, SummaryMetric.SELF_TRANSFERS, "100")
        metric(result, SummaryMetric.ACTUAL_ACCOUNT_SPENDING, "0")
        assertEquals(result, classified(input))
        // Similar messages are not paired here: another caller transaction stays independent.
        assertNotEquals(result.input.key, classified(input.copy(key = input.key.copy(transactionId = "tx-2"))).input.key)
        assertReview(input.copy(destination = owned(account)), ReviewReason.AMBIGUOUS_MATCH)
    }

    @Test fun suffixOrAbsentOwnerCannotCreateAnOwnedMovement() {
        assertReview(input(source = FinancialEndpoint.Unresolved), ReviewReason.OWNERSHIP)
        assertReview(input(source = owned(account)).copy(assets = registry - account.id), ReviewReason.OWNERSHIP)
        assertReview(input(FinancialType.EXTERNAL_TRANSFER_OUT, destination = owned(otherAccount)), ReviewReason.AMBIGUOUS_MATCH)
        assertReview(input(FinancialType.REFUND, external, FinancialEndpoint.Unresolved), ReviewReason.OWNERSHIP)
    }

    @Test fun danglingWrongKindAndOtherEnvironmentParentsFailClosed() {
        assertReview(input(source = owned(debit)).copy(assets = registry - account.id), ReviewReason.OWNERSHIP)
        assertReview(input(source = owned(debit)).copy(assets = registry + (account.id to facility)), ReviewReason.OWNERSHIP)
        val foreign = account.copy(id = account.id.copy(environment = Environment.MIGRATION_REHEARSAL))
        assertReview(input(source = owned(foreign)).copy(assets = registry + (foreign.id to foreign)), ReviewReason.OWNERSHIP)
    }

    @Test fun classificationDoesNotLiftAutomationGateAndMetadataOnlyNeverConfirmsFinancialUse() {
        listOf(CorrectionAuthority.NONE, CorrectionAuthority.METADATA_ONLY).forEach {
            assertReview(input().copy(validatedParse = false, authority = it), ReviewReason.VALIDATION)
        }
        listOf(CorrectionAuthority.CONFIRMED_FAMILY_OR_AMOUNT, CorrectionAuthority.USER_FINANCIAL_TYPE).forEach {
            classified(input().copy(validatedParse = false, authority = it))
            assertReview(input(source = FinancialEndpoint.Unresolved).copy(validatedParse = false, authority = it), ReviewReason.OWNERSHIP)
            assertReview(input().copy(authority = it, unresolved = setOf(ReviewReason.AMBIGUOUS_MATCH)), ReviewReason.AMBIGUOUS_MATCH)
        }
    }

    @Test fun unknownAdjustmentAndMissingEvidenceNeverExposeImpact() {
        assertReview(input(FinancialType.UNKNOWN), ReviewReason.UNKNOWN_TYPE)
        assertReview(input(FinancialType.ADJUSTMENT), ReviewReason.UNKNOWN_TYPE)
        assertReview(input().copy(evidenceIds = emptySet()), ReviewReason.VALIDATION)
        assertReview(input().copy(evidenceIds = setOf("")), ReviewReason.VALIDATION)
    }

    @Test fun nativeForeignAmountPrecisionAndAttributionSurviveWithoutInventedConversion() {
        val fx = AppliedFx(BigDecimal("3.751234"), "SMS")
        val input = input(source = owned(credit)).copy(amount = Money.of("19.99", Currency.USD), appliedFx = fx)
        val result = classified(input)
        assertSame(input.amount, result.input.amount)
        assertSame(fx, result.input.appliedFx)
        assertSame(input.time, result.input.time)
        assertEquals(input.evidenceIds, result.input.evidenceIds)
        assertEquals(input.revision, result.input.revision)
        assertEquals(BigDecimal("3.751234"), result.input.appliedFx!!.rateToSar)
        assertTrue(result.metrics.values.all { it.currency == Currency.USD })
        metric(result, SummaryMetric.CREDIT_PURCHASES, "19.99")
        val unresolvedFx = classified(input.copy(appliedFx = null))
        assertNull(unresolvedFx.input.appliedFx)
        assertEquals(result.metrics, unresolvedFx.metrics)
        // A half FX pair is not representable: AppliedFx requires a positive rate and nonblank source.
        assertThrows(IllegalArgumentException::class.java) { AppliedFx(BigDecimal("3.75"), "") }
        assertThrows(IllegalArgumentException::class.java) { AppliedFx(BigDecimal.ZERO, "SMS") }
    }

    @Test fun callerCollectionChangesCannotRewriteValidatedClassificationSnapshot() {
        val assets = registry.toMutableMap()
        val evidence = mutableSetOf("evidence-1")
        val unresolved = mutableSetOf<ReviewReason>()
        val result = classified(input().copy(assets = assets, evidenceIds = evidence, unresolved = unresolved))
        assets.clear()
        evidence.clear()
        unresolved += ReviewReason.VALIDATION
        assertEquals(registry, result.input.assets)
        assertEquals(setOf("evidence-1"), result.input.evidenceIds)
        assertTrue(result.input.unresolved.isEmpty())
        assertEquals(account.id, result.accountMovements.single().account)
    }

    @Test fun storedIncomePrecedenceAndTransferAttributionPreserveFactsAndMonthlyFinancialType() {
        listOf(FinancialType.INCOME, FinancialType.EXTERNAL_TRANSFER_IN).forEach { type ->
            val candidate = input(type, external, owned(account))
            val ordinary = classified(candidate)
            val salary = classified(candidate.copy(incomeAttribution = IncomeAttribution.SALARY))
            assertEquals(if (type == FinancialType.INCOME) FlowBucket.SALARY else FlowBucket.EXTERNAL_IN,
                ordinary.accountMovements.single().bucket)
            assertEquals(FlowBucket.SALARY, salary.accountMovements.single().bucket)
            assertEquals(ordinary.metrics, salary.metrics)
            assertEquals(type, salary.effectiveType)
            assertEquals(IncomeAttribution.OTHER, ordinary.input.incomeAttribution)
            assertEquals(IncomeAttribution.SALARY, salary.input.incomeAttribution)
            assertReview(candidate.copy(incomeAttribution = null), ReviewReason.VALIDATION)
        }
    }

    private fun assertReview(input: FinancialClassificationInput, reason: ReviewReason) {
        val result = FinancialImpactClassifier.classify(input)
        assertTrue("Expected $reason, got $result", result is FinancialClassification.Review && reason in result.reasons)
    }
}
