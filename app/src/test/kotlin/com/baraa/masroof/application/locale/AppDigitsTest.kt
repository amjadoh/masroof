package com.baraa.masroof.application.locale

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDigitsTest {
    @Test
    fun display_normalizesBothDigitSets_withoutChangingWordsOrPunctuation() {
        assertEquals("حساب 0123456789 · بطاقة 0123456789", AppDigits.display("حساب ٠١٢٣٤٥٦٧٨٩ · بطاقة ۰۱۲۳۴۵۶۷۸۹"))
        assertEquals("-1,234.50 ر.س", AppDigits.display("-١,٢٣٤.٥٠ ر.س"))
        assertEquals("2026-10-11 · ABC123", AppDigits.display("2026-10-11 · ABC123"))
        assertEquals("", AppDigits.display(""))
    }

    @Test
    fun display_isIdempotent_andPreservesTextLength() {
        val original = "بطاقة ٧٢٧١ / ۷۲۷۱"
        val displayed = AppDigits.display(original)
        assertEquals(original.length, displayed.length)
        assertEquals(displayed, AppDigits.display(displayed))
        assertFalse(displayed.any { it in '\u0660'..'\u0669' || it in '\u06F0'..'\u06F9' })
    }

    @Test
    fun arabicDisplayLocale_usesAsciiDigitsForNumbersDatesAndResourceStyleFormats() {
        val locale = AppLocale.displayLocale("ar-SA")
        assertEquals("ar", locale.language)
        assertEquals("latn", locale.getUnicodeLocaleType("nu"))
        assertEquals("123", String.format(locale, "%d", 123))
        assertEquals("1,234", NumberFormat.getIntegerInstance(locale).format(1234))
        val date = DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(LocalDate.of(2026, 10, 11))
        assertTrue(date.contains("11") && date.contains("2026"))
        assertFalse(date.any { it in '\u0660'..'\u0669' || it in '\u06F0'..'\u06F9' })
    }
}
