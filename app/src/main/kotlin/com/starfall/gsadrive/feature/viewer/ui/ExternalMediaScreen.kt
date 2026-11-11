package com.starfall.gsadrive.feature.viewer.ui

import com.starfall.gsadrive.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.shared.i18n.localized

@Composable
internal fun ExternalMediaScreen(
    file: DriveFile?,
    loading: Boolean,
    error: String?,
    player: MediaController?,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    when {
        loading -> Box(
            Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        error != null -> Box(
            Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(error, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                FilledTonalButton(onClick = onBack) { Text(localized(R.string.close)) }
            }
        }

        file == null -> Unit

        file.mimeType.startsWith("image/") -> Box(
            Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            ImageViewer(file.id)
        }

        player != null -> ExpandableMediaPlayer(
            player = player,
            minimized = false,
            topPadding = 0.dp,
            miniBounds = null,
            onMinimize = onBack,
            onExpand = {},
            onClose = onBack,
            closeOnBack = true,
            queue = listOf(file),
            index = 0,
            previewPaths = emptyMap(),
            error = null,
            onSwipeTo = {}
        )
    }
}
