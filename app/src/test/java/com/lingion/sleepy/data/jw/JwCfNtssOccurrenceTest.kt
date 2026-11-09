package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic cases based on NTSS field contracts; no authenticated school capture. */
class JwCfNtssOccurrenceTest {
    private fun row(vararg fields: Pair<String, String>): JSONObject = JSONObject().apply {
        put("kcmc", "实验"); put("teaxms", "张/李"); put("jxcdmc", "A & B")
        put("xq", "3"); put("ps", "01"); put("pe", "02"); put("zc", "1,3,5")
        fields.forEach { (key, value) -> put(key, value) }
    }

    private fun source(rows: List<JSONObject>, periods: JSONArray = JSONArray(), bucket: Int = 0): String = JSONObject()
        .put("sleepyCfNtss", true).put("periods", periods)
        .put("weeks", JSONArray().put(JSONObject().put("week", bucket).put("rows", JSONArray(rows)))).toString()

    private fun assertRoutes(source: String, expected: List<JwCourse>) {
        assertEquals("direct", expected, JwCfNewParser(source).generateCourseList())
        assertEquals("declared", expected, JwParserRegistry.selectBest(source, "cf_new").first)
        assertEquals("screen", expected, JwImportViewModel.parseHtmlStatic(source, "cf_new").first)
    }

    @Test fun `different actual time ranges stay separate across weeks`() {
        val source = source(listOf(
            row("qssj" to "8:30:00", "jssj" to "10:10:00"),
            row("qssj" to "09:00", "jssj" to "10:40", "zc" to "2,4"),
            row("qssj" to "8:30:00", "jssj" to "10:10:00"),
        ))
        assertRoutes(source, listOf(
            JwCourse("实验", "A & B", "张/李", 3, 1, 2, 1, 5, 1, "08:30", "10:10"),
            JwCourse("实验", "A & B", "张/李", 3, 1, 2, 2, 4, 2, "09:00", "10:40"),
        ))
    }

    @Test fun `invalid explicit coordinates never use time or bucket to hide errors`() {
        val invalid = listOf(
            "xq" to "0", "xq" to "8", "ps" to "0", "ps" to "-1", "ps" to "bad",
            "pe" to "0", "pe" to "bad", "zc" to "0", "zc" to "bad", "zc" to "1,bad",
            "zc" to "3-1", "zc" to "1,0", "zc" to "1-2147483647",
            "qssj" to "25:00", "jssj" to "08:20", "jssj" to "10:70",
        )
        for (field in invalid) {
            val r = row("qssj" to "08:30", "jssj" to "10:10", field)
            assertRoutes(source(listOf(r), bucket = 4), emptyList())
        }
        assertRoutes(source(listOf(row("ps" to "4", "pe" to "2"))), emptyList())
    }

    @Test fun `missing end node cannot be silently replaced with start node`() {
        assertRoutes(source(listOf(row("pe" to ""))), emptyList())
    }

    @Test fun `time inference requires unique mappings including conflicts with periods`() {
        val anchors = listOf(
            row("qssj" to "08:30", "jssj" to "10:10", "ps" to "1", "pe" to "2"),
            row("qssj" to "08:30", "jssj" to "10:10", "ps" to "3", "pe" to "4"),
        )
        val inferred = row("kcmc" to "歧义课程", "qssj" to "08:30", "jssj" to "10:10", "ps" to "", "pe" to "")
        assertEquals(emptyList<JwCourse>(), JwCfNewParser(source(anchors + inferred)).generateCourseList().filter { it.name == "歧义课程" })
        val periods = JSONArray("""[{"node":3,"start":"08:30","end":"09:15"},{"node":4,"start":"09:25","end":"10:10"}]""")
        assertEquals(emptyList<JwCourse>(), JwCfNewParser(source(listOf(anchors[0], inferred), periods)).generateCourseList().filter { it.name == "歧义课程" })
    }

    @Test fun `unique observed clock mapping infers both nodes and keeps original clock`() {
        val anchor = row("qssj" to "08:30", "jssj" to "10:10")
        val inferred = row("kcmc" to "推导课程", "qssj" to "08:30:00", "jssj" to "10:10:00", "ps" to "", "pe" to "", "zc" to "")
        val parsed = JwCfNewParser(source(listOf(anchor, inferred), bucket = 7)).generateCourseList()
        assertEquals(JwCourse("推导课程", "A & B", "张/李", 3, 1, 2, 7, 7, 0, "08:30", "10:10"), parsed.single { it.name == "推导课程" })
    }
}
