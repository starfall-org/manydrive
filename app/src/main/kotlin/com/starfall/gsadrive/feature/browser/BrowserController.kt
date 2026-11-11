package com.starfall.gsadrive.feature.browser

import com.starfall.gsadrive.R

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.starfall.gsadrive.data.DriveApi
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.FileListCache
import com.starfall.gsadrive.data.S3Account
import com.starfall.gsadrive.data.S3Api
import com.starfall.gsadrive.data.ServiceAccessToken
import com.starfall.gsadrive.data.ServiceAccountApi
import com.starfall.gsadrive.data.ServiceAccountCredentials
import com.starfall.gsadrive.data.driveLoadError
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.feature.accounts.AccountType
import com.starfall.gsadrive.feature.accounts.isTabEnabled
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.model.Model
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns independent tab snapshots, request identity, navigation and cache-first listing. */
internal class BrowserController(
    private val scope: CoroutineScope,
    private val listingCache: FileListCache,
    private val activeAccount: () -> AccountEntry?,
    private val s3Account: (String) -> S3Account?,
    private val serviceAccount: (String) -> ServiceAccountCredentials?,
    private val storedAccountName: (AccountType, String) -> String,
    private val refreshAccountName: (String) -> Unit,
    private val requestAuthorization: (BrowserRefreshTarget) -> Unit,
    private val refreshPhotos: () -> Unit,
    private val onTokenAvailable: () -> Unit,
    private val onTokenError: (String) -> Unit
) {
    var model by mutableStateOf(Model())
    var tab by mutableIntStateOf(0)
    val browserTabModels = mutableStateMapOf<Int, Model>()
    val serviceTokens = mutableMapOf<String, ServiceAccessToken>()
    private val browserRefreshIds = mutableMapOf<Int, Int>()
    private var browserRefreshCounter = 0

    fun clearSnapshots() {
        browserTabModels.clear()
        browserRefreshIds.clear()
    }

    fun selectTab(next: Int) {
        if (!isTabEnabled(activeAccount()?.type, next) || tab == next) return

        // Keep the complete state of the page we are leaving, including an in-progress refresh.
        // Switching tabs does not invalidate its request; completion is routed back to this tab.
        if (tab in 0..1) browserTabModels[tab] = model

        val previous = model
        tab = next
        if (next in 0..1) {
            val restored = browserTabModels[next]
            model = restored?.copy(
                user = restored.user ?: previous.user,
                token = restored.token ?: previous.token
            ) ?: Model(user = previous.user, token = previous.token)
            if (restored == null) refresh()
        } else {
            model = Model(user = previous.user, token = previous.token)
            refresh()
        }
    }

    fun searchDrive(query: String, done: (Result<List<DriveFile>>) -> Unit) {
        val account = activeAccount()
        val token = model.token
        if (account == null || account.type == AccountType.S3) {
            done(Result.failure(UnsupportedOperationException(localized(R.string.drive_wide_search_only_applies_to_google_drive))))
            return
        }
        if (token == null) {
            done(Result.failure(IllegalStateException(localized(R.string.need_to_grant_drive_permission_before_searching))))
            return
        }
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { DriveApi.searchFiles(token, query) } }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            done(result)
        }
    }

    fun openSearchFolder(file: DriveFile) {
        if (!file.isFolder || model.loading || tab == 3) return
        model = model.copy(path = listOf(file), files = emptyList(), fromCache = false)
        refresh()
    }

    fun openFolder(file: DriveFile) {
        if (!file.isFolder || model.loading || tab == 3) return
        model = model.copy(path = model.path + file, files = emptyList())
        refresh()
    }

    fun goUp() {
        if (model.path.isEmpty()) return
        model = model.copy(path = model.path.dropLast(1), files = emptyList())
        refresh()
    }

    fun cacheLocation(tabIndex: Int = tab, path: List<DriveFile> = model.path): String =
        "$tabIndex:${path.lastOrNull()?.id.orEmpty()}"

    fun cacheFiles(files: List<DriveFile>) {
        val account = activeAccount()?.key ?: return
        val location = cacheLocation()
        scope.launch(Dispatchers.IO) { listingCache.write(account, location, files) }
    }

    fun cacheFiles(target: BrowserRefreshTarget, files: List<DriveFile>) {
        val location = cacheLocation(target.tab, target.path)
        scope.launch(Dispatchers.IO) { listingCache.write(target.accountKey, location, files) }
    }

    fun newBrowserRefreshTarget(active: AccountEntry): BrowserRefreshTarget {
        val requestId = ++browserRefreshCounter
        browserRefreshIds[tab] = requestId
        return BrowserRefreshTarget(active.key, tab, model.path, requestId)
    }

    fun isBrowserRefreshCurrent(target: BrowserRefreshTarget): Boolean {
        val current = if (tab == target.tab) model else browserTabModels[target.tab]
        return activeAccount()?.key == target.accountKey &&
            browserRefreshIds[target.tab] == target.requestId && current?.path == target.path
    }

    fun updateBrowserTab(target: BrowserRefreshTarget, update: (Model) -> Model): Boolean {
        if (!isBrowserRefreshCurrent(target)) return false
        if (tab == target.tab) {
            model = update(model)
        } else {
            val base = browserTabModels[target.tab]
                ?: Model(user = activeAccount()?.name, token = model.token, path = target.path)
            browserTabModels[target.tab] = update(base)
        }
        return true
    }

    fun browserError(target: BrowserRefreshTarget, message: String) {
        updateBrowserTab(target) { it.copy(loading = false, message = message) }
        onTokenError(message)
    }

    /** Access tokens belong to the account, not to one tab. Keep every tab snapshot in sync. */
    fun propagateBrowserToken(token: String) {
        model = model.copy(token = token)
        browserTabModels.keys.toList().forEach { index ->
            browserTabModels[index] = browserTabModels.getValue(index).copy(token = token)
        }
    }

    fun refresh(forceNetwork: Boolean = false) {
        if (tab == 4) { refreshPhotos(); return }
        val active = activeAccount() ?: return
        val target = newBrowserRefreshTarget(active)
        val location = cacheLocation(target.tab, target.path)
        val startingToken = model.token
        updateBrowserTab(target) { it.copy(loading = true, message = null) }
        scope.launch {
            val cached = withContext(Dispatchers.IO) { listingCache.read(active.key, location) }
            if (!isBrowserRefreshCurrent(target)) return@launch
            if (cached != null) {
                val needsToken = active.type != AccountType.S3 && startingToken == null
                updateBrowserTab(target) {
                    it.copy(files = cached, loading = forceNetwork || needsToken, fromCache = true)
                }
                if (!forceNetwork && !needsToken) return@launch
            }
            when (active.type) {
                AccountType.GOOGLE -> if (startingToken != null) loadDrive(startingToken, target) else requestAuthorization(target)
                AccountType.S3 -> loadS3(target)
                AccountType.SERVICE -> loadService(target)
            }
        }
    }

    fun loadDrive(token: String, target: BrowserRefreshTarget? = null) {
        val active = activeAccount() ?: return
        val refreshTarget = target ?: newBrowserRefreshTarget(active)
        if (!isBrowserRefreshCurrent(refreshTarget)) return
        refreshAccountName(token)
        val parentId = refreshTarget.path.lastOrNull()?.id
        if (isBrowserRefreshCurrent(refreshTarget)) propagateBrowserToken(token)
        updateBrowserTab(refreshTarget) { it.copy(loading = true, message = null, token = token) }
        onTokenAvailable()
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    DriveApi.listFiles(
                        token,
                        sharedWithMe = refreshTarget.tab == 1 && parentId == null,
                        parentId = parentId,
                        trashed = refreshTarget.tab == 3
                    )
                }
            }.onSuccess { files ->
                if (updateBrowserTab(refreshTarget) { it.copy(files = files, loading = false, fromCache = false) }) {
                    cacheFiles(refreshTarget, files)
                }
            }.onFailure { failure ->
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                if (isBrowserRefreshCurrent(refreshTarget)) {
                    val diagnostic = driveLoadError(failure)
                    android.util.Log.e("ManyDrive", diagnostic + "\n" + failure.stackTrace.take(12).joinToString("\n"))
                    browserError(refreshTarget, diagnostic)
                }
            }
        }
    }

    fun loadS3(target: BrowserRefreshTarget? = null, completionMessage: String? = null) {
        val entry = activeAccount() ?: return
        val refreshTarget = target ?: newBrowserRefreshTarget(entry)
        val account = s3Account(entry.id) ?: return
        val prefix = refreshTarget.path.lastOrNull()?.id.orEmpty()
        updateBrowserTab(refreshTarget) { it.copy(user = account.name, loading = true, message = null) }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { S3Api.list(account.config, prefix) } }
                .onSuccess { files ->
                    if (updateBrowserTab(refreshTarget) { it.copy(files = files, loading = false, fromCache = false, message = completionMessage) }) {
                        cacheFiles(refreshTarget, files)
                    }
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (isBrowserRefreshCurrent(refreshTarget)) {
                        browserError(refreshTarget, localized(R.string.unable_to_connect_s3_check_bucket_and_network_permissions_and_then_try_refreshing))
                    }
                }
        }
    }

    fun loadService(target: BrowserRefreshTarget? = null) {
        val entry = activeAccount() ?: return
        val refreshTarget = target ?: newBrowserRefreshTarget(entry)
        val account = serviceAccount(entry.id) ?: return
        val parentId = refreshTarget.path.lastOrNull()?.id
        val shared = refreshTarget.tab == 1 && parentId == null
        val trashed = refreshTarget.tab == 3
        val cached = serviceTokens[account.id]?.takeIf { it.validAt(System.currentTimeMillis() / 1000) }
        updateBrowserTab(refreshTarget) {
            it.copy(user = storedAccountName(AccountType.SERVICE, account.email), loading = true, message = null)
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val token = cached ?: ServiceAccountApi.accessToken(account)
                    token to DriveApi.listFiles(token.value, sharedWithMe = shared, parentId = parentId, trashed = trashed)
                }
            }.onSuccess { (token, files) ->
                if (isBrowserRefreshCurrent(refreshTarget)) {
                    serviceTokens[account.id] = token
                    refreshAccountName(token.value)
                    propagateBrowserToken(token.value)
                    if (updateBrowserTab(refreshTarget) {
                            it.copy(token = token.value, files = files, loading = false, fromCache = false)
                        }) {
                        onTokenAvailable()
                        cacheFiles(refreshTarget, files)
                    }
                }
            }.onFailure {
                if (it is CancellationException) throw it
                if (isBrowserRefreshCurrent(refreshTarget)) {
                    serviceTokens.remove(account.id)
                    browserError(refreshTarget,
                        localized(R.string.unable_to_load_service_account_drive_check_the_key_drive_api_and_share_permissions_then_try_refreshing))
                }
            }
        }
    }

}
