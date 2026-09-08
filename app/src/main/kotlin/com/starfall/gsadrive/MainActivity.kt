package com.starfall.gsadrive

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import java.io.File
import com.starfall.gsadrive.data.FileListCache
import com.starfall.gsadrive.data.DocumentUploads
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
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
    private var tab by mutableIntStateOf(0)
    private var generation = 0
    private var pendingAuthorization: String? = null
    private var pendingUpload: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                App(model, tab, ::selectTab, ::signIn, ::authorize, ::connectS3, ::signOut,
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
                    } }
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
        ++generation
        pendingAuthorization = null
        pendingUpload = null
        tab = 0
        accountUi = accountUi.copy(active = entry, message = null)
        selection.edit().putString("active", entry.key).apply()
        model = Model(user = entry.name)
        refresh()
    }

    private fun selectTab(next: Int) {
        if (model.uploading || !isTabEnabled(accountUi.active?.type, next)) return
        if (tab != next) model = model.copy(path = emptyList(), files = emptyList(), fromCache = false)
        tab = next
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

    private fun refresh() {
        val active = accountUi.active ?: return
        if (model.uploading) return
        val request = ++generation
        val location = cacheLocation()
        val useCache = tab != 2
        model = model.copy(loading = true, message = null)
        lifecycleScope.launch {
            val cached = if (useCache) withContext(Dispatchers.IO) { listingCache.read(active.key, location) } else null
            if (request != generation) return@launch
            if (cached != null) model = model.copy(files = cached, fromCache = true)
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
        if (entry.type != AccountType.GOOGLE) return refresh()
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
                    if (selectedTab != 2) cacheFiles(files)
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
        val account = accountUi.active ?: return
        if (model.loading || model.uploading || tab == 2 || tab == 3) return
        if (account.type == AccountType.SERVICE && model.path.isEmpty()) {
            error("Mở một thư mục có quyền ghi trong bộ nhớ dùng chung để tải lên.")
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
                refresh()
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
            } }.onSuccess { if (request == generation) refresh() }
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
                .onSuccess { if (request == generation) refresh() }
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
                .onSuccess { if (request == generation) refresh() }
                .onFailure { if (request == generation) error("Không thể chuyển tệp vào thùng rác.") }
        }
    }

    private fun signOut() {
        if (model.uploading) return
        accountUi.active?.key?.let { key -> lifecycleScope.launch(Dispatchers.IO) { listingCache.clear(key) } }
        ++generation
        pendingAuthorization = null
        pendingUpload = null
        serviceTokens.clear()
        googleStore.clearActive()
        selection.edit().putString("active", "").apply()
        accountUi = accountUi.copy(active = null)
        tab = 0
        model = Model(message = "Đã đăng xuất.")
    }

    private fun accountError(message: String) { accountUi = accountUi.copy(busy = false, message = message) }
    private fun error(message: String) { model = model.copy(loading = false, message = message) }
}

private data class Model(
    val user: String? = null, val token: String? = null,
    val files: List<DriveFile> = emptyList(), val photos: List<PhotoItem> = emptyList(),
    val loading: Boolean = false, val message: String? = null,
    val path: List<DriveFile> = emptyList(),
    val fromCache: Boolean = false, val uploading: Boolean = false
)

private enum class AccountType(val label: String) { GOOGLE("Google"), S3("S3"), SERVICE("Service Account") }
private fun isTabEnabled(type: AccountType?, index: Int): Boolean = when (index) {
    0 -> true
    1 -> type == AccountType.GOOGLE || type == AccountType.SERVICE
    2 -> type == AccountType.GOOGLE
    3 -> type == AccountType.GOOGLE || type == AccountType.SERVICE
    else -> false
}

private data class AccountEntry(val type: AccountType, val id: String, val name: String, val detail: String = "") {
    val key: String get() = "${type.name}:$id"
    val title: String get() = if (type == AccountType.S3) detail.ifBlank { name } else name.substringBefore('@')
    val subtitle: String get() = if (type == AccountType.S3) name else id
}
private data class AccountUi(
    val entries: List<AccountEntry> = emptyList(),
    val active: AccountEntry? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val revision: Int = 0
)

