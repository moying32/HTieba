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

/** Small direct manipulation feedback; commit changes the route once, cancel keeps it. */
@Composable
internal fun Modifier.backGesture(
    enabled: Boolean,
    motion: Boolean = LocalMotionEnabled.current,
    routeKey: Any? = Unit,
    onBack: (gesture: Boolean) -> Unit,
): Modifier {
    val progress = remember { Animatable(0f) }
    var fromLeft by remember { mutableStateOf(true) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val currentBack by rememberUpdatedState(onBack)
    val currentMotion by rememberUpdatedState(motion)
    val currentRoute by rememberUpdatedState(routeKey)
    val currentEnabled by rememberUpdatedState(enabled)
    val distance = with(LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(routeKey, motion) { settling?.cancel(); progress.snapTo(0f) }
    PredictiveBackHandler(enabled) { events ->
        settling?.cancel()
        val source = currentRoute
        var gesture = false
        try {
            events.collect { event ->
                fromLeft = event.swipeEdge == BackEventCompat.EDGE_LEFT
                gesture = true
                progress.snapTo(if (currentMotion) event.progress.coerceIn(0f, 1f) else 0f)
            }
            // A gesture already supplied feedback. Do not replay a second slide.
            if (currentEnabled && currentRoute == source) currentBack(gesture)
            progress.snapTo(0f)
        } catch (cancelled: CancellationException) {
            settling = scope.launch {
                if (currentMotion) progress.animateTo(0f, tween(140, easing = HydrogenMotion.EaseOut))
                else progress.snapTo(0f)
            }
            throw cancelled
        }
    }
    return graphicsLayer {
        val fraction = if (motion) progress.value else 0f
        translationX = backGestureOffset(fraction, fromLeft) * distance
        scaleX = 1f - .015f * fraction
        scaleY = 1f - .015f * fraction
    }
}
