package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 经典金智/树维 EAMS (`courseTableForStd!courseTable.action`) 解析测试
 * (2026-09 211 批量收录: 电子科大/上财/湖南师大/南航 4 所解锁)。
 *
 * 协议形态: 课表数据在页面内嵌 JS 块 (`new TaskActivity(...)` + `index =D*unitCount+P;`),
 * HTML 表格是空壳。调研: .superpowers/sdd/2026-09-05-211-batch/research/classic-eams.md
 * 算法形态参考 shiguang_warehouse (MIT) hunnu.js/uestc.js + WakeupSchedule_Kotlin
 * (Apache-2.0) ImportViewModel.kt; 代码自写。fixture 同构自造 ( GPL 原件不入库)。
 *
 * 关键规则 (调研多源交叉验证):
 *   - 周次位图下标 0 占位, 下标 i=1 即第 i 周 (勿 +1)
 *   - index = D*unitCount+P → day=D+1, node=P+1; unitCount 禁写死
 *   - 教师 [1] 可能是 actTeacherName.join(',') 表达式 → 块前 2500 字符反查 var actTeachers
 *   - 参数切分必须带引号/括号深度状态机 (课名可含逗号)
 */
class JwClassicEamsParserTest {

    // -------- 天大同构 fixture: 表达式教师 + actTeachers 反查 + 连堂 + 稀疏位图 --------

    private val tjuLike = """
        <table id="manualArrangeCourseTable" class="gridtable"><tr><td id="TD0_0"></td></tr></table>
        <script>
        var table0 = new CourseTable(2019,84);
        var unitCount = 12;
        var index=0;
        var activity=null;
            var teachers = [{id:6540,name:"曲老师",lab:false},{id:7396,name:"张老师",lab:true}];
            var actTeachers = [{id:7396,name:"张老师",lab:true}];
            var assistantName = "";
                activity = new TaskActivity(actTeacherId.join(','),actTeacherName.join(','),"39603(04127)","离散数学","3883","23楼506","00000000010000000000000000000000000000000000000000000",null,"",assistantName,"","","");
                index =3*unitCount+0;
                table0.activities[index][table0.activities[index].length]=activity;
                index =3*unitCount+1;
                table0.activities[index][table0.activities[index].length]=activity;
            var teachers = [{id:100,name:"李老师",lab:false}];
            var actTeachers = [{id:100,name:"李老师",lab:false}];
            var assistantName = "";
                activity = new TaskActivity(actTeacherId.join(','),actTeacherName.join(','),"39605(04131)","程序设计原理","3901","23楼214","00000000000001010101000000000000000000000000000000000",null,"",assistantName,"","","");
                index =1*unitCount+2;
                table0.activities[index][table0.activities[index].length]=activity;
        table0.marshalTable(2,1,21);
        </script>
    """.trimIndent()

    @Test
    fun `parses TaskActivity blocks with expression teacher resolved from actTeachers`() {
        val courses = JwClassicEamsParser(tjuLike).generateCourseList()
        // 离散数学 1 周 + 程序设计原理 4 周 (稀疏) = 5 行
        assertEquals(5, courses.size)
        val discrete = courses.first { it.name == "离散数学" }
        assertEquals("张老师", discrete.teacher)
        assertEquals("23楼506", discrete.room)
    }

    @Test
    fun `index expr maps to day and node one-based`() {
        val courses = JwClassicEamsParser(tjuLike).generateCourseList()
        val discrete = courses.filter { it.name == "离散数学" }
        // index =3*unitCount+0/1 → day=4 (周四), node 1..2 连堂
        assertEquals(1, discrete.size)
        assertEquals(4, discrete[0].day)
        assertEquals(1, discrete[0].startNode)
        assertEquals(2, discrete[0].endNode)
    }

    @Test
    fun `week bitmap index i equals week i - no off by one`() {
        val courses = JwClassicEamsParser(tjuLike).generateCourseList()
        // 位图 "000000001000..." → 单周第 9 周 (下标 0 占位)
        val discrete = courses.first { it.name == "离散数学" }
        assertEquals(9, discrete.startWeek)
        assertEquals(9, discrete.endWeek)
        assertEquals(0, discrete.type)
    }

