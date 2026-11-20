package dev.hydrogen.tieba.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LocalMotionEnabled = staticCompositionLocalOf { true }
val LocalTouchTarget = staticCompositionLocalOf { 48.dp }
object HydrogenMotion {
    const val PageDuration=220
    const val TabDuration=200
    const val ExitDuration=120
    const val ImageControlsEnterDuration=260
    const val ImageControlsExitDuration=240
    const val ImageZoomDuration=180
    const val ImageZoomStiffness=1200f
    const val VideoControlsEnterDuration=260
    const val VideoControlsExitDuration=240
    val EaseOut = CubicBezierEasing(.23f, 1f, .32f, 1f)
}
