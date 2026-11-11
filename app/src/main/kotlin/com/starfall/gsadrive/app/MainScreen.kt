package com.starfall.gsadrive.app

import com.starfall.gsadrive.R

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.starfall.gsadrive.app.platform.AppSystemBars
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.S3Config
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.feature.accounts.AccountSwitcherDialog
import com.starfall.gsadrive.feature.accounts.AccountTypeDialog
import com.starfall.gsadrive.feature.accounts.AccountUi
import com.starfall.gsadrive.feature.accounts.S3AccountDialog
import com.starfall.gsadrive.feature.accounts.isTabEnabled
import com.starfall.gsadrive.feature.browser.BrowserPageActions
import com.starfall.gsadrive.feature.browser.BrowserTabSearchState
import com.starfall.gsadrive.feature.browser.BrowserTabsPage
import com.starfall.gsadrive.feature.browser.BrowserUiState
import com.starfall.gsadrive.feature.browser.CreateFileMenu
import com.starfall.gsadrive.feature.browser.CreateFolderDialog
import com.starfall.gsadrive.feature.browser.DriveRootTopBar
import com.starfall.gsadrive.feature.browser.FolderBrowserTopBar
import com.starfall.gsadrive.feature.browser.TrashPage
import com.starfall.gsadrive.feature.browser.rememberBrowserSearch
import com.starfall.gsadrive.feature.fileactions.FileActionCallbacks
import com.starfall.gsadrive.feature.photos.GooglePhotosPage
import com.starfall.gsadrive.feature.settings.SettingsPage
import com.starfall.gsadrive.feature.systemfiles.SystemFilesPage
import com.starfall.gsadrive.feature.systemfiles.SystemFilesTopBar
import com.starfall.gsadrive.feature.viewer.ViewerState
import com.starfall.gsadrive.feature.viewer.isMediaPreview
import com.starfall.gsadrive.feature.viewer.isSwipePreview
import com.starfall.gsadrive.feature.viewer.ui.ExpandableMediaPlayer
import com.starfall.gsadrive.feature.viewer.ui.FileViewerPage
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.model.Model
import com.starfall.gsadrive.shared.ui.NavigationSwipe
import com.starfall.gsadrive.shared.ui.UserRefreshBox
import com.starfall.gsadrive.shared.ui.navigationSwipes
import com.starfall.gsadrive.shared.ui.theme.ThemeMode
import java.io.File
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class MainDestination { BROWSER, TRASH, PHOTOS, SYSTEM_FILES, SETTINGS }


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
    restoreTrashFiles: (List<DriveFile>) -> Unit = {},
    deleteTrashFiles: (List<DriveFile>) -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    superDark: Boolean = false,
    setThemeMode: (ThemeMode) -> Unit = {},
    setSuperDark: (Boolean) -> Unit = {},
    showHiddenSystemFiles: Boolean = false,
    setShowHiddenSystemFiles: (Boolean) -> Unit = {},
    clearCache: () -> Unit = {},
    viewer: ViewerState? = null,
    openFile: (DriveFile, List<DriveFile>) -> Unit = { _, _ -> },
    closeViewer: () -> Unit = {},
    updateViewerText: (String) -> Unit = {},
    saveViewerText: () -> Unit = {},
    swipeViewer: (Int) -> Unit = {},
    playback: Player? = null,
    minimizeViewer: () -> Unit = {},
    expandViewer: () -> Unit = {},
    browserModels: Map<Int, Model> = emptyMap(),
    openLocalFile: (DriveFile, List<DriveFile>) -> Unit = { _, _ -> },
    uploadLocalFile: (DriveFile, (Result<Unit>) -> Unit) -> Unit = { _, done -> done(Result.failure(IllegalStateException(localized(R.string.add_or_select_an_account_before_transferring_files)))) },
    transferLocalToCloud: (DriveFile, String, String?, Boolean, (Result<Unit>) -> Unit) -> Unit = { _, _, _, _, done ->
        done(Result.failure(IllegalStateException(localized(R.string.add_or_select_an_account_before_transferring_files))))
    }
) {
    var showAccounts by remember { mutableStateOf(false) }
    var showTypes by remember { mutableStateOf(false) }
    var addingS3 by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<AccountEntry?>(null) }
    val systemFilesState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var showSystemFiles by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val defaultSystemFilesRoot = remember {
        runCatching { Environment.getExternalStorageDirectory().canonicalPath }
            .getOrElse { Environment.getExternalStorageDirectory().absolutePath }
    }
    var systemFilesRoot by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(defaultSystemFilesRoot) }
    var systemFilesDirectory by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(defaultSystemFilesRoot) }
    var systemFilesQuery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var systemFilesSearching by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var systemFilesRevision by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val active = accounts.active
    val tabSearchStates = rememberBrowserSearch(active, selected, searchDrive)
    fun tabSearchState(index: Int) = tabSearchStates[index] ?: BrowserTabSearchState()
    fun updateTabSearch(index: Int, update: (BrowserTabSearchState) -> BrowserTabSearchState) {
        tabSearchStates[index] = update(tabSearchState(index))
    }
    val currentSearch = if (selected in 0..1) tabSearchState(selected) else BrowserTabSearchState()
    val searchQuery = currentSearch.query
    val folderSearching = currentSearch.folderSearching

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    LaunchedEffect(accounts.revision) {
        showAccounts = false
        showTypes = false
        addingS3 = false
    }
    val tabs = listOf(
        Tab(localized(R.string.files), Icons.Outlined.Folder), Tab(localized(R.string.share), Icons.Outlined.People)
    )
    val tabPagerState = rememberPagerState(
        initialPage = selected.coerceIn(0, tabs.lastIndex),
        pageCount = { tabs.size }
    )
    LaunchedEffect(selected) {
        if (selected in tabs.indices && !tabPagerState.isScrollInProgress && tabPagerState.currentPage != selected) {
            tabPagerState.scrollToPage(selected)
        }
    }
    LaunchedEffect(tabPagerState, active?.type, selected) {
        // Sidebar destinations are outside the pager; its retained page must not replace them.
        if (selected !in tabs.indices) return@LaunchedEffect
        snapshotFlow { tabPagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page in tabs.indices && page != selected && isTabEnabled(active?.type, page)) select(page)
            }
    }
    fun requestBrowserTab(index: Int) {
        if (index !in tabs.indices || !isTabEnabled(active?.type, index)) return
        showSettings = false
        showSystemFiles = false
        if (selected in tabs.indices) {
            drawerScope.launch { tabPagerState.animateScrollToPage(index) }
        } else {
            select(index)
        }
    }
    val openAccounts = { showAccounts = true }
    val viewerExpanded = viewer != null && !viewer.minimized
    val mediaViewer = viewer?.takeIf { isSwipePreview(it.file) && it.swipeQueue.isNotEmpty() }
    // Non-media previews still use the app viewer scaffold. Expanded media does not.
    val appViewerExpanded = viewerExpanded && mediaViewer == null
    val viewerBlack = viewerExpanded && viewer?.let { isSwipePreview(it.file) } == true
    val destination = when {
        showSettings -> MainDestination.SETTINGS
        showSystemFiles -> MainDestination.SYSTEM_FILES
        selected == 4 -> MainDestination.PHOTOS
        selected == 3 -> MainDestination.TRASH
        else -> MainDestination.BROWSER
    }
    var miniBounds by remember { mutableStateOf<Rect?>(null) }
    var playerTopPadding by remember { mutableStateOf(0.dp) }
    AppSystemBars(viewerBlack, themeMode)
    BackHandler(enabled = !viewerExpanded && showFabMenu) { showFabMenu = false }
    BackHandler(enabled = !viewerExpanded && drawerState.isOpen && !showFabMenu) { drawerScope.launch { drawerState.close() } }
    BackHandler(enabled = !appViewerExpanded && showSettings && !drawerState.isOpen) { showSettings = false }
    BackHandler(enabled = !viewerExpanded && showSystemFiles && systemFilesSearching && !drawerState.isOpen) {
        systemFilesQuery = ""
        systemFilesSearching = false
    }
    BackHandler(
        enabled = !viewerExpanded && selected in setOf(3, 4) && !showFabMenu && !drawerState.isOpen && !showSettings && !showSystemFiles &&
            !showAccounts && !showTypes && !addingS3
    ) { select(0) }
    BackHandler(enabled = !viewerExpanded && model.path.isNotEmpty() && !showSettings && !showSystemFiles && !showAccounts && !showTypes && !addingS3 && !drawerState.isOpen) {
        if (selected in tabs.indices) tabSearchStates[selected] = BrowserTabSearchState()
        goUp()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Native dragging remains available to close an open drawer. Opening is edge-only.
        gesturesEnabled = !viewerExpanded && drawerState.isOpen,
        drawerContent = {
            DriveNavigationDrawer(
                account = active,
                photosSelected = !showSettings && !showSystemFiles && selected == 4,
                onPhotos = {
                    showSettings = false
                    showSystemFiles = false
                    showFabMenu = false
                    select(4)
                    drawerScope.launch { drawerState.close() }
                },
                trashSelected = !showSettings && !showSystemFiles && selected == 3,
                settingsSelected = showSettings,
                systemFilesSelected = showSystemFiles && !showSettings,
                onSystemFiles = {
                    showSystemFiles = true
                    showSettings = false
                    showFabMenu = false
                    drawerScope.launch { drawerState.close() }
                },
                onAccounts = {
                    drawerScope.launch { drawerState.close() }
                    showAccounts = true
                },
                onTrash = {
                    if (isTabEnabled(active?.type, 3)) {
                        showSettings = false
                        showSystemFiles = false
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
            // Keep the browser mounted and geometrically stable behind the independent media
            // screen. This avoids rebuilding the whole browser during mini/full transitions.
            Scaffold(
                containerColor = if (appViewerExpanded && viewerBlack) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.background,
                topBar = {
                    when {
                        !appViewerExpanded && !showSettings && !showSystemFiles && selected in 0..1 && (active == null || model.path.isEmpty()) ->
                            DriveRootTopBar(
                                query = searchQuery,
                                onQueryChange = { value ->
                                    if (selected in tabs.indices) updateTabSearch(selected) { it.copy(query = value) }
                                },
                                onMenu = { drawerScope.launch { drawerState.open() } },
                                onAccounts = openAccounts,
                                account = active
                            )
                        !appViewerExpanded && !showSettings && !showSystemFiles && selected in 0..1 && active != null && model.path.isNotEmpty() ->
                            FolderBrowserTopBar(
                                title = model.path.last().name,
                                query = searchQuery,
                                searching = folderSearching,
                                onQueryChange = { value ->
                                    if (selected in tabs.indices) updateTabSearch(selected) { it.copy(query = value) }
                                },
                                onSearchingChange = { searching ->
                                    if (selected in tabs.indices) updateTabSearch(selected) { it.copy(folderSearching = searching) }
                                },
                                onBack = {
                                    if (selected in tabs.indices) tabSearchStates[selected] = BrowserTabSearchState()
                                    goUp()
                                },
                                onRefresh = reload,
                                onAccounts = openAccounts
                            )
                        !appViewerExpanded && !showSettings && showSystemFiles -> SystemFilesTopBar(
                            root = systemFilesRoot, directory = systemFilesDirectory,
                            query = systemFilesQuery, searching = systemFilesSearching,
                            onQueryChange = { systemFilesQuery = it },
                            onBack = {
                                systemFilesQuery = ""
                                systemFilesSearching = false
                                if (systemFilesDirectory == systemFilesRoot) {
                                    showSystemFiles = false
                                    select(0)
                                } else systemFilesDirectory = File(systemFilesDirectory).parent ?: systemFilesRoot
                            },
                            onCloseSearch = { systemFilesQuery = ""; systemFilesSearching = false },
                            onSearch = { systemFilesSearching = true },
                            onRefresh = { systemFilesRevision++ }
                        )
                        !appViewerExpanded && showSettings -> TopAppBar(
                            title = {
                                Text(localized(R.string.settings), style = MaterialTheme.typography.headlineSmall)
                            },
                            navigationIcon = {
                                IconButton(onClick = { showSettings = false }) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, localized(R.string.back))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                        else -> AppPageTopBar(
                            viewer = viewer, expanded = appViewerExpanded, selected = selected, black = viewerBlack,
                            account = active, showAccount = !appViewerExpanded && !showSettings && !showSystemFiles,
                            onViewerBack = { if (viewer?.let { isMediaPreview(it.file) } == true) minimizeViewer() else closeViewer() },
                            onBrowser = { select(0) }, onMenu = { drawerScope.launch { drawerState.open() } },
                            onAccounts = openAccounts
                        )
                    }
                },
                bottomBar = {
                    // This browser bar is retained only under the mini/full transition.
                    if (!appViewerExpanded) Column(
                        modifier = if (showSettings || showSystemFiles || selected in setOf(3, 4)) {
                            Modifier.navigationBarsPadding()
                        } else {
                            Modifier
                        }
                    ) {
                        if (mediaViewer != null && playback != null) {
                            // Reserve and measure the compact slot; the video itself remains in the overlay.
                            Spacer(Modifier.fillMaxWidth().height(72.dp).onGloballyPositioned {
                                miniBounds = it.boundsInRoot()
                            })
                        }
                        if (!showSettings && !showSystemFiles && selected !in setOf(3, 4)) NavigationBar {
                            tabs.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    selected = !showSystemFiles && selected in tabs.indices && index == tabPagerState.currentPage,
                                    onClick = { requestBrowserTab(index) },
                                    enabled = isTabEnabled(active?.type, index),
                                    icon = { Icon(item.icon, item.label) }, label = { Text(item.label) })
                            }
                        }
                    }
                },
                floatingActionButton = {
                    if (!appViewerExpanded && !showSettings && !showSystemFiles && selected !in setOf(3, 4)) {
                        CreateFileMenu(
                            expanded = showFabMenu,
                            onToggle = { if (active == null) showTypes = true else showFabMenu = !showFabMenu },
                            onUpload = { showFabMenu = false; upload() },
                            onUploadFolder = { showFabMenu = false; uploadFolder() },
                            onCreateFolder = { showFabMenu = false; newFolderName = ""; showCreateFolderDialog = true }
                        )
                    }
                }
            ) { padding ->
                SideEffect { playerTopPadding = padding.calculateTopPadding() }
                Box(Modifier.fillMaxSize()) {
                    if (appViewerExpanded && viewer != null && playback != null) {
                        FileViewerPage(
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
                    } else {
                        AnimatedContent(
                            targetState = destination,
                            transitionSpec = {
                                val direction = if (targetState.ordinal > initialState.ordinal) {
                                    AnimatedContentTransitionScope.SlideDirection.Left
                                } else {
                                    AnimatedContentTransitionScope.SlideDirection.Right
                                }
                                val enter = slideIntoContainer(direction, tween(300, easing = FastOutSlowInEasing)) +
                                    fadeIn(tween(300))
                                val exit = slideOutOfContainer(direction, tween(300, easing = FastOutSlowInEasing)) +
                                    fadeOut(tween(220))
                                enter.togetherWith(exit)
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "Main page"
                        ) { page ->
                            when (page) {
                                MainDestination.SETTINGS -> SettingsPage(
                                    padding, themeMode, superDark, showHiddenSystemFiles,
                                    setThemeMode, setSuperDark, setShowHiddenSystemFiles, clearCache
                                )
                                MainDestination.SYSTEM_FILES -> systemFilesState.SaveableStateProvider("system-files") {
                                    SystemFilesPage(
                                        padding = padding,
                                        rootPath = systemFilesRoot,
                                        directory = systemFilesDirectory,
                                        revision = systemFilesRevision,
                                        query = systemFilesQuery,
                                        showHiddenFiles = showHiddenSystemFiles,
                                        onDirectoryChange = {
                                            systemFilesQuery = ""
                                            systemFilesSearching = false
                                            systemFilesDirectory = it
                                        },
                                        onRootChange = { root ->
                                            systemFilesQuery = ""
                                            systemFilesSearching = false
                                            systemFilesRoot = root
                                            systemFilesDirectory = root
                                            systemFilesRevision++
                                        },
                                        onRefresh = { systemFilesRevision++ },
                                        onExit = {
                                            systemFilesQuery = ""
                                            systemFilesSearching = false
                                            showSystemFiles = false
                                            select(0)
                                        },
                                        openFile = openLocalFile,
                                        uploadLocal = uploadLocalFile,
                                        cloudDestination = active?.let { it.title + " / " + model.path.joinToString(" / ") { folder -> folder.name } },
                                        cloudAccount = active,
                                        loadCloudFolders = fileActions.loadFolders,
                                        transferLocalToCloud = transferLocalToCloud,
                                        handleBack = page == destination && !viewerExpanded && !systemFilesSearching &&
                                            !drawerState.isOpen && !showAccounts && !showTypes && !addingS3
                                    )
                                }
                                MainDestination.BROWSER -> BrowserTabsPage(
                                    pagerState = tabPagerState,
                                    state = BrowserUiState(selected, model, browserModels, active, accounts.busy, systemFilesRoot, tabSearchStates),
                                    actions = BrowserPageActions(reload, authorize,
                                        { tabSearchStates[it] = BrowserTabSearchState() }, openFolder, openSearchFolder, openFile),
                                    fileActions = fileActions, padding = padding,
                                    allowTabSwipes = !appViewerExpanded && !showSettings && !showSystemFiles && !accounts.busy &&
                                        !showFabMenu && !showAccounts && !showTypes && !addingS3 && !showCreateFolderDialog
                                )
                                MainDestination.PHOTOS -> UserRefreshBox(
                                    loading = model.loading,
                                    enabled = page == destination && !accounts.busy,
                                    refreshKey = active?.key to "photos",
                                    onRefresh = reload,
                                    modifier = Modifier.fillMaxSize().padding(padding)
                                ) {
                                    GooglePhotosPage(model, PaddingValues(0.dp), openFile)
                                }
                                MainDestination.TRASH -> UserRefreshBox(
                                    loading = model.loading,
                                    enabled = page == destination && !accounts.busy,
                                    refreshKey = active?.key to model.path,
                                    onRefresh = reload,
                                    modifier = Modifier.fillMaxSize().padding(padding)
                                ) {
                                    TrashPage(
                                        model = model,
                                        padding = PaddingValues(0.dp),
                                        restore = restoreTrashFiles,
                                        deletePermanently = deleteTrashFiles
                                    )
                                }
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
            // Capture only the narrow left-edge strip above the pager. Normal horizontal
            // drags elsewhere remain owned by HorizontalPager and therefore switch tabs.
            if (!viewerExpanded && drawerState.isClosed && !drawerState.isAnimationRunning &&
                !showFabMenu && !showAccounts && !showTypes && !addingS3 &&
                !showCreateFolderDialog
            ) {
                Box(
                    Modifier.align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(24.dp)
                        .navigationSwipes(enabled = true, allowTabSwipes = false) { gesture ->
                            if (gesture == NavigationSwipe.OPEN_DRAWER) {
                                drawerScope.launch { drawerState.open() }
                            }
                        }
                )
            }

            if (mediaViewer != null && playback != null) {
                // Independent media overlay with its own header; browser chrome is never reused.
                ExpandableMediaPlayer(
                    player = playback,
                    minimized = mediaViewer.minimized,
                    topPadding = playerTopPadding,
                    miniBounds = miniBounds,
                    onMinimize = minimizeViewer,
                    onExpand = expandViewer,
                    onClose = closeViewer,
                    queue = mediaViewer.swipeQueue,
                    index = mediaViewer.swipeIndex,
                    previewPaths = mediaViewer.previewPaths,
                    error = mediaViewer.error,
                    onSwipeTo = swipeViewer
                )
            }
        }
    }

    if (showCreateFolderDialog) CreateFolderDialog(
        name = newFolderName, onNameChange = { newFolderName = it },
        onDismiss = { showCreateFolderDialog = false },
        onCreate = {
            val name = newFolderName.trim()
            showCreateFolderDialog = false
            newFolderName = ""
            createFolder(name)
        }
    )

    if (showAccounts) AccountSwitcherDialog(
        accounts = accounts,
        active = active,
        onDismiss = { showAccounts = false },
        onSelect = { entry ->
            showAccounts = false
            showSystemFiles = false
            selectAccount(entry)
        },
        onRemove = { entry -> removing = entry },
        onAdd = {
            showAccounts = false
            showTypes = true
        }
    )
    if (showTypes) AccountTypeDialog(
        onDismiss = { showTypes = false },
        onGoogle = { showTypes = false; showAccounts = true; signIn() },
        onS3 = { showTypes = false; addingS3 = true },
        onService = { showTypes = false; showAccounts = true; importService() }
    )
    if (addingS3) S3AccountDialog(onDismiss = { addingS3 = false }, loading = accounts.busy,
        message = accounts.message, connect = connectS3)
    removing?.let { entry ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text(localized(R.string.sign_out_of_arg_1_2, entry.title)) },
            text = { Text(localized(R.string.the_account_will_be_removed_from_the_app_your_cloud_files_will_not_be_changed)) },
            confirmButton = { TextButton(onClick = { removeAccount(entry); removing = null }) { Text(localized(R.string.sign_out)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(localized(R.string.cancel)) } })
    }
}
