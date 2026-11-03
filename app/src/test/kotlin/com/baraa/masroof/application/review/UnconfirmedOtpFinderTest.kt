package com.baraa.masroof.application.review

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Fictional SNB one-time codes in the bank's layout. */
class UnconfirmedOtpFinderTest {
    private val snb = Bank("BANK_SNB")
    private val now = Instant.parse("2026-09-20T12:00:00Z")
    private val sentAt = Instant.parse("2026-09-15T13:29:00Z")

    @Test fun readsTheCodedOperationAndAmount() {
        assertEquals(
            UnconfirmedOtpFinder.Operation(FinancialTransactionType.EXTERNAL_TRANSFER_OUT, sar("84.00")),
            UnconfirmedOtpFinder.operation("لا تشارك رمز التفعيل 1111\nتحويل لبنك محلي\nمبلغ SAR 84"),
        )
        assertEquals(
            UnconfirmedOtpFinder.Operation(FinancialTransactionType.CREDIT_CARD_PAYMENT, sar("2487.90")),
            UnconfirmedOtpFinder.operation("لا تشارك رمز التفعيل 2222\nسداد بطاقة إئتمانية\nمبلغ SAR 2,487.9"),
        )
        assertEquals(
            FinancialTransactionType.BILL_PAYMENT,
            UnconfirmedOtpFinder.operation("لا تشارك رمز التفعيل 3333\nسداد فواتير مسجلة\nمبلغ SAR 10")?.type,
        )
    }

    @Test fun loginCodesAreNotOperations() {
        assertNull(UnconfirmedOtpFinder.operation("لا تشارك رمز التفعيل 4444\nتسجيل الدخول الأهلي موبايل"))
    }

    @Test fun aCodeWithNoBankMessageIsAsked() {
        val found = UnconfirmedOtpFinder.find(listOf(transferCode("otp-1", sentAt)), emptyList(), now)
        assertEquals(listOf("otp-1"), found.map { it.message.rawSmsId })
    }

    @Test fun aPostedTransferWithItsFeeAnswersTheCode() {
        val posted = posted(sar("84.29"), sentAt.plusSeconds(40))
        assertTrue(UnconfirmedOtpFinder.find(listOf(transferCode("otp-1", sentAt)), listOf(posted), now).isEmpty())
    }

    @Test fun anotherBanksMovementDoesNotAnswerTheCode() {
        val posted = posted(sar("84.00"), sentAt.plusSeconds(40), source = "account:BANK_STC:3448")
        assertEquals(1, UnconfirmedOtpFinder.find(listOf(transferCode("otp-1", sentAt)), listOf(posted), now).size)
    }

    @Test fun retriesAreOneQuestionAndAnyPostedRetryAnswersAll() {
        val codes = listOf(transferCode("otp-1", sentAt), transferCode("otp-2", sentAt.plus(Duration.ofMinutes(5))))
        assertEquals(listOf("otp-2"), UnconfirmedOtpFinder.find(codes, emptyList(), now).map { it.message.rawSmsId })
        val posted = posted(sar("84.25"), sentAt.plus(Duration.ofMinutes(6)))
        assertTrue(UnconfirmedOtpFinder.find(codes, listOf(posted), now).isEmpty())
    }

    @Test fun aFreshCodeWaitsForItsConfirmation() {
        val fresh = transferCode("otp-1", now.minus(Duration.ofMinutes(30)))
        assertTrue(UnconfirmedOtpFinder.find(listOf(fresh), emptyList(), now).isEmpty())
    }

    @Test fun oldCodesAreNotAskedAbout() {
        val old = transferCode("otp-1", now.minus(Duration.ofDays(120)))
        assertTrue(UnconfirmedOtpFinder.find(listOf(old), emptyList(), now).isEmpty())
    }

    private fun transferCode(id: String, at: Instant) =
        UnconfirmedOtpFinder.OtpMessage(id, snb, "لا تشارك رمز التفعيل 1111\nتحويل لبنك محلي\nمبلغ SAR 84", at)

    private fun posted(amount: Money, at: Instant, source: String = "account:BANK_SNB:0100") = FinancialTransaction(
        "tx-$at", FinancialTransactionType.EXTERNAL_TRANSFER_OUT, amount, at, source, null, null, null, null, emptyList(),
    )

    private fun sar(amount: String) = Money.of(amount, Currency.SAR)
}
