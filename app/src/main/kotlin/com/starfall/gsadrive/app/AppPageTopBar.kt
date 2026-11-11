package com.starfall.gsadrive.app

import com.starfall.gsadrive.R

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.starfall.gsadrive.feature.accounts.AccountAvatar
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.feature.viewer.ViewerState
import com.starfall.gsadrive.feature.viewer.isMediaPreview
import com.starfall.gsadrive.shared.i18n.localized

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppPageTopBar(
    viewer: ViewerState?, expanded: Boolean, selected: Int, black: Boolean,
    account: AccountEntry?, showAccount: Boolean,
    onViewerBack: () -> Unit, onBrowser: () -> Unit, onMenu: () -> Unit, onAccounts: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Text(when {
                expanded -> viewer?.file?.name.orEmpty()
                selected == 3 -> localized(R.string.trash)
                selected == 4 -> "Google Photos"
                else -> "ManyDrive"
            }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        navigationIcon = {
            when {
                expanded -> IconButton(onClick = onViewerBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack,
                        if (viewer?.let { isMediaPreview(it.file) } == true) localized(R.string.minimize_player) else localized(R.string.close_viewer))
                }
                selected in setOf(3, 4) -> IconButton(onClick = onBrowser) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, localized(R.string.back))
                }
                else -> IconButton(onClick = onMenu) {
                    Icon(Icons.Outlined.Menu, localized(R.string.open_menu))
                }
            }
        },
        actions = {
            if (showAccount && account != null)
                AccountAvatar(account, onAccounts)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (black) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.surface,
            titleContentColor = if (black) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = if (black) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = if (black) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
        )
    )
}
