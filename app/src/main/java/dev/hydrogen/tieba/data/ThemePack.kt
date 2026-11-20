package dev.hydrogen.tieba.data

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.pow

enum class AppFont(val label: String, val description: String) {
    Theme("跟随主题", "使用当前主题推荐的字体"),
    System("系统默认", "跟随手机系统字体，缺字由系统补全"),
    NotoSans("思源黑体", "清晰中性，适合日常阅读"),
    WenKai("霞鹜文楷", "舒展的楷书笔意"),
    XiaoWei("站酷小薇体", "柔和的宋体风格"),
    ;
    companion object { fun fromStored(value: String?) = entries.firstOrNull { it.name == value } ?: Theme }
}

data class ThemePalette(val background: String, val surface: String, val text: String, val muted: String, val border: String)
data class ThemePack(val id: String, val name: String, val author: String, val description: String,
    val accent: String, val font: AppFont, val cards: Boolean, val cornerRadius: Int,
    val light: ThemePalette, val dark: ThemePalette)

/** Data-only app container: a version marker followed by a single-entry ZIP. Never extracts paths. */
object ThemePackCodec {
    const val EXTENSION = "h2theme"
    const val MAX_PACKAGE_BYTES = 256 * 1024
    const val MAX_JSON_BYTES = 32 * 1024
    private val magic = "HYDROGEN-THEME/1\n".toByteArray(Charsets.US_ASCII)
    private fun InputStream.bounded(limit: Int): ByteArray = ByteArrayOutputStream().use { out ->
        val buffer = ByteArray(4096)
        while (out.size() <= limit) {
            val count = read(buffer,0,minOf(buffer.size,limit + 1 - out.size()))
            if (count < 0) break
            out.write(buffer,0,count)
        }
        out.toByteArray()
    }

    fun read(input: InputStream): ThemePack {
        val bytes = input.bounded(MAX_PACKAGE_BYTES)
        require(bytes.size <= MAX_PACKAGE_BYTES) { "主题包超过 256 KB" }
        require(bytes.size > magic.size && bytes.copyOfRange(0, magic.size).contentEquals(magic)) { "不是氢贴吧主题包，或版本不受支持；请按教程生成 .h2theme 文件" }
        return ZipInputStream(ByteArrayInputStream(bytes, magic.size, bytes.size - magic.size)).use { zip ->
            val entry = zip.nextEntry ?: error("主题包缺少 theme.json")
            require(entry.name == "theme.json" && !entry.isDirectory) { "压缩包根目录只能包含 theme.json" }
            val json = zip.bounded(MAX_JSON_BYTES)
            require(json.size <= MAX_JSON_BYTES) { "theme.json 超过 32 KB" }
            zip.closeEntry()
            require(zip.nextEntry == null) { "主题包包含多余文件；仅支持 theme.json" }
            parse(JSONObject(json.toString(Charsets.UTF_8)))
        }
    }

