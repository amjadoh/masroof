package com.baraa.masroof.bank.sab

import java.math.BigDecimal

/**
 * Reads SAB's "تم تحويل العملية الشرائية ... إلى N قسط" confirmation into a 0%-profit plan.
 *
 * Ported from the standalone SAB Visa tracker. Promotions ("حوّل عمليتك إلى أقساط") are not
 * confirmations. A plan with profit, admin fees, missing fields or an installment that does not
 * split the principal is rejected rather than guessed.
 */
object SabInstallmentPlanReader {
    data class PlanConfirmation(
        val principalHalalas: Long,
        val months: Int,
        val monthlyHalalas: Long,
        val cardLast4: String?,
    )

    private const val NUMBER = "([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)(?![0-9.]|,[0-9])"
    private const val CURRENCY = "(?:SAR|ر\\.?س\\.?|س\\.?ر\\.?|ريال(?:\\s+سعودي)?)"
    private val confirmation = Regex("تم تحويل العملية الشرائية")
    private val monthsRegex = Regex("(?:الى|إلى)\\s*([0-9]{1,3})\\s*قسط")
    private val monthlyRegex = Regex("مبلغ القسط\\s*(.*)")
    private val profitRegex = Regex("الربح الشهر[يى]\\s*([0-9]*(?:\\.[0-9]+)?)\\s*%")
    private val feesRegex = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*رسوم\\s*[اإ]دار[يى][ةه]")
    private val cardRegex = Regex(
        "\\(\\*{0,6}([0-9]{4})\\*{0,6}\\)|(?:VISA|بطاقة)\\s*[:;]\\s*\\*{0,6}([0-9]{4})\\*{0,6}|الائتماني[ةه]\\s*[:;]?\\s*([0-9]{4})(?![0-9])",
        RegexOption.IGNORE_CASE,
    )

    fun isConfirmation(body: String): Boolean = confirmation.containsMatchIn(normalize(body))

    fun read(body: String): PlanConfirmation? {
        val text = normalize(body)
        if (!confirmation.containsMatchIn(text)) return null
        val principal = amount(text.substringBefore("مبلغ القسط")) ?: return null
        val months = monthsRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val monthly = monthlyRegex.find(text)?.groupValues?.get(1)?.let(::amount) ?: return null
        val profit = profitRegex.find(text)?.groupValues?.get(1)?.toBigDecimalOrNull() ?: return null
        val fees = feesRegex.find(text)?.groupValues?.get(1)?.toBigDecimalOrNull() ?: return null
        if (months !in 1..120 || principal < months || monthly <= 0) return null
        if (profit.signum() != 0 || fees.signum() != 0) return null
        if (kotlin.math.abs(principal - monthly * months) > months) return null
        val card = cardRegex.find(text)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
        return PlanConfirmation(principal, months, monthly, card)
    }

    private fun amount(text: String): Long? = runCatching {
        val before = Regex("$CURRENCY\\s*$NUMBER", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        val after = Regex("$NUMBER\\s*$CURRENCY", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        (before ?: after)?.let { BigDecimal(it.replace(",", "")).movePointRight(2).longValueExact() }
    }.getOrNull()

    private fun normalize(body: String): String =
        body.map { c ->
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                else -> c
            }
        }.joinToString("")
            .replace("٫", ".")
            .replace("٬", ",")
            .replace(Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
