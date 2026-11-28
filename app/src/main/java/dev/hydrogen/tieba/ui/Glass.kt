package dev.hydrogen.tieba.ui

import dev.hydrogen.tieba.data.VisualStyle
import dev.hydrogen.tieba.ui.theme.LocalVisualStyle
import dev.hydrogen.tieba.ui.theme.SurfaceFinish
import dev.hydrogen.tieba.ui.theme.currentDesign
import dev.hydrogen.tieba.ui.theme.actionShape
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
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
internal val LocalGlassTransparency = compositionLocalOf { .68f }
val LocalGlassScene = staticCompositionLocalOf<GlassScene?> { null }

class GlassScene(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Unspecified)
        internal set
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
    val design = currentDesign
    val rich = backdrop && liquid && LocalRichEffects.current && design.finish == SurfaceFinish.Liquid
    val refracts = design.finish == SurfaceFinish.Liquid && liquid
    val scene = LocalGlassScene.current
    val blurred = rememberGraphicsLayer()
    val refraction = remember(refracts, rich) {
        if (refracts && rich && Build.VERSION.SDK_INT >= 33) runCatching { LiquidRefraction() }.getOrNull() else null
    }
    var position by remember { mutableStateOf(Offset.Unspecified) }
    val style = LocalVisualStyle.current
    val transparency = LocalGlassTransparency.current
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < .5f
    val shape = if (style == VisualStyle.Pixel) MaterialTheme.shapes.large else RoundedCornerShape(if (refracts) 48.dp else design.panel.dp)
    val tint = scheme.surface
    val strength = tintStrength.coerceIn(0f, 1f)
    val opacity = if (dark) .93f - transparency * .74f else .96f - transparency * .79f
    val fill = Brush.verticalGradient(listOf(
        tint.copy(alpha = ((opacity + .07f) * strength).coerceAtMost(1f)),
        tint.copy(alpha = ((opacity - .03f) * strength).coerceAtLeast(0f)),
    ))
    val blurRadius = glassBlurDp(transparency).dp
    val rim = Brush.verticalGradient(colorStops = arrayOf(
        0f to Color.White.copy(alpha = if (dark) (.16f + transparency * .37f) else (.48f + transparency * .48f)),
        .36f to Color.White.copy(alpha = if (dark) (.08f + transparency * .13f) else (.19f + transparency * .21f)),
        .75f to scheme.onSurface.copy(alpha = .06f),
        1f to Color.White.copy(alpha = if (dark) (.12f + transparency * .20f) else (.36f + transparency * .43f)),
    ))
    Box(modifier.shadow(if (rich) 12.dp else 0.dp, shape, ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = .15f),
        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = .18f)).clip(shape)
        .onGloballyPositioned { position = it.positionInRoot() }
        .drawWithContent {
            val canSample = Build.VERSION.SDK_INT >= 31 && rich && scene != null &&
                position.isSpecified && scene.origin.isSpecified && scene.layer.size.width > 0 &&
                scene.layer.size.height > 0 && size.minDimension >= 1f
            // Keep a full backdrop under controls while the source is being measured or on older Android.
            if (canSample) {
                blurred.renderEffect = if (Build.VERSION.SDK_INT >= 33 && refraction != null)
                    refraction.effect(size.width, size.height, density, focus(), transparency)
                else BlurEffect(blurRadius.toPx(), blurRadius.toPx(), TileMode.Clamp)
                blurred.record(size = IntSize(size.width.roundToInt(), size.height.roundToInt())) {
                    drawRect(tint)
                    val delta = scene.origin - position
                    translate(delta.x, delta.y) { drawLayer(scene.layer) }
                }
                drawLayer(blurred)
                drawRect(fill)
            } else {
                drawRect(tint)
            }
            drawContent()
        }
        .then(when {
            rich -> Modifier.border(1.dp, rim, shape)
            style == VisualStyle.Pixel -> Modifier.border(1.dp, scheme.outline.copy(alpha = .48f), shape)
            style == VisualStyle.Minimal -> Modifier
            else -> Modifier.border(.75.dp, scheme.outlineVariant, shape)
        })) {
        content()
    }
}

@Composable
fun GlassNavigation(model: TiebaViewModel) {
    val motion = LocalMotionEnabled.current
    val design = currentDesign
    val rich = LocalRichEffects.current
    val style = LocalVisualStyle.current
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
                val lensShape = design.actionShape(style)
                val tint = MaterialTheme.colorScheme.surface
                Box(Modifier.width(slot - 4.dp).height(with(density) { rowHeight.toDp() }).graphicsLayer {
                    translationX = (if (dragging) NavigationPhysics.resist(tracked) else position.value) * slotPx + insetPx
                    val stretch = if (!motion || design.finish != SurfaceFinish.Liquid) 0f else if (dragging) .07f else (abs(position.velocity) * .012f).coerceAtMost(.16f)
                    scaleX = 1f + stretch
                    scaleY = 1f - stretch * .25f
                }.then(if (rich) Modifier.shadow(2.dp, lensShape) else Modifier)
                    .clip(lensShape)
                    .background(if (rich) Brush.verticalGradient(listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = .91f), MaterialTheme.colorScheme.primaryContainer.copy(alpha = .88f),
                    )) else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primaryContainer)))
                    .then(if (style == VisualStyle.Pixel) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .60f), lensShape) else Modifier)
                    .then(if (rich && style == VisualStyle.Liquid) Modifier.border(.8.dp,
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = .78f), tint.copy(alpha = .16f))), lensShape)
                        else Modifier))
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
                        val colors = MaterialTheme.colorScheme
                        Column(Modifier.weight(1f).clip(lensShape)
                            .background(if (pressed) colors.onSurface.copy(alpha = .08f) else Color.Transparent)
                            .clickable(interactionSource = interaction, indication = null,
                                role = if (destination == HomeTab.Compose) Role.Button else Role.Tab) { model.navigateTab(destination) }
                            .semantics { selected = active }.heightIn(min = 60.dp).padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            val tint = if (active || destination == HomeTab.Compose) colors.primary else colors.onSurfaceVariant
                            val iconBackground = when {
                                rich && !active -> Modifier.background(colors.surface.copy(alpha = .92f), RoundedCornerShape(8.dp))
                                style == VisualStyle.Claude && destination == HomeTab.Compose ->
                                    Modifier.background(colors.primary, RoundedCornerShape(9.dp))
                                else -> Modifier
                            }
                            Box(iconBackground.padding(horizontal = 5.dp, vertical = 1.dp)) {
                                ThemeTabIcon(destination, active, Modifier.size(23.dp),
                                    if (style == VisualStyle.Claude && destination == HomeTab.Compose) colors.onPrimary else tint)
                            }
                            Text(destination.title, style = MaterialTheme.typography.labelMedium,
                                color = if (active) colors.primary else colors.onSurfaceVariant,
                                modifier = (if (rich && !active) Modifier.background(colors.surface.copy(alpha = .92f), RoundedCornerShape(5.dp))
                                    else Modifier).padding(horizontal = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

