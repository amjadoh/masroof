package com.baraa.masroof.domain.rules

import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.PurchaseChannel
import com.baraa.masroof.domain.model.TransferOwnershipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionClassifierTest {

    private val ownedAccount = ResolvedContainerFacts(
        kind = ContainerKind.ACCOUNT,
        ownership = OwnershipStatus.OWNED,
    )
    private val externalAccount = ResolvedContainerFacts(
        kind = ContainerKind.ACCOUNT,
        ownership = OwnershipStatus.EXTERNAL,
    )
    private val ownedCreditCard = ResolvedContainerFacts(
        kind = ContainerKind.CARD,
        ownership = OwnershipStatus.OWNED,
        knownCardType = CardType.CREDIT,
    )
    private val ownedDebitCard = ResolvedContainerFacts(
        kind = ContainerKind.CARD,
        ownership = OwnershipStatus.OWNED,
        knownCardType = CardType.DEBIT,
    )
    private val unknownAccount = ResolvedContainerFacts(
        kind = ContainerKind.ACCOUNT,
        ownership = OwnershipStatus.UNKNOWN,
    )

    @Test
    fun ownedToOwned_sameBank_isSelfTransfer() {
        val result = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.TRANSFER_OUT,
                source = ownedAccount,
                destination = ownedAccount,
                bankNetworkType = BankNetworkType.INTRA_BANK,
            ),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.SELF_TRANSFER, classified.transactionType)
        assertEquals(TransferOwnershipType.SELF_TRANSFER, classified.transferOwnership)
        assertFalse(classified.impact.countsAsExpense)
        assertFalse(classified.impact.countsAsIncome)
        assertEquals(NetWorthEffect.ZERO, classified.impact.netWorthEffect)
    }

    @Test
    fun ownedToOwned_crossBank_isSelfTransfer() {
        val result = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.TRANSFER_OUT,
                source = ownedAccount,
                destination = ownedAccount,
                bankNetworkType = BankNetworkType.INTER_BANK,
            ),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.SELF_TRANSFER, classified.transactionType)
        assertFalse(classified.impact.countsAsExpense)
        assertFalse(classified.impact.countsAsIncome)
        assertEquals(NetWorthEffect.ZERO, classified.impact.netWorthEffect)
    }

    @Test
    fun externalToOwned_intraBank_isNotSelfTransfer() {
        val result = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.TRANSFER_IN,
                source = externalAccount,
                destination = ownedAccount,
                bankNetworkType = BankNetworkType.INTRA_BANK,
            ),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_IN, classified.transactionType)
        assertEquals(TransferOwnershipType.EXTERNAL_INCOMING, classified.transferOwnership)
        assertFalse(classified.impact.countsAsIncome)
        assertFalse(classified.impact.countsAsExpense)
        assertEquals(NetWorthEffect.UNRESOLVED, classified.impact.netWorthEffect)
    }

    @Test
    fun ownedToExternal_isExternalTransferOut() {
        val result = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.TRANSFER_OUT,
                source = ownedAccount,
                destination = externalAccount,
                bankNetworkType = BankNetworkType.INTRA_BANK,
            ),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_OUT, classified.transactionType)
        assertEquals(TransferOwnershipType.EXTERNAL_OUTGOING, classified.transferOwnership)
        assertFalse(classified.impact.countsAsExpense)
        assertFalse(classified.impact.countsAsIncome)
        assertEquals(NetWorthEffect.UNRESOLVED, classified.impact.netWorthEffect)
    }

    @Test
    fun externalTransfers_doNotInferNetWorthFromCashDirection() {
        val incoming = FinancialImpactCalculator.forType(FinancialTransactionType.EXTERNAL_TRANSFER_IN)
        val outgoing = FinancialImpactCalculator.forType(FinancialTransactionType.EXTERNAL_TRANSFER_OUT)

        assertFalse(incoming.countsAsExpense)
        assertFalse(incoming.countsAsIncome)
        assertEquals(NetWorthEffect.UNRESOLVED, incoming.netWorthEffect)

        assertFalse(outgoing.countsAsExpense)
        assertFalse(outgoing.countsAsIncome)
        assertEquals(NetWorthEffect.UNRESOLVED, outgoing.netWorthEffect)
    }

    @Test
    fun billPayment_classifiesToBillPaymentType() {
        val result = TransactionClassifier.classify(
            ClassificationEvidence(messageFamily = MessageFamily.BILL_PAYMENT),
        )

        assertTrue(result is ClassificationResult.Classified)
        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.BILL_PAYMENT, classified.transactionType)
        assertTrue(classified.impact.countsAsExpense)
        assertFalse(classified.impact.countsAsIncome)
        assertEquals(NetWorthEffect.DECREASE, classified.impact.netWorthEffect)
    }

    @Test
    fun debitOrCurrentPurchase_isExpense() {
        val debitResult = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.PURCHASE,
                instrument = ownedDebitCard,
                purchaseChannel = PurchaseChannel.POS,
            ),
        )
        val currentResult = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.PURCHASE,
                instrument = ownedAccount,
                purchaseChannel = PurchaseChannel.ONLINE,
            ),
        )

        val debit = debitResult as ClassificationResult.Classified
        val current = currentResult as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.EXPENSE, debit.transactionType)
        assertEquals(FinancialTransactionType.EXPENSE, current.transactionType)
        assertTrue(debit.impact.countsAsExpense)
        assertTrue(current.impact.countsAsExpense)
        assertFalse(debit.impact.countsAsIncome)
    }

    @Test
    fun creditCardPurchase_isExpense() {
        val result = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.PURCHASE,
                instrument = ownedCreditCard,
                purchaseChannel = PurchaseChannel.ONLINE,
            ),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.EXPENSE, classified.transactionType)
        assertTrue(classified.impact.countsAsExpense)
        assertEquals(NetWorthEffect.DECREASE, classified.impact.netWorthEffect)
    }

    @Test
    fun creditCardPayment_isNotExpense() {
        val fromFamily = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.CARD_PAYMENT,
                source = ownedAccount,
                destination = ownedCreditCard,
            ),
        )
        val fromTransferShape = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.TRANSFER_OUT,
                source = ownedAccount,
                destination = ownedCreditCard,
                bankNetworkType = BankNetworkType.INTRA_BANK,
            ),
        )

        val a = fromFamily as ClassificationResult.Classified
        val b = fromTransferShape as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.CREDIT_CARD_PAYMENT, a.transactionType)
        assertEquals(FinancialTransactionType.CREDIT_CARD_PAYMENT, b.transactionType)
        assertFalse(a.impact.countsAsExpense)
        assertFalse(a.impact.countsAsIncome)
        assertEquals(NetWorthEffect.ZERO, a.impact.netWorthEffect)
        assertFalse(b.impact.countsAsExpense)
    }

    @Test
    fun refund_isNotOrdinaryIncome() {
        val result = TransactionClassifier.classify(
            ClassificationEvidence(messageFamily = MessageFamily.REFUND),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.REFUND, classified.transactionType)
        assertFalse(classified.impact.countsAsIncome)
        assertFalse(classified.impact.countsAsExpense)
        assertEquals(NetWorthEffect.INCREASE, classified.impact.netWorthEffect)
    }

    @Test
    fun cashWithdrawal_isNotAutomaticallyExpense() {
        val result = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.WITHDRAWAL,
                instrument = ownedAccount,
            ),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.CASH_WITHDRAWAL, classified.transactionType)
        assertFalse(classified.impact.countsAsExpense)
        assertFalse(classified.impact.countsAsIncome)
        assertEquals(NetWorthEffect.ZERO, classified.impact.netWorthEffect)
    }

    @Test
    fun fee_countsAsExpense() {
        val result = TransactionClassifier.classify(
            ClassificationEvidence(messageFamily = MessageFamily.FEE),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.FEE, classified.transactionType)
        assertTrue(classified.impact.countsAsExpense)
        assertEquals(NetWorthEffect.DECREASE, classified.impact.netWorthEffect)
    }

    @Test
    fun unknownOwnership_doesNotGuessSelfOrExternalTransfer() {
        val ownedToUnknown = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.TRANSFER_OUT,
                source = ownedAccount,
                destination = unknownAccount,
                bankNetworkType = BankNetworkType.INTRA_BANK,
            ),
        )
        val unknownToOwned = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.TRANSFER_IN,
                source = unknownAccount,
                destination = ownedAccount,
                bankNetworkType = BankNetworkType.INTER_BANK,
            ),
        )
        val unknownToUnknown = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.TRANSFER_OUT,
                source = unknownAccount,
                destination = unknownAccount,
            ),
        )

        listOf(ownedToUnknown, unknownToOwned, unknownToUnknown).forEach { result ->
            assertTrue(result is ClassificationResult.NeedsReview)
            val review = result as ClassificationResult.NeedsReview
            assertEquals(TransferOwnershipType.UNKNOWN, review.transferOwnership)
            assertTrue(review.tentativeType != FinancialTransactionType.SELF_TRANSFER)
            assertTrue(review.tentativeType != FinancialTransactionType.EXTERNAL_TRANSFER_IN)
            assertTrue(review.tentativeType != FinancialTransactionType.EXTERNAL_TRANSFER_OUT)
        }
    }

    @Test
    fun cardPaymentEvidence_ownedAccountToOwnedCard_withoutInventedCardType() {
        val result = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.CARD_PAYMENT,
                source = ResolvedContainerFacts(
                    kind = ContainerKind.ACCOUNT,
                    ownership = OwnershipStatus.OWNED,
                ),
                destination = ResolvedContainerFacts(
                    kind = ContainerKind.CARD,
                    ownership = OwnershipStatus.OWNED,
                    knownCardType = null,
                ),
            ),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.CREDIT_CARD_PAYMENT, classified.transactionType)
        assertFalse(classified.impact.countsAsExpense)
    }

    @Test
    fun transferFamily_requiresKnownCreditTypeForCardPaymentShape() {
        val withoutType = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.TRANSFER_OUT,
                source = ResolvedContainerFacts(
                    kind = ContainerKind.ACCOUNT,
                    ownership = OwnershipStatus.OWNED,
                ),
                destination = ResolvedContainerFacts(
                    kind = ContainerKind.CARD,
                    ownership = OwnershipStatus.OWNED,
                    knownCardType = null,
                ),
            ),
        ) as ClassificationResult.Classified
        // Without a genuine CardType.CREDIT, do not invent credit-card payment.
        assertEquals(FinancialTransactionType.SELF_TRANSFER, withoutType.transactionType)

        val withCredit = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.TRANSFER_OUT,
                source = ResolvedContainerFacts(
                    kind = ContainerKind.ACCOUNT,
                    ownership = OwnershipStatus.OWNED,
                ),
                destination = ResolvedContainerFacts(
                    kind = ContainerKind.CARD,
                    ownership = OwnershipStatus.OWNED,
                    knownCardType = CardType.CREDIT,
                ),
            ),
        ) as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.CREDIT_CARD_PAYMENT, withCredit.transactionType)
    }

    @Test
    fun networkIndependence_sameOwnership_sameClassification() {
        val networks = listOf(
            BankNetworkType.INTRA_BANK,
            BankNetworkType.INTER_BANK,
            BankNetworkType.UNKNOWN,
            null,
        )

        networks.forEach { network ->
            val ownedOwned = TransactionClassifier.classify(
                ClassificationEvidence(
                    messageFamily = MessageFamily.TRANSFER_OUT,
                    source = ownedAccount,
                    destination = ownedAccount,
                    bankNetworkType = network,
                ),
            ) as ClassificationResult.Classified
            assertEquals(FinancialTransactionType.SELF_TRANSFER, ownedOwned.transactionType)

            val externalOwned = TransactionClassifier.classify(
                ClassificationEvidence(
                    messageFamily = MessageFamily.TRANSFER_IN,
                    source = externalAccount,
                    destination = ownedAccount,
                    bankNetworkType = network,
                ),
            ) as ClassificationResult.Classified
            assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_IN, externalOwned.transactionType)
        }
    }
}
