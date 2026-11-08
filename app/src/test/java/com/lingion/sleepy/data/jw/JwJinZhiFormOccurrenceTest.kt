package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic pages derived from 6.6.2 jz.OooO00o field and rowspan instructions. */
class JwJinZhiFormOccurrenceTest {
    @Test fun `class table preserves occupied days and each course field order`() {
        val html = """
            <table class="CourseFormTable">
            <tr><td style="text-align:center">节次</td><td style="text-align:center">星期一</td></tr>
            <tr><td class="firstHiddenTd">上午</td><td style="text-align:center">一</td>
              <td rowspan="2"><div>&nbsp;实验(一)[必修]<br>1-5周(单)<br>第1-2节<br>甲老师<br>实验室A</div></td>
              <td rowspan="1"></td><td rowspan="1"><div>&nbsp;线代<br>乙老师<br>2,6周<br>第1节<br>B室</div></td>
            </tr>
            <tr><td style="text-align:center">二</td>
              <td rowspan="1"><div>&nbsp;算法<br>3-4周<br>第2节<br>丙老师<br>C室<hr>
                写作<br>丁老师<br>7周<br>第2节<br>D室</div></td>
            </tr></table>
        """.trimIndent()
        assertEquals(listOf(
            JwCourse("实验(一)", "实验室A", "甲老师", 1, 1, 2, 1, 5, 1),
            JwCourse("线代", "B室", "乙老师", 3, 1, 1, 2, 2),
            JwCourse("线代", "B室", "乙老师", 3, 1, 1, 6, 6),
            JwCourse("算法", "C室", "丙老师", 2, 2, 2, 3, 4),
            JwCourse("写作", "D室", "丁老师", 2, 2, 2, 7, 7)
        ), assertJinZhiImportRoutes(html))
    }

    @Test fun `numeric id table and nbsp delimiters keep empty teacher`() {
        val html = """<table id="1"><tr><th>星期一</th></tr><tr>
            <td style="text-align:center">一</td>
            <td rowspan="1"><div>&nbsp;自习&nbsp;2-4周(双)&nbsp;第3-4节<br><br>A101</div></td>
            </tr></table>"""
        assertEquals(listOf(JwCourse("自习", "A101", "", 1, 3, 4, 2, 4, 2)),
            assertJinZhiImportRoutes(html))
    }

    @Test fun `missing section cannot silently become first section`() {
        val html = """<table id="CourseFormTable"><tr>
            <td>缺节次课程<br>1-16周<br>教师<br>地点</td>
            </tr></table>"""
        assertEquals(emptyList<JwCourse>(), assertJinZhiImportRoutes(html))
    }
}
