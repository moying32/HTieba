package dev.hydrogen.tieba.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.hydrogen.tieba.R
import dev.hydrogen.tieba.data.VideoSource
import kotlinx.coroutines.delay

@Composable
fun VideoAttachment(url: String, cover: String = "", title: String = "贴吧视频", threadId: String = "") {
    val open=LocalOpenVideo.current
    Surface(onClick={ open(VideoSource(url,cover,title,threadId)) },shape=RoundedCornerShape(12.dp),
        color=Color(0xFF101216),modifier=Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(12.dp))) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            if(cover.isNotBlank()) PostImage(cover,Modifier.fillMaxSize())
            // Do not sample the unrelated root page behind a thumbnail.
            CompositionLocalProvider(LocalGlassScene provides null) {
                MaterialTheme(colorScheme=videoColors()) {
                    GlassPanel(liquid=true) {
                        Row(Modifier.padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PlayArrow,null,Modifier.size(26.dp),tint=Color.White)
                            Text("播放视频",style=MaterialTheme.typography.labelLarge,color=Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun videoColors() = darkColorScheme(primary=Color.White,onPrimary=Color.Black,
    surface=Color(0xFF181C23),onSurface=Color.White,background=Color.Black,onBackground=Color.White,outline=Color(0xFFADB6C2))

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VideoPreview(model: TiebaViewModel) {
    val source=model.video ?: return
    val context=LocalContext.current
    // A Dialog installs its own density. Re-provide the app's interface/font scale inside it.
    val interfaceDensity=LocalDensity.current
    val lifecycle=LocalLifecycleOwner.current
    val player=remember(source.url) { if(source.url.isBlank()) null else ExoPlayer.Builder(context.applicationContext).build().apply {
        setAudioAttributes(AudioAttributes.DEFAULT,true); setHandleAudioBecomingNoisy(true)
        setMediaItem(MediaItem.fromUri(source.url)); prepare(); playWhenReady=true
    } }
    var playing by remember(player) { mutableStateOf(false) }
    var buffering by remember(player) { mutableStateOf(player!=null) }
    var ended by remember(player) { mutableStateOf(false) }
    var error by remember(player) { mutableStateOf<String?>(null) }
    var position by remember(player) { mutableLongStateOf(0) }
    var duration by remember(player) { mutableLongStateOf(0) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    var muted by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var speedMenu by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    val holdSpeed=remember(player) { VideoHoldSpeed() }
    var boosting by remember(player) { mutableStateOf(false) }
    var multipleTouches by remember(player) { mutableStateOf(false) }
    var controlFocused by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableIntStateOf(0) }
    val revealControls: () -> Unit = { controls=true; interactionTick++ }
    val finishHold: () -> Unit = {
        holdSpeed.finish()?.let { previous ->
            player?.setPlaybackSpeed(previous)
            boosting=false
            revealControls()
        }
    }
    val latestFinishHold by rememberUpdatedState(finishHold)
    val activity=context as? Activity
    val originalOrientation=remember { activity?.requestedOrientation }
    val screenOrientation=LocalConfiguration.current.orientation
    val originalScreen=remember { screenOrientation }
    val landscape=screenOrientation==Configuration.ORIENTATION_LANDSCAPE
    val accessibility=remember { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var touchExploration by remember { mutableStateOf(accessibility.isTouchExplorationEnabled) }
    DisposableEffect(accessibility) {
        val listener=AccessibilityManager.TouchExplorationStateChangeListener { touchExploration=it }
        accessibility.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility.removeTouchExplorationStateChangeListener(listener) }
    }
    DisposableEffect(Unit) {
        onDispose {
            if(activity!=null && originalOrientation!=null) {
                // Rotation-locked devices may retain the forced video rotation. Restore the
                // starting direction first, then hand orientation back to the user's policy.
                val restored=if(originalScreen==Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                activity.requestedOrientation=restored
                activity.window.decorView.postDelayed({
                    if(activity.requestedOrientation==restored) activity.requestedOrientation=originalOrientation
                },250)
            }
        }
    }
    DisposableEffect(player,lifecycle) {
        val listener=object: Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                playing=value
                position=player?.currentPosition?.coerceAtLeast(0) ?: 0
                if(!value) { latestFinishHold(); controls=true }
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,newPosition: Player.PositionInfo,reason: Int) {
                position=newPosition.positionMs.coerceAtLeast(0)
            }
            override fun onPlaybackStateChanged(state: Int) { buffering=state==Player.STATE_BUFFERING; ended=state==Player.STATE_ENDED }
            override fun onPlayerError(e: PlaybackException) {
                latestFinishHold(); error="视频暂时无法播放（${e.errorCodeName}）"; buffering=false; controls=true
            }
        }
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_PAUSE) {
            latestFinishHold(); speedMenu=false; player?.pause()
        } }
        player?.addListener(listener); lifecycle.lifecycle.addObserver(observer)
        onDispose { latestFinishHold(); player?.removeListener(listener); player?.release(); lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(player,muted,speed) {
        player?.volume=if(muted) 0f else 1f
        if(!holdSpeed.active) player?.setPlaybackSpeed(speed)
    }
    LaunchedEffect(player) { while(player!=null) { position=player.currentPosition.coerceAtLeast(0); duration=player.duration.coerceAtLeast(0); delay(250) } }
    LaunchedEffect(playing,controls,seeking,speedMenu,touchExploration,error,model.videoError,
        buffering,model.videoLoading,boosting,interactionTick,controlFocused) {
        if(controls && VideoControlsPolicy.canAutoHide(playing,buffering || model.videoLoading,
            seeking!=null,speedMenu,touchExploration,boosting,error!=null || model.videoError!=null,controlFocused)) {
            delay(VideoControlsPolicy.AutoHideDelay); controls=false
        }
    }
    val togglePlayback: () -> Unit = {
        latestFinishHold(); revealControls()
        player?.let { if(ended) { it.seekTo(0); it.play() } else if(it.isPlaying) it.pause() else it.play() }
    }
    val latestTogglePlayback by rememberUpdatedState(togglePlayback)
    val latestToggleControls by rememberUpdatedState<() -> Unit> { controls=!controls; interactionTick++ }
    val latestBeginHold by rememberUpdatedState<() -> Unit> {
        if(player!=null && !multipleTouches && !touchExploration && error==null && model.videoError==null && !model.videoLoading) {
            holdSpeed.begin(player.isPlaying,player.playbackParameters.speed)?.let { rate ->
                boosting=true; controls=false; speedMenu=false; player.setPlaybackSpeed(rate)
            }
        }
    }
    Dialog(onDismissRequest=model::closeVideo,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val view=LocalView.current
        DisposableEffect(view) {
            val window=(view.parent as? DialogWindowProvider)?.window
            val controller=window?.let { WindowCompat.getInsetsController(it,view) }
            controller?.apply {
                isAppearanceLightStatusBars=false; isAppearanceLightNavigationBars=false
                systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
        }
        CompositionLocalProvider(LocalDensity provides interfaceDensity) {
        MaterialTheme(colorScheme=videoColors()) {
            val layer=rememberGraphicsLayer()
            val scene=remember(layer) { GlassScene(layer) }
            CompositionLocalProvider(LocalGlassScene provides scene) {
                BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
                    val target=maxOf(48.dp,LocalTouchTarget.current)
                    val preferredWidth=if(landscape) minOf(maxWidth*.64f,560.dp) else maxWidth*.88f
                    // At the largest interface scale, widen only enough for
                    // four genuine touch targets and the speed label.
                    val dockWidth=maxOf(preferredWidth,target*3+maxOf(target,64.dp)+16.dp).coerceAtMost(maxWidth)
                    Box(Modifier.fillMaxSize().then(if (LocalRichEffects.current) Modifier.captureGlassScene(scene) else Modifier)) {
                        if(player!=null) AndroidView(
                            factory={ LayoutInflater.from(it).inflate(R.layout.video_player,null,false) as PlayerView },
                            update={ it.player=player },modifier=Modifier.fillMaxSize(),onRelease={ it.player=null })
                    }
                    val showControls=(controls || !playing || touchExploration) && !boosting
                    Box(Modifier.fillMaxSize()
                        .semantics {
                            role=Role.Button
                            contentDescription="视频画面，播放时长按左右两侧临时2倍速，松手恢复"
                            onClick(if(showControls) "隐藏播放控件" else "显示播放控件") { latestToggleControls(); true }
                            customActions=listOf(CustomAccessibilityAction("播放或暂停") { latestTogglePlayback(); true })
                        }.onKeyEvent { event ->
                            if(event.type==KeyEventType.KeyUp && (event.key==Key.Spacebar || event.key==Key.Enter)) {
                                latestTogglePlayback(); true
                            } else false
                        }.focusable()
                        .pointerInput(player,touchExploration) {
                            detectTapGestures(onTap={ latestToggleControls() },
                                onLongPress=if(touchExploration) null else { point ->
                                    if(VideoControlsPolicy.isHoldEdge(point.x,size.width.toFloat())) latestBeginHold()
                                },onPress={
                                    try { tryAwaitRelease() } finally { latestFinishHold() }
                                })
                        }.pointerInput(player) {
                            try {
                                awaitPointerEventScope {
                                    while(true) {
                                        val count=awaitPointerEvent(PointerEventPass.Initial).changes.count { it.pressed }
                                        if(count>1) { multipleTouches=true; latestFinishHold() }
                                        if(count==0) multipleTouches=false
                                    }
                                }
                            } finally { multipleTouches=false; latestFinishHold() }
                        })
                    val failure=model.videoError ?: error
                    if((model.videoLoading || buffering) && failure==null) {
                        AppCircularProgressIndicator(Modifier.align(Alignment.Center).size(34.dp),color=Color.White,strokeWidth=2.dp)
                    }
                    val motion=LocalMotionEnabled.current
                    AnimatedVisibility(
                        visible=showControls,
                        enter=fadeIn(tween(if(motion) HydrogenMotion.VideoControlsEnterDuration else 0,easing=HydrogenMotion.EaseOut)),
                        exit=fadeOut(tween(if(motion) HydrogenMotion.VideoControlsExitDuration else 0,easing=HydrogenMotion.EaseOut)),
                        modifier=Modifier.fillMaxSize().onFocusChanged { controlFocused=it.hasFocus }.focusGroup()) {
                        Box(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.55f),Color.Transparent)))
                                .statusBarsPadding().padding(horizontal=12.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                                VideoGlassButton("关闭视频",Icons.Outlined.Close,model::closeVideo)
                                Text(if(landscape) source.title else "视频",style=MaterialTheme.typography.titleSmall,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis,
                                    modifier=Modifier.weight(1f).padding(horizontal=12.dp))
                            }
                            // Orbit belongs to the screen edge, independent of letterboxing.
                            // Keep the gesture-bar clearance even while system bars are hidden.
                            Column(Modifier.align(Alignment.BottomCenter).width(dockWidth)
                                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom))
                                .padding(bottom=if(landscape) 12.dp else 16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                if(!landscape && source.title.isNotBlank()) Text(source.title,
                                    modifier=Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp,bottom=8.dp),
                                    style=MaterialTheme.typography.titleSmall,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis)
                                GlassPanel(Modifier.fillMaxWidth(),backdrop=true,liquid=true,tintStrength=.42f) {
                                    Column(Modifier.padding(horizontal=8.dp,vertical=8.dp)) {
                                        // Keep labels inside the rounded upper corners at large UI scales.
                                        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp)
                                            .offset(y=target/2-10.dp),verticalAlignment=Alignment.CenterVertically,
                                            horizontalArrangement=Arrangement.SpaceBetween) {
                                            Text(videoTime(seeking?.toLong() ?: position),style=MaterialTheme.typography.labelSmall,color=Color.White.copy(alpha=.85f))
                                            Text(videoTime(duration),style=MaterialTheme.typography.labelSmall,color=Color.White.copy(alpha=.65f))
                                        }
                                        VideoProgress(player=player,playing=playing,seeking=seeking!=null,
                                            value=seeking ?: position.toFloat(),duration=duration,enabled=player!=null && duration>0,
                                            onValueChange={ revealControls(); seeking=it },
                                            onValueChangeFinished={ seeking?.let { position=it.toLong(); player?.seekTo(position) }; seeking=null; revealControls() },
                                            modifier=Modifier.fillMaxWidth())
                                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                                            VideoDockButton(if(playing) "暂停" else if(ended) "重新播放" else "播放",
                                                if(playing) Icons.Outlined.Pause else if(ended) Icons.Outlined.Replay else Icons.Outlined.PlayArrow,
                                                enabled=player!=null && failure==null,emphasized=true,onClick=togglePlayback)
                                            Box {
                                                TextButton(onClick={ revealControls(); speedMenu=true },enabled=player!=null,
                                                    contentPadding=PaddingValues(horizontal=2.dp),
                                                    modifier=Modifier.widthIn(min=target).height(target)) {
                                                    Text("${speed}×",style=MaterialTheme.typography.labelLarge,maxLines=1,
                                                        color=if(player!=null) Color.White else Color.White.copy(alpha=.38f))
                                                }
                                                VideoSpeedMenu(speedMenu,speed,onDismiss={ speedMenu=false; revealControls() },
                                                    onSelect={ speed=it; speedMenu=false; revealControls() })
                                            }
                                            VideoDockButton(if(muted) "开启声音" else "静音",
                                                if(muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,enabled=player!=null) {
                                                muted=!muted; revealControls()
                                            }
                                            VideoDockButton(if(landscape) "退出全屏" else "全屏播放",
                                                if(landscape) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen) {
                                                latestFinishHold(); revealControls()
                                                activity?.requestedOrientation=if(landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    AnimatedVisibility(boosting,enter=fadeIn(tween(if(motion) 140 else 0)),
                        exit=fadeOut(tween(if(motion) 120 else 0)),modifier=Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top=26.dp)) {
                        GlassPanel(backdrop=true,liquid=true) {
                            Row(Modifier.padding(horizontal=18.dp,vertical=11.dp).semantics { liveRegion=LiveRegionMode.Polite },
                                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.FastForward,null,Modifier.size(20.dp),tint=Color.White)
                                Text("2× 倍速播放",color=Color.White,style=MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    if(failure!=null) GlassPanel(Modifier.align(Alignment.Center).padding(24.dp).widthIn(max=420.dp),backdrop=true) {
                        Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(failure,color=Color.White,style=MaterialTheme.typography.bodyMedium)
                            Row {
                                BubbleTextButton(onClick={ controls=true; if(player!=null) { error=null; player.prepare(); player.play() } else model.previewVideo(source) }) { Text("重试") }
                                if(source.threadId.isNotBlank()) BubbleTextButton(onClick={ model.closeVideo(); model.officialThread(source.threadId) }) { Text("原帖查看") }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun VideoDockButton(label: String,icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,emphasized: Boolean = false,onClick: () -> Unit) {
    val target=maxOf(48.dp,LocalTouchTarget.current)
    val interaction=remember { MutableInteractionSource() }
    IconButton(onClick=onClick,enabled=enabled,interactionSource=interaction,
        modifier=Modifier.size(target).pressMotion(interaction)) {
        Box(Modifier.size(target).clip(CircleShape)
            .background(if(emphasized) Color(0xFFEDF2D8).copy(alpha=if(enabled) 1f else .38f) else Color.White.copy(alpha=.045f)),
            contentAlignment=Alignment.Center) {
            Icon(icon,label,Modifier.size(if(emphasized) 28.dp else 26.dp),
                tint=if(emphasized) Color(0xFF203D2D) else Color.White.copy(alpha=if(enabled) 1f else .38f))
        }
    }
}

@Composable
private fun VideoGlassButton(label: String,icon: androidx.compose.ui.graphics.vector.ImageVector,onClick: () -> Unit) {
    val target=maxOf(48.dp,LocalTouchTarget.current)
    val interaction=remember { MutableInteractionSource() }
    IconButton(onClick=onClick,interactionSource=interaction,modifier=Modifier.size(target).pressMotion(interaction)) {
        GlassPanel(Modifier.size(36.dp),backdrop=true,liquid=true,tintStrength=.42f) {
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Icon(icon,label,tint=Color.White,modifier=Modifier.size(20.dp)) }
        }
    }
}

private fun videoTime(ms: Long): String = "%d:%02d".format(ms/60000,(ms/1000)%60)

@Composable
private fun VideoSpeedMenu(expanded: Boolean,speed: Float,onDismiss: () -> Unit,onSelect: (Float) -> Unit) {
    // Compact visuals, with a genuine 48 physical dp target even at reduced UI scale.
    val target=LocalTouchTarget.current
    val interfaceDensity=LocalDensity.current
    val fontScale=interfaceDensity.fontScale
    val rowHeight=maxOf(target,(MaterialTheme.typography.labelMedium.lineHeight.value*fontScale+8).dp)
    val rowWidth=maxOf(96.dp,(40*fontScale+42).dp)
    DropdownMenu(expanded,onDismiss,shape=RoundedCornerShape(26.dp),
        containerColor=MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation=0.dp,shadowElevation=6.dp,
        border=BorderStroke(.6.dp,MaterialTheme.colorScheme.outlineVariant)) {
        // Like Dialog, Popup may install the system density. Keep the compact rows
        // in the same interface/font scale as the dock, without shrinking touch targets.
        CompositionLocalProvider(LocalDensity provides interfaceDensity) {
        listOf(.5f,1f,1.25f,1.5f,2f).forEach { value ->
            DropdownMenuItem(onClick={ onSelect(value) },
                text={ Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                    verticalAlignment=Alignment.CenterVertically) {
                    Text("${value}×",style=MaterialTheme.typography.labelMedium,maxLines=1)
                    if(speed==value) Icon(Icons.Outlined.Check,null,Modifier.size(14.dp))
                } },
                contentPadding=PaddingValues(horizontal=12.dp),
                modifier=Modifier.padding(horizontal=4.dp).width(rowWidth).requiredHeight(rowHeight)
                    .bubbleBackground(RoundedCornerShape(22.dp),selected=speed==value)
                    .semantics { selected=speed==value })
        }
        }
    }
}

/** A single canvas keeps track and thumb on exactly the same centre line.
 * The transparent native Slider retains seeking, keyboard and progress semantics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoProgress(player: Player?,playing: Boolean,seeking: Boolean,
    value: Float,duration: Long,enabled: Boolean,onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,modifier: Modifier = Modifier) {
    val maximum=duration.coerceAtLeast(1).toFloat()
    val clamped=value.coerceIn(0f,maximum)
    val framePosition=remember(player) { mutableLongStateOf(player?.currentPosition?.coerceAtLeast(0) ?: 0) }
    val minimal = LocalMinimalMode.current
    LaunchedEffect(player,playing,minimal) {
        if(player!=null && playing) while(true) {
            if (minimal) { framePosition.longValue = player.currentPosition.coerceAtLeast(0); kotlinx.coroutines.delay(250) }
            else withFrameNanos { framePosition.longValue=player.currentPosition.coerceAtLeast(0) }
        }
    }
    Box(modifier.height(maxOf(48.dp,LocalTouchTarget.current)),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // Read the frame state only during drawing: no per-frame dock/blur recomposition.
            // Sample the real player clock, so pause, buffering, seeks and rate changes never ease behind.
            val playhead=if(playing && !seeking) framePosition.longValue.toFloat().coerceIn(0f,maximum) else clamped
            val inset=6.dp.toPx()
            val start=Offset(inset,size.height/2)
            val end=Offset((size.width-inset).coerceAtLeast(inset),start.y)
            val thumb=Offset(start.x+(end.x-start.x)*playhead/maximum,start.y)
            drawLine(Color.White.copy(alpha=.3f),start,end,3.dp.toPx(),StrokeCap.Round)
            if(playhead>0) drawLine(Color.White,start,thumb,3.dp.toPx(),StrokeCap.Round)
            drawCircle(Color.White.copy(alpha=if(enabled) 1f else .38f),4.dp.toPx(),thumb)
        }
        Slider(value=clamped,onValueChange=onValueChange,onValueChangeFinished=onValueChangeFinished,
            valueRange=0f..maximum,enabled=enabled,
            thumb={ Spacer(Modifier.size(12.dp)) },track={ Spacer(Modifier.fillMaxWidth().height(3.dp)) },
            modifier=Modifier.fillMaxSize().semantics { contentDescription="视频播放进度" })
    }
}
