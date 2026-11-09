package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic positional pages; blank cells and rows still occupy their source coordinates. */
class JwUndergradLegacyNodeBoundsTest {
    @Test fun jnuKeepsHighAdjacentCellsAndTheirOwnRooms() {
        val source = jnu((1..33).joinToString("") { node ->
            when (node) {
                1 -> jnuCell("低节课", "L")
                31, 32 -> jnuCell("离散数学", "A")
                33 -> jnuCell("离散数学", "B")
                else -> "<td></td>"
            }
        })
        assertEquals(listOf(
            JwCourse("低节课", "L", "", 1, 1, 1, 2, 10, 2),
            JwCourse("离散数学", "A", "", 1, 31, 32, 2, 10, 2),
            JwCourse("离散数学", "B", "", 1, 33, 33, 2, 10, 2),
        ), JwUndergradLegacyParser(source, "jnu").generateCourseList())
    }

    @Test fun jnuResourceGuardDoesNotMergeAnUnsupportedCellIntoTheLastGoodCell() {
        val source = jnu("<td></td>".repeat(365) + jnuCell("边界课", "A").repeat(2))
        assertEquals(listOf(JwCourse("边界课", "A", "", 1, 366, 366, 2, 10, 2)),
            JwUndergradLegacyParser(source, "jnu").generateCourseList())
    }

    @Test fun swustKeepsPhysicalRowsAndSundayAfterThirtyEmptyRows() {
        val source = swust((1..32).joinToString("") { node ->
            when (node) {
                1 -> swustRow(1, "低节课", "L")
                31 -> swustRow(7, "离散数学", "A")
                32 -> swustRow(1, "物理", "B")
                else -> swustRow()
            }
        })
        assertEquals(listOf(
            JwCourse("低节课", "L", "", 1, 1, 1, 1, 8),
            JwCourse("离散数学", "A", "", 7, 31, 31, 1, 8),
            JwCourse("物理", "B", "", 1, 32, 32, 1, 8),
        ), JwUndergradLegacyParser(source, "swust").generateCourseList())
    }

    @Test fun swustResourceGuardKeepsTheLastSupportedRowOnly() {
        val source = swust(swustRow().repeat(365) + swustRow(2, "边界课", "A").repeat(2))
        assertEquals(listOf(JwCourse("边界课", "A", "", 2, 366, 366, 1, 8)),
            JwUndergradLegacyParser(source, "swust").generateCourseList())
    }

    @Test fun bjtuLegacyKeepsSingleNodesAndEmptyWeekdayCells() {
        val source = bjtu((1..32).joinToString("") { node ->
            when (node) {
                1 -> bjtuRow(2, "低节课")
                31 -> bjtuRow(7, "离散数学")
                32 -> bjtuRow(1, "物理")
                else -> bjtuRow()
            }
        })
        assertEquals(listOf(
            JwCourse("低节课", "AB123", "王老师", 2, 1, 1, 1, 8),
            JwCourse("离散数学", "AB123", "王老师", 7, 31, 31, 1, 8),
            JwCourse("物理", "AB123", "王老师", 1, 32, 32, 1, 8),
        ), JwUndergradLegacyParser(source, "bjtu").generateCourseList())
    }

    @Test fun bjtuLegacyResourceGuardDoesNotDiscardAnIndependentGoodRow() {
        val source = bjtu(bjtuRow(1, "正确") + bjtuRow().repeat(364) + bjtuRow(7, "边界课") + bjtuRow(1, "过界"))
        assertEquals(listOf(
            JwCourse("正确", "AB123", "王老师", 1, 1, 1, 1, 8),
            JwCourse("边界课", "AB123", "王老师", 7, 366, 366, 1, 8),
        ), JwUndergradLegacyParser(source, "bjtu").generateCourseList())
    }

    private fun jnu(cells: String) = "<div id='oReportCell'><table class='a8'>" +
        "<tr><td>表头</td></tr>".repeat(3) + "<tr><td>星期一</td>$cells</tr>" +
        "<tr><td></td></tr>".repeat(6) + "</table></div>"

    private fun jnuCell(name: String, room: String) = "<td><div>课程：$name(理论) 从第2至10 双周 $room</div></td>"
    private fun swust(rows: String) = "<table class='UICourseTable'><tbody>$rows</tbody></table>"
    private fun swustRow(day: Int = 0, name: String = "", room: String = "") =
        (1..7).joinToString("", "<tr><td>时段</td>", "</tr>") { column ->
            if (day == column) "<td><div class='lecture'><span class='course'>$name</span>" +
                "<span class='place'>$room</span><span class='week'>1-8(周)</span></div></td>" else "<td></td>"
        }

    private fun bjtu(rows: String) = "<table class='table table-bordered'><tr><th>课表</th></tr>$rows</table>"
    private fun bjtuRow(day: Int = 0, name: String = "") = (1..7).joinToString("", "<tr><td>节次</td>", "</tr>") {
        if (it == day) "<td>ABCDEFG [01] $name [1] 第01-08周 王老师 AB123</td>" else "<td></td>"
    }
}
