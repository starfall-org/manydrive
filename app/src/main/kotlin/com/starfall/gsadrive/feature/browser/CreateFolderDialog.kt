package com.starfall.gsadrive.feature.browser

import com.starfall.gsadrive.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.starfall.gsadrive.shared.i18n.localized

@Composable
internal fun CreateFolderDialog(name: String, onNameChange: (String) -> Unit, onDismiss: () -> Unit, onCreate: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localized(R.string.new_folder)) },
        text = { OutlinedTextField(name, onNameChange, label = { Text(localized(R.string.folder_name)) }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = onCreate) { Text(localized(R.string.create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localized(R.string.cancel)) } }
    )
}
