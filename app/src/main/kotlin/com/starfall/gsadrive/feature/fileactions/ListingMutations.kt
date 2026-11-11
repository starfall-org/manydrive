package com.starfall.gsadrive.feature.fileactions

import com.starfall.gsadrive.R

import com.starfall.gsadrive.data.DriveApi
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.FileListCache
import com.starfall.gsadrive.data.S3Api
import com.starfall.gsadrive.feature.accounts.AccountType
import com.starfall.gsadrive.feature.accounts.StorageSession
import com.starfall.gsadrive.feature.browser.BrowserController
import com.starfall.gsadrive.shared.operations.runFileBatch
import com.starfall.gsadrive.shared.operations.ioResult
import com.starfall.gsadrive.shared.i18n.localized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Mutations that own loading/error state for the current browser listing. */
internal class ListingMutations(
    private val scope: CoroutineScope,
    private val listingCache: FileListCache,
    private val browser: BrowserController,
    private val session: () -> StorageSession?,
    private val onError: (String) -> Unit
) {
    private var generation = 0

    fun invalidate() { ++generation }

    fun restoreTrashFiles(files: List<DriveFile>) {
        mutateTrashFiles(
            files = files,
            failureMessage = localized(R.string.the_selected_items_cannot_be_restored_check_permissions_and_connection),
            action = { token, file -> DriveApi.restore(token, file.id) }
        )
    }

    fun deleteTrashFiles(files: List<DriveFile>) {
        mutateTrashFiles(
            files = files,
            failureMessage = localized(R.string.selected_items_cannot_be_permanently_deleted),
            action = { token, file -> DriveApi.deletePermanently(token, file.id) }
        )
    }

    fun mutateTrashFiles(
        files: List<DriveFile>,
        failureMessage: String,
        action: (String, DriveFile) -> Unit
    ) {
        val token = browser.model.token ?: return
        val account = session()?.account ?: return
        val unique = files.distinctBy { it.id }
        if (unique.isEmpty()) return
        val request = ++generation
        browser.model = browser.model.copy(loading = true, message = null)
        scope.launch {
            val result = ioResult {
                try { unique.forEach { file -> action(token, file) } }
                finally { listingCache.clear(account.key) }
            }
            if (request != generation) return@launch
            result.onSuccess { browser.refresh(forceNetwork = true) }
                .onFailure { browser.model = browser.model.copy(loading = false, message = failureMessage) }
        }
    }

    fun createFolder(name: String) {
        val account = session()?.account ?: return
        if (browser.model.loading || name.isBlank() || account.type != AccountType.GOOGLE) return
        val token = browser.model.token ?: return
        val request = ++generation
        val parentId = browser.model.path.lastOrNull()?.id
        browser.model = browser.model.copy(loading = true, message = null)
        scope.launch {
            ioResult { DriveApi.createFolder(token, name.trim(), parentId); listingCache.clear(account.key) }
                .onSuccess { if (request == generation) browser.refresh(forceNetwork = true) }
                .onFailure { if (request == generation) onError(localized(R.string.unable_to_create_directory)) }
        }
    }

    fun moveToTrash(file: DriveFile) = moveToTrash(listOf(file))

    fun moveToTrash(files: List<DriveFile>) {
        if (session()?.account?.type == AccountType.S3) {
            deleteS3Files(files)
            return
        }
        val account = session()?.account ?: return
        if (browser.model.loading || account.type !in setOf(AccountType.GOOGLE, AccountType.SERVICE)) return
        val token = browser.model.token ?: return
        val unique = files.distinctBy { it.id }
        if (unique.isEmpty()) return
        val request = ++generation
        browser.model = browser.model.copy(loading = true, message = null)
        scope.launch {
            val failures = withContext(Dispatchers.IO) {
                try {
                    unique.runFileBatch(DriveFile::id) { DriveApi.moveToTrash(token, it.id) }.failed
                } finally {
                    listingCache.clear(account.key)
                }
            }

            if (request == generation) {
                if (failures > 0) {
                    browser.model = browser.model.copy(loading = false, message = localized(R.string.could_not_move_arg_1_arg_2_items_to_trash, failures, unique.size))
                } else {
                    browser.refresh(forceNetwork = true)
                }
            }
        }
    }

    fun deleteS3Files(files: List<DriveFile>) {
        if (browser.model.loading) return
        val account = session()?.account?.takeIf { it.type == AccountType.S3 } ?: return
        val config = session()?.s3 ?: return
        val unique = files.distinctBy { it.id }
        if (unique.isEmpty()) return
        val request = ++generation
        browser.model = browser.model.copy(loading = true, message = null)
        scope.launch {
            val failures = withContext(Dispatchers.IO) {
                try {
                    unique.runFileBatch(DriveFile::id) { S3Api.delete(config, it) }.failed
                } finally {
                    // Keep invalidation bound to the captured source account, including partial failures.
                    listingCache.clear(account.key)
                }
            }

            if (request == generation && session()?.account?.key == account.key) {
                // Refresh even after partial failure, since some objects may already be gone.
                browser.loadS3(completionMessage = if (failures == 0) null
                    else localized(R.string.some_s3_items_could_not_be_deleted_check_delete_permissions_and_try_again) + " ($failures/${unique.size})")
            }
        }
    }

}
