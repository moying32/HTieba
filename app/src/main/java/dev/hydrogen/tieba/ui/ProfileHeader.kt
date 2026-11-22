package dev.hydrogen.tieba.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.hydrogen.tieba.data.AccountState
import dev.hydrogen.tieba.data.Person

/** Samples only the cover, keeping the glass controls outside their own backdrop recording. */
@Composable
internal fun ProfileCanvas(image: String, modifier: Modifier, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val scene = remember(layer) { GlassScene(layer) }
    val surface = MaterialTheme.colorScheme.surface
    val rich = LocalRichEffects.current
    Box(modifier.background(surface)) {
        Box(Modifier.matchParentSize().then(if (rich) Modifier.captureGlassScene(scene) else Modifier)) {
            Box(Modifier.fillMaxSize().background(surface))
            ProfileCover(image, Modifier.fillMaxWidth().height(300.dp))
        }
        CompositionLocalProvider(LocalGlassScene provides scene) { content() }
    }
}

@Composable
private fun ProfileCover(image: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < .5f
    var state by remember(image) { mutableIntStateOf(if (image.isBlank()) 0 else 1) }
    Box(modifier.background(Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer, colors.surface)))
        .semantics {
            contentDescription = "个人主页背景"
            stateDescription = when (state) {
                0 -> "未提供背景，显示主题渐变"
                1 -> "正在加载"
                2 -> "已加载个人主页背景"
                else -> "背景加载失败，显示主题渐变"
            }
        }) {
        if (image.isNotBlank()) AsyncImage(image, null, modifier = Modifier.fillMaxSize(), imageLoader = LocalAppImageLoader.current, contentScale = ContentScale.Crop,
            onSuccess = { state = 2 }, onError = { state = 3 })
        // Protect the heading and fade into the light surface (or the selected dark/theme surface).
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to colors.surface.copy(alpha = .66f),
            .22f to colors.surface.copy(alpha = if (dark) .62f else .52f),
            .45f to colors.surface.copy(alpha = if (dark) .72f else .62f),
            .70f to colors.surface.copy(alpha = .92f),
            1f to colors.surface,
        )))
    }
}

@Composable
internal fun ProfileIdentityRow(account: AccountState, onFollows: () -> Unit, onFans: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PersonAvatar(Person(account.uid, account.name.ifBlank { "我" }, account.avatar), Modifier.size(72.dp))
        if (account.loggedIn) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileRelationPill(account.profile?.follows, "关注", Modifier.weight(1f), onFollows)
            ProfileRelationPill(account.profile?.fans, "粉丝", Modifier.weight(1f), onFans)
        }
    }
}

@Composable
private fun ProfileRelationPill(count: Long?, label: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    GlassPanel(modifier.pressMotion(interaction), backdrop = true, liquid = true) {
        Column(Modifier.fillMaxWidth().clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button,
                onClickLabel = "查看${label}列表", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "查看${label}列表，共${count?.toString() ?: "未知"}人" }
            .heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(count?.let(::compactNumber) ?: "—", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
