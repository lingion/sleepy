package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class UndergradRoundThreeSchoolTableTest {
    private fun parse(source: String, type: String): List<JwCourse> {
        val courses = JwParserRegistry.parserFor(type, source).generateCourseList()
        assertEquals(courses, JwParserRegistry.selectBest(source, type).first)
        assertEquals(courses, JwImportViewModel.parseHtmlStatic(source, type).first)
        assertDeclaredHtmlFrame(type, source, courses)
        val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "协议回归", type = type), courses, emptyList())
        assertEquals(courses, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!.courses)
        return courses
    }

    @Test fun hbutPreservesDoubleBreakCoursesAndLocalWeekParity() {
        val source = "<div class='table-Schedule'><table><tr><th>表头</th></tr><tr><td>数学<br>王老师<br>第1-5单周 A<br><br>英语<br>第2-8双周 B</td>" + "<td></td>".repeat(6) + "</tr></table></div>"
        assertEquals(listOf(JwCourse("数学", "A", "王老师", 1, 1, 2, 1, 5, 1), JwCourse("英语", "B", "", 1, 1, 2, 2, 8, 2)),
            parse(source, "hbut"))
    }

    @Test fun hbutReadsWeekScheduleDayRangeAndExactNodeRange() {
        val source = "<table id='weekSchedule'><tr><td>物理,说明</td><td>第2~8双周</td><td>星期六~星期日</td><td>第3~5节</td><td>地点:C</td></tr></table>"
        assertEquals(listOf(JwCourse("物理", "C", "", 6, 3, 5, 2, 8, 2), JwCourse("物理", "C", "", 7, 3, 5, 2, 8, 2)),
            parse(source, "hbut"))
        assertEquals(emptyList<JwCourse>(), parse(source.replace("星期六~星期日", "星期日~星期一"), "hbut"))
    }

    @Test fun hbutKeepsEverySparseWeekAndNodeInsteadOfCuttingAtAComma() {
        val source = "<table id='weekSchedule'><tr><td>物理,实验,说明</td><td>第1,3,7~9双周</td><td>星期日</td><td>第3,5~6节</td><td>地点:C</td></tr></table>"
        assertEquals(listOf(
            JwCourse("物理,实验", "C", "", 7, 3, 3, 7, 9, 2),
            JwCourse("物理,实验", "C", "", 7, 5, 6, 7, 9, 2),
        ), parse(source, "hbut"))
        val mixed = source.replace("第1,3,7~9双周", "第1,3,7~9周")
        assertEquals(listOf(
            JwCourse("物理,实验", "C", "", 7, 3, 3, 1, 1), JwCourse("物理,实验", "C", "", 7, 3, 3, 3, 3), JwCourse("物理,实验", "C", "", 7, 3, 3, 7, 9),
            JwCourse("物理,实验", "C", "", 7, 5, 6, 1, 1), JwCourse("物理,实验", "C", "", 7, 5, 6, 3, 3), JwCourse("物理,实验", "C", "", 7, 5, 6, 7, 9),
        ), parse(mixed, "hbut"))
        for (bad in listOf(source.replace("第1,3,7~9双周", "第1,3周,未知"), source.replace("星期日", "星期日,未知"), source.replace("第3,5~6节", "第3,5节,未知")))
            assertEquals(emptyList<JwCourse>(), parse(bad, "hbut"))
    }

    @Test fun hnjmUsesDayTitlesAndCompleteSchoolClock() {
        val source = "<div class='Sub-kcbt'>上午</div><br><div class='Sub-kcbt'>星期日</div><table><tr><td>9-10节</td><td>英语 2-8周 （图301）】</td><td>王老师</td></tr><tr><td>3-4节</td><td>物理 1-5周 【说明；A101】</td><td>李老师</td></tr></table>"
        assertEquals(listOf(
            JwCourse("英语", "图301", "王老师", 7, 9, 10, 2, 8, 0, "19:00", "20:40"),
            JwCourse("物理", "A101", "李老师", 7, 3, 4, 1, 5, 0, "10:15", "11:55"),
        ), parse(source, "hnjm"))
        assertEquals(emptyList<JwCourse>(), parse(source.replace("9-10节", "11-12节").replace("1-5周", "8-1周"), "hnjm"))
    }

    @Test fun lzReadsBothCellFormsAndDoesNotApplyParityToPreviousCourse() {
        val source = "<table id='TableLCRoomOccupy'><tbody><tr>" +
            "<td class='PuTongCell' id='jieci3day1'><div>数学<br>王老师<br>教室:A(教室人数50)<br>备注<br>周次:1-5周[单],8周</div></td>" +
            "<td class='PuTongCell'>英语<br>李老师<br>B<br>2-8周,双周[9-10节]</td></tr></tbody></table>"
        assertEquals(listOf(JwCourse("数学", "A", "王老师", 1, 3, 3, 1, 5, 1), JwCourse("数学", "A", "王老师", 1, 3, 3, 8, 8), JwCourse("英语", "B", "李老师", 2, 9, 10, 2, 8, 2)),
            parse(source, "lz"))
        assertEquals(emptyList<JwCourse>(), parse("<table id='TableLCRoomOccupy'><tr><td class='PuTongCell'>单周</td></tr></table>", "lz"))
    }

    @Test fun nauReadsDynamicHeadersAndMultiplePlaces() {
        val source = nauTable("上课地点：A 上课时间：第1-5周之单周 星期 1 第 3-4节上课地点：B 上课时间：第2-8周之双周 星期 7 第 9-10节")
        assertEquals(listOf(JwCourse("数学", "A", "王老师", 1, 3, 4, 1, 5, 1, credit = 2f), JwCourse("数学", "B", "王老师", 7, 9, 10, 2, 8, 2, credit = 2f)),
            parse(source, "nau"))
        assertEquals(emptyList<JwCourse>(), parse(source.replace("任课教师", "未识别"), "nau"))
        assertEquals(emptyList<JwCourse>(), parse(nauTable("上课地点：A 上课时间：第1-8周 星期 8 第 3-4节"), "nau"))
    }

    @Test fun njuUsesEveryCardAndBothPeriodEndpointClocks() {
        val source = "<div id='kcb_container'><table class='wut_table'><tbody><tr><th>表头</th></tr>" +
            "<tr><td class='mtt_bgcolor_grey' data-unit='3'>第3节(09:40~10:25)</td><td data-role='item'>" +
            njuCard("CODE 数学 1班", "周日 3-4节 1-5单周,8周", "A") + njuCard("CODE 英语 2班", "周二 3节 2周", "B") + "</td></tr>" +
            "<tr><td class='mtt_bgcolor_grey' data-unit='4'>第4节(10:35~11:20)</td></tr></tbody></table></div>"
        assertEquals(listOf(
            JwCourse("数学", "A", "教学班001", 7, 3, 4, 1, 5, 1, "09:40", "11:20"),
            JwCourse("数学", "A", "教学班001", 7, 3, 4, 8, 8, 0, "09:40", "11:20"),
            JwCourse("英语", "B", "教学班001", 2, 3, 3, 2, 2, 0, "09:40", "10:25"),
        ), parse(source, "nju"))
    }

    @Test fun njuDoesNotDefaultMissingWeeksOrCreateHalfClockPairs() {
        val source = "<div id='kcb_container'><table class='wut_table'><tbody><tr><th>表头</th></tr><tr><td class='mtt_bgcolor_grey' data-unit='3'>第3节(09:40~10:25)</td><td data-role='item'>" + njuCard("CODE 物理", "周一 3-4节 2-8双周", "A") + "</td></tr></tbody></table></div>"
        assertEquals(listOf(JwCourse("物理", "A", "教学班001", 1, 3, 4, 2, 8, 2)), parse(source, "nju"))
        for (bad in listOf("周一 3-4节", "周八 3-4节 1-8周", "周一 4-3节 1-8周", "周一 3-4节 8-1周"))
            assertEquals(emptyList<JwCourse>(), parse(source.replace("周一 3-4节 2-8双周", bad), "nju"))
    }

    @Test fun njuPreservesSparseWeeksAndNodesAndRejectsUnexplainedTimingText() {
        val source = "<div id='kcb_container'><table class='wut_table'><tbody><tr><th>表头</th></tr><tr><td class='mtt_bgcolor_grey' data-unit='3'>第3节</td><td data-role='item'>" + njuCard("CODE 物理", "周一 3,5节 1,3,7周", "A") + "</td></tr></tbody></table></div>"
        assertEquals(listOf(
            JwCourse("物理", "A", "教学班001", 1, 3, 3, 1, 1), JwCourse("物理", "A", "教学班001", 1, 3, 3, 3, 3), JwCourse("物理", "A", "教学班001", 1, 3, 3, 7, 7),
            JwCourse("物理", "A", "教学班001", 1, 5, 5, 1, 1), JwCourse("物理", "A", "教学班001", 1, 5, 5, 3, 3), JwCourse("物理", "A", "教学班001", 1, 5, 5, 7, 7),
        ), parse(source, "nju"))
        assertEquals(emptyList<JwCourse>(), parse(source.replace("1,3,7周", "1,3,7周 未知"), "nju"))
    }

    @Test fun njuKeepsAmbiguousCourseDigitsAndProvidedTeacherText() {
        for ((label, expectedName) in listOf("数学分析21班" to "数学分析21班", "数学分析2 1班" to "数学分析2", "数学分析2（1班）" to "数学分析2")) {
            val source = "<div id='kcb_container'><table class='wut_table'><tbody><tr><th>表头</th></tr><tr><td class='mtt_bgcolor_grey' data-unit='3'>第3节</td><td data-role='item'>" +
                njuCard("CODE $label", "周一 3-4节 1-2周", "A").replace("教学班001", "王老师") + "</td></tr></tbody></table></div>"
            assertEquals(listOf(JwCourse(expectedName, "A", "王老师", 1, 3, 4, 1, 2)), parse(source, "nju"))
        }
    }

    @Test fun cquptUsesWeekMaskSparseWeeksAndConsecutiveDuration() {
        val source = cquptTable("1010001", "3节连上", "王老师 2.5学分")
        assertEquals(listOf(JwCourse("数学", "A", "王老师", 1, 3, 5, 1, 1, credit = 2.5f), JwCourse("数学", "A", "王老师", 1, 3, 5, 3, 3, credit = 2.5f), JwCourse("数学", "A", "王老师", 1, 3, 5, 7, 7, credit = 2.5f)),
            parse(source, "cqupt"))
        assertEquals(listOf(JwCourse("数学", "A", "王老师", 1, 3, 4, 1, 8, credit = 2.5f)), parse(cquptTable("11111111", "", "王老师 2.5学分"), "cqupt"))
    }

    @Test fun tableLayoutsPreserveSourceSectionsBeyondThirty() {
        val hbut = "<table id='weekSchedule'><tr><td>物理,说明</td><td>第1~2周</td><td>星期一</td><td>第31~32节</td><td>地点:A</td></tr></table>"
        assertEquals(listOf(JwCourse("物理", "A", "", 1, 31, 32, 1, 2)), parse(hbut, "hbut"))
        val lz = "<table id='TableLCRoomOccupy'><tr><td class='PuTongCell'>物理<br>王老师<br>A<br>1-2周[31-32节]</td></tr></table>"
        assertEquals(listOf(JwCourse("物理", "A", "王老师", 1, 31, 32, 1, 2)), parse(lz, "lz"))
        assertEquals(listOf(JwCourse("数学", "A", "王老师", 1, 31, 32, 1, 2, credit = 2f)),
            parse(nauTable("上课地点：A 上课时间：第1-2周 星期 1 第 31-32节"), "nau"))
        val nju = "<div id='kcb_container'><table class='wut_table'><tbody><tr><th>表头</th></tr><tr><td class='mtt_bgcolor_grey' data-unit='31'>第31节</td><td data-role='item'>" + njuCard("CODE 物理", "周一 31-32节 1-2周", "A") + "</td></tr></tbody></table></div>"
        assertEquals(listOf(JwCourse("物理", "A", "教学班001", 1, 31, 32, 1, 2)), parse(nju, "nju"))
        assertEquals(listOf(JwCourse("数学", "A", "王老师", 1, 3, 33, 1, 2, credit = 2f)),
            parse(cquptTable("11", "31节连上", "王老师 2学分"), "cqupt"))
        assertEquals(emptyList<JwCourse>(), parse(cquptTable("11", "2147483647节连上", "王老师 2学分"), "cqupt"))
    }

    @Test fun tableLayoutsRejectMalformedMasksBoundsAndShortRows() {
        for (source in listOf(cquptTable("10x1", "", "王老师 2学分"), cquptTable("0000", "", "王老师 2学分"), cquptTable("1".repeat(367), "", "王老师 2学分"), cquptTable("11", "0节连上", "王老师 2学分")))
            assertEquals(emptyList<JwCourse>(), parse(source, "cqupt"))
        for (type in listOf("hbut", "hnjm", "lz", "nau", "nju", "cqupt")) assertEquals(emptyList<JwCourse>(), parse("<table><tr><td>未知</td></tr></table>", type))
    }

    private fun nauTable(timing: String) = "<table id='content'><tr><th>任课教师</th><th>空</th><th>上课时间及地点</th><th>学分</th><th>空</th><th>课程名称</th><th>空</th><th>空</th></tr><tr><td>王老师</td><td></td><td>$timing</td><td>2</td><td></td><td>数学</td><td></td><td></td></tr></table>"
    private fun njuCard(name: String, timing: String, room: String) = "<div class='mtt_arrange_item'><span class='mtt_item_kcmc'>$name</span><span class='mtt_item_jxbmc'>教学班001</span><span class='mtt_item_room'>$room</span><span class='mtt_item_sksj'>$timing</span></div>"
    private fun cquptTable(mask: String, duration: String, teacher: String) = "<table id='stuPanel'><tr style='text-align:center'><td>时段</td></tr><tr style='text-align:center'><td>节次</td><td><div zc='$mask'><span>001-数学</span><br><span>地点：A</span><br><span>$teacher</span><br><span>$duration</span></div></td>" + "<td></td>".repeat(6) + "</tr></table>"
}
