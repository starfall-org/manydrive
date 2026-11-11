package com.starfall.gsadrive.data

import android.net.Uri
import java.io.File
import java.io.InputStream

/** Provider operations shared by file actions and transfers. Call from an IO dispatcher. */
internal interface StorageAccess {
    suspend fun list(parent: String?): List<DriveFile>
    suspend fun createFolder(name: String, parent: String?): String
    suspend fun upload(name: String, mime: String, file: File, parent: String?)
    suspend fun upload(name: String, mime: String, input: InputStream, parent: String?, cacheDir: File)
    suspend fun downloadTo(id: String, target: File)
    suspend fun downloadSource(id: String): DownloadSource
    /** Drive moves to Trash; S3 deletes directly. */
    suspend fun remove(file: DriveFile)
}

internal class DriveStorageAccess(private val token: String) : StorageAccess {
    override suspend fun list(parent: String?) = DriveApi.listFiles(token, parentId = parent)
    override suspend fun createFolder(name: String, parent: String?) = DriveApi.createFolder(token, name, parent)
    override suspend fun upload(name: String, mime: String, file: File, parent: String?) {
        file.inputStream().use { DriveApi.upload(token, name, mime, it, parent) }
    }
    override suspend fun upload(name: String, mime: String, input: InputStream, parent: String?, cacheDir: File) =
        DriveApi.upload(token, name, mime, input, parent)
    override suspend fun downloadTo(id: String, target: File) = DriveApi.downloadTo(token, id, target)
    override suspend fun downloadSource(id: String) = DownloadSource(
        "https://www.googleapis.com/drive/v3/files/${Uri.encode(id)}?alt=media&supportsAllDrives=true",
        mapOf("Authorization" to "Bearer $token")
    )
    override suspend fun remove(file: DriveFile) = DriveApi.moveToTrash(token, file.id)
}

internal class S3StorageAccess(private val config: S3Config) : StorageAccess {
    override suspend fun list(parent: String?) = S3Api.list(config, parent.orEmpty())
    override suspend fun createFolder(name: String, parent: String?): String {
        val key = parent.orEmpty() + name + "/"
        S3Api.createFolder(config, key)
        return key
    }
    override suspend fun upload(name: String, mime: String, file: File, parent: String?) =
        S3Api.upload(config, parent.orEmpty() + name, mime, file)
    override suspend fun upload(name: String, mime: String, input: InputStream, parent: String?, cacheDir: File) {
        val temporary = File.createTempFile("upload-", ".tmp", cacheDir)
        try {
            temporary.outputStream().use { input.copyTo(it, 64 * 1024) }
            upload(name, mime, temporary, parent)
        } finally {
            temporary.delete()
        }
    }
    override suspend fun downloadTo(id: String, target: File) = S3Api.downloadTo(config, id, target)
    override suspend fun downloadSource(id: String) = S3Api.downloadSource(config, id)
    override suspend fun remove(file: DriveFile) = S3Api.delete(config, file)
}
