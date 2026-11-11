package com.starfall.gsadrive.feature.browser

import com.starfall.gsadrive.R

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.feature.accounts.AccountType
import com.starfall.gsadrive.feature.fileactions.FileActionCallbacks
import com.starfall.gsadrive.feature.fileactions.FileActionsSheet
import com.starfall.gsadrive.feature.fileactions.MultiFileActionsSheet
import com.starfall.gsadrive.feature.viewer.isSwipePreview
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.model.Model
import com.starfall.gsadrive.shared.ui.CopyableError
import com.starfall.gsadrive.shared.ui.FileGridCard
import com.starfall.gsadrive.shared.ui.FileListRow
import com.starfall.gsadrive.shared.ui.FolderZoomTransition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private enum class BrowserSort(val labelRes: Int) {
    NAME(R.string.name), MODIFIED(R.string.date_modified), SHARED(R.string.date_shared)
}

@Composable
internal fun FileBrowserPage(
    model: Model,
    padding: PaddingValues,
    account: AccountEntry?,
    shared: Boolean,
    query: String,
    searchResults: List<DriveFile>? = null,
    searchLoading: Boolean = false,
    searchError: String? = null,
    authorize: () -> Unit,
    openFolder: (DriveFile) -> Unit,
    openFile: (DriveFile, List<DriveFile>) -> Unit,
    actions: FileActionCallbacks = FileActionCallbacks(),
    browserKey: String = account?.key ?: "empty",
    showEmptyMessage: Boolean = account != null,
    localMenu: ((DriveFile) -> Unit)? = null,
    folderTransitionKey: String? = null,
    folderTransitionDepth: Int = 0,
    toolbarAction: (@Composable () -> Unit)? = null
) {
    val scopeKey = "${browserKey}:${if (shared) 1 else 0}:${model.path.lastOrNull()?.id.orEmpty()}"
    val defaultSort = when {
        shared -> BrowserSort.SHARED
        model.path.isNotEmpty() -> BrowserSort.MODIFIED
        else -> BrowserSort.NAME
    }
    val context = LocalContext.current
    val sortPreferences = remember(context) { context.getSharedPreferences("browser_sort", Context.MODE_PRIVATE) }
    val sortPreferenceKey = "sort:$scopeKey"
    val ascendingPreferenceKey = "ascending:$scopeKey"
    var sortName by remember(scopeKey) {
        mutableStateOf(sortPreferences.getString(sortPreferenceKey, defaultSort.name) ?: defaultSort.name)
    }
    var ascending by remember(scopeKey) {
        mutableStateOf(
            if (sortPreferences.contains(ascendingPreferenceKey))
                sortPreferences.getBoolean(ascendingPreferenceKey, defaultSort == BrowserSort.NAME)
            else defaultSort == BrowserSort.NAME
        )
    }
    var grid by rememberSaveable(browserKey) { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var actionFile by remember { mutableStateOf<DriveFile?>(null) }
    var selectedIds by remember(scopeKey) { mutableStateOf<List<String>>(emptyList()) }
    var multiActionFiles by remember { mutableStateOf<List<DriveFile>?>(null) }
    val sort = runCatching { BrowserSort.valueOf(sortName) }.getOrDefault(defaultSort)
    val globalSearch = query.isNotBlank() && searchResults != null
    val visible = remember(model.files, searchResults, query, sort, ascending) {
        val source = searchResults ?: model.files
        val filtered = if (query.isBlank() || searchResults != null) source
            else source.filter { it.name.contains(query, ignoreCase = true) }
        val sorted = when (sort) {
            BrowserSort.NAME -> filtered.sortedWith(compareByDescending<DriveFile> { it.isFolder }.thenBy { it.name.lowercase() })
            BrowserSort.MODIFIED -> filtered.sortedBy { it.modifiedTime.orEmpty() }
            BrowserSort.SHARED -> filtered.sortedBy { it.sharedWithMeTime.orEmpty() }
        }
        if (ascending) sorted else sorted.reversed()
    }
    val selectedSet = selectedIds.toSet()
    val selectedFiles = visible.filter { it.id in selectedSet }
    val selectionMode = selectedIds.isNotEmpty()

    fun toggleSelection(file: DriveFile) {
        selectedIds = if (file.id in selectedSet) selectedIds.filterNot { it == file.id }
            else selectedIds + file.id
    }
    fun enterSelection(file: DriveFile) {
        if (account == null) return
        if (file.id !in selectedSet) selectedIds = selectedIds + file.id
    }
    fun showMenu(file: DriveFile) {
        if (localMenu != null) { localMenu(file); return }
        if (account == null) return
        if (file.id in selectedSet) multiActionFiles = selectedFiles
        else actionFile = file
    }

    BackHandler(enabled = selectionMode) {
        multiActionFiles = null
        selectedIds = emptyList()
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    IconButton(onClick = { selectedIds = emptyList() }) {
                        Icon(Icons.Outlined.Close, localized(R.string.clear_selection))
                    }
                    Text(
                        localized(R.string.arg_1_selected, selectedIds.size),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        enabled = selectedSet.size < visible.size,
                        onClick = { selectedIds = visible.map { it.id } }
                    ) {
                        Icon(Icons.Outlined.SelectAll, localized(R.string.select_all))
                    }
                } else {
                    Text(
                        localized(sort.labelRes),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp, end = 2.dp)
                    )
                    Box {
                        val directionDescription = when (sort) {
                            BrowserSort.NAME -> if (ascending) localized(R.string.a_to_z) else localized(R.string.z_to_a)
                            BrowserSort.MODIFIED, BrowserSort.SHARED ->
                                if (ascending) localized(R.string.oldest_first) else localized(R.string.newest_first)
                        }
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(
                                if (ascending) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                                localized(R.string.sort_arg_1, directionDescription)
                            )
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            Text(
                                localized(R.string.sort_by),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                            BrowserSort.entries.filter { it != BrowserSort.SHARED || shared }.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(localized(option.labelRes)) },
                                    leadingIcon = {
                                        if (sort == option) Icon(Icons.Outlined.Check, null)
                                        else Spacer(Modifier.size(24.dp))
                                    },
                                    onClick = {
                                        sortName = option.name
                                        sortPreferences.edit().putString(sortPreferenceKey, option.name).apply()
                                        sortMenu = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            val directionOptions = if (sort == BrowserSort.NAME) {
                                listOf(false to localized(R.string.z_to_a), true to localized(R.string.a_to_z))
                            } else {
                                listOf(false to localized(R.string.newest_first), true to localized(R.string.oldest_first))
                            }
                            directionOptions.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = {
                                        if (ascending == value) Icon(Icons.Outlined.Check, null)
                                        else Spacer(Modifier.size(24.dp))
                                    },
                                    onClick = {
                                        ascending = value
                                        sortPreferences.edit().putBoolean(ascendingPreferenceKey, value).apply()
                                        sortMenu = false
                                    }
                                )
                            }
                        }
                    }
                    toolbarAction?.invoke()
                }
                if (!selectionMode) Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row {
                        IconButton(onClick = { grid = false }, colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (!grid) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)) {
                            Icon(Icons.Outlined.ViewList, localized(R.string.list))
                        }
                        IconButton(onClick = { grid = true }, colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (grid) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)) {
                            Icon(Icons.Outlined.GridView, localized(R.string.grid))
                        }
                    }
                }
            }
        }

        model.message?.let {
            CopyableError(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        searchError?.let {
            CopyableError(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        if (searchLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
        }
        if (account?.type == AccountType.GOOGLE && model.token == null && model.files.isEmpty()) {
            FilledTonalButton(onClick = authorize, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(localized(R.string.allow_drive_access))
            }
        }

        val fileListContent: @Composable () -> Unit = {
            if (showEmptyMessage && !model.loading && !searchLoading && model.message == null && searchError == null && visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (query.isBlank()) localized(R.string.there_are_no_files_in_this_location) else localized(R.string.no_matching_files_found),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (grid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { file ->
                        FileGridCard(
                            file,
                            shared && !globalSearch,
                            accessToken = model.token,
                            thumbnailCacheNamespace = browserKey,
                            onOpen = openFolder,
                            onPreview = { selected -> openFile(selected, visible.filter(::isSwipePreview)) },
                            selectionMode = selectionMode,
                            selected = file.id in selectedSet,
                            onToggleSelection = ::toggleSelection,
                            onLongSelect = ::enterSelection,
                            onMenu = if (account != null || localMenu != null) ::showMenu else null
                        )
                    }
                }
            } else if (shared && !globalSearch) {
                val sections = remember(visible) { visible.groupBy(::sharedSection) }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sections.forEach { (section, files) ->
                        item("section:$section") {
                            Text(section, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
                        }
                        items(files, key = { it.id }) { file ->
                            FileListRow(
                                file,
                                shared = true,
                                onOpen = openFolder,
                                onPreview = { selected -> openFile(selected, visible.filter(::isSwipePreview)) },
                                selectionMode = selectionMode,
                                selected = file.id in selectedSet,
                                onToggleSelection = ::toggleSelection,
                                onLongSelect = ::enterSelection,
                                onMenu = if (account != null || localMenu != null) ::showMenu else null
                            )
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(visible, key = { it.id }) { file ->
                        FileListRow(
                            file,
                            shared = false,
                            onOpen = openFolder,
                            onPreview = { selected -> openFile(selected, visible.filter(::isSwipePreview)) },
                            selectionMode = selectionMode,
                            selected = file.id in selectedSet,
                            onToggleSelection = ::toggleSelection,
                            onLongSelect = ::enterSelection,
                            onMenu = if (account != null || localMenu != null) ::showMenu else null
                        )
                    }
                }
            }
        }
        if (folderTransitionKey != null) {
            FolderZoomTransition(
                key = folderTransitionKey,
                depth = folderTransitionDepth,
                modifier = Modifier.fillMaxSize()
            ) {
                fileListContent()
            }
        } else {
            fileListContent()
        }
    }
    actionFile?.takeIf { account != null }?.let { selected ->
        FileActionsSheet(file = selected, account = requireNotNull(account), actions = actions, onDismiss = { actionFile = null })
    }
    multiActionFiles?.takeIf { it.isNotEmpty() && account != null }?.let { selected ->
        MultiFileActionsSheet(
            files = selected,
            account = requireNotNull(account),
            actions = actions,
            onDismiss = { multiActionFiles = null },
            onActionDone = {
                multiActionFiles = null
                selectedIds = emptyList()
            }
        )
    }
}

private fun sharedSection(value: DriveFile): String {
    val raw = value.sharedWithMeTime ?: value.modifiedTime ?: return localized(R.string.older)
    val parsed = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(raw)
    }.getOrNull() ?: return localized(R.string.older)
    val now = Calendar.getInstance()
    val date = Calendar.getInstance().apply { time = parsed }
    if (now.get(Calendar.YEAR) == date.get(Calendar.YEAR) && now.get(Calendar.MONTH) == date.get(Calendar.MONTH)) return localized(R.string.this_month)
    val previousMonth = (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
    if (previousMonth.get(Calendar.YEAR) == date.get(Calendar.YEAR) && previousMonth.get(Calendar.MONTH) == date.get(Calendar.MONTH)) return localized(R.string.last_month)
    if (now.get(Calendar.YEAR) == date.get(Calendar.YEAR)) return localized(R.string.earlier_this_year)
    return localized(R.string.older)
}
