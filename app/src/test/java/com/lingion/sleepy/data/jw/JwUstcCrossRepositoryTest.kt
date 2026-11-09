package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

/** Synthetic field vectors, checked against pyustc 50174b8; no student session. */
class JwUstcCrossRepositoryTest {
    private fun source(fields: String) = """{"studentTableVm":{"activities":[{
        "courseName":"拓扑学","weekday":3,"weeksStr":"1-5,9-13(单)周",
        "lessonCode":"0708","startUnit":3,"endUnit":4,
        "startDate":"09:45","endDate":"11:20","room":null,"customPlace":"线上教室",
        "teacherDeepVms":[{"person":{"nameZh":"王老师"}},{"person":{"nameZh":"李老师"}}],
        "credits":2.5,"lessonRemark":"研讨课"$fields
    }]}}"""

    @Test fun `explicit units win over numeric lesson code and retain clock metadata`() {
        val courses = JwUstcParser(source("")).generateCourseList()
        assertEquals(listOf(Triple(1,5,0), Triple(9,13,1)), courses.map { Triple(it.startWeek,it.endWeek,it.type) })
        courses.forEach {
            assertEquals("拓扑学", it.name)
            assertEquals("王老师 李老师", it.teacher)
            assertEquals("线上教室", it.room)
            assertEquals(3, it.day)
            assertEquals(3, it.startNode)
            assertEquals(4, it.endNode)
            assertEquals("09:45", it.startTime)
            assertEquals("11:20", it.endTime)
            assertEquals(2.5f, it.credit)
            assertTrue(it.note.contains("0708"))
        }
    }

    @Test fun `lesson code alone never supplies period numbers`() {
        val text = source("").replace("\"startUnit\":3,\"endUnit\":4,", "")
        val courses = JwUstcParser(text).generateCourseList()
        assertFalse(courses.isEmpty())
        assertTrue(courses.all { it.hasActualTime })
        assertEquals(courses, JwUstcParser(text.replace("0708", "9999")).generateCourseList().map {
            it.copy(note = it.note.replace("9999", "0708"))
        })
        assertTrue(JwUstcParser(text.replace("\"startDate\":\"09:45\",\"endDate\":\"11:20\",", ""))
            .generateCourseList().isEmpty())
    }

    @Test fun `malformed explicit units are not repaired using course code or clock`() {
        for ((start,end) in listOf("0" to "4", "5" to "4", "3" to "\"invalid\"", "3" to "367")) {
            val text = source("").replace("\"startUnit\":3,\"endUnit\":4", "\"startUnit\":$start,\"endUnit\":$end")
            assertTrue("$start-$end", JwUstcParser(text).generateCourseList().isEmpty())
        }
    }

    @Test fun `each week segment retains its own parity and rejects malformed suffix`() {
        val text = source("").replace("1-5,9-13(单)周", "1-4(双)周,7-11(单)周,14周")
        assertEquals(listOf(Triple(2,4,2),Triple(7,11,1),Triple(14,14,0)),
            JwUstcParser(text).generateCourseList().map { Triple(it.startWeek,it.endWeek,it.type) })
        for (bad in listOf("8-2周", "3-invalid", "0-4周", "1-3周,garbage", "")) {
            assertTrue(bad, JwUstcParser(text.replace("1-4(双)周,7-11(单)周,14周",bad)).generateCourseList().isEmpty())
        }
    }

    @Test fun `all explicit high periods survive without interpreting lesson code`() {
        val text = source("").replace("\"startUnit\":3,\"endUnit\":4", "\"startUnit\":31,\"endUnit\":32")
        val courses = JwUstcParser(text).generateCourseList()
        assertEquals(2, courses.size)
        assertTrue(courses.all { it.startNode == 31 && it.endNode == 32 })
    }

    @Test fun `EAMS print data supports nested teachers custom place and actual clock`() {
        val text = source("").replace("\"weeksStr\":\"1-5,9-13(单)周\"", "\"weekIndexes\":[2,6,10]")
        val courses = JwEams5Parser(text).generateCourseList()
        assertEquals(listOf(2,6,10), courses.map { it.startWeek })
        courses.forEach {
            assertEquals("王老师/李老师", it.teacher)
            assertEquals("线上教室", it.room)
            assertEquals("09:45", it.startTime)
            assertEquals("11:20", it.endTime)
        }
    }

    @Test fun `EAMS calendar dates do not replace an explicit period schedule with clock times`() {
        val text = source("").replace("\"weeksStr\":\"1-5,9-13(单)周\"", "\"weekIndexes\":[2]")
            .replace("09:45", "2026-09-07").replace("11:20", "2026-12-18")
        val course = JwEams5Parser(text).generateCourseList().single()
        assertEquals(3, course.startNode)
        assertEquals(4, course.endNode)
        assertEquals("", course.startTime)
        assertEquals("", course.endTime)
    }

    @Test fun `actual USTC clock survives draft and entity conversion`() {
        val courses = JwUstcParser(source("")).generateCourseList()
        val draft = JwImportDraftSnapshot(JwSchoolInfo("Z","中国科学技术大学",type=JwProtocol.TYPE_USTC), courses, emptyList())
        val restored = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!
        assertEquals(courses, restored.courses)
        val entity = JwImportViewModel(android.app.Application()).toCourseEntities(restored.courses, 1L, "#FF6750A4").first()
        assertEquals("09:45", entity.startTime)
        assertEquals("11:20", entity.endTime)
        assertTrue(entity.ownTime)
        assertTrue(entity.isIrregularTime)
    }
}
