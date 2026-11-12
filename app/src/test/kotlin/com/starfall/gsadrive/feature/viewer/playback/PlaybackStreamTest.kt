package com.starfall.gsadrive.feature.viewer.playback

import org.junit.Assert.*
import org.junit.Test

class PlaybackStreamTest {
    @Test fun photosVideoBytesMustNotBeCachedAcrossTranscodedDownloads() {
        assertFalse(shouldCacheVideoBytes("PHOTOS", "video/mp4"))
        assertTrue(shouldCacheVideoBytes("S3", "video/mp4"))
        assertFalse(shouldCacheVideoBytes("S3", "image/jpeg"))
        assertFalse(shouldCacheVideoBytes("GOOGLE", "video/mp4"))
    }

    @Test fun photosUrlIsReusedForSeeksAndRefreshedBeforeExpiry() {
        var now = 1000L
        var requests = 0
        val urls = ExpiringPlaybackUrl(clock = { now })
        fun load() = "https://photos.test/video-${++requests}=dv"

        assertEquals("https://photos.test/video-1=dv", urls.getOrLoad(::load))
        now += 49L * 60L * 1000L
        assertEquals("https://photos.test/video-1=dv", urls.getOrLoad(::load))
        now += 60L * 1000L
        assertEquals("https://photos.test/video-2=dv", urls.getOrLoad(::load))
        assertEquals(2, requests)
    }

    @Test fun failedRefreshDoesNotPoisonPhotosUrlCache() {
        var requests = 0
        val urls = ExpiringPlaybackUrl()
        try {
            urls.getOrLoad { requests++; error("temporary failure") }
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertEquals("working", urls.getOrLoad { requests++; "working" })
        assertEquals(2, requests)
    }
}
