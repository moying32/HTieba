package dev.hydrogen.tieba.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import dev.hydrogen.tieba.data.VisualStyle
import dev.hydrogen.tieba.ui.theme.LocalVisualStyle

@Composable
internal fun PostActionIcon(icon: ImageVector, description: String?) {
    val vector = if (LocalVisualStyle.current == VisualStyle.Pixel) icon
        else postActionOutlines[icon.name.substringAfterLast('.')] ?: icon
    ThemeIcon(vector, description, Modifier.size(18.dp), pixelEmphasis = true)
}

private val postActionOutlines = mapOf(
    "FavoriteBorder" to actionOutline("M12 20.5L4.95 13.65C.55 9.45 4.9 3.3 9.35 5.45C10.5 6 11.3 6.8 12 7.65C12.7 6.8 13.5 6 14.65 5.45C19.1 3.3 23.45 9.45 19.05 13.65Z"),
    "ChatBubbleOutline" to actionOutline("M5 4.75H19A1.25 1.25 0 0 1 20.25 6V16A1.25 1.25 0 0 1 19 17.25H9L3.75 21V6A1.25 1.25 0 0 1 5 4.75Z"),
    "StarOutline" to actionOutline("M12 3.3L14.7 8.85L20.85 9.7L16.4 14.05L17.45 20.15L12 17.25L6.55 20.15L7.6 14.05L3.15 9.7L9.3 8.85Z"),
    "BookmarkBorder" to actionOutline("M7 4.25H17A1 1 0 0 1 18 5.25V20L12 16.15L6 20V5.25A1 1 0 0 1 7 4.25Z"),
)

private fun actionOutline(path: String): ImageVector =
    ImageVector.Builder(defaultWidth = 18.dp, defaultHeight = 18.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.65f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        .build()
