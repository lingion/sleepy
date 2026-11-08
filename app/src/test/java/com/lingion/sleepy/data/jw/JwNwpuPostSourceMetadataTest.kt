package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JwNwpuPostSourceMetadataTest {
    private fun source(vararg times: String, term: String = "2026-2027秋"): String = """
        <select id="xq"><option>2025-2026春</option><option selected>$term</option></select>
        <table id="sample-table-1"><thead><tr><th>课程名称</th><th>上课时间</th></tr></thead>
        <tbody>${times.joinToString("") { "<tr><td>数学分析2</td><td>$it</td></tr>" }}</tbody></table>
    """.trimIndent()

    private fun periods(html: String) = JwSourcePeriods.resolve(html, "nwpu_post", "西北工业大学 - 研究生", emptyList())

    @Test fun changanDefaultRetainsAllThirteenPeriods() {
        val actual = periods(source("长安-教一(第1-7周 星期一上1节)"))
        val expected = "08:30-09:15 09:25-10:10 10:30-11:15 11:25-12:10 12:20-13:05 13:05-13:50 14:00-14:45 14:55-15:40 16:00-16:45 16:55-17:40 19:00-19:45 19:55-20:40 20:40-21:25"
            .split(' ').mapIndexed { index, value -> Triple(index + 1, value.substringBefore('-'), value.substringAfter('-')) }
        assertEquals(expected, actual)
    }

    @Test fun campusVotesCountRowsAndPreferChanganOnTies() {
        val expected = Triple(1, "08:30", "09:15")
        assertEquals(expected, periods(source("长安", "友谊")).firstOrNull())
        assertEquals(expected, periods(source("长安<br>友谊<br>友谊")).firstOrNull())
        assertEquals(expected, periods(source("无校区")).firstOrNull())
    }

    @Test fun campusTextOutsideTimeColumnDoesNotVote() {
        val html = source("长安").replace("数学分析2", "友谊友谊友谊") + "<p>友谊</p>"
        assertEquals(Triple(1, "08:30", "09:15"), periods(html).firstOrNull())
    }

    @Test fun youyiSeasonUsesCurrentMonthWithBothBoundaries() {
        val html = source("友谊", "友谊", "长安")
        val winter = "08:00-08:50 09:00-09:50 10:10-11:00 11:10-12:00 12:10-13:00 13:10-14:00 14:00-14:50 15:00-15:50 16:10-17:00 17:10-18:00 19:00-19:50 20:00-20:50 20:50-21:25"
        val summer = "08:00-08:50 09:00-09:50 10:10-11:00 11:10-12:00 12:10-13:10 13:20-14:20 14:30-15:20 15:30-16:20 16:40-17:30 17:40-18:30 19:30-20:20 20:30-21:20 20:40-21:25"
        for (month in 1..12) {
            val expected = (if (month in 5..9) summer else winter).split(' ').mapIndexed { index, value ->
                Triple(index + 1, value.substringBefore('-'), value.substringAfter('-'))
            }
            assertEquals("month=$month", expected, JwNwpuPostSourceMetadata.periods(html, month))
        }
    }

    @Test fun multipleLinesInOneCellCannotOutvoteMoreCourseRows() {
        val html = source("友谊<br>友谊<br>友谊", "长安", "长安")
        assertEquals(Triple(1, "08:30", "09:15"), JwNwpuPostSourceMetadata.periods(html, 5).firstOrNull())
    }

    @Test fun selectedTermSeedsTheImportedTableName() {
        assertEquals("西工大研 2026-2027秋", JwSourceTableName.resolve(source("长安"), "nwpu_post", "西北工业大学"))
        assertEquals("西工大研 2027春", JwSourceTableName.resolve(source("友谊", term = "2027春"), "nwpu_post", "西北工业大学"))
    }

    @Test fun unrelatedPageHasNoSourceMetadata() {
        assertTrue(periods("<p>长安 友谊</p>").isEmpty())
        assertNull(JwSourceTableName.resolve("<select id='xq'><option selected>2026秋</option></select>", "nwpu_post", "西北工业大学"))
        assertNull(JwSourceTableName.resolve(source("长安").replace(" selected", ""), "nwpu_post", "西北工业大学"))
    }

    @Test fun capturedClockAlwaysOverridesDefaults() {
        val captured = listOf(Triple(1, "09:01", "09:46"))
        assertEquals(captured, JwSourcePeriods.resolve(source("友谊"), "nwpu_post", "西北工业大学", captured))
    }
}
