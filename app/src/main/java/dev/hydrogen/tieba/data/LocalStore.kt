package dev.hydrogen.tieba.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val preferences = context.getSharedPreferences("reading", Context.MODE_PRIVATE)
    fun chatState(owner: String): String = preferences.getString("chat_v1:$owner", "{}").orEmpty()
    fun saveChatState(owner: String, json: String) { preferences.edit().putString("chat_v1:$owner", json).apply() }
    var legacyPrivateChat: Boolean
        get() = preferences.getBoolean("legacy_private_chat", true)
        set(value) { preferences.edit().putBoolean("legacy_private_chat", value).apply() }
    // An installation identifier, independent of hardware IDs and other apps.
    val installationId: String
        get() = preferences.getString("installation_id", null) ?: java.util.UUID.randomUUID().toString().also {
            preferences.edit().putString("installation_id", it).apply()
        }
    var lastForum: String
        get() = preferences.getString("last_forum", "Minecraft") ?: "Minecraft"
        set(value) { preferences.edit().putString("last_forum", value).apply() }
    var theme: ThemeMode
        get() = ThemeMode.entries.firstOrNull { it.name == preferences.getString("theme", null) } ?: ThemeMode.System
        set(value) { preferences.edit().putString("theme", value.name).apply() }
    var largeText: Boolean
        get() = preferences.getBoolean("large_text", false)
        set(value) { preferences.edit().putBoolean("large_text", value).apply() }
    var richEffects: Boolean
        get() = preferences.getBoolean("rich_effects", true)
        set(value) { preferences.edit().putBoolean("rich_effects", value).apply() }
    var accentColor: String
        get() = customThemeId?.let { preferences.getString("theme_accent:$it", null) }
            ?: preferences.getString(visualStyle.accentPreferenceKey, visualStyle.defaultAccent) ?: visualStyle.defaultAccent
        set(value) { preferences.edit().putString(customThemeId?.let { "theme_accent:$it" } ?: visualStyle.accentPreferenceKey, value).apply() }
    var appFont: AppFont
        get() = AppFont.fromStored(preferences.getString("app_font",null))
        set(value) { preferences.edit().putString("app_font",value.name).apply() }
    var customThemeId: String?
        get() = preferences.getString("custom_theme",null)
        set(value) { preferences.edit().apply { if (value == null) remove("custom_theme") else putString("custom_theme",value) }.apply() }
    fun customAccent(pack: ThemePack) = preferences.getString("theme_accent:${pack.id}",pack.accent) ?: pack.accent
    fun forgetThemeAccent(id: String) { preferences.edit().remove("theme_accent:$id").apply() }
    var visualStyle: VisualStyle
        get() = VisualStyle.fromStored(preferences.getString("visual_style", null))
        set(value) { preferences.edit().putString("visual_style", value.name).apply() }
    var reduceMotion: Boolean
        get() = preferences.getBoolean("reduce_motion", false)
        set(value) { preferences.edit().putBoolean("reduce_motion", value).apply() }
    var autoHideNavigation: Boolean
        get() = preferences.getBoolean("auto_hide_navigation", true)
        set(value) { preferences.edit().putBoolean("auto_hide_navigation", value).apply() }
    var textScale: Float
        get() = preferences.getFloat("text_scale", 1f).takeIf { it.isFinite() }?.coerceIn(.5f, 1.5f) ?: 1f
        set(value) { preferences.edit().putFloat("text_scale", value.takeIf { it.isFinite() }?.coerceIn(.5f, 1.5f) ?: 1f).apply() }
    var imageQuality: ImageQuality
        get() = ImageQuality.entries.firstOrNull { it.name == preferences.getString("image_quality",null) } ?: ImageQuality.High
        set(value) { preferences.edit().putString("image_quality",value.name).apply() }
    var interfaceScale: Float
        get() = preferences.getFloat("interface_scale", 1f).takeIf { it.isFinite() }?.coerceIn(.5f, 1.5f) ?: 1f
        set(value) { preferences.edit().putFloat("interface_scale", value.takeIf { it.isFinite() }?.coerceIn(.5f, 1.5f) ?: 1f).apply() }
    fun profileCover(uid: String): String = preferences.getString("profile_cover:$uid", "").orEmpty()
    fun setProfileCover(uid: String, value: String) { preferences.edit().putString("profile_cover:$uid", value).apply() }
    fun searchHistory(): List<String> = runCatching {
        val array = JSONArray(preferences.getString("search_history", "[]"))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())
    fun rememberSearch(query: String) { preferences.edit().putString("search_history", JSONArray((listOf(query) + searchHistory()).distinct().take(20)).toString()).apply() }
    fun clearSearchHistory() { preferences.edit().remove("search_history").apply() }
    fun removeHistory(id: String) { saveThreads("history", history().filterNot { it.id == id }) }
    fun clearHistory() { preferences.edit().remove("history").apply() }

    fun history(): List<ThreadItem> = readThreads("history")
    fun rememberThread(thread: ThreadItem) { saveThreads("history", (listOf(thread) + history()).distinctBy { it.id }.take(100)) }

    fun draft(key: String): String = preferences.getString("draft:$key", "").orEmpty()
    fun draftUncertain(key: String): Boolean = preferences.getBoolean("draft_uncertain:$key", false)

    fun setDraftUncertain(key: String, value: Boolean) {
        preferences.edit().putBoolean("draft_uncertain:$key", value).apply()
    }

    fun saveDraft(key: String, value: String) {
        preferences.edit().apply { if (value.isBlank()) remove("draft:$key") else putString("draft:$key", value) }.apply()
    }

    fun recentForums(): List<String> = runCatching {
        val array = JSONArray(preferences.getString("forums", "[]"))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())

    fun rememberForum(name: String) {
        val forums = (listOf(name) + recentForums()).distinctBy { it.lowercase() }.take(12)
        preferences.edit().putString("forums", JSONArray(forums).toString()).putString("last_forum", name).apply()
    }

    fun bookmarks(): List<ThreadItem> = readThreads("bookmarks")

    private fun readThreads(key: String): List<ThreadItem> = runCatching {
        val array = JSONArray(preferences.getString(key, "[]"))
        (0 until array.length()).map { index ->
            ThreadStorage.decode(array.getJSONObject(index))
        }
    }.getOrDefault(emptyList())

    fun saveBookmarks(threads: List<ThreadItem>) {
        saveThreads("bookmarks", threads)
    }

    private fun saveThreads(key: String, threads: List<ThreadItem>) {
        val array = JSONArray()
        threads.take(1000).forEach { item ->
            array.put(ThreadStorage.encode(item))
        }
        preferences.edit().putString(key, array.toString()).apply()
    }
}
