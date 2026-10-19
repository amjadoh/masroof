package com.baraa.masroof.lab.beta.application.evidence

import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.beta.application.BetaAssetProposal
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.IncomeAttribution
import com.baraa.masroof.lab.finance.posting.FinancialType
import com.baraa.masroof.lab.finance.value.*
import org.junit.Assert.*
import org.junit.Test

class BetaAutomaticPostingPolicyTest {
    private val bank = BankId("BANK_ALJAZIRA")
    private val account = OwnedAsset(AssetId(Environment.BETA, bank, "account"), AssetKind.ACCOUNT, "1234")
    private val debit = OwnedAsset(account.id.copy(localId = "debit"), AssetKind.DEBIT_CARD, "5678", account.id)
    private val credit = OwnedAsset(account.id.copy(localId = "credit"), AssetKind.CREDIT_CARD, "9012")
    private fun proposal(asset: OwnedAsset, bound: AssetId? = null) = BetaAssetProposal(asset.id.copy(localId = "proposal-${asset.id.localId}"), asset.kind, asset.last4!!, "evidence", "revision", bound)
    private fun decide(family: MessageFamily, direction: MoneyDirection = MoneyDirection.OUTGOING,
        assets: List<OwnedAsset> = listOf(account, debit, credit), proposals: List<BetaAssetProposal> = listOf(proposal(credit)),
        card: Boolean = true, salary: Boolean = false, financing: Boolean = false) = BetaAutomaticPostingPolicy.decide(
        family, direction, salary, financing, card, proposals, assets.associateBy { it.id })

    @Test fun creditPurchasesAndRefundsUseOppositeEndpointsAndKnownDirection() {
        val purchase = decide(MessageFamily.PURCHASE)
        assertEquals(FinancialType.EXPENSE, purchase.type); assertEquals(credit.id, purchase.source); assertNull(purchase.hold)
        val refund = decide(MessageFamily.REFUND, MoneyDirection.INCOMING)
        assertEquals(FinancialType.REFUND, refund.type); assertEquals(credit.id, refund.destination); assertNull(refund.hold)
        assertNotNull(decide(MessageFamily.PURCHASE, MoneyDirection.UNKNOWN).hold)
    }
    @Test fun debitRequiresConfirmedLinkedAccountAndNeverFallsBackToUnclassifiedCard() {
        assertNull(decide(MessageFamily.PURCHASE, proposals = listOf(proposal(debit))).hold)
        assertNotNull(decide(MessageFamily.PURCHASE, assets = listOf(debit.copy(parentId = null)), proposals = listOf(proposal(debit))).hold)
        assertNotNull(decide(MessageFamily.PURCHASE, proposals = listOf(proposal(account))).hold)
    }
    @Test fun ambiguousSuffixNeedsDecisionUnlessThisEvidenceHasAnExplicitBinding() {
        val other = credit.copy(id = credit.id.copy(localId = "collision"))
        assertEquals("AMBIGUOUS_OWNER", decide(MessageFamily.PURCHASE, assets = listOf(credit, other)).hold)
        assertEquals(credit.id, decide(MessageFamily.PURCHASE, assets = listOf(credit, other), proposals = listOf(proposal(credit, credit.id))).source)
        assertNotNull(decide(MessageFamily.PURCHASE, assets = listOf(credit.copy(id = credit.id.copy(bank = BankId("OTHER"))))).hold)
    }
    @Test fun salaryIsKnownIncomeWhileUnpairedTransfersAndLoanAttributionStayReviewable() {
        val salary = decide(MessageFamily.TRANSFER_IN, MoneyDirection.INCOMING, proposals = listOf(proposal(account)), card = false, salary = true)
        assertEquals(FinancialType.INCOME, salary.type); assertEquals(IncomeAttribution.SALARY, salary.income)
        assertEquals(account.id, salary.destination); assertNull(salary.hold)
        assertNotNull(decide(MessageFamily.TRANSFER_IN, MoneyDirection.INCOMING, proposals = listOf(proposal(account)), card = false).hold)
        assertNotNull(decide(MessageFamily.TRANSFER_OUT, proposals = listOf(proposal(account)), card = false).hold)
        assertNotNull(decide(MessageFamily.FEE, proposals = listOf(proposal(account)), card = false, financing = true).hold)
    }
    @Test fun cardSettlementRequiresBothExplicitOwnedEndpoints() {
        val result = decide(MessageFamily.CARD_PAYMENT, proposals = listOf(proposal(account), proposal(credit)))
        assertEquals(FinancialType.CREDIT_CARD_PAYMENT, result.type)
        assertEquals(account.id, result.source); assertEquals(credit.id, result.destination); assertNull(result.hold)
        assertNotNull(decide(MessageFamily.CARD_PAYMENT).hold)
        assertNotNull(decide(MessageFamily.OTP).hold)
    }
}
