package com.starfall.gsadrive

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.api.Scope
import com.google.common.util.concurrent.ListenableFuture
import com.starfall.gsadrive.app.App
import com.starfall.gsadrive.app.platform.AndroidDocumentsMultiPicker
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.FileListCache
import com.starfall.gsadrive.feature.accounts.AccountController
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.feature.accounts.AccountType
import com.starfall.gsadrive.feature.accounts.StorageSession
import com.starfall.gsadrive.feature.browser.BrowserController
import com.starfall.gsadrive.feature.browser.BrowserRefreshTarget
import com.starfall.gsadrive.feature.fileactions.FileActionCallbacks
import com.starfall.gsadrive.feature.fileactions.FileMutations
import com.starfall.gsadrive.feature.fileactions.ListingMutations
import com.starfall.gsadrive.feature.photos.PhotosFolderUploadMode
import com.starfall.gsadrive.feature.photos.PhotosLibraryController
import com.starfall.gsadrive.feature.transfers.CloudDownloads
import com.starfall.gsadrive.feature.transfers.DocumentTransferUploads
import com.starfall.gsadrive.feature.transfers.LocalCloudTransfers
import com.starfall.gsadrive.feature.transfers.PhotosUploads
import com.starfall.gsadrive.feature.viewer.ViewerCoordinator
import com.starfall.gsadrive.feature.viewer.ViewerState
import com.starfall.gsadrive.feature.viewer.playback.MediaPagePlayback
import com.starfall.gsadrive.feature.viewer.playback.MediaPlaybackService
import com.starfall.gsadrive.feature.viewer.playback.VideoStreamCache
import com.starfall.gsadrive.shared.operations.ioResult
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.media.ThumbnailRepository
import com.starfall.gsadrive.shared.media.resolveLocalMedia
import com.starfall.gsadrive.shared.model.Model
import com.starfall.gsadrive.shared.ui.theme.ManyDriveTheme
import com.starfall.gsadrive.shared.ui.theme.ThemeMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
private const val PHOTOS_SCOPE = "https://www.googleapis.com/auth/photoslibrary.appendonly"


class MainActivity : ComponentActivity() {
    companion object {
        internal const val ACTION_OPEN_PLAYER = "com.starfall.gsadrive.action.OPEN_PLAYER"
    }

    private val accounts: AccountController by lazy {
        AccountController(
            context = this, scope = lifecycleScope, selection = selection, listingCache = listingCache,
            onAdded = ::finishAdding,
            onDisplayName = { model = model.copy(user = it) },
            onServiceToken = { id, token -> serviceTokens[id] = token },
            onServiceRemoved = { serviceTokens.remove(it) },
            onSignOut = ::signOut
        )
    }
    private val listingMutations by lazy {
        ListingMutations(lifecycleScope, listingCache, browser, ::storageSession, ::error)
    }
    private val fileMutations by lazy {
        FileMutations(this, lifecycleScope, listingCache, ::storageSession) { accountKey ->
            if (accounts.ui.active?.key == accountKey) browser.refresh(forceNetwork = true)
        }
    }
    private val photosUploads by lazy { PhotosUploads(this, lifecycleScope) }
    private val documentTransferUploads by lazy { DocumentTransferUploads(this, lifecycleScope, listingCache) }
    private val localCloudTransfers by lazy { LocalCloudTransfers(this, lifecycleScope, listingCache) }
    private val cloudDownloads by lazy { CloudDownloads(this, lifecycleScope) }

    private fun storageSession(): StorageSession? {
        val account = accounts.ui.active ?: return null
        return StorageSession(account, model.token,
            accounts.s3Accounts.accounts.firstOrNull { it.id == account.id }?.config,
            accounts.serviceAccounts.firstOrNull { it.id == account.id })
    }

