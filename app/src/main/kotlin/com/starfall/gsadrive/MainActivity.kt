package com.starfall.gsadrive

import android.app.PendingIntent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.UploadFile
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.starfall.gsadrive.data.AccountStore
import com.starfall.gsadrive.data.DriveApi
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.PhotoItem
import com.starfall.gsadrive.data.PhotosApi
import com.starfall.gsadrive.data.S3Api
import com.starfall.gsadrive.data.S3Config
import com.starfall.gsadrive.ui.theme.ManyDriveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
private const val PHOTOS_SCOPE = "https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata"

class MainActivity : ComponentActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val credentials by lazy { CredentialManager.create(this) }
    private val authorization by lazy { Identity.getAuthorizationClient(this) }
    private val accounts by lazy { AccountStore(this) }
    private lateinit var resolution: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var filePicker: ActivityResultLauncher<Array<String>>
    private var model by mutableStateOf(Model())
    private var tab by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        resolution = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            runCatching { authorization.getAuthorizationResultFromIntent(result.data) }
                .onSuccess { it.accessToken?.let(::loadDrive) ?: error("Google không trả về access token") }
                .onFailure { error(it.localizedMessage ?: "Không thể cấp quyền") }
        }
        filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> upload(uris) }
        auth.currentUser?.email?.let(accounts::select)
        model = model.copy(
            user = auth.currentUser?.displayName ?: auth.currentUser?.email,
            savedAccounts = accounts.accounts()
        )
        setContent {
            ManyDriveTheme {
                App(
                    model,
                    tab,
                    { selectTab(it) },
                    ::signIn,
                    ::authorize,
                    ::connectS3,
                    ::signOut,
                    { filePicker.launch(arrayOf("*/*")) },
                    ::createFolder,
                    ::moveToTrash
                )
            }
        }
    }

    private fun selectTab(next: Int) {
        tab = next
        model.token?.let { token ->
            when (next) {
                0 -> loadDrive(token)
                1 -> loadDrive(token, shared = true)
                2 -> loadPhotos(token)
            }
        }
    }

    private fun signIn() {
        if (getString(R.string.google_oauth_server_client_id).startsWith("REPLACE_")) {
            error("Cần đặt Web OAuth client ID trong res/values/auth.xml.")
            return
        }
        model = model.copy(loading = true, message = null)
        lifecycleScope.launch {
            runCatching {
                val option =
                    GetGoogleIdOption.Builder().setServerClientId(getString(R.string.google_oauth_server_client_id))
                        .setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(false).build()
                credentials.getCredential(
                    this@MainActivity,
                    GetCredentialRequest.Builder().addCredentialOption(option).build()
                ).credential
            }.onSuccess { credential ->
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).addOnSuccessListener {
                        auth.currentUser?.email?.let(accounts::select)
                        model = model.copy(
                            user = auth.currentUser?.displayName ?: auth.currentUser?.email,
                            savedAccounts = accounts.accounts(),
                            loading = false
                        )
                        authorize()
                    }.addOnFailureListener { error("Firebase: ${it.localizedMessage}") }
                } else error("Google không trả về Google ID token.")
            }.onFailure { error("Đăng nhập Google: ${it.localizedMessage}") }
        }
    }

    private fun authorize() {
        model = model.copy(loading = true, message = null)
        authorization.authorize(
            AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_SCOPE), Scope(PHOTOS_SCOPE))).build()
        )
            .addOnSuccessListener { result ->
                when {
                    result.accessToken != null -> loadDrive(result.accessToken!!)
                    result.hasResolution() -> {
                        model = model.copy(loading = false)
                        result.pendingIntent?.let {
                            resolution.launch(
                                IntentSenderRequest.Builder(it.intentSender).build()
                            )
                        } ?: error("Không thể mở cấp quyền.")
                    }

                    else -> error("Google không trả về quyền Drive.")
                }
            }.addOnFailureListener { error("Cấp quyền Google: ${it.localizedMessage}") }
    }

    private fun loadDrive(token: String, shared: Boolean = false) {
        model = model.copy(loading = true, message = null, token = token)
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { DriveApi.listFiles(token, sharedWithMe = shared) } }
                .onSuccess { model = model.copy(files = it, loading = false) }
                .onFailure { error("Google Drive: ${it.localizedMessage}") }
        }
    }

    private fun loadPhotos(token: String) {
        model = model.copy(loading = true, message = null, token = token)
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { PhotosApi.listAppCreatedMedia(token) } }
                .onSuccess { model = model.copy(photos = it, loading = false) }
                .onFailure { error("Google Photos: ${it.localizedMessage}") }
        }
    }

    private fun connectS3(config: S3Config) {
        model = model.copy(loading = true, message = null)
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { S3Api.list(config) } }
                .onSuccess { model = model.copy(s3Files = it, loading = false) }
                .onFailure { error("S3: ${it.localizedMessage}") }
        }
    }

    private fun upload(uris: List<Uri>) {
        val token = model.token ?: return error("Hãy cho phép Google Drive trước khi tải tệp lên.")
        if (uris.isEmpty()) return
        model = model.copy(loading = true, message = null)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    uris.forEach { uri ->
                        val name = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            cursor.moveToFirst()
                            cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                        } ?: uri.lastPathSegment ?: "upload"
                        val bytes =
                            contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: kotlin.error("Không thể đọc $name")
                        DriveApi.upload(token, name, contentResolver.getType(uri) ?: "application/octet-stream", bytes)
                    }
                }
            }.onSuccess { loadDrive(token, tab == 1) }.onFailure { error("Tải tệp lên: ${it.localizedMessage}") }
        }
    }

    private fun createFolder(name: String) {
        val token = model.token ?: return error("Hãy cho phép Google Drive trước.")
        if (name.isBlank()) return
        model = model.copy(loading = true, message = null)
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { DriveApi.createFolder(token, name.trim()) } }
                .onSuccess { loadDrive(token, tab == 1) }
                .onFailure { error("Tạo thư mục: ${it.localizedMessage}") }
        }
    }

    private fun moveToTrash(file: DriveFile) {
        val token = model.token ?: return
        model = model.copy(loading = true, message = null)
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { DriveApi.moveToTrash(token, file.id) } }
                .onSuccess { loadDrive(token, tab == 1) }
                .onFailure { error("Chuyển vào thùng rác: ${it.localizedMessage}") }
        }
    }

    private fun signOut() {
        auth.signOut(); model = Model(message = "Đã đăng xuất.", savedAccounts = accounts.accounts())
    }

    private fun error(text: String) {
        model = model.copy(loading = false, message = text)
    }
}

