package com.baraa.masroof.presentation.locale

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDigitsVisualTransformationTest {
    @Test
    fun inputDisplaysAsciiDigits_withoutChangingStoredValueOrCursorOffsets() {
        val original = AnnotatedString("حساب ١٢٣ / ۴۵۶")
        val transformed = AppDigitsVisualTransformation.filter(original)
        assertEquals("حساب 123 / 456", transformed.text.text)
        assertEquals("حساب ١٢٣ / ۴۵۶", original.text)
        (0..original.length).forEach { index ->
            assertEquals(index, transformed.offsetMapping.originalToTransformed(index))
            assertEquals(index, transformed.offsetMapping.transformedToOriginal(index))
        }
    }
}
