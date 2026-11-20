package dev.hydrogen.tieba.data

import java.security.MessageDigest
import java.util.zip.CRC32

/** Galaxy2 wire format, derived from this installation's random seed, never a hardware ID. */
internal object ClientIdentity {
    fun cuid(installationId: String): String = fromSeed(installationId.replace("-", "").take(16))

    internal fun fromSeed(seed: String): String {
        require(seed.length == 16 && seed.all { it in '0'..'9' || it in 'a'..'f' })
        val digest = MessageDigest.getInstance("MD5").digest("com.baidu$seed".toByteArray(Charsets.US_ASCII))
            .joinToString("") { "%02X".format(it.toInt() and 255) }
        return "$digest|V${checksum(digest.toByteArray(Charsets.US_ASCII))}"
    }

    private fun checksum(input: ByteArray): String {
        var state = (1L shl 40) - 1
        val blocks = ByteArray(20)
        blocks.fill(255.toByte(), 0, 5)
        fun write(offset: Int) { repeat(5) { blocks[offset + it] = (state ushr (it * 8)).toByte() } }
        fun combine(hash: Long, shift: Int, xor: Boolean) {
            val mask = 0xffffffffL shl shift
            val previous = (state ushr shift) and 0xffffffffL
            val next = if (xor) previous xor hash else previous and hash
            state = (state and mask.inv()) or ((next and 0xffffffffL) shl shift)
        }
        val crc = CRC32().apply { update(input); update(blocks, 0, 5) }
        combine(crc.value, 8, false)
        write(5)
        combine(xxHash32(input + blocks.copyOfRange(0, 10)), 0, true)
        write(10)
        combine(xxHash32(input + blocks.copyOfRange(0, 15)), 1, true)
        write(15)
        crc.update(blocks, 5, 15)
        combine(crc.value, 7, true)
        write(0)
        // Five little-endian bytes form exactly eight RFC 4648 base32 characters.
        var bits = 0L
        repeat(5) { bits = (bits shl 8) or (blocks[it].toLong() and 255) }
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        return (7 downTo 0).map { alphabet[((bits ushr (it * 5)) and 31).toInt()] }.joinToString("")
    }

    /** XXH32 with seed zero; all arithmetic intentionally wraps at 32 bits. */
    private fun xxHash32(bytes: ByteArray): Long {
        val p1 = 0x9E3779B1.toInt()
        val p2 = 0x85EBCA77.toInt()
        val p3 = 0xC2B2AE3D.toInt()
        val p4 = 0x27D4EB2F
        val p5 = 0x165667B1
        fun word(index: Int): Int = (0..3).fold(0) { value, byte -> value or ((bytes[index + byte].toInt() and 255) shl (8 * byte)) }
        fun round(value: Int, word: Int) = Integer.rotateLeft(value + word * p2, 13) * p1
        var cursor = 0
        var hash: Int
        if (bytes.size >= 16) {
            var a = p1 + p2
            var b = p2
            var c = 0
            var d = -p1
            while (cursor <= bytes.size - 16) {
                a = round(a, word(cursor)); b = round(b, word(cursor + 4))
                c = round(c, word(cursor + 8)); d = round(d, word(cursor + 12))
                cursor += 16
            }
            hash = Integer.rotateLeft(a, 1) + Integer.rotateLeft(b, 7) + Integer.rotateLeft(c, 12) + Integer.rotateLeft(d, 18)
        } else hash = p5
        hash += bytes.size
        while (cursor <= bytes.size - 4) {
            hash = Integer.rotateLeft(hash + word(cursor) * p3, 17) * p4
            cursor += 4
        }
        while (cursor < bytes.size) {
            hash = Integer.rotateLeft(hash + (bytes[cursor].toInt() and 255) * p5, 11) * p1
            cursor++
        }
        hash = (hash xor (hash ushr 15)) * p2
        hash = (hash xor (hash ushr 13)) * p3
        return (hash xor (hash ushr 16)).toLong() and 0xffffffffL
    }
}