private data class Tab(val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(
    model: Model,
    selected: Int,
    select: (Int) -> Unit,
    signIn: () -> Unit,
    authorize: () -> Unit,
    connectS3: (String, S3Config) -> Unit,
    signOut: () -> Unit,
    upload: () -> Unit,
    createFolder: (String) -> Unit,
    trash: (DriveFile) -> Unit,
    accounts: AccountUi = AccountUi(),
    selectAccount: (AccountEntry) -> Unit = {},
    removeAccount: (AccountEntry) -> Unit = {},
    importService: () -> Unit = {},
    openFolder: (DriveFile) -> Unit = {},
    goUp: () -> Unit = {}
) {
    var showAccounts by remember { mutableStateOf(false) }
    var showTypes by remember { mutableStateOf(false) }
    var addingS3 by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<AccountEntry?>(null) }
    LaunchedEffect(accounts.revision) {
        showAccounts = false
        showTypes = false
        addingS3 = false
    }
    val active = accounts.active
    val tabs = listOf(
        Tab("Tệp", Icons.Outlined.Folder), Tab("Chia sẻ", Icons.Outlined.People), Tab("Ảnh", Icons.Outlined.Image)
    )
    val openAccounts = { showAccounts = true }
    BackHandler(enabled = model.path.isNotEmpty() && !showAccounts && !showTypes && !addingS3) { goUp() }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ManyDrive", fontWeight = FontWeight.SemiBold)
                        active?.let { Text(it.type.label, style = MaterialTheme.typography.labelSmall) }
                    }
                },
                actions = {
                    IconButton(onClick = openAccounts) { Icon(Icons.Outlined.AccountCircle, "Tài khoản") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(selected = index == selected, onClick = { select(index) },
                        enabled = isTabEnabled(active?.type, index),
                        icon = { Icon(item.icon, item.label) }, label = { Text(item.label) })
                }
            }
        }
    ) { padding ->
        if (active == null) {
            StoragePage(model, padding, null, { showTypes = true }, openAccounts, signOut)
        } else {
            PullToRefreshBox(
                isRefreshing = model.loading,
                onRefresh = { if (!model.loading && !accounts.busy) select(selected) },
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                val contentPadding = PaddingValues(0.dp)
                when {
                    active.type != AccountType.GOOGLE -> StoragePage(model, contentPadding, active,
                        { showTypes = true }, openAccounts, signOut, shared = selected == 1,
                        openFolder = openFolder, goUp = goUp)
                    selected == 2 -> PhotosPage(model, contentPadding, openAccounts, authorize)
                    else -> DrivePage(model, contentPadding, openAccounts, authorize, signOut,
                        upload, createFolder, trash, openFolder, goUp)
                }
            }
        }
    }

    if (showAccounts) AlertDialog(
        onDismissRequest = { if (!accounts.busy) showAccounts = false },
        title = { Text("Tài khoản") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (active != null) TextButton(onClick = { showAccounts = false; signOut() }, enabled = !accounts.busy) { Text("Đăng xuất") }
                if (accounts.busy) Loading()
                if (accounts.entries.isEmpty()) Text("Chưa có tài khoản. Thêm Google, S3 hoặc Service Account để bắt đầu.")
                accounts.entries.forEach { entry ->
                    Surface(
                        color = if (entry.key == active?.key) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).clickable(enabled = !accounts.busy) {
                                showAccounts = false
                                selectAccount(entry)
                            }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (entry.type == AccountType.S3) Icons.Outlined.Cloud else Icons.Outlined.AccountCircle,
                                    null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(entry.title, style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(entry.subtitle, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (entry.key == active?.key) Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(14.dp))
                                        Text(entry.type.label + if (entry.key == active?.key) " • Đang chọn" else "",
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            IconButton(onClick = { removing = entry }, enabled = !accounts.busy) {
                                Icon(Icons.AutoMirrored.Outlined.Logout, "Đăng xuất khỏi ${entry.title}")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showTypes = true }, enabled = !accounts.busy) { Text("Thêm tài khoản") } },
        dismissButton = { TextButton(onClick = { showAccounts = false }, enabled = !accounts.busy) { Text("Đóng") } }
    )
    if (showTypes) AlertDialog(
        onDismissRequest = { showTypes = false },
        title = { Text("Thêm tài khoản") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                TextButton(onClick = { showTypes = false; showAccounts = true; signIn() }) { Text("Google · Tài khoản trên thiết bị") }
                TextButton(onClick = { showTypes = false; addingS3 = true }) { Text("S3 · Nhập thông tin kết nối") }
                TextButton(onClick = { showTypes = false; showAccounts = true; importService() }) { Text("Service Account · Nhập file JSON") }
            }
        },
        confirmButton = { TextButton(onClick = { showTypes = false }) { Text("Hủy") } }
    )
    if (addingS3) S3AccountDialog(onDismiss = { addingS3 = false }, loading = accounts.busy,
        message = accounts.message, connect = connectS3)
    removing?.let { entry ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("Đăng xuất khỏi ${entry.title}?") },
            text = { Text("Tài khoản sẽ được gỡ khỏi danh sách đã lưu trong ứng dụng. Tệp trên đám mây vẫn được giữ nguyên.") },
            confirmButton = { TextButton(onClick = { removeAccount(entry); removing = null }) { Text("Đăng xuất") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Hủy") } })
    }
}

@Composable
private fun StoragePage(
    model: Model,
    padding: PaddingValues,
    account: AccountEntry?,
    add: () -> Unit,
    accounts: () -> Unit,
    signOut: () -> Unit,
    shared: Boolean = false,
    openFolder: (DriveFile) -> Unit = {},
    goUp: () -> Unit = {}
) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (account == null) item {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tệp của bạn", style = MaterialTheme.typography.titleLarge)
                    Text("Kết nối Google, S3 hoặc Service Account để xem tệp.")
                    FilledTonalButton(onClick = add) { Text("Thêm tài khoản") }
                    TextButton(onClick = accounts) { Text("Tài khoản đã lưu") }
                }
            }
        }
        if (shared) item { Text("Chia sẻ với tôi", style = MaterialTheme.typography.titleMedium) }
        if (model.path.isNotEmpty()) item { FolderNavigation(model.path, goUp) }
        model.message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (model.loading) item { Loading() }
        if (account != null && !model.loading && model.message == null && model.files.isEmpty()) item { Text("Chưa có tệp để hiển thị.") }
        items(model.files, key = { it.id }) { FileRow(it, onOpen = openFolder, enabled = !model.loading) }
    }
}

