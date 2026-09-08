package com.starfall.gsadrive.ui

import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberUpdatedState
import com.starfall.gsadrive.R
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.Color
import com.starfall.gsadrive.PlaybackSettings
import kotlinx.coroutines.delay
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
                CopyableError(error)
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

/** PlayerView only owns the surface; Compose owns controls and seek gestures. */
@OptIn(UnstableApi::class)
@Composable
internal fun MediaViewer(
    player: Player,
    alwaysShowControls: Boolean,
    compactFraction: Float = 0f,
    title: String = "",
    onExpand: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    var controlsVisible by remember(player) { mutableStateOf(true) }
    var interactionVersion by remember { mutableLongStateOf(0L) }
    var settingsOpen by remember { mutableStateOf(false) }
    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    var playRequested by remember(player) { mutableStateOf(player.playWhenReady) }
    val controlsInteraction = remember { MutableInteractionSource() }
    val controlsPressed by controlsInteraction.collectIsPressedAsState()
    val compact = compactFraction > 0.85f
    val currentCompact by rememberUpdatedState(compact)
    val currentExpand by rememberUpdatedState(onExpand)
    val slideshowEnabled by PlaybackSettings.slideshowEnabled.collectAsState()
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var seekable by remember(player) { mutableStateOf(false) }
    var dragging by remember(player) { mutableStateOf(false) }
    var scrubPosition by remember(player) { mutableFloatStateOf(0f) }
    var feedback by remember(player) { mutableStateOf<String?>(null) }
    var feedbackVersion by remember(player) { mutableLongStateOf(0L) }

    LaunchedEffect(controlsVisible, interactionVersion, playing, dragging, settingsOpen, controlsPressed, compact) {
        if (controlsVisible && playing && !dragging && !settingsOpen && !controlsPressed && !compact) {
            delay(3000)
            controlsVisible = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing = player.isPlaying
                playRequested = player.playWhenReady
                duration = player.duration.coerceAtLeast(0L)
                position = player.currentPosition.coerceAtLeast(0L)
                seekable = player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) &&
                    player.isCurrentMediaItemSeekable
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) dragging = false
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            seekable = player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) &&
                player.isCurrentMediaItemSeekable
            delay(250)
        }
    }
    LaunchedEffect(feedbackVersion) {
        delay(700)
        feedback = null
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val videoWidth = maxWidth * (1f - compactFraction) + 120.dp.coerceAtMost(maxWidth * 0.4f) * compactFraction
        AndroidView(
            modifier = Modifier.fillMaxHeight().width(videoWidth),
            factory = { context ->
                (LayoutInflater.from(context).inflate(R.layout.media_player_surface, null) as PlayerView).apply {
                    this.player = player
                    useController = false
                    setKeepContentOnPlayerReset(true)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                }
            },
            update = { view ->
                if (view.player !== player) view.player = player
                view.useController = false
            },
            onRelease = { view ->
                view.player = null
                view.keepScreenOn = false
            }
        )
        Box(
            Modifier.fillMaxSize().pointerInput(player) {
                detectTapGestures(onTap = { if (currentCompact) currentExpand() else { controlsVisible = !controlsVisible; interactionVersion++ } }, onDoubleTap = { offset ->
                    if (currentCompact) {
                        currentExpand()
                    } else if (player.isCurrentMediaItemSeekable &&
                        player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
                        val backward = offset.x < size.width / 2f
                        val target = (player.currentPosition + if (backward) -10_000L else 10_000L)
                            .coerceAtLeast(0L)
                        player.seekTo(if (player.duration > 0) target.coerceAtMost(player.duration) else target)
                        feedback = if (backward) "−10 giây" else "+10 giây"
                        feedbackVersion++
                    }
                })
            }
        )
        if (alwaysShowControls) {
            Box(Modifier.fillMaxHeight().width(videoWidth), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AudioFile, null, tint = Color.LightGray)
            }
        }
        feedback?.let {
            Text(it, Modifier.align(Alignment.Center).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))).padding(horizontal = 20.dp, vertical = 12.dp),
                color = Color.White)
        }
        if (compact) {
            Row(Modifier.fillMaxSize().graphicsLayer { alpha = ((compactFraction - 0.85f) / 0.15f).coerceIn(0f, 1f) }
                .padding(start = videoWidth, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f).padding(horizontal = 8.dp), color = Color.White,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    PlayPauseButton(player)
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Đóng trình phát") }
                }
            }
        }
        if (compactFraction < 0.5f && controlsVisible) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .graphicsLayer { alpha = (1f - compactFraction * 2f).coerceIn(0f, 1f) }
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), color = Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Box {
                    IconButton(onClick = { settingsOpen = true; interactionVersion++ }) {
                        Icon(Icons.Outlined.Settings, "Cài đặt phát", tint = Color.White)
                    }
                    DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false; interactionVersion++ }) {
                        DropdownMenuItem(text = { Text("Tự động chuyển bài") },
                            trailingIcon = { Switch(checked = slideshowEnabled, onCheckedChange = {
                                PlaybackSettings.setSlideshowEnabled(it); interactionVersion++
                            }) },
                            onClick = { PlaybackSettings.setSlideshowEnabled(!slideshowEnabled); interactionVersion++ })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                IconButton(enabled = seekable, onClick = { player.seekBack(); interactionVersion++ }) {
                    Icon(Icons.Outlined.Replay10, "Lùi 10 giây", tint = Color.White)
                }
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    IconButton(onClick = {
                        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) player.pause()
                        else {
                            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                            player.play()
                        }
                        interactionVersion++
                    }, modifier = Modifier.size(56.dp), interactionSource = controlsInteraction) {
                        Icon(if (playRequested && player.playbackState != Player.STATE_ENDED) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            if (playRequested && player.playbackState != Player.STATE_ENDED) "Tạm dừng" else "Phát",
                            modifier = Modifier.size(40.dp))
                    }
                }
                IconButton(enabled = seekable, onClick = { player.seekForward(); interactionVersion++ }) {
                    Icon(Icons.Outlined.Forward10, "Tiến 10 giây", tint = Color.White)
                }
            }
            Slider(
                value = if (dragging) scrubPosition else position.toFloat().coerceIn(0f, duration.toFloat()),
                onValueChange = { dragging = true; scrubPosition = it; interactionVersion++ },
                onValueChangeFinished = {
                    player.seekTo(scrubPosition.toLong().coerceIn(0L, duration))
                    dragging = false
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                enabled = seekable && duration > 0,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Tiến độ phát" }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(mediaTime(if (dragging) scrubPosition.toLong() else position), color = Color.White)
                Text(mediaTime(duration), color = Color.White)
            }

        }
    }
}

private fun mediaTime(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}

/** Standalone compact player; the app's expanding player keeps this same surface mounted. */
@OptIn(UnstableApi::class)
@Composable
fun MediaMiniPlayer(
    player: Player,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var verticalDrag by remember { mutableFloatStateOf(0f) }
    val expand by rememberUpdatedState(onExpand)
    Box(modifier.fillMaxWidth().height(72.dp).pointerInput(player) {
        detectVerticalDragGestures(
            onDragStart = { verticalDrag = 0f },
            onVerticalDrag = { change, amount -> change.consume(); verticalDrag += amount },
            onDragEnd = { if (verticalDrag < -40.dp.toPx()) expand() },
            onDragCancel = { verticalDrag = 0f }
        )
    }) {
        MediaViewer(player, alwaysShowControls = false, compactFraction = 1f,
            title = player.mediaMetadata.title?.toString().orEmpty(), onExpand = onExpand, onClose = onClose)
    }
}
