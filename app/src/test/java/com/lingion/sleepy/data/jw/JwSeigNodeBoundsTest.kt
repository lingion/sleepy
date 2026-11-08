package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwSeigNodeBoundsTest {
    @Test fun sixteenthPairKeepsSundaySparseWeeksAndParenthesizedTitle() {
        val source = table(row().repeat(15) + row("程序设计(实验)(A 王老师 1周 3周 7周 [B201])", 7))
        assertEquals(listOf(
            JwCourse("程序设计(实验)", "B201", "王老师", 7, 31, 32, 1, 3, 1),
            JwCourse("程序设计(实验)", "B201", "王老师", 7, 31, 32, 7, 7),
        ), JwSeigParser(source).generateCourseList())
    }

    @Test fun resourceGuardKeepsTheLastCompletePairAndIndependentLowCourse() {
        val source = table(row("正确(A 陈老师 2周 [C])", 1) + row().repeat(181) +
            row("边界课(A 王老师 4周 [A])", 2) + row("越界课(A 李老师 6周 [B])", 3))
        assertEquals(listOf(JwCourse("正确", "C", "陈老师", 1, 1, 2, 2, 2),
            JwCourse("边界课", "A", "王老师", 2, 365, 366, 4, 4)), JwSeigParser(source).generateCourseList())
    }

    @Test fun invalidCellsAndBlankRowsDoNotShiftLaterHighPairs() {
        val source = table(row().repeat(15) + row("坏课(A 李老师 2147483647周 [A])", 1) +
            row("正确(B 王老师 9周 [B])", 2))
        assertEquals(listOf(JwCourse("正确", "B", "王老师", 2, 33, 34, 9, 9)), JwSeigParser(source).generateCourseList())
    }

    @Test fun unsupportedMergedCellsStillInvalidateThePositionalForm() {
        val source = table(row().repeat(15) + row("正确(A 王老师 9周 [A])", 1)).replace("<td>标签", "<td rowspan='2'>标签")
        assertEquals(emptyList<JwCourse>(), JwSeigParser(source).generateCourseList())
    }

    private fun table(rows: String) = "<table><tr><td>节次</td>" +
        listOf("一", "二", "三", "四", "五", "六", "日").joinToString("") { "<td>星期$it</td>" } + "</tr>$rows</table>"
    private fun row(course: String = "", day: Int = 0) = (1..7).joinToString("", "<tr><td>标签</td>", "</tr>") {
        "<td>${if (it == day) course else ""}</td>"
    }
}