    @Test
    fun `sparse bitmap weeks expand to per-week rows`() {
        val courses = JwClassicEamsParser(tjuLike).generateCourseList()
        // "000000000000101010100000..." 下标 13,15,17,19 → 稀疏 4 周, 输出 4 行
        val prog = courses.filter { it.name == "程序设计原理" }
        assertEquals(4, prog.size)
        assertEquals(listOf(13, 15, 17, 19), prog.map { it.startWeek })
        assertTrue(prog.all { it.day == 2 && it.startNode == 3 && it.endNode == 3 })
    }

    // -------- 字面参数形态 (老版多数: 教师直接是引号字符串) --------

    private val literalLike = """
        <script>
        var table0 = new CourseTable(2025,300);
        var unitCount = 13;
        var activity=null;
            activity = new TaskActivity("9766","韦老师","11870(G0102030.01)","局域网与城域网(G0102030.01)","364","A108","00000000010000000000000000000000000000000000000000000",null,"","","","","");
            index =0*unitCount+0;
            table0.activities[index][table0.activities[index].length]=activity;
            index =0*unitCount+1;
            table0.activities[index][table0.activities[index].length]=activity;
        table0.marshalTable(2,1,21);
        </script>
    """.trimIndent()

    @Test
    fun `parses literal string args - teacher is quoted directly`() {
        val courses = JwClassicEamsParser(literalLike).generateCourseList()
        assertEquals(1, courses.size)
        val lan = courses.single()
        assertEquals("局域网与城域网", lan.name)   // 课程编码后缀被剥离 (uestc.js 形态)
        assertEquals("韦老师", lan.teacher)
        assertEquals("A108", lan.room)
        assertEquals(1, lan.day)
        assertEquals(1, lan.startNode)
        assertEquals(2, lan.endNode)
        // 位图 仅下标 9=1 → 第 9 周; 位图展开行 startWeek==endWeek, type 恒 0 (每周)
        // — 单双周语义由具体周次表达, 不再用 type 标记
        assertEquals(9, lan.startWeek)
        assertEquals(9, lan.endWeek)
        assertEquals(0, lan.type)
    }

    // -------- unitCount 每校不一 (禁写死): 13 = 湖南师大形态 --------

    @Test
    fun `unitCount is read from page not hardcoded`() {
        val courses = JwClassicEamsParser(literalLike).generateCourseList()
        // unitCount=13: index=0*13+0 → day1 node1
        assertEquals(1, courses.single().startNode)
    }

    // -------- 上财 courseNameLessonNo 表达式 (2026-08 起) --------

    @Test
    fun `parses courseNameLessonNo expression as 4th arg`() {
        val sufe = """
            <script>
            var table0 = new CourseTable(2026,401);
            var unitCount = 12;
            var activity=null;
                var teachers = [{id:9,name:"陈老师",lab:false}];
                var actTeachers = [{id:9,name:"陈老师",lab:false}];
                activity = new TaskActivity(actTeacherId.join(','),actTeacherName.join(','),"20401(X090101.01)",this.courseNameLessonNo,"4002","会计学院201","00000000010000000000000000000000000000000000000000000",null,"","","","","");
                index =4*unitCount+0;
                table0.activities[index][table0.activities[index].length]=activity;
                index =4*unitCount+1;
                table0.activities[index][table0.activities[index].length]=activity;
            var courseNameLessonNo = "会计学原理(ACCT1001)";
            table0.marshalTable(2,1,21);
            </script>
        """.trimIndent()
        val courses = JwClassicEamsParser(sufe).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("会计学原理(ACCT1001)", courses.single().name)
        assertEquals(5, courses.single().day)
        assertEquals(1, courses.single().startNode)
        assertEquals(2, courses.single().endNode)   // 两行 index → 连堂合并
        assertEquals("陈老师", courses.single().teacher)
    }

    // -------- adversarial: ,-1 跳块 / 停课教室 / 纯数字 index / 空页 --------

