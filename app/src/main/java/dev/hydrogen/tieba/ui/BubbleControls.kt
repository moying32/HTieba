package dev.hydrogen.tieba.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
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
    shape: Shape = RoundedCornerShape(50), selected: Boolean = false, enabled: Boolean = true,
): Modifier {
    val colors = MaterialTheme.colorScheme
    val rich = LocalRichEffects.current
    val base = if (selected) colors.primaryContainer else colors.surfaceContainerHigh
    val opacity = if (enabled) 1f else .55f
    val fill = if (rich) listOf(lerp(base, colors.surface, .38f).copy(alpha = .94f * opacity), base.copy(alpha = .82f * opacity))
        else listOf(base.copy(alpha = opacity), base.copy(alpha = opacity))
    val edge = if (selected) colors.primary.copy(alpha = .45f) else colors.outlineVariant.copy(alpha = .85f)
    return clip(shape).background(Brush.linearGradient(fill), shape)
        .border(BorderStroke(.6.dp, Brush.linearGradient(listOf(colors.surface.copy(alpha = .9f), edge))), shape)
}

/** Outer padding separates entries; the whole rounded surface is clickable. */
@Composable
internal fun Modifier.navigationBubble(): Modifier = padding(horizontal = 12.dp, vertical = 3.dp)
    .bubbleBackground(RoundedCornerShape(26.dp))

/** Draw a shallower capsule inside the existing target, without clipping text,
 * changing layout, or creating overlapping invisible touch areas. */
@Composable
internal fun Modifier.insetBubbleBackground(selected: Boolean = false, enabled: Boolean = true): Modifier {
    val colors = MaterialTheme.colorScheme
    val rich = LocalRichEffects.current
    val base = if (selected) colors.primaryContainer else colors.surfaceContainerHigh
    val alpha = if (enabled) 1f else .55f
    return drawWithCache {
        val inset = 5.dp.toPx().coerceAtMost(size.height / 8f)
        val outline = RoundedCornerShape(50).createOutline(Size(size.width, size.height - inset * 2), layoutDirection, this)
        val fill = Brush.linearGradient(listOf(lerp(base, colors.surface, if (rich) .38f else 0f).copy(alpha = alpha),
            base.copy(alpha = if (rich) .86f * alpha else alpha)))
        val edge = Brush.linearGradient(listOf(colors.surface.copy(alpha = .9f),
            (if (selected) colors.primary else colors.outlineVariant).copy(alpha = .7f)))
        onDrawBehind { translate(top = inset) { drawOutline(outline, fill); drawOutline(outline, edge, style = Stroke(.6.dp.toPx())) } }
    }
}

/** A post owns one material, rather than putting a large bubble behind each label.
 * Repeated cards never sample the layer they themselves are being recorded into. */
@Composable
internal fun Modifier.postGlassBackground(shape: Shape = RoundedCornerShape(28.dp)): Modifier {
    val colors = MaterialTheme.colorScheme
    val rich = LocalRichEffects.current
    val fill = if (rich) listOf(colors.surfaceContainerLow.copy(alpha = .96f), colors.surface.copy(alpha = .88f))
        else listOf(colors.surface, colors.surface)
    return clip(shape).background(Brush.linearGradient(fill), shape)
        .border(.75.dp, Brush.linearGradient(listOf(colors.outlineVariant.copy(alpha = .9f),
            colors.surface.copy(alpha = .7f), colors.outlineVariant.copy(alpha = .6f))), shape)
}

/** The visual capsule follows its content; the independent outer target remains
 * at least 48 physical dp at every interface scale. No overlapping hit slop. */
@Composable
internal fun CompactBubbleButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, selected: Boolean = false, fill: Boolean = false,
    shape: Shape = RoundedCornerShape(50),
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    visualMinHeight: Dp = 30.dp,
    content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    Box(modifier.sizeIn(minWidth = LocalTouchTarget.current, minHeight = LocalTouchTarget.current)
        .clip(shape).clickable(enabled = enabled, role = Role.Button, interactionSource = interaction,
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
    interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit) {
    IconButton(onClick, modifier.sizeIn(minWidth = LocalTouchTarget.current, minHeight = LocalTouchTarget.current),
        enabled = enabled, interactionSource = interactionSource) {
        Box(Modifier.size(38.dp).bubbleBackground(CircleShape, enabled = enabled), contentAlignment = Alignment.Center) { content() }
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
    DropdownMenu(expanded, onDismissRequest, shape = RoundedCornerShape(30.dp),
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
