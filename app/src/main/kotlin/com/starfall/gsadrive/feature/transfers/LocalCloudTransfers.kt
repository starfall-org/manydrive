package com.starfall.gsadrive.feature.transfers

import com.starfall.gsadrive.R

import android.content.Context
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.FileListCache
import com.starfall.gsadrive.feature.accounts.openStorage
import com.starfall.gsadrive.feature.accounts.StorageSession
import com.starfall.gsadrive.shared.operations.ioResult
import com.starfall.gsadrive.shared.i18n.localized
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class LocalCloudTransfers(
    private val context: Context,
    private val scope: CoroutineScope,
    private val listingCache: FileListCache
) {
    fun toCloud(file: DriveFile, sourceRoot: String, parentId: String?, move: Boolean,
        session: StorageSession, done: (Result<Unit>) -> Unit, onComplete: () -> Unit) {
        val account = session.account
        val notifications = UploadNotifications(context)
        notifications.running(UploadNotifications.Kind.DRIVE, completed = 0, total = null)
        scope.launch {
            var completed = 0
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val access = com.starfall.gsadrive.data.LocalFileAccess(File(sourceRoot))
                    val source = access.checked(file.id)
                    val files = access.tree(file.id)
                    val storage = session.openStorage()
                    val folders = mutableMapOf<String, String>()
                    for (local in files) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val destination = if (local == source) parentId else folders.getValue(requireNotNull(local.parentFile).path)
                        if (local.isDirectory) {
                            folders[local.path] = storage.createFolder(local.name, destination)
                        } else {
                            notifications.running(
                                UploadNotifications.Kind.DRIVE,
                                currentName = local.name, completed = completed, total = null
                            )
                            val mime = android.webkit.MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(local.extension.lowercase()) ?: "application/octet-stream"
                            storage.upload(local.name, mime, local, destination)
                            completed++
                        }
                    }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (move) access.delete(file.id)
                }
            }
            withContext(kotlinx.coroutines.NonCancellable) {
                withContext(Dispatchers.IO) { listingCache.clear(account.key) }
                notifications.finished(
                    UploadNotifications.Kind.DRIVE,
                    if (result.isSuccess) {
                        if (move) localized(R.string.moved_arg_1_files, completed) else localized(R.string.copied_arg_1_files, completed)
                    } else localized(R.string.file_transfer_could_not_be_completed),
                    success = result.isSuccess
                )
                if (result.exceptionOrNull() !is CancellationException) {
                    done(result)
                    onComplete()
                }
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        }
    }


    fun toLocal(file: DriveFile, localRoot: String, destinationPath: String, move: Boolean,
        session: StorageSession, done: (Result<Unit>) -> Unit, onComplete: () -> Unit) {
        val account = session.account
        val accountKey = account.key
        scope.launch {
            val result = ioResult {
                val access = com.starfall.gsadrive.data.LocalFileAccess(File(localRoot))
                access.list(destinationPath) // Validate that the destination is inside the selected system root.
                val destination = File(destinationPath).canonicalFile
                fun safeLocalName(name: String): String {
                    require(
                        name.isNotBlank() && name !in setOf(".", "..") &&
                            name.none { it == '/' || it == '\\' || it.isISOControl() }
                    ) { localized(R.string.invalid_file_name_for_system_files) }
                    return name
                }
                val target = File(destination, safeLocalName(file.name))
                require(!target.exists()) { localized(R.string.the_destination_already_has_an_item_with_the_same_name) }
                val staging = File(destination, ".manydrive-${java.util.UUID.randomUUID()}")
                val storage = session.openStorage()
                suspend fun copyTree(source: DriveFile, output: File) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (source.isFolder) {
                        check(output.mkdirs()) { localized(R.string.unable_to_create_destination_folder) }
                        storage.list(source.id).forEach { child ->
                            copyTree(child, File(output, safeLocalName(child.name)))
                        }
                    } else {
                        output.parentFile?.mkdirs()
                        storage.downloadTo(source.id, output)
                    }
                }
                try {
                    copyTree(file, staging)
                    check(staging.renameTo(target)) { localized(R.string.unable_to_complete_copy_to_file_system) }
                } catch (failure: Throwable) {
                    staging.deleteRecursively()
                    throw failure
                }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (move) {
                    storage.remove(file)
                    listingCache.clear(accountKey)
                }
            }
            done(result)
            if (move && result.isSuccess) onComplete()
        }
    }

}
