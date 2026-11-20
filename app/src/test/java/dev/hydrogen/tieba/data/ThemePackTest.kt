package dev.hydrogen.tieba.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ThemePackTest {
    private fun container(vararg entries: Pair<String,ByteArray>): ByteArray = ByteArrayOutputStream().use { out ->
        out.write("HYDROGEN-THEME/1\n".toByteArray())
        ZipOutputStream(out).use { zip -> entries.forEach { (name,bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
        out.toByteArray()
    }
    private fun rejected(bytes: ByteArray) {
        try { ThemePackCodec.read(ByteArrayInputStream(bytes)); fail("Invalid package was accepted") } catch(e: Exception) { assertTrue(e.message.orEmpty().isNotBlank()) }
    }
    @Test fun validPackageRoundTripsEveryField() {
        assertEquals(ThemePackCodec.example,ThemePackCodec.read(ByteArrayInputStream(ThemePackCodec.encode(ThemePackCodec.example))))
    }
    @Test fun documentedAiExampleCanBeImported() {
        val json=java.io.File("examples/theme.json").readText(Charsets.UTF_8)
        val expected=ThemePackCodec.parse(org.json.JSONObject(json))
        val imported=java.io.File("examples/paper-sky.h2theme").inputStream().use(ThemePackCodec::read)
        assertEquals(expected,imported)
    }
    @Test fun refusesPlainZipAndForeignOrFuturePackages() {
        val bytes=ThemePackCodec.encode(ThemePackCodec.example)
        rejected(bytes.copyOfRange("HYDROGEN-THEME/1\n".length,bytes.size))
        val json=ThemePackCodec.json(ThemePackCodec.example)
        json.put("target","another.app")
        rejected(container("theme.json" to json.toString().toByteArray()))
        json.put("target","dev.hydrogen.tieba").put("schemaVersion",2)
        rejected(container("theme.json" to json.toString().toByteArray()))
    }
    @Test fun refusesTraversalExtraEntriesAndUnsupportedExecutableFields() {
        val raw=ThemePackCodec.json(ThemePackCodec.example).toString().toByteArray()
        rejected(container("../theme.json" to raw))
        rejected(container("theme.json" to raw,"script.js" to byteArrayOf(1)))
        val json=ThemePackCodec.json(ThemePackCodec.example).put("script","anything")
        rejected(container("theme.json" to json.toString().toByteArray()))
    }
    @Test fun boundsCompressedAndInflatedInput() {
        rejected(ByteArray(ThemePackCodec.MAX_PACKAGE_BYTES + 1))
        rejected(container("theme.json" to ByteArray(ThemePackCodec.MAX_JSON_BYTES+1) { ' '.code.toByte() }))
        rejected(ThemePackCodec.encode(ThemePackCodec.example).copyOf(24))
    }
    @Test fun refusesWrongTypesBadColorsAndUnsafePaletteSurfaces() {
        val edits: List<(org.json.JSONObject) -> Unit> = listOf(
            { it.put("name",5) }, { it.put("id","../../outside") }, { it.put("accent","javascript:alert(1)") },
            { it.getJSONObject("layout").put("cards","true") }, { it.getJSONObject("layout").put("cornerRadius",29) },
            { it.getJSONObject("light").put("surface","#000000") }, { it.getJSONObject("dark").put("background","#FFFFFF") },
            { it.put("font","https://font.example/file.ttf") }
        )
        edits.forEach { edit -> val json=ThemePackCodec.json(ThemePackCodec.example); edit(json); rejected(container("theme.json" to json.toString().toByteArray())) }
    }
    @Test fun storageDoesNotOverwriteSameIdAndSupportsRemoval() {
        val folder=kotlin.io.path.createTempDirectory("hydrogen-theme-test").toFile()
        try {
            val store=ThemePackStore(folder)
            store.save(ThemePackCodec.example)
            try { store.save(ThemePackCodec.example.copy(name="Overwrite")); fail("Duplicate id overwritten") } catch(_: IllegalArgumentException) { }
            assertEquals(listOf(ThemePackCodec.example),store.all())
            store.delete(ThemePackCodec.example.id)
            assertTrue(store.all().isEmpty())
        } finally { folder.deleteRecursively() }
    }
}
