package com.baraa.masroof.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide "hide amounts" switch behind the eye button. Display formatting reads it, so every
 * composable showing money recomposes when it changes. It never alters stored or computed values.
 */
object MasroofAmountPrivacy {
    const val MASK: String = "••••"

    var hidden: Boolean by mutableStateOf(false)
}