@Composable
private fun DrivePage(
    model: Model,
    padding: PaddingValues,
    signIn: () -> Unit,
    authorize: () -> Unit,
    signOut: () -> Unit,
    upload: () -> Unit,
    createFolder: (String) -> Unit,
    trash: (DriveFile) -> Unit,
    openFolder: (DriveFile) -> Unit = {},
    goUp: () -> Unit = {}
) {
    var folderName by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(model.user?.let { "$it’s Drive" } ?: "Lưu trữ cùng Google Drive",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(if (model.user == null) "Thêm tài khoản để quản lý tệp." else "Bấm biểu tượng tài khoản để chuyển hoặc thêm tài khoản.")
                    Spacer(Modifier.height(12.dp))
                    if (model.user == null) FilledTonalButton(onClick = signIn) { Text("Thêm tài khoản") }
                    else if (model.token == null) FilledTonalButton(onClick = authorize) { Text("Cho phép Drive") }
                    else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = upload) {
                                Icon(
                                    Icons.Outlined.UploadFile,
                                    null
                                ); Spacer(Modifier.width(6.dp)); Text("Tải tệp lên")
                            }
                            FilledTonalButton(onClick = signOut) { Text("Đăng xuất") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                            OutlinedTextField(
                                folderName,
                                { folderName = it },
                                label = { Text("Thư mục mới") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = { createFolder(folderName); folderName = "" },
                                enabled = folderName.isNotBlank(),
                                modifier = Modifier.padding(start = 8.dp)
                            ) { Text("Tạo") }
                        }
                    }
                }
            }
        }
        model.message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (model.loading) item { Loading() }
        if (!model.loading && model.token != null && model.files.isEmpty()) item { Text("Không có tệp trong vị trí này.") }
        if (model.path.isNotEmpty()) item { FolderNavigation(model.path, goUp) }
        items(model.files, key = { it.id }) { FileRow(it, trash, openFolder, !model.loading) }
    }
}

