package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwUndergradHtmlContainerRenameTest {
    private fun parse(type: String, html: String) = JwUndergradHtmlParser(html, type).generateCourseList()

    private fun jmucy(name: String) = """
        <table id="layout"><tr><th>课表</th></tr><tr><td>第一二节</td><td></td>
        <td>$name A 王老师 第1-4周,6周★英语 李老师 第2-8双周</td></tr></table>
    """.trimIndent()

    private val nuaajc = """
        <table id="print-v2"><tr><th>weekly</th><th>unit</th><th>kcm</th><th>week</th><th>roomid</th><th>lsjs</th><th>jsm</th></tr>
        <tr><td>1-4,6</td><td>9</td><td>离散数学</td><td>7</td><td>A</td><td>3</td><td>王老师</td></tr></table>
    """.trimIndent()

    private fun qmu(name: String) = """
        <table class="grid"><tr><th>课表</th></tr><tr><td valign="top"></td></tr>
        <tr><th>中午</th></tr><tr><td valign="top"></td><td valign="top">$name<br>李老师<br>B<br>2-8.12周 双周 1节</td></tr></table>
    """.trimIndent()

    private val title = "编号\n代码\n课程名称：离散数学\n授课教师：王老师\n班级\n上课周次：1-4,6\n开课地点：A\n备注"

    private fun javtc(tip: String) = """<table class="grid"><tr><th>课表</th></tr><tr><td>第三四节</td>
        <td></td><td><a title="$tip">详情</a></td>""" + "<td></td>".repeat(5) + "</tr></table>"

    private val theory = "离散数学 [王老师] [第1-8单周,10周][A] 第3-4节"
    private val lab = "【实验】物理 [第5-6节][第2-8双周] [实验楼]"
    private fun hitszBody(cards: String) =
        "<tbody class='body-v2'><tr><td>节次</td><td></td><td>$cards</td></tr></tbody>"

    @Test fun jmucyKeepsStarGroupsDayAndFirstSemesterFragment() {
        val expected = listOf(
            JwCourse("离散数学", "A", "王老师", 2, 1, 2, 1, 4),
            JwCourse("离散数学", "A", "王老师", 2, 1, 2, 6, 6),
            JwCourse("英语", "", "李老师", 2, 1, 2, 2, 8, 2),
        )
        assertEquals(expected, parse("jmucy", jmucy("离散数学") + "<head></head>" + jmucy("下学期")))
    }

    @Test fun nuaajcKeepsReorderedColumnsAndLength() {
        val expected = listOf(
            JwCourse("离散数学", "A", "王老师", 7, 9, 11, 1, 4),
            JwCourse("离散数学", "A", "王老师", 7, 9, 11, 6, 6),
        )
        assertEquals(expected, parse("nuaajc", nuaajc))
        val single = nuaajc.replace("<th>lsjs</th>", "").replace("<td>3</td>", "")
        assertEquals(expected.map { it.copy(endNode = 9) }, parse("nuaajc", single))
    }

    @Test fun qmuCountsEmptyCourseRowsAndSkipsLunchBeforeFirstSemesterResult() {
        val expected = listOf(
            JwCourse("英语", "B", "李老师", 2, 3, 3, 2, 8, 2),
            JwCourse("英语", "B", "李老师", 2, 3, 3, 12, 12, 2),
        )
        assertEquals(expected, parse("qmu", qmu("英语") + "<head></head>" + qmu("下学期")))
    }

    @Test fun javtcUsesLabelledTitleFromTheWeekdayCell() {
        assertEquals(listOf(
            JwCourse("离散数学", "A", "王老师", 2, 3, 4, 1, 4),
            JwCourse("离散数学", "A", "王老师", 2, 3, 4, 6, 6),
        ), parse("javtc", javtc(title)))
    }

    @Test fun hitszKeepsEachRenamedCardAndWeekday() {
        val cards = "<div><div class='entry'>$theory</div><div class='entry'>$lab</div></div>"
        assertEquals(listOf(
            JwCourse("离散数学", "A", "王老师", 2, 3, 4, 1, 8, 1),
            JwCourse("离散数学", "A", "王老师", 2, 3, 4, 10, 10),
            JwCourse("【实验】物理", "实验楼", "", 2, 5, 6, 2, 8, 2),
        ), parse("hitsz", "<table>" + hitszBody(cards) + "</table>"))
    }

    @Test fun differentFallbackTablesRejectAndEquivalentCopiesKeepOneLayout() {
        val pages = mapOf("jmucy" to jmucy("离散数学"), "nuaajc" to nuaajc, "qmu" to qmu("离散数学"),
            "javtc" to javtc(title), "hitsz" to "<table>" + hitszBody("<div>$theory</div>") + "</table>")
        for ((type, page) in pages) {
            val one = parse(type, page)
            org.junit.Assert.assertTrue(type, one.isNotEmpty())
            assertEquals(type, one, parse(type, page + page))
            val conflict = page + page.replace("离散数学", "另一学期")
            assertEquals(type, emptyList<JwCourse>(), parse(type, conflict))
            assertEquals(type, emptyList<JwCourse>(), parse(type, conflict + "<head></head>" + page))
        }
    }

    @Test fun misleadingRowsAndSplitFieldsCannotProveACourse() {
        val decoys = listOf(
            "jmucy" to jmucy("设置").replace("第一二节", "统计").replace("第1-4周,6周", "12"),
            "jmucy" to jmucy("设置").replace("第1-4周,6周★英语 李老师 第2-8双周", "12"),
            "nuaajc" to nuaajc.replace("<th>week</th>", "<th>unit</th>"),
            "nuaajc" to nuaajc.replace("<th>week</th>", "</tr><tr><th>week</th>"),
            "nuaajc" to nuaajc.replace("<th>roomid</th>", "<th>kcm</th>"),
            "qmu" to qmu("英语").replace("B<br>2-8", "B</td><td valign='top'>2-8"),
            "javtc" to javtc(title.replace("课程名称：", "").replace("上课周次：", "")),
            "hitsz" to "<table>" + hitszBody("<div>离散数学 [王老师]</div><div>[第2周][A] 第1节</div>") + "</table>",
            "hitsz" to "<table>" + hitszBody("<div>$theory</div>") + hitszBody("<div>$lab</div>") + "</table>",
        )
        for ((type, html) in decoys) assertEquals(type, emptyList<JwCourse>(), parse(type, html))
    }

    @Test fun outerLayoutDoesNotOwnNestedCoursesOrShiftInnerCoordinates() {
        for ((type, page) in mapOf("jmucy" to jmucy("离散数学"), "nuaajc" to nuaajc, "qmu" to qmu("英语"),
            "javtc" to javtc(title), "hitsz" to "<table>" + hitszBody("<div>$theory</div>") + "</table>")) {
            val nested = "<table><tr><th>外围</th></tr><tr><td>第一二节</td><td>$page</td></tr></table>"
            assertEquals(type, parse(type, page), parse(type, nested))
        }
        val foreignCells = "<table><tr><td>1-4</td><td>9</td><td>离散数学</td><td>7</td></tr></table>"
        val headers = "<tr><th>weekly</th><th>unit</th><th>kcm</th><th>week</th></tr>"
        assertEquals(emptyList<JwCourse>(), parse("nuaajc", "<table>$headers<tr><td>$foreignCells</td></tr></table>"))
    }

    @Test fun otherSchoolsArchivedTimetablesAreRejected() {
        for (path in listOf("/jw_fixtures/xju-postgraduate-real-gwork.html", "/jw/fixtures/ucas/person-schedule.real-0908.html")) {
            val html = requireNotNull(javaClass.getResource(path)).readText()
            for (type in listOf("jmucy", "nuaajc", "qmu", "javtc", "hitsz")) {
                assertEquals("$type $path", emptyList<JwCourse>(), parse(type, html))
            }
        }
    }
}
