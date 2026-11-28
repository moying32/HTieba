package dev.hydrogen.tieba.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal val PixelStepShape = PixelCorners(CornerSize(2.dp))
internal val PixelPanelShape = PixelCorners(CornerSize(4.dp))

/** Stepped corners on the same grid, with wider corners for larger panels. */
internal class PixelCorners(
    topStart: CornerSize,
    topEnd: CornerSize = topStart,
    bottomEnd: CornerSize = topStart,
    bottomStart: CornerSize = topStart,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize) =
        PixelCorners(topStart, topEnd, bottomEnd, bottomStart)

    override fun createOutline(size: Size, topStart: Float, topEnd: Float, bottomEnd: Float, bottomStart: Float,
        layoutDirection: LayoutDirection): Outline {
        val w = size.width
        val h = size.height
        val ltr = layoutDirection == LayoutDirection.Ltr
        fun cell(value: Float) = value.roundToInt().toFloat().coerceAtMost(size.minDimension / 8f)
        val tl = cell(if (ltr) topStart else topEnd)
        val tr = cell(if (ltr) topEnd else topStart)
        val br = cell(if (ltr) bottomEnd else bottomStart)
        val bl = cell(if (ltr) bottomStart else bottomEnd)
        return Outline.Generic(Path().apply {
            moveTo(tl * 3, 0f)
            lineTo(w - tr * 3, 0f)
            lineTo(w - tr * 3, tr)
            lineTo(w - tr, tr)
            lineTo(w - tr, tr * 3)
            lineTo(w, tr * 3)
            lineTo(w, h - br * 3)
            lineTo(w - br, h - br * 3)
            lineTo(w - br, h - br)
            lineTo(w - br * 3, h - br)
            lineTo(w - br * 3, h)
            lineTo(bl * 3, h)
            lineTo(bl * 3, h - bl)
            lineTo(bl, h - bl)
            lineTo(bl, h - bl * 3)
            lineTo(0f, h - bl * 3)
            lineTo(0f, tl * 3)
            lineTo(tl, tl * 3)
            lineTo(tl, tl)
            lineTo(tl * 3, tl)
            close()
        })
    }
}