    @Test
    fun `activity with negative-one teacher marker is skipped`() {
        val bad = """
            <script>
            var table0 = new CourseTable(2025,1);
            var unitCount = 12;
            var activity=null;
                activity = new TaskActivity("","-1","","停课数据","","","00000000100000000000000000000000000000000000000000000",null,"","","","","");
                index =0*unitCount+0;
                table0.activities[index][table0.activities[index].length]=activity;
                var teachers = [{id:1,name:"好老师",lab:false}];
                var actTeachers = [{id:1,name:"好老师",lab:false}];
                activity = new TaskActivity(actTeacherId.join(','),actTeacherName.join(','),"1(01)","正常课程","11","教1-101","00000000100000000000000000000000000000000000000000000",null,"","","","","");
                index =1*unitCount+0;
                table0.activities[index][table0.activities[index].length]=activity;
            table0.marshalTable(2,1,21);
            </script>
        """.trimIndent()
        val courses = JwClassicEamsParser(bad).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("正常课程", courses.single().name)
    }

    @Test
    fun `suspended class room value excludes that week row`() {
        // 电子科大形态: 教室字段 = "停课" → 该条目不输出
        val suspended = """
            <script>
            var table0 = new CourseTable(2025,2);
            var unitCount = 12;
            var activity=null;
                activity = new TaskActivity("1","王老师","2(02)","课程甲","100","停课","00000000100000000000000000000000000000000000000000000",null,"","","","","");
                index =2*unitCount+0;
                table0.activities[index][table0.activities[index].length]=activity;
            table0.marshalTable(2,1,21);
            </script>
        """.trimIndent()
        val courses = JwClassicEamsParser(suspended).generateCourseList()
        assertTrue("教室=停课 的条目不应输出", courses.isEmpty())
    }

    @Test
    fun `numeric index form is converted via unitCount modulo`() {
        val numeric = """
            <script>
            var table0 = new CourseTable(2025,3);
            var unitCount = 12;
            var activity=null;
                activity = new TaskActivity("2","赵老师","3(03)","课程乙","101","教2-201","00000000100000000000000000000000000000000000000000000",null,"","","","","");
                index =26;
                table0.activities[index][table0.activities[index].length]=activity;
            table0.marshalTable(2,1,21);
            </script>
        """.trimIndent()
        val courses = JwClassicEamsParser(numeric).generateCourseList()
        assertEquals(1, courses.size)
        // 26 = 2*12+2 → day=3, node=3
        assertEquals(3, courses.single().day)
        assertEquals(3, courses.single().startNode)
    }

    @Test
    fun `page without TaskActivity yields empty list`() {
        val empty = """
            <html><body><table id="manualArrangeCourseTable"></table>
            <script>var table0 = new CourseTable(2025,4); var unitCount = 12; table0.marshalTable(2,1,21);</script>
            </body></html>
        """.trimIndent()
        val courses = JwClassicEamsParser(empty).generateCourseList()
        assertTrue(courses.isEmpty())
        assertEquals(0, JwClassicEamsParser(empty).confidence())
    }

    @Test
    fun `course name containing comma survives arg splitting`() {
        val comma = """
            <script>
            var table0 = new CourseTable(2025,5);
            var unitCount = 12;
            var activity=null;
                activity = new TaskActivity("3","钱老师","4(04)","概率论与数理统计,上","102","教3-301","00000000100000000000000000000000000000000000000000000",null,"","","","","");
                index =0*unitCount+4;
                table0.activities[index][table0.activities[index].length]=activity;
            table0.marshalTable(2,1,21);
            </script>
        """.trimIndent()
        val courses = JwClassicEamsParser(comma).generateCourseList()
        assertEquals("概率论与数理统计,上", courses.single().name)
    }

    // -------- confidence 锚点 --------

    @Test
    fun `confidence anchors table plus taskactivity plus unitcount`() {
        assertEquals(95, JwClassicEamsParser(tjuLike).confidence())
        // 只有 TaskActivity, 缺 manualArrangeCourseTable/unitCount → 中档
        val partial = "<script>activity = new TaskActivity(\"1\",\"t\",\"c\",\"n\",\"r\",\"p\",\"01\",null);</script>"
        val p = JwClassicEamsParser(partial)
        assertTrue(p.confidence() in 50..79)
    }

    @Test
    fun `matchedFeatures reports script anchors`() {
        val feats = JwClassicEamsParser(tjuLike).matchedFeatures()
        assertTrue(feats.any { it.contains("TaskActivity") })
        assertTrue(feats.any { it.contains("manualArrangeCourseTable") })
    }
}
