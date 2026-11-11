package com.starfall.gsadrive.app.preview

import com.starfall.gsadrive.R

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
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.model.Model
import com.starfall.gsadrive.shared.ui.CopyableError

@Composable
internal fun StoragePage(
    model: Model,
    padding: PaddingValues,
    account: AccountEntry?,
    add: () -> Unit,
    accounts: () -> Unit,
    signOut: () -> Unit,
    shared: Boolean = false,
    openFolder: (DriveFile) -> Unit = {},
    goUp: () -> Unit = {},
    openFile: (DriveFile) -> Unit = {}
) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (account == null) item {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localized(R.string.your_files), style = MaterialTheme.typography.titleLarge)
                    Text(localized(R.string.connect_google_s3_or_a_service_account_to_view_files))
                    FilledTonalButton(onClick = add) { Text(localized(R.string.add_account)) }
                    TextButton(onClick = accounts) { Text(localized(R.string.saved_accounts)) }
                }
            }
        }
        if (shared) item { Text(localized(R.string.shared_with_me), style = MaterialTheme.typography.titleMedium) }
        model.message?.let { item { CopyableError(it, color = MaterialTheme.colorScheme.error) } }
        if (account != null && !model.loading && model.message == null && model.files.isEmpty()) item { Text(localized(R.string.no_files_to_display)) }
        items(model.files, key = { it.id }) { FileRow(it, onOpen = openFolder, onPreview = openFile, enabled = !model.loading) }
    }
}

@Composable
internal fun DrivePage(
    model: Model,
    padding: PaddingValues,
    signIn: () -> Unit,
    authorize: () -> Unit,
    signOut: () -> Unit,
    upload: () -> Unit,
    createFolder: (String) -> Unit,
    trash: (DriveFile) -> Unit,
    openFolder: (DriveFile) -> Unit = {},
    goUp: () -> Unit = {},
    openFile: (DriveFile) -> Unit = {}
) {
    var folderName by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(model.user?.let { "$it’s Drive" } ?: localized(R.string.storage_with_google_drive),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(if (model.user == null) localized(R.string.add_an_account_to_manage_files) else localized(R.string.tap_the_account_icon_to_switch_or_add_an_account))
                    Spacer(Modifier.height(12.dp))
                    if (model.user == null) FilledTonalButton(onClick = signIn) { Text(localized(R.string.add_account)) }
                    else if (model.token == null) FilledTonalButton(onClick = authorize) { Text(localized(R.string.allow_drive_access)) }
                    else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = upload) {
                                Icon(
                                    Icons.Outlined.UploadFile,
                                    null
                                ); Spacer(Modifier.width(6.dp)); Text(localized(R.string.upload_files))
                            }
                            FilledTonalButton(onClick = signOut) { Text(localized(R.string.sign_out)) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                            OutlinedTextField(
                                folderName,
                                { folderName = it },
                                label = { Text(localized(R.string.new_folder)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = { createFolder(folderName); folderName = "" },
                                enabled = folderName.isNotBlank(),
                                modifier = Modifier.padding(start = 8.dp)
                            ) { Text(localized(R.string.create)) }
                        }
                    }
                }
            }
        }
        model.message?.let { item { CopyableError(it, color = MaterialTheme.colorScheme.error) } }
        if (!model.loading && model.token != null && model.files.isEmpty()) item { Text(localized(R.string.there_are_no_files_in_this_location)) }
        items(model.files, key = { it.id }) { FileRow(it, trash, openFolder, !model.loading, onPreview = openFile) }
    }
}
