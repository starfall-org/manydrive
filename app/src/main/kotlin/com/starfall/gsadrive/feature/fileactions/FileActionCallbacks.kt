package com.starfall.gsadrive.feature.fileactions

import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.DrivePermission
import com.starfall.gsadrive.feature.photos.PhotosFolderUploadMode

internal data class FileActionCallbacks(
    val download: ((DriveFile) -> Unit)? = null,
    val uploadToPhotos: ((DriveFile, PhotosFolderUploadMode) -> Unit)? = null,
    val uploadManyToPhotos: ((List<DriveFile>, PhotosFolderUploadMode) -> Unit)? = null,
    val share: (DriveFile, String, String, (Result<Unit>) -> Unit) -> Unit = { _, _, _, done -> done(Result.failure(UnsupportedOperationException())) },
    val rename: (DriveFile, String, (Result<Unit>) -> Unit) -> Unit = { _, _, done -> done(Result.failure(UnsupportedOperationException())) },
    val loadPermissions: (DriveFile, (Result<List<com.starfall.gsadrive.data.DrivePermission>>) -> Unit) -> Unit = { _, done -> done(Result.failure(UnsupportedOperationException())) },
    val removePermission: (DriveFile, com.starfall.gsadrive.data.DrivePermission, (Result<Unit>) -> Unit) -> Unit = { _, _, done -> done(Result.failure(UnsupportedOperationException())) },
    val loadFolders: (String?, (Result<List<DriveFile>>) -> Unit) -> Unit = { _, done -> done(Result.failure(UnsupportedOperationException())) },
    val copy: ((DriveFile, String, (Result<Unit>) -> Unit) -> Unit)? = null,
    val move: (DriveFile, String, (Result<Unit>) -> Unit) -> Unit = { _, _, done -> done(Result.failure(UnsupportedOperationException())) },
    val localRootPath: String? = null,
    val transferToLocal: ((DriveFile, String, String, Boolean, (Result<Unit>) -> Unit) -> Unit)? = null,
    val moveMany: (List<DriveFile>, String, (Result<Unit>) -> Unit) -> Unit = { _, _, done -> done(Result.failure(UnsupportedOperationException())) },
    val trash: ((DriveFile) -> Unit)? = null,
    val trashMany: ((List<DriveFile>) -> Unit)? = null
)