@Composable
private fun PhotosPage(model: Model, padding: PaddingValues, signIn: () -> Unit, authorize: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Google Photos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        item {
            Text(
                "Google chỉ cho phép app đọc ảnh do chính app tạo. Để chọn ảnh bất kỳ, cần dùng Google Photos Picker.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (model.user == null) item { FilledTonalButton(onClick = signIn) { Text("Thêm tài khoản") } }
        else if (model.token == null) item { FilledTonalButton(onClick = authorize) { Text("Cho phép Photos") } }
        if (model.loading) item { Loading() }
        items(model.photos, key = { it.id }) { photo ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Image,
                    null,
                    tint = MaterialTheme.colorScheme.primary
                ); Spacer(Modifier.width(16.dp)); Column {
                Text(photo.filename); Text(
                photo.mimeType,
                style = MaterialTheme.typography.bodySmall
            )
            }
            }
        }
    }
}

@Composable
private fun S3AccountDialog(
    onDismiss: () -> Unit,
    loading: Boolean = false,
    message: String? = null,
    connect: (String, S3Config) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var bucket by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("us-east-1") }
    val validEndpoint = runCatching {
        val uri = java.net.URI(endpoint.trim())
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null
    }.getOrDefault(false)
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Thêm tài khoản S3") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (loading) Loading()
                OutlinedTextField(name, { name = it }, label = { Text("Tên tài khoản") }, enabled = !loading, singleLine = true)
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text("Endpoint HTTPS") }, enabled = !loading, singleLine = true)
                OutlinedTextField(bucket, { bucket = it }, label = { Text("Bucket") }, enabled = !loading, singleLine = true)
                OutlinedTextField(region, { region = it }, label = { Text("Region") }, enabled = !loading, singleLine = true)
                OutlinedTextField(key, { key = it }, label = { Text("Access key") }, enabled = !loading, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(secret, { secret = it }, label = { Text("Secret key") }, enabled = !loading, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && name.isNotBlank() && validEndpoint && key.isNotBlank() && secret.isNotBlank() && bucket.isNotBlank(),
                onClick = { connect(name.trim(), S3Config(endpoint.trim(), key.trim(), secret, bucket.trim(), region.trim().ifBlank { "us-east-1" })) }
            ) { Text("Kết nối và lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Hủy") } }
    )
}

@Composable
private fun FolderNavigation(path: List<DriveFile>, goUp: () -> Unit) {
    if (path.isNotEmpty()) Column {
        TextButton(onClick = goUp) { Text("← Thư mục cha") }
        Text(path.joinToString(" / ") { it.name }, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun FileRow(file: DriveFile, onDelete: ((DriveFile) -> Unit)? = null,
    onOpen: (DriveFile) -> Unit = {}, enabled: Boolean = true) =
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled && file.isFolder) { onOpen(file) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (file.isFolder) Icons.Outlined.Folder else Icons.Outlined.Description,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        ); Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name); Text(
            file.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        }
        onDelete?.let { IconButton(onClick = { it(file) }) { Icon(Icons.Outlined.Delete, "Chuyển vào thùng rác") } }
    }

@Composable
private fun Loading() =
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

