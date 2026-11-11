package com.starfall.gsadrive.feature.fileactions

import com.starfall.gsadrive.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import com.starfall.gsadrive.data.DrivePermission
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.ui.CopyableError

@Composable
internal fun ShareDialog(file: DriveFile, actions: FileActionCallbacks, onDismiss: () -> Unit, onDone: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("reader") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(localized(R.string.share_arg_1, file.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(email, { email = it }, label = { Text(localized(R.string.email)) }, singleLine = true,
                    enabled = !busy, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = role == "reader", onClick = { role = "reader" }, enabled = !busy)
                    Text(localized(R.string.viewer))
                    Spacer(Modifier.width(12.dp))
                    RadioButton(selected = role == "writer", onClick = { role = "writer" }, enabled = !busy)
                    Text(localized(R.string.editor))
                }
                error?.let { CopyableError(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && email.contains('@'), onClick = {
                busy = true; error = null
                actions.share(file, email.trim(), role) { result ->
                    busy = false
                    result.onSuccess { onDone() }.onFailure { error = it.message ?: localized(R.string.cannot_be_shared) }
                }
            }) { Text(localized(R.string.share)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(localized(R.string.cancel)) } }
    )
}

@Composable
internal fun RenameDialog(file: DriveFile, actions: FileActionCallbacks, onDismiss: () -> Unit, onDone: () -> Unit) {
    var name by remember(file.id) { mutableStateOf(file.name) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(localized(R.string.rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(localized(R.string.new_name)) }, singleLine = true,
                    enabled = !busy, modifier = Modifier.fillMaxWidth())
                error?.let { CopyableError(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank() && name.trim() != file.name, onClick = {
                busy = true; error = null
                actions.rename(file, name.trim()) { result ->
                    busy = false
                    result.onSuccess { onDone() }.onFailure { error = it.message ?: localized(R.string.cannot_change_name) }
                }
            }) { Text(localized(R.string.rename)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(localized(R.string.cancel)) } }
    )
}

@Composable
internal fun PermissionsPanel(file: DriveFile, actions: FileActionCallbacks, onBack: () -> Unit) {
    var permissions by remember(file.id) { mutableStateOf<List<DrivePermission>>(emptyList()) }
    var loading by remember(file.id) { mutableStateOf(true) }
    var error by remember(file.id) { mutableStateOf<String?>(null) }

    fun reload() {
        loading = true; error = null
        actions.loadPermissions(file) { result ->
            loading = false
            result.onSuccess { permissions = it }.onFailure { error = it.message ?: localized(R.string.could_not_get_permissions) }
        }
    }
    LaunchedEffect(file.id) { reload() }

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, localized(R.string.back)) }
        Text(localized(R.string.manage_access), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { CopyableError(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp)) }
    if (!loading && error == null && permissions.isEmpty()) {
        Text(localized(R.string.no_individual_sharing_permissions), modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
        items(permissions, key = { it.id }) { permission ->
            ListItem(
                headlineContent = { Text(permission.displayName ?: permission.emailAddress ?: permission.type) },
                supportingContent = { Text("${permission.emailAddress.orEmpty()}${if (permission.emailAddress != null) " · " else ""}${roleLabel(permission.role)}") },
                leadingContent = { Icon(if (permission.type == "anyone") Icons.Outlined.Public else Icons.Outlined.Person, null) },
                trailingContent = {
                    if (permission.role != "owner") {
                        IconButton(onClick = {
                            actions.removePermission(file, permission) { result ->
                                result.onSuccess { reload() }.onFailure { error = it.message ?: localized(R.string.cannot_remove_permissions) }
                            }
                        }) { Icon(Icons.Outlined.PersonRemove, localized(R.string.remove_access)) }
                    }
                }
            )
        }
    }
}

internal fun roleLabel(role: String): String = when (role) {
    "owner" -> localized(R.string.owner)
    "writer" -> localized(R.string.editor)
    "commenter" -> localized(R.string.commenter)
    "reader" -> localized(R.string.viewer)
    else -> role
}
