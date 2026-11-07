package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwJinZhiVariantOccurrenceTest {
    @Test fun `HQU space separated room weeks sections preserves credits`() {
        val source = """<table class="wut_table"><tr><td data-week="2">
            <div class="mtt_arrange_item"><div class="mtt_item_kcmc">[1] 高数(2.5学分)</div>
            <div class="mtt_item_jxbmc">甲老师</div>
            <div class="mtt_item_room">A101 1-5周(单) 9周 第三节-第四节</div></div>
            </td></tr></table>"""
        assertEquals(listOf(
            JwCourse("高数", "A101", "甲老师", 2, 3, 4, 1, 5, 1, credit=2.5f),
            JwCourse("高数", "A101", "甲老师", 2, 3, 4, 9, 9, credit=2.5f)
        ), assertJinZhiImportRoutes(source))
    }

    @Test fun `XJTU takes name from room line and weeks and teacher from heading`() {
        val source = """<table class="wut_table"><tr><td data-week="3">
            <div class="mtt_arrange_item"><div class="mtt_item_kcmc">2,6周&nbsp;乙老师[001]</div>
            <div class="mtt_item_room">[9] 材料学,星期三,第5-6节,B202</div></div>
            </td></tr></table>"""
        assertEquals(listOf(
            JwCourse("材料学", "B202", "乙老师", 3, 5, 6, 2, 2),
            JwCourse("材料学", "B202", "乙老师", 3, 5, 6, 6, 6)
        ), assertJinZhiImportRoutes(source))
    }

    @Test fun `post graduate mode changes teacher source and hidden duplicate is excluded`() {
        val hidden = """<td xq="1" jc="1" style="display: none"><div class="arrage">
            <p>1-16周</p><p>隐藏重复课</p><p>甲</p><p>A</p></div></td>"""
        val source = """<table id="jsTbl_123"><tr>$hidden<td xq="5" jc="7" rowspan="3">
            <div class="arrage"><p>研</p><p>第2-6双周(丙老师)</p><p>研讨(教学班)</p><p>C303</p><p>面授讲课</p></div>
            <div class="arrage"><p>第1周、第9周</p><p>写作</p><p>丁老师</p><p>D404</p></div>
            </td></tr></table>"""
        assertEquals(listOf(
            JwCourse("研讨", "C303", "丙老师", 5, 7, 9, 2, 6, 2),
            JwCourse("写作", "D404", "丁老师", 5, 7, 9, 1, 1),
            JwCourse("写作", "D404", "丁老师", 5, 7, 9, 9, 9)
        ), assertJinZhiImportRoutes(source))
    }
}
