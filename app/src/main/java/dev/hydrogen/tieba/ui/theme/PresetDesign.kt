package dev.hydrogen.tieba.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.hydrogen.tieba.data.GlassPalette
import dev.hydrogen.tieba.data.ThemePalette
import dev.hydrogen.tieba.data.VisualStyle

internal enum class SurfaceFinish { Liquid, Solid }

internal data class PresetDesign(
    val finish: SurfaceFinish,
    val control: Int,
    val field: Int,
    val card: Int,
    val panel: Int,
    val light: ThemePalette? = null,
    val dark: ThemePalette? = null,
    val stepped: Boolean = false,
) {
    val shapes = if (stepped) Shapes(PixelStepShape, PixelStepShape, PixelPanelShape, PixelPanelShape, PixelPanelShape) else Shapes(
        extraSmall = RoundedCornerShape((field / 2).dp),
        small = RoundedCornerShape(field.dp),
        medium = RoundedCornerShape(card.dp),
        large = RoundedCornerShape(panel.dp),
        extraLarge = RoundedCornerShape(panel.dp),
    )
}

private val liquid = PresetDesign(SurfaceFinish.Liquid, 24, 18, 24, 36,
    light = ThemePalette("#EDF5F6", "#FAFEFF", "#142B31", "#48616A", "#C4DADF"),
    dark = ThemePalette("#101D24", "#1A2B33", "#EBF7F8", "#A8C2C9", "#354D56"))
private val pixel = PresetDesign(SurfaceFinish.Solid, 10, 12, 16, 19,
    light = ThemePalette("#F0EFE8", "#FBFAF4", "#282642", "#595773", "#D4D0C5"),
    dark = ThemePalette("#1D1C29", "#282638", "#F0EDF8", "#BBB5D0", "#454258"), stepped = true)
private val claude = PresetDesign(SurfaceFinish.Solid, 12, 18, 18, 24,
    light = ThemePalette("#F5F1E8", "#FEFBF3", "#322A25", "#645850", "#DAD0C1"),
    dark = ThemePalette("#22201D", "#2D2824", "#F2E9DE", "#C1AEA0", "#534840"))
private val minimal = PresetDesign(SurfaceFinish.Solid, 12, 12, 12, 14,
    light = ThemePalette("#F7F7F6", "#FFFFFF", "#202326", "#50565C", "#D5D8DB"),
    dark = ThemePalette("#141516", "#1C1E20", "#F0F1F2", "#B3BAC0", "#3F4449"))

internal fun PresetDesign.actionShape(style: VisualStyle): Shape =
    if (style == VisualStyle.Pixel) PixelStepShape else RoundedCornerShape(control.dp)

internal val VisualStyle.design: PresetDesign
    get() = when (this) {
        VisualStyle.Liquid -> liquid
        VisualStyle.Pixel -> pixel
        VisualStyle.Claude -> claude
        VisualStyle.Minimal -> minimal
    }

internal val currentDesign: PresetDesign
    @Composable get() = LocalVisualStyle.current.design

internal fun GlassPalette.colors(dark: Boolean): ThemePalette = when (this) {
    GlassPalette.Mist -> requireNotNull(if (dark) liquid.dark else liquid.light)
    GlassPalette.Pine -> if (dark) ThemePalette("#151918", "#202622", "#E3E9E0", "#AAB7AB", "#3B4A3F")
        else ThemePalette("#F7F8F2", "#FCFDF9", "#202923", "#536058", "#D7DFD3")
    GlassPalette.Ink -> if (dark) ThemePalette("#111317", "#1A1D23", "#EBEEF5", "#ABB5C3", "#3A424F")
        else ThemePalette("#F5F4F0", "#FFFEFB", "#242931", "#565E6B", "#D9DDE4")
}
