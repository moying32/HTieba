package dev.hydrogen.tieba.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import dev.hydrogen.tieba.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

val LocalOpenVideo = staticCompositionLocalOf<(VideoSource) -> Unit> { {} }

@Composable
fun ImageViewer(gallery: ImageGallery, defaultQuality: ImageQuality,
    resolveSources: suspend (ImageGallery) -> ImageGallery, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = gallery.initialIndex) { gallery.sources.size }
    val scope = rememberCoroutineScope()
    var zoom by remember(gallery) { mutableFloatStateOf(1f) }
    var controlsVisible by remember(gallery) { mutableStateOf(true) }
    val motion = LocalMotionEnabled.current
    fun switchImage(direction: Int) {
        // Use the pager's pending destination so repeated/reversed taps can
        // interrupt an in-flight slide without snapping back to currentPage.
        val destination = (pager.targetPage + direction).coerceIn(0, pager.pageCount - 1)
        scope.launch {
            if (motion) pager.animateScrollToPage(destination)
            else pager.scrollToPage(destination)
        }
    }
    var saving by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<ImageSource?>(null) }
    var resolved by remember(gallery) { mutableStateOf(gallery) }
    var resolving by remember(gallery, defaultQuality) { mutableStateOf(gallery.threadId.isNotBlank() && gallery.sources.any { it.needsPreviewDetails(defaultQuality) }) }
    var resolutionError by remember(gallery) { mutableStateOf<String?>(null) }
    var metadataRetry by remember(gallery) { mutableIntStateOf(0) }
    var acceptedPreviews by remember(gallery, defaultQuality) { mutableStateOf(emptySet<Int>()) }
    LaunchedEffect(gallery, defaultQuality, metadataRetry) {
        if (gallery.threadId.isBlank() || resolved.sources.none { it.needsPreviewDetails(defaultQuality) }) return@LaunchedEffect
        resolving = true; resolutionError = null
        try { resolved = resolveSources(gallery) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { resolutionError = "暂时无法加载完整图片，请重试" }
        finally { resolving = false }
    }
    val snack = remember { SnackbarHostState() }
    val layer = rememberGraphicsLayer()
    val scene = remember(layer) { GlassScene(layer) }
    fun download(source: ImageSource) {
        if (saving) return
        saving = true
        scope.launch {
            var downloaded: DownloadedImage? = null
            val message = try {
                val file = ImageDownload.fetch(source, context.cacheDir)
                downloaded = file
                DownloadStorage.save(context.applicationContext, file)
                "已下载到 Download/HTieBa"
            } catch (e: CancellationException) { throw e }
              catch (_: SecurityException) { "无法写入下载文件，请允许存储权限后重试" }
              catch (e: Exception) {
                e.message?.takeIf { it.any { c -> c.code > 127 } } ?: "图片下载失败，请检查网络或存储空间后重试"
            } finally {
                downloaded?.file?.delete()
                saving = false
            }
            snack.showSnackbar(message)
        }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val source = pendingDownload
        pendingDownload = null
        if (granted && source != null) download(source)
        else scope.launch { snack.showSnackbar("下载需要存储权限，请在系统应用权限中允许后重试") }
    }
    LaunchedEffect(pager.currentPage) { zoom = 1f }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CompositionLocalProvider(LocalGlassScene provides scene) {
            Box(Modifier.fillMaxSize().background(Color.Black).semantics {
                customActions = listOf(CustomAccessibilityAction(if (controlsVisible) "隐藏图片工具栏" else "显示图片工具栏") {
                    controlsVisible = !controlsVisible; true
                }, CustomAccessibilityAction("关闭图片预览") { onDismiss(); true })
            }) {
                HorizontalPager(state = pager, userScrollEnabled = zoom <= 1.001f,
                    modifier = Modifier.fillMaxSize().then(if (LocalRichEffects.current) Modifier.captureGlassScene(scene) else Modifier), key = { it }) { page ->
                    val source = resolved.sources[page]
                    val needsDetail = gallery.threadId.isNotBlank() && source.needsPreviewDetails(defaultQuality) && page !in acceptedPreviews
                    when {
                        needsDetail && resolving -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AppCircularProgressIndicator(color = Color.White)
                        }
                        needsDetail -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(resolutionError ?: "贴吧未返回这张图片的完整图片地址", color = Color.White, modifier = Modifier.padding(16.dp))
                            BubbleTextButton(onClick = { metadataRetry++ }) { Text("重试加载") }
                            BubbleTextButton(onClick = { acceptedPreviews = acceptedPreviews + page }) { Text("查看现有图片") }
                        }
                        else -> ZoomImage(source, defaultQuality, page == pager.currentPage,
                            onTap = { controlsVisible = !controlsVisible }, onZoom = { zoom = it })
                    }
                }
                AnimatedVisibility(visible = controlsVisible, modifier = Modifier.align(Alignment.TopCenter),
                    enter = fadeIn(tween(if (motion) HydrogenMotion.ImageControlsEnterDuration else 0, easing = LinearEasing)) +
                        slideInVertically(tween(if (motion) HydrogenMotion.ImageControlsEnterDuration else 0, easing = HydrogenMotion.EaseOut)) { if (motion) -it / 12 else 0 },
                    exit = fadeOut(tween(if (motion) HydrogenMotion.ImageControlsExitDuration else 0, easing = LinearEasing)) +
                        slideOutVertically(tween(if (motion) HydrogenMotion.ImageControlsExitDuration else 0, easing = HydrogenMotion.EaseOut)) { if (motion) -it / 12 else 0 }) {
                Box(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                    GlassPanel(Modifier.fillMaxWidth(), backdrop = true, liquid = true) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            BubbleIconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "关闭图片") }
                            Text(if (gallery.sources.size > 1) "${pager.currentPage + 1} / ${gallery.sources.size}" else "",
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            if (gallery.sources.size > 1) {
                                BubbleIconButton(onClick = { switchImage(-1) }, enabled = pager.targetPage > 0) {
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上一张图片")
                                }
                                BubbleIconButton(onClick = { switchImage(1) }, enabled = pager.targetPage < gallery.sources.lastIndex) {
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下一张图片")
                                }
                            }
                            BubbleIconButton(enabled = !saving && pendingDownload == null && !(gallery.threadId.isNotBlank() &&
                                resolved.sources[pager.currentPage].needsPreviewDetails(defaultQuality) && pager.currentPage !in acceptedPreviews), onClick = {
                                val source = resolved.sources[pager.currentPage]
                                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                    pendingDownload = source
                                    storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                } else download(source)
                            }) {
                                if (saving) AppCircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Outlined.Download, "下载图片到 Download/HTieBa")
                            }
                        }
                    }
                }
                }
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun ZoomImage(source: ImageSource, quality: ImageQuality, active: Boolean, onTap: () -> Unit, onZoom: (Float) -> Unit) {
    val context = LocalContext.current
    val tap by rememberUpdatedState(onTap)
    val reportZoom by rememberUpdatedState(onZoom)
    val motion = LocalMotionEnabled.current
    val scope = rememberCoroutineScope()
    var animation by remember { mutableStateOf<Job?>(null) }
    var scale by remember(source, quality) { mutableFloatStateOf(1f) }
    var offset by remember(source, quality) { mutableStateOf(Offset.Zero) }
    var dimensions by remember { mutableStateOf(IntSize.Zero) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(source, quality, active) { animation?.cancel(); scale = 1f; offset = Offset.Zero }
    fun bounded(point: Offset, zoom: Float): Offset {
        val x = dimensions.width * (zoom - 1f).coerceAtLeast(0f) / 2f
        val y = dimensions.height * (zoom - 1f).coerceAtLeast(0f) / 2f
        return Offset(point.x.coerceIn(-x, x), point.y.coerceIn(-y, y))
    }
    LaunchedEffect(motion) {
        if (!motion) { animation?.cancel(); scale = scale.coerceIn(1f, 8f); offset = bounded(offset, scale) }
    }
    val transform = rememberTransformableState { zoom, pan, _ ->
        // Write the displayed transform directly while fingers are down. A
        // spring here would make the picture lag behind a two-finger gesture.
        scale = (scale * zoom).coerceIn(if (motion) .92f else 1f, if (motion) 8.4f else 8f)
        offset = bounded(offset + pan, scale)
    }
    LaunchedEffect(transform, active) {
        if (active) snapshotFlow { scale > 1.001f || transform.isTransformInProgress }.distinctUntilChanged().collect {
            // Pager only needs a gate, not the scale on every animation frame.
            reportZoom(if (it) 2f else 1f)
        }
    }
    fun animateZoom(destination: Float, position: Offset, settle: Boolean) {
        animation?.cancel()
        animation = scope.launch {
            // Share Compose's mutation mutex with pinch/pan: a new gesture
            // interrupts from the currently displayed transform, without a jump.
            transform.transform {
                val startScale = scale
                val startOffset = offset
                val targetOffset = bounded(position, destination)
                if (!motion) { scale = destination; offset = targetOffset }
                else animate(0f, 1f, animationSpec = if (settle)
                    spring(dampingRatio = 1f, stiffness = HydrogenMotion.ImageZoomStiffness, visibilityThreshold = .002f)
                    else tween(HydrogenMotion.ImageZoomDuration, easing = HydrogenMotion.EaseOut)) { progress, _ ->
                    scale = startScale + (destination - startScale) * progress
                    offset = startOffset + (targetOffset - startOffset) * progress
                }
            }
        }
    }
    LaunchedEffect(transform, active, motion) {
        snapshotFlow { transform.isTransformInProgress }.distinctUntilChanged().collect { transforming ->
            if (active && !transforming) {
                val target = scale.coerceIn(1f, 8f)
                val point = bounded(offset, target)
                if (target != scale || point != offset) animateZoom(target, point, settle = true)
            }
        }
    }
    val request = remember(source, quality, retry) {
        val url = source.previewUrl(quality)
        ImageRequest.Builder(context).data(url).size(quality.edge, quality.edge).precision(Precision.EXACT)
            .memoryCacheKey("image-preview:${quality.name}:${quality.edge}:$url").build()
    }
    Box(Modifier.fillMaxSize().onSizeChanged { dimensions = it }
        .transformable(state = transform, canPan = { scale > 1f })
        .pointerInput(source, quality, motion) {
            detectTapGestures(onTap = { tap() }, onDoubleTap = { point ->
                val target = if (scale > 1.01f) 1f else 2.5f
                val focus = if (target == 1f) Offset.Zero else
                    Offset(dimensions.width / 2f - point.x, dimensions.height / 2f - point.y) * (target - 1f)
                animateZoom(target, focus, settle = false)
            })
        }) {
        key(source, quality, retry) {
            SubcomposeAsyncImage(request, "图片预览，可双指缩放", imageLoader = LocalAppImageLoader.current, contentScale = ContentScale.Fit,
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppCircularProgressIndicator(color = Color.White) } },
                error = { Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("图片加载失败", color = Color.White, modifier = Modifier.padding(12.dp))
                    Button(onClick = { retry++ }) { Text("重新加载") }
                } }, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y })
        }
    }
}