    fun encode(pack: ThemePack): ByteArray = ByteArrayOutputStream().use { out ->
        out.write(magic)
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("theme.json"))
            zip.write(json(pack).toString(2).toByteArray(Charsets.UTF_8)); zip.closeEntry()
        }
        out.toByteArray()
    }

    fun parse(root: JSONObject): ThemePack {
        fun keys(o: JSONObject, allowed: Set<String>) { require(o.keys().asSequence().toSet() == allowed) { "主题字段缺失或含不支持的字段" } }
        fun str(o: JSONObject, key: String, max: Int, empty: Boolean = false): String {
            val v = o.opt(key)
            require(v is String && v.length <= max && (empty || v.isNotBlank()) && v.none { it.code < 32 }) { "$key 格式无效" }
            return v
        }
        fun color(o: JSONObject, key: String): String = str(o,key,7).also { require(Regex("#[0-9A-Fa-f]{6}").matches(it)) { "$key 须为 #RRGGBB" } }.uppercase()
        fun palette(key: String): ThemePalette {
            val o = root.getJSONObject(key)
            keys(o,setOf("background","surface","text","muted","border"))
            val result = ThemePalette(color(o,"background"),color(o,"surface"),color(o,"text"),color(o,"muted"),color(o,"border"))
            fun luminance(hex: String): Double {
                val rgb = hex.removePrefix("#").toInt(16)
                fun channel(shift: Int): Double { val c = ((rgb shr shift) and 255) / 255.0; return if(c <= .04045) c / 12.92 else ((c + .055) / 1.055).pow(2.4) }
                return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
            }
            require(listOf(result.background,result.surface).all { if(key == "light") luminance(it) >= .55 else luminance(it) <= .12 }) {
                "浅色背景须足够明亮、深色背景须足够暗；请按教程的亮度范围调整 background 与 surface"
            }
            return result
        }
        keys(root,setOf("format","schemaVersion","target","id","name","author","description","accent","font","layout","light","dark"))
        require(root.get("format") == "hydrogen-tieba-theme" && root.get("schemaVersion") == 1 && root.get("target") == "dev.hydrogen.tieba") { "主题包应用标识或协议版本不匹配" }
        val id = str(root,"id",48).also { require(Regex("[a-z][a-z0-9-]{0,47}").matches(it)) { "id 仅支持小写字母、数字与短横线，且以字母开头" } }
        val font = when(str(root,"font",24)) {
            "system" -> AppFont.System; "noto-sans-sc" -> AppFont.NotoSans; "wenkai" -> AppFont.WenKai; "xiaowei" -> AppFont.XiaoWei
            else -> error("主题字体不受支持")
        }
        val layout = root.getJSONObject("layout")
        keys(layout,setOf("cards","cornerRadius"))
        require(layout.get("cards") is Boolean && layout.get("cornerRadius") is Int && layout.getInt("cornerRadius") in 0..28) { "layout 须包含布尔 cards 和 0–28 整数 cornerRadius" }
        return ThemePack(id,str(root,"name",40),str(root,"author",60),str(root,"description",160,true),color(root,"accent").removePrefix("#"),
            font,layout.getBoolean("cards"),layout.getInt("cornerRadius"),palette("light"),palette("dark"))
    }

    fun json(pack: ThemePack): JSONObject {
        fun palette(p: ThemePalette) = JSONObject().put("background",p.background).put("surface",p.surface).put("text",p.text).put("muted",p.muted).put("border",p.border)
        val font = when(pack.font) { AppFont.NotoSans -> "noto-sans-sc"; AppFont.WenKai -> "wenkai"; AppFont.XiaoWei -> "xiaowei"; else -> "system" }
        return JSONObject().put("format","hydrogen-tieba-theme").put("schemaVersion",1).put("target","dev.hydrogen.tieba")
            .put("id",pack.id).put("name",pack.name).put("author",pack.author).put("description",pack.description)
            .put("accent","#${pack.accent}").put("font",font)
            .put("layout",JSONObject().put("cards",pack.cards).put("cornerRadius",pack.cornerRadius))
            .put("light",palette(pack.light)).put("dark",palette(pack.dark))
    }

    val example = ThemePack("paper-sky","纸与晴空","氢贴吧示例","暖纸色与柔和的蓝，保留液态玻璃。","346B91",AppFont.System,true,18,
        ThemePalette("#F5F2EA","#FFFDFA","#272C31","#60666B","#D5D4CE"),
        ThemePalette("#191D22","#242A31","#F0EDE7","#B7BEC6","#424B55"))
}

class ThemePackStore(private val directory: File) {
    fun all(): List<ThemePack> = directory.listFiles().orEmpty().filter { it.extension == ThemePackCodec.EXTENSION }.take(20)
        .mapNotNull { runCatching { it.inputStream().use(ThemePackCodec::read) }.getOrNull() }.sortedBy { it.name }
    @Synchronized fun save(pack: ThemePack) {
        require(all().none { it.id == pack.id }) { "同一 id 的主题已导入，请先删除旧版本，或为新主题使用新 id" }
        require(all().size < 20) { "最多保存 20 个自制主题，请先删除不用的主题" }
        check(directory.exists() || directory.mkdirs()) { "无法创建本机主题目录" }
        val pending = File(directory,"${UUID.randomUUID()}.pending")
        try {
            pending.outputStream().use { it.write(ThemePackCodec.encode(pack)) }
            check(pending.renameTo(File(directory,"${pending.nameWithoutExtension}.${ThemePackCodec.EXTENSION}"))) { "无法保存主题" }
        } finally { pending.delete() }
    }
    @Synchronized fun delete(id: String) {
        directory.listFiles().orEmpty().filter { it.extension == ThemePackCodec.EXTENSION }.forEach { file ->
            val pack = runCatching { file.inputStream().use(ThemePackCodec::read) }.getOrNull()
            if (pack?.id == id) check(file.delete()) { "无法删除主题" }
        }
    }
}
