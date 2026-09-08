package com.starfall.gsadrive

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import java.io.File
import com.starfall.gsadrive.data.FileListCache
import com.starfall.gsadrive.data.DocumentUploads
import com.starfall.gsadrive.ui.FileViewerPage
import com.starfall.gsadrive.ui.MediaMiniPlayer
import com.starfall.gsadrive.ui.SettingsPage
import com.starfall.gsadrive.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.common.util.concurrent.ListenableFuture
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.api.Scope
import com.starfall.gsadrive.data.AccountStore
import com.starfall.gsadrive.data.DriveApi
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.PhotoItem
import com.starfall.gsadrive.data.PhotosApi
import com.starfall.gsadrive.data.S3Api
import com.starfall.gsadrive.data.S3Account
import com.starfall.gsadrive.data.S3Accounts
import com.starfall.gsadrive.data.S3AccountStore
import com.starfall.gsadrive.data.ServiceAccountApi
import com.starfall.gsadrive.data.ServiceAccountCredentials
import com.starfall.gsadrive.data.ServiceAccountStore
import com.starfall.gsadrive.data.ServiceAccessToken
import com.starfall.gsadrive.data.S3Config
import com.starfall.gsadrive.ui.theme.ManyDriveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
private const val PHOTOS_SCOPE = "https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata"

