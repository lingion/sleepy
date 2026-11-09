package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwEcuplSourcePeriodsTest {
    private fun table(vararg times: String) = "<table class='listTable extra'><tr><td>星期</td>" +
        times.joinToString("") { "<td>$it</td>" } + "</tr></table>"

    @Test fun annualTimeColumnsReachTheImportConfirmationResolver() {
        val source = table("8:00 - 8:45", "08:55-09:40", "10:00-10:45") +
            "<table class='listTable'><tr><td>课程名</td><td>教师</td></tr></table>"
        val expected = listOf(Triple(1, "08:00", "08:45"), Triple(2, "08:55", "09:40"),
            Triple(3, "10:00", "10:45"))
        for (type in listOf("ecupl", "ecupl_super")) {
            assertEquals(type, expected, JwSourcePeriods.resolve(source, type, "华东政法大学", emptyList()))
        }
    }

    @Test fun brokenHeaderCannotShiftLaterPeriodNumbersOrBorrowAnotherTable() {
        for (value in listOf("坏时间", "24:00-24:45", "08:60-09:40", "09:00-08:45", "08:00-08:00")) {
            val source = table("08:00-08:45", value, "10:00-10:45") + table("11:00-11:45")
            assertTrue(value, JwSourcePeriods.resolve(source, "ecupl", "", emptyList()).isEmpty())
        }
        val metadata = table("课程名称", "教师") + table("08:00-08:45")
        assertTrue(JwSourcePeriods.resolve(metadata, "ecupl", "", emptyList()).isEmpty())
    }

    @Test fun capturedValuesAndOtherProtocolsKeepTheirOwnMeaning() {
        val source = table("08:00-08:45")
        val captured = listOf(Triple(7, "15:00", "15:45"))
        assertEquals(captured, JwSourcePeriods.resolve(source, "ecupl", "", captured))
        for (type in listOf("classic_eams", "jz", "ecupl_script", "unknown")) {
            assertTrue(type, JwSourcePeriods.resolve(source, type, "", emptyList()).isEmpty())
        }
    }

    @Test fun everyDeclaredTimeColumnKeepsItsOriginalSectionIndex() {
        val source = table(*Array(32) { "08:00-08:45" })
        val actual = JwSourcePeriods.resolve(source, "ecupl", "", emptyList())
        assertEquals((1..32).toList(), actual.map { it.first })
        assertEquals(Triple(32, "08:00", "08:45"), actual.last())
    }
}
