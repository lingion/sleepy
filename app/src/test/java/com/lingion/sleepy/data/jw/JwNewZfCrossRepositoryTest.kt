package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic negative controls for the kbList contracts read in the pinned student repositories. */
class JwNewZfCrossRepositoryTest {
    private fun row() = JSONObject().put("kcmc", "离散数学").put("xm", "教师甲")
        .put("cdmc", "教学楼301").put("xqj", 3).put("jcs", "3-4").put("zcd", "1,3,7-9周")
    private fun parse(row: JSONObject): List<JwCourse> = JwNewZfParser(
        JSONObject().put("kbList", JSONArray().put(row)).toString()).generateCourseList()

    @Test fun `invalid weekdays are never clamped into Monday or Sunday`() {
        for (day in listOf(-1, 0, 8, 367)) assertTrue("weekday $day", parse(row().put("xqj", day)).isEmpty())
    }

    @Test fun `malformed explicit weekday cannot be repaired using a text alias`() {
        for (day in listOf("broken", "3.5", "null")) {
            assertTrue(day, parse(row().put("xqj", day).put("xqjmc", "星期三")).isEmpty())
        }
    }

    @Test fun `missing or unrecognized weeks never manufacture sixteen weeks`() {
        val missing = row().apply { remove("zcd") }
        assertTrue(parse(missing).isEmpty())
        for (weeks in listOf("", "garbage", "1-3周,garbage", "第8garbage周", "0-4周", "9-2周", "367周")) {
            assertTrue(weeks, parse(row().put("zcd", weeks)).isEmpty())
        }
    }

    @Test fun `bitmap uses the declared resource bound without inventing a tail`() {
        assertTrue(parse(row().put("zcd", "0".repeat(366) + "1")).isEmpty())
        assertEquals(listOf(366), parse(row().put("zcd", "0".repeat(365) + "1")).map { it.startWeek })
    }

    @Test fun `valid separated ranges retain every field and their own parity`() {
        val courses = parse(row().put("zcd", "1-4周(双),7-11周(单),14周"))
        assertEquals(3, courses.size)
        assertEquals(setOf(2,4,7,9,11,14), courses.flatMap { course ->
            (course.startWeek..course.endWeek).filter { course.type == 0 || it % 2 == course.type % 2 }
        }.toSet())
        assertTrue(courses.all { it.name == "离散数学" && it.teacher == "教师甲" && it.room == "教学楼301" &&
            it.day == 3 && it.startNode == 3 && it.endNode == 4 })
    }

    @Test fun `text weekday fallback remains available only when numeric fields are absent`() {
        assertEquals(7, parse(row().apply { remove("xqj"); put("xqjmc", "周天") }).first().day)
        assertTrue(parse(row().apply { remove("xqj"); put("xqjmc", "坏一字段") }).isEmpty())
    }
}
