package dev.hydrogen.tieba.data

import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HotTopicsParserTest {
    @Test fun preservesServiceOrderRanksAndUnknownHeatWithoutInventingEntries() {
        val topics=HotTopicsParser.parse(JSONObject("""{"data":{"bang_topic":{"topic_list":[
          {"topic_id":22,"topic_name":"服务器排在前面","idx_num":3,"discuss_num":"0"},
          {"topic_id":11,"topic_name":"另一个话题","idx_num":1},
          {"topic_id":22,"topic_name":"重复话题"},{"topic_id":33,"topic_name":""}
        ]}},"errno":0}"""))
        assertEquals(listOf("22","11"),topics.map { it.id })
        assertEquals(listOf(3,1),topics.map { it.rank })
        assertEquals(0L,topics[0].discussions)
        assertNull(topics[1].discussions)
    }
    @Test fun validEmptyListRemainsEmpty() {
        assertTrue(HotTopicsParser.parse(JSONObject("""{"errno":0,"data":{"bang_topic":{"topic_list":[]}}}""")).isEmpty())
    }
    @Test(expected=IOException::class) fun unexpectedSchemaIsAnError() {
        HotTopicsParser.parse(JSONObject("""{"errno":0,"data":{"unrelated":["不能冒充热榜"]}}"""))
    }
    @Test(expected=TiebaRejectedException::class) fun errorCannotMasqueradeAsEmptyRanking() {
        HotTopicsParser.parse(JSONObject("""{"errno":3,"errmsg":"暂时不可用","data":{"bang_topic":{"topic_list":[]}}}"""))
    }
}
