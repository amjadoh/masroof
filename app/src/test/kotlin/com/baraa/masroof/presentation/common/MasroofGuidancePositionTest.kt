package com.baraa.masroof.presentation.common

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class MasroofGuidancePositionTest {
    private val provider = MasroofGuidancePositionProvider(gap = 8, margin = 16)

    @Test
    fun explanationAnchorsToItsSubject_inBothDirections() {
        val anchor = IntRect(400, 100, 448, 148)
        val window = IntSize(1000, 800)
        val content = IntSize(300, 120)
        assertEquals(IntOffset(400, 156), provider.calculatePosition(anchor, window, LayoutDirection.Ltr, content))
        assertEquals(IntOffset(148, 156), provider.calculatePosition(anchor, window, LayoutDirection.Rtl, content))
    }

    @Test
    fun explanationFlipsAbove_andStaysInsideNarrowWindow() {
        assertEquals(IntOffset(16, 492), provider.calculatePosition(IntRect(10, 700, 58, 748),
            IntSize(400, 800), LayoutDirection.Rtl, IntSize(300, 200)))
        assertEquals(IntOffset(0, 0), provider.calculatePosition(IntRect(0, 20, 48, 68),
            IntSize(240, 100), LayoutDirection.Ltr, IntSize(240, 100)))
    }
}
