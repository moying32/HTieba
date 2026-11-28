package dev.hydrogen.tieba.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class BackPreview(private val fraction: () -> Float, private val source: () -> Any?, private val fromLeft: () -> Boolean, private val distance: Float) {
    fun layer(route: Any?): Modifier = Modifier.graphicsLayer {
        val value = if (source() == route) fraction() else 0f
        translationX = backGestureOffset(value, fromLeft()) * distance
        scaleX = 1f - .015f * value.coerceAtMost(1f)
        scaleY = scaleX
    }
}

/** Keep the handoff on the outgoing page so the incoming page never inherits its offset. */
@Composable
internal fun rememberBackPreview(
    enabled: Boolean,
    motion: Boolean = LocalMotionEnabled.current,
    routeKey: Any? = Unit,
    onBack: (gesture: Boolean) -> Unit,
): BackPreview {
    val progress = remember { Animatable(0f) }
    var fromLeft by remember { mutableStateOf(true) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val currentBack by rememberUpdatedState(onBack)
    val currentMotion by rememberUpdatedState(motion)
    val currentRoute by rememberUpdatedState(routeKey)
    val currentEnabled by rememberUpdatedState(enabled)
    val distance = with(LocalDensity.current) { 24.dp.toPx() }
    var previewRoute by remember { mutableStateOf<Any?>(null) }
    PredictiveBackHandler(enabled) { events ->
        settling?.cancel()
        val source = currentRoute
        progress.snapTo(0f)
        previewRoute = source
        var gesture = false
        try {
            events.collect { event ->
                fromLeft = event.swipeEdge == BackEventCompat.EDGE_LEFT
                if (currentMotion && event.progress > 0f) gesture = true
                progress.snapTo(if (currentMotion) event.progress.coerceIn(0f, 1f) else 0f)
            }
            if (currentEnabled && currentRoute == source) currentBack(gesture)
            settling = scope.launch {
                if (gesture && currentMotion) progress.animateTo(1f, tween(HydrogenMotion.ExitDuration, easing = HydrogenMotion.EaseOut))
                progress.snapTo(0f)
            }
        } catch (cancelled: CancellationException) {
            settling = scope.launch {
                if (currentMotion) progress.animateTo(0f, tween(140, easing = HydrogenMotion.EaseOut))
                else progress.snapTo(0f)
            }
            throw cancelled
        }
    }
    return BackPreview({ if (currentMotion) progress.value else 0f }, { previewRoute }, { fromLeft }, distance)
}
