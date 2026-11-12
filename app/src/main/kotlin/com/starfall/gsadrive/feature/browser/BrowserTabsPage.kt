package com.starfall.gsadrive.feature.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.feature.accounts.isTabEnabled
import com.starfall.gsadrive.feature.fileactions.FileActionCallbacks
import com.starfall.gsadrive.shared.model.Model
import com.starfall.gsadrive.shared.ui.UserRefreshBox

internal data class BrowserUiState(
    val selected: Int,
    val model: Model,
    val snapshots: Map<Int, Model>,
    val account: AccountEntry?,
    val busy: Boolean,
    val localRootPath: String,
    val searchStates: Map<Int, BrowserTabSearchState>
)

internal data class BrowserPageActions(
    val reload: () -> Unit,
    val authorize: () -> Unit,
    val resetSearch: (Int) -> Unit,
    val openFolder: (DriveFile) -> Unit,
    val openSearchFolder: (DriveFile) -> Unit,
    val openFile: (DriveFile, List<DriveFile>) -> Unit
)

@Composable
internal fun BrowserTabsPage(
    pagerState: androidx.compose.foundation.pager.PagerState,
    state: BrowserUiState,
    actions: BrowserPageActions,
    fileActions: FileActionCallbacks,
    padding: PaddingValues,
    allowTabSwipes: Boolean
) {
    if (state.account == null) FileBrowserPage(
        model = Model(), padding = padding, account = null, shared = false,
        query = state.searchStates[state.selected]?.query.orEmpty(), authorize = {}, openFolder = {}, openFile = { _, _ -> }
    ) else HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().padding(padding),
        userScrollEnabled = allowTabSwipes && isTabEnabled(state.account.type, 1),
        beyondViewportPageCount = 1,
        key = { page -> "browser-tab-$page" }
    ) { page ->
        val pageModel = if (page == state.selected) state.model
            else state.snapshots[page] ?: Model(user = state.model.user, token = state.model.token)
        val pageSearch = state.searchStates[page] ?: BrowserTabSearchState()
        val initialized = page == state.selected || state.snapshots.containsKey(page)

        UserRefreshBox(
            loading = pageModel.loading,
            enabled = page == state.selected && !state.busy,
            refreshKey = state.account.key to pageModel.path,
            onRefresh = actions.reload,
            modifier = Modifier.fillMaxSize()
        ) {
            if (!initialized) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val globalSearch = pageSearch.results != null && pageSearch.query.isNotBlank()
                FileBrowserPage(
                    model = pageModel,
                    padding = PaddingValues(0.dp),
                    account = state.account,
                    shared = page == 1,
                    query = pageSearch.query,
                    searchResults = pageSearch.results,
                    searchLoading = pageSearch.loading,
                    searchError = pageSearch.error,
                    authorize = if (page == state.selected) actions.authorize else ({}),
                    openFolder = { file ->
                        if (page == state.selected) {
                            actions.resetSearch(page)
                            if (globalSearch) actions.openSearchFolder(file) else actions.openFolder(file)
                        }
                    },
                    openFile = { file, queue -> if (page == state.selected) actions.openFile(file, queue) },
                    actions = fileActions.copy(
                        localRootPath = state.localRootPath,
                        trash = if (page == 0) fileActions.trash else null,
                        trashMany = if (page == 0) fileActions.trashMany else null
                    )
                )
            }
        }
    }
}
