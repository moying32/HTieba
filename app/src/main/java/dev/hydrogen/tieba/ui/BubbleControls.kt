package dev.hydrogen.tieba.ui

import dev.hydrogen.tieba.data.VisualStyle
import dev.hydrogen.tieba.ui.theme.LocalVisualStyle
import dev.hydrogen.tieba.ui.theme.currentDesign
import dev.hydrogen.tieba.ui.theme.actionShape
import dev.hydrogen.tieba.ui.theme.PixelStepShape
import dev.hydrogen.tieba.ui.theme.PixelPanelShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/** Lightweight material for repeated actions. Existing glass panels keep their
 * own background sampling and refraction; list buttons don't each allocate a blur. */
@Composable
internal fun Modifier.bubbleBackground(
    shape: Shape = currentDesign.actionShape(LocalVisualStyle.current), selected: Boolean = false, enabled: Boolean = true,
): Modifier {
    val colors = MaterialTheme.colorScheme
    val style = LocalVisualStyle.current
    val opacity = if (enabled) 1f else .55f
    val fill = when {
        selected -> colors.primaryContainer
        style == VisualStyle.Minimal -> Color.Transparent
        style == VisualStyle.Claude -> colors.surface
        else -> colors.surfaceContainerLow
    }
    if (style == VisualStyle.Pixel) {
        return pixelSurface(
            if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
            PixelStepShape, selected, opacity,
        )
    }
    val surface = clip(shape).background(fill.copy(alpha = fill.alpha * opacity), shape)
    return when {
        style == VisualStyle.Claude -> surface.border(.75.dp,
            if (selected) colors.primary.copy(alpha = .64f) else colors.outlineVariant, shape)
        else -> surface
    }
}

/** A single cached strip gives the stepped surface a raised tile edge. */
@Composable
private fun Modifier.pixelSurface(fill: Color, shape: Shape, selected: Boolean = false, opacity: Float = 1f): Modifier {
    val colors = MaterialTheme.colorScheme
    val edge = if (selected) colors.primary else colors.outline
    return clip(shape).background(fill.copy(alpha = opacity)).drawWithCache {
        val base = 2.dp.toPx()
        onDrawBehind {
            drawRect(edge.copy(alpha = (if (selected) .48f else .23f) * opacity),
                Offset(0f, size.height - base), Size(size.width, base))
        }
    }.border(1.dp, edge.copy(alpha = (if (selected) .78f else .40f) * opacity), shape)
}

/** Outer padding separates entries; the whole rounded surface is clickable. */
@Composable
internal fun Modifier.navigationBubble(): Modifier {
    val colors = MaterialTheme.colorScheme
    val style = LocalVisualStyle.current
    val inset = padding(horizontal = 12.dp, vertical = 3.dp)
    if (style == VisualStyle.Pixel) return inset.pixelSurface(colors.surface, PixelPanelShape)
    if (style == VisualStyle.Claude) return inset.drawWithCache {
        onDrawBehind { drawLine(colors.outlineVariant, Offset(12.dp.toPx(), size.height - .5.dp.toPx()),
            Offset((size.width - 12.dp.toPx()).coerceAtLeast(12.dp.toPx()), size.height - .5.dp.toPx()), .75.dp.toPx()) }
    }
    return inset.background(if (style == VisualStyle.Minimal) Color.Transparent else colors.surface,
        RoundedCornerShape(currentDesign.field.dp))
}

/** Draw a shallower capsule inside the existing target, without clipping text,
 * changing layout, or creating overlapping invisible touch areas. */
@Composable
internal fun Modifier.insetBubbleBackground(selected: Boolean = false, enabled: Boolean = true): Modifier {
    val colors = MaterialTheme.colorScheme
    val style = LocalVisualStyle.current
    if (!selected) return this
    val shape = currentDesign.actionShape(style)
    val alpha = if (enabled) 1f else .55f
    return drawWithCache {
        val inset = 5.dp.toPx().coerceAtMost(size.height / 8f)
        val outline = shape.createOutline(Size(size.width, size.height - inset * 2), layoutDirection, this)
        onDrawBehind { translate(top = inset) { drawOutline(outline, colors.primaryContainer.copy(alpha = alpha)) } }
    }
}

