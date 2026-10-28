package com.baraa.masroof.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified

private const val FIT_STEP = 0.9f
private const val FIT_MIN_SCALE = 0.5f

/**
 * One-line text that shrinks its font until it fits, so money is never cut to "…" or split mid-number.
 * shortcut: hand-rolled because Compose BOM 2024.06 lacks TextAutoSize; swap for `autoSize` after a BOM upgrade.
 */
@Composable
fun MasroofFitText(text: AnnotatedString, style: TextStyle, modifier: Modifier = Modifier,
    color: Color = Color.Unspecified, textAlign: TextAlign? = null) {
    var scale by remember(text, style) { mutableFloatStateOf(1f) }
    var ready by remember(text, style) { mutableStateOf(false) }
    val fitted = if (style.fontSize.isSpecified) style.copy(fontSize = style.fontSize * scale) else style
    Text(text, modifier.drawWithContent { if (ready) drawContent() }, color = color, style = fitted,
        textAlign = textAlign, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if ((result.didOverflowWidth || result.isLineEllipsized(0)) && style.fontSize.isSpecified && scale * FIT_STEP >= FIT_MIN_SCALE) {
                scale *= FIT_STEP
            } else {
                ready = true
            }
        })
}

@Composable
fun MasroofFitText(text: String, style: TextStyle, modifier: Modifier = Modifier,
    color: Color = Color.Unspecified, textAlign: TextAlign? = null) =
    MasroofFitText(AnnotatedString(text), style, modifier, color, textAlign)
