package dev.hydrogen.tieba.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.hydrogen.tieba.data.Person
import dev.hydrogen.tieba.data.ThreadItem
import dev.hydrogen.tieba.data.IdentitySurface
import dev.hydrogen.tieba.data.UserBadge
import dev.hydrogen.tieba.data.VisualStyle
import dev.hydrogen.tieba.ui.theme.LocalVisualStyle
import dev.hydrogen.tieba.ui.theme.LocalThemePack
import dev.hydrogen.tieba.ui.theme.LocalAppFont
import dev.hydrogen.tieba.data.AppFont
import dev.hydrogen.tieba.data.galleryImages
import dev.hydrogen.tieba.data.ImageGallery
import dev.hydrogen.tieba.data.ImageSource
import dev.hydrogen.tieba.data.ImageQuality
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PersonAvatar(person: Person, modifier: Modifier = Modifier) {
    var state by remember(person.avatar) { mutableIntStateOf(if (person.avatar.isBlank()) 0 else 1) }
    Box(modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).semantics {
        contentDescription = "${person.name}的头像"
        stateDescription = when (state) { 0 -> "未提供头像"; 1 -> "正在加载"; 2 -> "已加载"; else -> "加载失败，显示名字占位" }
    }, contentAlignment = Alignment.Center) {
        if (state != 2) Text(person.name.ifEmpty { "吧" }.let { String(Character.toChars(it.codePointAt(0))) },
            color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp)
        if (person.avatar.isNotBlank()) AsyncImage(person.avatar, null, modifier = Modifier.fillMaxSize(), imageLoader = LocalAppImageLoader.current, contentScale = ContentScale.Crop,
            onSuccess = { state = 2 }, onError = { state = 3 })
    }
}

@Composable
fun PersonBadges(person: Person, surface: IdentitySurface, modifier: Modifier = Modifier) {
    val badges = if (LocalDecorativeBadges.current) person.badges.filter { surface in it.surfaces } else emptyList()
    // Forum levels and moderation roles have meaning only inside that forum.
    val contextual = surface != IdentitySurface.Profile
    if ((!contextual || (person.level == 0 && person.role.isBlank())) && badges.isEmpty()) return
    FlowRow(modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (contextual && person.level > 0) IdentityLabel(
            "Lv.${person.level}${person.levelName.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}",
            "吧内等级 ${person.level} 级 ${person.levelName}")
        if (contextual && person.role.isNotBlank()) IdentityLabel(person.role, person.role)
        badges.forEach { badge -> BadgeImage(badge) }
    }
}

@Composable
private fun IdentityLabel(label: String, description: String) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 5.dp, vertical = 2.dp).semantics { contentDescription = description })
}

@Composable
private fun BadgeImage(badge: UserBadge) {
    var failed by remember(badge.image) { mutableIntStateOf(0) }
    var ratio by remember(badge.image) { mutableFloatStateOf(1f) }
    Box(Modifier.height(20.dp).width((20 * ratio).dp).semantics {
        contentDescription = badge.label
        stateDescription = when (failed) { 1 -> "已加载"; 2 -> "图片加载失败"; else -> "正在加载" }
    }, contentAlignment = Alignment.Center) {
        if (failed == 2) Icon(Icons.Outlined.ImageNotSupported, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(badge.image, null, modifier = Modifier.fillMaxSize(), imageLoader = LocalAppImageLoader.current, contentScale = ContentScale.Fit,
            onSuccess = {
                val size = it.painter.intrinsicSize
                ratio = if (size.height > 0 && size.width.isFinite()) (size.width / size.height).coerceIn(.6f, 5f) else 1f
                failed = 1
            }, onError = { failed = 2 })
    }
}

@Composable
fun PostImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    var state by remember(url) { mutableIntStateOf(0) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant).semantics {
        contentDescription = "帖子图片，点击放大"
        stateDescription = when (state) { 1 -> "已加载"; 2 -> "加载失败，可打开大图重试"; else -> "正在加载" }
    }, contentAlignment = Alignment.Center) {
        if (state != 1) Icon(if (state == 2) Icons.Outlined.ImageNotSupported else Icons.Outlined.Image, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(url, null, modifier = Modifier.fillMaxSize(), imageLoader = LocalAppImageLoader.current, contentScale = contentScale,
            onSuccess = { state = 1 }, onError = { state = 2 })
    }
}

