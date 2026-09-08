package com.starfall.gsadrive.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(primary = Color(0xFF1565C0), secondary = Color(0xFF00695C))
private val DarkColors = darkColorScheme(primary = Color(0xFF90CAF9), secondary = Color(0xFF80CBC4))

@Composable
fun ManyDriveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
