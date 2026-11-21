package dev.hydrogen.tieba.ui

import androidx.compose.runtime.compositionLocalOf
import dev.hydrogen.tieba.data.ImageQuality

internal val LocalDecorativeBadges = compositionLocalOf { true }
internal val LocalFeedImageQuality = compositionLocalOf { ImageQuality.Low }
