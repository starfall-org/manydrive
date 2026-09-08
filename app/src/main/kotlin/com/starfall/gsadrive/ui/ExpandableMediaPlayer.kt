package com.starfall.gsadrive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.starfall.gsadrive.PlaybackSourceRegistry
import com.starfall.gsadrive.PlaybackProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.starfall.gsadrive.data.DriveFile
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlin.math.roundToInt

/** One mounted video surface moves between the viewer and the measured bottom-bar slot. */
@Composable
internal fun ExpandableMediaPlayer(
    player: Player,
    title: String,
    audioOnly: Boolean,
    minimized: Boolean,
    topPadding: Dp,
    miniBounds: Rect?,
    onMinimize: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    queue: List<DriveFile>,
    index: Int,
    onSwipeTo: (Int) -> Unit
) {
    BackHandler(enabled = !minimized, onBack = onMinimize)
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
    val currentMinimized by rememberUpdatedState(minimized)
    val bottomInset = WindowInsets.safeDrawing.getBottom(density)

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { rootTop = it.positionInRoot().y }) {
        val fullTop = with(density) { topPadding.toPx() }
        val fullHeight = (constraints.maxHeight - fullTop - bottomInset).coerceAtLeast(1f)
        val miniHeight = miniBounds?.height ?: with(density) { 72.dp.toPx() }
        val miniTop = miniBounds?.let { it.top - rootTop }
            ?: (constraints.maxHeight - bottomInset - miniHeight)
        val travel = (miniTop - fullTop).coerceAtLeast(1f)
        val currentTravel by rememberUpdatedState(travel)
        val height = fullHeight + (miniHeight - fullHeight) * fraction
        val y = fullTop + (miniTop - fullTop) * fraction
        val pager = rememberPagerState(initialPage = (queue.lastIndex - index).coerceAtLeast(0), pageCount = { queue.size })
        LaunchedEffect(index, queue.size) {
            val target = queue.lastIndex - index
            if (target >= 0 && !pager.isScrollInProgress && pager.currentPage != target) pager.scrollToPage(target)
        }
        LaunchedEffect(pager, queue) {
            snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
                val target = queue.lastIndex - page
                if (target in queue.indices && target != currentIndex) currentSwipe(target)
            }
        }

        Box(
            Modifier.offset { IntOffset(0, y.roundToInt()) }
                .fillMaxWidth().height(with(density) { height.toDp() }).clipToBounds()
                .pointerInput(player) {
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
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().background(Color.Black),
                userScrollEnabled = !minimized && fraction < 0.05f,
                key = { page -> queue[queue.lastIndex - page].id }
            ) { page ->
                val itemIndex = queue.lastIndex - page
                if (itemIndex == index) {
                    MediaViewer(player, audioOnly, fraction, title, onExpand, onClose)
                } else {
                    val item = queue[itemIndex]
                    MediaPagePreview(item)

                }
            }
        }
    }
}

/** Use a cached resume frame when available without starting a second audio stream. */
@Composable
private fun MediaPagePreview(item: DriveFile) {
    val context = LocalContext.current
    val source = PlaybackSourceRegistry.all().firstOrNull { it.file.id == item.id }
    val frame by produceState<android.graphics.Bitmap?>(null, source?.mediaId) {
        if (item.mimeType.startsWith("video/") && source?.cacheFile?.isFile == true) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(source.cacheFile.path)
                        retriever.getFrameAtTime(PlaybackProgress.read(context, source.mediaId) * 1000,
                            android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally { retriever.release() }
                }.getOrNull()
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val bitmap = frame
        if (bitmap != null) Image(bitmap.asImageBitmap(), item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (item.mimeType.startsWith("audio/")) Icons.Outlined.AudioFile else Icons.Outlined.Movie,
                null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(item.name, color = Color.White)
        }
    }
}
