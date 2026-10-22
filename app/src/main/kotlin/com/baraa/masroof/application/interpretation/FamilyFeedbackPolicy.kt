package com.baraa.masroof.application.interpretation

import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily

enum class LearningFeedbackState { LEARNED, BLOCKED, EXCLUDED, PENDING }
data class LearningFeedbackStatus(val state: LearningFeedbackState, val reason: String? = null)

/** A financial type is not generally an SMS family. Never learn an inferred family back into itself. */
object FamilyFeedbackPolicy {
    fun explicitFamily(type: FinancialTransactionType): MessageFamily? = when (type) {
        FinancialTransactionType.EXTERNAL_TRANSFER_IN -> MessageFamily.TRANSFER_IN
        FinancialTransactionType.EXTERNAL_TRANSFER_OUT -> MessageFamily.TRANSFER_OUT
        FinancialTransactionType.CREDIT_CARD_PAYMENT -> MessageFamily.CARD_PAYMENT
        FinancialTransactionType.REFUND -> MessageFamily.REFUND
        FinancialTransactionType.CASH_WITHDRAWAL -> MessageFamily.WITHDRAWAL
        FinancialTransactionType.BILL_PAYMENT -> MessageFamily.BILL_PAYMENT
        FinancialTransactionType.LOAN_REPAYMENT -> MessageFamily.FINANCING_INSTALLMENT
        FinancialTransactionType.FEE -> MessageFamily.FEE
        else -> null
    }

    fun contradicts(family: MessageFamily, type: FinancialTransactionType): Boolean = when (family) {
        MessageFamily.UNKNOWN -> false
        MessageFamily.PURCHASE -> type != FinancialTransactionType.EXPENSE
        MessageFamily.TRANSFER_IN -> type !in setOf(FinancialTransactionType.INCOME,
            FinancialTransactionType.EXTERNAL_TRANSFER_IN, FinancialTransactionType.SELF_TRANSFER)
        MessageFamily.TRANSFER_OUT -> type !in setOf(FinancialTransactionType.EXTERNAL_TRANSFER_OUT,
            FinancialTransactionType.SELF_TRANSFER)
        else -> explicitFamily(type) != family
    }
}
