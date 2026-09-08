package com.starfall.gsadrive.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.ui.theme.ThemeMode

@Composable
fun SettingsPage(padding: PaddingValues, mode: ThemeMode, superDark: Boolean,
    setMode: (ThemeMode) -> Unit, setSuperDark: (Boolean) -> Unit, clearCache: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Cài đặt", style = MaterialTheme.typography.headlineSmall)
        Text("Giao diện", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().selectable(mode == option, role = Role.RadioButton,
                    onClick = { setMode(option) }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == option, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(option.label)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Super Dark Mode", style = MaterialTheme.typography.titleMedium)
                Text("Dùng nền đen thuần khi giao diện tối đang bật.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = superDark, onCheckedChange = setSuperDark)
        }
        HorizontalDivider()
        Text("Bộ nhớ đệm", style = MaterialTheme.typography.titleMedium)
        Text("Lưu danh sách tệp để hiển thị nhanh và xem lại khi mất mạng. Kéo xuống đầu danh sách để cập nhật.")
        OutlinedButton(onClick = clearCache) { Text("Xóa cache danh sách tệp") }
    }
}
