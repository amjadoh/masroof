package com.baraa.masroof.presentation.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.ui.components.MASROOF_FIT_MIN_SCALE
import com.baraa.masroof.ui.components.fitScale
import com.baraa.masroof.ui.components.scaledBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MasroofFitTextTest {
    private val measurer = TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext()),
        Density(1f), LayoutDirection.Ltr)
    private val style = TextStyle(fontSize = 34.sp)
    private val amount = AnnotatedString("1,234,567.89 SAR")
    private val fullWidth = measurer.measure(amount, style, softWrap = false, maxLines = 1).size.width

    private fun widthAt(scale: Float) =
        measurer.measure(amount.scaledBy(scale), style.scaledBy(scale), softWrap = false, maxLines = 1).size.width

    @Test fun fitsAtFullSizeWhenWide() {
        assertEquals(1f, measurer.fitScale(amount, style, fullWidth + 10))
    }

    @Test fun shrinksToFitAndGrowsBackWithWidth() {
        val narrow = measurer.fitScale(amount, style, fullWidth * 2 / 3)!!
        assertTrue(narrow < 1f && widthAt(narrow) <= fullWidth * 2 / 3)
        assertEquals(1f, measurer.fitScale(amount, style, fullWidth))
    }

    @Test fun reportsNoFitBelowMinimumScale() {
        assertNull(measurer.fitScale(amount, style, (fullWidth * MASROOF_FIT_MIN_SCALE / 2).toInt()))
    }
}
