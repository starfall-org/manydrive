package com.starfall.gsadrive.feature.viewer

import com.starfall.gsadrive.R

import android.content.Context
import com.starfall.gsadrive.data.DriveApi
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.PhotosApi
import com.starfall.gsadrive.data.S3Api
import com.starfall.gsadrive.data.S3Config
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.feature.accounts.AccountType
import com.starfall.gsadrive.feature.viewer.playback.pruneVideoFileCache
import com.starfall.gsadrive.shared.i18n.localized
import java.io.File
import java.security.MessageDigest

/** Preview IO and on-disk cache policy; viewer state remains in ViewerCoordinator. */
internal class PreviewFiles(
    private val context: Context,
    private val accessToken: () -> String?,
    private val photosTokenProvider: (String) -> (() -> String)?,
    private val s3Config: (AccountEntry) -> S3Config?
) {
    suspend fun downloadPreview(account: AccountEntry, file: DriveFile, target: File) {
        val temporary = File(target.path + ".tmp")
        temporary.parentFile?.mkdirs()
        temporary.delete()
        if (file.photosMediaId != null) {
            com.starfall.gsadrive.data.PhotosApi.downloadImage(
                photosTokenProvider(account.key)?.invoke() ?: error(localized(R.string.google_photos_authorization_is_required)), file.photosMediaId, temporary)
        } else when (account.type) {
            AccountType.S3 -> S3Api.downloadTo(
                s3Config(account) ?: error(localized(R.string.s3_configuration_not_found)), file.id, temporary)
            AccountType.GOOGLE, AccountType.SERVICE -> DriveApi.downloadTo(
                accessToken() ?: error(localized(R.string.access_permissions_need_to_be_granted_before_opening_the_file)), file.id, temporary)
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
    }

    fun cachedImagePaths(accountKey: String, queue: List<DriveFile>): Map<String, String> =
        queue.asSequence().filter { it.mimeType.startsWith("image/") }.mapNotNull { item ->
            previewCacheFile(accountKey, item).takeIf { it.isFile && it.length() > 0L }
                ?.let { item.id to it.path }
        }.toMap()

    fun previewCacheFile(account: String, file: DriveFile): File {
        val seed = "$account|${file.id}|${file.modifiedTime.orEmpty()}|${file.size ?: -1L}"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray()).joinToString("") { "%02x".format(it) }
        val extension = file.name.substringAfterLast('.', "").lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }?.let { ".$it" }.orEmpty()
        val directory = if (file.mimeType.startsWith("video/")) {
            File(context.filesDir, "video-file-cache")
        } else {
            File(context.cacheDir, "viewer")
        }
        return File(directory, hash + extension)
    }

    fun prunePreviewCache() {
        val directory = File(context.cacheDir, "viewer")
        var total = 0L
        directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.sortedByDescending { it.lastModified() }?.forEach { file ->
                total += file.length()
                if (total > MAX_PREVIEW_CACHE_BYTES) file.delete()
            }
        pruneVideoFileCache(File(context.filesDir, "video-file-cache"))
    }
}
