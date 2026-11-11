package com.starfall.gsadrive.app

import com.starfall.gsadrive.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.feature.accounts.AccountAvatar
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.feature.accounts.isTabEnabled
import com.starfall.gsadrive.shared.i18n.localized

@Composable
internal fun DriveNavigationDrawer(
    account: AccountEntry?,
    trashSelected: Boolean,
    settingsSelected: Boolean,
    onAccounts: () -> Unit,
    onTrash: () -> Unit,
    onSettings: () -> Unit,
    systemFilesSelected: Boolean = false,
    onSystemFiles: () -> Unit = {},
    photosSelected: Boolean = false,
    onPhotos: () -> Unit = {}
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val drawerWidth = (screenWidth - 56.dp).coerceAtLeast(240.dp).coerceAtMost(360.dp)
    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight().width(drawerWidth),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "ManyDrive",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            DriveDrawerItem(
                icon = Icons.Outlined.AccountCircle,
                label = localized(R.string.account),
                onClick = onAccounts
            )
            DriveDrawerItem(
                icon = Icons.Outlined.Storage,
                label = localized(R.string.system_files),
                selected = systemFilesSelected,
                onClick = onSystemFiles
            )
            DriveDrawerItem(
                icon = Icons.Outlined.Image,
                label = "Google Photos",
                selected = photosSelected,
                enabled = isTabEnabled(account?.type, 4),
                onClick = onPhotos
            )
            DriveDrawerItem(
                icon = Icons.Outlined.Delete,
                label = localized(R.string.trash),
                selected = trashSelected,
                enabled = isTabEnabled(account?.type, 3),
                onClick = onTrash
            )
            DriveDrawerItem(
                icon = Icons.Outlined.Settings,
                label = localized(R.string.settings),
                selected = settingsSelected,
                onClick = onSettings
            )

            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (account != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccountAvatar(account, onClick = onAccounts, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(account.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(account.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun DriveDrawerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        color = background,
        shape = RoundedCornerShape(28.dp),
        enabled = enabled,
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(26.dp), tint = contentColor)
            Spacer(Modifier.width(24.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = contentColor)
        }
    }
}
