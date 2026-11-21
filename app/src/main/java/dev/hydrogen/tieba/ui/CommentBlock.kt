package dev.hydrogen.tieba.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.FilterQuality
import coil3.request.ImageRequest
import coil3.size.Size
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hydrogen.tieba.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
internal fun CommentBlock(post: PostItem, authorId: String, largeText: Boolean, onImage: (ImageGallery) -> Unit,
    onThreadLink: (String) -> Unit, onDiscussion: () -> Unit, nested: Boolean = false,
    onLike: () -> Unit = {}, onReply: () -> Unit = {}, busy: Boolean = false, liking: Boolean = false,
    onAuthor: (() -> Unit)? = null, onUser: (Person) -> Unit = {}, videoThreadId: String = "") {
    var menu by remember(post.id) { mutableStateOf(false) }
    var info by remember(post.id) { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val root = !nested && post.floor == 1
    val line = MaterialTheme.colorScheme.outlineVariant
    Box(if (nested) Modifier else Modifier.fillMaxWidth().padding(vertical = 4.dp).postGlassBackground()) {
    Row(Modifier.fillMaxWidth().then(if (nested) Modifier.padding(start = 14.dp).drawBehind {
        drawLine(line, Offset(2.dp.toPx(), 0f), Offset(2.dp.toPx(), size.height), 1.dp.toPx())
    } else Modifier).padding(horizontal = 6.dp, vertical = 6.dp)) {
        Box(Modifier.size(LocalTouchTarget.current).clip(androidx.compose.foundation.shape.CircleShape).then(if (onAuthor != null) Modifier.clickable(onClick = onAuthor) else Modifier),
            contentAlignment = Alignment.TopCenter) { PersonAvatar(post.author, Modifier.padding(top = 8.dp).size(if (nested) 26.dp else 32.dp)) }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).heightIn(min = LocalTouchTarget.current).clip(RoundedCornerShape(16.dp)).padding(horizontal = 4.dp)
                    .then(if (onAuthor != null) Modifier.clickable(onClick = onAuthor) else Modifier).wrapContentHeight()) {
                    Text(post.author.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    PersonBadges(post.author, IdentitySurface.Post)
                }
                if (root || (authorId.isNotBlank() && post.author.id == authorId)) Text("楼主",
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 5.dp))
                if (!nested) post.floorLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Box {
                    CompactBubbleButton(onClick = { menu = true }, shape = androidx.compose.foundation.shape.CircleShape,
                        contentPadding = PaddingValues(7.dp)) { Icon(Icons.Outlined.MoreHoriz,
                        if (nested || post.floorLabel == null) "${post.author.name}的回复更多操作" else "${post.floorLabel}更多操作", Modifier.size(18.dp)) }
                    BubbleDropdownMenu(menu, { menu = false }) {
                        BubbleMenuItem(text = { Text("复制正文") }, onClick = {
                            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("评论正文", post.copyableText()))) }; menu = false
                        })
                        BubbleMenuItem(text = { Text("查看发布时间与属地") }, onClick = { menu = false; info = true })
                    }
                }
            }
            PostBody(post, root, nested, largeText, onImage, onThreadLink, onUser, videoThreadId)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(listOf(relativeTime(post.timestamp), post.location).filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 4.dp))
                CompactBubbleButton(onClick = onReply, enabled = !busy) { Text("回复", style = MaterialTheme.typography.labelMedium) }
                CompactBubbleButton(onClick = onLike, enabled = !busy, selected = post.liked) {
                    if (liking) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                    else Icon(if (post.liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        if (post.liked) "取消点赞" else "点赞", Modifier.size(16.dp),
                        tint = if (post.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (post.likes > 0) Text(compactNumber(post.likes.toLong()), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (!nested && post.subReplies > 0) {
                Surface(onClick = onDiscussion, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .66f),
                    shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                        post.previewReplies.take(2).forEach { reply ->
                            Text(buildAnnotatedString {
                                pushStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)); append(reply.author.name); pop()
                                append("：${reply.copyableText()}")
                            }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = (15 + if (largeText) 3 else 0).sp),
                                modifier = Modifier.padding(top = 4.dp))
                        }
                        Box(Modifier.heightIn(min = LocalTouchTarget.current).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            Text("共 ${post.subReplies} 条回复", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    }
    if (info) AlertDialog(onDismissRequest = { info = false }, title = { Text("评论信息") }, text = {
        SelectionContainer { Text("发布时间：${exactPostTime(post.timestamp)}\nIP 属地：${post.location.ifBlank { "贴吧未提供" }}\n回复 ID：${post.id}") }
    }, confirmButton = { BubbleTextButton(onClick = { info = false }) { Text("关闭") } })
}

internal fun exactPostTime(seconds: Long): String = if (seconds <= 0) "贴吧未提供" else runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(seconds))
}.getOrDefault("贴吧未提供")

@Composable
private fun PostBody(post: PostItem, root: Boolean, nested: Boolean, largeText: Boolean, onImage: (ImageGallery) -> Unit,
    onThreadLink: (String) -> Unit, onUser: (Person) -> Unit, videoThreadId: String) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val fontSize = (if (root) 16 else 15) + if (largeText) 3 else 0
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize.sp, lineHeight = (fontSize + 6).sp)
    val sources = remember(post.content) { post.content.filter { it.image.isNotBlank() }.map { it.imageSource ?: ImageSource(it.image) } }
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
    var index = 0
    while (index < post.content.size) {
        val part = post.content[index]
        when {
            part.isVideo || part.video.isNotBlank() -> { VideoAttachment(part.video, part.videoCover, threadId = videoThreadId); index++ }
            part.image.isNotBlank() -> {
                val begin = index
                while (index < post.content.size && post.content[index].image.isNotBlank()) index++
                val parts = post.content.subList(begin, index)
                FlowRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    parts.forEach { image ->
                        val source = image.imageSource ?: ImageSource(image.image)
                        PostImage(source.url(ImageQuality.Medium), contentScale = if (root) ContentScale.Fit else ContentScale.Crop,
                            modifier = (if (root) Modifier.fillMaxWidth().aspectRatio(image.ratio) else Modifier.width(108.dp).aspectRatio(1f))
                                .clip(RoundedCornerShape(9.dp)).clickable { onImage(ImageGallery(sources, sources.indexOf(source).coerceAtLeast(0))) })
                    }
                }
            }
            else -> {
                val begin = index
                while (index < post.content.size && post.content[index].image.isBlank() && !post.content[index].isVideo && post.content[index].video.isBlank()) index++
                val parts = post.content.subList(begin, index)
                val inline = parts.mapIndexedNotNull { offset, p ->
                    if (p.emoji.isBlank()) null else "emoji-$begin-$offset" to InlineTextContent(
                        Placeholder((fontSize * 1.12f).sp, (fontSize * 1.12f).sp, PlaceholderVerticalAlign.TextCenter)) {
                        SubcomposeAsyncImage(remember(p.emoji) { ImageRequest.Builder(context).data(p.emoji).size(Size.ORIGINAL).build() },
                            contentDescription = p.text, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.High) {
                            when (painter.state.collectAsState().value) {
                                is coil3.compose.AsyncImagePainter.State.Error -> Text(p.text, fontSize = (fontSize * .6f).sp, maxLines = 1)
                                else -> SubcomposeAsyncImageContent()
                            }
                        }
                    }
                }.toMap()
                val text = buildAnnotatedString {
                    parts.forEachIndexed { offset, p ->
                        when {
                            p.emoji.isNotBlank() -> appendInlineContent("emoji-$begin-$offset", p.text)
                            p.mention != null -> withLink(LinkAnnotation.Clickable("user-$begin-$offset", linkStyle) { onUser(p.mention) }) { append(p.text) }
                            p.link.isNotBlank() -> withLink(LinkAnnotation.Clickable("link-$begin-$offset", linkStyle) {
                                if (Regex("https://tieba\\.baidu\\.com/p/\\d+").containsMatchIn(p.link)) onThreadLink(p.link)
                                else if (p.link.startsWith("https://")) runCatching { uriHandler.openUri(p.link) }
                            }) { append(p.text.ifBlank { p.link }) }
                            else -> append(p.text)
                        }
                    }
                }
                if (text.isNotBlank()) SelectionContainer { Text(text, inlineContent = inline, style = style, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }
    }
}
