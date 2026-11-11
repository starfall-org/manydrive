package com.starfall.gsadrive.app.platform

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.starfall.gsadrive.shared.ui.theme.ThemeMode

@Composable
internal fun AppSystemBars(viewerBlack: Boolean, themeMode: ThemeMode) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val normalDarkBars = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        val activity = view.context as? android.app.Activity ?: return@SideEffect
        activity.window.statusBarColor = if (viewerBlack) android.graphics.Color.BLACK else android.graphics.Color.TRANSPARENT
        activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
        }
        androidx.core.view.WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !viewerBlack && !normalDarkBars
            isAppearanceLightNavigationBars = !viewerBlack && !normalDarkBars
        }
    }
}
