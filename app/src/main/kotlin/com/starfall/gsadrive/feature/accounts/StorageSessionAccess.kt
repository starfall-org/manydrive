package com.starfall.gsadrive.feature.accounts

import com.starfall.gsadrive.R
import com.starfall.gsadrive.data.DriveStorageAccess
import com.starfall.gsadrive.data.S3StorageAccess
import com.starfall.gsadrive.data.ServiceAccountApi
import com.starfall.gsadrive.data.StorageAccess
import com.starfall.gsadrive.shared.i18n.localized

/** Resolve credentials from the captured session, never from the currently selected account. */
internal fun StorageSession.requireDriveToken(): String = when (account.type) {
    AccountType.GOOGLE -> requireNotNull(token) { localized(R.string.need_to_grant_drive_permission_first) }
    AccountType.SERVICE -> ServiceAccountApi.accessToken(requireNotNull(service)).value
    AccountType.S3 -> error("S3 does not use Drive credentials")
}

internal fun StorageSession.openStorage(): StorageAccess = when (account.type) {
    AccountType.S3 -> S3StorageAccess(requireNotNull(s3))
    AccountType.GOOGLE, AccountType.SERVICE -> DriveStorageAccess(requireDriveToken())
}
