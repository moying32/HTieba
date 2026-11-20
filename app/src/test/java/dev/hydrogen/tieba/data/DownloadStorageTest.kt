package dev.hydrogen.tieba.data

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class DownloadStorageTest {
    private fun files(test: (File, File) -> Unit) {
        val root = Files.createTempDirectory("h2-download-test").toFile()
        try { test(File(root, "source.gif").apply { writeBytes(ByteArray(40000) { (it % 251).toByte() }) }, File(root, "Download/HTieBa")) }
        finally { root.deleteRecursively() }
    }

    @Test fun createsFolderAndPreservesOriginalBytes() = files { source, directory ->
        val result = OriginalFileStorage.copy(source, directory, "sample.gif")
        assertEquals(File(directory, "sample.gif"), result)
        assertArrayEquals(source.readBytes(), result.readBytes())
    }

    @Test fun existingFileIsNeverOverwritten() = files { source, directory ->
        directory.mkdirs()
        val existing = File(directory, "sample.gif").apply { writeText("keep this file") }
        try { OriginalFileStorage.copy(source, directory, "sample.gif"); fail("Should not overwrite") }
        catch (_: IOException) { }
        assertEquals("keep this file", existing.readText())
    }

    @Test fun cancellationDeletesPartialFileAndKeepsSource() = files { source, directory ->
        var reads = 0
        try {
            OriginalFileStorage.copy(source, directory, "partial.gif") {
                if (++reads == 2) throw CancellationException()
            }
            fail("Should cancel")
        } catch (_: CancellationException) { }
        assertFalse(File(directory, "partial.gif").exists())
        assertEquals(40000L, source.length())
    }

    @Test fun ioFailureDeletesPartialFile() = files { source, directory ->
        var reads = 0
        try {
            OriginalFileStorage.copy(source, directory, "partial.gif") {
                if (++reads == 2) throw IOException("disk full")
            }
            fail("Should fail")
        } catch (_: IOException) { }
        assertFalse(File(directory, "partial.gif").exists())
    }
}
