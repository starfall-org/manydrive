package com.starfall.gsadrive.feature.photos

import com.starfall.gsadrive.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.model.Model
import com.starfall.gsadrive.shared.ui.CopyableError

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GooglePhotosPage(
    model: Model,
    padding: PaddingValues,
    open: (DriveFile, List<DriveFile>) -> Unit
) {
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var actionTarget by remember { mutableStateOf<DriveFile?>(null) }
    var infoTarget by remember { mutableStateOf<DriveFile?>(null) }
    val selectionMode = selectedIds.isNotEmpty()

    BackHandler(enabled = selectionMode && actionTarget == null && infoTarget == null) {
        selectedIds = emptySet()
    }

    Box(Modifier.fillMaxSize().padding(padding)) {
        val mosaicBlocks = remember(model.files) { buildPhotoMosaic(model.files) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            model.message?.let { message ->
                item(key = "photos-error") {
                    com.starfall.gsadrive.shared.ui.CopyableError(
                        message,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (!model.loading && model.message == null && model.files.isEmpty()) {
                item(key = "photos-empty") {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            localized(R.string.no_photos_or_videos_uploaded_by_this_app_to_google_photos_yet),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(mosaicBlocks, key = { it.key }) { block ->
                when (block.kind) {
                    PhotoMosaicKind.ROW -> {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            block.files.forEach { file ->
                                PhotoMosaicTile(
                                    file = file,
                                    allFiles = model.files,
                                    selectionMode = selectionMode,
                                    selected = file.id in selectedIds,
                                    featured = false,
                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                    onOpen = open,
                                    onToggleSelection = { selectedIds = togglePhotoSelection(selectedIds, file.id) },
                                    onLongSelect = {
                                        selectedIds = selectedIds + file.id
                                        actionTarget = file
                                    }
                                )
                            }
                        }
                    }

                    PhotoMosaicKind.WIDE -> {
                        val file = block.files.first()
                        PhotoMosaicTile(
                            file = file,
                            allFiles = model.files,
                            selectionMode = selectionMode,
                            selected = file.id in selectedIds,
                            featured = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.65f),
                            onOpen = open,
                            onToggleSelection = { selectedIds = togglePhotoSelection(selectedIds, file.id) },
                            onLongSelect = {
                                selectedIds = selectedIds + file.id
                                actionTarget = file
                            }
                        )
                    }

                    PhotoMosaicKind.HERO_LEFT, PhotoMosaicKind.HERO_RIGHT -> {
                        val hero = block.files.first()
                        val side = block.files.drop(1)
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val spacing = 2.dp
                            val smallWidth = (maxWidth - spacing * 2) / 3
                            val clusterHeight = smallWidth * 3 + spacing * 2
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing)
                            ) {
                                if (block.kind == PhotoMosaicKind.HERO_LEFT) {
                                    PhotoMosaicTile(
                                        file = hero,
                                        allFiles = model.files,
                                        selectionMode = selectionMode,
                                        selected = hero.id in selectedIds,
                                        featured = true,
                                        modifier = Modifier.weight(2f).height(clusterHeight),
                                        onOpen = open,
                                        onToggleSelection = { selectedIds = togglePhotoSelection(selectedIds, hero.id) },
                                        onLongSelect = {
                                            selectedIds = selectedIds + hero.id
                                            actionTarget = hero
                                        }
                                    )
                                }

                                Column(
                                    Modifier.weight(1f).height(clusterHeight),
                                    verticalArrangement = Arrangement.spacedBy(spacing)
                                ) {
                                    side.forEach { file ->
                                        PhotoMosaicTile(
                                            file = file,
                                            allFiles = model.files,
                                            selectionMode = selectionMode,
                                            selected = file.id in selectedIds,
                                            featured = false,
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                            onOpen = open,
                                            onToggleSelection = { selectedIds = togglePhotoSelection(selectedIds, file.id) },
                                            onLongSelect = {
                                                selectedIds = selectedIds + file.id
                                                actionTarget = file
                                            }
                                        )
                                    }
                                }

                                if (block.kind == PhotoMosaicKind.HERO_RIGHT) {
                                    PhotoMosaicTile(
                                        file = hero,
                                        allFiles = model.files,
                                        selectionMode = selectionMode,
                                        selected = hero.id in selectedIds,
                                        featured = true,
                                        modifier = Modifier.weight(2f).height(clusterHeight),
                                        onOpen = open,
                                        onToggleSelection = { selectedIds = togglePhotoSelection(selectedIds, hero.id) },
                                        onLongSelect = {
                                            selectedIds = selectedIds + hero.id
                                            actionTarget = hero
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectionMode && actionTarget == null) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                tonalElevation = 3.dp
            ) {
                Text(
                    localized(R.string.arg_1_selected, selectedIds.size),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    actionTarget?.let { file ->
        PhotoItemActionsSheet(
            file = file,
            selectedCount = selectedIds.size,
            onDismiss = { actionTarget = null },
            onOpen = {
                actionTarget = null
                selectedIds = emptySet()
                open(file, model.files)
            },
            onInfo = {
                actionTarget = null
                infoTarget = file
            },
            onSelectAll = {
                selectedIds = model.files.mapTo(linkedSetOf()) { it.id }
                actionTarget = null
            },
            onDeselect = {
                selectedIds = selectedIds - file.id
                actionTarget = null
            }
        )
    }

    infoTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { infoTarget = null },
            title = { Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(file.mimeType)
                    file.modifiedTime?.takeIf { it.isNotBlank() }?.let {
                        Text(it.replace("T", " ").substringBefore("."))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { infoTarget = null }) { Text(localized(R.string.close)) }
            }
        )
    }
}
