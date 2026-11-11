package com.starfall.gsadrive.app.preview

import com.starfall.gsadrive.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.shared.i18n.localized

@Composable
internal fun FileRow(file: DriveFile, onDelete: ((DriveFile) -> Unit)? = null,
    onOpen: (DriveFile) -> Unit = {}, enabled: Boolean = true,
    onPreview: (DriveFile) -> Unit = {}) =
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) {
        if (file.isFolder) onOpen(file) else onPreview(file)
    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (file.isFolder) Icons.Outlined.Folder else Icons.Outlined.Description,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        ); Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name); Text(
            file.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        }
        onDelete?.let { IconButton(onClick = { it(file) }) { Icon(Icons.Outlined.Delete, localized(R.string.move_to_trash)) } }
    }
