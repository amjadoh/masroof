package com.baraa.masroof.instrumentation

import android.graphics.Bitmap
import android.graphics.Color
import com.baraa.masroof.presentation.theme.MasroofReference
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Integrate subpixel coverage instead of requiring an opaque pixel in one rounded row. */
internal object AverageLinePixels {
    fun matches(bitmap: Bitmap, expectedY: Float, foreground: Int, background: Int): Boolean {
        val density = bitmap.height / MasroofReference.chartHeight.value
        val stroke = MasroofReference.averageLineStroke.value * density
        val dash = MasroofReference.averageDash.value * density
        val left = MasroofReference.chartLeft.value * density
        val right = bitmap.width - MasroofReference.chartRight.value * density
        val delta = floatArrayOf((Color.red(foreground) - Color.red(background)).toFloat(),
            (Color.green(foreground) - Color.green(background)).toFloat(),
            (Color.blue(foreground) - Color.blue(background)).toFloat())
        val norm = delta.sumOf { (it * it).toDouble() }.toFloat()
        val from = floor(expectedY - stroke / 2 - 1).toInt().coerceAtLeast(0)
        val to = ceil(expectedY + stroke / 2 + 1).toInt().coerceAtMost(bitmap.height - 1)
        fun column(x: Int): Pair<Float, Float> {
            var coverage = 0f
            var moment = 0f
            for (y in from..to) {
                val pixel = bitmap.getPixel(x, y)
                val sample = floatArrayOf((Color.red(pixel) - Color.red(background)).toFloat(),
                    (Color.green(pixel) - Color.green(background)).toFloat(),
                    (Color.blue(pixel) - Color.blue(background)).toFloat())
                val alpha = (sample.indices.sumOf { (sample[it] * delta[it]).toDouble() } / norm).toFloat()
                val residual = sample.indices.maxOf { abs(sample[it] - alpha * delta[it]) }
                if (alpha in 0.02f..1.02f && residual <= 5f) {
                    coverage += alpha
                    moment += alpha * (y + 0.5f)
                }
            }
            return coverage to if (coverage > 0) moment / coverage else Float.NaN
        }
        val count = floor((right - left) / (2 * dash)).toInt()
        if (count < 3) return false
        var on = 0
        var off = 0
        repeat(count) { index ->
            val x = left + index * 2 * dash
            val (coverage, center) = column((x + dash / 2).toInt())
            if (coverage in stroke * 0.85f..stroke * 1.15f && abs(center - expectedY) <= 0.55f) on++
            if (column((x + dash * 1.5f).toInt()).first < stroke * 0.15f) off++
        }
        // Series/selection intersections may occlude a few samples, not a missing/solid/shifted line.
        return on >= ceil(count * 0.7).toInt() && off >= ceil(count * 0.7).toInt()
    }
}
