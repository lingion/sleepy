package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    // ============================================================================
    //    Kingosoft Super (新青果 super 2026-10) HTML 3-chain fallback — 8 test methods
    //    chains: chain1 o0oOOo (xkinfo + NBSP + <br>) / chain2 o00O0OO (thead 中文表头)
    //            / chain3 oo0oOO0 (kbDiv div layout)
    //    8 regex_invariants: HTML-FRAG-SPLIT / NBSP-CLEANUP / CN-HEADER-LEXICON / DAY-DICT
    //    / WEEKS-PARSE / SECTIONS-RANGE / NO-SECTION-TIME-MAP / ERROR-AGGREGATE
    // ============================================================================

    @Test
    fun `kingo super chain1 o0oOOo parses xkinfo div with nbsp and br`() {
        // 链 1: o0oOOo 老 div — xkinfo class + NBSP cleanup + <br> row split
        // INV-NBSP-CLEANUP: 连续 &nbsp; 塌缩空格, 然后 Jsoup text() 切行
        val source = """
            <html><head><title>kingo super chain1 o0oOOo</title></head><body>
            <div class="xkinfo">
                高等数学
                <br>周一&nbsp;1-16
                <br>&nbsp;&nbsp;第1-2节
                <br>张三
                <br>博学楼A-101
            </div>
            </body></html>
        """.trimIndent()

        val courses = JwKingoParser(source).generateCourseList()

        assertEquals(1, courses.size)
        val c = courses.single()
        assertEquals("高等数学", c.name)
        assertEquals("博学楼A-101", c.room)
        assertEquals("张三", c.teacher)
        assertEquals(1, c.day) // 周一 → 1
        assertEquals(1, c.startNode)
        assertEquals(2, c.endNode)
        assertEquals(1, c.startWeek)
        assertEquals(16, c.endWeek)
        assertEquals(0, c.type) // 全段无"单"/"双" → 0 (每周)
    }

    @Test
    fun `kingo super chain2 o00O0OO parses thead with chinese header lexicon`() {
        // 链 2: o00O0OO 标准 thead — 中文表头 [课程/学分/教师/地点/周次/节次/单双周]
        val source = """
            <html><head><title>kingo super chain2 o00O0OO</title></head><body>
            <table border="1">
              <thead><tr><th>日期</th><th>课程</th><th>学分</th><th>教师</th><th>地点</th><th>周次</th><th>节次</th><th>单双周</th></tr></thead>
              <tbody>
                <tr><td>周一</td><td>大学物理</td><td>3</td><td>李四</td><td>物理楼-201</td><td>1-16</td><td>3-4节</td><td></td></tr>
                <tr><td>周四</td><td>英语</td><td>2</td><td>王五</td><td>外语楼-101</td><td>1-16</td><td>5-6节</td><td>单</td></tr>
              </tbody>
            </table>
            </body></html>
        """.trimIndent()

        val courses = JwKingoParser(source).generateCourseList()

        assertEquals(2, courses.size)
        val physics = courses.single { it.name == "大学物理" }
        assertEquals("李四", physics.teacher)
        assertEquals("物理楼-201", physics.room)
        assertEquals(1, physics.day)
        assertEquals(3, physics.startNode)
        assertEquals(4, physics.endNode)
        val english = courses.single { it.name == "英语" }
        assertEquals(4, english.day) // 周四 → 4
        assertEquals(1, english.type) // 单周
    }

    @Test
    fun `kingo super chain3 oo0oOO0 parses kbDiv layout`() {
        // 链 3: oo0oOO0 新版 kbDiv div 布局
        val source = """
            <html><head><title>kingo super chain3 oo0oOO0</title></head><body>
            <div class="kbDiv"><div>数据结构 周三 第3-4节 教工-301 1-16周</div></div>
            </body></html>
        """.trimIndent()

        val courses = JwKingoParser(source).generateCourseList()

        assertEquals(1, courses.size)
        val c = courses.single()
        assertEquals("数据结构", c.name)
        assertEquals(3, c.day) // 周三 → 3
        assertEquals(3, c.startNode)
        assertEquals(4, c.endNode)
        assertEquals(1, c.startWeek)
        assertEquals(16, c.endWeek)
    }

    @Test
    fun `kingo super 3-chain fallback chain1 fails falls through to chain2`() {
        // 3-chain fallback: chain1 (xkinfo) 缺解析数据 → fallback 到 chain2 (thead)
        val source = """
            <html><head><title>fallback</title></head><body>
            <div class="xkinfo"></div>
            <table border="1">
              <thead><tr><th>日期</th><th>课程</th><th>教师</th><th>地点</th><th>周次</th><th>节次</th><th>单双周</th></tr></thead>
              <tbody>
                <tr><td>周二</td><td>算法</td><td>赵老师</td><td>信科-301</td><td>1-8</td><td>5-6节</td><td></td></tr>
              </tbody>
            </table>
            <div class="kbDiv"><div>应被忽略 周一 第3-4节 1-16周</div></div>
            </body></html>
        """.trimIndent()

        val courses = JwKingoParser(source).generateCourseList()

        assertEquals(1, courses.size)
        assertEquals("算法", courses.single().name)
        assertEquals(2, courses.single().day) // 周二 → 2
    }

    @Test
    fun `kingo super TaskActivity chain still works regression`() {
        // 阶段 2 回归: 3 链失败时仍走老 kingo TaskActivity 路径
        val source = """
            <html><body><script>
            var activity = null;
            activity = new TaskActivity('线性代数','钱老师','教2-301','11111111111111111111');
            index = 4 * unitCount + 3;
            activity.index = index;
            table0.marshalTable();
            </script></body></html>
        """.trimIndent()

        val courses = JwKingoParser(source).generateCourseList()

        assertEquals(1, courses.size)
        assertEquals("线性代数", courses.single().name)
        assertEquals("钱老师", courses.single().teacher)
        assertEquals(5, courses.single().day)
    }

    @Test
    fun `kingo super fixture hits all 8 regex invariants simultaneously`() {
        val source = java.io.File("src/test/resources/jw/kingo_super_sample.html").readText()

        assertTrue("fixture 必含 <head", source.contains("<head"))
        assertTrue("fixture 必含 </head>", source.contains("</head>"))
        assertTrue("fixture 必含 &nbsp; 触发 NBSP 清理", source.contains("&nbsp;"))
        listOf("课程", "学分", "教师", "地点", "周次", "节次", "单双周").forEach {
            assertTrue("fixture 必含中文表头 '$it'", source.contains(it))
        }
        assertTrue("fixture 必含 '周一'", source.contains("周一"))
        assertTrue("fixture 必含周次表达式 '1-16'", source.contains("1-16"))
        assertTrue("fixture 必含节次区间 '第1-2节' / '3-4节'", source.contains("第1-2节") || source.contains("3-4节"))
        // INV-NO-SECTION-TIME-MAP: 禁止 HH:MM 时间字面量
        listOf("08:00", "10:00", "14:00", "19:00").forEach {
            assertFalse("fixture 禁止时间字面量 '$it'", source.contains(it))
        }
        assertTrue("fixture 必含 chain1 (xkinfo) marker", source.contains("xkinfo"))
        assertTrue("fixture 必含 chain3 (kbDiv) marker", source.contains("kbDiv"))
        assertTrue("fixture 必含 chain4 (TaskActivity) marker", source.contains("TaskActivity"))

        val courses = JwKingoParser(source).generateCourseList()
        assertTrue("fixture 应至少解析出 1 课程, 实际 0", courses.isNotEmpty())
    }

    @Test
    fun `kingo super throws aggregated error when all chains fail`() {
        val source = "<html><head><title>empty</title></head><body></body></html>"

        val ex = try {
            JwKingoParser(source).generateCourseList()
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertNotNull("全链降级失败应抛 IllegalStateException, 实际未抛", ex)
        val msg = ex!!.message.orEmpty()
        assertTrue("errorList 聚合消息应含 '全链降级失败'", msg.contains("全链降级失败"))
        assertTrue("errorList 聚合消息应含 chain1 o0oOOo 错误条目", msg.contains("o0oOOo"))
        assertTrue("errorList 聚合消息应含 chain2 o00O0OO 错误条目", msg.contains("o00O0OO"))
        assertTrue("errorList 聚合消息应含 chain3 oo0oOO0 错误条目", msg.contains("oo0oOO0"))
    }

    @Test
    fun `kingo super fixture file compliance end to end parse`() {
        val fixture = java.io.File("src/test/resources/jw/kingo_super_sample.html")
        assertTrue("kingo_super_sample.html fixture 必存在", fixture.exists())
        val html = fixture.readText()

        val courses = JwKingoParser(html).generateCourseList()
        assertTrue("fixture 端到端解析应至少 1 课程, 实际 0", courses.isNotEmpty())

        val first = courses.first()
        assertTrue("课程 name 必非空", first.name.isNotBlank())
        assertTrue("课程 day 必在 1..7", first.day in 1..7)
        assertTrue("课程 startWeek 必 ≥ 1", first.startWeek >= 1)
        assertTrue("课程 endWeek 必 ≥ startWeek", first.endWeek >= first.startWeek)
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
}
