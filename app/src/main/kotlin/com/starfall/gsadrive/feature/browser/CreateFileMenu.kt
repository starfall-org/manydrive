package com.starfall.gsadrive.feature.browser

import com.starfall.gsadrive.R

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.shared.i18n.localized

@Composable
internal fun CreateFileMenu(
    expanded: Boolean, onToggle: () -> Unit,
    onUpload: () -> Unit, onUploadFolder: () -> Unit, onCreateFolder: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (expanded) {
            ExtendedFloatingActionButton(
                onClick = {
                    onUpload()
                },
                icon = { Icon(Icons.Outlined.UploadFile, null) },
                text = { Text(localized(R.string.upload)) },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            ExtendedFloatingActionButton(
                onClick = {
                    onUploadFolder()
                },
                icon = { Icon(Icons.Outlined.DriveFolderUpload, null) },
                text = { Text(localized(R.string.upload_folder)) },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            ExtendedFloatingActionButton(
                onClick = {
                    onCreateFolder()
                },
                icon = { Icon(Icons.Outlined.CreateNewFolder, null) },
                text = { Text(localized(R.string.folder)) },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        FloatingActionButton(
            onClick = onToggle,
            containerColor = if (expanded) MaterialTheme.colorScheme.inverseSurface
                else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (expanded) MaterialTheme.colorScheme.inverseOnSurface
                else MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(
                if (expanded) Icons.Outlined.Close else Icons.Outlined.Add,
                if (expanded) localized(R.string.close_create_menu) else localized(R.string.create_new)
            )
        }
    }
}
