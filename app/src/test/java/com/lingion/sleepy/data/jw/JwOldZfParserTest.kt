package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
 * T1 新增 14 用例按缺口编号命名:
 *   G1 (N-M节覆盖startNode) / G2 (COURSE_PROPERTY 23 项) / G3 (hasTypeFlag 复位) /
 *   G4 (末尾 token 越界) / G5 (blacktab 兜底) / G6 (合并行头) / G7 (周天别名) /
 *   G12 (result[1]=step 移植漏行, endNode 恒错) / composite (G1+G2+G6 复合)
 * 既有 9 用例(issue #5 回归)保留在文件后半。
 *
 * 纯 JVM，不依赖 Android。
 */
class JwOldZfParserTest {

    // ───────────── G2: COURSE_PROPERTY 补齐 23 项 ─────────────

    @Test
    fun `G2 通识必修 属性行 5 字段走 5 行分支`() {
        val html = """
            <html><body><table id="Table1">
              <tr><td>第1节</td><td>
                <a>大学英语<br>通识必修<br>周一第1,2节{第1-16周}<br>张三<br>A101</a>
              </td></tr>
            </table></body></html>
        """.trimIndent()
        val courses = JwOldZfParser(html).generateCourseList()
        assertEquals(1, courses.size)
        val c = courses[0]
        assertEquals("大学英语", c.name)
        assertEquals("张三", c.teacher)
        assertEquals("A101", c.room)
        assertEquals(1, c.startNode)
        assertEquals(2, c.endNode)
        assertEquals(1, c.startWeek)
        assertEquals(16, c.endWeek)
    }

    @Test
    fun `G2 体育必 属性行 4 字段无老师`() {
        val html = """
            <html><body><table id="Table1">
              <tr><td>第1节</td><td>
                <a>体育<br>体育必<br>周一第1节{第1-16周}<br>操场</a>
              </td></tr>
            </table></body></html>
        """.trimIndent()
        val courses = JwOldZfParser(html).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("体育", courses[0].name)
        assertEquals("", courses[0].teacher)
        assertEquals("操场", courses[0].room)
    }

    @Test
    fun `G2 四个缺失属性词联合场景 通识必修 专业必修 学科必修 体育必`() {
        val html = """
            <html><body><table id="Table1">
              <tr><td>时间</td><td>星期一</td><td>星期二</td><td>星期三</td><td>星期四</td><td>星期五</td></tr>
              <tr><td>第1节</td>
                <td>&nbsp;</td>
                <td><a>大学英语<br>通识必修<br>周二第1,2节{第1-16周}<br>张老师<br>B203</a></td>
                <td><a>程序设计<br>专业必修<br>周三第1,2节{第2-18周}<br>赵老师<br>D202</a></td>
                <td><a>高等数学<br>学科必修<br>周四第1,2节{第3-15周|单周}<br>王老师<br>A101</a></td>
                <td><a>体育<br>体育必<br>周五第1节{第1-16周}<br>陈老师<br>体育馆</a></td>
              </tr>
            </table></body></html>
        """.trimIndent()
        val courses = JwOldZfParser(html).generateCourseList()
        assertEquals(4, courses.size)
        val byName = courses.associateBy { it.name }
        assertEquals("张老师", byName["大学英语"]!!.teacher)
        assertEquals("赵老师", byName["程序设计"]!!.teacher)
        assertEquals("王老师", byName["高等数学"]!!.teacher)
        assertEquals(1, byName["高等数学"]!!.type)   // |单周
        assertEquals("陈老师", byName["体育"]!!.teacher)
        assertEquals("体育馆", byName["体育"]!!.room)
    }

    // ───────────── G1: (N-M节) 写回 startNode ─────────────

    @Test
    fun `G1 行头第3节时 0102节 覆盖 startNode 为 1`() {
        // 行头给 node=3, 时间串 (01-02节) 应把 startNode 拉回 1、step=2 → endNode=2。
        // 修复前: startNode 保持 3 → endNode=4 (错)。行头与时间串起点必须不同才能区分红绿。
        val html = """
            <html><body><table id="Table1">
              <tr><td>第3节</td><td>
                <a>高等数学<br>通识必修<br>周一第5节(01-02节){第1-16周}<br>张老师<br>A101</a>
              </td></tr>
            </table></body></html>
        """.trimIndent()
        val courses = JwOldZfParser(html).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals(1, courses[0].startNode)
        assertEquals(2, courses[0].endNode)
    }

    // ───────────── G3: hasTypeFlag 复位 ─────────────

    @Test
    fun `G3 type1 hasTypeFlag 每门课后复位 同格第二门课名不误取`() {
        // 第一门带 '通识必修' 属性 token → 第一门 name=split[0]; 复位后第二门(无属性)
        // name=split[preIndex-1]='大学英语'。修复前第二门 name 错取 'A101'(split[preIndex-2])。
        val html = """
            <html><body><table id="Table1">
              <tr><td>第1节</td>
                <td>高等数学 通识必修 {第1-16周} 张老师 A101 大学英语 {第2-16周|单周} 李老师 B202</td>
              </tr>
            </table></body></html>
        """.trimIndent()
        val courses = JwOldZfParser(html, 1).generateCourseList()
        assertEquals(2, courses.size)
        assertEquals("高等数学", courses[0].name)
        assertEquals("张老师", courses[0].teacher)
        assertEquals("A101", courses[0].room)
        assertEquals("大学英语", courses[1].name)
        assertEquals("李老师", courses[1].teacher)
        assertEquals("B202", courses[1].room)
    }

    // ───────────── G4: 时间 token 为末尾 token 不越界 ─────────────

    @Test
    fun `G4 type1 时间 token 是最后 token 不抛 IOOBE teacher room 留空`() {
        // split=[高等数学, {第1-16周}]: i=1=preIndex → 末尾分支 (i-preIndex)==0 走 else
        // 原版取 split[2] → IndexOutOfBoundsException; 修复后 teacher/room 留空。
        val html = """
            <html><body><table id="Table1">
              <tr><td>第1节</td><td>高等数学 {第1-16周}</td></tr>
            </table></body></html>
        """.trimIndent()
        try {
            val courses = JwOldZfParser(html, 1).generateCourseList()
            assertEquals(1, courses.size)
            assertEquals("高等数学", courses[0].name)
            assertEquals("", courses[0].teacher)
            assertEquals("", courses[0].room)
            assertEquals(1, courses[0].startWeek)
            assertEquals(16, courses[0].endWeek)
        } catch (e: IndexOutOfBoundsException) {
            fail("末尾时间 token 不应越界: ${e.message}")
        }
    }

    @Test
    fun `G4 type1 逗号合并行头加末尾时间 token 对抗组合不崩`() {
        // 来自 /tmp/jw_fixtures/adversarial/merged-rows/zf_1_rowspan_style_grid_crash.html 语义:
        // 行1 '第1,2节' 合并行头 + '高等数学 {第1-16周}' 末尾时间 token;
        // 行2 '第三节' 正常行头 + 完整 [名,时间,老师,教室] token 流。
        val html = """
            <html><body><table id="Table1">
              <tr><td>第1,2节</td><td>高等数学 {第1-16周}</td><td>&nbsp;</td><td>&nbsp;</td></tr>
              <tr><td>第三节</td><td>&nbsp;</td><td>大学英语 {第2-16周} 张老师 B-205</td><td>&nbsp;</td></tr>
            </table></body></html>
        """.trimIndent()
        try {
            val courses = JwOldZfParser(html, 1).generateCourseList()
            assertEquals(2, courses.size)
            val math = courses.first { it.name == "高等数学" }
            assertEquals(1, math.startNode)   // '第1,2节' 首段
            assertEquals("", math.teacher)
            assertEquals("", math.room)
            val eng = courses.first { it.name == "大学英语" }
            assertEquals(3, eng.startNode)    // '第三节' 行头
            assertEquals("张老师", eng.teacher)
            assertEquals("B-205", eng.room)
        } catch (e: IndexOutOfBoundsException) {
            fail("对抗组合不应越界: ${e.message}")
        }
    }

    // ───────────── G5: 表格选择器兜底 ─────────────

    @Test
    fun `G5 class blacktab 表格可被解析`() {
        val html = """
            <html><body><table class="blacktab">
              <tr><td>第1节</td><td>
                <a>高等数学<br>通识必修<br>周一第1,2节{第1-16周}<br>张老师<br>A101</a>
              </td></tr>
            </table></body></html>
        """.trimIndent()
        val courses = JwOldZfParser(html).generateCourseList()
        assertTrue("blacktab 兜底应解析出课程, 实得 ${courses.size}", courses.isNotEmpty())
        assertEquals("高等数学", courses[0].name)
    }

    @Test
    fun `G5 无标识表格按含星期一文本兜底`() {
        val html = """
            <html><body>
              <table><tr><td>导航: 首页 选课 成绩</td></tr></table>
              <table><tr>
                <td>第1节</td><td><a>高数<br>周一第1,2节{第1-16周}<br>张老师<br>A101</a></td>
                <td>星期一</td><td>星期二</td>
              </tr></table>
            </body></html>
        """.trimIndent()
        val courses = JwOldZfParser(html).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("高数", courses[0].name)
    }

    // ───────────── G6: 合并节次行头 ─────────────

    @Test
    fun `G6 数字合并行头 第3-4节 按首段3解析 不递增 countDay`() {
        // 修复前: '第3-4节' 返回 -1 → 当课程格 countDay++ → 该行起 day 整体 +1,
        // 且 node 沿用上一行(1) → 大学英语 startNode=1/endNode=2 (错)。
        val html = """
            <html><body><table id="Table1">
              <tr>
                <td colspan="2">时间</td>
                <td>星期一</td><td>星期二</td><td>星期三</td><td>星期四</td><td>星期五</td>
              </tr>
              <tr>
                <td colspan="2">第一节</td>
                <td><a>高等数学<br>周一第1,2节{第1-16周}<br>张老师<br>A101</a></td>
                <td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>
              </tr>
              <tr>
                <td colspan="2">第3-4节</td>
                <td>&nbsp;</td><td>&nbsp;</td>
                <td><a>大学英语<br>周三第3,4节{第2-16周|双周}<br>王老师<br>B202</a></td>
                <td>&nbsp;</td><td>&nbsp;</td>
              </tr>
              <tr>
                <td colspan="2">第五节</td>
                <td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>
                <td><a>大学物理<br>第1-16周<br>李老师<br>C303</a></td>
                <td>&nbsp;</td>
              </tr>
            </table></body></html>
        """.trimIndent()
        val courses = JwOldZfParser(html).generateCourseList()
        assertEquals(3, courses.size)
        val eng = courses.first { it.name == "大学英语" }
        assertEquals(3, eng.startNode)   // 合并行头首段 3, 修复前为 1
        assertEquals(4, eng.endNode)
        assertEquals(2, eng.type)        // |双周
        // 大学物理: timeInfo 无 花括号 无 周X 前缀 → day 走 cDay 兜底, cDay 不因合并行头错位
        val phys = courses.first { it.name == "大学物理" }
        assertEquals(5, phys.startNode)
        assertEquals(1, phys.startWeek)  // 无 {第N-M周} → 默认 1
        assertEquals(20, phys.endWeek)   // 默认 20
    }

    @Test
    fun `G6 中文区间行头 第一节-第二节 按首段一解析`() {
        // '第一节-第二节' 剥首尾后是 '一节-第二', 首段须剥 第/节 才能查 CN_NUM。
        // 修复前: 两行 node 均 -1 → startNode=-1, toCourseEntities coerceAtLeast(1) 压成第1节。
        val html = """
            <html><body><table id="Table1">
              <tr><td colspan="2">时间</td><td>星期一</td><td>星期二</td></tr>
              <tr><td colspan="2">上午</td><td>&nbsp;</td><td>&nbsp;</td></tr>
              <tr>
                <td colspan="2">第一节-第二节</td>
                <td><a>高等数学<br>周一第1,2节{第1-16周}<br>张老师<br>A101</a></td>
                <td>&nbsp;</td>
              </tr>
              <tr><td colspan="2">下午</td><td>&nbsp;</td><td>&nbsp;</td></tr>
              <tr>
                <td colspan="2">第三节-第四节</td>
                <td>&nbsp;</td>
                <td><a>大学物理<br>周二第3,4节{第1-16周|单周}<br>李老师<br>C303</a></td>
              </tr>
            </table></body></html>
        """.trimIndent()
        val courses = JwOldZfParser(html).generateCourseList()
        assertEquals(2, courses.size)
        val math = courses.first { it.name == "高等数学" }
        assertEquals(1, math.startNode)
        assertEquals(2, math.endNode)
        val phys = courses.first { it.name == "大学物理" }
        assertEquals(3, phys.startNode)
        assertEquals(4, phys.endNode)
        assertEquals(1, phys.type)   // |单周
    }

    // ───────────── G7: 周天别名 ─────────────

    @Test
    fun `G7 周天 被识别为 day7`() {
        // 行头须用 第10节: startNode 只来自行头(或 (N-M节) 覆盖), timeInfo 里的
        // '第10节' 字样不参与 startNode 解析(它只走 contains→step=1 路径)。
        // 修复前 CHINESE_WEEK_LIST 无 '周天' → day 落 cDay 网格列号(本例=1, 错)。
        val html = """
            <html><body><table id="Table1">
              <tr><td>第10节</td><td>
                <a>文化地理<br>周天第10节{第17-17周}<br>李老师<br>E110</a>
              </td></tr>
            </table></body></html>
        """.trimIndent()
        val courses = JwOldZfParser(html).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals(7, courses[0].day)
        assertEquals(10, courses[0].startNode)
        assertEquals(10, courses[0].endNode)   // contains("第10节") → step=1
        assertEquals(17, courses[0].startWeek)
        assertEquals(17, courses[0].endWeek)
    }

    // ───────────── G12: result[1] = step 移植漏行 ─────────────

    @Test
    fun `G12 逗号连堂 endNode 等于 startNode 加 step 减一`() {
        // 缺 result[1]=step 时 time[1] 恒 0 → endNode = startNode - 1 < startNode。
        // "第1,2节" 逗号规则算出 step=2, 必须写入返回值, 否则 "周一第1,2节" 变单节。
        val html = """
            <html><body><table id="Table1">
              <tr><td>第1节</td><td>
                <a>数据结构<br>周一第1,2节{第1-16周}<br>孙老师<br>D401</a>
              </td></tr>
            </table></body></html>
        """.trimIndent()
        val courses = JwOldZfParser(html).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals(1, courses[0].startNode)
        assertEquals("逗号连堂 step=2 必须写回(endNode=2), 修复前 endNode=0", 2, courses[0].endNode)
    }

    @Test
    fun `G12 无节次信息的默认 step 为 1`() {
        // timeInfo 只有周次无节次串: step 走 fallback=1, endNode==startNode。
        val html = """
            <html><body><table id="Table1">
              <tr><td>第5节</td><td>
                <a>形势与政策<br>{第1-16周}<br>周老师<br>F208</a>
              </td></tr>
            </table></body></html>
        """.trimIndent()
        val courses = JwOldZfParser(html).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals(5, courses[0].startNode)
        assertEquals("默认 step=1 → endNode==startNode", 5, courses[0].endNode)
    }

    // ───────────── 复合: G1+G2+G6 ─────────────

    @Test
    fun `composite 属性词加节点覆盖加双课同格`() {
        // 语义来自 /tmp/jw_fixtures/zf-old-variants/course_property_with_node_override.html(缩减版)
        val html = """
            <html><body><table id="Table1">
              <tr><td colspan="2">时间</td><td>星期一</td><td>星期二</td></tr>
              <tr>
                <td colspan="2">第一节</td>
                <td><a>高等数学<br>通识必修<br>周一第2节(01-02节){第1-16周}<br>张老师<br>A101</a><br><br><a>体育<br>体育必<br>周一第2节{第1-16周}<br>操场</a></td>
                <td>&nbsp;</td>
              </tr>
              <tr><td colspan="2">第二节</td><td>&nbsp;</td><td>&nbsp;</td></tr>
              <tr>
                <td colspan="2">第三节</td>
                <td>&nbsp;</td>
                <td><a>大学英语<br>周一第5,6节{第2-16周|单周}<br>李老师<br>B202</a></td>
              </tr>
            </table></body></html>
        """.trimIndent()
        val courses = JwOldZfParser(html).generateCourseList()
        assertEquals(3, courses.size)
        val math = courses.first { it.name == "高等数学" }
        assertEquals("张老师", math.teacher)
        assertEquals("A101", math.room)
        assertEquals(2, math.endNode)     // (01-02节) step=2
        val pe = courses.first { it.name == "体育" }
        assertEquals("", pe.teacher)      // 属性 4 行分支无老师
        assertEquals("操场", pe.room)
        val eng = courses.first { it.name == "大学英语" }
        assertEquals(3, eng.startNode)
        assertEquals(4, eng.endNode)
        assertEquals(1, eng.type)
    }

    // ───────────── 以下为既有 issue #5 回归用例(原样保留) ─────────────

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
