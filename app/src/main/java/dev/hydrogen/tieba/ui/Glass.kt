package dev.hydrogen.tieba.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

val LocalRichEffects = staticCompositionLocalOf { true }
val LocalGlassScene = staticCompositionLocalOf<GlassScene?> { null }

class GlassScene(val layer: GraphicsLayer) {
    var origin = Offset.Zero
}

fun Modifier.captureGlassScene(scene: GlassScene): Modifier = onGloballyPositioned { scene.origin = it.positionInRoot() }
    .drawWithContent {
        scene.layer.record { this@drawWithContent.drawContent() }
        drawLayer(scene.layer)
    }

@Composable
fun glassBackground(): Brush = Brush.linearGradient(listOf(
    MaterialTheme.colorScheme.background,
    MaterialTheme.colorScheme.primaryContainer.copy(alpha = .54f),
    MaterialTheme.colorScheme.background,
    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .45f),
))

fun Modifier.pressMotion(source: MutableInteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val rich = LocalMotionEnabled.current
    val scale by animateFloatAsState(if (pressed && rich) .975f else 1f, tween(if (rich) 120 else 0), label = "按压")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
fun GlassPanel(modifier: Modifier = Modifier, backdrop: Boolean = false, liquid: Boolean = false,
    focus: () -> Float = { .5f }, tintStrength: Float = 1f, content: @Composable () -> Unit) {
    val rich = LocalRichEffects.current
    val scene = LocalGlassScene.current
    val blurred = rememberGraphicsLayer()
    val refraction = remember(liquid, rich) { if (liquid && rich && Build.VERSION.SDK_INT >= 33) runCatching { LiquidRefraction() }.getOrNull() else null }
    var position by remember { mutableStateOf(Offset.Zero) }
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val shape = RoundedCornerShape(if (liquid) 50.dp else 36.dp)
    val tint = MaterialTheme.colorScheme.surface
    val strength = tintStrength.coerceIn(0f, 1f)
    val colors = if (rich) listOf(tint.copy(alpha = (if (liquid) { if (dark) .72f else .66f } else if (dark) .72f else .78f) * strength),
        tint.copy(alpha = (if (liquid) { if (dark) .64f else .56f } else if (dark) .64f else .56f) * strength)) else listOf(tint, tint)
    val light = if (dark) Color.White.copy(alpha = .25f) else Color.White.copy(alpha = .95f)
    Box(modifier.shadow(if (rich) 12.dp else 0.dp, shape, ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = .15f),
        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = .18f)).clip(shape)
        .onGloballyPositioned { position = it.positionInRoot() }
        .drawWithContent {
            if (backdrop && rich && Build.VERSION.SDK_INT >= 31 && scene != null && scene.layer.size.width > 0) {
                blurred.renderEffect = if (Build.VERSION.SDK_INT >= 33 && refraction != null)
                    refraction.effect(size.width, size.height, density, focus())
                else BlurEffect(16.dp.toPx(), 16.dp.toPx(), TileMode.Clamp)
                blurred.record(size = IntSize(size.width.roundToInt(), size.height.roundToInt())) {
                    val delta = scene.origin - position
                    translate(delta.x, delta.y) { drawLayer(scene.layer) }
                }
                drawLayer(blurred)
            }
            drawRect(Brush.linearGradient(colors))
            drawContent()
        }
        .border(1.dp, Brush.linearGradient(listOf(light, MaterialTheme.colorScheme.primary.copy(alpha = .08f), light.copy(alpha = .2f))), shape)) {
        content()
    }
}

