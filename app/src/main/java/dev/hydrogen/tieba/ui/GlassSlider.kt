package dev.hydrogen.tieba.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Native Slider owns gestures, keyboard steps and progress semantics; only its material changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: String,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val dragged by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val motion = LocalMotionEnabled.current
    val rich = LocalRichEffects.current
    val layer = rememberGraphicsLayer()
    val scene = remember(layer) { GlassScene(layer) }
    val colors = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    // Record just this track. Sampling the whole settings scene would include the thumb itself.
    CompositionLocalProvider(LocalGlassScene provides scene) {
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps,
            onValueChangeFinished = onValueChangeFinished, interactionSource = interaction,
            modifier = modifier.fillMaxWidth().heightIn(min = LocalTouchTarget.current).semantics {
                contentDescription = label
                stateDescription = "${(value * 100).roundToInt()}%"
            },
            thumb = {
                GlassPanel(Modifier.size(width = 44.dp, height = 32.dp).graphicsLayer {
                    val held = motion && (dragged || pressed)
                    scaleX = if (held) 1.06f else 1f
                    scaleY = if (held) 1.06f else 1f
                }.then(if (focused) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier),
                    backdrop = true, liquid = true, tintStrength = .85f) {
                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Canvas(Modifier.size(width = 14.dp, height = 10.dp)) {
                            for (mark in 1..3) {
                                val x = size.width * mark / 4f
                                drawLine(colors.onSurface, Offset(x, size.height * .23f),
                                    Offset(x, size.height * .77f), 1.dp.toPx(), StrokeCap.Round)
                            }
                        }
                    }
                }
            },
            track = {
                Canvas(Modifier.fillMaxWidth().height(48.dp)
                    .then(if (rich) Modifier.captureGlassScene(scene) else Modifier)) {
                    val start = Offset(if (rtl) size.width else 0f, size.height / 2f)
                    val end = Offset(if (rtl) 0f else size.width, size.height / 2f)
                    val railHeight = if (rich) 12.dp.toPx() else 6.dp.toPx()
                    val top = (size.height - railHeight) / 2f
                    val corners = CornerRadius(railHeight / 2f)
                    drawRoundRect(colors.surfaceContainerHighest, Offset(0f, top), Size(size.width, railHeight), corners)
                    if (fraction > 0f) {
                        val range = (size.width - railHeight).coerceAtLeast(0f)
                        val from = start.x.coerceIn(railHeight / 2f, size.width - railHeight / 2f)
                        val to = from + if (rtl) -range * fraction else range * fraction
                        drawLine(colors.primary.copy(alpha = if (rich) .70f else 1f),
                            Offset(from, start.y), Offset(to, end.y), railHeight, StrokeCap.Round)
                    }
                    if (rich) drawRoundRect(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = .75f), colors.outline.copy(alpha = .24f)),
                            startY = top, endY = top + railHeight),
                        Offset(.5.dp.toPx(), top + .5.dp.toPx()), Size((size.width - 1.dp.toPx()).coerceAtLeast(0f), railHeight - 1.dp.toPx()),
                        corners, style = Stroke(1.dp.toPx()),
                    )
                }
            })
    }
}
