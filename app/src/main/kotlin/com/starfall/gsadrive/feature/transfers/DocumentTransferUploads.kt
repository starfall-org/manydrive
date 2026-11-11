package com.starfall.gsadrive.feature.transfers

import com.starfall.gsadrive.R

import android.content.Context
import android.net.Uri
import com.starfall.gsadrive.data.DocumentUploads
import com.starfall.gsadrive.data.DriveApi
import com.starfall.gsadrive.data.FileListCache
import com.starfall.gsadrive.data.S3Api
import com.starfall.gsadrive.data.ServiceAccountApi
import com.starfall.gsadrive.feature.accounts.AccountType
import com.starfall.gsadrive.feature.accounts.StorageSession
import com.starfall.gsadrive.shared.i18n.localized
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DocumentTransferUploads(
    private val context: Context,
    private val scope: CoroutineScope,
    private val listingCache: FileListCache
) {
    fun upload(uris: List<Uri>, tree: Uri?, parent: String?, session: StorageSession, onComplete: () -> Unit) {
        val account = session.account
        val s3 = session.s3
        val service = session.service
        val existingToken = session.token
        val uploadNotifications = UploadNotifications(context)
        val total = if (tree == null) uris.size else null
        uploadNotifications.running(UploadNotifications.Kind.DRIVE, completed = 0, total = total)
        scope.launch {
            var completed = 0
            var currentName: String? = null
            var cancelled = false
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val token = if (account.type == AccountType.SERVICE) ServiceAccountApi.accessToken(requireNotNull(service)).value
                        else existingToken
                    val uploader = DocumentUploads(
                        context.contentResolver,
                        createFolder = { name, destination ->
                            if (account.type == AccountType.S3) {
                                val key = destination.orEmpty() + name + "/"
                                S3Api.createFolder(requireNotNull(s3), key)
                                key
                            } else DriveApi.createFolder(
                                requireNotNull(token) { localized(R.string.please_grant_drive_permission_before_uploading) },
                                name,
                                destination
                            )
                        },
                        uploadFile = { uri, name, mime, destination ->
                            currentName = name
                            uploadNotifications.running(
                                UploadNotifications.Kind.DRIVE,
                                currentName = name,
                                completed = completed,
                                total = total
                            )
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                if (account.type == AccountType.S3) {
                                    val temporary = File.createTempFile("upload-", ".tmp", context.cacheDir)
                                    try {
                                        temporary.outputStream().use { input.copyTo(it, 64 * 1024) }
                                        S3Api.upload(requireNotNull(s3), destination.orEmpty() + name, mime, temporary)
                                    } finally {
                                        temporary.delete()
                                    }
                                } else DriveApi.upload(requireNotNull(token), name, mime, input, destination)
                            } ?: kotlin.error(localized(R.string.unable_to_read_file_arg_1, name))
                            completed++
                            uploadNotifications.running(
                                UploadNotifications.Kind.DRIVE,
                                completed = completed,
                                total = total
                            )
                        }
                    )
                    if (tree != null) uploader.tree(tree, parent) else uploader.files(uris, parent)
                }
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) cancelled = true }
            when {
                cancelled -> uploadNotifications.finished(
                    UploadNotifications.Kind.DRIVE,
                    localized(R.string.upload_canceled_completed_arg_1_files, completed),
                    success = false
                )
                result.isSuccess -> uploadNotifications.finished(
                    UploadNotifications.Kind.DRIVE,
                    localized(R.string.uploaded_arg_1_files, completed),
                    success = true
                )
                else -> {
                    val stoppedAt = currentName ?: localized(R.string.a_file)
                    uploadNotifications.finished(
                        UploadNotifications.Kind.DRIVE,
                        localized(R.string.upload_stopped_at_arg_1_completed_arg_2_files, stoppedAt, completed),
                        success = false
                    )
                }
            }
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { listingCache.clear(account.key) }
            if (cancelled) throw result.exceptionOrNull() as CancellationException
            onComplete()
        }
    }
}
