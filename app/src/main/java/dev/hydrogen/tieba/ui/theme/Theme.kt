package dev.hydrogen.tieba.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.hydrogen.tieba.data.ThemeMode
import dev.hydrogen.tieba.data.GlassPalette
import dev.hydrogen.tieba.data.VisualStyle
import dev.hydrogen.tieba.data.AppFont
import dev.hydrogen.tieba.data.ThemePack
import dev.hydrogen.tieba.data.ThemePalette
import dev.hydrogen.tieba.R

val LocalVisualStyle = staticCompositionLocalOf { VisualStyle.Liquid }
val LocalThemePack = staticCompositionLocalOf<ThemePack?> { null }
val LocalAppFont = staticCompositionLocalOf { AppFont.Theme }

@OptIn(ExperimentalTextApi::class)
@Composable
fun appFontFamily(font: AppFont): FontFamily = remember(font) {
    when(font) {
        AppFont.NotoSans -> FontFamily(*listOf(400 to FontWeight.Normal, 500 to FontWeight.Medium, 600 to FontWeight.SemiBold, 700 to FontWeight.Bold).map { (axis, weight) ->
            Font(R.font.noto_sans_sc, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(axis)))
        }.toTypedArray())
        AppFont.WenKai -> FontFamily(Font(R.font.wenkai_lite))
        AppFont.XiaoWei -> FontFamily(Font(R.font.zcool_xiaowei))
        else -> FontFamily.Default
    }
}

private val Light = lightColorScheme(
    primary = Color(0xFF326A54), onPrimary = Color.White, primaryContainer = Color(0xFFE4EDE3),
    onPrimaryContainer = Color(0xFF224B39), background = Color(0xFFF7F8F4), onBackground = Color(0xFF202923),
    secondary = Color(0xFF536650), onSecondary = Color.White, secondaryContainer = Color(0xFFE2EBDD), onSecondaryContainer = Color(0xFF273D2D),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF202923), surfaceVariant = Color(0xFFEDF0E9),
    onSurfaceVariant = Color(0xFF69736B), outline = Color(0xFF8B958C), outlineVariant = Color(0xFFE1E5DC),
    surfaceContainer = Color(0xFFEEF1E9), surfaceContainerLow = Color(0xFFF2F4EE),
    surfaceContainerHigh = Color(0xFFE8ECE3), surfaceContainerHighest = Color(0xFFE2E7DD),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA6D3B6), onPrimary = Color(0xFF143B29), primaryContainer = Color(0xFF284D39),
    onPrimaryContainer = Color(0xFFC6EBD2), background = Color(0xFF131A16), onBackground = Color(0xFFE3E9E0),
    secondary = Color(0xFFBBCDB6), onSecondary = Color(0xFF263C2A), secondaryContainer = Color(0xFF354D39), onSecondaryContainer = Color(0xFFD6E9D1),
    surface = Color(0xFF1C241E), onSurface = Color(0xFFE3E9E0), surfaceVariant = Color(0xFF29322B),
    onSurfaceVariant = Color(0xFFA6B2A7), outline = Color(0xFF7A887C), outlineVariant = Color(0xFF343F35),
    surfaceContainer = Color(0xFF222D24), surfaceContainerLow = Color(0xFF19211B),
    surfaceContainerHigh = Color(0xFF29352C), surfaceContainerHighest = Color(0xFF303E33),
)

