package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class JwSourceTableNameTest {
    private val school = "武汉船舶职业技术学院"
    private fun classes(vararg values: String) = "<table><tr>" + values.joinToString("") {
        "<td data-role='item'><div class='mtt_arrange_item'><div class='mtt_item_bjmc'>$it</div></div></td>"
    } + "</tr></table>"

    @Test fun wistKeepsItsDeclaredTwentyWeeksEvenWithNoLateCourses() {
        assertEquals(20, JwSourceCalendar.resolveWeekCount("<table></table>", "wist", school))
        assertEquals("武船课表", JwSourceTableName.resolve("<table></table>", "wist", school))
    }
    @Test fun wistNameUsesDistinctSortedClassNames() {
        assertEquals("武船A班,B班课表", JwSourceTableName.resolve(classes("B班", "A班", "B班", " "), "wist", school))
    }
    @Test fun wistMetadataDoesNotChangeOtherSchoolOrProtocol() {
        for ((type, name) in listOf("wist" to "其他学校", "wisedu" to school, "jz" to school)) {
            assertEquals(0, JwSourceCalendar.resolveWeekCount(classes("A"), type, name))
            assertNull(JwSourceTableName.resolve(classes("A"), type, name))
        }
    }
    @Test fun shanghaiQuarterNameIsAvailableToTheImportFlow() {
        val page = "<a class='gologin-btn'><span class='el-link--inner'>2026-2027学年秋季学期</span></a>"
        assertEquals("26-27秋", JwSourceTableName.resolve(page, "shu2024", "上海大学"))
        assertNull(JwSourceTableName.resolve(page, "unknown", "上海大学"))
    }
    @Test fun editedDraftNameAndSemesterLengthRemainUserValues() {
        val draft = JwImportDraftSnapshot(JwSchoolInfo("W", school, type="wist"), emptyList(), emptyList(),
            tableName="我的实验课表", termWeekCount=20)
        val restored = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!
        assertEquals("我的实验课表", restored.tableName)
        assertEquals(20, restored.termWeekCount)
    }
}
