package dev.hydrogen.tieba.data

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Device-local feed filtering; never presented as a server blacklist. */
data class ContentFilters(
    val users: Map<String, String> = emptyMap(),
    val forums: Set<String> = emptySet(),
    val threads: Set<String> = emptySet(),
) {
    fun hides(thread: ThreadItem): Boolean = thread.id in threads ||
        (thread.author.id.isNotBlank() && thread.author.id in users) ||
        forums.any { it.lowercase(Locale.ROOT) == thread.forum.trim().lowercase(Locale.ROOT) }

    fun encode(): String = JSONObject().put("users", JSONObject(users))
        .put("forums", JSONArray(forums.toList())).put("threads", JSONArray(threads.toList())).toString()

    companion object {
        fun decode(value: String): ContentFilters = runCatching {
            val raw = JSONObject(value)
            val people = raw.optJSONObject("users") ?: JSONObject()
            fun strings(key: String): Set<String> {
                val array = raw.optJSONArray(key) ?: return emptySet()
                return (0 until array.length()).mapNotNull { array.opt(it) as? String }
                    .map(String::trim).filter(String::isNotBlank).toSet()
            }
            ContentFilters(people.keys().asSequence().filter(String::isNotBlank)
                .associateWith { people.optString(it).ifBlank { it } }, strings("forums"), strings("threads"))
        }.getOrDefault(ContentFilters())
    }
}
