package com.lingion.sleepy.data.jw

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic bridge payloads based on the MIT shiguang-bridge.d.ts contract. */
class JwSgAssetSourceMetadataTest {
    private val courseJson = """[{"name":"离散数学","teacher":"王老师","position":"教学楼301",
        "day":3,"startSection":5,"endSection":6,"weeks":[1,3,7],"credit":3.5}]"""

    private fun payload(config: String) = JSONObject()
        .put("sleepySgAsset", true).put("coursesJson", courseJson)
        .put("config", JSONObject(config)).toString()

    @Test fun `explicit semester count includes empty weeks and survives draft`() {
        val source = payload("""{"semesterTotalWeeks":24,"semesterStartDate":"2026-09-07"}""")
        val courses = JwSgAssetParser(source).generateCourseList()
        assertEquals(3, courses.size)
        assertTrue(courses.all {
            it.name == "离散数学" && it.teacher == "王老师" && it.room == "教学楼301" &&
                it.day == 3 && it.startNode == 5 && it.endNode == 6 && it.credit == 3.5f
        })
        assertEquals(listOf(1, 3, 7), courses.map { it.startWeek })
        assertTrue(courses.all { it.startWeek == it.endWeek })
        val count = JwSourceCalendar.resolveWeekCount(source, JwProtocol.TYPE_SG_ASSET)
        val date = JwSourceCalendar.resolveStartDate(source, JwProtocol.TYPE_SG_ASSET, "")
        assertEquals(24, count)
        assertEquals("2026-09-07", date)
        val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "桥接回归"), courses, emptyList(),
            termStartDate = date, termWeekCount = count)
        val restored = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!
        assertEquals(draft, restored)
        val entities = JwImportViewModel(android.app.Application()).toCourseEntities(
            restored.courses, 9L, "#FF6750A4")
        assertEquals(listOf(1, 3, 7), entities.map { it.startWeek })
        assertTrue(entities.all { it.courseName == "离散数学" && it.teacher == "王老师" &&
            it.room == "教学楼301" && it.day == 3 && it.credit == 3.5f })
    }

    @Test fun `legacy course arrays retain credits without a config wrapper`() {
        val courses = JwSgAssetParser(courseJson).generateCourseList()
        assertEquals(3, courses.size)
        assertTrue(courses.all { it.credit == 3.5f })
        assertEquals(0, JwSourceCalendar.resolveWeekCount(courseJson, JwProtocol.TYPE_SG_ASSET))
        assertEquals("", JwSourceCalendar.resolveStartDate(courseJson, JwProtocol.TYPE_SG_ASSET, ""))
    }

    @Test fun `invalid or missing counts never manufacture the upstream default`() {
        for (count in listOf("0", "-1", "367", "24.5", "null", "\"bad\"")) {
            val source = payload("""{"semesterTotalWeeks":$count}""")
            assertEquals(count, 0, JwSourceCalendar.resolveWeekCount(source, JwProtocol.TYPE_SG_ASSET))
        }
        assertEquals(0, JwSourceCalendar.resolveWeekCount(payload("{}"), JwProtocol.TYPE_SG_ASSET))
        assertEquals(0, JwSourceCalendar.resolveWeekCount(payload("""{"semesterTotalWeeks":24}"""), "unknown"))
    }

    @Test fun `unmarked objects cannot be interpreted as bridge courses or calendar`() {
        val source = JSONObject(payload("""{"semesterTotalWeeks":24}""")).put("sleepySgAsset", false).toString()
        assertTrue(JwSgAssetParser(source).generateCourseList().isEmpty())
        assertEquals(0, JwSourceCalendar.resolveWeekCount(source, JwProtocol.TYPE_SG_ASSET))
        assertEquals("", JwSourceCalendar.resolveStartDate(source, JwProtocol.TYPE_SG_ASSET, ""))
    }

    @Test fun `explicit null semester date clears a previously captured suggestion`() {
        assertEquals("", JwSourceCalendar.resolveStartDate(payload("""{"semesterStartDate":null}"""),
            JwProtocol.TYPE_SG_ASSET, "2026-09-07"))
        assertEquals("2026-09-07", JwSourceCalendar.resolveStartDate(payload("{}"),
            JwProtocol.TYPE_SG_ASSET, "2026-09-07"))
    }
}
