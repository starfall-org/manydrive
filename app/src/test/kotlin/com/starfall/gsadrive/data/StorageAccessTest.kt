package com.starfall.gsadrive.data

import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageAccessTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun failedStreamUploadRemovesPartialTemporaryFile() = runBlocking {
        assertTemporaryUploadCleanup(IOException("source unavailable"))
    }

    @Test fun cancelledStreamUploadRemovesPartialTemporaryFile() = runBlocking {
        assertTemporaryUploadCleanup(CancellationException("stop"))
    }

    private suspend fun assertTemporaryUploadCleanup(failure: Exception) {
        val cache = directory.newFolder()
        val existing = java.io.File(cache, "unrelated").apply { writeText("keep") }
        val input = object : InputStream() {
            override fun read(): Int = throw failure
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                // The temporary file exists before reading; fail without issuing a network request.
                assertEquals(2, cache.listFiles()!!.size)
                throw failure
            }
        }
        val storage = S3StorageAccess(S3Config("https://example.com", "key", "secret", "bucket"))
        try {
            storage.upload("file", "application/octet-stream", input, "folder/", cache)
            fail("Source failure must propagate")
        } catch (actual: Exception) {
            assertSame(failure, actual)
        }
        assertEquals(listOf(existing), cache.listFiles()!!.toList())
        assertEquals("keep", existing.readText())
    }
}
