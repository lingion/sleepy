package com.lingion.sleepy.data.jw

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JwWhutCollectorIntegrityTest {
    private fun occurrences(rows: List<JwCourse>) = rows.flatMap { r ->
        (r.startWeek..r.endWeek).filter { r.type == 0 || it % 2 == r.type % 2 }.flatMap { w ->
            (r.startNode..r.endNode).map { n -> "${r.name}|${r.teacher}|${r.room}|${r.day}|$n|$w" }
        }
    }.sorted()

    @Test fun `production collector output matches every occurrence in the original response`() {
        val raw = JSONObject(javaClass.getResource("/jw/whut-live/whut_cxxszhxqkb_live.json")!!.readText())
            .getJSONObject("datas").getJSONObject("cxxszhxqkb").getJSONArray("rows")
        val physical = listOf(1,2,3,4,5,8,9,10,11,12,14,15,16).withIndex().associate { it.value to it.index + 1 }
        val expected = (0 until raw.length()).flatMap { index ->
            val r = raw.getJSONObject(index)
            val first = physical.getValue(r.getInt("KSJC"))
            val last = physical.getValue(r.getInt("JSJC"))
            r.getString("SKZC").mapIndexedNotNull { w, bit -> (w + 1).takeIf { bit == '1' } }.flatMap { w ->
                (first..last).map { n -> "${r.getString("KCM")}|${(r.opt("SKJS") as? String).orEmpty()}|${(r.opt("JASMC") as? String).orEmpty()}|${r.getInt("SKXQ")}|$n|$w" }
            }
        }.sorted()
        for (name in listOf("dynamic", "fallback")) {
            val source = javaClass.getResource("/jw/fixtures/whut-collector/$name.json")!!.readText()
            val rows = JwImportViewModel.parseHtmlStatic(source, "whut").first
            assertEquals(name, expected, occurrences(rows))
            val art = rows.filter { it.name.startsWith("世界美术欣赏") }
            assertFalse(art.isEmpty())
            assertTrue(art.all { it.startNode == 11 && it.endNode == 13 })
            val snapshot = JwImportDraftSnapshot(JwSchoolInfo("W", "武汉理工"), rows, emptyList())
            assertEquals(snapshot, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(snapshot)))
        }
    }

    @Test fun `dynamic section mapping is applied once at the Kotlin boundary`() {
        val source = """{"sleepyWhutSectionMap":{"14":12,"15":13},"datas":{"cxxszhxqkb":{"rows":[
            {"KCM":"晚课","SKJS":"张","JASMC":"A","SKXQ":3,"KSJC":14,"JSJC":15,"SKZC":"101"}
        ]}}}"""
        val rows = JwImportViewModel.parseHtmlStatic(source, "whut").first
        assertFalse(rows.isEmpty())
        assertTrue(rows.all { it.startNode == 12 && it.endNode == 13 })
        val invalid = JSONObject(source).put("sleepyWhutSectionMap", JSONObject().put("14", -1).put("15", 2.5)).toString()
        assertTrue(JwImportViewModel.parseHtmlStatic(invalid, "whut").first.all { it.startNode == 11 && it.endNode == 12 })
    }
}
