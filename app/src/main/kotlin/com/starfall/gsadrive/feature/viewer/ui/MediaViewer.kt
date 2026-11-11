package com.starfall.gsadrive.feature.viewer.ui

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import com.starfall.gsadrive.R
import com.starfall.gsadrive.feature.viewer.playback.PlaybackSettings
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.ui.CopyableError
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
internal fun MediaSurface(
    player: Player,
    audio: Boolean,
    compactFraction: Float = 0f,
    selected: Boolean = false,
    title: String = ""
) {
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val videoWidth = maxWidth * (1f - compactFraction) + 120.dp.coerceAtMost(maxWidth * 0.4f) * compactFraction
        val shortAudioLayout = maxHeight < 420.dp
        if (!audio) {
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
                        keepScreenOn = selected && isPlaying
                    }
                },
                update = { view ->
                    if (view.player !== player) view.player = player
                    view.useController = false
                    view.keepScreenOn = selected && isPlaying
                    view.setShowBuffering(if (selected) PlayerView.SHOW_BUFFERING_WHEN_PLAYING else PlayerView.SHOW_BUFFERING_ALWAYS)
                },
                onRelease = { view ->
                    view.player = null
                    view.keepScreenOn = false
                }
            )
        } else {
            // Audio has no video surface. Keeping a TextureView here made every mini/full
            // transition resize a hardware surface for no benefit and caused visible hitches.
            Box(Modifier.fillMaxHeight().width(videoWidth)
                .background(Brush.verticalGradient(listOf(Color(0xFF242644), Color(0xFF10111C)))),
                contentAlignment = Alignment.Center) {
                if (compactFraction > 0.5f) {
                    Icon(Icons.Outlined.AudioFile, null, tint = Color(0xFFC9C3FF), modifier = Modifier.size(36.dp))
                } else Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 180.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (shortAudioLayout) 4.dp else 16.dp)) {
                    if (!shortAudioLayout) Icon(Icons.Outlined.AudioFile, null, tint = Color(0xFFC9C3FF),
                        modifier = Modifier.size(96.dp).background(Color.White.copy(alpha = 0.08f),
                            androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).padding(20.dp))
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge,
                        maxLines = if (shortAudioLayout) 1 else 2, overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(if (isPlaying) localized(R.string.playing) else localized(R.string.pause),
                        color = Color(0xFFC9C3FF), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** PlayerView only owns the surface; Compose owns controls and seek gestures. */
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaViewer(
    player: Player,
    alwaysShowControls: Boolean,
    compactFraction: Float = 0f,
    title: String = "",
    onExpand: () -> Unit = {},
    onClose: () -> Unit = {},
    interactive: Boolean = true,
    miniChromeAlpha: Float = if (compactFraction > 0.99f) 1f else 0f,
    fullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    content: @Composable () -> Unit = {
        MediaSurface(player, alwaysShowControls, compactFraction, interactive, title)
    }
) {
    var controlsVisible by remember(player) { mutableStateOf(true) }
    var interactionVersion by remember { mutableLongStateOf(0L) }
    var settingsOpen by remember { mutableStateOf(false) }
    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    var playRequested by remember(player) { mutableStateOf(player.playWhenReady) }
    var playbackError by remember(player) { mutableStateOf(player.playerError) }
    val controlsInteraction = remember { MutableInteractionSource() }
    val controlsPressed by controlsInteraction.collectIsPressedAsState()
    val compact = compactFraction > 0.85f
    val currentInteractive by rememberUpdatedState(interactive)
    val currentCompact by rememberUpdatedState(compact)
    val keepControls by rememberUpdatedState(alwaysShowControls)
    val currentExpand by rememberUpdatedState(onExpand)
    val slideshowEnabled by PlaybackSettings.slideshowEnabled.collectAsState()
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var seekable by remember(player) { mutableStateOf(false) }
    var dragging by remember(player) { mutableStateOf(false) }
    var scrubPosition by remember(player) { mutableFloatStateOf(0f) }
    var feedback by remember(player) { mutableStateOf<String?>(null) }
    var feedbackVersion by remember(player) { mutableLongStateOf(0L) }

    LaunchedEffect(controlsVisible, interactionVersion, playing, dragging, settingsOpen, controlsPressed, compact, alwaysShowControls) {
        if (!alwaysShowControls && controlsVisible && playing && !dragging && !settingsOpen && !controlsPressed && !compact) {
            delay(3000)
            controlsVisible = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing = player.isPlaying
                playbackError = player.playerError
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

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).pointerInput(player) {
                detectTapGestures(onTap = { if (!currentInteractive) return@detectTapGestures; if (currentCompact) currentExpand() else { controlsVisible = keepControls || !controlsVisible; interactionVersion++ } }, onDoubleTap = { offset ->
                    if (!currentInteractive) return@detectTapGestures
                    if (currentCompact) {
                        currentExpand()
                    } else if (player.isCurrentMediaItemSeekable &&
                        player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
                        val backward = offset.x < size.width / 2f
                        val target = (player.currentPosition + if (backward) -10_000L else 10_000L)
                            .coerceAtLeast(0L)
                        player.seekTo(if (player.duration > 0) target.coerceAtMost(player.duration) else target)
                        feedback = if (backward) localized(R.string.text_10_seconds_2) else localized(R.string.text_10_seconds)
                        feedbackVersion++
                    }
                })
    }) {
        val videoWidth = maxWidth * (1f - compactFraction) + 120.dp.coerceAtMost(maxWidth * 0.4f) * compactFraction
        val miniBackground = MaterialTheme.colorScheme.surfaceContainerHigh
        val miniContentColor = MaterialTheme.colorScheme.onSurface
        content()
        if (!compact && interactive) playbackError?.let { failure ->
            CopyableError(
                failure.cause?.message ?: failure.message ?: localized(R.string.cannot_open_media),
                modifier = Modifier.align(Alignment.Center).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.85f)).padding(20.dp),
                color = Color.White
            )
        }
        if (miniChromeAlpha > 0f && videoWidth < maxWidth) {
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(maxWidth - videoWidth)
                    .graphicsLayer { alpha = miniChromeAlpha.coerceIn(0f, 1f) }
                    .background(miniBackground)
            )
        }
        feedback?.let {
            Text(it, Modifier.align(Alignment.Center).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))).padding(horizontal = 20.dp, vertical = 12.dp),
                color = Color.White)
        }
        if (miniChromeAlpha > 0f && compact && interactive) {
            Row(Modifier.fillMaxSize()
                .graphicsLayer { alpha = miniChromeAlpha.coerceIn(0f, 1f) }
                .padding(start = videoWidth, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    Modifier.weight(1f).padding(horizontal = 8.dp),
                    color = miniContentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                CompositionLocalProvider(LocalContentColor provides miniContentColor) {
                    PlayPauseButton(player)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, localized(R.string.close_player), tint = miniContentColor)
                    }
                }
            }
        }
        val fullControlsAlpha = ((0.55f - compactFraction) / 0.20f).coerceIn(0f, 1f)
        val fullControlsInteractive = fullControlsAlpha > 0.98f
        if ((alwaysShowControls || controlsVisible) && interactive) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().testTag("media-controls")
                .graphicsLayer { alpha = fullControlsAlpha }
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (alwaysShowControls) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(enabled = fullControlsInteractive && player.hasPreviousMediaItem(),
                    onClick = { player.seekToPreviousMediaItem(); interactionVersion++ }) {
                    Text(localized(R.string.previous_track), color = if (player.hasPreviousMediaItem()) Color.White else Color.Gray)
                }
                TextButton(enabled = fullControlsInteractive && player.hasNextMediaItem(),
                    onClick = { player.seekToNextMediaItem(); interactionVersion++ }) {
                    Text(localized(R.string.next_track), color = if (player.hasNextMediaItem()) Color.White else Color.Gray)
                }
            }
            val progressColor = Color.White.copy(alpha = 0.78f)
            Slider(
                value = if (dragging) scrubPosition else position.toFloat().coerceIn(0f, duration.toFloat()),
                onValueChange = { dragging = true; scrubPosition = it; interactionVersion++ },
                onValueChangeFinished = {
                    player.seekTo(scrubPosition.toLong().coerceIn(0L, duration))
                    dragging = false
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                enabled = fullControlsInteractive && seekable && duration > 0,
                modifier = Modifier.fillMaxWidth().height(20.dp)
                    .semantics { contentDescription = localized(R.string.playback_progress) },
                thumb = {
                    // Use a centered vertical tick rather than a dot so it reads as the playhead.
                    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(2.dp).height(12.dp).background(progressColor))
                    }
                },
                track = { sliderState ->
                    val range = sliderState.valueRange
                    val span = (range.endInclusive - range.start).coerceAtLeast(0.0001f)
                    val playedFraction = ((sliderState.value - range.start) / span).coerceIn(0f, 1f)
                    Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                        val centerY = size.height / 2f
                        val playedX = size.width * playedFraction
                        val thin = 1.dp.toPx()
                        val thick = 3.dp.toPx()
                        drawLine(
                            color = progressColor,
                            start = androidx.compose.ui.geometry.Offset(0f, centerY),
                            end = androidx.compose.ui.geometry.Offset(size.width, centerY),
                            strokeWidth = thin
                        )
                        if (playedX > 0f) {
                            drawLine(
                                color = progressColor,
                                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                                end = androidx.compose.ui.geometry.Offset(playedX, centerY),
                                strokeWidth = thick
                            )
                        }
                    }
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(mediaTime(if (dragging) scrubPosition.toLong() else position), color = Color.White)
                Text(mediaTime(duration), color = Color.White)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(enabled = fullControlsInteractive, onClick = { settingsOpen = true; interactionVersion++ }) {
                        Icon(Icons.Outlined.Settings, localized(R.string.playback_settings), tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = settingsOpen,
                        onDismissRequest = { settingsOpen = false; interactionVersion++ },
                        containerColor = Color.Black.copy(alpha = 0.82f),
                        tonalElevation = 0.dp
                    ) {
                        DropdownMenuItem(
                            text = { Text(localized(R.string.autoplay_next), color = Color.White) },
                            trailingIcon = {
                                Switch(
                                    checked = slideshowEnabled,
                                    onCheckedChange = {
                                        PlaybackSettings.setSlideshowEnabled(it); interactionVersion++
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color.White.copy(alpha = 0.34f),
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = Color.Transparent,
                                        uncheckedBorderColor = Color.White.copy(alpha = 0.65f)
                                    )
                                )
                            },
                            onClick = { PlaybackSettings.setSlideshowEnabled(!slideshowEnabled); interactionVersion++ }
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    IconButton(enabled = fullControlsInteractive && seekable, onClick = { player.seekBack(); interactionVersion++ }) {
                        Icon(Icons.Outlined.Replay10, localized(R.string.rewind_10_seconds), tint = Color.White)
                    }
                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                        IconButton(enabled = fullControlsInteractive, onClick = {
                            if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) player.pause()
                            else {
                                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                                if (player.playerError != null) player.prepare()
                                player.play()
                            }
                            interactionVersion++
                        }, modifier = Modifier.size(56.dp), interactionSource = controlsInteraction) {
                            Icon(if (playRequested && player.playbackState != Player.STATE_ENDED) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                if (playRequested && player.playbackState != Player.STATE_ENDED) localized(R.string.pause) else localized(R.string.play),
                                modifier = Modifier.size(40.dp))
                        }
                    }
                    IconButton(enabled = fullControlsInteractive && seekable, onClick = { player.seekForward(); interactionVersion++ }) {
                        Icon(Icons.Outlined.Forward10, localized(R.string.forward_10_seconds), tint = Color.White)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!alwaysShowControls) IconButton(enabled = fullControlsInteractive, onClick = { onToggleFullscreen(); interactionVersion++ }) {
                    Icon(
                        if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        if (fullscreen) localized(R.string.exit_full_screen) else localized(R.string.full_screen),
                        tint = Color.White
                    )
                }
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
