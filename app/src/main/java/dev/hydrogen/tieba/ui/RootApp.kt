package dev.hydrogen.tieba.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import dev.hydrogen.tieba.OfficialActivity
import dev.hydrogen.tieba.ui.theme.HydrogenTheme

@Composable
fun HydrogenApp(model: TiebaViewModel) {
    val systemDensity = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(systemDensity.density * model.interfaceScale,
        systemDensity.fontScale * model.textScale / model.interfaceScale),
        LocalMinimumInteractiveComponentSize provides (48f / model.interfaceScale).dp,
        LocalTouchTarget provides (48f / model.interfaceScale).dp) {
    HydrogenTheme(model.theme, model.accentColor, model.visualStyle, model.customTheme, model.appFont) {
        val context = LocalContext.current
        val dark = MaterialTheme.colorScheme.background.luminance() < .5f
        var systemAnimations by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f) }
        DisposableEffect(context) {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    systemAnimations = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
                }
            }
            context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
            onDispose { context.contentResolver.unregisterContentObserver(observer) }
        }
        val motion = model.richEffects && !model.reduceMotion && systemAnimations
        val layer = rememberGraphicsLayer()
        val scene = remember(layer) { GlassScene(layer) }
        CompositionLocalProvider(LocalRichEffects provides model.richEffects, LocalGlassScene provides scene, LocalMotionEnabled provides motion, LocalOpenVideo provides model::previewVideo) {
            LaunchedEffect(model.webUrl) {
                model.webUrl?.let { url ->
                    context.startActivity(Intent(context, OfficialActivity::class.java).putExtra("url", url).putExtra("dark", dark))
                    model.webUrl = null
                }
            }
            SideEffect {
                (context as? Activity)?.let {
                    WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                        isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
                    }
                }
            }
            val snack = remember { SnackbarHostState() }
            LaunchedEffect(Unit) { model.messages.collect { snack.showSnackbar(it) } }
            var image by remember { mutableStateOf<dev.hydrogen.tieba.data.ImageGallery?>(null) }
            val feedScroll = rememberLazyListState()
            val followingScroll = rememberLazyListState()
            val discoveryScroll = rememberLazyListState()
            val holder = rememberSaveableStateHolder()
            var navigationVisible by remember { mutableStateOf(true) }
            var scrollDistance by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(model.account) { model.chats.bind(model.account) }
            val route = when {
                model.reading != null -> "thread:${model.reading!!.id}"
                model.settingsOpen -> "settings"
                model.panel != null -> "panel:${model.panel!!.key}"
                model.collection != null -> "collection:${model.collection}"
                model.forumOpen -> "forum:${model.forum}"
                model.tab == HomeTab.Messages && model.chats.selected != null -> "chat:${model.chats.selected}"
                else -> "tab:${model.tab.name}"
            }
            LaunchedEffect(route) { navigationVisible = true; scrollDistance = 0f }
            val autoHide = model.autoHideNavigation
            val browsingChromeVisible = route == "tab:Profile" || !autoHide || navigationVisible
            val hideDistance = with(LocalDensity.current) { 38.dp.toPx() }
            val restoreDistance = with(LocalDensity.current) { 20.dp.toPx() }
            val scrollConnection = remember(autoHide, route, hideDistance, restoreDistance) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (autoHide && source == NestedScrollSource.UserInput) {
                            if (scrollDistance * available.y < 0) scrollDistance = 0f
                            scrollDistance += available.y
                            if (scrollDistance < -hideDistance) navigationVisible = false
                            if (scrollDistance > restoreDistance) navigationVisible = true
                        }
                        return Offset.Zero
                    }
                }
            }
            BackHandler(enabled = model.reading != null || model.settingsOpen || model.collection != null || model.panel != null || model.forumOpen || model.tab != HomeTab.Feed) {
                when {
                    model.reading != null -> model.closeThread()
                    model.settingsOpen -> model.settingsOpen = false
                    model.panel != null -> model.closePanel()
                    model.collection != null -> model.collection = null
                    model.forumOpen -> model.closeForum()
                    model.chats.selected != null && model.tab == HomeTab.Messages -> model.closeNativeChat()
                    else -> model.navigateTab(HomeTab.Feed)
                }
            }
            Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snack) }, bottomBar = {
                val reading = model.reading
                val summary = reading?.summary
                if (summary != null) {
                    ReadingActions(summary, model.bookmarks.any { it.id == reading.id },
                        onSave = { model.toggleBookmark(summary) }, onReply = { model.openComposer() },
                        onLike = { model.like(summary) }, busy = model.pending != null, liking = model.pending == "like:${reading.id}:")
                } else {
                    AnimatedVisibility(visible = route.startsWith("tab:") && browsingChromeVisible,
                        enter = fadeIn(tween(if (motion) 140 else 0)) + slideInVertically(tween(if (motion) 220 else 0, easing = HydrogenMotion.EaseOut)) { it },
                        exit = fadeOut(tween(if (motion) 120 else 0)) + slideOutVertically(tween(if (motion) 180 else 0, easing = HydrogenMotion.EaseOut)) { it }) {
                        GlassNavigation(model)
                    }
                }
            }) { padding ->
                val modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
                    .then(if (model.richEffects && !route.startsWith("chat:")) Modifier.captureGlassScene(scene) else Modifier)
                    .themeCanvas().nestedScroll(scrollConnection)
                val target=PageSnapshot(route,model.reading,model.panel,model.forum,model.feed,model.forumSort)
                val pageTransition = updateTransition(target, label = "页面路由")
                pageTransition.AnimatedContent(contentKey={ it.route },modifier=modifier,
                    transitionSpec={
                        val primary=initialState.route.startsWith("tab:") && targetState.route.startsWith("tab:")
                        val duration=if(primary) HydrogenMotion.TabDuration else HydrogenMotion.PageDuration
                        if(!motion) EnterTransition.None togetherWith ExitTransition.None
                        else if(primary) {
                            val direction = tabDirection(initialState.route, targetState.route)
                            (fadeIn(tween(duration, easing = HydrogenMotion.EaseOut)) +
                                slideInHorizontally(tween(duration, easing = HydrogenMotion.EaseOut)) { direction * (it / 10) }) togetherWith
                                (fadeOut(tween(duration, easing = HydrogenMotion.EaseOut)) +
                                    slideOutHorizontally(tween(duration, easing = HydrogenMotion.EaseOut)) { -direction * (it / 10) })
                        }
                        else {
                            val direction=if(pageDepth(targetState.route)>=pageDepth(initialState.route)) 1 else -1
                            (fadeIn(tween(duration,easing=HydrogenMotion.EaseOut)) + slideInHorizontally(tween(duration,easing=HydrogenMotion.EaseOut)) { direction * (it / 18) }) togetherWith
                                (fadeOut(tween(HydrogenMotion.ExitDuration)) + slideOutHorizontally(tween(HydrogenMotion.ExitDuration,easing=HydrogenMotion.EaseOut)) { -direction * (it / 24) })
                        }.using(SizeTransform(clip=false,sizeAnimationSpec={ _,_ -> tween(0) }))
                    }) { snapshot ->
                    val destination=snapshot.route
                    holder.SaveableStateProvider(destination) {
                        val camera = motion && pageTransition.currentState.route.startsWith("tab:") &&
                            pageTransition.targetState.route.startsWith("tab:")
                        val screen = Modifier.fillMaxSize().then(cameraMaterial(camera, MaterialTheme.colorScheme.background))
                        when {
                            destination.startsWith("thread:") -> {
                                val id = destination.substringAfter(':')
                                val reading = snapshot.reading?.takeIf { it.id == id }
                                if (reading != null) ThreadScreen(model, screen, onImage = { image = it }, state = reading)
                            }
                            destination == "settings" -> SettingsScreen(model, screen)
                            destination.startsWith("collection:") -> CollectionScreen(model, destination.substringAfter(':'), screen, onImage = { image = it })
                            destination.startsWith("panel:") -> CommunityScreen(model, screen, onImage = { image = it }, state = snapshot.panel)
                            destination.startsWith("forum:") -> ForumScreen(model, screen, rememberLazyListState(), onSearch = { model.openPanel(PanelKind.Search) }, onImage = { image = it }, feed = snapshot.feed, forumName = snapshot.forum, sort = snapshot.sort, autoHideHeader = autoHide)
                            destination.startsWith("chat:") -> NativeConversation(model, screen, onImage = { image = it })
                            destination == "tab:Feed" -> StreamScreen(model, screen, if (model.followingSelected) followingScroll else feedScroll, onSearch = { model.openPanel(PanelKind.Search) }, onImage = { image = it }, autoHideHeader = autoHide)
                            destination == "tab:Browse" -> DiscoverScreen(model, screen, discoveryScroll, onSearch = { model.openPanel(PanelKind.Search) }, autoHideHeader = autoHide)
                            destination == "tab:Messages" -> MessagesScreen(model, screen)
                            else -> ProfileScreen(model, screen)
                        }
                    }
                }
            }
            if (model.floor != null) { FloorSheet(model, onImage = { image = it }) }
            if (model.composer != null) { CommentComposer(model) }
            if (model.forumInfoOpen) { ForumInfoSheet(model) }
            image?.let { ImageViewer(it, model.imageQuality, resolveSources = model::resolveImageGallery) { image = null } }
            if (model.video != null) { VideoPreview(model) }
            model.interactionError?.let { error -> AlertDialog(onDismissRequest = model::dismissInteractionError,
                title = { Text("操作未完成") }, text = { Text(error) },
                confirmButton = { BubbleTextButton(onClick = model::dismissInteractionError) { Text("知道了") } }) }
        }
    }
    }
}

private data class PageSnapshot(val route: String, val reading: ReadingState?, val panel: PanelState?,
    val forum: String, val feed: FeedState, val sort: dev.hydrogen.tieba.data.ForumSort)
private fun pageDepth(route: String) = when { route.startsWith("thread:") -> 3; route.startsWith("panel:") -> 2; route.startsWith("forum:") -> 1; route=="settings" || route.startsWith("collection:") -> 1; else -> 0 }