class MainActivity : ComponentActivity() {
    private lateinit var accountPicker: ActivityResultLauncher<Intent>
    private lateinit var servicePicker: ActivityResultLauncher<Array<String>>
    private lateinit var resolution: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var folderPicker: ActivityResultLauncher<Uri?>
    private lateinit var filePicker: ActivityResultLauncher<Array<String>>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private val authorization by lazy { Identity.getAuthorizationClient(this) }
    private val googleStore by lazy { AccountStore(this) }
    private val s3Store by lazy { S3AccountStore(this) }
    private val serviceStore by lazy { ServiceAccountStore(this) }
    private val selection by lazy { getSharedPreferences("manydrive_selection", MODE_PRIVATE) }
    private val listingCache by lazy { FileListCache(File(cacheDir, "file-lists")) }
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var superDark by mutableStateOf(false)
    private var pendingUploadParent: String? = null
    private var s3Accounts = S3Accounts()
    private var serviceAccounts = emptyList<ServiceAccountCredentials>()
    private val serviceTokens = mutableMapOf<String, ServiceAccessToken>()
    private var s3Available = true
    private var serviceAvailable = true
    private var accountUi by mutableStateOf(AccountUi())
    private var model by mutableStateOf(Model())
    private var playback by mutableStateOf<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val viewerCoordinator by lazy {
        ViewerCoordinator(
            context = this,
            scope = lifecycleScope,
            listCache = listingCache,
            activeAccount = { accountUi.active },
            accessToken = { model.token },
            s3Config = { entry -> s3Accounts.accounts.firstOrNull { it.id == entry.id }?.config },
            currentFiles = { model.files },
            player = { playback },
            awaitPlayer = {
                playback ?: withContext(Dispatchers.IO) {
                    controllerFuture?.get() ?: kotlin.error("Dịch vụ phát media chưa sẵn sàng.")
                }
            },
            requestNotificationPermission = ::ensureNotificationPermission
        )
    }
    private val viewer: ViewerState? get() = viewerCoordinator.state
    private var tab by mutableIntStateOf(0)
    private var generation = 0
    private var photosLoadedForAccount: String? = null
    private var pendingAuthorization: String? = null
    private var pendingUpload: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(this, "Không có quyền thông báo: trình phát vẫn chạy nền nhưng điều khiển media có thể không hiện trên thanh thông báo.", Toast.LENGTH_LONG).show()
            }
        }
        val sessionToken = SessionToken(this, ComponentName(this, MediaPlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync().also { future ->
            future.addListener({
                runCatching { future.get() }
                    .onSuccess { controller ->
                        controller.addListener(playbackListener)
                        playback = controller
                        controller.currentMediaItem?.let(viewerCoordinator::syncToMediaItem)
                    }
                    .onFailure {
                        Toast.makeText(this, "Không thể kết nối dịch vụ phát media.", Toast.LENGTH_LONG).show()
                    }
            }, ContextCompat.getMainExecutor(this))
        }
        themeMode = runCatching { ThemeMode.valueOf(selection.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
        superDark = selection.getBoolean("superDark", false)
        pendingUploadParent = savedInstanceState?.getString("uploadParent")
        pendingAuthorization = savedInstanceState?.getString("authorizationAccount")
        pendingUpload = savedInstanceState?.getString("uploadAccount")
        resolution = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val expected = pendingAuthorization
            pendingAuthorization = null
            if (expected == null || expected != accountUi.active?.key) return@registerForActivityResult
            if (result.resultCode != RESULT_OK) {
                error("Đã hủy cấp quyền Google.")
            } else {
                runCatching { authorization.getAuthorizationResultFromIntent(result.data) }
                    .onSuccess { it.accessToken?.let(::loadDrive) ?: error("Google không trả về access token.") }
                    .onFailure { error("Không thể cấp quyền Google.") }
            }
        }
        filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (pendingUpload == accountUi.active?.key) upload(uris, parent = pendingUploadParent)
            pendingUpload = null
        }
        folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null && pendingUpload == accountUi.active?.key) upload(emptyList(), tree = uri, parent = pendingUploadParent)
            pendingUpload = null
        }
        servicePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(::importService)
        }
        accountPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                val type = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)
                if (!email.isNullOrBlank() && type == "com.google") {
                    googleStore.select(email)
                    refreshAccounts()
                    accountUi = accountUi.copy(revision = accountUi.revision + 1, message = null)
                    activate(accountUi.entries.first { it.type == AccountType.GOOGLE && it.id == email })
                } else accountError("Không lấy được tài khoản Google đã chọn.")
            }
        }
        runCatching { s3Store.load() }
            .onSuccess { s3Accounts = it }
            .onFailure { s3Available = false; accountError("Không thể đọc tài khoản S3 đã lưu.") }
        runCatching { serviceStore.load() }
            .onSuccess { serviceAccounts = it }
            .onFailure { serviceAvailable = false; accountError("Không thể đọc Service Account đã lưu.") }
        refreshAccounts()
        val restoredKey = if (selection.contains("active")) selection.getString("active", "")
            else googleStore.active()?.let { "GOOGLE:$it" }
        accountUi.entries.find { it.key == restoredKey }?.let { entry ->
            accountUi = accountUi.copy(active = entry)
            model = Model(user = entry.name)
        }
        setContent {
            ManyDriveTheme(themeMode, superDark) {
                App(model, tab, ::selectTab, { refresh(forceNetwork = true) }, ::signIn, ::authorize, ::connectS3, ::signOut,
                    { pickUpload(false) }, ::createFolder, ::moveToTrash,
                    accountUi.copy(busy = accountUi.busy || model.uploading), ::activate, ::removeAccount,
                    { servicePicker.launch(arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")) },
                    ::openFolder, ::goUp, { pickUpload(true) }, ::restoreFile,
                    themeMode, superDark,
                    { themeMode = it; selection.edit().putString("theme", it.name).apply() },
                    { superDark = it; selection.edit().putBoolean("superDark", it).apply() },
                    { lifecycleScope.launch {
                        withContext(Dispatchers.IO) { listingCache.clearAll() }
                        Toast.makeText(this@MainActivity, "Đã xóa cache danh sách tệp", Toast.LENGTH_SHORT).show()
                    } },
                    viewer, ::openPreview, ::closePreview, ::updatePreviewText, ::savePreviewText,
                    playback, ::minimizePreview, ::expandPreview
                )
            }
        }
        if (pendingAuthorization == null) accountUi.active?.let { refresh() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("authorizationAccount", pendingAuthorization)
        outState.putString("uploadAccount", pendingUpload)
        outState.putString("uploadParent", pendingUploadParent)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        playback?.removeListener(playbackListener)
        controllerFuture?.let(MediaController::releaseFuture)
        playback = null
        super.onDestroy()
    }

    private val playbackListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.let(viewerCoordinator::syncToMediaItem)
        }
    }

    private fun refreshAccounts() {
        accountUi = accountUi.copy(entries =
            googleStore.accounts().map { AccountEntry(AccountType.GOOGLE, it, storedAccountName(AccountType.GOOGLE, it)) } +
                s3Accounts.accounts.map { AccountEntry(AccountType.S3, it.id, it.name, it.config.bucket) } +
                serviceAccounts.map { AccountEntry(AccountType.SERVICE, it.id, storedAccountName(AccountType.SERVICE, it.email)) })
    }

    private fun storedAccountName(type: AccountType, email: String): String =
        selection.getString("name:${type.name}:$email", null)
            ?.takeIf { it.isNotBlank() && '@' !in it } ?: email.substringBefore('@')

    private fun refreshAccountName(token: String) {
        val entry = accountUi.active ?: return
        if (entry.type == AccountType.S3) return
        if (selection.contains("name:${entry.key}")) return
        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) { runCatching { DriveApi.displayName(token) }.getOrNull() }
                ?.takeIf { it.isNotBlank() && '@' !in it } ?: return@launch
            if (accountUi.entries.none { it.key == entry.key }) return@launch
            selection.edit().putString("name:${entry.key}", name).apply()
            refreshAccounts()
            if (accountUi.active?.key == entry.key) {
                accountUi = accountUi.copy(active = entry.copy(name = name))
                model = model.copy(user = name)
            }
        }
    }

    private fun activate(entry: AccountEntry) {
        if (model.uploading) return
        if (viewer != null) closePreview()
        ++generation
        pendingAuthorization = null
        pendingUpload = null
        tab = 0
        photosLoadedForAccount = null
        accountUi = accountUi.copy(active = entry, message = null)
        selection.edit().putString("active", entry.key).apply()
        model = Model(user = entry.name)
        refresh()
    }

    private fun selectTab(next: Int) {
        if (model.uploading || !isTabEnabled(accountUi.active?.type, next) || tab == next) return
        model = model.copy(path = emptyList(), files = emptyList(), fromCache = false)
        tab = next
        val activeKey = accountUi.active?.key
        if (next == 2 && activeKey != null && photosLoadedForAccount == activeKey) {
            model = model.copy(loading = false, message = null)
            return
        }
        refresh()
    }

    private fun openFolder(file: DriveFile) {
        if (!file.isFolder || model.loading || tab == 3) return
        model = model.copy(path = model.path + file, files = emptyList())
        refresh()
    }

    private fun goUp() {
        if (model.path.isEmpty() || model.uploading) return
        model = model.copy(path = model.path.dropLast(1), files = emptyList())
        refresh()
    }

    private fun cacheLocation(): String = "$tab:${model.path.lastOrNull()?.id.orEmpty()}"

    private fun cacheFiles(files: List<DriveFile>) {
        val account = accountUi.active?.key ?: return
        val location = cacheLocation()
        lifecycleScope.launch(Dispatchers.IO) { listingCache.write(account, location, files) }
    }

    private fun refresh(forceNetwork: Boolean = false) {
        val active = accountUi.active ?: return
        if (model.uploading) return
        if (!forceNetwork && tab == 2 && photosLoadedForAccount == active.key) {
            model = model.copy(loading = false, message = null)
            return
        }
        val request = ++generation
        val location = cacheLocation()
        val useCache = tab != 2
        model = model.copy(loading = true, message = null)
        lifecycleScope.launch {
            val cached = if (useCache) withContext(Dispatchers.IO) { listingCache.read(active.key, location) } else null
            if (request != generation) return@launch
            if (cached != null) {
                model = model.copy(files = cached, loading = forceNetwork, fromCache = true)
                if (!forceNetwork) return@launch
            }
            when (active.type) {
                AccountType.GOOGLE -> authorize()
                AccountType.S3 -> loadS3()
                AccountType.SERVICE -> loadService()
            }
        }
    }

    private fun signIn() {
        runCatching {
            accountPicker.launch(AccountPicker.newChooseAccountIntent(
                AccountPicker.AccountChooserOptions.Builder()
                    .setAllowableAccountsTypes(listOf("com.google"))
                    .setAlwaysShowAccountPicker(true).build()))
        }.onFailure { accountError("Không thể mở danh sách tài khoản Google.") }
    }

    private fun authorize() {
        val entry = accountUi.active ?: return
        if (entry.type != AccountType.GOOGLE) return when (entry.type) {
            AccountType.S3 -> loadS3()
            AccountType.SERVICE -> loadService()
            AccountType.GOOGLE -> Unit
        }
        val request = ++generation
        model = model.copy(loading = true, message = null)
        val scopes = listOf(Scope(DRIVE_SCOPE)) + if (tab == 2) listOf(Scope(PHOTOS_SCOPE)) else emptyList()
        authorization.authorize(AuthorizationRequest.builder().setAccount(Account(entry.id, "com.google"))
            .setRequestedScopes(scopes).build())
            .addOnSuccessListener { result ->
                if (request != generation) return@addOnSuccessListener
                when {
                    result.hasResolution() -> {
                        pendingAuthorization = entry.key
                        result.pendingIntent?.let {
                            resolution.launch(IntentSenderRequest.Builder(it.intentSender).build())
                        } ?: error("Không thể mở cấp quyền.")
                    }
                    result.accessToken != null -> loadDrive(result.accessToken!!)
                    else -> error("Google không trả về quyền Drive.")
                }
            }.addOnFailureListener { if (request == generation) error("Không thể cấp quyền Google.") }
    }

    private fun loadDrive(token: String) {
        refreshAccountName(token)
        val request = ++generation
        val selectedTab = tab
        val parentId = model.path.lastOrNull()?.id
        model = model.copy(loading = true, message = null, token = token)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (selectedTab == 2) Pair(emptyList<DriveFile>(), PhotosApi.listAppCreatedMedia(token))
                    else Pair(DriveApi.listFiles(token, sharedWithMe = selectedTab == 1 && parentId == null, parentId = parentId, trashed = selectedTab == 3), emptyList<PhotoItem>())
                }
            }.onSuccess { (files, photos) ->
                if (request == generation) {
                    model = model.copy(files = files, photos = photos, loading = false, fromCache = false)
                    if (selectedTab == 2) photosLoadedForAccount = accountUi.active?.key
                    else cacheFiles(files)
                }
            }.onFailure { if (request == generation) error("Không thể tải dữ liệu Google. Hãy thử làm mới.") }
        }
    }

    private fun loadS3() {
        val account = s3Accounts.accounts.find { it.id == accountUi.active?.id } ?: return
        val request = ++generation
        val prefix = model.path.lastOrNull()?.id.orEmpty()
        model = model.copy(user = account.name, loading = true, message = null)
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { S3Api.list(account.config, prefix) } }
                .onSuccess { if (request == generation) { model = model.copy(files = it, loading = false, fromCache = false); cacheFiles(it) } }
                .onFailure { if (request == generation) error("Không thể kết nối S3. Kiểm tra quyền bucket và mạng rồi thử làm mới.") }
        }
    }

    private fun loadService() {
        val account = serviceAccounts.find { it.id == accountUi.active?.id } ?: return
        val request = ++generation
        val parentId = model.path.lastOrNull()?.id
        val shared = tab == 1 && parentId == null
        val trashed = tab == 3
        val cached = serviceTokens[account.id]?.takeIf { it.validAt(System.currentTimeMillis() / 1000) }
        model = model.copy(user = storedAccountName(AccountType.SERVICE, account.email), loading = true, message = null)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val token = cached ?: ServiceAccountApi.accessToken(account)
                    token to DriveApi.listFiles(token.value, sharedWithMe = shared, parentId = parentId, trashed = trashed)
                }
            }.onSuccess { (token, files) ->
                if (request == generation) {
                    serviceTokens[account.id] = token
                    refreshAccountName(token.value)
                    model = model.copy(token = token.value, files = files, loading = false, fromCache = false)
                    cacheFiles(files)
                }
            }.onFailure {
                if (request == generation) {
                    serviceTokens.remove(account.id)
                    error("Không thể tải Drive của Service Account. Kiểm tra khóa, Drive API và quyền chia sẻ rồi thử làm mới.")
                }
            }
        }
    }

    private fun connectS3(name: String, config: S3Config) {
        if (!s3Available) return accountError("Không thể mở kho tài khoản S3 trên thiết bị.")
        val account = S3Account(name = name.trim(), config = config)
        accountUi = accountUi.copy(busy = true, message = null)
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { S3Api.list(config) } }
                .onSuccess { files ->
                    val saved = s3Accounts.copy(accounts = s3Accounts.accounts + account, activeId = account.id)
                    runCatching { s3Store.save(saved) }
                        .onSuccess {
                            s3Accounts = saved
                            refreshAccounts()
                            finishAdding(AccountEntry(AccountType.S3, account.id, account.name, config.bucket), files)
                        }.onFailure { accountError("Không thể lưu tài khoản S3.") }
                }.onFailure { accountError("Không thể kết nối S3. Kiểm tra thông tin, quyền bucket và mạng.") }
        }
    }

    private fun importService(uri: Uri) {
        if (!serviceAvailable) return accountError("Không thể mở kho Service Account trên thiết bị.")
        accountUi = accountUi.copy(busy = true, message = null)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= ServiceAccountCredentials.MAX_JSON_BYTES) { "File JSON quá lớn." }
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    } ?: throw IllegalArgumentException("Không thể đọc file JSON đã chọn.")
                    val credentials = ServiceAccountCredentials.parse(text)
                    val token = ServiceAccountApi.accessToken(credentials)
                    Triple(credentials, token, DriveApi.listFiles(token.value))
                }
            }.onSuccess { (credentials, token, files) ->
                val saved = serviceAccounts.filterNot { it.id == credentials.id } + credentials
                runCatching { serviceStore.save(saved) }
                    .onSuccess {
                        serviceAccounts = saved
                        serviceTokens[credentials.id] = token
                        refreshAccounts()
                        finishAdding(accountUi.entries.first { it.type == AccountType.SERVICE && it.id == credentials.id }, files, token.value)
                    }.onFailure { accountError("Không thể lưu Service Account trên thiết bị.") }
            }.onFailure {
                accountError(if (it is IllegalArgumentException) it.message ?: "File JSON không hợp lệ."
                    else "Không thể kết nối Service Account. Kiểm tra khóa JSON, Drive API và mạng, rồi nhập lại file.")
            }
        }
    }

    private fun finishAdding(entry: AccountEntry, files: List<DriveFile>, token: String? = null) {
        ++generation
        pendingAuthorization = null
        pendingUpload = null
        tab = 0
        selection.edit().putString("active", entry.key).apply()
        accountUi = accountUi.copy(active = entry, busy = false, message = null, revision = accountUi.revision + 1)
        model = Model(user = entry.name, files = files, token = token)
        token?.let(::refreshAccountName)
        cacheFiles(files)
    }

    private fun removeAccount(entry: AccountEntry) {
        runCatching {
            when (entry.type) {
                AccountType.GOOGLE -> googleStore.remove(entry.id)
                AccountType.S3 -> {
                    val saved = s3Accounts.copy(accounts = s3Accounts.accounts.filterNot { it.id == entry.id },
                        activeId = s3Accounts.activeId?.takeUnless { it == entry.id })
                    s3Store.save(saved)
                    s3Accounts = saved
                }
                AccountType.SERVICE -> {
                    val saved = serviceAccounts.filterNot { it.id == entry.id }
                    serviceStore.save(saved)
                    serviceAccounts = saved
                    serviceTokens.remove(entry.id)
                }
            }
        }.onSuccess {
            lifecycleScope.launch(Dispatchers.IO) { listingCache.clear(entry.key) }
            selection.edit().remove("name:${entry.key}").apply()
            if (accountUi.active?.key == entry.key) signOut()
            refreshAccounts()
        }.onFailure { accountError("Không thể xóa tài khoản đã lưu.") }
    }

    private fun pickUpload(folder: Boolean) {
        val account = accountUi.active
        if (account == null) {
            Toast.makeText(this, "Hãy thêm hoặc chọn một tài khoản trước khi tải lên.", Toast.LENGTH_SHORT).show()
            return
        }
        if (model.uploading) {
            Toast.makeText(this, "Đang có một tác vụ tải lên.", Toast.LENGTH_SHORT).show()
            return
        }
        if (model.loading) {
            Toast.makeText(this, "Danh sách đang cập nhật. Hãy thử lại ngay sau đó.", Toast.LENGTH_SHORT).show()
            return
        }
        if (account.type == AccountType.SERVICE && model.path.isEmpty()) {
            Toast.makeText(this, "Mở một thư mục có quyền ghi của Service Account trước khi tải lên.", Toast.LENGTH_LONG).show()
            return
        }
        pendingUpload = account.key
        pendingUploadParent = model.path.lastOrNull()?.id
        if (folder) folderPicker.launch(null) else filePicker.launch(arrayOf("*/*"))
    }

    private fun upload(uris: List<Uri>, tree: Uri? = null, parent: String? = null) {
        if ((uris.isEmpty() && tree == null) || model.uploading) return
        val account = accountUi.active ?: return
        val s3 = s3Accounts.accounts.find { it.id == account.id }?.config
        val service = serviceAccounts.find { it.id == account.id }
        val existingToken = model.token
        val request = ++generation
        model = model.copy(loading = true, uploading = true, message = "Đang chuẩn bị tải lên…")
        lifecycleScope.launch {
            var completed = 0
            val result = runCatching { withContext(Dispatchers.IO) {
                val token = if (account.type == AccountType.SERVICE) ServiceAccountApi.accessToken(requireNotNull(service)).value
                    else existingToken
                val uploader = DocumentUploads(contentResolver,
                    createFolder = { name, destination ->
                        if (account.type == AccountType.S3) {
                            val key = destination.orEmpty() + name + "/"
                            S3Api.createFolder(requireNotNull(s3), key)
                            key
                        } else DriveApi.createFolder(requireNotNull(token) { "Hãy cấp quyền Drive trước khi tải lên." }, name, destination)
                    },
                    uploadFile = { uri, name, mime, destination ->
                        withContext(Dispatchers.Main) {
                            if (request == generation) model = model.copy(message = "Đang tải: $name · Đã xong $completed tệp")
                        }
                        contentResolver.openInputStream(uri)?.use { input ->
                            if (account.type == AccountType.S3) {
                                val temporary = File.createTempFile("upload-", ".tmp", cacheDir)
                                try {
                                    temporary.outputStream().use { input.copyTo(it, 64 * 1024) }
                                    S3Api.upload(requireNotNull(s3), destination.orEmpty() + name, mime, temporary)
                                } finally { temporary.delete() }
                            } else DriveApi.upload(requireNotNull(token), name, mime, input, destination)
                        } ?: kotlin.error("Không đọc được tệp $name.")
                        completed++
                    })
                if (tree != null) uploader.tree(tree, parent) else uploader.files(uris, parent)
            } }
            withContext(Dispatchers.IO) { listingCache.clear(account.key) }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            if (request == generation) {
                model = model.copy(loading = false, uploading = false, fromCache = false)
                val message = if (result.isSuccess) "Đã tải lên $completed tệp." else
                    "Đã tải $completed tệp; tải lên bị dừng. Kiểm tra quyền ghi, dung lượng và kết nối. Các tệp đã tải được giữ lại."
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                refresh(forceNetwork = true)
            }
        }
    }

    private fun restoreFile(file: DriveFile) {
        val token = model.token ?: return
        val account = accountUi.active ?: return
        val request = ++generation
        model = model.copy(loading = true, message = null)
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                DriveApi.restore(token, file.id)
                listingCache.clear(account.key)
            } }.onSuccess { if (request == generation) refresh(forceNetwork = true) }
                .onFailure { if (request == generation) error("Không thể khôi phục tệp. Kiểm tra quyền và kết nối.") }
        }
    }

    private fun createFolder(name: String) {
        if (model.loading || name.isBlank() || accountUi.active?.type != AccountType.GOOGLE) return
        val token = model.token ?: return
        val request = ++generation
        val parentId = model.path.lastOrNull()?.id
        model = model.copy(loading = true, message = null)
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { DriveApi.createFolder(token, name.trim(), parentId); accountUi.active?.key?.let { listingCache.clear(it) } } }
                .onSuccess { if (request == generation) refresh(forceNetwork = true) }
                .onFailure { if (request == generation) error("Không thể tạo thư mục.") }
        }
    }

    private fun moveToTrash(file: DriveFile) {
        if (model.loading || accountUi.active?.type != AccountType.GOOGLE) return
        val token = model.token ?: return
        val request = ++generation
        model = model.copy(loading = true, message = null)
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { DriveApi.moveToTrash(token, file.id); accountUi.active?.key?.let { listingCache.clear(it) } } }
                .onSuccess { if (request == generation) refresh(forceNetwork = true) }
                .onFailure { if (request == generation) error("Không thể chuyển tệp vào thùng rác.") }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openPreview(file: DriveFile, minimized: Boolean = false, mediaQueue: List<DriveFile>? = null) =
        viewerCoordinator.open(file, minimized, mediaQueue)

    private fun closePreview() = viewerCoordinator.close()

    private fun minimizePreview() = viewerCoordinator.minimize()

    private fun expandPreview() = viewerCoordinator.expand()

    private fun updatePreviewText(text: String) = viewerCoordinator.updateText(text)

    private fun savePreviewText() = viewerCoordinator.saveText()

    private fun signOut() {
        if (model.uploading) return
        if (viewer != null) closePreview()
        accountUi.active?.key?.let { key -> lifecycleScope.launch(Dispatchers.IO) { listingCache.clear(key) } }
        ++generation
        pendingAuthorization = null
        pendingUpload = null
        serviceTokens.clear()
        googleStore.clearActive()
        selection.edit().putString("active", "").apply()
        accountUi = accountUi.copy(active = null)
        tab = 0
        photosLoadedForAccount = null
        model = Model(message = "Đã đăng xuất.")
    }

    private fun accountError(message: String) { accountUi = accountUi.copy(busy = false, message = message) }
    private fun error(message: String) { model = model.copy(loading = false, message = message) }
}
