package com.starfall.gsadrive.shared.cache

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiskCacheFilesTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun cacheHashesRemainCompatibleWithExistingPaths() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", cacheKeyHash("abc"))
    }

    @Test fun successfulWriteReplacesEntryAndRemovesStagingFile() {
        val folder = directory.newFolder()
        val target = File(folder, "entry").apply { writeText("old") }
        writeCacheFile(target) { it.writeText("new") }
        assertEquals("new", target.readText())
        assertEquals(listOf(target), folder.listFiles()!!.toList())
    }

    @Test fun failedWritePreservesOldEntryAndRemovesPartialDownload() {
        assertFailedWriteCleanup(IOException("download interrupted"))
    }

    @Test fun cancelledWritePreservesOldEntryAndRemovesPartialDownload() {
        assertFailedWriteCleanup(CancellationException("stop"))
    }

    private fun assertFailedWriteCleanup(failure: Exception) {
        val folder = directory.newFolder()
        val target = File(folder, "entry").apply { writeText("old") }
        try {
            writeCacheFile(target) {
                it.writeText("partial")
                throw failure
            }
            fail("Write failure must propagate")
        } catch (actual: Exception) {
            assertSame(failure, actual)
        }
        assertEquals("old", target.readText())
        assertEquals(listOf(target), folder.listFiles()!!.toList())
    }

    @Test fun overlappingWritesUseSeparateStagingFiles() {
        val target = File(directory.newFolder(), "entry")
        writeCacheFile(target) { first ->
            first.writeText("first")
            writeCacheFile(target) { second ->
                assertNotEquals(first, second)
                second.writeText("second")
            }
            assertTrue(first.exists())
            assertEquals("second", target.readText())
        }
        assertEquals("first", target.readText())
        assertEquals(listOf(target), target.parentFile!!.listFiles()!!.toList())
    }

    @Test fun byteBudgetKeepsNewestEntriesAcrossAccountDirectories() {
        val firstAccount = directory.newFolder()
        val secondAccount = directory.newFolder()
        val old = entry(firstAccount, "old", 10_000)
        val newer = entry(secondAccount, "newer", 20_000)
        val newest = entry(firstAccount, "newest", 30_000)
        trimFileCache(listOf(old, newer, newest), maxBytes = 8)
        assertFalse(old.exists())
        assertTrue(newer.exists())
        assertTrue(newest.exists())
    }

    @Test fun expiryAndByteBudgetDoNotTouchExcludedStagingFiles() {
        val folder = directory.newFolder()
        val expired = entry(folder, "expired", 10_000)
        val current = entry(folder, "current", 20_000)
        val staging = entry(folder, "active.tmp", 5_000)
        trimFileCache(folder.listFiles()!!.filterNot { it.name.endsWith(".tmp") }, 8, expireBefore = 15_000)
        assertFalse(expired.exists())
        assertTrue(current.exists())
        assertTrue(staging.exists())
    }

    private fun entry(folder: File, name: String, modified: Long) = File(folder, name).apply {
        writeText("data")
        assertTrue(setLastModified(modified))
    }
}
