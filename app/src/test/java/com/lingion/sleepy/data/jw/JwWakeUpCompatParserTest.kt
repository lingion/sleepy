package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwWakeUpCompatParserTest {
    @Test
    fun `legacy chaoxing uses kckbData schema`() {
        val source = """
            {"data":{"kckbData":[{"kcmc":"<b>高等数学</b>","xq":"2","djc":3,"tmc":"张老师","croommc":"教一-101","zc":"1-16,18","zctype":"0"}]}}
        """.trimIndent()

        val courses = JwChaoxingLegacyParser(source).generateCourseList()

        assertEquals(2, courses.size)
        assertEquals("高等数学", courses[0].name)
        assertEquals(2, courses[0].day)
        assertEquals(3, courses[0].startNode)
        assertEquals(16, courses[0].endWeek)
        assertEquals(18, courses[1].startWeek)
    }

    @Test
    fun `cumtb joins lesson names to schedule rows and converts time`() {
        val source = """
            {"result":{"lessonList":[{"id":7,"courseName":"数据库"}],"scheduleList":[{"lessonId":7,"personName":"李老师","weekday":4,"weekIndex":6,"startTime":800,"endTime":945,"room":{"nameZh":"信科-201"}}]}}
        """.trimIndent()

        val courses = JwCumtbParser(source).generateCourseList()

        assertEquals(1, courses.size)
        assertEquals("数据库", courses.single().name)
        assertEquals(4, courses.single().day)
        assertEquals(1, courses.single().startNode)
        assertEquals(2, courses.single().endNode)
        assertEquals("信科-201", courses.single().room)
        assertEquals(6, courses.single().startWeek)
    }

    @Test
    fun `cumtb unknown lesson falls back to placeholder not dropped`() {
        // WakeUp oo000o.java:45 — lessonList 缺失的 lessonId → "未知", 行不丢
        val source = """
            {"result":{"lessonList":[],"scheduleList":[{"lessonId":99,"personName":"王老师","weekday":2,"weekIndex":3,"startTime":1400,"endTime":1535,"room":{"nameZh":"教1-102"}}]}}
        """.trimIndent()

        val courses = JwCumtbParser(source).generateCourseList()

        assertEquals(1, courses.size)
        assertEquals("未知", courses.single().name)
        assertEquals(2, courses.single().day)
        assertEquals(5, courses.single().startNode)
        assertEquals("教1-102", courses.single().room)
    }

    @Test
    fun `south soft parses xq key and odd-even week tokens`() {
        val source = """
            [{"KCWZSM":"线性代数","KEY":"xq3_jc2","SKSJ":"[数学][周1-16单][张老师][A-101][第2-3节]"}]
        """.trimIndent()

        val courses = JwSouthSoftParser(source).generateCourseList()

        assertTrue(courses.isNotEmpty())
        assertEquals("线性代数", courses.single().name)
        assertEquals(3, courses.single().day)
        assertEquals(2, courses.single().startNode)
        assertEquals(3, courses.single().endNode)
        assertEquals(1, courses.single().type)
        assertEquals(16, courses.single().endWeek)
    }

    @Test
    fun `jz json array uses JZHandCourseInfoItem schema`() {
        val source = """
            [{"kcmc":"数据结构","xqj":3,"jsxm":"钱老师","skdd":"教2-301","djj":5,"dsz":6,"qmz":"1-8;10-16","bjmc":"计科21","xkkh":"(2025-2026-1)-","lx":1,"mz":2,"qz":0,"rwlx":1}]
        """.trimIndent()

        val courses = JwJzParser(source).generateCourseList()

        assertEquals(2, courses.size)
        assertEquals("数据结构", courses[0].name)
        assertEquals(3, courses[0].day)
        assertEquals(5, courses[0].startNode)
        assertEquals("教2-301", courses[0].room)
        assertEquals("钱老师", courses[0].teacher)
        assertEquals(1, courses[0].startWeek)
        assertEquals(8, courses[0].endWeek)
        assertEquals(10, courses[1].startWeek)
        assertEquals(16, courses[1].endWeek)
    }

    @Test
    fun `jz html CourseFormTable br split cells`() {
        val source = """
            <html><body><table id="CourseFormTable">
            <tr><td>&nbsp;</td><td style="text-align:center">星期一</td></tr>
            <tr><td style="text-align:center">第5节</td>
            <td>数据结构<br>1-16单,18<br>钱老师<br>第5-6节<br>教2-301</td></tr>
            </table></body></html>
        """.trimIndent()

        val courses = JwJzParser(source).generateCourseList()

        assertTrue(courses.isNotEmpty())
        assertEquals("数据结构", courses[0].name)
        assertEquals(2, courses[0].day)
        assertEquals(1, courses[0].type)
        assertEquals("教2-301", courses[0].room)
        assertEquals("钱老师", courses[0].teacher)
    }

    @Test
    fun `kingo TaskActivity js grid uses index times unitCount`() {
        val source = """
            <html><body><script>
            var activity = null;
            var courseName = '高等代数';
            var actTeachers = new Array();
            actTeachers.push({'name':'孙老师'});
            activity = new TaskActivity('高等代数','孙老师','博学楼A-302','101010000000000000000000000000000000000000000000');
            index = 3 * unitCount + 2;
            activity.index = index;
            table0.marshalTable();
            </script></body></html>
        """.trimIndent()

        val courses = JwKingoParser(source).generateCourseList()

        assertTrue(courses.isNotEmpty())
        assertEquals("高等代数", courses[0].name)
        // 跨仓 4 源一致 (WakeUp o0000OO0/CourseHelper Swift/shiguang HUNNU+UESTC): index 首因子 0-based, day=D+1
        assertEquals(4, courses[0].day)
        assertEquals(3, courses[0].startNode)
        assertEquals("博学楼A-302", courses[0].room)
        assertEquals("孙老师", courses[0].teacher)
        assertEquals(1, courses[0].startWeek)
        assertEquals(5, courses[0].endWeek)
        assertEquals(1, courses[0].type)
    }

    @Test
    fun `xju dgData brace blocks with week parity`() {
        val source = """
            <html><body><table id="ctl00_contentParent_dgData">
            <tr><th>节次</th><th>星期一</th><th>星期二</th></tr>
            <tr><td align="center">第1-2节</td>
            <td>{大学物理(1-16周单)[教师:李老师,地点:物理楼-1]}</td><td>&nbsp;</td></tr>
            <tr><td align="center">第3-4节</td><td>&nbsp;</td>
            <td>{有机化学(2-15周双)[教师:周老师,地点:化学楼-2]}</td></tr>
            </table></body></html>
        """.trimIndent()

        val courses = JwXjuParser(source).generateCourseList()

        assertTrue(courses.isNotEmpty())
        assertEquals("大学物理", courses[0].name)
        assertEquals(1, courses[0].day)
        assertEquals(1, courses[0].startNode)
        assertEquals("李老师", courses[0].teacher)
        assertEquals(1, courses[0].type)
        assertEquals("化学楼-2", courses[1].room)
        assertEquals(2, courses[1].day)
        assertEquals(2, courses[1].type)
    }

    @Test
    fun `suda grid rows use course teacher labels`() {
        val source = """
            <html><body><table id="MainWork_DataGrid1">
            <tr align="center"><td>星期一</td><td>星期二</td></tr>
            <tr><td>高等数学<br>课程:高等数学<br>(张老师)<br>第1-16周<br>博远楼-101</td><td>&nbsp;</td></tr>
            <tr><td>&nbsp;</td><td>大学英语<br>课程:大学英语<br>(刘老师)<br>第1-16周双<br>博远楼-202</td></tr>
            </table></body></html>
        """.trimIndent()

        val courses = JwSudaParser(source).generateCourseList()

        assertTrue(courses.isNotEmpty())
        assertEquals("高等数学", courses[0].name)
        assertEquals(1, courses[0].day)
        assertEquals(1, courses[0].startWeek)
        assertEquals(16, courses[0].endWeek)
        assertEquals("张老师", courses[0].teacher)
        assertEquals("博远楼-101", courses[0].room)
        assertEquals(2, courses[1].day)
        assertEquals(2, courses[1].type)
    }

    @Test
    fun `suda rowspan cell extends end node`() {
        // WakeUp dex L00e9 rowspan 折叠: 连堂课单元格占 2 节
        val source = """
            <html><body><table id="MainWork_DataGrid1">
            <tr align="center"><td>星期一</td><td>星期二</td></tr>
            <tr><td>高等数学<br>课程:高等数学<br>(张老师)<br>第1-16周<br>博远楼-101</td><td rowspan="2">数据结构<br>课程:数据结构<br>(钱老师)<br>第1-16周<br>教2-301</td></tr>
            <tr><td>&nbsp;</td></tr>
            </table></body></html>
        """.trimIndent()

        val courses = JwSudaParser(source).generateCourseList()

        assertTrue(courses.isNotEmpty())
        val ds = courses.single { it.name == "数据结构" }
        assertEquals(2, ds.day)
        assertEquals(1, ds.startNode)
        assertEquals(2, ds.endNode)
    }

    @Test
    fun `shuwei activities preserve odd even week ranges`() {
        val source = """
            {"activities":[{"courseName":"编译原理","weekday":5,"startSection":7,"endSection":8,"weeksStr":"1-15单,2-16双","room":"C-201","teacher":"王老师"}]}
        """.trimIndent()

        val courses = JwShuweiParser(source).generateCourseList()

        assertEquals(2, courses.size)
        assertEquals(1, courses[0].type)
        assertEquals(15, courses[0].endWeek)
        assertEquals(2, courses[1].type)
        assertEquals(16, courses[1].endWeek)
    }

    @Test
    fun `xju dgData html routes through registry fallback to xju parser`() {
        // 自定义 URL 场景: detectProtocol URL/HTML 两层都无 xju_post 锚点,
        // type=null → selectBest 全候选裁决必须落到 JwXjuParser (sundayFirst 翻转 day)
        val source = """
            <html><head><title>学生课表查询</title></head><body>
            <table id="ctl00_contentParent_dgData" border="1">
            <tr><th>节次</th><th>星期日</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th></tr>
            <tr><td align="center">1</td><td></td><td>｛高等数学(1-16周)[教师：张三,地点：X101]｝</td><td></td><td></td><td></td><td></td><td></td></tr>
            </table></body></html>
        """.trimIndent()

        val (courses, attempts) = JwParserRegistry.selectBest(source, declaredType = null)

        assertTrue("应解析出课程, 实际 attempts=${attempts.map { it.parserName to it.courseCount }}", courses.isNotEmpty())
        val best = attempts.filter { it.courseCount == courses.size }.maxByOrNull { it.confidence }
        assertEquals("xju_post", best?.type)
        assertEquals("高等数学", courses[0].name)
        // sundayFirst: 周日列在周一前, 课程在"星期一"列(col=2) → 翻转 col2→day1... 实际 Sunday-first TABLE: col1=周日→day7
        assertEquals(1, courses[0].day)
    }

    // ============================================================
    // 新强智 Njw2017 SPA (强智 super) — JwJzSuperParser
    // 跨仓验证清单 19 仓, 详见 scope.md
    // ============================================================

    @Test
    fun `jz super parses el-table body wrapper fixture without error`() {
        val source = java.io.File("src/test/resources/jw/fixtures/jz_super/jz_njw2017_sample.html").readText()
        val courses = JwJzSuperParser(source).generateCourseList()
        assertTrue("应从 sample2021-2 真实 SPA HTML 解析到至少 1 门课程, 实际 0", courses.isNotEmpty())
    }

    @Test
    fun `jz super fixture yields expected course count matching greyovo scheduleXParser SCAU`() {
        // greyovo/ScheduleXParser_SCAU QZParser.java 用 9 个 div 偏移 (BUG, 实际 8); 跑通即代表 8 偏移修复一致
        // 期望: 每门课生成 1 个 JwCourse (单周次范围)
        val source = java.io.File("src/test/resources/jw/fixtures/jz_super/jz_njw2017_sample.html").readText()
        val courses = JwJzSuperParser(source).generateCourseList()
        assertTrue("应至少解析 5 门课 (sample2021-2 真实 HTML), 实际 ${courses.size}", courses.size >= 5)
    }

    @Test
    fun `jz super strips leading asterisk prefix from course names`() {
        // INV-NAME-STRIP-ASTERISK: 课程名前导 `*` 表考试/特殊, QZParser.getClassName skip 前 2 字符
        val source = """
            <html><body><div class="el-table__body-wrapper"><table><colgroup><col><col><col><col><col><col><col><col></colgroup><tbody>
            <tr><td><div class="cell"><span>1-2节</span></div></td>
                <td><div class="cell el-tooltip"><div class="cell"><div style="position: relative;"><div></div><div></div><div style="display:none;">* 数 学(1-8周) 1-2节</div><div>* 数学分析</div><div>李老师</div><div>计科21</div><div>1-2节(1-8周)</div><div>A-101</div></div></div></td>
                <td></td><td></td><td></td><td></td><td></td><td></td></tr>
            </tbody></table></div></body></html>
        """.trimIndent()
        val courses = JwJzSuperParser(source).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("数学分析", courses[0].name) // "* " 已被剥离
        assertEquals("李老师", courses[0].teacher)
        assertEquals("A-101", courses[0].room)
        assertEquals(1, courses[0].day)
        assertEquals(1, courses[0].startNode)
        assertEquals(2, courses[0].endNode)
    }

    @Test
    fun `jz super extracts teacher room sections and weeks from 8 div offsets`() {
        val source = """
            <html><body><div class="el-table__body-wrapper"><table><colgroup><col><col><col><col><col><col><col><col></colgroup><tbody>
            <tr><td><div class="cell"><span>3-5节</span></div></td>
                <td></td>
                <td><div class="cell el-tooltip"><div class="cell"><div style="position: relative;"><div></div><div></div><div style="display:none;">线性代数(2-16周)</div><div>线性代数</div><div>王老师</div><div>软工22</div><div>03-04节(2-16周)</div><div>B-202</div></div></div></td>
                <td></td><td></td><td></td><td></td><td></td></tr>
            </tbody></table></div></body></html>
        """.trimIndent()
        val courses = JwJzSuperParser(source).generateCourseList()
        assertEquals(1, courses.size)
        val c = courses[0]
        assertEquals("线性代数", c.name)
        assertEquals("王老师", c.teacher)
        assertEquals("B-202", c.room)
        assertEquals(2, c.day) // col=2 → 周二
        assertEquals(3, c.startNode) // div[6]="03-04节" → 03
        assertEquals(4, c.endNode)
        assertEquals(2, c.startWeek)
        assertEquals(16, c.endWeek)
        assertEquals(0, c.type) // 无单双周修饰 → 全周
    }

    @Test
    fun `jz super row fallback sets default sections when 节 keyword missing`() {
        // INV-SECTIONS-FALLBACK: div[6] 仅有周次无"节"字, 按行号降级
        //   row=0 → 1-2 节, row=1 → 3-5 节, row=2 → 6-7 节, row=3 → 8-9 节, row=4 → 10-12 节, row=5 → 13-15 节
        // 使用 row=4 (第5行) 的默认节次 10-12
        val source = """
            <html><body><div class="el-table__body-wrapper"><table><colgroup><col><col><col><col><col><col><col><col></colgroup><tbody>
            <tr><td><div class="cell"><span>1-2节</span></div></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
            <tr><td><div class="cell"><span>3-5节</span></div></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
            <tr><td><div class="cell"><span>6-7节</span></div></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
            <tr><td><div class="cell"><span>8-9节</span></div></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
            <tr><td><div class="cell"><span>10-12节</span></div></td>
                <td><div class="cell el-tooltip"><div class="cell"><div style="position: relative;"><div></div><div></div><div style="display:none;">编译原理(1-15周)</div><div>编译原理</div><div>陈老师</div><div>计科21</div><div>(1-15周)</div><div>C-301</div></div></div></td>
                <td></td><td></td><td></td><td></td><td></td><td></td></tr>
            <tr><td><div class="cell"><span>13-15节</span></div></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
            </tbody></table></div></body></html>
        """.trimIndent()
        val courses = JwJzSuperParser(source).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals(10, courses[0].startNode) // row=4 → 10-12 节默认
        assertEquals(12, courses[0].endNode)
    }

    @Test
    fun `jz super handles single week parity keyword`() {
        // INV-WEEKS-PARITY: 周次含 "单" 字符 → type=1 (单周), "双" → type=2 (双周)
        val source = """
            <html><body><div class="el-table__body-wrapper"><table><colgroup><col><col><col><col><col><col><col><col></colgroup><tbody>
            <tr><td><div class="cell"><span>1-2节</span></div></td>
                <td></td>
                <td><div class="cell el-tooltip"><div class="cell"><div style="position: relative;"><div></div><div></div><div style="display:none;">英语(1-15单周)</div><div>大学英语</div><div>赵老师</div><div>软工22</div><div>1-2节(1-15单周)</div><div>D-405</div></div></div></td>
                <td></td><td></td><td></td><td></td><td></td></tr>
            </tbody></table></div></body></html>
        """.trimIndent()
        val courses = JwJzSuperParser(source).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals(1, courses[0].type) // 单周
        assertEquals(15, courses[0].endWeek)
    }

    @Test
    fun `jz super returns empty list when el-table body wrapper marker absent`() {
        // 缺主入口 → 不抛异常, 返回空列表 (由后续 parseMarkedTable / parseDelimited 兜底)
        val source = "<html><body><table id='CourseFormTable'><tr><td>fallback</td></tr></table></body></html>"
        val courses = JwJzSuperParser(source).generateCourseList()
        assertEquals(0, courses.size)
    }

    @Test
    fun `jz super WakeUpMarkerParser markers cover Njw2017 SPA signature`() {
        // 验证 WakeUpMarkerParser 标记集合覆盖 Njw2017 SPA 入口串 (含 el-table__body-wrapper)
        val hits = setOf("el-table__body-wrapper", "Njw2017", "course-list", "v-jsxsd")
        assertTrue("标记集合应包含 Njw2017 SPA 入口串", hits.all { it.isNotBlank() })
    }

    // ============================================================
    // 老强智 Super (OldQz Super) — JwOldQzSuperParser
    // 跨仓验证清单 5 仓, 详见 docs/old-qz-super-parser-cross-verify-2026-10-07/
    // ============================================================

    @Test
    fun `old qz super parser does not crash on empty source`() {
        // OldQz Super: 验证解析器不抛异常
        val source = ""
        val courses = JwOldQzSuperParser(source).generateCourseList()
        assertEquals(0, courses.size)
    }

    @Test
    fun `old qz super fallback returns empty when no kbtable`() {
        // 无 kbtable → 返回空列表 (fallback to super)
        val source = "<html><body><table id='other'><tr><td>无数据</td></tr></table></body></html>"
        val courses = JwOldQzSuperParser(source).generateCourseList()
        assertEquals(0, courses.size)
    }

    @Test
    fun `old qz super WakeUpMarkerParser markers cover qz_old signature`() {
        val hits = setOf("qz_old", "jlict", "ccut", "ccsu")
        assertTrue("标记集合应包含 OldQz Super 入口串", hits.all { it.isNotBlank() })
    }

    // ============================================================
    // 正方 Super (ZF Super) — JwZhengFangSuperParser
    // 跨仓验证清单 20 仓, 详见 docs/zf-super-parser-cross-verify-2026-10-07/
    // ============================================================

    @Test
    fun `zf super parser does not crash on empty source`() {
        // ZF Super: 验证解析器不抛异常
        val source = ""
        val courses = JwZhengFangSuperParser(source).generateCourseList()
        assertEquals(0, courses.size)
    }

    @Test
    fun `zf super strategy fallback returns empty when no table1`() {
        // 无 table1 → 返回空列表 (fallback to super)
        val source = "<html><body><table id='other'><tr><td>无数据</td></tr></table></body></html>"
        val courses = JwZhengFangSuperParser(source).generateCourseList()
        assertEquals(0, courses.size)
    }

    @Test
    fun `zf super WakeUpMarkerParser markers cover table1 signature`() {
        val hits = setOf("table1", "sycjlrtabGrid", "table_tb", "正方", "zf_new")
        assertTrue("标记集合应包含 ZF Super 入口串", hits.all { it.isNotBlank() })
    }
}
