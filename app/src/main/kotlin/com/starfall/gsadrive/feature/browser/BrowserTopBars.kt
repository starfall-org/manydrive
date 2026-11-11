package com.starfall.gsadrive.feature.browser

import com.starfall.gsadrive.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.feature.accounts.AccountAvatar
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.shared.i18n.localized

@Composable
internal fun DriveRootTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onMenu: () -> Unit,
    onAccounts: () -> Unit,
    account: AccountEntry?
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onMenu) { Icon(Icons.Outlined.Menu, localized(R.string.open_menu)) }
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(localized(R.string.search_drive)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(32.dp),
                    leadingIcon = if (query.isNotBlank()) ({ Icon(Icons.Outlined.Search, null) }) else null,
                    trailingIcon = if (query.isNotBlank()) ({
                        IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Outlined.Close, localized(R.string.clear_search)) }
                    }) else null,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
                AccountAvatar(account = account, onClick = onAccounts)
            }
        }
    }
}

@Composable
internal fun FolderBrowserTopBar(
    title: String,
    query: String,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchingChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAccounts: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = 64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, localized(R.string.back)) }
            if (searching) {
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(localized(R.string.search_drive)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
                IconButton(onClick = { onQueryChange(""); onSearchingChange(false) }) {
                    Icon(Icons.Outlined.Close, localized(R.string.close_search))
                }
            } else {
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { onSearchingChange(true) }) { Icon(Icons.Outlined.Search, localized(R.string.search)) }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, localized(R.string.options)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(localized(R.string.refresh)) }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                            onClick = { menuOpen = false; onRefresh() })
                        DropdownMenuItem(text = { Text(localized(R.string.account)) }, leadingIcon = { Icon(Icons.Outlined.AccountCircle, null) },
                            onClick = { menuOpen = false; onAccounts() })
                    }
                }
            }
        }
    }
}
