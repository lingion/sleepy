package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class JwPostgradContainerResilienceTest(private val type: String, private val source: String, private val expected: JwCourse) {
    private val noise = "<table><tr><td>菜单</td><td>公告</td></tr></table>"
    private fun mutate(transform: (String) -> String): String = if (type == "zju_post")
        JSONArray().put(transform(JSONArray(source).getString(0))).toString() else transform(source)
    private fun rename(html: String): String {
        val document = Jsoup.parse(html)
        document.select("table, #table, #pageRpt, #StuCul_TimetableQry_TimeTable, form#kcbForm").forEach {
            if (it.hasAttr("id")) it.attr("id", "arbitrary-container")
            if (it.hasAttr("class")) it.attr("class", "arbitrary-presentation")
        }
        return document.outerHtml()
    }
    private fun verify(text: String) {
        val parser = JwParserRegistry.parserFor(type, text)
        assertEquals(type, listOf(expected), parser.generateCourseList())
        assertTrue(type, parser.confidence() > 0)
        assertEquals(type, listOf(expected), JwParserRegistry.selectBest(text, type).first)
    }
    @Test fun `semantic course layout survives presentation container renaming`() = verify(mutate(::rename))
    @Test fun `unrelated surrounding tables cannot shadow the complete layout`() {
        for (wrap in listOf<(String)->String>({ noise + it }, { it + noise }, { "<section>"+noise+rename(it)+noise+"</section>" })) verify(mutate(wrap))
    }
    @Test fun `ambiguous fallback layouts are not combined or chosen by document order`() {
        val text = mutate { html ->
            val first = Jsoup.parse(rename(html))
            val second = Jsoup.parse(rename(html).replace("课程2", "另一课"))
            first.body().appendChildren(second.body().children().toList())
            first.outerHtml()
        }
        assertTrue(type, JwParserRegistry.parserFor(type,text).generateCourseList().isEmpty())
    }
    @Test fun `equivalent repeated layouts keep one complete copy`() {
        verify(mutate { html ->
            val first = Jsoup.parse(rename(html))
            val second = Jsoup.parse(rename(html))
            first.body().appendChildren(second.body().children().toList())
            first.outerHtml()
        })
    }
    @Test fun `a presentation title row cannot shift the declared period`() {
        if (type !in listOf("cupl_post", "ysu_post", "shtu_post", "zju_post")) return
        verify(mutate { html ->
            val document = Jsoup.parse(rename(html))
            document.selectFirst("table tr")!!.before("<tr><th colspan='8'>课程表标题</th></tr>")
            document.outerHtml()
        })
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}") fun cases() = JwPostgradRoundTwoRoutingTest.cases()
            .filter { it[0] !in listOf("csv", "shuwei_old", "xauat_post") }
    }
}
