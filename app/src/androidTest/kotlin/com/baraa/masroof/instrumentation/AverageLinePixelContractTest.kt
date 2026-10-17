package com.baraa.masroof.instrumentation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import com.baraa.masroof.ui.theme.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

/** Calibrate the visual assertion across subpixel phases; prove that regressions are rejected. */
class AverageLinePixelContractTest {
    @Test
    fun detectsRealDashedStroke_andRejectsMissingShiftedSolidOrWrongStroke() {
        for (density in listOf(1f, 1.5f, 2.625f, 3f)) for (dark in listOf(false, true))
            for (phase in listOf(0.05f, 0.25f, 0.5f, 0.75f, 0.95f)) {
                val fg = (if (dark) MasroofDarkColors.OnSurfaceVariant else MasroofLightColors.OnSurfaceVariant).toArgb()
                val bg = (if (dark) MasroofDarkColors.Surface else MasroofLightColors.Surface).toArgb()
                val y = 140 * density + phase
                for (kind in listOf("correct", "missing", "shifted", "solid", "thick", "wrong-color")) {
                    val bitmap = Bitmap.createBitmap((254 * density).roundToInt(), (MasroofReference.chartHeight.value * density).roundToInt(), Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(bg)
                    if (kind != "missing") {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = if (kind == "wrong-color") MasroofPalette.Primary.toArgb() else fg
                            strokeWidth = MasroofReference.averageLineStroke.value * density * if (kind == "thick") 2 else 1
                            if (kind != "solid") pathEffect = DashPathEffect(floatArrayOf(MasroofReference.averageDash.value * density,
                                MasroofReference.averageDash.value * density), 0f)
                        }
                        val lineY = y + if (kind == "shifted") 8 * density else 0f
                        Canvas(bitmap).drawLine(MasroofReference.chartLeft.value * density, lineY,
                            bitmap.width - MasroofReference.chartRight.value * density, lineY, paint)
                    }
                    try {
                        assertEquals("density=$density dark=$dark phase=$phase kind=$kind", kind == "correct",
                            AverageLinePixels.matches(bitmap, y, fg, bg))
                    } finally { bitmap.recycle() }
                }
            }
    }
}
