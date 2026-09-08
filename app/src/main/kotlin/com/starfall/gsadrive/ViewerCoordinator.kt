package com.starfall.gsadrive

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.starfall.gsadrive.data.DriveApi
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.FileListCache
import com.starfall.gsadrive.data.S3Api
import com.starfall.gsadrive.data.S3Config
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns viewer state and file-preview/media-playback orchestration.
 * MainActivity only supplies platform/account state and a MediaController connection.
 */
internal class ViewerCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val listCache: FileListCache,
    private val activeAccount: () -> AccountEntry?,
    private val accessToken: () -> String?,
    private val s3Config: (AccountEntry) -> S3Config?,
    private val currentFiles: () -> List<DriveFile>,
    private val player: () -> Player?,
    private val awaitPlayer: suspend () -> Player,
    private val requestNotificationPermission: () -> Unit
) {
    var state by mutableStateOf<ViewerState?>(null)
        private set

    private var generation = 0

    fun syncToMediaItem(mediaItem: MediaItem) {
        val source = PlaybackSourceRegistry.get(mediaItem.mediaId) ?: return
        val ordered = PlaybackSourceRegistry.all()
        val index = ordered.indexOfFirst { it.mediaId == mediaItem.mediaId }
        val previous = state
        state = ViewerState(
            file = source.file,
            localPath = source.cacheFile.path,
            loading = false,
            minimized = previous?.minimized ?: true,
            mediaQueue = ordered.map { it.file },
            mediaIndex = index
        )
    }

    fun open(file: DriveFile, minimized: Boolean = false, mediaQueue: List<DriveFile>? = null) {
        if (file.isFolder) return
        if (!isPreviewable(file)) {
            Toast.makeText(context, "Chưa hỗ trợ xem loại tệp ${file.mimeType.ifBlank { "này" }}", Toast.LENGTH_SHORT).show()
            return
        }
        val account = activeAccount() ?: return
        if (isMediaPreview(file)) requestNotificationPermission()
        val request = ++generation

        if (isMediaPreview(file)) {
            openMedia(request, account, file, minimized, mediaQueue)
            return
        }

        player()?.stop()
        player()?.clearMediaItems()
        PlaybackSourceRegistry.clear()
        state = ViewerState(file = file, loading = true)
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val target = previewCacheFile(account.key, file)
                    if (!target.isFile || target.length() == 0L) {
                        downloadPreview(account, file, target)
                        prunePreviewCache()
                    }
                    val text = if (isTextPreview(file)) {
                        require(target.length() <= MAX_TEXT_PREVIEW_BYTES) {
                            "Tệp text quá lớn để sửa trực tiếp (giới hạn 4 MB)."
                        }
                        target.readText(Charsets.UTF_8)
                    } else null
                    target.path to text
                }
            }.onSuccess { (path, text) ->
                if (request == generation && state?.file?.id == file.id) {
                    state = state?.copy(localPath = path, text = text, loading = false, error = null)
                }
            }.onFailure {
                if (request == generation && state?.file?.id == file.id) {
                    state = state?.copy(loading = false, error = it.message ?: "Không thể mở tệp.")
                }
            }
        }
    }

    private fun openMedia(
        request: Int,
        account: AccountEntry,
        file: DriveFile,
        minimized: Boolean,
        mediaQueue: List<DriveFile>?
    ) {
        val queue = mediaQueue ?: currentFiles().filter(::isMediaPreview)
        val queueIndex = queue.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
        val config = if (account.type == AccountType.S3) s3Config(account) else null
        val token = if (account.type == AccountType.S3) null else accessToken()
        if (account.type != AccountType.S3 && token == null) {
            state = ViewerState(file = file, error = "Cần cấp quyền truy cập trước khi phát media.")
            return
        }
        if (account.type == AccountType.S3 && config == null) {
            state = ViewerState(file = file, error = "Không tìm thấy cấu hình S3.")
            return
        }

        val sources = queue.mapIndexed { index, item ->
            PlaybackSource(
                mediaId = "${account.key}:$index:${item.id}",
                file = item,
                accountType = account.type.name,
                accessToken = token,
                s3Config = config,
                cacheFile = previewCacheFile(account.key, item)
            )
        }
        PlaybackSourceRegistry.replace(sources)
        val selectedSource = sources.getOrNull(queueIndex) ?: return
        state = ViewerState(
            file = file,
            localPath = selectedSource.cacheFile.path,
            loading = false,
            minimized = minimized,
            mediaQueue = queue,
            mediaIndex = queueIndex
        )

        scope.launch {
            runCatching {
                val controller = player() ?: awaitPlayer()
                val items = sources.map { source ->
                    MediaItem.Builder()
                        .setMediaId(source.mediaId)
                        .setUri(Uri.Builder().scheme("manydrive").authority("media")
                            .appendQueryParameter("id", source.mediaId).build())
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(source.file.name).build())
                        .build()
                }
                controller to items
            }.onSuccess { (controller, items) ->
                if (request == generation && state?.file?.id == file.id) {
                    controller.setMediaItems(items, queueIndex, 0L)
                    controller.prepare()
                    controller.play()
                }
            }.onFailure {
                if (request == generation && state?.file?.id == file.id) {
                    state = state?.copy(error = it.message ?: "Không thể mở media.")
                }
            }
        }
    }

    fun close() {
        ++generation
        player()?.stop()
        player()?.clearMediaItems()
        PlaybackSourceRegistry.clear()
        state = null
    }

    fun minimize() {
        val current = state ?: return
        if (isMediaPreview(current.file)) state = current.copy(minimized = true) else close()
    }

    fun expand() {
        state = state?.copy(minimized = false)
    }

    fun updateText(text: String) {
        val current = state ?: return
        if (current.text != null && !current.saving) state = current.copy(text = text)
    }

    fun saveText() {
        val current = state ?: return
        val text = current.text ?: return
        if (current.saving) return
        val account = activeAccount() ?: return
        val content = text.toByteArray(Charsets.UTF_8)
        if (content.size > MAX_TEXT_PREVIEW_BYTES) {
            Toast.makeText(context, "Nội dung vượt giới hạn 4 MB.", Toast.LENGTH_SHORT).show()
            return
        }
        val request = generation
        state = current.copy(saving = true, error = null)
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    when (account.type) {
                        AccountType.S3 -> {
                            val config = s3Config(account) ?: error("Không tìm thấy cấu hình S3.")
                            val temporary = File.createTempFile("manydrive-text-", ".tmp", context.cacheDir)
                            try {
                                temporary.writeBytes(content)
                                S3Api.upload(config, current.file.id,
                                    current.file.mimeType.ifBlank { "text/plain" }, temporary)
                            } finally {
                                temporary.delete()
                            }
                        }
                        AccountType.GOOGLE, AccountType.SERVICE -> {
                            val token = accessToken() ?: error("Cần cấp quyền ghi trước khi lưu.")
                            DriveApi.updateContent(token, current.file.id,
                                current.file.mimeType.ifBlank { "text/plain; charset=UTF-8" }, content)
                        }
                    }
                    current.localPath?.let { File(it).writeBytes(content) }
                    listCache.clear(account.key)
                }
            }
            if (request == generation && state?.file?.id == current.file.id) {
                state = state?.copy(saving = false, error = result.exceptionOrNull()?.message)
                Toast.makeText(context,
                    if (result.isSuccess) "Đã lưu thay đổi." else "Không thể lưu thay đổi.",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun downloadPreview(account: AccountEntry, file: DriveFile, target: File) {
        val temporary = File(target.path + ".tmp")
        temporary.parentFile?.mkdirs()
        temporary.delete()
        when (account.type) {
            AccountType.S3 -> S3Api.downloadTo(
                s3Config(account) ?: error("Không tìm thấy cấu hình S3."), file.id, temporary)
            AccountType.GOOGLE, AccountType.SERVICE -> DriveApi.downloadTo(
                accessToken() ?: error("Cần cấp quyền truy cập trước khi mở tệp."), file.id, temporary)
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
    }

    private fun previewCacheFile(account: String, file: DriveFile): File {
        val seed = "$account|${file.id}|${file.modifiedTime.orEmpty()}|${file.size ?: -1L}"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray()).joinToString("") { "%02x".format(it) }
        val extension = file.name.substringAfterLast('.', "").lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }?.let { ".$it" }.orEmpty()
        return File(File(context.cacheDir, "viewer"), hash + extension)
    }

    private fun prunePreviewCache() {
        val directory = File(context.cacheDir, "viewer")
        var total = 0L
        directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.sortedByDescending { it.lastModified() }?.forEach { file ->
                total += file.length()
                if (total > MAX_PREVIEW_CACHE_BYTES) file.delete()
            }
    }
}
