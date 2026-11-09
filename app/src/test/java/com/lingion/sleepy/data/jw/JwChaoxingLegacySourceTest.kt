package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic response shapes from moeshin/ai-schedule-chaoxing at 4650f598.
 * scheduleHtmlProvider.js assigns sections=[djc], weeks=zc, day=xingqi from the
 * top-level sdpkkbList array. djc is one explicit period, never a duration or end.
 */
class JwChaoxingLegacySourceTest {
    private data class Slot(val name: String, val teacher: String, val room: String, val day: Int, val week: Int, val node: Int)

    private fun expand(courses: List<JwCourse>): List<Slot> = courses.flatMap { c ->
        (c.startWeek..c.endWeek).filter { c.type == 0 || it % 2 == c.type % 2 }.flatMap { week ->
            (c.startNode..c.endNode).map { Slot(c.name, c.teacher, c.room, c.day, week, it) }
        }
    }

    private fun assertSlots(source: String, expected: Set<Slot>) {
        val actual = expand(JwChaoxingParser(source).generateCourseList())
        assertEquals(expected, actual.toSet())
        assertEquals("duplicate lesson", actual.toSet().size, actual.size)
    }

    @Test fun `top level legacy array preserves separate week period teacher and campus arrangements`() {
        val source = """[
            {"kcmc":"<a>实验</a>","djc":"3","zc":"1-1,3-3周","xingqi":"2","tmc":"<a>张</a>","croommc":"<a>A101</a>","xqmc":"东校区"},
            {"kcmc":"实验","djc":5,"zc":[2,4],"xingqi":2,"tmc":"李","croommc":"A101","xqmc":"西校区"},
            {"kcmc":"实验","djc":6,"zc":"4周","xingqi":2,"tmc":"李","croommc":"A101","xqmc":"西校区"}
        ]"""
        assertSlots(source, setOf(
            Slot("实验", "张", "A101（东校区）", 2, 1, 3), Slot("实验", "张", "A101（东校区）", 2, 3, 3),
            Slot("实验", "李", "A101（西校区）", 2, 2, 5), Slot("实验", "李", "A101（西校区）", 2, 4, 5),
            Slot("实验", "李", "A101（西校区）", 2, 4, 6),
        ))
    }

    @Test fun `wrapped legacy rows retain range parity separately for each segment`() {
        val source = """{"rows":[{"kcmc":"讨论","djc":7,"zc":"1-6(单),8-12(双)周","xingqi":7,"tmc":"师","croommc":"室"}]}"""
        assertSlots(source, setOf(1, 3, 5, 8, 10, 12).map { Slot("讨论", "师", "室", 7, it, 7) }.toSet())
    }

    @Test fun `djc remains one period and never becomes a count or end node`() {
        val source = """[{"kcmc":"单节","djc":13,"zc":"2","xingqi":1,"tmc":"师","croommc":"室","continuingSession":2}]"""
        assertEquals(listOf(JwCourse("单节", "室", "师", 1, 13, 13, 2, 2)), JwChaoxingParser(source).generateCourseList())
    }

    @Test fun `modern fields win when both documented row shapes are present`() {
        val source = """{"rows":[{"kcmc":"优先级","xjc":"4","djc":9,"rqxl":609,"zcstr":"2,4","zc":"1-16周","xingqi":3,"tmc":"师","croommc":"室"}]}"""
        assertEquals(listOf(JwCourse("优先级", "室", "师", 3, 4, 4, 2, 4, 2)), JwChaoxingParser(source).generateCourseList())
    }

    @Test fun `invalid explicit legacy arrangements do not invent coordinates or partial weeks`() {
        for ((node, weeks, day) in listOf(
            Triple("0", "\"1周\"", "1"), Triple("-1", "\"1周\"", "1"),
            Triple("367", "\"1周\"", "1"), Triple("\"3-4\"", "\"1周\"", "1"),
            Triple("3", "\"1周\"", "0"), Triple("3", "\"1周\"", "8"),
            Triple("3", "\"1,garbage\"", "1"), Triple("3", "\"3-1周\"", "1"),
            Triple("3", "\"0-3周\"", "1"), Triple("3", "\"1-367周\"", "1"),
            Triple("3", "[1,0]", "1"), Triple("3", "[1,\"bad\"]", "1"),
            Triple("3", "[]", "1"), Triple("3", "null", "1")
        )) {
            val source = """[{"kcmc":"坏安排","djc":$node,"zc":$weeks,"xingqi":$day}]"""
            assertEquals(source, emptyList<JwCourse>(), JwChaoxingParser(source).generateCourseList())
        }
    }

    @Test fun `malformed explicit primary coordinates cannot be rescued by fallback fields`() {
        for (fields in listOf(
            "\"xingqi\":\"bad\",\"rqxl\":103,\"xjc\":3,\"zcstr\":\"1\"",
            "\"xingqi\":1,\"xjc\":\"bad\",\"djc\":3,\"rqxl\":103,\"zcstr\":\"1\"",
            "\"xingqi\":1,\"djc\":\"bad\",\"rqxl\":103,\"zc\":\"1\"",
            "\"xingqi\":1,\"xjc\":3,\"zcstr\":\"bad\",\"zc\":\"1\""
        )) {
            val source = """{"rows":[{"kcmc":"坏安排",$fields}]}"""
            assertEquals(fields, emptyList<JwCourse>(), JwChaoxingParser(source).generateCourseList())
        }
    }
}
