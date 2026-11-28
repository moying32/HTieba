package dev.hydrogen.tieba.ui

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import dev.hydrogen.tieba.data.VisualStyle
import dev.hydrogen.tieba.ui.theme.LocalVisualStyle

@Composable
fun Modifier.themeCanvas(): Modifier {
    val colors = MaterialTheme.colorScheme
    val canvas = background(colors.background)
    if (LocalVisualStyle.current != VisualStyle.Pixel) return canvas
    val ink = colors.onSurface.copy(alpha = .065f)
    return canvas.drawWithCache {
        val cell = 2.dp.roundToPx().coerceAtLeast(1).toFloat()
        val spacing = cell * 12
        val tiles = Path().apply {
            var y = spacing / 2
            var row = 0
            while (y < size.height) {
                var x = spacing / 2 + if (row % 2 == 0) 0f else spacing / 2
                while (x < size.width) {
                    addRect(Rect(x, y, x + cell, y + cell))
                    x += spacing
                }
                row++
                y += spacing
            }
        }
        onDrawBehind { drawPath(tiles, ink) }
    }
}
