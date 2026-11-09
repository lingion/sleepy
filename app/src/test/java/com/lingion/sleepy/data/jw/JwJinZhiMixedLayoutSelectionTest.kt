package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** A page can retain another timetable layout while displaying the selected one. */
class JwJinZhiMixedLayoutSelectionTest {
    private val comma = """<td data-week="1" data-begin-unit="1" data-end-unit="2">
        <div class="mtt_arrange_item"><div class="mtt_item_kcmc">标准课</div>
        <div class="mtt_item_jxbmc">甲老师</div>
        <div class="mtt_item_room">1-3周,星期一,1-2,A101</div></div></td>"""
    private val hqu = """<td data-week="2" data-begin-unit="3" data-end-unit="4">
        <div class="mtt_arrange_item"><div class="mtt_item_kcmc">空格课(2.5学分)</div>
        <div class="mtt_item_jxbmc">乙老师</div>
        <div class="mtt_item_room">B202 1-3周 3-4</div></div></td>"""
    private val xjtu = """<td data-week="3" data-begin-unit="5" data-end-unit="6">
        <div class="mtt_arrange_item"><div class="mtt_item_kcmc">2,6周&nbsp;丙老师[001]</div>
        <div class="mtt_item_room">[9] 标题课,星期三,5-6,C303</div></div></td>"""
    private val wist = """<table><tr><td data-role="item" data-week="4" data-begin-unit="7" data-end-unit="8">
        <div class="mtt_arrange_item"><div class="mtt_item_kcmc">WIST课</div>
        <div class="mtt_item_jxbmc">丁老师</div>
        <div class="mtt_item_room">2周,W201教室</div></div></td></tr></table>"""

    private fun table(cells: String) = """<table class="wut_table"><tr>$cells</tr></table>"""

    private fun verify(source: String, expected: List<JwCourse>) {
        assertEquals(expected, WakeUpCompat.parseJzNativeChain(source))
        assertEquals(expected, JwJzParser(source).generateCourseList())
        assertEquals(expected, JwDeclaredJinZhiParser(source, "swjtu_post").generateCourseList())
    }

    @Test fun wistPrecedesSpaceSeparatedMtt() {
        verify(table(hqu) + wist, listOf(JwCourse("WIST课", "W201教室", "丁老师", 4, 7, 8, 2, 2)))
    }

    @Test fun wistPrecedesHeadingMtt() {
        verify(table(xjtu) + wist, listOf(JwCourse("WIST课", "W201教室", "丁老师", 4, 7, 8, 2, 2)))
    }

    @Test fun commaMttDoesNotAddTheSpaceLayoutSchedule() {
        verify(table(comma + hqu), listOf(JwCourse("标准课", "A101", "甲老师", 1, 1, 2, 1, 3)))
    }

    @Test fun spaceMttPrecedesHeadingMtt() {
        verify(table(hqu + xjtu), listOf(JwCourse("空格课", "B202", "乙老师", 2, 3, 4, 1, 3, credit = 2.5f)))
    }
}
