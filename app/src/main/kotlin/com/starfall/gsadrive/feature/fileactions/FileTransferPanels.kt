package com.starfall.gsadrive.feature.fileactions

import com.starfall.gsadrive.R

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.LocalFileAccess
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.feature.accounts.AccountType
import com.starfall.gsadrive.feature.photos.PhotosFolderUploadMode
import com.starfall.gsadrive.shared.operations.ioResult
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.ui.CopyableError
import java.io.File

@Composable
internal fun TransferPanel(
    file: DriveFile,
    account: AccountEntry,
    actions: FileActionCallbacks,
    move: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val canUseCloud = account.type != AccountType.S3 && (move || actions.copy != null)
    val localRoot = actions.localRootPath
    val canUseSystem = localRoot != null && actions.transferToLocal != null
    var scope by remember(file.id, move) { mutableStateOf(if (canUseCloud) "cloud" else "system") }
    var cloudPath by remember(file.id, move) { mutableStateOf<List<DriveFile>>(emptyList()) }
    var cloudFolders by remember(file.id, move) { mutableStateOf<List<DriveFile>>(emptyList()) }
    var cloudLoading by remember(file.id, move) { mutableStateOf(false) }
    var cloudValid by remember(file.id, move) { mutableStateOf(false) }
    var localDestination by remember(file.id, move, localRoot) { mutableStateOf(localRoot.orEmpty()) }
    var localFolders by remember(file.id, move) { mutableStateOf<List<File>>(emptyList()) }
    var localLoading by remember(file.id, move) { mutableStateOf(false) }
    var localValid by remember(file.id, move) { mutableStateOf(false) }
    var transferring by remember(file.id, move) { mutableStateOf(false) }
    var error by remember(file.id, move) { mutableStateOf<String?>(null) }

    fun loadCloud(parentId: String?) {
        cloudLoading = true
        cloudValid = false
        error = null
        actions.loadFolders(parentId) { result ->
            cloudLoading = false
            result.onSuccess {
                cloudFolders = it.filterNot { folder -> folder.id == file.id }
                cloudValid = true
            }.onFailure { error = it.message ?: localized(R.string.unable_to_load_directory_listing) }
        }
    }

    LaunchedEffect(file.id, move, canUseCloud) {
        if (canUseCloud) loadCloud(null)
    }
    LaunchedEffect(scope, localDestination, localRoot) {
        if (scope != "system" || localRoot == null) return@LaunchedEffect
        localLoading = true
        localValid = false
        error = null
        val result = ioResult { LocalFileAccess(File(localRoot)).list(localDestination).filter { it.isDirectory } }
        localFolders = result.getOrDefault(emptyList())
        localValid = result.isSuccess
        error = result.exceptionOrNull()?.message
        localLoading = false
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, enabled = !transferring) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, localized(R.string.back))
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (move) localized(R.string.move) else localized(R.string.copy),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(file.name, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    if (canUseCloud && canUseSystem) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = scope == "cloud",
                onClick = { scope = "cloud"; error = null },
                label = { Text(account.title, maxLines = 1) },
                leadingIcon = { Icon(Icons.Outlined.Cloud, null, Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = scope == "system",
                onClick = { scope = "system"; error = null },
                label = { Text(localized(R.string.system_files)) },
                leadingIcon = { Icon(Icons.Outlined.Storage, null, Modifier.size(18.dp)) }
            )
        }
    }

    if (transferring || cloudLoading || localLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let {
        CopyableError(it, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    }

    if (scope == "cloud" && canUseCloud) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = !transferring && cloudPath.isNotEmpty(), onClick = {
                cloudPath = cloudPath.dropLast(1)
                loadCloud(cloudPath.lastOrNull()?.id)
            }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, localized(R.string.back)) }
            Text(cloudPath.lastOrNull()?.name ?: account.title, Modifier.weight(1f), maxLines = 1)
        }
        DestinationFolderList(
            folders = cloudFolders, key = { it.id }, name = { it.name },
            enabled = !transferring && !cloudLoading
        ) { folder ->
            cloudPath = cloudPath + folder
            loadCloud(folder.id)
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            Button(enabled = !transferring && !cloudLoading && cloudValid, onClick = {
                transferring = true
                error = null
                val destination = cloudPath.lastOrNull()?.id ?: "root"
                val complete: (Result<Unit>) -> Unit = { result ->
                    transferring = false
                    result.onSuccess { onDone() }
                        .onFailure { error = it.message ?: localized(R.string.file_transfer_could_not_be_completed) }
                }
                if (move) actions.move(file, destination, complete)
                else actions.copy?.invoke(file, destination, complete)
                    ?: complete(Result.failure(UnsupportedOperationException()))
            }) { Text(if (move) localized(R.string.move_here) else localized(R.string.copy_here)) }
        }
    } else if (canUseSystem) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                enabled = !transferring && localDestination != localRoot,
                onClick = { localDestination = File(localDestination).parent ?: localRoot }
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, localized(R.string.back)) }
            Text(localDestination, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DestinationFolderList(
            folders = localFolders, key = { it.path }, name = { it.name },
            enabled = !transferring && !localLoading
        ) { folder ->
            localDestination = folder.path
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            Button(enabled = !transferring && !localLoading && localValid, onClick = {
                transferring = true
                error = null
                actions.transferToLocal.invoke(file, localRoot, localDestination, move) { result ->
                    transferring = false
                    result.onSuccess { onDone() }
                        .onFailure { error = it.message ?: localized(R.string.file_transfer_could_not_be_completed) }
                }
            }) { Text(if (move) localized(R.string.move_here) else localized(R.string.copy_here)) }
        }
    } else {
        CopyableError(
            localized(R.string.there_are_no_target_locations_available),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(20.dp)
        )
    }
}

