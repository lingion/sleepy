package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic DOM vectors built from the pinned 6.6.2 parser field contracts. */
class JwNativeHtmlProtocolTest {
    @Test fun `umooc reads cell identity and separate course blocks with correct parity`() {
        val html = """<table id="timetable"><tbody>
          <tr class="infolist_hr_common"><th>下午</th><td></td><td id="5-7">
            &lt;&lt;概率论&gt;&gt;<br>A102<br>张老师<br>32学时<br>2-8双周<br>
            &lt;&lt;研讨&gt;&gt;<br>B203<br>李老师<br>16学时<br>1,5,9周<br>
          </td></tr>
          <tr class="infolist_hr_common"><th>第9节</th><td id="2-10">
            &lt;&lt;晚课&gt;&gt;<br>C301<br>周老师<br>16学时<br>全周<br>
          </td></tr>
        </tbody></table>"""
        assertEquals(listOf(
            JwCourse("概率论", "A102", "张老师", 5, 7, 7, 2, 8, 2),
            JwCourse("研讨", "B203", "李老师", 5, 7, 7, 1, 1),
            JwCourse("研讨", "B203", "李老师", 5, 7, 7, 5, 5),
            JwCourse("研讨", "B203", "李老师", 5, 7, 7, 9, 9),
            JwCourse("晚课", "C301", "周老师", 2, 9, 9, 1, 18),
        ), JwUmoocParser(html).generateCourseList())
    }

    @Test fun `yl reads course boxes fields and comma separated period endpoints`() {
        val html = """<div class="app-components-CourseTimeTable-styles-timeTable-hash"><table><tbody>
          <tr><td>上午</td><td></td><td>
            <div class="app-components-CourseTimeTable-styles-courseBox-hash">
              <div>线性代数</div>
              <div><span>时间</span><span>2-8双周;11周</span><span>第3,4节</span></div>
              <div>数学学院</div><div>A301(主楼)</div><div>王老师</div>
            </div>
            <div class="app-components-CourseTimeTable-styles-courseBox-hash">
              <div>线性代数</div>
              <div><span>时间</span><span>1-7单周</span><span>第3,4节</span></div>
              <div>数学学院</div><div>B202(分楼)</div><div>李老师</div>
            </div>
          </td></tr></tbody></table></div>"""
        assertEquals(listOf(
            JwCourse("线性代数", "A301", "王老师", 2, 3, 4, 2, 8, 2),
            JwCourse("线性代数", "A301", "王老师", 2, 3, 4, 11, 11),
            JwCourse("线性代数", "B202", "李老师", 2, 3, 4, 1, 7, 1),
        ), JwYlParser(html).generateCourseList())
    }

    @Test fun `south soft native kb table skips unnumbered rows and keeps teacher room separate`() {
        val html = """<table id="kb"><tbody>
          <tr><td>无节次</td><td></td><td></td></tr>
          <tr><td>第1节</td><td></td><td><div>
            <span>课程名称:数据库||班级：计科</span><span>3-12周</span>
            <span>钱老师(连续周)</span><span>信息楼201</span>
          </div></td></tr></tbody></table>"""
        assertEquals(listOf(JwCourse("数据库", "信息楼201", "钱老师", 2, 1, 1, 3, 12)),
            JwSouthSoftParser(html).generateCourseList())
    }
}
