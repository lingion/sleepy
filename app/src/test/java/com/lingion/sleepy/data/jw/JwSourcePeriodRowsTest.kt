package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwSourcePeriodRowsTest {
    @Test fun nwpuThirteenPeriodsSurviveWhenCoursesEndAtFourthPeriod() {
        val source = """
            <table id="sample-table-1"><thead><tr><th>课程名称</th><th>上课时间</th></tr></thead>
            <tbody><tr><td>数学分析</td><td>长安 第1-8周 星期一上3-4节</td></tr></tbody></table>
        """.trimIndent()
        val declared = JwSourcePeriods.resolve(source, JwProtocol.TYPE_NWPU_POST, "西北工业大学 - 研究生", emptyList())

        val rows = JwSourcePeriods.confirmationRows(4, declared)

        assertEquals(13, rows.size)
        assertEquals(declared, rows)
        assertEquals(Triple(13, "20:40", "21:25"), rows.last())
    }

    @Test fun capturedTailUsesItsNodeNumberRatherThanNumberOfCapturedRows() {
        val captured = listOf(Triple(1, "08:00", "08:45"), Triple(13, "20:40", "21:25"))

        val rows = JwSourcePeriods.confirmationRows(2, captured)

        assertEquals((1..13).toList(), rows.map { it.first })
        assertEquals(Triple(2, "", ""), rows[1])
        assertEquals(captured.last(), rows.last())
    }

    @Test fun laterCourseStillGetsAnEditableRowWithoutFabricatedTime() {
        val rows = JwSourcePeriods.confirmationRows(4, listOf(Triple(1, "08:00", "08:45")))

        assertEquals((1..4).toList(), rows.map { it.first })
        assertEquals(Triple(1, "08:00", "08:45"), rows.first())
        assertEquals(Triple(4, "", ""), rows.last())
    }

    @Test fun noSourceClocksRetainOnlyCourseRange() {
        assertEquals(listOf(Triple(1, "", ""), Triple(2, "", "")),
            JwSourcePeriods.confirmationRows(2, emptyList()))
        assertTrue(JwSourcePeriods.confirmationRows(0, emptyList()).isEmpty())
    }

    @Test fun sourceOrderCannotChangePrintedNodeOrder() {
        val rows = JwSourcePeriods.confirmationRows(1,
            listOf(Triple(3, "10:00", "10:45"), Triple(1, "08:00", "08:45")))

        assertEquals(listOf(Triple(1, "08:00", "08:45"), Triple(2, "", ""), Triple(3, "10:00", "10:45")), rows)
    }
}
