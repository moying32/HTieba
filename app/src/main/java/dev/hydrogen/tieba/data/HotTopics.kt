package dev.hydrogen.tieba.data

import java.io.IOException
import org.json.JSONObject
import okhttp3.Request
import kotlinx.coroutines.CancellationException

internal object HotTopicsParser {
    fun parse(root: JSONObject): List<HotTopic> {
        val code=root.optString("errno")
        if(code.isBlank()) throw IOException("贴吧热榜没有返回结果状态，请稍后重试")
        if(code!="0") throw TiebaRejectedException(code,root.optString("errmsg").ifBlank { "贴吧热榜暂时不可用" }.take(160))
        val list = root.optJSONObject("data")?.optJSONObject("bang_topic")?.optJSONArray("topic_list")
            ?: throw IOException("百度贴吧未返回热榜，请稍后重试")
        return CommunityParser.objects(list).mapIndexedNotNull { index, item ->
            val title = CommunityParser.text(item,"topic_name")
            if(title.isBlank()) null else HotTopic(CommunityParser.text(item,"topic_id"),title,
                CommunityParser.text(item,"topic_desc","abstract"),CommunityParser.number(item,"discuss_num"),
                item.optInt("idx_num",index+1),item.optInt("tag"))
        }.distinctBy { it.id.ifBlank { it.title } }
    }
}

internal suspend fun TiebaApi.hotTopics(): List<HotTopic> {
    // This is a public web ranking, not an account-specific recommendation endpoint.
    val request=Request.Builder().url("https://tieba.baidu.com/hottopic/browse/topicList")
        .header("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
        .header("Referer","https://tieba.baidu.com/").header("Accept","application/json").build()
    val text=try { execute(request) } catch(e: CancellationException) { throw e }
        catch(e: IOException) {
            if(e.message?.any { it.code > 127 } == true) throw e
            throw IOException("贴吧热榜暂时连接失败（${e.javaClass.simpleName}），请下拉重试",e)
        }
    val data=try { JSONObject(text) } catch(e: org.json.JSONException) { throw IOException("贴吧热榜返回了验证页面，请稍后重试",e) }
    return HotTopicsParser.parse(data)
}