@Composable
internal fun PhotosFolderModePanel(
    files: List<DriveFile>,
    actions: FileActionCallbacks,
    multiple: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, localized(R.string.back)) }
        Column(Modifier.weight(1f)) {
            Text(localized(R.string.upload_to_photos), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (multiple) localized(R.string.arg_1_items_selected, files.size) else files.firstOrNull()?.name.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    ActionRow(Icons.Outlined.PhotoLibrary, localized(R.string.upload_directly)) {
        if (multiple) actions.uploadManyToPhotos?.invoke(files, PhotosFolderUploadMode.RAW)
        else files.firstOrNull()?.let { actions.uploadToPhotos?.invoke(it, PhotosFolderUploadMode.RAW) }
        onDone()
    }
    Text(
        localized(R.string.photos_and_videos_in_folders_are_uploaded_directly_to_the_photos_library_without_creating_an_album),
        modifier = Modifier.padding(start = 76.dp, end = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ActionRow(Icons.Outlined.PhotoAlbum, localized(R.string.upload_as_album)) {
        if (multiple) actions.uploadManyToPhotos?.invoke(files, PhotosFolderUploadMode.ALBUM)
        else files.firstOrNull()?.let { actions.uploadToPhotos?.invoke(it, PhotosFolderUploadMode.ALBUM) }
        onDone()
    }
    Text(
        localized(R.string.each_selected_root_folder_creates_an_album_with_the_same_name_containing_its_photos_and_videos),
        modifier = Modifier.padding(start = 76.dp, end = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun MoveManyPanel(
    files: List<DriveFile>,
    actions: FileActionCallbacks,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val selectionKey = remember(files) { files.map { it.id }.sorted().joinToString(":") }
    val selectedFolderIds = remember(files) { files.filter { it.isFolder }.map { it.id }.toSet() }
    var path by remember(selectionKey) { mutableStateOf<List<DriveFile>>(emptyList()) }
    var folders by remember(selectionKey) { mutableStateOf<List<DriveFile>>(emptyList()) }
    var loading by remember(selectionKey) { mutableStateOf(true) }
    var moving by remember(selectionKey) { mutableStateOf(false) }
    var error by remember(selectionKey) { mutableStateOf<String?>(null) }

    fun load(parentId: String?) {
        loading = true
        error = null
        actions.loadFolders(parentId) { result ->
            loading = false
            result.onSuccess { folders = it.filterNot { folder -> folder.id in selectedFolderIds } }
                .onFailure { error = it.message ?: localized(R.string.unable_to_load_directory_listing) }
        }
    }

    LaunchedEffect(selectionKey) { load(null) }

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (path.isEmpty()) onBack() else {
                path = path.dropLast(1)
                load(path.lastOrNull()?.id)
            }
        }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, localized(R.string.back)) }
        Column(Modifier.weight(1f)) {
            Text(localized(R.string.move_arg_1_items, files.size), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                path.lastOrNull()?.name ?: localized(R.string.my_drive),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (loading || moving) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let {
        CopyableError(
            it,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
    DestinationFolderList(
        folders = folders, key = { it.id }, name = { it.name },
        enabled = !loading && !moving
    ) { folder ->
        path = path + folder
        load(folder.id)
    }
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
        Button(enabled = !loading && !moving, onClick = {
            moving = true
            error = null
            val destination = path.lastOrNull()?.id ?: "root"
            actions.moveMany(files, destination) { result ->
                moving = false
                result.onSuccess { onDone() }
                    .onFailure { error = it.message ?: localized(R.string.selected_items_cannot_be_moved) }
            }
        }) { Text(localized(R.string.move_here)) }
    }
}

/** One destination-list presentation for single/multiple selection and local/cloud folders. */
@Composable
private fun <T> DestinationFolderList(
    folders: List<T>,
    key: (T) -> String,
    name: (T) -> String,
    enabled: Boolean,
    onOpen: (T) -> Unit
) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 380.dp)) {
        items(folders, key = key) { folder ->
            ListItem(
                headlineContent = { Text(name(folder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { Icon(Icons.Outlined.Folder, null) },
                modifier = Modifier.clickable(enabled = enabled) { onOpen(folder) }
            )
        }
    }
}
