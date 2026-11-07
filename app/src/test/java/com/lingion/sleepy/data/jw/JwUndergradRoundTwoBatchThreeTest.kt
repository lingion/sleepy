package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwUndergradRoundTwoBatchThreeTest {
    private fun imported(type: String, source: String): List<JwCourse> {
        val direct = JwParserRegistry.parserFor(type, source).generateCourseList()
        assertEquals(direct, JwParserRegistry.selectBest(source, type).first)
        val imported = JwImportViewModel.parseHtmlStatic(source, type).first
        assertEquals(direct, imported)
        val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "协议回归", type = type), imported, emptyList())
        assertEquals(imported, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!.courses)
        assertDeclaredHtmlFrame(type, source, imported)
        return imported
    }

    @Test fun jmucyKeepsStarSeparatedCoursesAndSparseWeeks() {
        val source = """<table id="ctl00_ContentPlaceHolder3_ScheduleTable"><tr><th>课表</th></tr><tr><td>第一二节</td><td>离散数学 A 王老师 第1-4周,6周★英语 李老师 第2-8双周</td></tr></table>"""
        assertEquals(
            listOf(
                JwCourse("离散数学", "A", "王老师", 1, 1, 2, 1, 4),
                JwCourse("离散数学", "A", "王老师", 1, 1, 2, 6, 6),
                JwCourse("英语", "", "李老师", 1, 1, 2, 2, 8, 2),
            ), imported("jmucy", source),
        )
    }

    @Test fun nuaajcFindsColumnsByTheirServerHeaders() {
        val source = """<table id="GrwCourseTablePrint"><tr><th>weekly</th><th>unit</th><th>kcm</th><th>week</th><th>roomid</th><th>lsjs</th><th>jsm</th></tr><tr><td>1-4,6</td><td>9</td><td>离散数学</td><td>7</td><td>A</td><td>3</td><td>王老师</td></tr></table>"""
        assertEquals(
            listOf(
                JwCourse("离散数学", "A", "王老师", 7, 9, 11, 1, 4),
                JwCourse("离散数学", "A", "王老师", 7, 9, 11, 6, 6),
            ), imported("nuaajc", source),
        )
    }

    @Test fun qmuUsesOnlyRowsWithCourseCellsAndDotSeparatedWeeks() {
        val source = """<table frame="box"><tr><th>表头</th></tr><tr><td valign="top">离散数学<br>王老师<br>A<br>1-4.6周 单周 2节</td></tr><tr><th>中午</th></tr><tr><td valign="top">英语<br>李老师<br>B<br>2-8周 双周 1节</td></tr></table>"""
        assertEquals(
            listOf(
                JwCourse("离散数学", "A", "王老师", 1, 1, 2, 1, 4, 1),
                JwCourse("离散数学", "A", "王老师", 1, 1, 2, 6, 6, 1),
                JwCourse("英语", "B", "李老师", 1, 3, 3, 2, 8, 2),
            ), imported("qmu", source),
        )
    }

    @Test fun javtcReadsMultilineTitleAndSparseSingleWeeks() {
        val title = "编号\n代码\n课程名称：离散数学\n授课教师：王老师\n班级\n上课周次：1-4,6\n开课地点：A\n备注"
        val source = """<table border="1" width="98%"><tr><th>课表</th></tr><tr><td>第三四节</td><td><a title="$title">课程</a></td>""" + "<td></td>".repeat(6) + "</tr></table>"
        assertEquals(
            listOf(
                JwCourse("离散数学", "A", "王老师", 1, 3, 4, 1, 4),
                JwCourse("离散数学", "A", "王老师", 1, 3, 4, 6, 6),
            ), imported("javtc", source),
        )
    }

    @Test fun hitszKeepsLabAndTheoryCardFields() {
        val source = """<table><tbody class="ivu-table-tbody"><tr><td>节次</td><td><div class="ivu-card-body">离散数学 [王老师] [第1-8单周,10周][A] 第3-4节</div><div class="ivu-card-body">【实验】物理 [第5-6节][第2-8双周] [实验楼]</div></td></tr></tbody></table>"""
        assertEquals(
            listOf(
                JwCourse("离散数学", "A", "王老师", 1, 3, 4, 1, 8, 1),
                JwCourse("离散数学", "A", "王老师", 1, 3, 4, 10, 10),
                JwCourse("【实验】物理", "实验楼", "", 1, 5, 6, 2, 8, 2),
            ), imported("hitsz", source),
        )
    }
}
