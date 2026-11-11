package com.starfall.gsadrive.feature.systemfiles

import com.starfall.gsadrive.R

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.LocalFileAccess
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.shared.i18n.localized
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalFileActions(
    file: DriveFile, access: LocalFileAccess,
    cloudDestination: String?,
    upload: (DriveFile, (Result<Unit>) -> Unit) -> Unit,
    cloudAccount: AccountEntry? = null,
    loadCloudFolders: (String?, (Result<List<DriveFile>>) -> Unit) -> Unit = { _, done ->
        done(Result.failure(IllegalStateException(localized(R.string.add_or_select_an_account_before_transferring_files))))
    },
    transferToCloud: (DriveFile, String?, Boolean, (Result<Unit>) -> Unit) -> Unit = { _, _, _, done ->
        done(Result.failure(IllegalStateException(localized(R.string.add_or_select_an_account_before_transferring_files))))
    },
    onChanged: () -> Unit, onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember(file.id) { mutableStateOf("menu") }
    var name by remember(file.id) { mutableStateOf(file.name) }
    var destination by remember(file.id) { mutableStateOf(access.root.path) }
    var destinationScope by remember(file.id) { mutableStateOf("system") }
    var cloudPath by remember(file.id) { mutableStateOf<List<DriveFile>>(emptyList()) }
    var cloudFolders by remember(file.id) { mutableStateOf<List<DriveFile>>(emptyList()) }
    var cloudFoldersLoading by remember(file.id) { mutableStateOf(false) }
    var cloudDestinationValid by remember(file.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var folders by remember { mutableStateOf<List<File>>(emptyList()) }
    var foldersLoading by remember { mutableStateOf(false) }
    var totalSize by remember { mutableStateOf<Long?>(file.size) }
    var destinationValid by remember { mutableStateOf(false) }
    LaunchedEffect(mode) {
        if (mode == "info" && file.isFolder) {
            val result = withContext(Dispatchers.IO) { runCatching { access.tree(file.id).filter { it.isFile }.sumOf { it.length() } } }
            totalSize = result.getOrNull()
            error = result.exceptionOrNull()?.message
        }
    }
    LaunchedEffect(destination, mode, destinationScope) {
        if (mode !in listOf("copy", "move") || destinationScope != "system") return@LaunchedEffect
        foldersLoading = true; destinationValid = false; error = null
        val result = withContext(Dispatchers.IO) { runCatching { access.list(destination).filter { it.isDirectory } } }
        folders = result.getOrDefault(emptyList())
        error = result.exceptionOrNull()?.message
        destinationValid = result.isSuccess
        foldersLoading = false
    }
    fun loadCloud(parentId: String?) {
        cloudFoldersLoading = true
        cloudDestinationValid = false
        error = null
        loadCloudFolders(parentId) { result ->
            cloudFoldersLoading = false
            result.onSuccess {
                cloudFolders = it
                cloudDestinationValid = true
            }.onFailure { error = it.message ?: localized(R.string.unable_to_load_directory_listing) }
        }
    }
    fun perform(operation: () -> Unit) {
        busy = true; error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(operation) }
            busy = false
            onChanged()
            if (result.isSuccess) onDismiss() else error = result.exceptionOrNull()?.message
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(file.name, style = MaterialTheme.typography.titleLarge)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (mode) {
                "menu" -> {
                    if (!file.isFolder) LocalAction(Icons.Outlined.OpenInNew, localized(R.string.open_with_another_app), !busy) {
                        runCatching { openLocalExternally(context, access.checked(file.id), file.mimeType, false) }
                            .onSuccess { onDismiss() }.onFailure { error = it.message }
                    }
                    LocalAction(Icons.Outlined.Edit, localized(R.string.rename), !busy) { mode = "rename" }
                    LocalAction(Icons.Outlined.ContentCopy, localized(R.string.copy), !busy) { mode = "copy" }
                    LocalAction(Icons.Outlined.DriveFileMove, localized(R.string.move), !busy) { mode = "move" }
                    LocalAction(Icons.Outlined.Delete, localized(R.string.delete), !busy) { mode = "delete" }
                    LocalAction(Icons.Outlined.Share, localized(R.string.share), !busy) {
                        busy = true; error = null
                        scope.launch {
                            val result = runCatching {
                                val shared = withContext(Dispatchers.IO) {
                                    if (file.isFolder) zipLocalFolder(context, access, file.id) else access.checked(file.id)
                                }
                                openLocalExternally(context, shared, if (file.isFolder) "application/zip" else file.mimeType, true)
                            }
                            busy = false
                            if (result.isSuccess) onDismiss() else error = result.exceptionOrNull()?.message
                        }
                    }
                    LocalAction(Icons.Outlined.CloudUpload, localized(R.string.upload_to_cloud_account), !busy) { mode = "upload" }
                    LocalAction(Icons.Outlined.Info, localized(R.string.details), !busy) { mode = "info" }
                }
                "rename" -> {
                    OutlinedTextField(name, { name = it }, label = { Text(localized(R.string.new_name)) }, enabled = !busy, singleLine = true)
                    FilledTonalButton(enabled = !busy && name.isNotBlank(), onClick = { perform { access.rename(file.id, name) } }) { Text(localized(R.string.rename)) }
                }
                "copy", "move" -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = destinationScope == "system",
                            onClick = { destinationScope = "system"; error = null },
                            label = { Text(localized(R.string.system_files)) },
                            leadingIcon = { Icon(Icons.Outlined.Storage, null, Modifier.size(18.dp)) }
                        )
                        cloudAccount?.let { account ->
                            FilterChip(
                                selected = destinationScope == "cloud",
                                onClick = {
                                    destinationScope = "cloud"
                                    cloudPath = emptyList()
                                    loadCloud(null)
                                },
                                label = { Text(account.title, maxLines = 1) },
                                leadingIcon = { Icon(Icons.Outlined.Cloud, null, Modifier.size(18.dp)) }
                            )
                        }
                    }
                    if (destinationScope == "system") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(enabled = !busy && destination != access.root.path, onClick = {
                                destination = File(destination).parent ?: access.root.path
                            }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, localized(R.string.back))
                            }
                            Text(destination, Modifier.weight(1f), maxLines = 1)
                        }
                        if (foldersLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        folders.forEach { folder ->
                            LocalAction(Icons.Outlined.Folder, folder.name, !busy) { destination = folder.path }
                        }
                        FilledTonalButton(
                            enabled = !busy && !foldersLoading && destinationValid,
                            onClick = { perform { access.transfer(file.id, destination, mode == "move") } }
                        ) {
                            Text(if (mode == "move") localized(R.string.move_here) else localized(R.string.copy_here))
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(enabled = !busy && cloudPath.isNotEmpty(), onClick = {
                                cloudPath = cloudPath.dropLast(1)
                                loadCloud(cloudPath.lastOrNull()?.id)
                            }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, localized(R.string.back))
                            }
                            Text(
                                cloudPath.lastOrNull()?.name ?: cloudAccount?.title.orEmpty(),
                                Modifier.weight(1f),
                                maxLines = 1
                            )
                        }
                        if (cloudFoldersLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        cloudFolders.forEach { folder ->
                            LocalAction(Icons.Outlined.Folder, folder.name, !busy && !cloudFoldersLoading) {
                                cloudPath = cloudPath + folder
                                loadCloud(folder.id)
                            }
                        }
                        FilledTonalButton(
                            enabled = !busy && !cloudFoldersLoading && cloudDestinationValid,
                            onClick = {
                                busy = true
                                error = null
                                transferToCloud(file, cloudPath.lastOrNull()?.id, mode == "move") { result ->
                                    busy = false
                                    if (result.isSuccess) {
                                        onChanged()
                                        onDismiss()
                                    } else {
                                        error = result.exceptionOrNull()?.message ?: localized(R.string.file_transfer_could_not_be_completed)
                                    }
                                }
                            }
                        ) {
                            Text(if (mode == "move") localized(R.string.move_here) else localized(R.string.copy_here))
                        }
                    }
                }
                "delete" -> {
                    Text(if (file.isFolder) localized(R.string.delete_this_folder_and_all_its_contents) else localized(R.string.delete_this_file))
                    Button(enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = { perform { access.delete(file.id) } }) { Text(localized(R.string.delete)) }
                }
                "upload" -> {
                    Text(cloudDestination ?: localized(R.string.add_or_select_an_account_before_uploading))
                    FilledTonalButton(enabled = !busy && cloudDestination != null, onClick = {
                        busy = true; error = null
                        upload(file) { result ->
                            busy = false
                            if (result.isSuccess) onDismiss() else error = result.exceptionOrNull()?.message
                        }
                    }) { Text(localized(R.string.upload)) }
                }
                "info" -> {
                    Text(localized(R.string.path) + ": " + file.id)
                    Text(localized(R.string.type) + ": " + if (file.isFolder) localized(R.string.folder) else file.mimeType)
                    totalSize?.let { Text(localized(R.string.size) + ": " + android.text.format.Formatter.formatFileSize(context, it)) }
                    Text(localized(R.string.modified) + ": " + file.modifiedTime.orEmpty())
                }
            }
            if (mode != "menu") TextButton(enabled = !busy, onClick = { mode = "menu"; error = null }) { Text(localized(R.string.back)) }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun LocalAction(icon: ImageVector, label: String, enabled: Boolean, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = action).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun openLocalExternally(context: Context, file: File, mime: String, share: Boolean) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
        if (share) { type = mime; putExtra(Intent.EXTRA_STREAM, uri) } else setDataAndType(uri, mime)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

private fun zipLocalFolder(context: Context, access: LocalFileAccess, path: String): File {
    val source = access.checked(path)
    val files = access.tree(path)
    val directory = File(context.cacheDir, "shared-exports").apply { mkdirs() }
    // Retain recent exports while another app may still be reading their granted URIs.
    directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
    val zip = File.createTempFile("${source.name.take(40)}-".padEnd(3, '_'), ".zip", directory)
    try {
        ZipOutputStream(zip.outputStream().buffered()).use { output ->
            files.forEach { file ->
                val relative = file.relativeTo(source.parentFile).invariantSeparatorsPath
                output.putNextEntry(ZipEntry(relative + if (file.isDirectory) "/" else ""))
                if (file.isFile) file.inputStream().use { it.copyTo(output) }
                output.closeEntry()
            }
        }
    } catch (error: Exception) { zip.delete(); throw error }
    return zip
}
