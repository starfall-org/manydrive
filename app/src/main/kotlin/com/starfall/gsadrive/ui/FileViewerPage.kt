package com.starfall.gsadrive.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.compose.material3.MiniController
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.isSwipePreview
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(UnstableApi::class)
@Composable
fun FileViewerPage(
    padding: PaddingValues,
    file: DriveFile,
    localPath: String?,
    text: String?,
    loading: Boolean,
    error: String?,
    saving: Boolean,
    player: Player,
    swipeQueue: List<DriveFile> = emptyList(),
    swipeIndex: Int = -1,
    previewPaths: Map<String, String> = emptyMap(),
    onSwipeTo: (Int) -> Unit = {},
    onBack: () -> Unit,
    onTextChange: (String) -> Unit,
    onSaveText: () -> Unit
) {
    BackHandler(onBack = onBack)

    val viewerModifier = if (isSwipePreview(file)) {
        Modifier.fillMaxSize().padding(padding).background(androidx.compose.ui.graphics.Color.Black)
    } else {
        Modifier.fillMaxSize().padding(padding)
    }
    Box(viewerModifier) {
        if (isSwipePreview(file) && swipeQueue.size > 1 && swipeIndex in swipeQueue.indices) {
            SwipeViewer(
                file = file,
                localPath = localPath,
                loading = loading,
                error = error,
                player = player,
                swipeQueue = swipeQueue,
                swipeIndex = swipeIndex,
                previewPaths = previewPaths,
                onSwipeTo = onSwipeTo,
                onBack = onBack
            )
        } else {
            ViewerContent(
                file = file,
                localPath = localPath,
                text = text,
                loading = loading,
                error = error,
                saving = saving,
                player = player,
                onBack = onBack,
                onTextChange = onTextChange,
                onSaveText = onSaveText
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SwipeViewer(
    file: DriveFile,
    localPath: String?,
    loading: Boolean,
    error: String?,
    player: Player,
    swipeQueue: List<DriveFile>,
    swipeIndex: Int,
    previewPaths: Map<String, String>,
    onSwipeTo: (Int) -> Unit,
    onBack: () -> Unit
) {
    // Queue is intentionally mirrored: swipe left = previous item, swipe right = next item.
    fun pagerPage(logicalIndex: Int) = swipeQueue.lastIndex - logicalIndex
    fun logicalIndex(page: Int) = swipeQueue.lastIndex - page

    val initialPage = pagerPage(swipeIndex).coerceIn(0, swipeQueue.lastIndex)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { swipeQueue.size })

    LaunchedEffect(swipeIndex, swipeQueue.size) {
        val target = pagerPage(swipeIndex)
        if (target in 0 until swipeQueue.size && pagerState.currentPage != target && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        }
    }

    LaunchedEffect(pagerState, swipeQueue, swipeIndex) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val target = logicalIndex(page)
                if (target != swipeIndex && target in swipeQueue.indices) onSwipeTo(target)
            }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { page -> swipeQueue[logicalIndex(page)].id }
    ) { page ->
        val index = logicalIndex(page)
        val item = swipeQueue[index]
        val current = index == swipeIndex && item.id == file.id
        if (current) {
            ViewerContent(
                file = item,
                localPath = localPath,
                text = null,
                loading = loading,
                error = error,
                saving = false,
                player = player,
                onBack = onBack,
                onTextChange = {},
                onSaveText = {}
            )
        } else {
            val cached = previewPaths[item.id]
            when {
                item.mimeType.startsWith("image/") && cached != null -> ImageViewer(cached)
                else -> AdjacentPreview(item)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ViewerContent(
    file: DriveFile,
    localPath: String?,
    text: String?,
    loading: Boolean,
    error: String?,
    saving: Boolean,
    player: Player,
    onBack: () -> Unit,
    onTextChange: (String) -> Unit,
    onSaveText: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            error != null -> Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(error, color = MaterialTheme.colorScheme.error)
                FilledTonalButton(onClick = onBack) { Text("Đóng") }
            }
            text != null -> TextEditor(text, saving, onTextChange, onSaveText)
            localPath != null && file.mimeType.startsWith("image/") -> ImageViewer(localPath)
            localPath != null && (file.mimeType.startsWith("video/") || file.mimeType.startsWith("audio/")) ->
                MediaViewer(player, alwaysShowControls = file.mimeType.startsWith("audio/"))
            else -> Text("Không thể xem loại tệp này.", Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun AdjacentPreview(file: DriveFile) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                when {
                    file.mimeType.startsWith("audio/") -> Icons.Outlined.AudioFile
                    file.mimeType.startsWith("video/") -> Icons.Outlined.Movie
                    else -> Icons.Outlined.Image
                },
                contentDescription = null,
                modifier = Modifier.padding(8.dp),
                tint = androidx.compose.ui.graphics.Color.LightGray
            )
            Text(
                file.name,
                style = MaterialTheme.typography.titleMedium,
                color = androidx.compose.ui.graphics.Color.LightGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun TextEditor(
    text: String,
    saving: Boolean,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            enabled = !saving,
            textStyle = MaterialTheme.typography.bodyMedium,
            label = { Text("Nội dung") }
        )
        FilledTonalButton(onClick = onSave, enabled = !saving, modifier = Modifier.align(Alignment.End)) {
            Text(if (saving) "Đang lưu…" else "Lưu thay đổi")
        }
    }
}

@Composable
private fun ImageViewer(path: String) {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
    if (bitmap == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentScale = ContentScale.Fit
        )
    }
}

/** PlayerView owns the video surface. Audio keeps its controls permanently visible. */
@OptIn(UnstableApi::class)
@Composable
private fun MediaViewer(player: Player, alwaysShowControls: Boolean) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                useController = true
                controllerAutoShow = true
                controllerHideOnTouch = !alwaysShowControls
                controllerShowTimeoutMs = if (alwaysShowControls) 0 else 3_000
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setShowPreviousButton(true)
                setShowRewindButton(true)
                setShowFastForwardButton(true)
                setShowNextButton(true)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepScreenOn = true
                if (alwaysShowControls) showController()
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.controllerHideOnTouch = !alwaysShowControls
            view.controllerShowTimeoutMs = if (alwaysShowControls) 0 else 3_000
            view.setShowPreviousButton(true)
            view.setShowRewindButton(true)
            view.setShowFastForwardButton(true)
            view.setShowNextButton(true)
            if (alwaysShowControls) view.showController()
        },
        onRelease = { view ->
            view.player = null
            view.keepScreenOn = false
        }
    )
}

/** YouTube-style horizontal mini-player. Tap or swipe upward to restore the full player. */
@OptIn(UnstableApi::class)
@Composable
fun MediaMiniPlayer(
    player: Player,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var verticalDrag by remember { mutableFloatStateOf(0f) }
    val gestureModifier = Modifier.pointerInput(onExpand) {
        detectVerticalDragGestures(
            onDragStart = { verticalDrag = 0f },
            onVerticalDrag = { _, amount -> verticalDrag += amount },
            onDragEnd = {
                if (verticalDrag < -40.dp.toPx()) onExpand()
                verticalDrag = 0f
            },
            onDragCancel = { verticalDrag = 0f }
        )
    }
    Box(modifier.then(gestureModifier)) {
        MiniController(
            player = player,
            modifier = Modifier.fillMaxWidth(),
            onClick = onExpand,
            playerControls = { currentPlayer ->
                PlayPauseButton(currentPlayer)
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, "Đóng trình phát")
                }
            }
        )
    }
}
