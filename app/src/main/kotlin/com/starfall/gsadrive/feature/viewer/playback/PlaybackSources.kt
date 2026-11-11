package com.starfall.gsadrive.feature.viewer.playback

import com.starfall.gsadrive.R

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.starfall.gsadrive.data.DriveApi
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.PhotosApi
import com.starfall.gsadrive.data.S3Api
import com.starfall.gsadrive.data.S3Config
import com.starfall.gsadrive.shared.i18n.localized
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking

/** Information needed to open a cloud media item lazily. */
data class PlaybackSource(
    val mediaId: String,
    val file: DriveFile,
    val accountType: String,
    val accessToken: String?,
    val s3Config: S3Config?,
    val cacheFile: File,
    val photosTokenProvider: (() -> String)? = null
) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(Uri.Builder().scheme("manydrive").authority("media").appendQueryParameter("id", mediaId).build())
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(file.name).apply {
                if (!file.thumbnailUrl.isNullOrBlank()) {
                    // Keep auth/source details inside the app. MediaSession resolves this URI with
                    // its BitmapLoader and publishes the decoded artwork to platform media controls.
                    setArtworkUri(
                        Uri.Builder().scheme("manydrive-artwork").authority("thumbnail")
                            .appendQueryParameter("id", mediaId).build()
                    )
                }
            }.build()
        )
        .build()
}

/**
 * In-process registry used by the playback service. Media items are added to the ExoPlayer playlist
 * immediately; S3 opens through a fresh presigned URL, while Drive uses the local viewer cache.
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
        val source = sources[mediaId] ?: throw IOException(localized(R.string.media_source_not_found_arg_1, mediaId))
        if (source.accountType == "LOCAL") return source.cacheFile
        if (source.accountType == "S3") throw IOException("S3 media must use presigned streaming")
        val isVideo = source.file.mimeType.startsWith("video/")
        if (isVideo && source.cacheFile.isFile && !isFreshVideoCacheFile(source.cacheFile)) source.cacheFile.delete()
        if (source.cacheFile.isFile && source.cacheFile.length() > 0L) {
            if (isVideo) touchVideoCacheFile(source.cacheFile)
            return source.cacheFile
        }

        val lock = locks.getOrPut(mediaId) { Any() }
        synchronized(lock) {
            if (isVideo && source.cacheFile.isFile && !isFreshVideoCacheFile(source.cacheFile)) source.cacheFile.delete()
            if (source.cacheFile.isFile && source.cacheFile.length() > 0L) {
                if (isVideo) touchVideoCacheFile(source.cacheFile)
                return source.cacheFile
            }
            val target = source.cacheFile
            val temporary = File(target.path + ".tmp")
            target.parentFile?.mkdirs()
            temporary.delete()
            try {
                when (source.accountType) {
                    "GOOGLE", "SERVICE" -> DriveApi.downloadTo(
                        source.accessToken ?: throw IOException(localized(R.string.missing_media_access)),
                        source.file.id,
                        temporary
                    )
                    else -> throw IOException(localized(R.string.the_account_type_does_not_support_media_playback))
                }
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                if (isVideo) {
                    touchVideoCacheFile(target)
                    target.parentFile?.let(::pruneVideoFileCache)
                }
                return target
            } catch (t: Throwable) {
                temporary.delete()
                if (t is IOException) throw t
                throw IOException(t.message ?: localized(R.string.unable_to_load_media), t)
            }
        }
    }
}

/** Resolve on every open/reopen so seeking never reuses an expired S3 URL. */
@OptIn(UnstableApi::class)
internal fun resolvePlaybackDataSpec(dataSpec: DataSpec): DataSpec {
    if (dataSpec.uri.scheme != "manydrive") return dataSpec
    val mediaId = dataSpec.uri.getQueryParameter("id")
        ?: throw IOException(localized(R.string.media_uri_is_not_valid))
    val source = PlaybackSourceRegistry.get(mediaId)
        ?: throw IOException(localized(R.string.media_source_not_found_arg_1, mediaId))
    if (source.accountType == "CONTENT") return dataSpec.withUri(Uri.parse(source.file.id))
    if (source.accountType == "PHOTOS") {
        try {
            val url = com.starfall.gsadrive.data.PhotosApi.mediaUrl(
                source.photosTokenProvider?.invoke() ?: source.accessToken ?: throw IOException(localized(R.string.google_photos_authorization_is_required)),
                requireNotNull(source.file.photosMediaId), true)
            return dataSpec.withUri(Uri.parse(url))
        } catch (error: Exception) {
            if (error is IOException) throw error
            throw IOException(error.message ?: localized(R.string.could_not_load_google_photos), error)
        }
    }
    if (source.accountType == "S3") {
        val remote = try {
            runBlocking {
                S3Api.downloadSource(
                    source.s3Config ?: throw IOException(localized(R.string.missing_s3_configuration_for_media)),
                    source.file.id
                )
            }
        } catch (error: Exception) {
            if (error is IOException) throw error
            throw IOException(localized(R.string.could_not_open_the_s3_media_stream), error)
        }
        // Preserve position and length: Media3 turns them into HTTP Range requests.
        return dataSpec.withUri(Uri.parse(remote.url)).withAdditionalHeaders(remote.headers)
    }
    return dataSpec.withUri(Uri.fromFile(PlaybackSourceRegistry.resolve(mediaId)))
}

/** A page starts paused and muted; the session activates this same player when selected. */
@OptIn(UnstableApi::class)
internal fun createMediaPagePlayer(context: android.content.Context): ExoPlayer {
    val resolving = ResolvingDataSource.Factory(DefaultDataSource.Factory(context), ::resolvePlaybackDataSpec)
    val dataSource = VideoStreamCache.dataSourceFactory(context, resolving)
    return ExoPlayer.Builder(context)
    .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
    .setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build(),
        true
    )
    .setHandleAudioBecomingNoisy(true)
    .setSeekBackIncrementMs(10_000L)
    .setSeekForwardIncrementMs(10_000L)
    .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder()
        .setBufferDurationsMs(1000, 5000, 250, 500).build())
    .build().apply { volume = 0f; playWhenReady = false }
}
