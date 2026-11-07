package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwJinZhiWistXmuOccurrenceTest {
    @Test fun `WIST joins adjacent sections but keeps different teachers and gaps`() {
        fun cell(node: Int, teacher: String) = """<td data-role="item" data-week="2" data-begin-unit="$node" data-end-unit="$node">
            <div class="mtt_arrange_item"><div class="mtt_item_kcmc">实验</div>
            <div class="mtt_item_jxbmc">$teacher</div><div class="mtt_item_room">2周,A101</div></div></td>"""
        val source = "<table><tr>" + cell(3, "甲") + cell(4, "甲") + cell(6, "甲") + cell(5, "乙") + "</tr></table>"
        assertEquals(setOf(
            JwCourse("实验", "A101", "甲", 2, 3, 4, 2, 2),
            JwCourse("实验", "A101", "甲", 2, 6, 6, 2, 2),
            JwCourse("实验", "A101", "乙", 2, 5, 5, 2, 2)
        ), assertJinZhiImportRoutes(source).toSet())
    }

    @Test fun `WIST maps noon labels without changing course name to class name`() {
        val source = """<table><tr><td data-role="item" data-week="2">
            <div class="mtt_arrange_item"><div class="mtt_item_kcmc">船舶实验</div>
            <div class="mtt_item_jxbmc">甲老师</div><div class="mtt_item_bjmc">船舶一班</div>
            <div class="mtt_item_room">1-5单周，9周，中1-中2，备注，船舶实验室</div></div>
            </td></tr></table>"""
        assertEquals(listOf(1,3,5,9).map {
            JwCourse("船舶实验", "船舶实验室", "甲老师", 2, 5, 6, it, it)
        }, assertJinZhiImportRoutes(source))
    }

    @Test fun `WIST attributes override numeric room line section token`() {
        val source = """<table><tr><td data-role="item" data-week="6" data-begin-unit="7" data-end-unit="8">
            <div class="mtt_arrange_item"><div class="mtt_item_kcmc">训练</div>
            <div class="mtt_item_jxbmc">乙老师</div><div class="mtt_item_room">2周,6周,3-4,B202</div></div>
            </td></tr></table>"""
        assertEquals(listOf(2,6).map {
            JwCourse("训练", "B202", "乙老师", 6, 7, 8, it, it)
        }, assertJinZhiImportRoutes(source))
    }

    @Test fun `XMU embedded schedule bitmap starts at week one and keeps alternate location`() {
        val source = """<script>var scheduleList = [
            {"KCM":"高数","SKJS":"丙老师","SKDD":"","YPSJDD":"A303",
             "SKZC":"10101","KSJC":3,"JSJC":4,"SKXQ":7},
            {"KCM":"无课周","SKZC":"00000","KSJC":1,"JSJC":2,"SKXQ":1}
            ];
            var notArrangedList = [{"KCM":"未安排课","SKZC":"11111"}];</script>"""
        assertEquals(listOf(1,3,5).map {
            JwCourse("高数", "A303", "丙老师", 7, 3, 4, it, it)
        }, assertJinZhiImportRoutes(source))
    }
}
