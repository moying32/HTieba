package dev.hydrogen.tieba.ui

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.hydrogen.tieba.data.VisualStyle
import dev.hydrogen.tieba.ui.theme.LocalVisualStyle

/** Cached static atmosphere: no timer, bitmap decoding, blur, or per-frame work. */
@Composable
fun Modifier.themeCanvas(): Modifier {
    val background = MaterialTheme.colorScheme.background
    if (LocalVisualStyle.current != VisualStyle.Margin) return background(background)
    val dark = background.luminance() < .5f
    return background(background).drawWithCache {
        val radius = size.width * 1.15f
        val upper = Brush.radialGradient(
            listOf(Color(0xFF497FDD).copy(alpha = if (dark) .13f else .085f), Color.Transparent),
            center = Offset(size.width, size.height * .06f), radius = radius)
        val lower = Brush.radialGradient(
            listOf(Color(0xFF00AEEC).copy(alpha = if (dark) .055f else .035f), Color.Transparent),
            center = Offset(-size.width * .25f, size.height * .72f), radius = radius)
        onDrawBehind { drawRect(upper); drawRect(lower) }
    }
}
