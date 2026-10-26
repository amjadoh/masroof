package com.baraa.masroof.engine.bank.interpretation

import com.baraa.masroof.engine.bank.Evidence

/** Shared security gate: bank rules and learned patterns cannot override OTP evidence. */
internal fun otpEvidence(source: SourceText): List<Evidence> = OTP_TERMS.flatMap { term ->
    source.phrase(term).findAll(source.text).map { source.evidence("security.otp", "family", it.range) }.toList()
}

private val OTP_TERMS = listOf("otp", "one time password", "one-time password", "password", "passcode", "pin",
    "verification code", "security code", "authentication code", "رمز التحقق", "رمز تحقق", "رمز التوثيق",
    "رمز تاكيد", "رمز لمرة واحدة", "كلمة المرور", "كلمة مرور", "رمز التفعيل", "رمز سري")

internal fun hasUnprovenExecution(source: SourceText) = NON_EXECUTION_TERMS.any { source.phrase(it).containsMatchIn(source.text) }

internal fun hasNoticeContext(source: SourceText) = NOTICE_CONTEXT_TERMS.any { source.phrase(it).containsMatchIn(source.text) }

private val NON_EXECUTION_TERMS = listOf("declined", "rejected", "unsuccessful", "failed", "pending", "request", "cancelled", "canceled", "not", "never", "لم", "لن", "لا", "غير", "رفض", "مرفوض", "مرفوضة", "طلب", "قيد التنفيذ", "تعذر", "الغاء")
private val NOTICE_CONTEXT_TERMS = listOf("statement", "كشف حساب", "beneficiary added", "اضافة مستفيد", "advertisement", "offer", "عرض", "minimum payment", "payment due date", "تاريخ الاستحقاق")
