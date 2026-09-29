package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import org.junit.Test
import org.junit.Assert.*

/**
 * issue#55 后续: ICS 导出→导入往返也必须保真非标准时间课程。
 *
 * 导出端本就写真实时间到 DTSTART/DTEND, 且 DESCRIPTION 末尾追加 "\nHH:mm-HH:mm"
 * 时间行(Sleepy 指纹)。导入端此前不读该行 → 往返后 ownTime=true 的 20:50-22:00
 * 退化回按节次定位。修: DESCRIPTION 解析出该行 → 恢复 ownTime;
 * WakeUp 原生 ICS 无此行, 保持 ownTime=false 兼容。
 */
class Issue55IcsOwnTimeRoundTripTest {

    private fun table() = TimeTableEntity(
        id = 1, name = "Test55Ics", startDate = "2026-09-07",
        maxWeek = 20, nodesPerDay = 12,
        timeJson = """[{"node":1,"start":"08:00","end":"08:45"},{"node":11,"start":"20:50","end":"21:35"},{"node":12,"start":"21:45","end":"22:30"}]""",
        color = "#FF6750A4", isDefault = true
    )

    private fun ownTimeCourse() = CourseEntity(
        id = 0, groupId = "", tableId = 1,
        courseName = "OwnTimeCourse", teacher = "Prof X", room = "B201",
        day = 2, startNode = 11, step = 2,
        startWeek = 1, endWeek = 16, type = 0,
        color = "#FF6750A4",
        ownTime = true, startTime = "20:50", endTime = "22:00"
    )

    @Test
    fun ics_roundTrip_preservesOwnTime() {
        val exported = ScheduleExporter.exportIcs(table(), listOf(ownTimeCourse()))
        // 导出端指纹: DESCRIPTION 里有时间行
        assertTrue("导出 DESCRIPTION 必须含 20:50-22:00 时间行",
            exported.contains("20:50-22:00"))

        val result = ScheduleParser.parse(exported, defaultTableId = 999L)
        assertTrue("Parse should succeed, got: ${result.exceptionOrNull()}", result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals(1, parsed.courses.size)
        val c = parsed.courses[0]
        assertTrue("ownTime 必须恢复; got ownTime=${c.ownTime} startTime=${c.startTime} endTime=${c.endTime}",
            c.ownTime)
        assertEquals("20:50", c.startTime)
        assertEquals("22:00", c.endTime)
        // 节次定位仍保留(网格布局兜底用)
        assertEquals(11, c.startNode)
        assertEquals(2, c.step)
    }

    @Test
    fun ics_wakeUpNative_withoutTimeLine_staysFalse() {
        // WakeUp 原生 ICS: DESCRIPTION="第X - Y节\n教室\n教师", 无 Sleepy 时间行
        val wakeUpIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//WakeUpSchedule//Course//CN
            BEGIN:VEVENT
            UID:1@wakeup
            DTSTAMP:20260922T000000Z
            DTSTART:20260908T205000
            DTEND:20260908T220000
            RRULE:FREQ=WEEKLY;BYDAY=TU;UNTIL=20261222T235959Z
            SUMMARY:WakeUpCourse
            LOCATION:教室W
            DESCRIPTION:第11 - 12节\n教室W\n王老师
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val result = ScheduleParser.parse(wakeUpIcs, defaultTableId = 999L)
        assertTrue("Parse should succeed, got: ${result.exceptionOrNull()}", result.isSuccess)
        val c = result.getOrThrow().courses[0]
        assertFalse("无 Sleepy 时间行时 ownTime 必须保持 false", c.ownTime)
        assertEquals("", c.startTime)
        assertEquals("", c.endTime)
        assertEquals("王老师", c.teacher)
    }
}
