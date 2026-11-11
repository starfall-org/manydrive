package com.starfall.gsadrive.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileListCacheTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun clearingOneAccountKeepsOtherAccountsAndLocationsIndependent() {
        val cache = FileListCache(directory.newFolder())
        val own = listOf(DriveFile("own", "Own", "text/plain", null))
        val shared = listOf(DriveFile("shared", "Shared", "text/plain", null))
        cache.write("GOOGLE:a", "0:", own)
        cache.write("GOOGLE:a", "1:", shared)
        cache.write("GOOGLE:b", "0:", shared)
        assertEquals(own, cache.read("GOOGLE:a", "0:"))
        assertEquals(shared, cache.read("GOOGLE:a", "1:"))
        cache.clear("GOOGLE:a")
        assertNull(cache.read("GOOGLE:a", "0:"))
        assertNull(cache.read("GOOGLE:a", "1:"))
        assertEquals(shared, cache.read("GOOGLE:b", "0:"))
    }
}