    private var pendingLegacyDownload: (() -> Unit)? = null
    private val downloadPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val start = pendingLegacyDownload
        pendingLegacyDownload = null
        if (granted) start?.invoke()
        else Toast.makeText(this, localized(R.string.needs_storage_permission_to_download_to_downloads), Toast.LENGTH_LONG).show()
    }
    private lateinit var photosLibrary: PhotosLibraryController
    private var externalOpenRevision = 0
    private var pendingOpenPlayer = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlayerIntent()
        handleExternalMediaIntent()
    }

    private fun handleExternalMediaIntent() {
        val incoming = intent ?: return
        if (incoming.action != Intent.ACTION_VIEW) return
        val uri = incoming.data ?: return
        if (uri.scheme !in setOf("content", "file")) return
        val declaredType = incoming.type
        incoming.action = null
        val request = ++externalOpenRevision
        lifecycleScope.launch {
            val result = ioResult { resolveLocalMedia(this@MainActivity, uri, declaredType) }
            if (request != externalOpenRevision) return@launch
            result.onSuccess { viewerCoordinator.openLocal(it) }.onFailure {
                Toast.makeText(this@MainActivity, it.message ?: localized(R.string.cannot_open_file), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handlePlayerIntent() {
        if (intent?.action == ACTION_OPEN_PLAYER) {
            pendingOpenPlayer = true
            // Consume the request so a later recreation does not expand a minimized player again.
            intent.action = null
        }
        openPendingPlayer()
    }

    private fun openPendingPlayer() {
        if (!pendingOpenPlayer) return
        val controller = playback ?: return
        val item = controller.currentMediaItem ?: return
        viewerCoordinator.syncToMediaItem(item)
        viewerCoordinator.expand()
        pendingOpenPlayer = false
    }

    private lateinit var accountPicker: ActivityResultLauncher<Intent>
    private lateinit var servicePicker: ActivityResultLauncher<Array<String>>
    private lateinit var resolution: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var photosAuthorizationResolution: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var folderPicker: ActivityResultLauncher<Uri?>
    private lateinit var filePicker: ActivityResultLauncher<Array<String>>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private var notificationPermissionPending = false
    private val authorization by lazy { Identity.getAuthorizationClient(this) }
    private val selection by lazy { getSharedPreferences("manydrive_selection", MODE_PRIVATE) }
    private val listingCache by lazy { FileListCache(File(cacheDir, "file-lists")) }
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var superDark by mutableStateOf(false)
    private var showHiddenSystemFiles by mutableStateOf(false)
    private var pendingUploadParent: String? = null
    private val serviceTokens get() = browser.serviceTokens
    private val browser: BrowserController by lazy {
        BrowserController(
            scope = lifecycleScope,
            listingCache = listingCache,
            activeAccount = { accounts.ui.active },
            s3Account = { id -> accounts.s3Accounts.accounts.find { it.id == id } },
            serviceAccount = { id -> accounts.serviceAccounts.find { it.id == id } },
            storedAccountName = accounts::storedAccountName,
            refreshAccountName = accounts::refreshAccountName,
            requestAuthorization = { authorize(it) },
            refreshPhotos = { photosLibrary.refresh() },
            onTokenAvailable = { viewerCoordinator.onAccessTokenAvailable() },
            onTokenError = { viewerCoordinator.onAccessTokenError(it) }
        )
    }
    private var model: Model
        get() = browser.model
        set(value) { browser.model = value }
    private val browserTabModels get() = browser.browserTabModels
    private var playback by mutableStateOf<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val viewerCoordinator: ViewerCoordinator by lazy {
        ViewerCoordinator(
            context = this,
            scope = lifecycleScope,
            listCache = listingCache,
            activeAccount = { accounts.ui.active },
            accessToken = { model.token },
            photosAccessToken = { photosLibrary.accessToken },
            photosTokenProvider = { key -> photosLibrary.tokenProvider(key) },
            s3Config = { entry -> accounts.s3Accounts.accounts.firstOrNull { it.id == entry.id }?.config },
            currentFiles = { model.files },
            player = { playback },
            awaitPlayer = {
                playback ?: withContext(Dispatchers.IO) {
                    controllerFuture?.get() ?: kotlin.error(localized(R.string.media_streaming_service_is_not_ready_yet))
                }
            },
            requestNotificationPermission = ::ensureNotificationPermission,
            requestAccessToken = {
                // A cache refresh or authorization may already be restoring this account.
                if (!model.loading && pendingAuthorization == null) authorize()
            }
        )
    }
    private val viewer: ViewerState? get() = viewerCoordinator.state
    private var tab: Int
        get() = browser.tab
        set(value) { browser.tab = value }
    private var pendingAuthorization: String? = null
    private var pendingAuthorizationTarget: BrowserRefreshTarget? = null
    private var pendingPhotosAuthorization: String? = null
    private var pendingPhotosFiles: List<DriveFile> = emptyList()
    private var pendingPhotosMode: PhotosFolderUploadMode = PhotosFolderUploadMode.RAW
    private var pendingUpload: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOpenPlayer = savedInstanceState?.getBoolean("openPlayer") ?: false
        handlePlayerIntent()
        photosLibrary = PhotosLibraryController(this, { accounts.ui.active }) { key, photos ->
            if (accounts.ui.active?.key == key) {
                val updated = photos.copy(user = model.user, token = model.token)
                if (tab == 4) model = updated else browserTabModels[4] = updated
            }
        }
        enableEdgeToEdge()
        notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationPermissionPending = false
            if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(this, localized(R.string.no_notification_permission_the_player_still_runs_in_the_background_but_media_controls_may_not_appear_in_the_notification_bar), Toast.LENGTH_LONG).show()
            }
        }
        // Notifications are the only runtime permission this app needs. Ask automatically only
        // on the first eligible launch; feature entry points ask again when the permission is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !selection.getBoolean("notificationPermissionOnboardingAsked", false)
        ) {
            selection.edit().putBoolean("notificationPermissionOnboardingAsked", true).apply()
            ensureNotificationPermission()
        }
        val sessionToken = SessionToken(this, ComponentName(this, MediaPlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync().also { future ->
            future.addListener({
                runCatching { future.get() }
                    .onSuccess { controller ->
                        controller.addListener(playbackListener)
                        playback = controller
                        controller.currentMediaItem?.let(viewerCoordinator::syncToMediaItem)
                        openPendingPlayer()
                    }
                    .onFailure {
                        Toast.makeText(this, localized(R.string.unable_to_connect_to_media_streaming_service), Toast.LENGTH_LONG).show()
                    }
            }, ContextCompat.getMainExecutor(this))
        }
        themeMode = runCatching { ThemeMode.valueOf(selection.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
        superDark = selection.getBoolean("superDark", false)
        showHiddenSystemFiles = selection.getBoolean("showHiddenSystemFiles", false)
        pendingUploadParent = savedInstanceState?.getString("uploadParent")
        pendingAuthorization = savedInstanceState?.getString("authorizationAccount")
        pendingPhotosAuthorization = savedInstanceState?.getString("photosAuthorizationAccount")
        pendingPhotosMode = runCatching {
            PhotosFolderUploadMode.valueOf(savedInstanceState?.getString("photosUploadMode") ?: PhotosFolderUploadMode.RAW.name)
        }.getOrDefault(PhotosFolderUploadMode.RAW)
        pendingUpload = savedInstanceState?.getString("uploadAccount")
        savedInstanceState?.getStringArrayList("photosFiles")?.let { encoded ->
            pendingPhotosFiles = encoded.chunked(3).mapNotNull { parts ->
                if (parts.size == 3) DriveFile(parts[0], parts[1], parts[2], null) else null
            }
        }
        photosAuthorizationResolution = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val expected = pendingPhotosAuthorization
            pendingPhotosAuthorization = null
            if (expected == null || expected != accounts.ui.active?.key) return@registerForActivityResult
            if (result.resultCode != RESULT_OK) {
                pendingPhotosFiles = emptyList()
                pendingPhotosMode = PhotosFolderUploadMode.RAW
                Toast.makeText(this, localized(R.string.google_photos_permission_has_been_revoked), Toast.LENGTH_SHORT).show()
            } else {
                runCatching { authorization.getAuthorizationResultFromIntent(result.data) }
                    .onSuccess { auth ->
                        auth.accessToken?.let { token ->
                            pendingPhotosFiles.takeIf { files -> files.isNotEmpty() }?.let { files ->
                                startPhotosUpload(files, token, pendingPhotosMode)
                            }
                        } ?: Toast.makeText(this, localized(R.string.google_doesn_t_return_photos_permission), Toast.LENGTH_LONG).show()
                    }
                    .onFailure {
                        pendingPhotosFiles = emptyList()
                        pendingPhotosMode = PhotosFolderUploadMode.RAW
                        Toast.makeText(this, localized(R.string.unable_to_grant_google_photos_permission), Toast.LENGTH_LONG).show()
                    }
            }
        }
        resolution = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val expected = pendingAuthorization
            val target = pendingAuthorizationTarget
            pendingAuthorization = null
            pendingAuthorizationTarget = null
            if (expected == null || expected != accounts.ui.active?.key) return@registerForActivityResult
            if (result.resultCode != RESULT_OK) {
                if (target != null) browser.browserError(target, localized(R.string.google_permission_has_been_revoked))
                else error(localized(R.string.google_permission_has_been_revoked))
            } else {
                runCatching { authorization.getAuthorizationResultFromIntent(result.data) }
                    .onSuccess { auth ->
                        val token = auth.accessToken
                        if (token != null) browser.loadDrive(token, target)
                        else if (target != null) browser.browserError(target, localized(R.string.google_does_not_return_an_access_token))
                        else error(localized(R.string.google_does_not_return_an_access_token))
                    }
                    .onFailure {
                        if (target != null) browser.browserError(target, localized(R.string.unable_to_grant_google_permission))
                        else error(localized(R.string.unable_to_grant_google_permission))
                    }
            }
        }
        filePicker = registerForActivityResult(AndroidDocumentsMultiPicker()) { uris ->
            if (pendingUpload == accounts.ui.active?.key) upload(uris, parent = pendingUploadParent)
            pendingUpload = null
        }
        folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null && pendingUpload == accounts.ui.active?.key) upload(emptyList(), tree = uri, parent = pendingUploadParent)
            pendingUpload = null
        }
        servicePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(accounts::importService)
        }
        accountPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                val type = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)
                if (!email.isNullOrBlank() && type == "com.google") {
                    accounts.selectGoogle(email)
                    activate(accounts.ui.entries.first { it.type == AccountType.GOOGLE && it.id == email })
                } else accounts.accountError(localized(R.string.unable_to_retrieve_selected_google_account))
            }
        }
        accounts.restore()?.let { model = Model(user = it.name) }
        setContent {
            ManyDriveTheme(themeMode, superDark) {
                App(model, tab, browser::selectTab, { browser.refresh(forceNetwork = true) }, ::signIn, ::authorize, accounts::connectS3, ::signOut,
                    { pickUpload(false) }, listingMutations::createFolder, listingMutations::moveToTrash,
                    FileActionCallbacks(
                        download = ::downloadFile,
                        uploadToPhotos = ::uploadToPhotos,
                        uploadManyToPhotos = ::uploadToPhotos,
                        share = fileMutations::shareFile,
                        rename = fileMutations::renameFile,
                        loadPermissions = fileMutations::loadPermissions,
                        removePermission = fileMutations::removePermission,
                        loadFolders = fileMutations::loadMoveFolders,
                        copy = fileMutations::copyFile,
                        move = ::moveFile,
                        transferToLocal = ::transferCloudToLocal,
                        moveMany = fileMutations::moveFiles,
                        trash = listingMutations::moveToTrash,
                        trashMany = listingMutations::moveToTrash
                    ),
                    accounts.ui, ::activate, accounts::removeAccount,
                    { servicePicker.launch(arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")) },
                    browser::openFolder, browser::goUp, browser::searchDrive, browser::openSearchFolder, { pickUpload(true) }, listingMutations::restoreTrashFiles, listingMutations::deleteTrashFiles,
                    themeMode, superDark,
                    { themeMode = it; selection.edit().putString("theme", it.name).apply() },
                    { superDark = it; selection.edit().putBoolean("superDark", it).apply() },
                    showHiddenSystemFiles,
                    { showHiddenSystemFiles = it; selection.edit().putBoolean("showHiddenSystemFiles", it).apply() },
                    { lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            listingCache.clearAll()
                            ThumbnailRepository.clear(this@MainActivity)
                            VideoStreamCache.clear(this@MainActivity)
                            File(cacheDir, "viewer").deleteRecursively()
                        }
                        Toast.makeText(this@MainActivity, localized(R.string.file_list_cache_cleared), Toast.LENGTH_SHORT).show()
                    } },
                    viewer, { file, queue -> openPreview(file, swipeQueue = queue) }, ::closePreview, ::updatePreviewText, ::savePreviewText, ::swipePreview,
                    playback, ::minimizePreview, ::expandPreview, browserModels = browserTabModels,
                    openLocalFile = { file, queue -> viewerCoordinator.openLocal(file, queue) },
                    uploadLocalFile = ::uploadLocalFile,
                    transferLocalToCloud = ::transferLocalFileToCloud
                )
            }
        }
        if (pendingAuthorization == null) accounts.ui.active?.let { browser.refresh() }
        handleExternalMediaIntent()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("openPlayer", pendingOpenPlayer)
        outState.putString("authorizationAccount", pendingAuthorization)
        outState.putString("photosAuthorizationAccount", pendingPhotosAuthorization)
        outState.putString("photosUploadMode", pendingPhotosMode.name)
        if (pendingPhotosFiles.isNotEmpty()) {
            outState.putStringArrayList(
                "photosFiles",
                ArrayList(pendingPhotosFiles.flatMap { listOf(it.id, it.name, it.mimeType) })
            )
        }
        outState.putString("uploadAccount", pendingUpload)
        outState.putString("uploadParent", pendingUploadParent)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (isFinishing) MediaPagePlayback.owner.value?.endAppSession()
        playback?.removeListener(playbackListener)
        controllerFuture?.let(MediaController::releaseFuture)
        playback = null
        super.onDestroy()
    }

    private val playbackListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.let(viewerCoordinator::syncToMediaItem)
            openPendingPlayer()
        }
    }

    private fun activate(entry: AccountEntry) {
        if (viewer != null) closePreview()
        listingMutations.invalidate()
        pendingAuthorization = null
        pendingAuthorizationTarget = null
        photosLibrary.reset()
        pendingPhotosAuthorization = null
        pendingPhotosFiles = emptyList()
        pendingPhotosMode = PhotosFolderUploadMode.RAW
        pendingUpload = null
        browser.clearSnapshots()
        tab = 0
        accounts.ui = accounts.ui.copy(active = entry, message = null)
        selection.edit().putString("active", entry.key).apply()
        model = Model(user = entry.name)
        browser.refresh()
    }

    private fun signIn() {
        runCatching {
            accountPicker.launch(AccountPicker.newChooseAccountIntent(
                AccountPicker.AccountChooserOptions.Builder()
                    .setAllowableAccountsTypes(listOf("com.google"))
                    .setAlwaysShowAccountPicker(true).build()))
        }.onFailure { accounts.accountError(localized(R.string.cannot_open_google_account_list)) }
    }

    private fun authorize(target: BrowserRefreshTarget? = null) {
        val entry = accounts.ui.active ?: return
        val refreshTarget = target ?: browser.newBrowserRefreshTarget(entry)
        if (entry.type != AccountType.GOOGLE) return when (entry.type) {
            AccountType.S3 -> browser.loadS3(refreshTarget)
            AccountType.SERVICE -> browser.loadService(refreshTarget)
            AccountType.GOOGLE -> Unit
        }
        browser.updateBrowserTab(refreshTarget) { it.copy(loading = true, message = null) }
        val scopes = listOf(Scope(DRIVE_SCOPE))
        authorization.authorize(AuthorizationRequest.builder().setAccount(Account(entry.id, "com.google"))
            .setRequestedScopes(scopes).build())
            .addOnSuccessListener { result ->
                if (!browser.isBrowserRefreshCurrent(refreshTarget)) return@addOnSuccessListener
                when {
                    result.hasResolution() -> {
                        pendingAuthorization = entry.key
                        pendingAuthorizationTarget = refreshTarget
                        result.pendingIntent?.let {
                            resolution.launch(IntentSenderRequest.Builder(it.intentSender).build())
                        } ?: browser.browserError(refreshTarget, localized(R.string.unable_to_open_permissions))
                    }
                    result.accessToken != null -> browser.loadDrive(result.accessToken!!, refreshTarget)
                    else -> browser.browserError(refreshTarget, localized(R.string.google_does_not_return_drive_permissions))
                }
            }.addOnFailureListener {
                if (browser.isBrowserRefreshCurrent(refreshTarget)) browser.browserError(refreshTarget, localized(R.string.unable_to_grant_google_permission))
            }
    }

    private fun uploadToPhotos(file: DriveFile, mode: PhotosFolderUploadMode) = uploadToPhotos(listOf(file), mode)

    private fun uploadToPhotos(files: List<DriveFile>, mode: PhotosFolderUploadMode) {
        if (model.loading || files.isEmpty()) return
        val eligible = files.distinctBy { it.id }.filter {
            it.isFolder || it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/")
        }
        if (eligible.isEmpty()) {
            Toast.makeText(this, localized(R.string.there_are_no_suitable_photos_videos_or_folders_to_upload_to_google_photos), Toast.LENGTH_LONG).show()
            return
        }
        val source = accounts.ui.active ?: return
        val destinations = accounts.ui.entries.filter { it.type == AccountType.GOOGLE }
        if (destinations.isEmpty()) {
            Toast.makeText(this, localized(R.string.add_a_google_account_to_upload_to_google_photos), Toast.LENGTH_LONG).show()
            return
        }
        fun authorizeDestination(destination: AccountEntry) {
            pendingPhotosFiles = eligible
            pendingPhotosMode = mode
            authorization.authorize(AuthorizationRequest.builder()
                .setAccount(Account(destination.id, "com.google"))
                .setRequestedScopes(listOf(Scope(PHOTOS_SCOPE))).build())
                .addOnSuccessListener { result ->
                    if (accounts.ui.active?.key != source.key) return@addOnSuccessListener
                    when {
                        result.hasResolution() -> {
                            pendingPhotosAuthorization = source.key
                            result.pendingIntent?.let {
                                photosAuthorizationResolution.launch(IntentSenderRequest.Builder(it.intentSender).build())
                            } ?: error(localized(R.string.unable_to_open_google_photos_permissions))
                        }
                        result.accessToken != null -> startPhotosUpload(eligible, result.accessToken!!, mode)
                        else -> error(localized(R.string.google_doesn_t_return_photos_permission))
                    }
                }.addOnFailureListener { error(localized(R.string.unable_to_grant_google_photos_permission)) }
        }
        if (source.type == AccountType.GOOGLE) authorizeDestination(source)
        else android.app.AlertDialog.Builder(this).setTitle(localized(R.string.google_photos_account))
            .setItems(destinations.map { it.id }.toTypedArray()) { _, index -> authorizeDestination(destinations[index]) }
            .setNegativeButton(localized(R.string.cancel), null).show()
    }

    private fun startPhotosUpload(files: List<DriveFile>, photosToken: String, mode: PhotosFolderUploadMode) {
        if (files.isEmpty()) return
        ensureNotificationPermission()
        val session = storageSession() ?: return
        pendingPhotosFiles = emptyList()
        pendingPhotosMode = PhotosFolderUploadMode.RAW
        photosUploads.upload(files, photosToken, mode, session)
    }

    private fun finishAdding(entry: AccountEntry, files: List<DriveFile>, token: String? = null) {
        listingMutations.invalidate()
        pendingAuthorization = null
        pendingAuthorizationTarget = null
        photosLibrary.reset()
        pendingPhotosAuthorization = null
        pendingPhotosFiles = emptyList()
        pendingPhotosMode = PhotosFolderUploadMode.RAW
        pendingUpload = null
        browser.clearSnapshots()
        tab = 0
        selection.edit().putString("active", entry.key).apply()
        accounts.ui = accounts.ui.copy(active = entry, busy = false, message = null, revision = accounts.ui.revision + 1)
        model = Model(user = entry.name, files = files, token = token)
        token?.let(accounts::refreshAccountName)
        browser.cacheFiles(files)
    }

    private fun downloadFile(file: DriveFile) {
        val session = storageSession() ?: return
        val start: () -> Unit = { cloudDownloads.download(file, session) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (pendingLegacyDownload == null) {
                pendingLegacyDownload = start
                downloadPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else start()
    }

    private fun uploadLocalFile(file: DriveFile, done: (Result<Unit>) -> Unit) {
        val root = runCatching { android.os.Environment.getExternalStorageDirectory().canonicalPath }
            .getOrElse { android.os.Environment.getExternalStorageDirectory().absolutePath }
        transferLocalFileToCloud(file, root, model.path.lastOrNull()?.id, false, done)
    }

    private fun transferLocalFileToCloud(
        file: DriveFile, sourceRoot: String, parentId: String?, move: Boolean, done: (Result<Unit>) -> Unit
    ) {
        val session = storageSession()
        if (session == null) {
            done(Result.failure(IllegalStateException(localized(R.string.add_or_select_an_account_before_uploading))))
            return
        }
        if (session.account.type == AccountType.GOOGLE && session.token == null) {
            authorize()
            done(Result.failure(IllegalStateException(localized(R.string.please_grant_drive_permission_before_uploading))))
            return
        }
        ensureNotificationPermission()
        localCloudTransfers.toCloud(file, sourceRoot, parentId, move, session, done) {
            if (accounts.ui.active?.key == session.account.key) browser.refresh(forceNetwork = true)
        }
    }

    private fun pickUpload(folder: Boolean) {
        val account = accounts.ui.active
        if (account == null) {
            Toast.makeText(this, localized(R.string.add_or_select_an_account_before_uploading), Toast.LENGTH_SHORT).show()
            return
        }
        pendingUpload = account.key
        pendingUploadParent = model.path.lastOrNull()?.id
        if (folder) folderPicker.launch(null) else filePicker.launch(arrayOf("*/*"))
    }

    private fun upload(uris: List<Uri>, tree: Uri? = null, parent: String? = null) {
        if (uris.isEmpty() && tree == null) return
        ensureNotificationPermission()
        val session = storageSession() ?: return
        val uploadTab = tab
        val uploadPath = model.path.toList()
        documentTransferUploads.upload(uris, tree, parent, session) {
            // Completion must not refresh another account or interrupt navigation/mutations.
            if (accounts.ui.active?.key == session.account.key &&
                tab == uploadTab && model.path == uploadPath && !model.loading) {
                browser.refresh(forceNetwork = true)
            }
        }
    }

    private fun transferCloudToLocal(
        file: DriveFile, localRoot: String, destinationPath: String, move: Boolean, done: (Result<Unit>) -> Unit
    ) {
        val session = storageSession()
        if (session == null) {
            done(Result.failure(IllegalStateException(localized(R.string.add_or_select_an_account_before_transferring_files))))
            return
        }
        localCloudTransfers.toLocal(file, localRoot, destinationPath, move, session, done) {
            if (accounts.ui.active?.key == session.account.key) browser.refresh(forceNetwork = true)
        }
    }

    private fun moveFile(file: DriveFile, destinationId: String, done: (Result<Unit>) -> Unit) =
        fileMutations.moveFiles(listOf(file), destinationId, done)

    private fun ensureNotificationPermission() {
        if (notificationPermissionPending) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionPending = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openPreview(file: DriveFile, minimized: Boolean = false, swipeQueue: List<DriveFile>? = null) =
        viewerCoordinator.open(file, minimized, swipeQueue)

    private fun swipePreview(index: Int) = viewerCoordinator.swipeTo(index)

    private fun closePreview() = viewerCoordinator.close()

    private fun minimizePreview() = viewerCoordinator.minimize()

    private fun expandPreview() = viewerCoordinator.expand()

    private fun updatePreviewText(text: String) = viewerCoordinator.updateText(text)

    private fun savePreviewText() = viewerCoordinator.saveText()

    private fun signOut() {
        if (viewer != null) closePreview()
        accounts.ui.active?.key?.let { key -> lifecycleScope.launch(Dispatchers.IO) { listingCache.clear(key) } }
        listingMutations.invalidate()
        pendingAuthorization = null
        pendingAuthorizationTarget = null
        photosLibrary.reset()
        pendingPhotosAuthorization = null
        pendingPhotosFiles = emptyList()
        pendingPhotosMode = PhotosFolderUploadMode.RAW
        pendingUpload = null
        serviceTokens.clear()
        accounts.clearActiveGoogle()
        selection.edit().putString("active", "").apply()
        accounts.ui = accounts.ui.copy(active = null)
        browser.clearSnapshots()
        tab = 0
        model = Model(message = localized(R.string.signed_out))
    }

    private fun error(message: String) {
        model = model.copy(loading = false, message = message)
        viewerCoordinator.onAccessTokenError(message)
    }
}
