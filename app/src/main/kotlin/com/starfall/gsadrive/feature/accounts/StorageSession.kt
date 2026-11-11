package com.starfall.gsadrive.feature.accounts

import com.starfall.gsadrive.data.S3Config
import com.starfall.gsadrive.data.ServiceAccountCredentials

/** Captured when a file operation starts; completion must keep using this account. */
internal data class StorageSession(
    val account: AccountEntry,
    val token: String?,
    val s3: S3Config?,
    val service: ServiceAccountCredentials?
)
