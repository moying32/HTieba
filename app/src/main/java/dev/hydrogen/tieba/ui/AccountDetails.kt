package dev.hydrogen.tieba.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hydrogen.tieba.data.IdentitySurface

@Composable
internal fun AccountDetails(model: TiebaViewModel) {
    val account = model.account
    val profile = account.profile
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonAvatar(account.identity.copy(name = account.name.ifBlank { "未登录" }, avatar = account.avatar), Modifier.size(60.dp))
            Column(Modifier.weight(1f)) {
                Text(account.name.ifBlank { "未登录百度账号" }, style = MaterialTheme.typography.titleMedium)
                Text(if (account.loggedIn) "百度贴吧 · 已连接" else "登录后查看账号资料", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                PersonBadges(account.identity, IdentitySurface.Profile)
            }
        }
        if (account.loggedIn) {
            val fields = listOf(
                "用户 ID" to account.uid,
                "昵称" to account.identity.name,
                "性别" to when (profile?.gender) { 1 -> "男"; 2 -> "女"; else -> "未提供" },
                "吧龄" to profile?.age.orEmpty(),
                "IP 属地" to profile?.location.orEmpty(),
                "个人简介" to profile?.introduction.orEmpty(),
                "关注" to profile?.follows?.let(::compactNumber).orEmpty(),
                "粉丝" to profile?.fans?.let(::compactNumber).orEmpty(),
                "主题帖" to profile?.threads?.let(::compactNumber).orEmpty(),
                "回帖" to profile?.posts?.let(::compactNumber).orEmpty(),
            )
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                SelectionContainer {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        fields.forEach { (label, value) ->
                            Column {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value.ifBlank { "贴吧未提供" }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            account.profileError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            BubbleOutlinedButton(onClick = { model.settingsOpen = false; model.openPanel(PanelKind.EditProfile) }) { Text("编辑贴吧资料") }
        }
    }
}