// Keep previews alongside the private UI components so they need no Activity or services.
@Preview(name = "Sáng", group = "ManyDrive", showBackground = true, widthDp = 400, heightDp = 850)
@Preview(name = "Tối", group = "ManyDrive", showBackground = true, widthDp = 400, heightDp = 850,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
private annotation class ScreenPreviews

@Preview(name = "Sáng", group = "Components", showBackground = true, widthDp = 400)
@Preview(name = "Tối", group = "Components", showBackground = true, widthDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
private annotation class ComponentPreviews

private val previewFiles = listOf(
    DriveFile("folder", "Tài liệu công việc", "application/vnd.google-apps.folder", null),
    DriveFile("pdf", "Kế hoạch dự án.pdf", "application/pdf", "2026-09-08T09:30:00.000Z"),
    DriveFile("image", "Ảnh chuyến đi cuối tuần.jpg", "image/jpeg", "2026-09-07T14:15:00.000Z")
)

private val previewModel = Model(
    user = "Minh Anh",
    token = "preview-token",
    files = previewFiles,
    photos = listOf(
        PhotoItem("photo-1", "Hoàng hôn trên biển.jpg", "image/jpeg", null),
        PhotoItem("photo-2", "Chuyến đi Đà Lạt.png", "image/png", null)
    )
)

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    ManyDriveTheme {
        Surface { content() }
    }
}

@ScreenPreviews
@Composable
private fun AppPreview() {
    var selected by remember { mutableIntStateOf(0) }
    PreviewSurface {
        App(
            model = previewModel, selected = selected, select = { selected = it },
            signIn = {}, authorize = {}, connectS3 = { _, _ -> }, signOut = {}, upload = {},
            createFolder = {}, trash = {},
            accounts = AccountUi(
                entries = listOf(AccountEntry(AccountType.GOOGLE, "minhanh@example.com", "Minh Anh"),
                    AccountEntry(AccountType.S3, "preview-s3", "Kho công việc", "documents"),
                    AccountEntry(AccountType.SERVICE, "drive@example.iam.gserviceaccount.com", "drive@example.iam.gserviceaccount.com")),
                active = AccountEntry(AccountType.GOOGLE, "minhanh@example.com", "Minh Anh")
            )
        )
    }
}

@Composable
private fun DrivePagePreviewContent(model: Model) {
    PreviewSurface {
        DrivePage(
            model = model, padding = PaddingValues(0.dp), signIn = {}, authorize = {},
            signOut = {}, upload = {}, createFolder = {}, trash = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun DrivePagePreview() = DrivePagePreviewContent(previewModel)

@ScreenPreviews
@Composable
private fun DriveSignedOutPreview() = DrivePagePreviewContent(Model())

@ScreenPreviews
@Composable
private fun DriveAuthorizationPreview() = DrivePagePreviewContent(previewModel.copy(token = null, files = emptyList()))

@ScreenPreviews
@Composable
private fun DriveEmptyPreview() = DrivePagePreviewContent(previewModel.copy(files = emptyList()))

@ScreenPreviews
@Composable
private fun DriveLoadingPreview() = DrivePagePreviewContent(previewModel.copy(loading = true, files = emptyList()))

@ScreenPreviews
@Composable
private fun DriveErrorPreview() = DrivePagePreviewContent(
    previewModel.copy(message = "Không thể tải danh sách tệp. Vui lòng thử lại.", files = emptyList())
)

@ScreenPreviews
@Composable
private fun PhotosPagePreview() {
    PreviewSurface { PhotosPage(previewModel, PaddingValues(0.dp), {}, {}) }
}

@ScreenPreviews
@Composable
private fun PhotosSignedOutPreview() {
    PreviewSurface { PhotosPage(Model(), PaddingValues(0.dp), {}, {}) }
}

@ScreenPreviews
@Composable
private fun S3PagePreview() {
    PreviewSurface {
        StoragePage(Model(files = previewFiles), PaddingValues(0.dp),
            AccountEntry(AccountType.S3, "s3-preview", "Kho công việc", "documents"), {}, {}, {})
    }
}

@ScreenPreviews
@Composable
private fun ServiceAccountPagePreview() {
    PreviewSurface {
        StoragePage(Model(files = previewFiles), PaddingValues(0.dp),
            AccountEntry(AccountType.SERVICE, "drive@example.iam.gserviceaccount.com", "drive@example.iam.gserviceaccount.com"), {}, {}, {})
    }
}

@ScreenPreviews
@Composable
private fun AccountsEmptyPreview() {
    PreviewSurface { StoragePage(Model(), PaddingValues(0.dp), null, {}, {}, {}) }
}

@ComponentPreviews
@Composable
private fun FileRowPreview() {
    PreviewSurface { FileRow(previewFiles[1], onDelete = {}) }
}

@ComponentPreviews
@Composable
private fun FolderRowPreview() {
    PreviewSurface { FileRow(previewFiles[0]) }
}

@ComponentPreviews
@Composable
private fun LoadingPreview() {
    PreviewSurface { Loading() }
}

@ScreenPreviews
@Composable
private fun S3AddAccountPreview() {
    PreviewSurface { S3AccountDialog(onDismiss = {}, connect = { _, _ -> }) }
}
