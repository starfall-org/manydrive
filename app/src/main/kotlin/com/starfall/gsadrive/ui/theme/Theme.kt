package com.starfall.gsadrive.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val label: String) { SYSTEM("System"), DARK("Dark"), LIGHT("Light") }

private val LightColors = lightColorScheme(primary = Color(0xFF1565C0), secondary = Color(0xFF00695C))
private val DarkColors = darkColorScheme(primary = Color(0xFF90CAF9), secondary = Color(0xFF80CBC4))
private val BlackColors = DarkColors.copy(background = Color.Black, surface = Color.Black,
    surfaceDim = Color.Black, surfaceBright = Color(0xFF1A1A1A),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black, surfaceContainerHigh = Color(0xFF101010),
    surfaceContainerHighest = Color(0xFF1A1A1A))

@Composable
fun ManyDriveTheme(mode: ThemeMode = ThemeMode.SYSTEM, superDark: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(colorScheme = if (!dark) LightColors else if (superDark) BlackColors else DarkColors, content = content)
}
