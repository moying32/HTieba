package dev.hydrogen.tieba.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ContentFilterSettings(model: TiebaViewModel, onBack: () -> Unit) {
    val filters = model.contentFilters
    Column(Modifier.fillMaxSize()) {
        ScreenHeading("本机内容过滤", onBack)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
            item {
                Text("仅过滤这台设备的推荐、关注和吧内帖子列表；直接链接、收藏与历史仍可查看。不会修改百度云黑名单或向推荐接口提交不感兴趣。",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SectionLabel("已隐藏帖子 · ${filters.threads.size}")
                BubbleOutlinedButton(onClick = model::restoreHiddenThreads, enabled = filters.threads.isNotEmpty()) { Text("恢复全部隐藏帖子") }
                SectionLabel("已屏蔽作者 · ${filters.users.size}")
            }
            items(filters.users.entries.toList(), key = { "user:${it.key}" }) { (id, name) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        Text("UID $id", style = MaterialTheme.typography.labelSmall)
                    }
                    BubbleTextButton(onClick = { model.unblockAuthor(id) }) { Text("取消屏蔽") }
                }
            }
            item { SectionLabel("已屏蔽贴吧 · ${filters.forums.size}") }
            items(filters.forums.toList(), key = { "forum:$it" }) { name ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${name}吧", Modifier.weight(1f).padding(vertical = 14.dp))
                    BubbleTextButton(onClick = { model.unblockForum(name) }) { Text("取消屏蔽") }
                }
            }
        }
    }
}