/** A post owns one material, rather than putting a large bubble behind each label.
 * Repeated cards never sample the layer they themselves are being recorded into. */
@Composable
internal fun Modifier.postGlassBackground(shape: Shape = MaterialTheme.shapes.medium): Modifier {
    val colors = MaterialTheme.colorScheme
    val style = LocalVisualStyle.current
    if (style == VisualStyle.Pixel) return pixelSurface(colors.surface, PixelPanelShape)
    val surface = clip(shape).background(colors.surface, shape)
    return when (style) {
        VisualStyle.Claude -> surface.drawWithCache {
            val start = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) 0f else size.width - 2.dp.toPx()
            onDrawBehind { drawRect(colors.primary.copy(alpha = .65f), Offset(start, 12.dp.toPx()),
                Size(2.dp.toPx(), (size.height - 24.dp.toPx()).coerceAtLeast(0f))) }
        }
        else -> surface
    }
}

/** The visual capsule follows its content; the independent outer target remains
 * at least 48 physical dp at every interface scale. No overlapping hit slop. */
@Composable
internal fun CompactBubbleButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, selected: Boolean = false, fill: Boolean = false,
    shape: Shape = currentDesign.actionShape(LocalVisualStyle.current),
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    visualMinHeight: Dp = 30.dp,
    content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val hitShape = if (LocalVisualStyle.current == VisualStyle.Pixel) PixelStepShape else shape
    Box(modifier.sizeIn(minWidth = LocalTouchTarget.current, minHeight = LocalTouchTarget.current)
        .clip(hitShape).clickable(enabled = enabled, role = Role.Button, interactionSource = interaction,
            indication = null, onClick = onClick), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }) {
            Row((if (fill) Modifier.fillMaxWidth() else Modifier)
                .bubbleBackground(shape, selected = selected || pressed || focused, enabled = enabled)
                .heightIn(min = visualMinHeight).padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                content = content)
        }
    }
}

@Composable
internal fun BubbleIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null, visualSize: Dp = 38.dp, content: @Composable () -> Unit) {
    IconButton(onClick, modifier.sizeIn(minWidth = LocalTouchTarget.current, minHeight = LocalTouchTarget.current),
        enabled = enabled, interactionSource = interactionSource) {
        Box(Modifier.size(visualSize).bubbleBackground(enabled = enabled), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
internal fun BubbleTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    content: @Composable RowScope.() -> Unit) {
    CompactBubbleButton(onClick, modifier.padding(2.dp), enabled = enabled, contentPadding = contentPadding) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .38f)) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
        }
    }
}

@Composable
internal fun BubbleOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    CompactBubbleButton(onClick, modifier, enabled = enabled, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp)) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
    }
}

@Composable
internal fun BubbleDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(expanded, onDismissRequest, shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp, shadowElevation = 6.dp,
        border = BorderStroke(.6.dp, MaterialTheme.colorScheme.outlineVariant), content = content)
}

@Composable
internal fun BubbleMenuItem(text: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null) {
    DropdownMenuItem(text, onClick, modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        .heightIn(min = LocalTouchTarget.current).insetBubbleBackground(enabled = enabled),
        enabled = enabled, leadingIcon = leadingIcon, trailingIcon = trailingIcon)
}

@Composable
internal fun BubbleFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    CompactBubbleButton(onClick, modifier.semantics { this.selected = selected }, selected = selected, enabled = enabled) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) { label() }
    }
}

@Composable
internal fun BubbleAssistChip(onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    CompactBubbleButton(onClick, modifier) { ProvideTextStyle(MaterialTheme.typography.labelLarge) { label() } }
}

@Composable
internal fun BubbleTab(selected: Boolean, onClick: () -> Unit, text: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Tab(selected, onClick, modifier.padding(horizontal = 3.dp).insetBubbleBackground(selected = selected)
        .heightIn(min = LocalTouchTarget.current), text = text)
}
