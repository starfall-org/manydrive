package com.starfall.gsadrive.shared.cache

import java.io.File
import java.security.MessageDigest

internal fun cacheKeyHash(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

/** Stage beside the target so failed writes preserve the previous cache entry. */
internal inline fun writeCacheFile(target: File, write: (File) -> Unit) {
    val directory = requireNotNull(target.absoluteFile.parentFile)
    directory.mkdirs()
    // Separate staging files also allow preview and playback to populate the same cache entry.
    val temporary = File.createTempFile("cache-", ".tmp", directory)
    try {
        write(temporary)
        if (!temporary.renameTo(target)) temporary.copyTo(target, overwrite = true)
    } finally {
        temporary.delete()
    }
}

/** Keep newest entries within the byte budget. Each caller chooses its own files and TTL. */
internal fun trimFileCache(files: Iterable<File>, maxBytes: Long, expireBefore: Long? = null) {
    var total = 0L
    files.filter { it.isFile }
        .onEach { if (expireBefore != null && it.lastModified() < expireBefore) it.delete() }
        .filter { it.isFile }
        .sortedByDescending { it.lastModified() }
        .forEach { file ->
            total += file.length()
            if (total > maxBytes) file.delete()
        }
}
