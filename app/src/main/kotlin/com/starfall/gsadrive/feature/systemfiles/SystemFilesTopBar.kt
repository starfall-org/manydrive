package com.starfall.gsadrive.feature.systemfiles

import com.starfall.gsadrive.R

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.starfall.gsadrive.shared.ui.FolderSearchField
import com.starfall.gsadrive.shared.i18n.localized
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SystemFilesTopBar(
    root: String, directory: String, query: String, searching: Boolean,
    onQueryChange: (String) -> Unit, onBack: () -> Unit,
    onCloseSearch: () -> Unit, onSearch: () -> Unit, onRefresh: () -> Unit
) {
    TopAppBar(
        title = {
            if (searching) {
                FolderSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = localized(R.string.search_this_folder),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    if (directory == root) localized(R.string.system_files)
                    else File(directory).name.ifBlank { localized(R.string.system_files) },
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, localized(R.string.back))
            }
        },
        actions = {
            if (searching) {
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.Outlined.Close, localized(R.string.close_search))
                }
            } else {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Outlined.Search, localized(R.string.search))
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, localized(R.string.refresh))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}
