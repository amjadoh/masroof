package com.baraa.masroof.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val MasroofShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

val MasroofCardShape = RoundedCornerShape(20.dp)
val MasroofPillShape = RoundedCornerShape(14.dp)
val MasroofRowShape = RoundedCornerShape(12.dp)
val MasroofBadgeShape = RoundedCornerShape(999.dp)
val MasroofLogoShape = RoundedCornerShape(10.dp)

/** Explicit shapes for branded badges and chart surfaces; never defined in features. */
object MasroofComponentShapes {
    val iconContainer = RoundedCornerShape(8.dp)
    val tokenSwatch = RoundedCornerShape(4.dp)
    val customizeControl = RoundedCornerShape(12.dp)
    val radius6 = RoundedCornerShape(6.dp)
    val radius12 = RoundedCornerShape(12.dp)
    val radius14 = RoundedCornerShape(14.dp)
    val radius16 = RoundedCornerShape(16.dp)
    val radius999 = RoundedCornerShape(999.dp)
}
