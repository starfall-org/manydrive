package com.starfall.gsadrive.feature.accounts

import com.starfall.gsadrive.R

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.shared.i18n.localized

@Composable
internal fun AccountTypeDialog(onDismiss: () -> Unit, onGoogle: () -> Unit, onS3: () -> Unit, onService: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localized(R.string.add_account)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountTypeButton(Icons.Outlined.AccountCircle, "Google", localized(R.string.account_on_this_device), onGoogle)
                AccountTypeButton(Icons.Outlined.Cloud, "S3", localized(R.string.enter_connection_details), onS3)
                AccountTypeButton(Icons.Outlined.Key, "Service Account", localized(R.string.import_a_json_file), onService)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(localized(R.string.cancel)) } }
    )
}
