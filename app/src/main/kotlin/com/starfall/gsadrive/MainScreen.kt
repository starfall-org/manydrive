package com.starfall.gsadrive

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.starfall.gsadrive.data.*
import com.starfall.gsadrive.ui.FileViewerPage
import com.starfall.gsadrive.ui.ExpandableMediaPlayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.starfall.gsadrive.ui.NavigationSwipe
import com.starfall.gsadrive.ui.navigationSwipes
import com.starfall.gsadrive.ui.SettingsPage
import com.starfall.gsadrive.ui.theme.ManyDriveTheme
import com.starfall.gsadrive.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun App(
    model: Model,
    selected: Int,
    select: (Int) -> Unit,
    reload: () -> Unit,
    signIn: () -> Unit,
    authorize: () -> Unit,
    connectS3: (String, S3Config) -> Unit,
    signOut: () -> Unit,
    upload: () -> Unit,
    createFolder: (String) -> Unit,
    trash: (DriveFile) -> Unit,
    fileActions: FileActionCallbacks = FileActionCallbacks(),
    accounts: AccountUi = AccountUi(),
    selectAccount: (AccountEntry) -> Unit = {},
    removeAccount: (AccountEntry) -> Unit = {},
    importService: () -> Unit = {},
    openFolder: (DriveFile) -> Unit = {},
    goUp: () -> Unit = {},
    searchDrive: (String, (Result<List<DriveFile>>) -> Unit) -> Unit = { _, done -> done(Result.success(emptyList())) },
    openSearchFolder: (DriveFile) -> Unit = {},
    uploadFolder: () -> Unit = {},
    restoreFile: (DriveFile) -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    superDark: Boolean = false,
    setThemeMode: (ThemeMode) -> Unit = {},
    setSuperDark: (Boolean) -> Unit = {},
    clearCache: () -> Unit = {},
    viewer: ViewerState? = null,
    openFile: (DriveFile) -> Unit = {},
    closeViewer: () -> Unit = {},
    updateViewerText: (String) -> Unit = {},
    saveViewerText: () -> Unit = {},
    swipeViewer: (Int) -> Unit = {},
    playback: Player? = null,
    minimizeViewer: () -> Unit = {},
    expandViewer: () -> Unit = {}
) {
    var showAccounts by remember { mutableStateOf(false) }
    var showTypes by remember { mutableStateOf(false) }
    var addingS3 by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<AccountEntry?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var folderSearching by remember { mutableStateOf(false) }
    var globalSearchResults by remember { mutableStateOf<List<DriveFile>?>(null) }
    var globalSearchLoading by remember { mutableStateOf(false) }
    var globalSearchError by remember { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    LaunchedEffect(accounts.revision) {
        showAccounts = false
        showTypes = false
        addingS3 = false
    }
    LaunchedEffect(selected, model.path.lastOrNull()?.id) {
        searchQuery = ""
        folderSearching = false
        globalSearchResults = null
        globalSearchLoading = false
        globalSearchError = null
    }
    val active = accounts.active
    LaunchedEffect(searchQuery, active?.key, selected) {
        globalSearchError = null
        val query = searchQuery.trim()
        if (query.isEmpty() || selected !in 0..1 || active == null || active.type == AccountType.S3) {
            globalSearchResults = null
            globalSearchLoading = false
            return@LaunchedEffect
        }
        val accountKey = active.key
        globalSearchResults = emptyList()
        globalSearchLoading = true
        delay(350)
        searchDrive(query) { result ->
            if (searchQuery.trim() != query || accounts.active?.key != accountKey || selected !in 0..1) return@searchDrive
            globalSearchLoading = false
            result.onSuccess { globalSearchResults = it }
                .onFailure {
                    globalSearchResults = emptyList()
                    globalSearchError = it.message ?: "Không thể tìm kiếm trên Drive."
                }
        }
    }
    val tabs = listOf(
        Tab("Tệp", Icons.Outlined.Folder), Tab("Chia sẻ", Icons.Outlined.People)
    )
    val openAccounts = { showAccounts = true }
    val viewerExpanded = viewer != null && !viewer.minimized
    val viewerBlack = viewerExpanded && viewer?.let { isSwipePreview(it.file) } == true
    val mediaViewer = viewer?.takeIf { isMediaPreview(it.file) && it.localPath != null && it.error == null }
    var miniBounds by remember { mutableStateOf<Rect?>(null) }
    var playerTopPadding by remember { mutableStateOf(0.dp) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val normalDarkBars = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        val activity = view.context as? android.app.Activity ?: return@SideEffect
        activity.window.statusBarColor = if (viewerBlack) android.graphics.Color.BLACK else android.graphics.Color.TRANSPARENT
        activity.window.navigationBarColor = if (viewerBlack) android.graphics.Color.BLACK else android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
        }
        androidx.core.view.WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !viewerBlack && !normalDarkBars
            isAppearanceLightNavigationBars = !viewerBlack && !normalDarkBars
        }
    }
    BackHandler(enabled = !viewerExpanded && showFabMenu) { showFabMenu = false }
    BackHandler(enabled = !viewerExpanded && drawerState.isOpen && !showFabMenu) { drawerScope.launch { drawerState.close() } }
    BackHandler(enabled = !viewerExpanded && showSettings && !drawerState.isOpen) { showSettings = false }
    BackHandler(
        enabled = !viewerExpanded && selected == 3 && !showFabMenu && !drawerState.isOpen && !showSettings &&
            !showAccounts && !showTypes && !addingS3
    ) { select(0) }
    BackHandler(enabled = !viewerExpanded && model.path.isNotEmpty() && !showSettings && !showAccounts && !showTypes && !addingS3 && !drawerState.isOpen) { goUp() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Native dragging remains available to close an open drawer. Opening is edge-only.
        gesturesEnabled = !viewerExpanded && drawerState.isOpen,
        drawerContent = {
            DriveNavigationDrawer(
                account = active,
                trashSelected = !showSettings && selected == 3,
                settingsSelected = showSettings,
                onAccounts = {
                    drawerScope.launch { drawerState.close() }
                    showAccounts = true
                },
                onTrash = {
                    if (isTabEnabled(active?.type, 3)) {
                        showSettings = false
                        drawerScope.launch { drawerState.close() }
                        select(3)
                    }
                },
                onSettings = {
                    showSettings = true
                    drawerScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.navigationSwipes(
                    enabled = !viewerExpanded && drawerState.isClosed && !drawerState.isAnimationRunning &&
                        !showFabMenu && !showAccounts && !showTypes && !addingS3 && !showCreateFolderDialog
                ) { gesture ->
                    when (gesture) {
                        NavigationSwipe.OPEN_DRAWER -> drawerScope.launch { drawerState.open() }
                        NavigationSwipe.PREVIOUS_TAB, NavigationSwipe.NEXT_TAB -> {
                            val target = selected + if (gesture == NavigationSwipe.NEXT_TAB) 1 else -1
                            if (!showSettings && selected in tabs.indices && target in tabs.indices &&
                                isTabEnabled(active?.type, target)) select(target)
                        }
                    }
                },
                containerColor = if (viewerBlack) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.background,
                topBar = {
                    when {
                        !viewerExpanded && !showSettings && selected in 0..1 && active != null && model.path.isEmpty() ->
                            DriveRootTopBar(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onMenu = { drawerScope.launch { drawerState.open() } },
                                onAccounts = openAccounts,
                                account = active
                            )
                        !viewerExpanded && !showSettings && selected in 0..1 && active != null && model.path.isNotEmpty() ->
                            FolderBrowserTopBar(
                                title = model.path.last().name,
                                query = searchQuery,
                                searching = folderSearching,
                                onQueryChange = { searchQuery = it },
                                onSearchingChange = { folderSearching = it },
                                onBack = goUp,
                                onRefresh = reload,
                                onAccounts = openAccounts
                            )
                        !viewerExpanded && showSettings -> TopAppBar(
                            title = {
                                Text("Cài đặt", style = MaterialTheme.typography.headlineSmall)
                            },
                            navigationIcon = {
                                IconButton(onClick = { showSettings = false }) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Quay lại")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                        else -> CenterAlignedTopAppBar(
                            title = {
                                Text(when {
                                    viewerExpanded -> viewer?.file?.name.orEmpty()
                                    selected == 3 -> "Thùng rác"
                                    else -> "ManyDrive"
                                }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            navigationIcon = {
                                when {
                                    viewerExpanded -> IconButton(onClick = {
                                        if (viewer?.let { isMediaPreview(it.file) } == true) minimizeViewer() else closeViewer()
                                    }) {
                                        Icon(Icons.AutoMirrored.Outlined.ArrowBack,
                                            if (viewer?.let { isMediaPreview(it.file) } == true) "Thu nhỏ trình phát" else "Đóng trình xem")
                                    }
                                    else -> IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                                        Icon(Icons.Outlined.Menu, "Mở menu")
                                    }
                                }
                            },
                            actions = {
                                if (!viewerExpanded && !showSettings && active != null)
                                    AccountAvatar(active, openAccounts)
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = if (viewerBlack) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.surface,
                                titleContentColor = if (viewerBlack) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
                                navigationIconContentColor = if (viewerBlack) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
                                actionIconContentColor = if (viewerBlack) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                },
                bottomBar = {
                    if (!viewerExpanded || mediaViewer != null) Column(
                        modifier = if (showSettings) Modifier.navigationBarsPadding() else Modifier
                    ) {
                        if (mediaViewer != null && playback != null) {
                            // Reserve and measure the compact slot; the video itself remains in the overlay.
                            Spacer(Modifier.fillMaxWidth().height(72.dp).onGloballyPositioned {
                                miniBounds = it.boundsInRoot()
                            })
                        }
                        if (!showSettings) NavigationBar {
                            tabs.forEachIndexed { index, item ->
                                NavigationBarItem(selected = index == selected, onClick = { showSettings = false; select(index) },
                                    enabled = isTabEnabled(active?.type, index),
                                    icon = { Icon(item.icon, item.label) }, label = { Text(item.label) })
                            }
                        }
                    }
                },
                floatingActionButton = {
                    if (!viewerExpanded && !showSettings) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (showFabMenu) {
                                ExtendedFloatingActionButton(
                                    onClick = {
                                        showFabMenu = false
                                        newFolderName = ""
                                        showCreateFolderDialog = true
                                    },
                                    icon = { Icon(Icons.Outlined.Folder, null) },
                                    text = { Text("Thư mục") },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            FloatingActionButton(
                                onClick = { showFabMenu = !showFabMenu },
                                containerColor = if (showFabMenu) MaterialTheme.colorScheme.inverseSurface
                                    else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (showFabMenu) MaterialTheme.colorScheme.inverseOnSurface
                                    else MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Icon(
                                    if (showFabMenu) Icons.Outlined.Close else Icons.Outlined.Add,
                                    if (showFabMenu) "Đóng menu tạo mới" else "Tạo mới"
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                SideEffect { playerTopPadding = padding.calculateTopPadding() }
                Box(Modifier.fillMaxSize()) {
                    when {
                        viewerExpanded && mediaViewer == null && viewer != null && playback != null -> FileViewerPage(
                            padding = padding,
                            file = viewer.file,
                            localPath = viewer.localPath,
                            text = viewer.text,
                            loading = viewer.loading,
                            error = viewer.error,
                            saving = viewer.saving,
                            player = playback,
                            swipeQueue = viewer.swipeQueue,
                            swipeIndex = viewer.swipeIndex,
                            previewPaths = viewer.previewPaths,
                            onSwipeTo = swipeViewer,
                            onBack = { if (isMediaPreview(viewer.file)) minimizeViewer() else closeViewer() },
                            onTextChange = updateViewerText,
                            onSaveText = saveViewerText
                        )
                        showSettings -> SettingsPage(padding, themeMode, superDark, setThemeMode, setSuperDark, clearCache)
                        active == null -> StoragePage(model, padding, null, { showTypes = true }, openAccounts, signOut, openFile = openFile)
                        else -> PullToRefreshBox(
                            isRefreshing = model.loading,
                            onRefresh = { if (!model.loading && !accounts.busy) reload() },
                            modifier = Modifier.fillMaxSize().padding(padding)
                        ) {
                            val contentPadding = PaddingValues(0.dp)
                            when {
                                selected == 3 -> TrashPage(model, contentPadding, restoreFile)
                                else -> FileBrowserPage(
                                    model = model,
                                    padding = contentPadding,
                                    account = active,
                                    shared = selected == 1,
                                    query = searchQuery,
                                    searchResults = globalSearchResults,
                                    searchLoading = globalSearchLoading,
                                    searchError = globalSearchError,
                                    authorize = authorize,
                                    openFolder = if (globalSearchResults != null && searchQuery.isNotBlank()) openSearchFolder else openFolder,
                                    openFile = openFile,
                                    actions = fileActions.copy(
                                        trash = if (active.type != AccountType.S3 && selected == 0) fileActions.trash else null
                                    )
                                )
                            }
                        }
                    }
                    if (showFabMenu) {
                        Box(
                            Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
                                .clickable { showFabMenu = false }
                        )
                    }
                }
            }
            if (mediaViewer != null && playback != null) {
                ExpandableMediaPlayer(
                    player = playback,
                    title = mediaViewer.file.name,
                    audioOnly = mediaViewer.file.mimeType.startsWith("audio/"),
                    minimized = mediaViewer.minimized,
                    topPadding = playerTopPadding,
                    miniBounds = miniBounds,
                    onMinimize = minimizeViewer,
                    onExpand = expandViewer,
                    onClose = closeViewer,
                    onSwipe = { next ->
                        val index = mediaViewer.swipeIndex + if (next) 1 else -1
                        if (index in mediaViewer.swipeQueue.indices) swipeViewer(index)
                    }
                )
            }
        }
    }

    if (showCreateFolderDialog) AlertDialog(
        onDismissRequest = { showCreateFolderDialog = false },
        title = { Text("Thư mục mới") },
        text = {
            OutlinedTextField(
                value = newFolderName,
                onValueChange = { newFolderName = it },
                label = { Text("Tên thư mục") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = newFolderName.isNotBlank(),
                onClick = {
                    val name = newFolderName.trim()
                    showCreateFolderDialog = false
                    newFolderName = ""
                    createFolder(name)
                }
            ) { Text("Tạo") }
        },
        dismissButton = {
            TextButton(onClick = { showCreateFolderDialog = false }) { Text("Hủy") }
        }
    )

    if (showAccounts) AccountSwitcherDialog(
        accounts = accounts,
        active = active,
        onDismiss = { showAccounts = false },
        onSelect = { entry ->
            showAccounts = false
            selectAccount(entry)
        },
        onRemove = { entry -> removing = entry },
        onAdd = {
            showAccounts = false
            showTypes = true
        }
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
    goUp: () -> Unit = {},
    openFile: (DriveFile) -> Unit = {}
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
        model.message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (account != null && !model.loading && model.message == null && model.files.isEmpty()) item { Text("Chưa có tệp để hiển thị.") }
        items(model.files, key = { it.id }) { FileRow(it, onOpen = openFolder, onPreview = openFile, enabled = !model.loading) }
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
    goUp: () -> Unit = {},
    openFile: (DriveFile) -> Unit = {}
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
        if (!model.loading && model.token != null && model.files.isEmpty()) item { Text("Không có tệp trong vị trí này.") }
        items(model.files, key = { it.id }) { FileRow(it, trash, openFolder, !model.loading, onPreview = openFile) }
    }
}

@Composable
private fun TrashPage(
    model: Model,
    padding: PaddingValues,
    restore: (DriveFile) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        model.message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (!model.loading && model.message == null && model.files.isEmpty()) item { Text("Thùng rác đang trống.") }
        items(model.files, key = { it.id }) { file ->
            FileRow(file = file, enabled = false, onRestore = restore)
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
private fun FileRow(file: DriveFile, onDelete: ((DriveFile) -> Unit)? = null,
    onOpen: (DriveFile) -> Unit = {}, enabled: Boolean = true,
    onRestore: ((DriveFile) -> Unit)? = null, onPreview: (DriveFile) -> Unit = {}) =
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) {
        if (file.isFolder) onOpen(file) else onPreview(file)
    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
        onRestore?.let { IconButton(onClick = { it(file) }) { Icon(Icons.Outlined.Restore, "Khôi phục") } }
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
            model = previewModel, selected = selected, select = { selected = it }, reload = {},
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
