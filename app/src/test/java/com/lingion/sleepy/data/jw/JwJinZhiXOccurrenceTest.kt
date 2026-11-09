package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic 6.6.2 jz.OooOO0 and jz.OooOO0O pages, not authenticated captures. */
class JwJinZhiXOccurrenceTest {
    @Test fun `wut table x keeps both nbsp field layouts and every week token`() {
        val source = """<table class="wut_tablex"><tr><td data-role="course" data-week="4">
            <div><i></i><b>《材料(实验)》</b><p>&nbsp;甲老师&nbsp;1-5周(单),9周&nbsp;第3-4节&nbsp;A&nbsp;<b>201</b></p></div>
            <div><i></i><b>《写作》</b><p>乙老师&nbsp;2,6周&nbsp;第5节&nbsp;B202</p></div>
            </td></tr></table>"""
        assertEquals(listOf(
            JwCourse("材料(实验)", "A201", "甲老师", 4, 3, 4, 1, 5, 1),
            JwCourse("材料(实验)", "A201", "甲老师", 4, 3, 4, 9, 9),
            JwCourse("写作", "B202", "乙老师", 4, 5, 5, 2, 2),
            JwCourse("写作", "B202", "乙老师", 4, 5, 5, 6, 6)
        ), assertJinZhiImportRoutes(source))
    }

    @Test fun `x table uses explicit weekday and section with multiple courses`() {
        val source = """<table class="x-table"><tr><td><div>
            ,数学(一)[必修]<br>甲老师<br>第1-3周,第7周，星期日，第2节-第3节，A101<br>
            ,物理<br>乙老师<br>第2-6双周，星期二，第5节，B202
            </div></td></tr></table>"""
        assertEquals(listOf(
            JwCourse("数学(一)", "A101", "甲老师", 7, 2, 3, 1, 3),
            JwCourse("数学(一)", "A101", "甲老师", 7, 2, 3, 7, 7),
            JwCourse("物理", "B202", "乙老师", 2, 5, 5, 2, 6, 2)
        ), assertJinZhiImportRoutes(source))
    }
}
