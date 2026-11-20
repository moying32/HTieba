package dev.hydrogen.tieba.data

import org.json.JSONArray
import org.json.JSONObject

/** Compatible with the original name-only local history and bookmarks. */
internal object ThreadStorage {
    fun encode(item: ThreadItem): JSONObject = JSONObject()
        .put("id", item.id).put("title", item.title).put("forum", item.forum)
        .put("author", item.author.name).put("author_details", JSONObject()
            .put("id", item.author.id).put("name", item.author.name).put("avatar", item.author.avatar)
            .put("level", item.author.level).put("level_name", item.author.levelName).put("role", item.author.role).put("location", item.author.location)
            .put("badges", JSONArray(item.author.badges.map { badge -> JSONObject()
                .put("name", badge.name).put("image", badge.image).put("label", badge.label)
                .put("surfaces", JSONArray(badge.surfaces.map { it.name })) })))
        .put("images", JSONArray(item.images)).put("excerpt", item.excerpt)
        .put("replies", item.replies).put("likes", item.likes).put("timestamp", item.timestamp)
        .put("pinned", item.pinned).put("forum_id", item.forumId).put("liked", item.liked)
        .put("views", item.views ?: JSONObject.NULL).put("media_count", item.mediaCount).put("video", item.video).put("video_cover", item.videoCover)
        .put("likes_known", item.likesKnown).put("replies_known", item.repliesKnown)
        .put("collections", item.collections ?: JSONObject.NULL)
        .put("image_sources", JSONArray(item.imageSources.map { JSONObject().put("preview",it.preview).put("medium",it.medium).put("high",it.high).put("original",it.original) }))
        .put("poll", item.poll?.let { JSONObject().put("title", it.title).put("options", JSONArray(it.options)).put("total", it.total ?: JSONObject.NULL) } ?: JSONObject.NULL)

    fun decode(item: JSONObject): ThreadItem {
        val author = item.optJSONObject("author_details")
        val badges = author?.optJSONArray("badges")
        val person = if (author == null) Person(name = item.optString("author", "吧友")) else Person(
            id = author.optString("id"), name = author.optString("name", "吧友"),
            avatar = IdentityParser.imageUrl(author.optString("avatar")), level = author.optInt("level"),
            levelName = author.optString("level_name"), role = author.optString("role"), location = author.optString("location"),
            badges = (0 until (badges?.length() ?: 0)).mapNotNull { index ->
                val badge = badges?.optJSONObject(index) ?: return@mapNotNull null
                val image = IdentityParser.imageUrl(badge.optString("image"))
                if (image.isBlank()) return@mapNotNull null
                val surfaces = badge.optJSONArray("surfaces")
                UserBadge(badge.optString("name"), image, badge.optString("label", "贴吧徽章"),
                    (0 until (surfaces?.length() ?: 0)).mapNotNull { i ->
                        IdentitySurface.entries.firstOrNull { it.name == surfaces?.optString(i) }
                    }.toSet())
            },
        )
        val images = item.optJSONArray("images")
        return ThreadItem(item.getString("id"), item.getString("title"), item.optString("forum"),
            author = person, excerpt = item.optString("excerpt"),
            images = (0 until (images?.length() ?: 0)).mapNotNull { IdentityParser.imageUrl(images?.optString(it).orEmpty()).takeIf(String::isNotBlank) },
            replies = item.optInt("replies"), likes = item.optInt("likes"), timestamp = item.optLong("timestamp"),
            pinned = item.optBoolean("pinned"), forumId = item.optString("forum_id"), liked = item.optBoolean("liked"),
            views = CommunityParser.number(item, "views"), mediaCount = if (item.has("media_count")) item.optInt("media_count") else images?.length() ?: 0,
            video = IdentityParser.imageUrl(item.optString("video")), videoCover = IdentityParser.imageUrl(item.optString("video_cover")),
            likesKnown = item.optBoolean("likes_known", true), repliesKnown = item.optBoolean("replies_known", true),
            collections = CommunityParser.number(item, "collections"),
            imageSources = CommunityParser.objects(item.optJSONArray("image_sources")).map { ImageSource(
                IdentityParser.imageUrl(it.optString("preview")),IdentityParser.imageUrl(it.optString("medium")),
                IdentityParser.imageUrl(it.optString("high")),IdentityParser.imageUrl(it.optString("original"))) },
            poll = item.optJSONObject("poll")?.let { poll -> val options = poll.optJSONArray("options"); PollInfo(poll.optString("title"),
                (0 until (options?.length() ?: 0)).map { options!!.optString(it) }, CommunityParser.number(poll, "total")) })
    }
}
