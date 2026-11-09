package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class UndergradRoundThreeHitTest {
    private fun parse(source: String): List<JwCourse> {
        val type = "hit"
        val courses = JwParserRegistry.parserFor(type, source).generateCourseList()
        assertEquals(courses, JwParserRegistry.selectBest(source, type).first)
        assertEquals(courses, JwImportViewModel.parseHtmlStatic(source, type).first)
        assertDeclaredHtmlFrame(type, source, courses)
        val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "协议回归", type = type), courses, emptyList())
        assertEquals(courses, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!.courses)
        return courses
    }

    @Test fun hitPreservesMultipleTeachersLocalParityAndSparseWeeks() {
        val source = hitTable("第1-2节", "数学<br>王老师[1-5单]周A，李老师[2-8双]周B<br>英语<br>陈老师[1,3,7]周C")
        assertEquals(listOf(
            JwCourse("数学", "A", "王老师", 1, 1, 2, 1, 5, 1), JwCourse("数学", "B", "李老师", 1, 1, 2, 2, 8, 2),
            JwCourse("英语", "C", "陈老师", 1, 1, 2, 1, 1), JwCourse("英语", "C", "陈老师", 1, 1, 2, 3, 3), JwCourse("英语", "C", "陈老师", 1, 1, 2, 7, 7),
        ), parse(source))
    }

    @Test fun hitSupportsEightColumnRowsExplicitNodesAndSameNameContinuation() {
        val source = hitTable("第3,5节", "数学<br>王老师[1-8]周A第9-10节<br>李老师[9-16]周B第9-10节", eightColumns = true)
        assertEquals(listOf(JwCourse("数学", "A", "王老师", 1, 9, 10, 1, 8), JwCourse("数学", "B", "李老师", 1, 9, 10, 9, 16)), parse(source))
        assertEquals(listOf(JwCourse("数学", "A", "王老师", 1, 3, 3, 1, 8), JwCourse("数学", "A", "王老师", 1, 5, 5, 1, 8)),
            parse(hitTable("第3,5节", "数学<br>王老师[1-8]周A", eightColumns = true)))
    }

    @Test fun hitUsesFirstProductiveHeadSegmentAndSecondSchoolAnchor() {
        val source = "<head>metadata</head>" + hitTable("第1-2节", "数学<br>王老师[1-8周]A", anchor = "id='xszp'") +
            "<head>duplicate</head>" + hitTable("第9-10节", "数学<br>王老师[1-8]周A")
        assertEquals(listOf(JwCourse("数学", "A", "王老师", 1, 1, 2, 1, 8)), parse(source))
    }

    @Test fun hitRejectsBadRangesAndNeverBorrowsNameAcrossCells() {
        for ((node, raw) in listOf("第0节" to "数学<br>王老师[1-8]周A", "第1-2节" to "数学<br>王老师[8-1]周A", "第1-2节" to "数学<br>王老师[1-367]周A", "第1-2节" to "王老师[1-8]周A", "第1-2节" to "数学<br>王老师[未知]周A"))
            assertEquals(emptyList<JwCourse>(), parse(hitTable(node, raw)))
        val source = hitTable("第1-2节", "数学<br>王老师[1-8]周A").replaceFirst("<td></td>", "<td>李老师[1-8]周B</td>")
        assertEquals(listOf(JwCourse("数学", "A", "王老师", 1, 1, 2, 1, 8)), parse(source))
    }

    private fun hitTable(nodes: String, card: String, eightColumns: Boolean = false, anchor: String = "class='xfyq_con'") =
        "<table $anchor><tr>" + (if (eightColumns) "" else "<td>上午</td>") + "<td>$nodes</td><td>$card</td>" + "<td></td>".repeat(6) + "</tr></table>"
}