@Composable
fun ThreadCard(thread: ThreadItem, saved: Boolean, onOpen: () -> Unit, onSave: () -> Unit, onImage: (dev.hydrogen.tieba.data.ImageGallery) -> Unit,
    onLike: (() -> Unit)? = null, busy: Boolean = false, liking: Boolean = false,
    onForum: (() -> Unit)? = null, onAuthor: (() -> Unit)? = null, onCloud: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null, cloudSaving: Boolean = false,
    onHide: (() -> Unit)? = null, onBlockAuthor: (() -> Unit)? = null, onBlockForum: (() -> Unit)? = null) {
    val pack=LocalThemePack.current
    val shape=RoundedCornerShape((pack?.cornerRadius ?: 28).coerceAtLeast(24).dp)
    val context=androidx.compose.ui.platform.LocalContext.current
    val openVideo=LocalOpenVideo.current
    var menu by remember { mutableStateOf(false) }
    val interaction=remember { MutableInteractionSource() }
    // Match the two round actions to the count capsules even when font and
    // interface scales differ. Touch target size remains independent.
    val actionPadding=(with(LocalDensity.current) { MaterialTheme.typography.labelSmall.lineHeight.toDp() }
        .coerceAtLeast(18.dp)-4.dp)/2
    val share: () -> Unit = {
        context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type="text/plain"; putExtra(android.content.Intent.EXTRA_TEXT,"${thread.title} https://tieba.baidu.com/p/${thread.id}")
        },"分享帖子"))
    }
    Surface(onClick=onOpen,interactionSource=interaction,
        modifier=Modifier.padding(horizontal=8.dp,vertical=4.dp).pressMotion(interaction).postGlassBackground(shape),
        shape=shape, color=androidx.compose.ui.graphics.Color.Transparent,
        contentColor=MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.weight(1.2f).heightIn(min=LocalTouchTarget.current).clip(RoundedCornerShape(18.dp))
                    .then(if(onAuthor!=null) Modifier.clickable(onClick=onAuthor) else Modifier),verticalAlignment=Alignment.CenterVertically) {
                    PersonAvatar(thread.author,Modifier.size(34.dp))
                    Column(Modifier.weight(1f).padding(start=7.dp)) {
                        Text(thread.author.name,style=MaterialTheme.typography.labelLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
                        PersonBadges(thread.author,IdentitySurface.Feed)
                    }
                }
                if(thread.forum.isNotBlank()) Box(Modifier.weight(1f).heightIn(min=LocalTouchTarget.current)
                    .then(if(onForum!=null) Modifier.clickable(onClick=onForum) else Modifier),contentAlignment=Alignment.CenterEnd) {
                    Surface(shape=RoundedCornerShape(50),color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.78f)) {
                        Text("${thread.forum}吧",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(horizontal=10.dp,vertical=6.dp))
                    }
                }
                Box {
                    CompactBubbleButton(onClick={ menu=true }, shape=CircleShape, contentPadding=PaddingValues(8.dp)) { Icon(Icons.Outlined.MoreHoriz,"帖子更多操作",Modifier.size(18.dp)) }
                    BubbleDropdownMenu(menu,{ menu=false }) {
                        BubbleMenuItem(text={ Text(if(saved) "取消本地收藏" else "收藏到本机") },onClick={ menu=false; onSave() })
                        onCloud?.let { BubbleMenuItem(text={ Text("收藏到百度账号") },enabled=!busy,onClick={ menu=false; it() }) }
                        onAuthor?.let { BubbleMenuItem(text={ Text("查看作者主页") },onClick={ menu=false; it() }) }
                        BubbleMenuItem(text={ Text("分享帖子") },onClick={ menu=false; share() })
                        onRemove?.let { BubbleMenuItem(text={ Text("移出浏览历史") },onClick={ menu=false; it() }) }
                        onHide?.let { BubbleMenuItem(text={ Text("不感兴趣 · 本机隐藏") },onClick={ menu=false; it() }) }
                        onBlockAuthor?.let { BubbleMenuItem(text={ Text("本机屏蔽该作者") },onClick={ menu=false; it() }) }
                        onBlockForum?.let { BubbleMenuItem(text={ Text("本机屏蔽该吧") },onClick={ menu=false; it() }) }
                    }
                }
            }
            if(thread.pinned) Text("置顶",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
            Text(thread.title,style=MaterialTheme.typography.titleMedium.copy(fontSize=16.sp,lineHeight=22.sp),
                maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=3.dp))
            if(thread.excerpt.isNotBlank() && thread.excerpt.trim() != thread.title.trim()) Text(thread.excerpt,style=MaterialTheme.typography.bodyMedium.copy(lineHeight=20.sp),
                maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=3.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(thread.video.isNotBlank() || thread.videoCover.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                VideoAttachment(thread.video,thread.videoCover,thread.title,thread.id)
            } else if(thread.images.isNotEmpty()) {
                Spacer(Modifier.height(7.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                val count = thread.images.size.coerceAtMost(3)
                val imageWidth = (maxWidth - 4.dp * (count - 1)) / count
                val imageHeight = if (count == 1) (imageWidth / 2.15f).coerceAtMost(180.dp) else imageWidth.coerceAtMost(180.dp)
                Row(Modifier.fillMaxWidth().height(imageHeight), horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    val gallery = thread.galleryImages()
                    thread.images.take(3).forEach { url ->
                        val source=thread.imageSources.firstOrNull { url in listOf(it.preview,it.medium,it.high,it.original) } ?: ImageSource(url)
                        PostImage(source.url(LocalFeedImageQuality.current),contentScale=ContentScale.Crop,
                            modifier=Modifier.weight(1f).fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp)).clickable { onImage(ImageGallery(gallery, gallery.indexOf(source).coerceAtLeast(0), threadId=thread.id)) })
                    }
                }
                }
                if(thread.mediaCount>3) Text("共 ${thread.mediaCount} 张图片",style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=3.dp))
            }
            thread.poll?.let { poll ->
                Surface(shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth().padding(top=6.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text(poll.title.ifBlank { "投票" },style=MaterialTheme.typography.labelLarge)
                        poll.options.take(2).forEach { Text("· $it",style=MaterialTheme.typography.bodySmall) }
                        Text("打开帖子查看投票",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(listOf(relativeTime(thread.timestamp),thread.views?.let { "${compactNumber(it)} 阅读" }).filterNotNull().filter(String::isNotBlank).joinToString(" · "),
                style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=7.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                CompactBubbleButton(onClick=onLike ?: onOpen,enabled=!busy,selected=thread.liked,fill=true,
                    contentPadding=PaddingValues(horizontal=5.dp,vertical=4.dp),modifier=Modifier.weight(1f)
                        .semantics { stateDescription=if(thread.liked) "已点赞" else "未点赞" }) {
                    if(liking) AppCircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
                    else Icon(if(thread.liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,if(thread.liked) "取消点赞" else "点赞",Modifier.size(18.dp))
                    Text(if(thread.likesKnown) compactNumber(thread.likes.toLong()) else "—",style=MaterialTheme.typography.labelSmall,
                        maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(start=5.dp))
                }
                CompactBubbleButton(onClick=onCloud ?: onOpen,enabled=!busy,fill=true,
                    contentPadding=PaddingValues(horizontal=5.dp,vertical=4.dp),modifier=Modifier.weight(1f)) {
                    if(cloudSaving) AppCircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
                    else Icon(Icons.Outlined.StarOutline,thread.collections?.let { "收藏到百度账号，收藏数${compactNumber(it)}" } ?: "收藏到百度账号",Modifier.size(18.dp))
                    thread.collections?.let { count ->
                        Text(compactNumber(count),style=MaterialTheme.typography.labelSmall,
                            maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(start=5.dp))
                    }
                }
                CompactBubbleButton(onClick=onOpen,fill=true,contentPadding=PaddingValues(horizontal=5.dp,vertical=4.dp),modifier=Modifier.weight(1f)) {
                    Icon(Icons.Outlined.ChatBubbleOutline,"评论数，打开帖子",Modifier.size(18.dp))
                    Text(if(thread.repliesKnown) compactNumber(thread.replies.toLong()) else "—",style=MaterialTheme.typography.labelSmall,
                        maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(start=5.dp))
                }
                CompactBubbleButton(onClick=onSave,selected=saved,shape=CircleShape,contentPadding=PaddingValues(actionPadding),
                    modifier=Modifier.semantics { stateDescription=if(saved) "已本地收藏" else "未本地收藏" }) {
                    Icon(if(saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,if(saved) "取消本地收藏" else "收藏到本机",Modifier.size(18.dp))
                }
                CompactBubbleButton(onClick=share,shape=CircleShape,contentPadding=PaddingValues(actionPadding)) {
                    Icon(Icons.Outlined.Share,"转发帖子",Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun Metric(icon: ImageVector, value: String, label: String) {
    val editorial = LocalVisualStyle.current == VisualStyle.Margin
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Icon(icon, label, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(5.dp))
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = if (editorial && LocalAppFont.current == AppFont.Theme) FontFamily.Monospace else MaterialTheme.typography.bodySmall.fontFamily), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(title: String, description: String, icon: ImageVector = Icons.Outlined.Forum, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(66.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(29.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = 20.dp).heightIn(min = LocalTouchTarget.current)) { Text(action) }
    }
}

@Composable
fun LoadingRow(label: String = "正在加载") {
    Row(Modifier.fillMaxWidth().padding(28.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        AppCircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PaginationFooter(loading: Boolean, error: String?, hasMore: Boolean, onNext: () -> Unit) {
    when {
        loading -> LoadingRow("正在加载更多")
        error != null -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            BubbleTextButton(onClick = onNext, modifier = Modifier.heightIn(min = LocalTouchTarget.current)) { Text("重新加载") }
        }
        hasMore -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BubbleTextButton(onClick = onNext, modifier = Modifier.heightIn(min = LocalTouchTarget.current)) { Text("加载更多") }
        }
        else -> Text("先看到这里", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

fun compactNumber(value: Long): String = if (value >= 10000) String.format(Locale.CHINA, "%.1f万", value / 10000.0) else value.toString()
fun relativeTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val seconds = ((System.currentTimeMillis() / 1000) - timestamp).coerceAtLeast(0)
    return when {
        seconds < 60 -> "刚刚"
        seconds < 3600 -> "${seconds / 60}分钟前"
        seconds < 86400 -> "${seconds / 3600}小时前"
        seconds < 604800 -> "${seconds / 86400}天前"
        else -> SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(timestamp * 1000))
    }
}
