package dev.hydrogen.tieba.ui

import androidx.compose.runtime.compositionLocalOf
import dev.hydrogen.tieba.data.ImageQuality

internal val LocalDecorativeBadges = compositionLocalOf { true }
internal val LocalFeedImageQuality = compositionLocalOf { ImageQuality.Low }
internal val LocalMinimalMode = compositionLocalOf { false }
internal val LocalAppImageLoader = androidx.compose.runtime.staticCompositionLocalOf<coil3.ImageLoader> {
    error("Image loader must be provided by HydrogenApp")
}
