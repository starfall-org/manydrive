package com.starfall.gsadrive.feature.viewer.ui

import com.starfall.gsadrive.R

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.feature.viewer.isSwipePreview
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.ui.CopyableError
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
    // Natural item order: drag left to next, drag right to previous.
    val currentIndex by rememberUpdatedState(swipeIndex)
    val currentSwipe by rememberUpdatedState(onSwipeTo)

    val initialPage = swipeIndex.coerceIn(0, swipeQueue.lastIndex)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { swipeQueue.size })

    LaunchedEffect(swipeIndex, swipeQueue.size) {
        val target = swipeIndex
        if (target in 0 until swipeQueue.size && pagerState.currentPage != target && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        }
    }

    LaunchedEffect(pagerState, swipeQueue) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val target = page
                if (target != currentIndex && target in swipeQueue.indices) currentSwipe(target)
            }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { page -> swipeQueue[page].id }
    ) { page ->
        val index = page
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
                FilledTonalButton(onClick = onBack) { Text(localized(R.string.close)) }
            }
            text != null -> TextDocumentViewer(file.id, text, saving, onTextChange, onSaveText)
            localPath != null && file.mimeType.startsWith("image/") -> ImageViewer(localPath)
            localPath != null && (file.mimeType.startsWith("video/") || file.mimeType.startsWith("audio/")) ->
                MediaViewer(player, alwaysShowControls = file.mimeType.startsWith("audio/"))
            else -> Text(localized(R.string.this_file_type_cannot_be_previewed), Modifier.align(Alignment.Center))
        }
    }
}

@Composable
internal fun AdjacentPreview(file: DriveFile) {
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
