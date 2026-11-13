package com.starfall.gsadrive.feature.viewer.playback

import org.junit.Assert.*
import org.junit.Test

class PlaybackStreamTest {
    @Test fun photosVideoBytesAreCachedForOfflineReplay() {
        assertTrue(shouldCacheVideoBytes("PHOTOS", "video/mp4"))
        assertTrue(shouldCacheVideoBytes("S3", "video/mp4"))
        assertFalse(shouldCacheVideoBytes("S3", "image/jpeg"))
        assertFalse(shouldCacheVideoBytes("GOOGLE", "video/mp4"))
    }

    @Test fun photosCacheDoesNotExpireByAgeWhileS3RetainsTtl() {
        val cutoff = 100_000L
        assertFalse(shouldPruneCachedVideoSpan("photos:v2:account:media", 1L, cutoff))
        assertTrue(shouldPruneCachedVideoSpan("video:s3:media", 1L, cutoff))
        assertFalse(shouldPruneCachedVideoSpan("video:s3:media", cutoff, cutoff))
    }

    @Test fun photosCacheKeysSurviveUrlRefreshButChangeWithMediaVersion() {
        val first = com.starfall.gsadrive.data.DriveFile(
            "photos:item", "clip.mp4", "video/mp4", "2026-11-13T00:00:00Z", photosMediaId = "item")
        val updated = first.copy(modifiedTime = "2026-11-14T00:00:00Z")
        val source = PlaybackSource("account:photos:item", first, "PHOTOS", null, null,
            java.io.File("unused"))
        val updatedSource = source.copy(file = updated)
        val key = cacheKeyForVideo(source, source.mediaId, "unused")

        assertTrue(isPersistentPhotosVideoKey(key))
        assertEquals(key, cacheKeyForVideo(source, source.mediaId, "other-url"))
        assertNotEquals(key, cacheKeyForVideo(updatedSource, source.mediaId, "unused"))
        assertNotEquals("video:${source.mediaId}", key)
        assertEquals("video:s3:item", cacheKeyForVideo(null, "s3:item", "fallback"))
    }

    @Test fun photosCacheDoesNotRequireTokenToReadCachedRanges() {
        // Token fetching belongs to the upstream resolver, not the cache-key lookup.
        val source = PlaybackSource("account:photos:item",
            com.starfall.gsadrive.data.DriveFile("photos:item", "clip.mp4", "video/mp4", null,
                photosMediaId = "item"), "PHOTOS", null, null, java.io.File("unused"), photosTokenProvider = { error("token must not be fetched on a cache lookup") })
        assertTrue(shouldCacheVideoBytes(source.accountType, source.file.mimeType))
        assertTrue(isPersistentPhotosVideoKey(cacheKeyForVideo(source, source.mediaId, "fallback")))
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
