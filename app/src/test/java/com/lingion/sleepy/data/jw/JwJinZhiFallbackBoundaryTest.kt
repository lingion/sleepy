package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class JwJinZhiFallbackBoundaryTest {
    private val brokenPost = """<table id="jsTbl_bad"><tr><td xq="5" jc="7" rowspan="3"><div class="arrage"><p>研</p><p>第999999999999999999999周(甲老师)</p><p>坏安排(教学班)</p><p>A</p><p>面授讲课</p></div></td></tr></table>"""
    private val xmu = """<script>var scheduleList=[{"KCM":"材料力学","SKJS":"乙老师","SKDD":"B202","SKZC":"10101","KSJC":3,"JSJC":4,"SKXQ":7}];var notArrangedList=[];</script>"""

    private fun verify(type: String, source: String, expected: List<JwCourse>) {
        assertEquals(expected, JwParserRegistry.parserFor(type, source).generateCourseList())
        assertEquals(expected, JwParserRegistry.selectBest(source, type).first)
        assertEquals(expected, JwImportViewModel.parseHtmlStatic(source, type).first)
        assertDeclaredHtmlFrame(type, source, expected)
        val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "金智边界", type = type), expected, emptyList())
        assertEquals(expected, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!.courses)
    }

    @Test fun earlyNativeFailureDoesNotHideLaterValidSchedule() {
        val expected = listOf(1, 3, 5).map { JwCourse("材料力学", "B202", "乙老师", 7, 3, 4, it, it) }
        for (type in listOf("jz", "swjtu_post")) verify(type, brokenPost + xmu, expected)
    }

    @Test fun failedNativeChainKeepsItsParserDiagnostic() {
        for (type in listOf("jz", "swjtu_post")) {
            val error = assertThrows(IllegalStateException::class.java) {
                JwParserRegistry.parserFor(type, brokenPost).generateCourseList()
            }
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("JinZhiPost"))
            assertTrue(error.suppressed.any { it is NumberFormatException })
        }
    }

    private fun wist(weeks: String) = """<table><tr><td data-role="item" data-week="2" data-begin-unit="5" data-end-unit="6"><div class="mtt_arrange_item"><div class="mtt_item_kcmc">船舶实验</div><div class="mtt_item_jxbmc">甲老师</div><div class="mtt_item_room">$weeks,船舶实验室</div></div></td></tr></table>"""

    @Test fun wistRejectsWholeBadWeekArrangement() {
        for (weeks in listOf("1周,bad5周", "1周,5-2周", "1周,0周", "1周,367周", "1周,1-367周", "1周,999999999999999999999周")) {
            verify("wist", wist(weeks), emptyList())
        }
    }

    @Test fun wistKeepsIndependentParityAndSparseWeekFields() {
        verify("wist", wist("第1-5单周,8-10双周,13周"), listOf(1, 3, 5, 8, 10, 13).map {
            JwCourse("船舶实验", "船舶实验室", "甲老师", 2, 5, 6, it, it)
        })
    }
}
