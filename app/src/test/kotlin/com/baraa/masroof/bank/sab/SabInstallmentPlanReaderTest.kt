package com.baraa.masroof.bank.sab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Synthetic messages only. */
class SabInstallmentPlanReaderTest {
    private val zeroProfit =
        "تم تحويل العملية الشرائية بمبلغ س.ر 2,400.00 على البطاقة الائتمانية9876 الى12 قسط شهرى, مبلغ القسط س.ر 200.00, " +
            "الربح الشهرى .00%و0 رسوم ادارية. التاريخ والوقت: 05-02-26 13:30:00"

    @Test
    fun zeroProfitConfirmation_becomesPlan() {
        val plan = SabInstallmentPlanReader.read(zeroProfit)!!

        assertEquals(240_000L, plan.principalHalalas)
        assertEquals(12, plan.months)
        assertEquals(20_000L, plan.monthlyHalalas)
        assertEquals("9876", plan.cardLast4)
    }

    @Test
    fun arabicIndicDigits_areRead() {
        val text = zeroProfit.replace("2,400.00", "٢٬٤٠٠٫٠٠").replace("200.00", "٢٠٠٫٠٠")

        assertEquals(240_000L, SabInstallmentPlanReader.read(text)!!.principalHalalas)
    }

    @Test
    fun promotion_isNotAPlan() {
        assertNull(SabInstallmentPlanReader.read("حوّل عمليتك إلى أقساط بدون أرباح للإستفادة من العرض"))
    }

    @Test
    fun profitOrFees_areRejectedNotGuessed() {
        assertNull(SabInstallmentPlanReader.read(zeroProfit.replace(".00%", "1.50%")))
        assertNull(SabInstallmentPlanReader.read(zeroProfit.replace("و0 رسوم", "و25 رسوم")))
    }

    @Test
    fun installmentThatDoesNotSplitPrincipal_isRejected() {
        assertNull(SabInstallmentPlanReader.read(zeroProfit.replace("مبلغ القسط س.ر 200.00", "مبلغ القسط س.ر 250.00")))
    }
}
