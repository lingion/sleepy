package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Source-shaped synthetic vectors for 6.6.2 jz.OooO0OO. */
class JwJinZhiMttOccurrenceTest {
    @Test fun `mtt keeps day attributes chinese sections sparse weeks and room before trailing label`() {
        val source = """<table class="wut_table"><tr><td data-week="4">
            <div class="mtt_arrange_item"><div class="mtt_item_kcmc">[1] <span>电路(实验)</span></div>
            <div class="mtt_item_jxbmc">甲老师</div>
            <div class="mtt_item_room">1-5周(单),9周,星期二,第三节-第四节,A101,必修</div></div>
            </td></tr></table>"""
        assertEquals(listOf(
            JwCourse("电路(实验)", "A101", "甲老师", 4, 3, 4, 1, 5, 1),
            JwCourse("电路(实验)", "A101", "甲老师", 4, 3, 4, 9, 9)
        ), assertJinZhiImportRoutes(source))
    }

    @Test fun `mtt obtains weekday from arrangement and nodes from attributes`() {
        val source = """<table class="wut_table"><tr><td data-begin-unit="7" data-end-unit="9">
            <divclass="mtt_arrange_item"><div class="mtt_item_kcmc">[2] 研讨课</div>
            <div class="mtt_item_jxbmc">乙老师</div>
            <div class="mtt_item_room">2周,6周,星期日,B202</div></div>
            </td></tr></table>"""
        assertEquals(listOf(
            JwCourse("研讨课", "B202", "乙老师", 7, 7, 9, 2, 2),
            JwCourse("研讨课", "B202", "乙老师", 7, 7, 9, 6, 6)
        ), assertJinZhiImportRoutes(source))
    }

    @Test fun `mtt missing day and sections cannot invent Monday first section`() {
        val source = """<table class="wut_table"><tr><td><div class="mtt_arrange_item">
            <div class="mtt_item_kcmc">课</div><div class="mtt_item_jxbmc">甲</div>
            <div class="mtt_item_room">1-16周,A101</div></div></td></tr></table>"""
        assertEquals(emptyList<JwCourse>(), assertJinZhiImportRoutes(source))
    }
}
