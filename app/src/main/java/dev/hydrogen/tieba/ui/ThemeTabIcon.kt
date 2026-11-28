package dev.hydrogen.tieba.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.hydrogen.tieba.data.VisualStyle
import dev.hydrogen.tieba.ui.theme.LocalVisualStyle

@Composable
internal fun ThemeTabIcon(tab: HomeTab, selected: Boolean, modifier: Modifier, tint: Color) {
    val style = LocalVisualStyle.current
    val vector = when {
        style == VisualStyle.Claude -> when (tab) {
            HomeTab.Feed -> Icons.Rounded.Home
            HomeTab.Browse -> Icons.Rounded.Explore
            HomeTab.Compose -> Icons.Rounded.Add
            HomeTab.Messages -> Icons.Rounded.ChatBubbleOutline
            HomeTab.Profile -> Icons.Rounded.PersonOutline
        }
        style == VisualStyle.Liquid && selected -> when (tab) {
            HomeTab.Feed -> Icons.Filled.Home
            HomeTab.Browse -> Icons.Filled.Explore
            HomeTab.Compose -> Icons.Filled.Add
            HomeTab.Messages -> Icons.Filled.ChatBubble
            HomeTab.Profile -> Icons.Filled.Person
        }
        else -> when (tab) {
            HomeTab.Feed -> Icons.Outlined.Home
            HomeTab.Browse -> Icons.Outlined.Explore
            HomeTab.Compose -> Icons.Outlined.Add
            HomeTab.Messages -> Icons.Outlined.ChatBubbleOutline
            HomeTab.Profile -> Icons.Outlined.PersonOutline
        }
    }
    ThemeIcon(vector, contentDescription = null, modifier = modifier, tint = tint)
}
