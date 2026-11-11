package com.starfall.gsadrive.shared.media

import com.starfall.gsadrive.R

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.starfall.gsadrive.shared.i18n.localized
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import com.starfall.gsadrive.shared.cache.cacheKeyHash
import com.starfall.gsadrive.shared.cache.trimFileCache
import com.starfall.gsadrive.shared.cache.writeCacheFile
import java.util.concurrent.ConcurrentHashMap

/** Shared two-level thumbnail cache for browser cards and MediaSession artwork. */
internal object ThumbnailRepository {
    private const val MEMORY_CACHE_KB = 24 * 1024
    private const val DISK_CACHE_BYTES = 256L * 1024L * 1024L

    private val memory = object : LruCache<String, Bitmap>(MEMORY_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val locks = ConcurrentHashMap<String, Any>()

    fun cached(cacheKey: String): Bitmap? = synchronized(memory) { memory.get(cacheKey) }

    @Throws(IOException::class)
    fun load(
        context: Context,
        url: String,
        accessToken: String? = null,
        cacheKey: String = url
    ): Bitmap {
        cached(cacheKey)?.let { return it }
        val lock = locks.getOrPut(cacheKey) { Any() }
        synchronized(lock) {
            cached(cacheKey)?.let { return it }

            val target = diskFile(context.applicationContext, cacheKey)
            decodeDisk(target)?.let { bitmap ->
                synchronized(memory) { memory.put(cacheKey, bitmap) }
                target.setLastModified(System.currentTimeMillis())
                return bitmap
            }

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                if (!accessToken.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $accessToken")
            }
            try {
                val status = connection.responseCode
                if (status !in 200..299) throw IOException("Thumbnail HTTP $status")
                val bytes = connection.inputStream.use { it.readBytes() }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IOException(localized(R.string.unable_to_decode_thumbnails))
                writeDisk(target, bytes)
                synchronized(memory) { memory.put(cacheKey, bitmap) }
                trimDiskCache(target.parentFile)
                return bitmap
            } finally {
                connection.disconnect()
                locks.remove(cacheKey, lock)
            }
        }
    }


    fun clear(context: Context) {
        synchronized(memory) { memory.evictAll() }
        runCatching { diskDirectory(context.applicationContext).deleteRecursively() }
    }

    private fun diskDirectory(context: Context): File =
        File(context.filesDir, "thumbnail-cache").apply { mkdirs() }

    private fun diskFile(context: Context, cacheKey: String): File =
        File(diskDirectory(context), cacheKeyHash(cacheKey) + ".img")

    private fun decodeDisk(file: File): Bitmap? {
        if (!file.isFile || file.length() <= 0L) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        if (bitmap == null) file.delete()
        return bitmap
    }

    private fun writeDisk(target: File, bytes: ByteArray) {
        runCatching {
            writeCacheFile(target) { it.writeBytes(bytes) }
            target.setLastModified(System.currentTimeMillis())
        }
    }

    private fun trimDiskCache(directory: File?) {
        if (directory == null || !directory.isDirectory) return
        runCatching {
            trimFileCache(
                directory.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".tmp") },
                DISK_CACHE_BYTES
            )
        }
    }
}
