package com.starfall.gsadrive.feature.viewer.playback

import android.content.Context
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.starfall.gsadrive.shared.cache.trimFileCache
import java.io.File

internal const val VIDEO_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L
internal const val VIDEO_CACHE_MAX_BYTES = 2L * 1024L * 1024L * 1024L

internal fun isFreshVideoCacheFile(file: File, now: Long = System.currentTimeMillis()): Boolean =
    file.isFile && file.length() > 0L && now - file.lastModified() <= VIDEO_CACHE_TTL_MS

internal fun touchVideoCacheFile(file: File) {
    if (file.isFile) file.setLastModified(System.currentTimeMillis())
}


internal fun pruneVideoFileCache(directory: File) {
    trimFileCache(
        directory.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".tmp") },
        VIDEO_CACHE_MAX_BYTES,
        expireBefore = System.currentTimeMillis() - VIDEO_CACHE_TTL_MS
    )
}

/** Persistent byte-range cache for Google Photos and S3 videos.
 * Photos bytes have no time-based expiry; they stay on disk until LRU eviction
 * or an explicit cache clear, even after the signed playback URL expires.
 */
internal object VideoStreamCache {
    private var cache: SimpleCache? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var lastPruneAt = 0L

    @Synchronized
    private fun get(context: Context): SimpleCache {
        cache?.let { return it }
        val appContext = context.applicationContext
        val provider = StandaloneDatabaseProvider(appContext)
        val created = SimpleCache(
            File(appContext.filesDir, "video-stream-cache").apply { mkdirs() },
            LeastRecentlyUsedCacheEvictor(VIDEO_CACHE_MAX_BYTES),
            provider
        )
        databaseProvider = provider
        cache = created
        pruneExpired(created, force = true)
        return created
    }

    @Synchronized
    private fun pruneExpired(cache: SimpleCache, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPruneAt < 60L * 60L * 1000L) return
        lastPruneAt = now
        val cutoff = now - VIDEO_CACHE_TTL_MS
        cache.keys.toList().forEach { key ->
            // Old S3 entries retain the existing 7-day TTL. Photos entries do not:
            // replaying a fully cached video must not require a network request.
            if (isPersistentPhotosVideoKey(key)) return@forEach
            cache.getCachedSpans(key).toList().forEach { span ->
                if (shouldPruneCachedVideoSpan(key, span.lastTouchTimestamp, cutoff)) {
                    runCatching { cache.removeSpan(span) }
                }
            }
        }
    }

    fun dataSourceFactory(context: Context, upstreamFactory: DataSource.Factory): DataSource.Factory {
        val simpleCache = get(context)
        synchronized(this) { pruneExpired(simpleCache) }
        val cachedFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheKeyFactory { dataSpec ->
                val mediaId = dataSpec.uri.takeIf { it.scheme == "manydrive" }?.getQueryParameter("id")
                cacheKeyForVideo(mediaId?.let(PlaybackSourceRegistry::get), mediaId,
                    dataSpec.key ?: dataSpec.uri.toString())
            }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return RoutingVideoDataSourceFactory(upstreamFactory, cachedFactory)
    }

    @Synchronized
    fun clear(context: Context) {
        cache?.keys?.toList()?.forEach { key -> runCatching { cache?.removeResource(key) } }
        if (cache == null) runCatching { File(context.applicationContext.filesDir, "video-stream-cache").deleteRecursively() }
        runCatching { File(context.applicationContext.filesDir, "video-file-cache").deleteRecursively() }
    }

    @Synchronized
    fun release() {
        cache?.release()
        cache = null
        databaseProvider?.close()
        databaseProvider = null
        lastPruneAt = 0L
    }
}

// Keep the cache enabled for Photos, so cache hits do not open the expiring
// Google Photos URL. Missing byte ranges alone are downloaded over the network.
internal fun shouldCacheVideoBytes(accountType: String?, mimeType: String?): Boolean =
    accountType in setOf("PHOTOS", "S3") && mimeType?.startsWith("video/") == true

// Photos uses a new namespace: never replay old potentially mixed byte ranges
// created by the earlier unversioned key. Media identity and creation time keep
// cache hits stable across app restarts, without tying them to expiring URLs.
internal fun cacheKeyForVideo(source: PlaybackSource?, mediaId: String?, fallback: String): String =
    when {
        source?.accountType == "PHOTOS" ->
            "photos:v2:${source.mediaId}:${source.file.modifiedTime.orEmpty()}:${source.file.size ?: -1L}"
        mediaId.isNullOrBlank() -> fallback
        else -> "video:$mediaId"
    }

internal fun isPersistentPhotosVideoKey(key: String): Boolean = key.startsWith("photos:v2:")

internal fun shouldPruneCachedVideoSpan(key: String, lastTouchMs: Long, cutoffMs: Long): Boolean =
    !isPersistentPhotosVideoKey(key) && lastTouchMs > 0L && lastTouchMs < cutoffMs

private class RoutingVideoDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val cacheFactory: DataSource.Factory
) : DataSource.Factory {
    override fun createDataSource(): DataSource = RoutingVideoDataSource(
        upstreamFactory.createDataSource(),
        cacheFactory.createDataSource()
    )
}

private class RoutingVideoDataSource(
    private val direct: DataSource,
    private val cached: DataSource
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        direct.addTransferListener(transferListener)
        cached.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val mediaId = dataSpec.uri.takeIf { it.scheme == "manydrive" }?.getQueryParameter("id")
        val source = mediaId?.let(PlaybackSourceRegistry::get)
        val streamCache = shouldCacheVideoBytes(source?.accountType, source?.file?.mimeType)
        active = if (streamCache) cached else direct
        return requireNotNull(active).open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        requireNotNull(active).read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders.orEmpty()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }
}
