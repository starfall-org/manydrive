package com.starfall.gsadrive.feature.browser

import com.starfall.gsadrive.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.model.Model
import com.starfall.gsadrive.shared.ui.CopyableError
import com.starfall.gsadrive.shared.ui.FileListRow

@Composable
internal fun TrashPage(
    model: Model,
    padding: PaddingValues,
    restore: (List<DriveFile>) -> Unit,
    deletePermanently: (List<DriveFile>) -> Unit
) {
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectedFiles = remember(model.files, selectedIds) {
        model.files.filter { it.id in selectedIds }
    }
    val selectionMode = selectedIds.isNotEmpty()

    BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

    Column(Modifier.fillMaxSize().padding(padding)) {
        if (selectionMode) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedIds = emptySet() }) {
                        Icon(Icons.Outlined.Close, localized(R.string.clear_selection))
                    }
                    Text(
                        localized(R.string.arg_1_selected, selectedIds.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        enabled = selectedIds.size < model.files.size,
                        onClick = { selectedIds = model.files.mapTo(linkedSetOf()) { it.id } }
                    ) {
                        Icon(Icons.Outlined.SelectAll, localized(R.string.select_all))
                    }
                    IconButton(
                        enabled = !model.loading && selectedFiles.isNotEmpty(),
                        onClick = {
                            val files = selectedFiles
                            selectedIds = emptySet()
                            restore(files)
                        }
                    ) {
                        Icon(Icons.Outlined.Restore, localized(R.string.restore))
                    }
                    IconButton(
                        enabled = !model.loading && selectedFiles.isNotEmpty(),
                        onClick = {
                            val files = selectedFiles
                            selectedIds = emptySet()
                            deletePermanently(files)
                        }
                    ) {
                        Icon(Icons.Outlined.DeleteForever, localized(R.string.delete_permanently))
                    }
                }
            }
        }

        model.message?.let {
            CopyableError(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }
        if (!model.loading && model.message == null && model.files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(localized(R.string.trash_is_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(model.files, key = { it.id }) { file ->
                    FileListRow(
                        file = file,
                        shared = false,
                        onOpen = {},
                        onPreview = {},
                        selectionMode = selectionMode,
                        selected = file.id in selectedIds,
                        onToggleSelection = { selected ->
                            selectedIds = if (selected.id in selectedIds) selectedIds - selected.id else selectedIds + selected.id
                        },
                        onLongSelect = { selected -> selectedIds = selectedIds + selected.id },
                        onMenu = null
                    )
                }
            }
        }
    }
}
