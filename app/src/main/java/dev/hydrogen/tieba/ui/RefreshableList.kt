package dev.hydrogen.tieba.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

/** Native nested scrolling: a pull is consumed only after the child reaches its top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableList(refreshing: Boolean, onRefresh: () -> Unit, hasContent: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    val state = rememberPullToRefreshState()
    val showRefresh = refreshing && hasContent
    PullToRefreshBox(
        isRefreshing = showRefresh,
        onRefresh = { if (!refreshing) onRefresh() },
        state = state,
        indicator = {
            // An empty list already owns its loading row. Keep pull feedback when idle,
            // but never display another spinner over that initial loading state.
            if (hasContent || !refreshing) PullToRefreshDefaults.Indicator(
                state = state, isRefreshing = showRefresh, modifier = Modifier.align(Alignment.TopCenter))
        },
        modifier = Modifier.fillMaxSize(),
        content = content,
    )
}

@Composable
fun RefreshFailure(message: String, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("更新失败，已保留当前内容。$message", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        BubbleTextButton(onClick = onRetry) { Text("重试") }
    }
}
