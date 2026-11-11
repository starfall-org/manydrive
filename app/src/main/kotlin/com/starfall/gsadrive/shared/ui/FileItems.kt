package com.starfall.gsadrive.shared.ui

import com.starfall.gsadrive.R

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.media.ThumbnailRepository
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FileListRow(
    file: DriveFile,
    shared: Boolean,
    onOpen: (DriveFile) -> Unit,
    onPreview: (DriveFile) -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelection: (DriveFile) -> Unit,
    onLongSelect: (DriveFile) -> Unit,
    onMenu: ((DriveFile) -> Unit)?
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().combinedClickable(
                onClick = {
                    if (selectionMode) onToggleSelection(file)
                    else if (file.isFolder) onOpen(file) else onPreview(file)
                },
                onLongClick = { onLongSelect(file) }
            ).padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Icon(
                    if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    if (selected) localized(R.string.selected) else localized(R.string.not_selected),
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.width(10.dp))
            }
            if (shared) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(34.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Person, null, modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FileTypeIcon(file, Modifier.size(38.dp))
                }
            } else {
                FileTypeIcon(file, Modifier.size(42.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (shared) formatDriveDate(file.sharedWithMeTime ?: file.modifiedTime, localized(R.string.shared))
                        else formatDriveDate(file.modifiedTime, localized(R.string.modified)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onMenu != null) IconButton(onClick = { onMenu(file) }) { Icon(Icons.Outlined.MoreVert, localized(R.string.options)) }
        }
    }
}

@Composable
private fun rememberFileThumbnail(
    file: DriveFile,
    accessToken: String?,
    cacheNamespace: String
): androidx.compose.ui.graphics.ImageBitmap? {
    val url = file.thumbnailUrl ?: return null
    val context = LocalContext.current.applicationContext
    val cacheKey = "$cacheNamespace:${file.id}"
    val state = produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = ThumbnailRepository.cached(cacheKey)?.asImageBitmap(),
        key1 = url,
        key2 = cacheKey
    ) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    ThumbnailRepository.load(context, url, accessToken, cacheKey).asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return state.value
}

@Composable
internal fun FileGridCard(
    file: DriveFile,
    shared: Boolean,
    accessToken: String?,
    thumbnailCacheNamespace: String,
    onOpen: (DriveFile) -> Unit,
    onPreview: (DriveFile) -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelection: (DriveFile) -> Unit,
    onLongSelect: (DriveFile) -> Unit,
    onMenu: ((DriveFile) -> Unit)?
) {
    val thumbnail = rememberFileThumbnail(file, accessToken, thumbnailCacheNamespace)
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = {
                if (selectionMode) onToggleSelection(file)
                else if (file.isFolder) onOpen(file) else onPreview(file)
            },
            onLongClick = { onLongSelect(file) }
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Icon(
                        if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        if (selected) localized(R.string.selected) else localized(R.string.not_selected),
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                FileTypeIcon(file, Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    file.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (onMenu != null) IconButton(onClick = { onMenu(file) }) { Icon(Icons.Outlined.MoreVert, localized(R.string.options)) }
            }
            if (thumbnail != null && !file.isFolder) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (shared) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
                        ) {
                            Icon(
                                Icons.Outlined.Group,
                                contentDescription = localized(R.string.shared_file),
                                modifier = Modifier.padding(6.dp).size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                Text(
                    if (shared) formatDriveDate(file.sharedWithMeTime ?: file.modifiedTime, localized(R.string.shared))
                    else formatDriveDate(file.modifiedTime, localized(R.string.modified)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FileTypeIcon(file: DriveFile, modifier: Modifier = Modifier) {
    val icon = when {
        file.isFolder -> Icons.Outlined.Folder
        file.mimeType.startsWith("video/") -> Icons.Outlined.Movie
        file.mimeType.startsWith("audio/") -> Icons.Outlined.AudioFile
        file.mimeType.startsWith("image/") -> Icons.Outlined.Image
        else -> Icons.Outlined.Description
    }
    if (file.isFolder) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(icon, null, modifier = Modifier.fillMaxSize(), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(26.dp),
                    tint = if (file.mimeType.startsWith("video/")) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatDriveDate(value: String?, prefix: String): String {
    if (value.isNullOrBlank()) return prefix
    val parsed = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(value)
            ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(value)
    }.getOrNull() ?: return "$prefix ${value.substringBefore('T')}"
    val formatted = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(parsed)
    return "$prefix $formatted"
}
