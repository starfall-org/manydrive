package com.starfall.gsadrive.feature.browser

import com.starfall.gsadrive.data.DriveFile

internal data class BrowserRefreshTarget(
        val accountKey: String,
        val tab: Int,
        val path: List<DriveFile>,
        val requestId: Int
    )
