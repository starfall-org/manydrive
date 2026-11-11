package com.starfall.gsadrive.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

// Background loads must not activate the floating pull-to-refresh indicator.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserRefreshBox(
    loading: Boolean,
    enabled: Boolean,
    refreshKey: Any?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var userRefreshing by remember(refreshKey) { mutableStateOf(false) }
    LaunchedEffect(loading, userRefreshing) {
        if (!loading) userRefreshing = false
    }
    PullToRefreshBox(
        isRefreshing = userRefreshing && loading,
        onRefresh = {
            if (enabled && !loading) {
                userRefreshing = true
                onRefresh()
            }
        },
        modifier = modifier,
        content = content
    )
}