@Composable
fun GlassNavigation(model: TiebaViewModel) {
    val motion = LocalMotionEnabled.current
    val rich = LocalRichEffects.current
    val scope = rememberCoroutineScope()
    val position = remember { Animatable(model.tab.ordinal.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var tracked by remember { mutableFloatStateOf(position.value) }
    var releaseVelocity by remember { mutableFloatStateOf(0f) }
    var releaseCount by remember { mutableIntStateOf(0) }
    val tracker = remember { VelocityTracker() }
    LaunchedEffect(model.tab, dragging, releaseCount, motion) {
        if (!dragging) {
            if (motion) position.animateTo(model.tab.ordinal.toFloat(),
                spring(dampingRatio = if (releaseVelocity == 0f) 1f else .8f, stiffness = 650f),
                initialVelocity = releaseVelocity)
            else position.snapTo(model.tab.ordinal.toFloat())
            releaseVelocity = 0f
        }
    }
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.widthIn(max = 500.dp).fillMaxWidth(), backdrop = true, liquid = true,
            focus = { ((if (dragging) NavigationPhysics.resist(tracked) else position.value) + .5f) / 5f }) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(5.dp)) {
                val slot = maxWidth / HomeTab.entries.size
                val slotPx = with(LocalDensity.current) { slot.toPx() }
                val insetPx = with(LocalDensity.current) { 2.dp.toPx() }
                val density = LocalDensity.current
                var rowHeight by remember(density) { mutableIntStateOf(with(density) { 60.dp.roundToPx() }) }
                val lensShape = RoundedCornerShape(32.dp)
                val tint = MaterialTheme.colorScheme.surface
                Box(Modifier.width(slot - 4.dp).height(with(density) { rowHeight.toDp() }).graphicsLayer {
                    translationX = (if (dragging) NavigationPhysics.resist(tracked) else position.value) * slotPx + insetPx
                    val stretch = if (!motion) 0f else if (dragging) .07f else (abs(position.velocity) * .012f).coerceAtMost(.16f)
                    scaleX = 1f + stretch
                    scaleY = 1f - stretch * .25f
                }.shadow(if (rich) 2.dp else 0.dp, lensShape)
                    .clip(lensShape).background(Brush.linearGradient(listOf(
                        tint.copy(alpha = if (rich) .66f else 1f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .70f))))
                    .border(.75.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = if (rich) .75f else .2f),
                        MaterialTheme.colorScheme.outline.copy(alpha = .10f), Color.White.copy(alpha = .18f))), lensShape))
                Row(Modifier.fillMaxWidth().onSizeChanged { rowHeight = it.height }.pointerInput(slotPx, motion) {
                    var accumulated = 0f
                    var start = 0f
                    fun finish(cancelled: Boolean) {
                        val velocity = if (cancelled || !motion) 0f else (tracker.calculateVelocity().x / slotPx).coerceIn(-15f, 15f)
                        val destination = if (cancelled) model.tab else NavigationPhysics.destination(tracked, velocity)
                        scope.launch {
                            position.snapTo(NavigationPhysics.resist(tracked))
                            releaseVelocity = velocity
                            dragging = false
                            releaseCount++
                            model.navigateTab(destination)
                        }
                    }
                    detectHorizontalDragGestures(
                        onDragStart = {
                            start = position.value
                            tracked = start
                            accumulated = 0f
                            tracker.resetTracking()
                            dragging = true
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            accumulated += delta
                            tracked = start + accumulated / slotPx
                            tracker.addPosition(change.uptimeMillis, change.position)
                        },
                        onDragEnd = { finish(false) }, onDragCancel = { finish(true) },
                    )
                }, verticalAlignment = Alignment.CenterVertically) {
                    HomeTab.entries.forEach { destination ->
                        val active = model.tab == destination
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        Column(Modifier.weight(1f).clip(lensShape)
                            .background(if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = .06f) else Color.Transparent)
                            .clickable(interactionSource = interaction, indication = null,
                                role = if (destination == HomeTab.Compose) Role.Button else Role.Tab) { model.navigateTab(destination) }
                            .semantics { selected = active }.heightIn(min = 60.dp).padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(when (destination) {
                                HomeTab.Feed -> Icons.Outlined.Home
                                HomeTab.Browse -> Icons.Outlined.Explore
                                HomeTab.Compose -> Icons.Outlined.Add
                                HomeTab.Messages -> Icons.Outlined.ChatBubbleOutline
                                HomeTab.Profile -> Icons.Outlined.PersonOutline
                            }, null, Modifier.size(23.dp), tint = if (active || destination == HomeTab.Compose)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(destination.title, style = MaterialTheme.typography.labelMedium,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

