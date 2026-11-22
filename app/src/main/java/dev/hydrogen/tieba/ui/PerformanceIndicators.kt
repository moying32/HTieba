package dev.hydrogen.tieba.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Minimal mode keeps a visible loading state without an infinite animation. */
@Composable
internal fun AppCircularProgressIndicator(modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary, strokeWidth: Dp = 4.dp) {
    if (!LocalMinimalMode.current) androidx.compose.material3.CircularProgressIndicator(modifier, color, strokeWidth)
    else Canvas(modifier.size(40.dp).semantics { contentDescription = "正在加载" }) {
        drawArc(color, -90f, 270f, false, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
internal fun AppLinearProgressIndicator(modifier: Modifier = Modifier) {
    if (!LocalMinimalMode.current) androidx.compose.material3.LinearProgressIndicator(modifier)
    else {
        val color = MaterialTheme.colorScheme.primary
        Canvas(modifier.fillMaxWidth().height(4.dp).semantics { contentDescription = "正在加载" }) {
            drawLine(color.copy(alpha = .2f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height)
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width * .35f, size.height / 2), size.height)
        }
    }
}

@Composable
internal fun AppLinearProgressIndicator(progress: () -> Float, modifier: Modifier = Modifier) {
    // Determinate progress represents actual work, so keep it in both modes.
    androidx.compose.material3.LinearProgressIndicator(progress = progress, modifier = modifier)
}
