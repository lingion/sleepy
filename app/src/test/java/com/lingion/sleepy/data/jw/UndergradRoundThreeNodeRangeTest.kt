package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Incremental evidence for removing the unsupported generic section ceiling. */
class UndergradRoundThreeNodeRangeTest {
    @Test fun gridPreservesExplicitSectionsThirtyOneAndThirtyTwo() {
        val source = "<table id='xkjbgzszGridIdGrid'><tbody><tr><td aria-describedby='xkjbgzszGridIdGrid_kcmc'>物理</td><td aria-describedby='xkjbgzszGridIdGrid_rkjs'>王老师</td><td aria-describedby='xkjbgzszGridIdGrid_xf'>2</td><td aria-describedby='xkjbgzszGridIdGrid_sksjdd'>第1-2周 星期一 31-32节【A】</td></tr></tbody></table>"
        assertEquals(listOf(JwCourse("物理", "A", "王老师", 1, 31, 32, 1, 2, credit = 2f)), JwSchoolGridParser(source, "gxnu").generateCourseList())
    }

    @Test fun hitPreservesExplicitSectionsThirtyOneAndThirtyTwo() {
        val source = "<table class='xfyq_con'><tr><td>上午</td><td>第31-32节</td><td>物理<br>王老师[1-2]周A</td>" + "<td></td>".repeat(6) + "</tr></table>"
        assertEquals(listOf(JwCourse("物理", "A", "王老师", 1, 31, 32, 1, 2)), JwHitParser(source).generateCourseList())
    }

    @Test fun buaaPreservesExplicitSectionsThirtyOneAndThirtyTwo() {
        val source = """{"datas":{"arrangedList":[{"courseName":"物理","dayOfWeek":1,"beginSection":31,"endSection":32,"placeName":"A","cellDetail":[{"text":"王老师[1-2周]"}]}]}}"""
        assertEquals(listOf(JwCourse("物理", "A", "王老师", 1, 31, 32, 1, 2)), JwBuaaParser(source).generateCourseList())
    }
}
