package com.starfall.gsadrive.feature.fileactions

import com.starfall.gsadrive.R

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.feature.accounts.AccountType
import com.starfall.gsadrive.feature.photos.PhotosFolderUploadMode
import com.starfall.gsadrive.shared.i18n.localized

private enum class FileActionMode { MAIN, PERMISSIONS, COPY, MOVE, PHOTOS, INFO }
private enum class MultiFileActionMode { MAIN, MOVE, PHOTOS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MultiFileActionsSheet(
    files: List<DriveFile>,
    account: AccountEntry,
    actions: FileActionCallbacks,
    onDismiss: () -> Unit,
    onActionDone: () -> Unit
) {
    var mode by remember(files.map { it.id }) { mutableStateOf(MultiFileActionMode.MAIN) }
    val driveActions = account.type != AccountType.S3

    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        when (mode) {
            MultiFileActionMode.MAIN -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(18.dp))
                    Text(
                        localized(R.string.arg_1_items_selected, files.size),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (driveActions) {
                    ActionRow(Icons.Outlined.DriveFileMove, localized(R.string.move)) {
                        mode = MultiFileActionMode.MOVE
                    }
                }
                ActionRow(Icons.Outlined.AddPhotoAlternate, localized(R.string.upload_to_photos)) {
                    if (files.any { it.isFolder }) mode = MultiFileActionMode.PHOTOS
                    else {
                        actions.uploadManyToPhotos?.invoke(files, PhotosFolderUploadMode.RAW)
                        onActionDone()
                    }
                }
                if (actions.trashMany != null) {
                    ActionRow(Icons.Outlined.Delete, if (driveActions) localized(R.string.move_to_trash) else localized(R.string.delete)) {
                        actions.trashMany.invoke(files)
                        onActionDone()
                    }
                }
            }
            MultiFileActionMode.MOVE -> MoveManyPanel(
                files = files,
                actions = actions,
                onBack = { mode = MultiFileActionMode.MAIN },
                onDone = onActionDone
            )
            MultiFileActionMode.PHOTOS -> PhotosFolderModePanel(
                files = files,
                actions = actions,
                multiple = true,
                onBack = { mode = MultiFileActionMode.MAIN },
                onDone = onActionDone
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileActionsSheet(
    file: DriveFile,
    account: AccountEntry,
    actions: FileActionCallbacks,
    onDismiss: () -> Unit
) {
    var mode by remember(file.id) { mutableStateOf(FileActionMode.MAIN) }
    var showShare by remember(file.id) { mutableStateOf(false) }
    var showRename by remember(file.id) { mutableStateOf(false) }
    val driveActions = account.type != AccountType.S3

    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        when (mode) {
            FileActionMode.MAIN -> MainActions(
                file = file,
                driveActions = driveActions,
                actions = actions,
                onShare = { showShare = true },
                onPermissions = { mode = FileActionMode.PERMISSIONS },
                onRename = { showRename = true },
                onCopy = { mode = FileActionMode.COPY },
                onMove = { mode = FileActionMode.MOVE },
                onUploadPhotos = {
                    if (file.isFolder) mode = FileActionMode.PHOTOS
                    else {
                        actions.uploadToPhotos?.invoke(file, PhotosFolderUploadMode.RAW)
                        onDismiss()
                    }
                },
                onInfo = { mode = FileActionMode.INFO },
                onDismiss = onDismiss
            )
            FileActionMode.PERMISSIONS -> PermissionsPanel(
                file = file,
                actions = actions,
                onBack = { mode = FileActionMode.MAIN }
            )
            FileActionMode.COPY -> TransferPanel(
                file = file,
                account = account,
                actions = actions,
                move = false,
                onBack = { mode = FileActionMode.MAIN },
                onDone = onDismiss
            )
            FileActionMode.MOVE -> TransferPanel(
                file = file,
                account = account,
                actions = actions,
                move = true,
                onBack = { mode = FileActionMode.MAIN },
                onDone = onDismiss
            )
            FileActionMode.PHOTOS -> PhotosFolderModePanel(
                files = listOf(file),
                actions = actions,
                multiple = false,
                onBack = { mode = FileActionMode.MAIN },
                onDone = onDismiss
            )
            FileActionMode.INFO -> InfoPanel(file = file, onBack = { mode = FileActionMode.MAIN })
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showShare) ShareDialog(
        file = file,
        actions = actions,
        onDismiss = { showShare = false },
        onDone = { showShare = false }
    )
    if (showRename) RenameDialog(
        file = file,
        actions = actions,
        onDismiss = { showRename = false },
        onDone = { showRename = false; onDismiss() }
    )
}

@Composable
private fun MainActions(
    file: DriveFile,
    driveActions: Boolean,
    actions: FileActionCallbacks,
    onShare: () -> Unit,
    onPermissions: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onUploadPhotos: () -> Unit,
    onInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (file.isFolder) Icons.Outlined.Folder else Icons.Outlined.Description, null, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(18.dp))
        Text(file.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(8.dp))

    if (driveActions) {
        ActionRow(Icons.Outlined.PersonAdd, localized(R.string.share), onShare)
        ActionRow(Icons.Outlined.ManageAccounts, localized(R.string.manage_access), onPermissions)
        HorizontalDivider(Modifier.padding(start = 72.dp))
        ActionRow(Icons.Outlined.Link, localized(R.string.copy_link)) {
            val link = file.webViewUrl ?: "https://drive.google.com/open?id=${file.id}"
            clipboard.setText(AnnotatedString(link))
            Toast.makeText(context, localized(R.string.link_copied), Toast.LENGTH_SHORT).show()
            onDismiss()
        }
        HorizontalDivider(Modifier.padding(start = 72.dp))
        ActionRow(Icons.Outlined.Edit, localized(R.string.rename), onRename)
    }
    if (actions.copy != null || actions.transferToLocal != null) {
        ActionRow(Icons.Outlined.ContentCopy, localized(R.string.copy), onCopy)
    }
    if (driveActions || actions.transferToLocal != null) {
        ActionRow(Icons.Outlined.DriveFileMove, localized(R.string.move), onMove)
    }
    if (actions.uploadToPhotos != null && (file.isFolder || file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/"))) {
        ActionRow(Icons.Outlined.AddPhotoAlternate, localized(R.string.upload_to_google_photos), onUploadPhotos)
    }
    actions.download?.let { download ->
        ActionRow(Icons.Outlined.Download, localized(R.string.download)) {
            onDismiss()
            download(file)
        }
    }
    ActionRow(Icons.Outlined.Info, localized(R.string.view_details), onInfo)
    if (actions.trash != null) {
        ActionRow(Icons.Outlined.Delete, if (driveActions) localized(R.string.move_to_trash) else localized(R.string.delete)) {
            actions.trash.invoke(file)
            onDismiss()
        }
    }
}

@Composable
internal fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(24.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