private data class Model(
    val user: String? = null, val savedAccounts: List<String> = emptyList(), val token: String? = null,
    val files: List<DriveFile> = emptyList(), val photos: List<PhotoItem> = emptyList(),
    val s3Files: List<DriveFile> = emptyList(), val loading: Boolean = false, val message: String? = null
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
    connectS3: (S3Config) -> Unit,
    signOut: () -> Unit,
    upload: () -> Unit,
    createFolder: (String) -> Unit,
    trash: (DriveFile) -> Unit
) {
    val tabs = listOf(
        Tab("Tệp", Icons.Outlined.Folder),
        Tab("Chia sẻ", Icons.Outlined.People),
        Tab("Ảnh", Icons.Outlined.Image),
        Tab("S3", Icons.Outlined.Cloud)
    )
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("ManyDrive", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = signIn) { Icon(Icons.Outlined.AccountCircle, "Tài khoản") }
                    IconButton(
                        onClick = { model.token?.let { if (selected == 2) select(2) else select(selected) } },
                        enabled = model.token != null
                    ) { Icon(Icons.Outlined.Refresh, "Làm mới") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, item ->
                    NavigationBarItem(
                        selected = i == selected,
                        onClick = { select(i) },
                        icon = { Icon(item.icon, item.label) },
                        label = { Text(item.label) })
                }
            }
        }
    ) { padding ->
        when (selected) {
            2 -> PhotosPage(model, padding, signIn, authorize)
            3 -> S3Page(model, padding, connectS3)
            else -> DrivePage(model, padding, signIn, authorize, signOut, upload, createFolder, trash)
        }
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
    trash: (DriveFile) -> Unit
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
                    Text(if (model.user == null) "Đăng nhập để quản lý tệp Drive." else "Tài khoản đã lưu: ${model.savedAccounts.size}. Bấm biểu tượng hồ sơ để thêm hoặc chuyển tài khoản.")
                    Spacer(Modifier.height(12.dp))
                    if (model.user == null) FilledTonalButton(onClick = signIn) { Text("Đăng nhập Google") }
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
        items(model.files, key = { it.id }) { FileRow(it, trash) }
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
        if (model.user == null) item { FilledTonalButton(onClick = signIn) { Text("Đăng nhập Google") } }
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
private fun S3Page(model: Model, padding: PaddingValues, connect: (S3Config) -> Unit) {
    var endpoint by remember { mutableStateOf("") };
    var key by remember { mutableStateOf("") };
    var secret by remember { mutableStateOf("") };
    var bucket by remember { mutableStateOf("") };
    var region by remember { mutableStateOf("us-east-1") }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "S3 Compatible Storage",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            Text(
                "AWS S3, MinIO và storage S3-compatible. Secret key chỉ được giữ trong phiên hiện tại.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            OutlinedTextField(
                endpoint,
                { endpoint = it },
                label = { Text("Endpoint") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                key,
                { key = it },
                label = { Text("Access key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                secret,
                { secret = it },
                label = { Text("Secret key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                bucket,
                { bucket = it },
                label = { Text("Bucket") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                region,
                { region = it },
                label = { Text("Region") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            FilledTonalButton(
                onClick = { connect(S3Config(endpoint, key, secret, bucket, region)) },
                enabled = endpoint.isNotBlank() && key.isNotBlank() && secret.isNotBlank() && bucket.isNotBlank()
            ) { Text("Kết nối S3") }
        }
        model.message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (model.loading) item { Loading() }
        items(model.s3Files, key = { it.id }) { FileRow(it) }
    }
}

@Composable
private fun FileRow(file: DriveFile, onDelete: ((DriveFile) -> Unit)? = null) =
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
    savedAccounts = listOf("minhanh@example.com", "work@example.com"),
    token = "preview-token",
    files = previewFiles,
    photos = listOf(
        PhotoItem("photo-1", "Hoàng hôn trên biển.jpg", "image/jpeg", null),
        PhotoItem("photo-2", "Chuyến đi Đà Lạt.png", "image/png", null)
    ),
    s3Files = previewFiles.filterNot { it.isFolder }
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
            signIn = {}, authorize = {}, connectS3 = {}, signOut = {}, upload = {},
            createFolder = {}, trash = {}
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
    PreviewSurface { S3Page(previewModel, PaddingValues(0.dp), {}) }
}

@ScreenPreviews
@Composable
private fun S3ConnectionPreview() {
    PreviewSurface { S3Page(Model(), PaddingValues(0.dp), {}) }
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
