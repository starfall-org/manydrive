package com.starfall.gsadrive.feature.fileactions

import com.starfall.gsadrive.R

import android.content.Context
import android.widget.Toast
import com.starfall.gsadrive.data.DriveApi
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.DrivePermission
import com.starfall.gsadrive.data.FileListCache
import com.starfall.gsadrive.data.S3Api
import com.starfall.gsadrive.data.ServiceAccountApi
import com.starfall.gsadrive.feature.accounts.AccountType
import com.starfall.gsadrive.feature.accounts.StorageSession
import com.starfall.gsadrive.shared.i18n.localized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class FileMutations(
    private val context: Context,
    private val scope: CoroutineScope,
    private val listingCache: FileListCache,
    private val session: () -> StorageSession?,
    private val onRefresh: (String) -> Unit
) {
    fun shareFile(file: DriveFile, email: String, role: String, done: (Result<Unit>) -> Unit) {
        val captured = session()
        val token = captured?.token
        if (token == null) return done(Result.failure(IllegalStateException(localized(R.string.need_to_grant_drive_permission_first))))
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { DriveApi.share(token, file.id, email.trim(), role) } }
            if (result.isSuccess) Toast.makeText(context, localized(R.string.shared_arg_1, file.name), Toast.LENGTH_SHORT).show()
            done(result)
        }
    }

    fun renameFile(file: DriveFile, newName: String, done: (Result<Unit>) -> Unit) {
        val captured = session()
        val token = captured?.token
        val accountKey = captured?.account?.key
        if (token == null || accountKey == null) return done(Result.failure(IllegalStateException(localized(R.string.need_to_grant_drive_permission_first))))
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) {
                DriveApi.rename(token, file.id, newName.trim())
                listingCache.clear(accountKey)
            } }
            done(result)
            if (result.isSuccess) onRefresh(accountKey)
        }
    }

    fun loadPermissions(file: DriveFile, done: (Result<List<DrivePermission>>) -> Unit) {
        val captured = session()
        val token = captured?.token
        if (token == null) return done(Result.failure(IllegalStateException(localized(R.string.need_to_grant_drive_permission_first))))
        scope.launch {
            done(runCatching { withContext(Dispatchers.IO) { DriveApi.listPermissions(token, file.id) } })
        }
    }

    fun removePermission(file: DriveFile, permission: DrivePermission, done: (Result<Unit>) -> Unit) {
        val captured = session()
        val token = captured?.token
        if (token == null) return done(Result.failure(IllegalStateException(localized(R.string.need_to_grant_drive_permission_first))))
        scope.launch {
            done(runCatching { withContext(Dispatchers.IO) { DriveApi.deletePermission(token, file.id, permission.id) } })
        }
    }

    fun loadMoveFolders(parentId: String?, done: (Result<List<DriveFile>>) -> Unit) {
        val captured = session()
        val account = captured?.account
            ?: return done(Result.failure(IllegalStateException(localized(R.string.add_or_select_an_account_before_uploading))))
        val config = captured?.s3
        val service = captured?.service
        val existingToken = captured?.token
        scope.launch {
            done(runCatching {
                withContext(Dispatchers.IO) {
                    when (account.type) {
                        AccountType.S3 -> S3Api.list(requireNotNull(config), parentId.orEmpty()).filter { it.isFolder }
                        AccountType.SERVICE -> {
                            val token = ServiceAccountApi.accessToken(requireNotNull(service)).value
                            DriveApi.listFiles(token, parentId = parentId).filter { it.isFolder }
                        }
                        AccountType.GOOGLE -> DriveApi.listFiles(
                            requireNotNull(existingToken) { localized(R.string.need_to_grant_drive_permission_first) },
                            parentId = parentId
                        ).filter { it.isFolder }
                    }
                }
            })
        }
    }

    fun copyFile(file: DriveFile, destinationId: String, done: (Result<Unit>) -> Unit) {
        val captured = session()
        val account = captured?.account
        val accountKey = account?.key
        if (account == null || accountKey == null) {
            done(Result.failure(IllegalStateException(localized(R.string.add_or_select_an_account_before_uploading))))
            return
        }
        if (account.type == AccountType.S3) {
            done(Result.failure(UnsupportedOperationException(localized(R.string.copying_within_the_same_s3_account_is_not_yet_supported))))
            return
        }
        val service = captured?.service
        val existingToken = captured?.token
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val token = if (account.type == AccountType.SERVICE)
                        ServiceAccountApi.accessToken(requireNotNull(service)).value
                    else requireNotNull(existingToken) { localized(R.string.need_to_grant_drive_permission_first) }
                    fun copyTree(source: DriveFile, parent: String) {
                        if (!source.isFolder) {
                            DriveApi.copy(token, source, parent)
                            return
                        }
                        val created = DriveApi.createFolder(token, source.name, parent)
                        DriveApi.listFiles(token, parentId = source.id).forEach { child -> copyTree(child, created) }
                    }
                    copyTree(file, destinationId)
                    listingCache.clear(accountKey)
                }
            }
            done(result)
            if (result.isSuccess) onRefresh(accountKey)
        }
    }


    fun moveFiles(files: List<DriveFile>, destinationId: String, done: (Result<Unit>) -> Unit) {
        val captured = session()
        val token = captured?.token
        val accountKey = captured?.account?.key
        if (token == null || accountKey == null) return done(Result.failure(IllegalStateException(localized(R.string.need_to_grant_drive_permission_first))))
        val unique = files.distinctBy { it.id }
        if (unique.isEmpty()) return done(Result.success(Unit))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                var failed = 0
                var firstFailure: Throwable? = null
                unique.forEach { file ->
                    runCatching { DriveApi.move(token, file, destinationId) }
                        .onFailure { failure ->
                            failed++
                            if (firstFailure == null) firstFailure = failure
                        }
                }
                listingCache.clear(accountKey)
                if (failed == 0) Result.success(Unit)
                else Result.failure(IllegalStateException(
                    localized(R.string.unable_to_move_arg_1_arg_2_items, failed, unique.size),
                    firstFailure
                ))
            }
            done(result)
            onRefresh(accountKey)
        }
    }

}
