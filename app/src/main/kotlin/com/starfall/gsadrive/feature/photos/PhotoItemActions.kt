package com.starfall.gsadrive.feature.photos

import com.starfall.gsadrive.R

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.shared.i18n.localized

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoItemActionsSheet(
    file: DriveFile,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onInfo: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselect: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Text(
            if (selectedCount > 1) localized(R.string.arg_1_selected, selectedCount) else file.name,
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PhotoActionButton(Icons.Outlined.Visibility, localized(R.string.see), onOpen)
            PhotoActionButton(Icons.Outlined.Info, localized(R.string.details), onInfo)
            PhotoActionButton(Icons.Outlined.DoneAll, localized(R.string.select_all), onSelectAll)
            PhotoActionButton(Icons.Outlined.Check, localized(R.string.deselect), onDeselect)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RowScope.PhotoActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.weight(1f).combinedClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Icon(icon, label, Modifier.padding(16.dp).size(26.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
