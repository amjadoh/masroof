package com.baraa.masroof.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.baraa.masroof.ui.theme.MasroofGuidanceStyle
import com.baraa.masroof.ui.theme.MasroofShapes
import com.baraa.masroof.ui.theme.MasroofSpacing
import kotlin.math.roundToInt

/** Window bounds of the parts a first-run tour can point at, keyed by id. */
class MasroofCoachMarkTargets {
    internal val bounds = mutableStateMapOf<String, Rect>()
    operator fun contains(id: String) = id in bounds
}

val LocalMasroofCoachMarkTargets = staticCompositionLocalOf<MasroofCoachMarkTargets?> { null }

/** Marks this element as a tour stop; a no-op where no tour is provided. */
fun Modifier.coachMarkTarget(id: String): Modifier = composed {
    val targets = LocalMasroofCoachMarkTargets.current ?: return@composed Modifier
    DisposableEffect(targets, id) { onDispose { targets.bounds.remove(id) } }
    Modifier.onGloballyPositioned { targets.bounds[id] = it.boundsInWindow() }
}

data class MasroofCoachMark(val id: String, val title: String, val body: String)

/**
 * Step-by-step tour: dims the screen, lights up one target and explains it in a bubble beside it.
 * Touches outside the bubble are blocked until the tour is finished or skipped.
 */
@Composable
fun MasroofCoachMarkTour(
    targets: MasroofCoachMarkTargets,
    steps: List<MasroofCoachMark>,
    nextLabel: String,
    doneLabel: String,
    skipLabel: String,
    progressLabel: (current: Int, total: Int) -> String,
    onFinish: () -> Unit,
) {
    val available = steps.filter { it.id in targets }
    if (available.isEmpty()) return
    var index by rememberSaveable { mutableStateOf(0) }
    val position = index.coerceAtMost(available.lastIndex)
    val step = available[position]
    val last = position == available.lastIndex
    var origin by remember { mutableStateOf(Offset.Zero) }
    val target = targets.bounds[step.id]?.translate(-origin) ?: return
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = MasroofGuidanceStyle.tourScrimAlpha)
    Box(Modifier.fillMaxSize().testTag("coach-mark-tour")
        .onGloballyPositioned { origin = it.positionInWindow() }
        .pointerInput(Unit) { detectTapGestures { } }) {
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            val pad = MasroofGuidanceStyle.tourSpotlightPadding.toPx()
            val radius = MasroofGuidanceStyle.tourSpotlightRadius.toPx()
            drawRect(scrim)
            drawRoundRect(Color.Transparent, topLeft = Offset(target.left - pad, target.top - pad),
                size = target.inflate(pad).size, cornerRadius = CornerRadius(radius), blendMode = BlendMode.Clear)
        }
        Layout(content = {
            Surface(Modifier.widthIn(max = MasroofGuidanceStyle.maxWidth).testTag("coach-mark-bubble")
                .semantics { paneTitle = step.title; liveRegion = LiveRegionMode.Polite },
                shape = MasroofGuidanceStyle.shape, color = MasroofGuidanceStyle.backgroundColor,
                border = BorderStroke(MasroofSpacing.borderWidth, MasroofGuidanceStyle.borderColor),
                contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = MasroofGuidanceStyle.elevation) {
                Column(Modifier.padding(MasroofGuidanceStyle.contentPadding),
                    verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
                    Text(progressLabel(position + 1, available.size), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text(step.title, style = MaterialTheme.typography.titleSmall)
                    Text(step.body, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        if (!last) TextButton(onFinish, Modifier.testTag("coach-mark-skip")) { Text(skipLabel) }
                        else Box(Modifier)
                        Button({ if (last) onFinish() else index = position + 1 },
                            Modifier.testTag("coach-mark-next"), shape = MasroofShapes.small) {
                            Text(if (last) doneLabel else nextLabel)
                        }
                    }
                }
            }
        }) { measurables, constraints ->
            val bubble = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val gap = (MasroofGuidanceStyle.anchorGap + MasroofGuidanceStyle.tourSpotlightPadding).roundToPx()
            val anchor = IntRect(target.left.roundToInt(), target.top.roundToInt(), target.right.roundToInt(), target.bottom.roundToInt())
            val at = MasroofGuidancePositionProvider(gap, MasroofGuidanceStyle.windowMargin.roundToPx())
                .calculatePosition(anchor, IntSize(constraints.maxWidth, constraints.maxHeight), layoutDirection,
                    IntSize(bubble.width, bubble.height))
            layout(constraints.maxWidth, constraints.maxHeight) { bubble.place(at) }
        }
    }
}
