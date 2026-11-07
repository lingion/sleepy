package com.lingion.sleepy.data.jw

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic protocol vectors; these are not authenticated school captures. */
class JwActualTimeImportTest {
    private fun draft(courses: List<JwCourse>) = JwImportDraftSnapshot(
        school = JwSchoolInfo(sortKey = "T", name = "时间回归"),
        courses = courses, periods = emptyList(),
    )

    @Test fun `HFU schedule keeps actual clock times through draft and entity mapping`() {
        val courses = JwCumtbParser("""{"result":{
          "lessonList":[{"id":7,"courseName":"实验"}],
          "scheduleList":[{"lessonId":7,"weekday":3,"weekIndex":5,
            "startTime":815,"endTime":950,"personName":"王老师","room":{"nameZh":"A101"}}]
        }}""").generateCourseList()
        val restored = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft(courses)))!!
        val vm = JwImportViewModel(android.app.Application())
        val entity = vm.toCourseEntities(restored.courses, 9L, "#FF6750A4").single()
        assertEquals("实验", entity.courseName)
        assertEquals("王老师", entity.teacher)
        assertEquals("A101", entity.room)
        assertEquals(3, entity.day)
        assertEquals(5, entity.startWeek)
        assertEquals(5, entity.endWeek)
        assertEquals("08:15", entity.startTime)
        assertEquals("09:50", entity.endTime)
        assertTrue(entity.ownTime)
        assertTrue(entity.isIrregularTime)
    }

    @Test fun `custom time bridge courses survive even without section numbers`() {
        val courses = JwSgAssetParser("""[
          {"name":"晚间实验","teacher":"李老师","position":"实验楼","day":6,
           "isCustomTime":true,"customStartTime":"19:15","customEndTime":"21:05","weeks":[1,5,9]}
        ]""").generateCourseList()
        assertEquals(listOf(1,5,9), courses.map { it.startWeek })
        val json = JSONObject(JwImportDraftCodec.toJson(draft(courses)))
        for (i in 0 until json.getJSONArray("courses").length()) {
            val course = json.getJSONArray("courses").getJSONObject(i)
            assertEquals("19:15", course.optString("startTime"))
            assertEquals("21:05", course.optString("endTime"))
        }
        assertEquals(courses, JwImportDraftCodec.fromJson(json.toString())!!.courses)
    }

    @Test fun `invalid actual clock values are rejected instead of converted into period courses`() {
        for ((start, end) in listOf("25:00" to "26:00", "09:70" to "10:30", "10:30" to "09:30", "09:30" to "")) {
            val json = JSONObject(JwImportDraftCodec.toJson(draft(listOf(JwCourse("课", day=1,
                startNode=1, endNode=2, startWeek=1, endWeek=2)))))
            json.getJSONArray("courses").getJSONObject(0).put("startTime", start).put("endTime", end)
            assertNull("$start-$end", JwImportDraftCodec.fromJson(json.toString()))
        }
    }
}
