package com.starfall.gsadrive.feature.fileactions

import com.starfall.gsadrive.R

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.shared.i18n.localized

@Composable
internal fun InfoPanel(file: DriveFile, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, localized(R.string.back)) }
        Text(localized(R.string.details), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        InfoLine(localized(R.string.name), file.name)
        InfoLine(localized(R.string.type), if (file.isFolder) localized(R.string.folder) else file.mimeType.ifBlank { localized(R.string.unknown) })
        file.size?.let { InfoLine(localized(R.string.size), formatFileSize(it)) }
        file.modifiedTime?.let { InfoLine(localized(R.string.modified), it.replace('T', ' ').substringBefore('.')) }
        InfoLine("ID", file.id)
        file.webViewUrl?.let { InfoLine(localized(R.string.link), it) }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return "%.1f %s".format(value, units[unit])
}
