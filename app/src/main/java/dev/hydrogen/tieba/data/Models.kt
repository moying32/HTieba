package dev.hydrogen.tieba.data

enum class IdentitySurface(val apiKey: String) { Feed("frs"), Post("pb"), Profile("home") }

data class UserBadge(
    val name: String,
    val image: String,
    val label: String = "贴吧徽章",
    val surfaces: Set<IdentitySurface> = IdentitySurface.entries.toSet(),
)

data class Person(
    val id: String = "", val name: String = "吧友", val avatar: String = "",
    val level: Int = 0, val levelName: String = "", val role: String = "",
    val badges: List<UserBadge> = emptyList(),
    val location: String = "",
)

data class ThreadItem(
    val id: String,
    val title: String,
    val forum: String,
    val author: Person = Person(),
    val excerpt: String = "",
    val images: List<String> = emptyList(),
    val replies: Int = 0,
    val likes: Int = 0,
    val timestamp: Long = 0,
    val pinned: Boolean = false,
    val forumId: String = "",
    val liked: Boolean = false,
    val views: Long? = null,
    val mediaCount: Int = images.size,
    val video: String = "",
    val videoCover: String = "",
    val poll: PollInfo? = null,
    val likesKnown: Boolean = true,
    val repliesKnown: Boolean = true,
    val imageSources: List<ImageSource> = emptyList(),
    val collections: Long? = null,
)

data class PollInfo(val title: String, val options: List<String>, val total: Long? = null)
data class ContentPart(val text: String = "", val image: String = "", val link: String = "", val ratio: Float = 1f, val video: String = "",
    val imageSource: ImageSource? = null, val videoCover: String = "", val isVideo: Boolean = false, val mention: Person? = null,
    val emoji: String = "")

enum class ImageQuality(val label: String, val edge: Int, val description: String) {
    Low("低", 720, "完整图片，较低预览精度"), Medium("中", 1440, "兼顾清晰度与流量"),
    High("高", 2560, "高清预览，适合放大"), Original("原画", 4096, "读取接口原图，超大图预览解码最长边上限 4096 像素")
}
data class ImageSource(val preview: String, val medium: String = "", val high: String = "", val original: String = "") {
    fun url(quality: ImageQuality) = when(quality) {
        ImageQuality.Low -> preview.ifBlank { medium.ifBlank { high.ifBlank { original } } }
        ImageQuality.Medium -> medium.ifBlank { high.ifBlank { original.ifBlank { preview } } }
        ImageQuality.High -> high.ifBlank { original.ifBlank { medium.ifBlank { preview } } }
        ImageQuality.Original -> original.ifBlank { high.ifBlank { medium.ifBlank { preview } } }
    }
}

data class ImageGallery(val sources: List<ImageSource>, val initialIndex: Int = 0, val threadId: String = "") {
    init { require(sources.isNotEmpty() && initialIndex in sources.indices) }
}

fun ThreadItem.galleryImages(): List<ImageSource> = imageSources.filter { it.url(ImageQuality.Original).isNotBlank() }
    .let { known -> known + images.filter { url -> known.none { url in listOf(it.preview, it.medium, it.high, it.original) } }.map(::ImageSource) }

data class VideoSource(val url: String = "", val cover: String = "", val title: String = "贴吧视频", val threadId: String = "")
data class HotTopic(val id: String, val title: String, val description: String, val discussions: Long?, val rank: Int, val tag: Int)

data class PostItem(
    val id: String,
    val floor: Int,
    val author: Person,
    val content: List<ContentPart>,
    val timestamp: Long,
    val likes: Int,
    val subReplies: Int,
    val liked: Boolean = false,
    val location: String = "",
    val previewReplies: List<PostItem> = emptyList(),
)

data class ForumPage(
    val name: String,
    val members: Long,
    val threads: List<ThreadItem>,
    val page: Int,
    val hasMore: Boolean,
    val forumId: String = "",
    val followed: Boolean = false,
    val info: ForumInfo? = null,
)
data class ThreadPage(val thread: ThreadItem, val posts: List<PostItem>, val page: Int, val hasMore: Boolean)
data class FloorPage(val posts: List<PostItem>, val page: Int, val hasMore: Boolean)
enum class ThemeMode(val label: String) { System("跟随系统"), Light("浅色"), Dark("深色") }

enum class VisualStyle(val label: String, val description: String, val defaultAccent: String) {
    Classic("经典", "紧凑信息流 · 柔和底色", "326A54"),
    Margin("映间 MARGIN", "编辑式排版 · 暖白与墨黑", "00AEEC"),
    Claude("Claude 风", "暖纸底色 · 陶土强调", "C16849");

    val accentPreferenceKey get() = when(this) { Classic -> "accent_color"; Margin -> "margin_accent_color"; Claude -> "claude_accent_color" }
    companion object {
        fun fromStored(value: String?) = entries.firstOrNull { it.name == value } ?: Classic
    }
}

data class ForumItem(val id: String, val name: String, val avatar: String = "", val description: String = "")
data class DiscoveryPage(val followed: List<ForumItem>, val recommended: List<ForumItem>)
data class StreamPage(val threads: List<ThreadItem>, val page: Int, val hasMore: Boolean, val cursor: String = "")

data class AccountState(
    val loading: Boolean = false,
    val loggedIn: Boolean = false,
    val uid: String = "",
    val name: String = "",
    val avatar: String = "",
    val identity: Person = Person(),
    val error: String? = null,
    val profile: UserProfile? = null,
    val profileError: String? = null,
)

data class UserProfile(val person: Person, val introduction: String = "", val gender: Int = 0,
    val follows: Long? = null, val fans: Long? = null, val threads: Long? = null,
    val posts: Long? = null, val age: String = "", val location: String = "", val followed: Boolean = false,
    val portrait: String = "", val backgroundImage: String = "")
data class ForumInfo(val id: String, val name: String, val avatar: String = "", val description: String = "",
    val members: Long? = null, val posts: Long? = null, val threads: Long? = null,
    val level: Int = 0, val levelName: String = "", val experience: Long? = null,
    val signed: Boolean = false, val managers: List<Person> = emptyList())
data class UserContent(val thread: ThreadItem, val postId: String = "", val text: String = "", val deleted: Boolean = false)
data class ContentPage(val items: List<UserContent>, val page: Int, val hasMore: Boolean)
data class PeoplePage(val users: List<UserProfile>, val page: Int, val hasMore: Boolean)
enum class SearchKind(val label: String) { Thread("帖子"), Forum("吧"), User("用户") }
data class SearchPage(val threads: List<ThreadItem> = emptyList(), val forums: List<ForumItem> = emptyList(),
    val users: List<UserProfile> = emptyList(), val page: Int = 1, val hasMore: Boolean = false)
enum class NoticeKind(val label: String, val path: String, val listKey: String) {
    Reply("回复", "replyme", "reply_list"), At("@我", "atme", "at_list"), Like("点赞", "agreeme", "agree_list")
}
data class Notice(val id: String, val threadId: String, val postId: String, val person: Person,
    val title: String, val text: String, val quote: String = "", val time: Long = 0, val unread: Boolean = false,
    val isFloor: Boolean = false)
data class NoticePage(val items: List<Notice>, val page: Int, val hasMore: Boolean, val cursor: String = "0")
enum class ForumSort(val label: String, val code: String, val good: Boolean = false) {
    Reply("最新回复", "6"), Newest("最新发布", "1"), Hot("热门", "3"), Good("精华", "6", true)
}

enum class ReplySort(val label: String, val code: String) {
    Ascending("正序", "0"), Descending("倒序", "1"), Hot("热门", "2")
}
