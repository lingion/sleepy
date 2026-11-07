package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwUndergradRoundTwoBatchTwoTest {
    private fun imported(type: String, source: String): List<JwCourse> {
        val direct = JwParserRegistry.parserFor(type, source).generateCourseList()
        assertEquals(direct, JwImportViewModel.parseHtmlStatic(source, type).first)
        if (type in setOf("jnu", "swust")) assertEquals(direct, JwParserRegistry.selectBest(source, type).first)
        val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "协议回归", type = type), direct, emptyList())
        assertEquals(direct, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!.courses)
        assertDeclaredHtmlFrame(type, source, direct)
        return direct
    }

    @Test fun bjtuKeepsTeachersInBothGroupingAndPartIdentity() {
        val header = "<tr><th>节次</th>" + listOf("一", "二", "三", "四", "五", "六", "日").joinToString("") { "<th>星期$it</th>" } + "</tr>"
        val first = "<tr><td>第1节</td><td><div><span>离散数学</span><div>第1-8周 <i>王老师</i></div><div>第1-8周 <i>李老师</i></div><span class=\"text-muted\">A</span></div></td>" + "<td></td>".repeat(6) + "</tr>"
        val second = "<tr><td>第2节</td><td><div><span>离散数学</span><div>第1-8周 <i>陈老师</i></div><span class=\"text-muted\">A</span></div></td>" + "<td></td>".repeat(6) + "</tr>"
        assertEquals(
            listOf(
                JwCourse("离散数学", "A", "王老师", 1, 1, 1, 1, 8),
                JwCourse("离散数学", "A", "李老师", 1, 1, 1, 1, 8),
                JwCourse("离散数学", "A", "陈老师", 1, 2, 2, 1, 8),
            ),
            JwParserRegistry.parserFor("bjtu", "<table>$header$first$second</table>").generateCourseList(),
        )
    }

    @Test fun bjtuReadsPinnedLegacyTextWithoutRequiringModernDayHeaders() {
        val source = """<table class="table table-bordered"><tr><th>课表</th></tr><tr><td>第一节</td><td>ABCDEFG [01] 离散数学 [1] 第01-08周 王老师 AB123</td><td></td><td></td><td></td><td></td><td></td><td></td></tr></table>"""
        assertEquals(
            listOf(JwCourse("离散数学", "AB123", "王老师", 1, 1, 1, 1, 8)),
            imported("bjtu", source),
        )
    }

    @Test fun jnuUsesSecondDocumentAndMergesOnlyIdenticalAdjacentCells() {
        val course = "课程：离散数学(理论) 从第2至10 双周 理科楼201"
        val ignored = "<html><body><div id=\"oReportCell\"><table class=\"a8\"><tr><td>无效旧页</td></tr></table></div></body></html>"
        val headers = "<tr><td>表头</td></tr>".repeat(3)
        val rows = "<tr><td>星期一</td><td><div>$course</div></td><td><div>$course</div></td><td></td><td><div>$course</div></td></tr>" +
            "<tr><td>星期二</td><td><div>课程：物理(理论) 实验楼301 尾注</div></td></tr>" +
            "<tr><td></td></tr>".repeat(5)
        val source = ignored + "<html><body><div id=\"oReportCell\"><table class=\"a8\">$headers$rows</table></div></body></html>"
        assertEquals(
            listOf(
                JwCourse("离散数学", "理科楼201", "", 1, 1, 2, 2, 10, 2),
                JwCourse("离散数学", "理科楼201", "", 1, 4, 4, 2, 10, 2),
                JwCourse("物理", "实验楼301", "", 2, 1, 1, 1, 18),
            ),
            imported("jnu", source),
        )
    }

    @Test fun swustUsesLastSevenColumnsAndOneBasedPeriods() {
        val source = """<table class="UICourseTable"><tbody><tr><td>时段</td><td></td><td></td><td></td><td></td><td></td><td></td><td><div class="lecture"><span class="course">离散数学</span><span class="place">理科楼201</span><span class="week">1-8(周)</span></div></td></tr></tbody></table>"""
        assertEquals(
            listOf(JwCourse("离散数学", "理科楼201", "", 7, 1, 1, 1, 8)),
            imported("swust", source),
        )
    }

    @Test fun jnuAcceptsSingleFragmentAndSingleClosedDocument() {
        val fragment = jnuDocument("课程：离散数学(理论) 从第2至10 双周 A")
        val expected = listOf(JwCourse("离散数学", "A", "", 1, 1, 1, 2, 10, 2))
        for (source in listOf(fragment, "<html><body>$fragment</body></html>")) {
            assertEquals(expected, imported("jnu", source))
        }
    }

    @Test fun jnuDoesNotFallBackToOldDocumentWhenSecondDocumentHasNoTimetable() {
        val source = "<html><body>${jnuDocument("课程：旧课(理论) 从第2至10 双周 A")}</body></html>" +
            "<html><body>请重新登录</body></html>"
        assertEquals(emptyList<JwCourse>(), imported("jnu", source))
    }

    @Test fun jnuRejectsAmbiguousThreeDocumentCapture() {
        val page = "<html><body>${jnuDocument("课程：数学(理论) 从第2至10 双周 A")}</body></html>"
        assertEquals(emptyList<JwCourse>(), JwParserRegistry.parserFor("jnu", page + page + page).generateCourseList())
    }

    @Test fun jnuRejectsMalformedWeeksAndMetadataCells() {
        for (text in listOf(
            "课程：数学(理论) 从第10至2 A 尾注",
            "课程：数学(理论) 从第0至8 A 尾注",
            "课程：数学(理论) 从第1至367 A 尾注",
            "课程：数学(理论) 从第999999999999999999至8 A 尾注",
            "课程：数学(理论) 从第未知至8 A 尾注",
            "教室开放时间(理论) A 尾注",
        )) assertEquals(emptyList<JwCourse>(), JwParserRegistry.parserFor("jnu", jnuDocument(text)).generateCourseList())
        assertEquals(emptyList<JwCourse>(), JwParserRegistry.parserFor("jnu", "<div id='oReportCell'><table class='a8'><tr><td>课程：数学(理论)</td></tr></table></div>").generateCourseList())
    }

    @Test fun swustDoesNotInventTeacherOrAcceptInvalidWeeks() {
        fun source(weeks: String, name: String = "离散数学") = "<table class='UICourseTable'><tbody><tr>" +
            "<td><div class='lecture'><span class='course'>$name</span><span class='place'>A</span><span class='week'>$weeks</span></div></td>" +
            "<td></td>".repeat(6) + "</tr></tbody></table>"
        assertEquals(listOf(JwCourse("离散数学", "A", "", 1, 1, 1, 1, 8)), JwParserRegistry.parserFor("swust", source("1-8(周)")).generateCourseList())
        for (weeks in listOf("8-1(周)", "0-8(周)", "1-367(周)", "未知", "1-3-9(周)", "999999999999999999-8(周)")) {
            assertEquals(emptyList<JwCourse>(), JwParserRegistry.parserFor("swust", source(weeks)).generateCourseList())
        }
        assertEquals(emptyList<JwCourse>(), JwParserRegistry.parserFor("swust", source("1-8(周)", "")).generateCourseList())
        assertEquals(emptyList<JwCourse>(), JwParserRegistry.parserFor("swust", "<table class='UICourseTable'><tbody><tr><td>不完整的一列</td></tr></tbody></table>").generateCourseList())
    }

    private fun jnuDocument(text: String): String = "<div id='oReportCell'><table class='a8'>" +
        "<tr><td>表头</td></tr>".repeat(3) + "<tr><td>星期一</td><td><div>$text</div></td></tr>" +
        "<tr><td></td></tr>".repeat(6) + "</table></div>"

    @Test fun scauReadsExplicitPackedSectionAndWeeksAtTheirActualOffsets() {
        val source = """<table border="1"><tr><th>课表</th></tr><tr><td valign="top">离散数学<br>(910节)(双)<br>王老师<br>理科楼201<br>2-10周</td></tr></table>"""
        assertEquals(
            listOf(JwCourse("离散数学", "理科楼201", "王老师", 1, 9, 10, 2, 10, 2)),
            imported("scau", source),
        )
    }

    @Test fun scauDoesNotReuseASectionFromThePreviousCell() {
        val source = """<table border="1"><tr><th>课表</th></tr><tr><td valign="top">离散数学<br>(12节)<br>王老师<br>A<br>1-8周</td><td valign="top">英语<br>(1112节)<br>李老师<br>B<br>2-10周</td></tr></table>"""
        assertEquals(
            listOf(
                JwCourse("离散数学", "A", "王老师", 1, 1, 2, 1, 8),
                JwCourse("英语", "B", "李老师", 2, 11, 12, 2, 10),
            ),
            imported("scau", source),
        )
    }
    @Test fun jnuAllWeeksKeepsOnlyTheRoomAfterItsWeekRange() {
        val source = jnuDocument("课程：数学(理论) 从第2至10 A101 尾注")
        assertEquals(listOf(JwCourse("数学", "A101", "", 1, 1, 1, 2, 10)),
            imported("jnu", source))
    }
}
