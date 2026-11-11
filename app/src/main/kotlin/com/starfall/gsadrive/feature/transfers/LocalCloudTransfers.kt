package com.starfall.gsadrive.feature.transfers

import com.starfall.gsadrive.R

import android.content.Context
import com.starfall.gsadrive.data.DriveApi
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.FileListCache
import com.starfall.gsadrive.data.LocalFileAccess
import com.starfall.gsadrive.data.S3Api
import com.starfall.gsadrive.data.ServiceAccountApi
import com.starfall.gsadrive.feature.accounts.AccountType
import com.starfall.gsadrive.feature.accounts.StorageSession
import com.starfall.gsadrive.shared.i18n.localized
import java.io.File
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
        val config = session.s3
        val service = session.service
        val existingToken = session.token
        val notifications = UploadNotifications(context)
        notifications.running(UploadNotifications.Kind.DRIVE, completed = 0, total = null)
        scope.launch {
            var completed = 0
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val access = com.starfall.gsadrive.data.LocalFileAccess(File(sourceRoot))
                    val source = access.checked(file.id)
                    val files = access.tree(file.id)
                    val token = if (account.type == AccountType.SERVICE)
                        ServiceAccountApi.accessToken(requireNotNull(service)).value else existingToken
                    val folders = mutableMapOf<String, String>()
                    for (local in files) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val destination = if (local == source) parentId else folders.getValue(requireNotNull(local.parentFile).path)
                        if (local.isDirectory) {
                            folders[local.path] = if (account.type == AccountType.S3) {
                                val key = destination.orEmpty() + local.name + "/"
                                S3Api.createFolder(requireNotNull(config), key)
                                key
                            } else DriveApi.createFolder(requireNotNull(token), local.name, destination)
                        } else {
                            notifications.running(
                                UploadNotifications.Kind.DRIVE,
                                currentName = local.name, completed = completed, total = null
                            )
                            val mime = android.webkit.MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(local.extension.lowercase()) ?: "application/octet-stream"
                            if (account.type == AccountType.S3) {
                                S3Api.upload(requireNotNull(config), destination.orEmpty() + local.name, mime, local)
                            } else {
                                local.inputStream().use {
                                    DriveApi.upload(requireNotNull(token), local.name, mime, it, destination)
                                }
                            }
                            completed++
                        }
                    }
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
                done(result)
                onComplete()
            }
        }
    }


    fun toLocal(file: DriveFile, localRoot: String, destinationPath: String, move: Boolean,
        session: StorageSession, done: (Result<Unit>) -> Unit, onComplete: () -> Unit) {
        val account = session.account
        val accountKey = account.key
        val config = session.s3
        val service = session.service
        val existingToken = session.token
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
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
                    val token = when (account.type) {
                        AccountType.GOOGLE -> requireNotNull(existingToken) { localized(R.string.need_to_grant_drive_permission_first) }
                        AccountType.SERVICE -> ServiceAccountApi.accessToken(requireNotNull(service)).value
                        AccountType.S3 -> null
                    }
                    suspend fun children(folder: DriveFile): List<DriveFile> = when (account.type) {
                        AccountType.S3 -> S3Api.list(requireNotNull(config), folder.id)
                        AccountType.GOOGLE, AccountType.SERVICE -> DriveApi.listFiles(requireNotNull(token), parentId = folder.id)
                    }
                    suspend fun copyTree(source: DriveFile, output: File) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        if (source.isFolder) {
                            check(output.mkdirs()) { localized(R.string.unable_to_create_destination_folder) }
                            children(source).forEach { child ->
                                copyTree(child, File(output, safeLocalName(child.name)))
                            }
                        } else {
                            output.parentFile?.mkdirs()
                            when (account.type) {
                                AccountType.S3 -> S3Api.downloadTo(requireNotNull(config), source.id, output)
                                AccountType.GOOGLE, AccountType.SERVICE -> DriveApi.downloadTo(requireNotNull(token), source.id, output)
                            }
                        }
                    }
                    try {
                        copyTree(file, staging)
                        check(staging.renameTo(target)) { localized(R.string.unable_to_complete_copy_to_file_system) }
                    } catch (failure: Throwable) {
                        staging.deleteRecursively()
                        throw failure
                    }
                    if (move) {
                        when (account.type) {
                            AccountType.S3 -> S3Api.delete(requireNotNull(config), file)
                            AccountType.GOOGLE, AccountType.SERVICE -> DriveApi.moveToTrash(requireNotNull(token), file.id)
                        }
                        listingCache.clear(accountKey)
                    }
                }
            }
            done(result)
            if (move && result.isSuccess) onComplete()
        }
    }

}
