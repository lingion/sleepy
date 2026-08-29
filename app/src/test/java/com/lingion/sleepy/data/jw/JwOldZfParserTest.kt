package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JwOldZfParser 单测 — issue #5（临沂大学，正方教务，个人课表页无法抓取）
 *
 * 根因：sleepy 移植时把 TYPE_ZF/TYPE_ZF_1 错接到 JwNewZfParser（只认新版），
 * 老版正方（default2.aspx 时代，xskbcx.aspx 个人课表页）的表格结构
 * （id="Table1" + <a>课程名<br>属性<br>{第N-M周}<br>老师<br>教室</a>）
 * 没有任何解析器能识别 → 永远报"解析结果为空"。
 *
 * 修复：忠实移植上游 dIT8Zv/WakeupSchedule_BUPT (Apache-2.0) ZhengFangParser。
 *
 * 纯 JVM，不依赖 Android。
 */
class JwOldZfParserTest {

    /**
     * 老版正方个人课表页（xskbcx.aspx）典型结构：
     *   - 外层表格 id="Table1"
     *   - 节次行头："第1节"（中文数字变体"第一节"也在真实页面出现）
     *   - 课程单元格：<a ...>课程名<br>周次信息<br>老师<br>教室</a>（<br><br> 分隔多门）
     *   - 周次格式：{第1-16周} / {第2-16周}（花括号）
     */
    private val mockOldZfHtml = """
<html><head><title>学生个人课表</title></head><body>
<form name="Form1">
<table id="Table1" border="1">
  <tr><td class="tdtitle">时间</td><td>星期一</td><td>星期二</td><td>星期三</td><td>星期四</td><td>星期五</td></tr>
  <tr>
    <td>第1节</td>
    <td>
      <a href="xxdm.aspx">高等数学<br>{第1-16周}<br>张三<br>A-101</a>
    </td>
    <td></td>
    <td>
      <a href="xxdm.aspx">大学英语<br>{第2-16周}<br>李四<br>B-205</a>
    </td>
    <td></td>
    <td></td>
  </tr>
  <tr>
    <td>第3节</td>
    <td></td>
    <td>
      <a href="xxdm.aspx">数据结构<br>{第1-16周}<br>王五<br>C-102</a>
    </td>
    <td></td>
    <td>
      <a href="xxdm.aspx">大学物理<br>{第1-8周}<br>赵六<br>D-201</a><br><br>
      <a href="xxdm.aspx">选修课<br>{第9-16周}<br>钱七<br>E-305</a>
    </td>
    <td></td>
  </tr>
</table>
</form>
</body></html>
    """.trimIndent()

    @Test
    fun `parses old zf Table1 html`() {
        val courses = JwOldZfParser(mockOldZfHtml).generateCourseList()
        println("老版正方解析出 ${courses.size} 门课:")
        courses.forEach { println("  ${it.name} 周${it.day} 第${it.startNode}-${it.endNode}节 ${it.startWeek}-${it.endWeek}周 type=${it.type}") }
        assertTrue("老版正方课表应解析出课程（issue #5 主断言）", courses.isNotEmpty())
    }

    @Test
    fun `course name and room are extracted`() {
        val courses = JwOldZfParser(mockOldZfHtml).generateCourseList()
        val math = courses.firstOrNull { it.name == "高等数学" }
        assertTrue("应解析出 高等数学", math != null)
        assertEquals("教室应从最后一行提取", "A-101", math!!.room)
        assertEquals("老师应从倒数第二行提取", "张三", math.teacher)
    }

    @Test
    fun `week range parsed from brace format`() {
        val courses = JwOldZfParser(mockOldZfHtml).generateCourseList()
        val math = courses.first { it.name == "高等数学" }
        assertEquals("1-16周", 1, math.startWeek)
        assertEquals("1-16周", 16, math.endWeek)
        val phys = courses.first { it.name == "大学物理" }
        assertEquals("1-8周", 1, phys.startWeek)
        assertEquals("1-8周", 8, phys.endWeek)
    }

    @Test
    fun `single-odd-week marker sets type`() {
        val src = """
<html><body><table id="Table1">
  <tr><td>第1节</td><td><a>毛概<br>{第1-15周|单周}<br>甲<br>Z-1</a></td></tr>
</table></body></html>
        """.trimIndent()
        val courses = JwOldZfParser(src).generateCourseList()
        assertTrue(courses.isNotEmpty())
        assertEquals("单周标记 → type=1", 1, courses[0].type)
        assertEquals(1, courses[0].startWeek)
        assertEquals(15, courses[0].endWeek)
    }

    @Test
    fun `day falls back to grid column when week string lacks weekday`() {
        // 老版正方课表单元格里通常不带"周一"，day 由 Table1 列位置决定
        val courses = JwOldZfParser(mockOldZfHtml).generateCourseList()
        val math = courses.first { it.name == "高等数学" }
        assertEquals("第1行第1列 → 周一", 1, math.day)
        val eng = courses.first { it.name == "大学英语" }
        assertEquals("第1行第3列 → 周三", 3, eng.day)
    }

    @Test
    fun `chinese numeral node header advances node pointer`() {
        val src = """
<html><body><table id="Table1">
  <tr><td>第一节</td><td><a>课程甲<br>{第1-16周}<br>老师甲<br>J-1</a></td><td></td></tr>
  <tr><td>第三节</td><td></td><td><a>课程乙<br>{第1-16周}<br>老师乙<br>J-2</a></td></tr>
</table></body></html>
        """.trimIndent()
        val courses = JwOldZfParser(src).generateCourseList()
        assertTrue(courses.isNotEmpty())
        val a = courses.first { it.name == "课程甲" }
        assertEquals("第一节 → startNode=1", 1, a.startNode)
        val b = courses.first { it.name == "课程乙" }
        assertEquals("第三节 → startNode=3", 3, b.startNode)
    }

    @Test
    fun `handles empty html gracefully`() {
        val result = JwOldZfParser("<html><body>空</body></html>").generateCourseList()
        assertEquals(0, result.size)
    }

    @Test
    fun `tryAllParsers now covers old zf`() {
        // 回归：自定义 URL 场景 type=null 走 tryAllParsers，老正方页面必须能被兜底解析
        val courses = JwImportViewModel.tryAllParsersForTest(mockOldZfHtml)
        assertTrue("tryAllParsers 应能解析老版正方页面（issue #5 兜底断言）", courses.isNotEmpty())
    }

    @Test
    fun `old zf urls are detected by fingerprint`() {
        // issue #5：手输地址后协议猜不出 → 无法抓取。老正方/强智指纹必须命中
        val vm = JwImportViewModel(JwImportViewModelTestApp())
        assertEquals("zf", vm.detectProtocolFromUrl("http://202.199.155.33/default2.aspx"))
        assertEquals("zf", vm.detectProtocolFromUrl("https://jwgl.example.edu.cn/xskbcx.aspx"))
        assertEquals("qz", vm.detectProtocolFromUrl("http://jwxt.lyu.edu.cn/jxd/"))
        assertEquals("qz", vm.detectProtocolFromUrl("http://jwxt.ahut.edu.cn/jsxsd/"))
        // 原有规则不回归
        assertEquals("zf_new", vm.detectProtocolFromUrl("http://jwglxt.buct.edu.cn/"))
        assertEquals("wisedu", vm.detectProtocolFromUrl("https://jwgl.hrbeu.edu.cn/jwapp/sys/wdkb/*default/index.do"))
    }
}

/** 纯 JVM 测试用空 Application（detectProtocolFromUrl 不触碰 Context） */
private class JwImportViewModelTestApp : android.app.Application()
