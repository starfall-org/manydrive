package com.starfall.gsadrive.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.compose.material3.MiniController
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import com.starfall.gsadrive.data.DriveFile

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
    onBack: () -> Unit,
    onTextChange: (String) -> Unit,
    onSaveText: () -> Unit
) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().padding(padding)) {
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
                MediaViewer(player)
            else -> Text("Không thể xem loại tệp này.", Modifier.align(Alignment.Center))
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Không thể giải mã ảnh.") }
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentScale = ContentScale.Fit
        )
    }
}

/** Media3 PlayerView owns the video surface and controller, avoiding Compose surface detach/black-frame issues. */
@OptIn(UnstableApi::class)
@Composable
private fun MediaViewer(player: Player) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                useController = true
                controllerAutoShow = true
                controllerHideOnTouch = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepScreenOn = true
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
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
