package com.starfall.gsadrive.feature.transfers

import com.starfall.gsadrive.R

import android.content.Context
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.PhotosApi
import com.starfall.gsadrive.feature.accounts.openStorage
import com.starfall.gsadrive.feature.accounts.StorageSession
import com.starfall.gsadrive.feature.photos.PhotosFolderUploadMode
import com.starfall.gsadrive.shared.i18n.localized
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PhotosUploads(private val context: Context, private val scope: CoroutineScope) {
    fun upload(files: List<DriveFile>, photosToken: String, mode: PhotosFolderUploadMode, session: StorageSession) {
        val uploadNotifications = UploadNotifications(context)
        val total = if (files.any { it.isFolder }) null else files.count {
            it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/")
        }
        uploadNotifications.running(UploadNotifications.Kind.PHOTOS, completed = 0, failed = 0, total = total)
        scope.launch {
            var uploaded = 0
            var failed = 0
            var cancelled = false
            var fatal: Throwable? = null
            var albumsCreated = 0
            try {
                withContext(Dispatchers.IO) {
                    val storage = session.openStorage()
                    val visited = mutableSetOf<String>()
                    files.forEach { root ->
                        val album = if (mode == PhotosFolderUploadMode.ALBUM && root.isFolder) {
                            PhotosApi.createAlbum(photosToken, root.name).also { albumsCreated++ }
                        } else null
                        val queue = java.util.ArrayDeque<DriveFile>().apply { add(root) }
                        while (queue.isNotEmpty()) {
                            val item = queue.removeFirst()
                            if (!visited.add(item.id)) continue
                            if (item.isFolder) {
                                val children = storage.list(item.id)
                                queue.addAll(children)
                                continue
                            }
                            if (!item.mimeType.startsWith("image/") && !item.mimeType.startsWith("video/")) continue
                            uploadNotifications.running(
                                UploadNotifications.Kind.PHOTOS,
                                currentName = item.name,
                                completed = uploaded,
                                failed = failed,
                                total = total
                            )
                            val temporary = File.createTempFile("photos-upload-", ".tmp", context.cacheDir)
                            try {
                                storage.downloadTo(item.id, temporary)
                                PhotosApi.upload(photosToken, temporary, item.name, item.mimeType, album)
                                uploaded++
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                failed++
                            } finally {
                                temporary.delete()
                            }
                            uploadNotifications.running(
                                UploadNotifications.Kind.PHOTOS,
                                completed = uploaded,
                                failed = failed,
                                total = total
                            )
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw e
            } catch (e: Exception) {
                fatal = e
            } finally {
                val albumSuffix = if (albumsCreated > 0) localized(R.string.created_arg_1_albums, albumsCreated) else "."
                when {
                    cancelled -> uploadNotifications.finished(
                        UploadNotifications.Kind.PHOTOS,
                        localized(R.string.google_photos_upload_canceled_uploaded_arg_1_files_arg_2_failed, uploaded, failed),
                        success = false
                    )
                    fatal != null -> uploadNotifications.finished(
                        UploadNotifications.Kind.PHOTOS,
                        localized(R.string.could_not_complete_google_photos_upload_uploaded_arg_1_files_arg_2_failed, uploaded, failed),
                        success = false
                    )
                    else -> uploadNotifications.finished(
                        UploadNotifications.Kind.PHOTOS,
                        localized(R.string.uploaded_arg_1_files_to_google_photos, uploaded) +
                            (if (failed > 0) localized(R.string.arg_1_failed, failed) else "") + albumSuffix,
                        success = failed == 0
                    )
                }
            }
        }
    }

}
