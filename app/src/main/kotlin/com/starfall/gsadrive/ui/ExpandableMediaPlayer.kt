package com.starfall.gsadrive.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.starfall.gsadrive.PlaybackProgress
import com.starfall.gsadrive.PlaybackSourceRegistry
import com.starfall.gsadrive.createMediaPagePlayer
import com.starfall.gsadrive.data.DriveFile
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The horizontal pager is the viewer itself, not a swipe animation layered over one fixed item.
 * Natural LTR order means drag left -> next item and drag right -> previous item.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun ExpandableMediaPlayer(
    player: Player,
    minimized: Boolean,
    topPadding: Dp,
    miniBounds: Rect?,
    onMinimize: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    queue: List<DriveFile>,
    index: Int,
    previewPaths: Map<String, String>,
    onSwipeTo: (Int) -> Unit
) {
    var fullscreen by remember { mutableStateOf(false) }
    var landscapeVideo by remember(player) {
        mutableStateOf(player.videoSize.width > player.videoSize.height && player.videoSize.height > 0)
    }
    val view = LocalView.current

    BackHandler(enabled = !minimized) {
        if (fullscreen) fullscreen = false else onMinimize()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_VIDEO_SIZE_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    landscapeVideo = player.videoSize.width > player.videoSize.height && player.videoSize.height > 0
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(minimized) {
        if (minimized) fullscreen = false
    }

    DisposableEffect(fullscreen, landscapeVideo, view) {
        val activity = view.context as? Activity
        if (activity != null) {
            val bars = WindowCompat.getInsetsController(activity.window, view)
            if (fullscreen) {
                bars.hide(WindowInsetsCompat.Type.systemBars())
                bars.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (landscapeVideo) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            } else {
                bars.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        onDispose {
            if (fullscreen && activity != null) {
                WindowCompat.getInsetsController(activity.window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    val density = LocalDensity.current
    var rootTop by remember { mutableFloatStateOf(0f) }
    var dragFraction by remember(player) { mutableStateOf<Float?>(null) }
    var dragDistance by remember(player) { mutableFloatStateOf(0f) }
    val fraction by animateFloatAsState(
        targetValue = dragFraction ?: if (minimized) 1f else 0f,
        animationSpec = if (dragFraction != null) snap() else tween(240),
        label = "player expansion"
    )
    val currentMinimize by rememberUpdatedState(onMinimize)
    val currentExpand by rememberUpdatedState(onExpand)
    val currentSwipe by rememberUpdatedState(onSwipeTo)
    val currentIndex by rememberUpdatedState(index)
    val systemBottomInset = WindowInsets.safeDrawing.getBottom(density)

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { rootTop = it.positionInRoot().y }) {
        val fullTop = if (fullscreen) 0f else with(density) { topPadding.toPx() }
        val bottomInset = if (fullscreen) 0 else systemBottomInset
        val fullHeight = (constraints.maxHeight - fullTop - bottomInset).coerceAtLeast(1f)
        val miniHeight = miniBounds?.height ?: with(density) { 72.dp.toPx() }
        val miniTop = miniBounds?.let { it.top - rootTop }
            ?: (constraints.maxHeight - bottomInset - miniHeight)
        val travel = (miniTop - fullTop).coerceAtLeast(1f)
        val currentTravel by rememberUpdatedState(travel)
        val height = fullHeight + (miniHeight - fullHeight) * fraction
        val y = fullTop + (miniTop - fullTop) * fraction
        val safeInitialIndex = index.coerceIn(0, queue.lastIndex.coerceAtLeast(0))
        val pager = rememberPagerState(initialPage = safeInitialIndex, pageCount = { queue.size })

        LaunchedEffect(index, queue.size) {
            val target = index
            if (target in queue.indices && !pager.isScrollInProgress && pager.currentPage != target) {
                pager.animateScrollToPage(target)
            }
        }
        LaunchedEffect(pager, queue) {
            snapshotFlow { pager.isScrollInProgress to pager.settledPage }
                .distinctUntilChanged()
                .collect { (scrolling, page) ->
                    // A settled pager page becomes the real selected viewer item immediately.
                    if (!scrolling && page in queue.indices && page != currentIndex) currentSwipe(page)
                }
        }

        Box(
            Modifier.offset { IntOffset(0, y.roundToInt()) }
                .fillMaxWidth()
                .height(with(density) { height.toDp() })
                .clipToBounds()
                .pointerInput(player, fullscreen) {
                    if (fullscreen) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { dragFraction = fraction; dragDistance = 0f },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragDistance += amount
                            dragFraction = ((dragFraction ?: fraction) + amount / currentTravel).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            val collapse = when {
                                dragDistance > 64.dp.toPx() -> true
                                dragDistance < -64.dp.toPx() -> false
                                else -> (dragFraction ?: fraction) >= 0.5f
                            }
                            if (collapse) currentMinimize() else currentExpand()
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null }
                    )
                }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    userScrollEnabled = !minimized && fraction < 0.05f,
                    beyondViewportPageCount = if (minimized) 0 else 1,
                    key = { page -> queue[page].id }
                ) { page ->
                    val item = queue[page]
                    if (item.mimeType.startsWith("image/")) {
                        val path = previewPaths[item.id]
                        if (path != null) ImageViewer(path)
                        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (page == index || abs(page - index) == 1) {
                        // Only the current item and its direct neighbors are ever composed as media pages.
                        MediaItemPage(
                            item = item,
                            sessionPlayer = player,
                            selected = page == index,
                            compactFraction = fraction,
                            onExpand = onExpand,
                            onClose = onClose,
                            fullscreen = fullscreen,
                            onToggleFullscreen = { fullscreen = !fullscreen }
                        )
                    } else {
                        // A fast fling must not allocate a player for pages outside prev/current/next.
                        Box(Modifier.fillMaxSize().background(Color.Black))
                    }
                }
            }
        }
    }
}

/**
 * The selected page exclusively uses the MediaSession player.
 * Direct neighbors get a frozen ExoPlayer object with a MediaItem assigned, but it is never
 * prepared or played, so it opens no cloud stream and allocates no decoder/buffer.
 */
@OptIn(UnstableApi::class)
@Composable
private fun MediaItemPage(
    item: DriveFile,
    sessionPlayer: Player,
    selected: Boolean,
    compactFraction: Float,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit
) {
    val context = LocalContext.current
    val source = PlaybackSourceRegistry.all().firstOrNull { it.file.id == item.id }
    if (source == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    var sessionMediaId by remember(sessionPlayer) { mutableStateOf(sessionPlayer.currentMediaItem?.mediaId) }
    DisposableEffect(sessionPlayer) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                sessionMediaId = player.currentMediaItem?.mediaId
            }
        }
        sessionPlayer.addListener(listener)
        onDispose { sessionPlayer.removeListener(listener) }
    }

    if (selected) {
        if (sessionMediaId == source.mediaId) {
            MediaViewer(
                player = sessionPlayer,
                alwaysShowControls = item.mimeType.startsWith("audio/"),
                compactFraction = compactFraction,
                title = item.name,
                onExpand = onExpand,
                onClose = onClose,
                interactive = true,
                fullscreen = fullscreen,
                onToggleFullscreen = onToggleFullscreen
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val frozenPlayer = remember(source.mediaId) { createMediaPagePlayer(context.applicationContext) }
    DisposableEffect(frozenPlayer, source.mediaId) {
        frozenPlayer.setMediaItem(source.toMediaItem(), PlaybackProgress.read(context, source.mediaId))
        frozenPlayer.pause()
        onDispose { frozenPlayer.release() }
    }
    MediaViewer(
        player = frozenPlayer,
        alwaysShowControls = item.mimeType.startsWith("audio/"),
        compactFraction = compactFraction,
        title = item.name,
        onExpand = onExpand,
        onClose = onClose,
        interactive = false
    )
}
