package com.starfall.gsadrive.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DocumentDownloadsTest {
    @Test fun remoteNamesCannotEscapeDownloadDirectories() {
        listOf("../secret", "..\\secret", "a/b", "a\u0000b").forEach { name ->
            val safe = DocumentDownloads.safeName(name)
            assertFalse(safe.contains('/'))
            assertFalse(safe.contains('\\'))
            assertFalse(safe.any { it.code < 32 })
        }
        assertEquals("download", DocumentDownloads.safeName(".."))
        assertEquals("download", DocumentDownloads.safeName(" "))
    }

    @Test fun duplicateAndSanitizedDownloadNamesRemainDistinct() {
        val used = mutableSetOf<String>()
        assertEquals("a_b", DocumentDownloads.uniqueName("a/b", used))
        assertEquals("1-a_b", DocumentDownloads.uniqueName("a\\b", used))
        assertEquals("2-a_b", DocumentDownloads.uniqueName("a_b", used))
        assertEquals(3, used.size)
    }
}
