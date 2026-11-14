package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.TimeTableEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class JwSchoolSourceRoundTripTest {
    private val schoolSource = """{"schemaVersion":1,"sourceType":"jw","school":{"sortKey":"H","name":"哈尔滨示例大学","url":"https://jw.example.edu.cn/","type":"wisedu","catalogId":"example-university-undergraduate"}}"""

    @Test
    fun `Sleepy native format preserves JW school source`() {
        val exported = SleepyNativeExporter.exportFile(
            tableName = "测试课表",
            startDate = "2026-09-07",
            maxWeek = 20,
            nodesPerDay = 12,
            timeJson = "",
            courses = emptyList(),
            jwSchoolSourceJson = schoolSource,
        )

        assertEquals(schoolSource, ScheduleParser.parse(exported, 1L).getOrThrow().jwSchoolSourceJson)
    }

    @Test
    fun `Sleepy JSON export preserves JW school source`() {
        val table = TimeTableEntity(
            name = "测试课表",
            startDate = "2026-09-07",
            jwSchoolSourceJson = schoolSource,
        )

        val exported = ScheduleExporter.exportWakeUpJson(table, emptyList())

        assertEquals(schoolSource, ScheduleParser.parse(exported, 1L).getOrThrow().jwSchoolSourceJson)
    }
}
