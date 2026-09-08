package com.starfall.gsadrive

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.starfall.gsadrive.data.DriveApi
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.S3Api
import com.starfall.gsadrive.data.S3Config
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking

/** Information needed to lazily materialize one media item into the viewer cache. */
data class PlaybackSource(
    val mediaId: String,
    val file: DriveFile,
    val accountType: String,
    val accessToken: String?,
    val s3Config: S3Config?,
    val cacheFile: File
)

/**
 * In-process registry used by the playback service. Media items are added to the ExoPlayer playlist
 * immediately, but the backing cloud object is only downloaded when ExoPlayer actually opens it.
 */
object PlaybackSourceRegistry {
    private val sources = ConcurrentHashMap<String, PlaybackSource>()
    private val locks = ConcurrentHashMap<String, Any>()
    private val orderedIds = CopyOnWriteArrayList<String>()

    fun replace(items: List<PlaybackSource>) {
        sources.clear()
        orderedIds.clear()
        items.forEach {
            sources[it.mediaId] = it
            orderedIds += it.mediaId
        }
    }

    fun clear() {
        sources.clear()
        locks.clear()
        orderedIds.clear()
    }

    fun get(mediaId: String): PlaybackSource? = sources[mediaId]

    fun all(): List<PlaybackSource> = orderedIds.mapNotNull(sources::get)

    @Throws(IOException::class)
    fun resolve(mediaId: String): File {
        val source = sources[mediaId] ?: throw IOException("Không tìm thấy nguồn media: $mediaId")
        if (source.cacheFile.isFile && source.cacheFile.length() > 0L) return source.cacheFile

        val lock = locks.getOrPut(mediaId) { Any() }
        synchronized(lock) {
            if (source.cacheFile.isFile && source.cacheFile.length() > 0L) return source.cacheFile
            val target = source.cacheFile
            val temporary = File(target.path + ".tmp")
            target.parentFile?.mkdirs()
            temporary.delete()
            try {
                when (source.accountType) {
                    "S3" -> runBlocking {
                        S3Api.downloadTo(
                            source.s3Config ?: throw IOException("Thiếu cấu hình S3 cho media."),
                            source.file.id,
                            temporary
                        )
                    }
                    "GOOGLE", "SERVICE" -> DriveApi.downloadTo(
                        source.accessToken ?: throw IOException("Thiếu quyền truy cập media."),
                        source.file.id,
                        temporary
                    )
                    else -> throw IOException("Loại tài khoản không hỗ trợ phát media.")
                }
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                return target
            } catch (t: Throwable) {
                temporary.delete()
                if (t is IOException) throw t
                throw IOException(t.message ?: "Không thể tải media.", t)
            }
        }
    }
}

/** DataSource that lazily downloads a ManyDrive media item, then exposes it as a seekable file. */
private class ManyDriveMediaDataSource : BaseDataSource(false) {
    private var opened = false
    private var currentUri: Uri? = null
    private var file: RandomAccessFile? = null
    private var bytesRemaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        currentUri = dataSpec.uri
        val mediaId = dataSpec.uri.getQueryParameter("id")
            ?: throw IOException("Media URI không hợp lệ.")
        val resolved = PlaybackSourceRegistry.resolve(mediaId)
        val handle = RandomAccessFile(resolved, "r")
        if (dataSpec.position > handle.length()) {
            handle.close()
            throw IOException("Vị trí đọc media vượt quá kích thước tệp.")
        }
        handle.seek(dataSpec.position)
        file = handle
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            handle.length() - dataSpec.position
        } else {
            minOf(dataSpec.length, handle.length() - dataSpec.position)
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val read = file?.read(buffer, offset, minOf(length.toLong(), bytesRemaining).toInt())
            ?: return C.RESULT_END_OF_INPUT
        if (read < 0) return C.RESULT_END_OF_INPUT
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        currentUri = null
        runCatching { file?.close() }
        file = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = ManyDriveMediaDataSource()
    }
}

class MediaPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()
        val dataSourceFactory = DefaultDataSource.Factory(this, ManyDriveMediaDataSource.Factory())
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()

        val launchIntent = Intent(this, MainActivity::class.java)
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing if playback is active. The default MediaSessionService behavior then keeps the
        // foreground service and notification alive even after the task is swiped away.
        if (!player.playWhenReady || player.playbackState == Player.STATE_ENDED || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        PlaybackSourceRegistry.clear()
        super.onDestroy()
    }
}
