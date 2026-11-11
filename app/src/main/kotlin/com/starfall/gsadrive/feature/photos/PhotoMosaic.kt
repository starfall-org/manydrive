package com.starfall.gsadrive.feature.photos

import com.starfall.gsadrive.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.media.ThumbnailRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class PhotoMosaicKind { ROW, WIDE, HERO_LEFT, HERO_RIGHT }

internal data class PhotoMosaicBlock(
    val kind: PhotoMosaicKind,
    val files: List<DriveFile>
) {
    val key: String = kind.name + ":" + files.joinToString("|") { it.id }
}

internal fun buildPhotoMosaic(files: List<DriveFile>): List<PhotoMosaicBlock> {
    if (files.isEmpty()) return emptyList()
    val blocks = mutableListOf<PhotoMosaicBlock>()
    var index = 0

    fun addSmallRow() {
        if (index >= files.size) return
        val count = minOf(3, files.size - index)
        blocks += PhotoMosaicBlock(PhotoMosaicKind.ROW, files.subList(index, index + count))
        index += count
    }

    // Match the Google Photos rhythm: two compact rows before the first featured card.
    addSmallRow()
    addSmallRow()

    val featuredCycle = listOf(
        PhotoMosaicKind.WIDE,
        PhotoMosaicKind.HERO_RIGHT,
        PhotoMosaicKind.WIDE,
        PhotoMosaicKind.HERO_LEFT
    )
    var featuredIndex = 0

    while (index < files.size) {
        val kind = featuredCycle[featuredIndex % featuredCycle.size]
        val required = if (kind == PhotoMosaicKind.WIDE) 1 else 4

        if (files.size - index >= required) {
            blocks += PhotoMosaicBlock(kind, files.subList(index, index + required))
            index += required
            featuredIndex++
        } else {
            // Not enough media for a complete featured block: finish with compact rows, no holes.
            while (index < files.size) addSmallRow()
            break
        }

        // Separate featured cards with one or two compact rows. Keep the choice stable for
        // the current media order so recomposition/scrolling never reshuffles the layout.
        val separatorRows = 1 + ((blocks.last().key.hashCode() and Int.MAX_VALUE) % 2)
        repeat(separatorRows) {
            if (index < files.size) addSmallRow()
        }
    }
    return blocks
}

internal fun togglePhotoSelection(selectedIds: Set<String>, id: String): Set<String> =
    if (id in selectedIds) selectedIds - id else selectedIds + id

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhotoMosaicTile(
    file: DriveFile,
    allFiles: List<DriveFile>,
    selectionMode: Boolean,
    selected: Boolean,
    featured: Boolean,
    modifier: Modifier,
    onOpen: (DriveFile, List<DriveFile>) -> Unit,
    onToggleSelection: () -> Unit,
    onLongSelect: () -> Unit
) {
    val shape = RoundedCornerShape(if (featured) 4.dp else 2.dp)
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(
                onClick = {
                    if (selectionMode) onToggleSelection() else onOpen(file, allFiles)
                },
                onLongClick = onLongSelect
            ),
        contentAlignment = Alignment.Center
    ) {
        val thumbnailUrl = remember(file.thumbnailUrl, featured) {
            photoThumbnailUrl(file.thumbnailUrl, featured)
        }
        val context = LocalContext.current.applicationContext
        val thumbnailCacheKey = remember(file.id, featured) {
            "google-photos:${file.id}:${if (featured) "featured" else "tile"}"
        }
        val bitmap by produceState(
            initialValue = ThumbnailRepository.cached(thumbnailCacheKey),
            key1 = thumbnailUrl,
            key2 = thumbnailCacheKey
        ) {
            if (value == null) {
                value = withContext(Dispatchers.IO) {
                    thumbnailUrl?.let {
                        runCatching { ThumbnailRepository.load(context, it, cacheKey = thumbnailCacheKey) }.getOrNull()
                    }
                }
            }
        }

        bitmap?.let {
            Image(
                it.asImageBitmap(),
                file.name,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: Icon(
            Icons.Outlined.Image,
            null,
            Modifier.size(if (featured) 42.dp else 28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (file.mimeType.startsWith("video/")) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (featured) 12.dp else 7.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.58f),
                contentColor = Color.White
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    localized(R.string.videos),
                    Modifier.padding(4.dp).size(if (featured) 24.dp else 18.dp)
                )
            }
        }

        if (selectionMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(if (featured) 12.dp else 7.dp)
                    .size(if (featured) 32.dp else 28.dp),
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary
                    else Color.Black.copy(alpha = 0.22f),
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.9f))
            ) {
                if (selected) {
                    Icon(Icons.Outlined.Check, localized(R.string.selected), Modifier.padding(4.dp))
                }
            }
        }
    }
}

private fun photoThumbnailUrl(url: String?, featured: Boolean): String? {
    if (url == null) return null
    val suffix = if (featured) "=w1200-h1800-c" else "=w520-h520-c"
    return if (url.matches(Regex(".*=w\\d+-h\\d+.*"))) {
        url.replace(Regex("=w\\d+-h\\d+.*$"), suffix)
    } else {
        url
    }
}
