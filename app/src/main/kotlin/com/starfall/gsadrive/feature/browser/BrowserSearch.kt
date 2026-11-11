package com.starfall.gsadrive.feature.browser

import com.starfall.gsadrive.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.feature.accounts.AccountType
import com.starfall.gsadrive.shared.i18n.localized
import kotlinx.coroutines.delay

internal data class BrowserTabSearchState(
    val query: String = "",
    val folderSearching: Boolean = false,
    val results: List<DriveFile>? = null,
    val loading: Boolean = false,
    val error: String? = null
)

/** Debounced Drive search retains an independent snapshot for each browser tab. */
@Composable
internal fun rememberBrowserSearch(
    active: AccountEntry?, selected: Int,
    searchDrive: (String, (Result<List<DriveFile>>) -> Unit) -> Unit
): androidx.compose.runtime.snapshots.SnapshotStateMap<Int, BrowserTabSearchState> {
    val tabSearchStates = remember(active?.key) { mutableStateMapOf<Int, BrowserTabSearchState>() }
    fun tabSearchState(index: Int): BrowserTabSearchState = tabSearchStates[index] ?: BrowserTabSearchState()
    fun updateTabSearch(index: Int, update: (BrowserTabSearchState) -> BrowserTabSearchState) {
        tabSearchStates[index] = update(tabSearchState(index))
    }
    val latestAccountKey by rememberUpdatedState(active?.key)
    val searchQuery = tabSearchState(selected).query
    LaunchedEffect(searchQuery, active?.key, selected) {
        if (selected !in 0..1) return@LaunchedEffect
        val tabIndex = selected
        updateTabSearch(tabIndex) { it.copy(error = null) }
        val query = searchQuery.trim()
        if (query.isEmpty() || active == null || active.type == AccountType.S3) {
            updateTabSearch(tabIndex) { it.copy(results = null, loading = false) }
            return@LaunchedEffect
        }
        val accountKey = active.key
        updateTabSearch(tabIndex) { it.copy(results = emptyList(), loading = true) }
        delay(350)
        searchDrive(query) { result ->
            if (tabSearchState(tabIndex).query.trim() != query || latestAccountKey != accountKey) return@searchDrive
            result.onSuccess { files ->
                updateTabSearch(tabIndex) { it.copy(results = files, loading = false, error = null) }
            }.onFailure { failure ->
                updateTabSearch(tabIndex) {
                    it.copy(results = emptyList(), loading = false,
                        error = failure.message ?: localized(R.string.can_t_search_drive))
                }
            }
        }
    }
    return tabSearchStates
}
