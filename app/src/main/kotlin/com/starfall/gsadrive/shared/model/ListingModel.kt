package com.starfall.gsadrive.shared.model

import com.starfall.gsadrive.data.DriveFile

internal data class Model(
    val user: String? = null, val token: String? = null,
    val files: List<DriveFile> = emptyList(),
    val loading: Boolean = false, val message: String? = null,
    val path: List<DriveFile> = emptyList(),
    val fromCache: Boolean = false
)
