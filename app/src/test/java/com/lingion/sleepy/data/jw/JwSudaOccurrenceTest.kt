package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwSudaOccurrenceTest {
    @Test fun `Suda row spans and centered gutter do not move later weekday columns`() {
        val source = """<table id="MainWork_DataGrid1">
          <tr align="center"><td>节次</td><td>星期一</td><td>星期二</td></tr>
          <tr><td align="center">第1节</td>
            <td rowspan="2">课程:数学<br>(A101)<br>第1-8周<br>主讲教师:张老师</td><td></td></tr>
          <tr><td align="center">第2节</td>
            <td>课程:物理<br>(B202)<br>第2-10周双<br>主讲教师:李老师</td></tr>
        </table>"""
        assertEquals(listOf(
            JwCourse("数学", "A101", "张老师", 1,1,2,1,8),
            JwCourse("物理", "B202", "李老师", 2,2,2,2,10,2),
        ), JwSudaParser(source).generateCourseList())
    }

    @Test fun `Suda separates multiple courses in the same cell and never invents weeks`() {
        val source = """<table id="DataGrid1"><tr align="center"><td>星期一</td></tr><tr><td>
          课程:课程甲<br>(A101)<br>第1-7周单<br>主讲教师:甲老师<br><br>
          课程:课程乙<br>(B202)<br>第2-8周双<br>主讲教师:乙老师<br><br>
          课程:无周次课程<br>(C303)<br>主讲教师:丙老师
        </td></tr></table>"""
        assertEquals(listOf(
            JwCourse("课程甲", "A101", "甲老师", 1,1,1,1,7,1),
            JwCourse("课程乙", "B202", "乙老师", 1,1,1,2,8,2),
        ), JwSudaParser(source).generateCourseList())
    }
}
