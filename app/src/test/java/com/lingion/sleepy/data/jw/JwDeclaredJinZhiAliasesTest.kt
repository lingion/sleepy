package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwDeclaredJinZhiAliasesTest {
    private fun verify(type: String, source: String, expected: List<JwCourse>) {
        assertEquals(expected, JwParserRegistry.parserFor(type, source).generateCourseList())
        assertEquals(expected, JwParserRegistry.selectBest(source, type).first)
        assertEquals(expected, JwImportViewModel.parseHtmlStatic(source, type).first)
        assertDeclaredHtmlFrame(type, source, expected)
        val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "声明协议回归", type = type), expected, emptyList())
        assertEquals(expected, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!.courses)
    }

    @Test fun jzOneReadsOnlyCourseFormTable() {
        val source = "<table id='CourseFormTable'><tr><td>数学<br>1-3周<br>第3-4节<br>王老师<br>A</td></tr></table>"
        verify("jz_1", source, listOf(JwCourse("数学", "A", "王老师", 1, 3, 4, 1, 3)))
        verify("jz_x", source, emptyList())
    }

    @Test fun jzXKeepsOwnFieldOrder() {
        val source = "<table class='wut_tablex'><tr><td data-role='course' data-week='4'><div><i></i><b>《材料》</b><p>王老师&nbsp;2,6周&nbsp;第5节&nbsp;A</p></div></td></tr></table>"
        verify("jz_x", source, listOf(2,6).map { JwCourse("材料", "A", "王老师", 4, 5, 5, it, it) })
        verify("jz_1", source, emptyList())
    }

    @Test fun wistKeepsNoonAndSparseWeeks() {
        val source = "<table><tr><td data-role='item' data-week='2'><div class='mtt_arrange_item'><div class='mtt_item_kcmc'>船舶实验</div><div class='mtt_item_jxbmc'>王老师</div><div class='mtt_item_bjmc'>船舶一班</div><div class='mtt_item_room'>1-5单周，9周，中1-中2，船舶实验室</div></div></td></tr></table>"
        verify("wist", source, listOf(1,3,5,9).map { JwCourse("船舶实验", "船舶实验室", "王老师", 2, 5, 6, it, it) })
        verify("xmu", source, emptyList())
    }

    @Test fun xmuReadsOnlyArrangedBitmap() {
        val source = """<script>var scheduleList=[{"KCM":"数学","SKJS":"王老师","SKDD":"A","SKZC":"10101","KSJC":3,"JSJC":4,"SKXQ":7}];var notArrangedList=[];</script>"""
        verify("xmu", source, listOf(1,3,5).map { JwCourse("数学", "A", "王老师", 7, 3, 4, it, it) })
        verify("wist", source, emptyList())
    }

    @Test fun swjtuPostUsesNativeJinZhiChain() {
        val source = "<table id='jsTbl_123'><tr><td xq='5' jc='7' rowspan='3'><div class='arrage'><p>研</p><p>第2-6双周(王老师)</p><p>研讨(教学班)</p><p>A</p><p>面授讲课</p></div></td></tr></table>"
        verify("swjtu_post", source, listOf(JwCourse("研讨", "A", "王老师", 5, 7, 9, 2, 6, 2)))
        verify("jz_1", source, emptyList())
    }

    @Test fun kgZxPreservesReportCreditAndTeacher() {
        val cells = MutableList(11) { "<td></td>" }
        cells[1] = "<td>[001]材料学</td>"; cells[2] = "<td>2.5</td>"; cells[4] = "<td>王老师</td>"
        cells[10] = "<td>[1-3周]星期三[3-4节]/A</td>"
        verify("kg_zx", "<div id='pageRpt'><table><tr>" + cells.joinToString("") + "</tr></table></div>",
            listOf(JwCourse("材料学", "A", "王老师", 3, 3, 4, 1, 3, credit = 2.5f)))
    }
}
