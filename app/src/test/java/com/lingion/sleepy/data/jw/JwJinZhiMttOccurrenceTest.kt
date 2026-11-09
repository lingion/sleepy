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
    private fun attributeSource(weeks:String) = """<table class="wut_table"><tr><td data-week="5" data-begin-unit="31" data-end-unit="32">
        <div class="mtt_arrange_item"><div class="mtt_item_kcmc">研讨课2</div><div class="mtt_item_jxbmc">李</div>
        <div class="mtt_item_room">$weeks,A201</div></div></td></tr></table>"""

    @Test fun `mtt complete attributes do not require redundant day and node text`() {
        val expected = listOf(JwCourse("研讨课2","A201","李",5,31,32,1,5,1),JwCourse("研讨课2","A201","李",5,31,32,9,9))
        assertEquals(expected,assertJinZhiImportRoutes(attributeSource("1-5周(单),9周")))
        assertEquals(expected,assertUndergradBImportRoutes(attributeSource("1-5周(单),9周"),"swjtu_post"))
    }

    @Test fun `mtt malformed week fragments cannot become a partial import`() {
        for(weeks in listOf("1-3周,第x周", "1-3周,4-2周", "1-3周,2147483648周")) {
            val source = attributeSource(weeks).replace(",A201",",星期五,第31节-第32节,A201")
            assertEquals(emptyList<JwCourse>(),assertJinZhiImportRoutes(source))
        }
    }

}
