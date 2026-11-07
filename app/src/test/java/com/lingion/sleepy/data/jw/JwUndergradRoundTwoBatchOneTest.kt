package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwUndergradRoundTwoBatchOneTest {
    private fun imported(type: String, source: String): List<JwCourse> {
        val direct = JwParserRegistry.parserFor(type, source).generateCourseList()
        assertEquals(direct, JwParserRegistry.selectBest(source, type).first)
        val imported = JwImportViewModel.parseHtmlStatic(source, type).first
        assertEquals(direct, imported)
        val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "协议回归", type=type), imported, emptyList())
        assertEquals(direct, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!.courses)
        if (type != "sustech") assertDeclaredHtmlFrame(type, source, imported)
        return imported
    }

    @Test fun hniuReadsItsOwnTableAndDiscontinuousWeeks() {
        val source = """<table bordercolordark="#FFFFFF"><tbody><tr><td align="center">时间</td><td valign="top">离散数学<br>王老师 [1-4,6周][3-4节]<br>理科楼201</td></tr></tbody></table>"""
        assertEquals(
            listOf(
                JwCourse("离散数学", "理科楼201", "王老师", 1, 3, 4, 1, 4),
                JwCourse("离散数学", "理科楼201", "王老师", 1, 3, 4, 6, 6),
            ),
            imported("hniu", source),
        )
    }

    @Test fun sustechUsesSectionBracketAndKeepsWeekSegments() {
        val source = """[{"KCWZSM":null,"RWH":null,"FILEURL":null,"SKSJ":"离散数学[王老师][教学班-01][1-8单周,10周][理科楼201][第3-4节]","XB":null,"SKSJ_EN":null,"KEY":"xq7_jc1"},{"KCWZSM":null,"RWH":null,"FILEURL":null,"SKSJ":"备注","XB":null,"SKSJ_EN":null,"KEY":"bz"}]"""
        assertEquals(
            listOf(
                JwCourse("离散数学", "理科楼201", "王老师", 7, 3, 4, 1, 8, 1, note = "01"),
                JwCourse("离散数学", "理科楼201", "王老师", 7, 3, 4, 10, 10, note = "01"),
            ),
            imported("sustech", source),
        )
    }

    @Test fun uicKeepsAdjacentSinglePeriodsAndTeacherLookup() {
        val source = """<table class="tablestyle-2"><tbody><tr><td>1</td><td>x</td><td>离散数学</td><td>王老师</td></tr></tbody></table><table id="mytimetable"><tr><th>星期一</th></tr><tr><td>离散数学<br>理科楼201</td></tr><tr><td>离散数学<br>理科楼201</td></tr></table>"""
        assertEquals(
            listOf(
                JwCourse("离散数学", "理科楼201", "王老师", 1, 1, 1, 1, 20),
                JwCourse("离散数学", "理科楼201", "王老师", 1, 2, 2, 1, 20),
            ),
            imported("uic", source),
        )
    }

    @Test fun cityuRecoversWeekdayAfterRowSpans() {
        val source = """<table class="ctt-matrix"><tr><th>课表</th></tr><tr><td class="ctt-matrix-td-time">08:00</td><td rowspan="2">代码<br>离散数学<br>理科楼201</td><td rowspan="2">代码<br>英语<br>文科楼101</td><td></td></tr><tr><td class="ctt-matrix-td-time">09:00</td><td>代码<br>物理<br>实验楼301</td></tr></table>"""
        assertEquals(
            listOf(
                JwCourse("离散数学", "理科楼201", "", 1, 1, 2, 1, 20),
                JwCourse("英语", "文科楼101", "", 2, 1, 2, 1, 20),
                JwCourse("物理", "实验楼301", "", 3, 2, 2, 1, 20),
            ),
            imported("cityu", source),
        )
    }

    @Test fun stuDecodesSundayLettersCreditAndParity() {
        val source = """<table id="DataGrid1"><tr class="DGHeaderStyle"><td>表头</td></tr><tr><td>1</td><td>[CS101]离散数学</td><td>2.5</td><td>王老师</td><td>理科楼201</td><td>2-16</td><td>AB双</td><td></td><td></td><td></td><td></td><td></td><td></td></tr><tr class="DGFooterStyle"><td>页脚</td></tr></table>"""
        assertEquals(
            listOf(JwCourse("离散数学", "理科楼201", "王老师", 7, 11, 12, 2, 16, 2, credit = 2.5f)),
            imported("stu", source),
        )
    }

    @Test fun nfuKeepsMultipleSundayOccurrencesAndOptionalRoom() {
        val source = """<table><tr class="ui-widget-content"><td><span>离散数学</span><span></span><span></span><span></span><span></span><span></span><span></span><span></span><span></span><span>第1-8周 第3-4节 王老师 理科楼201 单周/第10周 第5节 王老师</span></td></tr></table>"""
        assertEquals(
            listOf(
                JwCourse("离散数学", "理科楼201", "王老师", 7, 3, 4, 1, 8, 1),
                JwCourse("离散数学", "", "王老师", 7, 5, 5, 10, 10),
            ),
            imported("nfu", source),
        )
    }

    @Test fun cityuRestoresColumnsAfterEmptyAndExpiredRowSpans() {
        val source = """<table class="ctt-matrix"><tr><th>课表</th></tr><tr><td class="ctt-matrix-td-time">08:00</td><td rowspan="2">代码<br>离散数学<br>A</td><td></td></tr><tr><td class="ctt-matrix-td-time">09:00</td><td></td></tr><tr><td class="ctt-matrix-td-time">10:00</td><td rowspan="2">代码<br>英语<br>B</td><td></td></tr><tr><td class="ctt-matrix-td-time">11:00</td><td>代码<br>物理<br>C</td></tr></table>"""
        assertEquals(
            listOf(
                JwCourse("离散数学", "A", "", 1, 1, 2, 1, 20),
                JwCourse("英语", "B", "", 1, 3, 4, 1, 20),
                JwCourse("物理", "C", "", 2, 4, 4, 1, 20),
            ),
            imported("cityu", source),
        )
    }

    @Test fun stuDoesNotCreateUnadvertisedPeriodsOrInvalidCredit() {
        for (credit in listOf("NaN", "Infinity", "-2.5")) {
            val source = """<table id="DataGrid1"><tr><td>1</td><td>[CS101]离散数学</td><td>$credit</td><td>王老师</td><td>A</td><td>1-8</td><td>135</td></tr></table>"""
            assertEquals(
                listOf(1, 3, 5).map { JwCourse("离散数学", "A", "王老师", 7, it, it, 1, 8) },
                imported("stu", source),
            )
        }
    }

    @Test fun sustechRejectsInvalidDaysSectionsAndWeeks() {
        val arrangements = listOf(
            "xq0_jc1" to "课名[老师][班-1][1-8周][A][第1-2节]",
            "xq8_jc1" to "课名[老师][班-1][1-8周][A][第1-2节]",
            "xq1_jc1" to "课名[老师][班-1][1-8周][A][第0-2节]",
            "xq1_jc1" to "课名[老师][班-1][1-8周][A][第30-31节]",
            "xq1_jc1" to "课名[老师][班-1][0-8周][A][第1-2节]",
            "xq1_jc1" to "课名[老师][班-1][1-367周][A][第1-2节]",
            "xq1_jc1" to "课名[老师][班-1][8-1周][A][第1-2节]",
        )
        for ((key, text) in arrangements) {
            val source = """[{"KEY":"$key","SKSJ":"$text"}]"""
            assertEquals(emptyList<JwCourse>(), imported("sustech", source))
        }
    }
}
