package com.baraa.masroof.application.review

import com.baraa.masroof.application.transaction.ReconciliationReviewCandidate
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.ReviewKind
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * One-time codes that name an amount for a money operation ("لا تشارك رمز التفعيل …
 * تحويل لبنك محلي | مبلغ SAR 84") with no bank message for that money after them.
 *
 * Some banks send only the code for a transfer or a card payment. The money still
 * leaves the account, so the user is asked whether it went through. Nothing is posted
 * until they say so. Retries of one operation (same bank and amount within
 * [RETRY_WINDOW]) are one question; any covered retry answers all of them.
 */
object UnconfirmedOtpFinder {
    const val REASON = "otp_without_confirmation"

    /** Confirmation SMS can lag behind the code; a younger code is not asked about yet. */
    val SETTLE_DELAY: Duration = Duration.ofHours(2)
    val LOOKBACK: Duration = Duration.ofDays(90)
    private val RETRY_WINDOW: Duration = Duration.ofMinutes(30)
    private val EARLY_POSTING: Duration = Duration.ofMinutes(10)

    /** A posted movement may carry the bank's fee and its VAT on top of the coded amount. */
    private val FEE_ALLOWANCE = BigDecimal("2.00")

    private val AMOUNT = Regex("مبلغ\\s*(?:SAR|ر\\.?س\\.?)\\s*(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d{1,2}))?")

    data class OtpMessage(val rawSmsId: String, val bank: Bank, val body: String, val receivedAt: Instant)

    data class Operation(val type: FinancialTransactionType, val amount: Money)

    data class Unconfirmed(val message: OtpMessage, val operation: Operation)

    /** The money operation a code authorizes, or null for login, device and other codes. */
    fun operation(body: String): Operation? {
        val match = AMOUNT.find(body) ?: return null
        val whole = match.groupValues[1].replace(",", "")
        val fraction = match.groupValues[2].ifEmpty { "0" }
        val amount = Money.of("$whole.$fraction", Currency.SAR).takeIf { it.amount.signum() > 0 } ?: return null
        val type = when {
            "سداد بطاقة" in body -> FinancialTransactionType.CREDIT_CARD_PAYMENT
            "سداد" in body -> FinancialTransactionType.BILL_PAYMENT
            "تحويل" in body -> FinancialTransactionType.EXTERNAL_TRANSFER_OUT
            "شراء" in body -> FinancialTransactionType.EXPENSE
            else -> return null
        }
        return Operation(type, amount)
    }

    fun find(otps: List<OtpMessage>, transactions: List<FinancialTransaction>, now: Instant): List<Unconfirmed> {
        val coded = otps
            .filter { !it.receivedAt.isAfter(now.minus(SETTLE_DELAY)) && it.receivedAt.isAfter(now.minus(LOOKBACK)) }
            .mapNotNull { otp -> operation(otp.body)?.let { Unconfirmed(otp, it) } }
            .sortedBy { it.message.receivedAt }
        val retries = mutableListOf<MutableList<Unconfirmed>>()
        for (item in coded) {
            val last = retries.lastOrNull()?.last()
            if (last != null && last.message.bank == item.message.bank &&
                last.operation.amount == item.operation.amount &&
                Duration.between(last.message.receivedAt, item.message.receivedAt) <= RETRY_WINDOW
            ) {
                retries.last() += item
            } else {
                retries += mutableListOf(item)
            }
        }
        return retries
            .filter { group -> group.none { covered(it, transactions) } }
            .map { it.last() }
    }

    fun reviewCandidates(unconfirmed: List<Unconfirmed>): List<ReconciliationReviewCandidate> =
        unconfirmed.map { ReconciliationReviewCandidate(it.message.rawSmsId, ReviewKind.NEEDS_REVIEW, listOf(REASON)) }

    private fun covered(item: Unconfirmed, transactions: List<FinancialTransaction>): Boolean {
        val bankMarker = ":${item.message.bank.id}:"
        val coded = item.operation.amount
        val from = item.message.receivedAt.minus(EARLY_POSTING)
        val until = item.message.receivedAt.plus(SETTLE_DELAY)
        return transactions.any { tx ->
            val sameBank = tx.sourceContainerId?.contains(bankMarker) == true ||
                tx.destinationContainerId?.contains(bankMarker) == true
            sameBank && tx.amount.currency == coded.currency &&
                tx.amount.amount >= coded.amount && tx.amount.amount <= coded.amount + FEE_ALLOWANCE &&
                !tx.occurredAt.isBefore(from) && !tx.occurredAt.isAfter(until)
        }
    }
}
