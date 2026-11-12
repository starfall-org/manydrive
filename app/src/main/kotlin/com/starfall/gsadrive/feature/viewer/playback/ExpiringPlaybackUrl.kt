package com.starfall.gsadrive.feature.viewer.playback

/** Reuse one Photos URL for byte-range reads, refreshing before its 60-minute expiry. */
internal class ExpiringPlaybackUrl(
    private val maxAgeMs: Long = 50L * 60L * 1000L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class CachedUrl(val url: String, val createdAtMs: Long)

    @Volatile private var cached: CachedUrl? = null

    fun getOrLoad(load: () -> String): String {
        val now = clock()
        cached?.takeIf { now - it.createdAtMs in 0 until maxAgeMs }?.let { return it.url }
        return synchronized(this) {
            val currentTime = clock()
            cached?.takeIf { currentTime - it.createdAtMs in 0 until maxAgeMs }?.url
                ?: load().also { cached = CachedUrl(it, clock()) }
        }
    }
}
