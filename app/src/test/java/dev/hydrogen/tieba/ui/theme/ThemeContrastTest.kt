package dev.hydrogen.tieba.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.hydrogen.tieba.data.GlassPalette
import dev.hydrogen.tieba.data.VisualStyle
import dev.hydrogen.tieba.data.ThemePackCodec
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test fun customAccentsKeepTextReadableInBothModes() {
        val seeds = listOf(0x000000, 0xFFFFFF, 0xFFFF00, 0x00FF00, 0x0000FF, 0xFF0000, 0x326A54, 0x7954AB, 0x808080, 0x00AEEC, 0x12ABCD,0xC16849)
        val presets = GlassPalette.entries.map { VisualStyle.Liquid to it } +
            VisualStyle.entries.filterNot { it == VisualStyle.Liquid }.map { it to GlassPalette.Mist }
        for ((style, palette) in presets) for (seed in seeds) for (dark in listOf(false, true)) {
            val scheme = customColorScheme(Color(0xFF000000 or seed.toLong()), dark, style, palette)
            val pairs = mutableMapOf(
                "primary/background" to (scheme.primary to scheme.background),
                "primary/surface" to (scheme.primary to scheme.surface),
                "onPrimary" to (scheme.onPrimary to scheme.primary),
                "onPrimaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                "onSecondary" to (scheme.onSecondary to scheme.secondary),
                "onBackground" to (scheme.onBackground to scheme.background),
                "onSurface" to (scheme.onSurface to scheme.surface),
                "onSurfaceVariant" to (scheme.onSurfaceVariant to scheme.surfaceVariant),
                "onTertiary" to (scheme.onTertiary to scheme.tertiary),
            )
            for ((role, pair) in pairs) {
                val a = pair.first.luminance(); val b = pair.second.luminance()
                val ratio = (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
                assertTrue("$role style=$style palette=$palette seed=${seed.toString(16)} dark=$dark contrast=$ratio", ratio >= 4.499f)
            }
        }
    }
    @Test fun importedPaletteKeepsSharedTextReadableOnBothSurfaces() {
        for(dark in listOf(false,true)) for(seed in listOf(0x000000,0xFFFFFF,0xFFFF00,0x346B91)) {
            val scheme=packColorScheme(ThemePackCodec.example,Color(0xFF000000 or seed.toLong()),dark)
            for(ink in listOf(scheme.primary,scheme.onSurface,scheme.onSurfaceVariant,scheme.error)) for(bg in listOf(scheme.surface,scheme.background)) {
                val a=ink.luminance(); val b=bg.luminance()
                assertTrue((maxOf(a,b)+.05f)/(minOf(a,b)+.05f)>=4.499f)
            }
        }
    }
}
