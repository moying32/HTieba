package dev.hydrogen.tieba.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class ImageDownloadTest {
    @Test fun preservesOriginalBytesAndAnimatedGifRatherThanExportingThePreview() {
        val original = "GIF89a".toByteArray() + ByteArray(400) { (it % 256).toByte() }
        val output = ByteArrayOutputStream()
        assertEquals(ImageFormat("gif", "image/gif"), ImageFileCodec.copy(original.inputStream(), output))
        assertArrayEquals(original, output.toByteArray())
    }

    @Test fun detectsActualImageTypeIndependentlyOfUrlSuffixOrServerMime() {
        val samples = listOf(
            byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()) to ImageFormat("jpg", "image/jpeg"),
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) to ImageFormat("png", "image/png"),
            "RIFF0000WEBP".toByteArray() to ImageFormat("webp", "image/webp"),
            "0000ftypavif".toByteArray() to ImageFormat("avif", "image/avif"))
        samples.forEach { (header, type) ->
            val bytes = header + ByteArray(100)
            val output = ByteArrayOutputStream()
            assertEquals(type, ImageFileCodec.copy(bytes.inputStream(), output))
            assertArrayEquals(bytes, output.toByteArray())
        }
    }

    @Test fun htmlLoginAndErrorPagesNeverBecomeSavedPictures() {
        listOf("<html>安全验证</html>", "{\"error\":1}", "", "GIF").forEach { value ->
            val output = ByteArrayOutputStream()
            assertTrue(runCatching { ImageFileCodec.copy(value.byteInputStream(), output) }.exceptionOrNull() is IOException)
            assertEquals(0, output.size())
        }
    }

    @Test fun streamedUnknownLengthResponsesStillHonorTheFileLimit() {
        val bytes = "GIF89a".toByteArray() + ByteArray(64)
        assertTrue(runCatching { ImageFileCodec.copy(ByteArrayInputStream(bytes), ByteArrayOutputStream(), limit = 32) }.exceptionOrNull() is IOException)
    }

    @Test fun shortStreamReadsDoNotLoseHeaderOrTruncateTheSavedImage() {
        val bytes = "GIF89a".toByteArray() + ByteArray(100) { it.toByte() }
        val input = object : ByteArrayInputStream(bytes) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(len, 2))
        }
        val output = ByteArrayOutputStream()
        ImageFileCodec.copy(input, output)
        assertArrayEquals(bytes, output.toByteArray())
    }
}