@Composable
fun HydrogenTheme(mode: ThemeMode, accent: String = "326A54", style: VisualStyle = VisualStyle.Liquid,
    custom: ThemePack? = null, font: AppFont = AppFont.Theme, glassPalette: GlassPalette = GlassPalette.Mist, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.Dark || (mode == ThemeMode.System && isSystemInDarkTheme())
    val seed = runCatching { Color(0xFF000000 or accent.toLong(16)) }.getOrDefault(Color(0xFF326A54))
    val effectiveStyle = if (custom == null) style else VisualStyle.Liquid
    val claude = font == AppFont.Theme && custom == null && effectiveStyle == VisualStyle.Claude
    val editorial = custom == null && effectiveStyle == VisualStyle.Claude
    val family = if (claude) anthropicFamily(R.font.anthropic_sans) else
        appFontFamily(if (font == AppFont.Theme) custom?.font ?: AppFont.System else font)
    val headingFont = if (claude) anthropicFamily(R.font.anthropic_serif) else family
    val labelFont = if (font == AppFont.Theme && custom == null && effectiveStyle == VisualStyle.Pixel) FontFamily.Monospace else family
    val headingWeight = if (effectiveStyle == VisualStyle.Pixel) FontWeight.Bold else FontWeight.SemiBold
    val scheme = remember(seed, dark, style, custom, glassPalette) {
        if (custom == null) customColorScheme(seed, dark, style, glassPalette) else packColorScheme(custom, seed, dark)
    }
    CompositionLocalProvider(LocalVisualStyle provides effectiveStyle, LocalThemePack provides custom,LocalAppFont provides font) {
    MaterialTheme(colorScheme = scheme, shapes = effectiveStyle.design.shapes, typography = Typography(
        headlineLarge = TextStyle(fontFamily = headingFont, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
        displayLarge = TextStyle(fontFamily=headingFont,fontSize=57.sp,lineHeight=64.sp),
        displayMedium = TextStyle(fontFamily=headingFont,fontSize=45.sp,lineHeight=52.sp),
        displaySmall = TextStyle(fontFamily=headingFont,fontSize=36.sp,lineHeight=44.sp),
        headlineMedium = TextStyle(fontFamily=headingFont,fontSize=28.sp,lineHeight=36.sp),
        headlineSmall = TextStyle(fontFamily = headingFont, fontSize = 23.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontFamily = headingFont, fontSize = if (editorial) 23.sp else 20.sp, lineHeight = 30.sp, fontWeight = headingWeight),
        titleMedium = TextStyle(fontFamily = headingFont, fontSize = if (editorial) 17.sp else 16.sp, lineHeight = 25.sp, fontWeight = headingWeight),
        titleSmall = TextStyle(fontFamily=family,fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium),
        bodyLarge = TextStyle(fontFamily=family,fontSize = 16.sp, lineHeight = 27.sp),
        bodyMedium = TextStyle(fontFamily=family,fontSize = 14.sp, lineHeight = 22.sp),
        bodySmall = TextStyle(fontFamily=family,fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily=labelFont,fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontFamily=labelFont,fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontFamily=labelFont,fontSize=11.sp,lineHeight=16.sp,fontWeight=FontWeight.Medium),
    )) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
    }
}

// Keep a user's hue while selecting readable tones, including white/black
// seeds. Text roles retain >= 4.5:1 contrast against their paired surfaces.
internal fun readableTone(seed: Color, background: Color, minimum: Float = 4.5f): Color {
    fun ratio(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
    }
    if (ratio(seed, background) >= minimum) return seed
    val target = if (background.luminance() > .179f) Color.Black else Color.White
    var low = 0f; var high = 1f
    repeat(16) {
        val middle = (low + high) / 2
        if (ratio(lerp(seed, target, middle), background) >= minimum) high = middle else low = middle
    }
    return lerp(seed, target, high)
}

internal fun customColorScheme(seed: Color, dark: Boolean, style: VisualStyle = VisualStyle.Liquid,
    glassPalette: GlassPalette = GlassPalette.Mist): ColorScheme {
    val palette = if (style == VisualStyle.Liquid) glassPalette.colors(dark)
        else requireNotNull(if (dark) style.design.dark else style.design.light)
    return paletteColorScheme(palette, seed, dark)
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun anthropicFamily(resource: Int): FontFamily = remember(resource) {
    FontFamily(*listOf(400 to FontWeight.Normal, 500 to FontWeight.Medium,
        600 to FontWeight.SemiBold, 700 to FontWeight.Bold).map { (axis, weight) ->
        Font(resource, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(axis)))
    }.toTypedArray())
}

internal fun packColorScheme(pack: ThemePack, seed: Color, dark: Boolean) = paletteColorScheme(if (dark) pack.dark else pack.light,seed,dark)

private fun paletteColorScheme(p: ThemePalette, seed: Color, dark: Boolean): ColorScheme {
    fun color(s: String) = Color(0xFF000000 or s.removePrefix("#").toLong(16))
    val canvas = color(p.background); val surface = color(p.surface)
    // A shared ink/accent must remain readable on both surfaces, even for an extreme imported palette.
    // Reject opposite-luminance surfaces at import; these two backgrounds share the same readable endpoint.
    val ink = readableTone(readableTone(color(p.text),canvas,5f),surface,5f)
    val muted = readableTone(readableTone(color(p.muted),canvas,5f),surface,5f)
    val primary = readableTone(readableTone(seed,canvas,5f),surface,5f)
    val base = if(dark) Dark else Light
    val container = lerp(surface,primary,.10f)
    return base.copy(primary=primary,onPrimary=readableTone(Color.White,primary),primaryContainer=container,onPrimaryContainer=readableTone(ink,container),
        secondary=primary,onSecondary=readableTone(Color.White,primary),secondaryContainer=container,onSecondaryContainer=readableTone(ink,container),
        tertiary=primary,onTertiary=readableTone(Color.White,primary),tertiaryContainer=container,onTertiaryContainer=readableTone(ink,container),
        background=canvas,onBackground=ink,surface=surface,onSurface=ink,surfaceVariant=surface,onSurfaceVariant=muted,
        surfaceTint=primary,outline=muted,outlineVariant=color(p.border),
        surfaceContainerLow=lerp(surface,canvas,.5f),surfaceContainer=surface,surfaceContainerHigh=container,surfaceContainerHighest=container,
        error=readableTone(readableTone(base.error,canvas),surface),onError=readableTone(Color.White,readableTone(readableTone(base.error,canvas),surface)))
}
