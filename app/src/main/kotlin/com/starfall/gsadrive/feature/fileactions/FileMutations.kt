package com.starfall.gsadrive.feature.fileactions

import com.starfall.gsadrive.R

import android.content.Context
import android.widget.Toast
import com.starfall.gsadrive.data.DriveApi
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.DrivePermission
import com.starfall.gsadrive.data.FileListCache
import com.starfall.gsadrive.feature.accounts.AccountType
import com.starfall.gsadrive.feature.accounts.openStorage
import com.starfall.gsadrive.feature.accounts.requireDriveToken
import com.starfall.gsadrive.shared.operations.ioResult
import com.starfall.gsadrive.shared.operations.runFileBatch
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
            val result = ioResult { DriveApi.share(token, file.id, email.trim(), role) }
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
            val result = ioResult {
                DriveApi.rename(token, file.id, newName.trim())
                listingCache.clear(accountKey)
            }
            done(result)
            if (result.isSuccess) onRefresh(accountKey)
        }
    }

    fun loadPermissions(file: DriveFile, done: (Result<List<DrivePermission>>) -> Unit) {
        val captured = session()
        val token = captured?.token
        if (token == null) return done(Result.failure(IllegalStateException(localized(R.string.need_to_grant_drive_permission_first))))
        scope.launch {
            done(ioResult { DriveApi.listPermissions(token, file.id) })
        }
    }

    fun removePermission(file: DriveFile, permission: DrivePermission, done: (Result<Unit>) -> Unit) {
        val captured = session()
        val token = captured?.token
        if (token == null) return done(Result.failure(IllegalStateException(localized(R.string.need_to_grant_drive_permission_first))))
        scope.launch {
            done(ioResult { DriveApi.deletePermission(token, file.id, permission.id) })
        }
    }

    fun loadMoveFolders(parentId: String?, done: (Result<List<DriveFile>>) -> Unit) {
        val captured = session()
            ?: return done(Result.failure(IllegalStateException(localized(R.string.add_or_select_an_account_before_uploading))))
        scope.launch {
            done(ioResult { captured.openStorage().list(parentId).filter { it.isFolder } })
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
        scope.launch {
            val result = ioResult {
                val token = captured.requireDriveToken()
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
                val outcome = try {
                    unique.runFileBatch(DriveFile::id) { DriveApi.move(token, it, destinationId) }
                } finally {
                    listingCache.clear(accountKey)
                }
                if (outcome.failed == 0) Result.success(Unit)
                else Result.failure(IllegalStateException(
                    localized(R.string.unable_to_move_arg_1_arg_2_items, outcome.failed, outcome.total),
                    outcome.firstFailure
                ))
            }
            done(result)
            onRefresh(accountKey)
        }
    }

}
