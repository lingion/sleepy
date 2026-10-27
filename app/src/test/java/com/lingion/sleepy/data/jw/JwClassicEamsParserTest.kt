package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals("局域网与城域网(G0102030.01)", lan.name)   // verbatim: 课程编码后缀原样保留 (uestc.js 形态)
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

    // -------- verbatim: 课程名整体入库, 不做任何括号/编码剥离 --------
    // 用户明示: "你就正正经经的把这一整个课程提取出来, 不做任何的清洗适配"
    // 历史误伤: cleanCourseName("大学物理Ⅱ(D1200440.18)") → "大学物理Ⅱ" 丢了课程编码

    @Test
    fun `trailing course-code parenthetical is preserved verbatim for uestc`() {
        // 电子科大 (uestc.js) 形态: 课名尾缀课程编码 (D1200440.18) 必须原样保留
        val uestc = """
            <script>
            var table0 = new CourseTable(2025,1);
            var unitCount = 12;
            var activity=null;
                activity = new TaskActivity("1","王老师","2(02)","大学物理Ⅱ(D1200440.18)","100","教1-101","00000000100000000000000000000000000000000000000000000",null,"","","","","");
                index =2*unitCount+0;
                table0.activities[index][table0.activities[index].length]=activity;
            table0.marshalTable(2,1,21);
            </script>
        """.trimIndent()
        val courses = JwClassicEamsParser(uestc).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("大学物理Ⅱ(D1200440.18)", courses.single().name)
    }

    @Test
    fun `brackets in TaskActivity name field are preserved verbatim`() {
        // 通用: 课程名任意括号形态 (理论/实验/实践/课程编码/书名号引用) 全部原样入库
        // 不做清洗 — 包括括号内的非课程编码内容
        val page = """
            <script>
            var table0 = new CourseTable(2025,1);
            var unitCount = 12;
            var activity=null;
                activity = new TaskActivity("1","A","x","市场营销学(理论)","100","R1","00000000100000000000000000000000000000000000000000000",null,"","","","","");
                index =1*unitCount+0;
                table0.activities[index][table0.activities[index].length]=activity;
                activity = new TaskActivity("1","B","x","数据库实验(上机)","100","R2","00000000100000000000000000000000000000000000000000000",null,"","","","","");
                index =1*unitCount+1;
                table0.activities[index][table0.activities[index].length]=activity;
                activity = new TaskActivity("1","C","x","《红楼梦》导读","100","R3","00000000100000000000000000000000000000000000000000000",null,"","","","","");
                index =1*unitCount+2;
                table0.activities[index][table0.activities[index].length]=activity;
            table0.marshalTable(2,1,21);
            </script>
        """.trimIndent()
        val courses = JwClassicEamsParser(page).generateCourseList()
        assertEquals(3, courses.size)
        assertEquals("市场营销学(理论)", courses[0].name)
        assertEquals("数据库实验(上机)", courses[1].name)
        assertEquals("《红楼梦》导读", courses[2].name)
    }

    // ==================================================================
    // NBCC (批C 2026-10-26) — 双源合成成功样本 + 三型反例 (fix-design §5.1/§5.2)
    // fixture 目录 jw/fixtures/classic_eams/ (nwupl/lixin 先例同规);
    // 成功样本合成依据 = Wangxiaolu0716 Dart parser 头注真实样例 + 真实抓页结构,
    // 全部脱敏 (零真实姓名/学号, 公开通课名)。
    // ==================================================================

    private fun nbccFixture(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/classic_eams/$name")
        org.junit.Assert.assertNotNull("测试资源 jw/fixtures/classic_eams/$name 应存在", stream)
        return stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun `nbcc 成功样本 join反查 字面教师 数字index 三块逐形断言`() {
        val page = nbccFixture("nbcc-course-table-action.html")
        val courses = JwClassicEamsParser(page).generateCourseList()
        // 块1 教师join反查 12 周 + 块2 单周位图 5 周 + 块3 双周位图 4 周
        assertEquals("三块按周展开总行数不符: ${courses.size}", 21, courses.size)

        // 块1: actTeacherName.join(',') → 块前 var actTeachers 反查, 双师 "/" 连
        val pe = courses.filter { it.name == "体育健康-1(020D01G35)" }
        assertEquals(12, pe.size)
        assertEquals("赵老师/钱老师", pe.first().teacher)
        assertEquals("体育馆-201乒乓球馆", pe.first().room)
        // index =3*unitCount+2 / +3 → day=4, 连堂 3-4 节
        assertEquals(4, pe.first().day)
        assertEquals(3, pe.first().startNode)
        assertEquals(4, pe.first().endNode)
        // 真实样例位图 "000001111011111111…" → 第 5-8,10-17 周逐周展开, 每周 type=0
        assertEquals(listOf(5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17), pe.map { it.startWeek })
        assertTrue("逐周展开必须 startWeek==endWeek", pe.all { it.startWeek == it.endWeek })
        assertTrue("位图单双周逐周天然表达, type 恒 0", pe.all { it.type == 0 })

        // 块2: 老版第 2 参引号字面教师 + 单周位图形态
        val mao = courses.filter { it.name.startsWith("毛泽东思想") }
        assertEquals(5, mao.size)
        assertEquals("孙老师", mao.first().teacher)
        // index =0*unitCount+9 / +10 → day=1, 10-11 节连堂
        assertEquals(1, mao.first().day)
        assertEquals(10, mao.first().startNode)
        assertEquals(11, mao.first().endNode)
        // 位图 "0101010101…" 下标1即第1周 → 单周 1,3,5,7,9
        assertEquals(listOf(1, 3, 5, 7, 9), mao.map { it.startWeek })

        // 块3: 纯数字 index 形态 (兼容 Dart 变体) — 42 = 3*12+6 → day=4, node=7
        val math = courses.filter { it.name.startsWith("高等数学") }
        assertEquals(4, math.size)
        assertEquals("李老师", math.first().teacher)
        assertEquals(4, math.first().day)
        assertEquals(7, math.first().startNode)
        assertEquals(7, math.first().endNode)
        // 位图 "0010101010…" → 双周 2,4,6,8
        assertEquals(listOf(2, 4, 6, 8), math.map { it.startWeek })
    }

    @Test
    fun `nbcc 成功样本 unitCount 取值必须来自页面而非写死`() {
        val page = nbccFixture("nbcc-course-table-action.html")
        // 页面值 12→13 原位改写: `D*unitCount+P` 两块 day/node 不变, 纯数字 index 块
        // 重排 42 = 3*13+3 → day=4, node=4 — 证明数字 index 换算吃页面 unitCount。
        val rewritten = page.replace("var unitCount = 12;", "var unitCount = 13;")
        assertTrue("改写锚必须命中(fixture 漂移预警)", rewritten != page)
        val math = JwClassicEamsParser(rewritten).generateCourseList()
            .first { it.name.startsWith("高等数学") }
        assertEquals(4, math.day)
        assertEquals(4, math.startNode)
        val pe = JwClassicEamsParser(rewritten).generateCourseList()
            .first { it.name == "体育健康-1(020D01G35)" }
        assertEquals(4, pe.day)
        assertEquals(3, pe.startNode)
    }

    @Test
    fun `nbcc 成功样本 confidence 95 五锚全中`() {
        val parser = JwClassicEamsParser(nbccFixture("nbcc-course-table-action.html"))
        assertEquals(95, parser.confidence())
        assertEquals(
            listOf(
                "table#manualArrangeCourseTable",
                "new TaskActivity(...)",
                "var unitCount = N",
                "table0.marshalTable",
                "courseTableForStd",
            ),
            parser.matchedFeatures()
        )
    }

    // -------- 南开 (issue #236) 同族形态五锚全中 --------

    @Test
    fun `nankai 成功样本 confidence 95 五锚全中与 NBCC 同族互证`() {
        val parser = JwClassicEamsParser(nbccFixture("nankai-course-table-action.html"))
        assertEquals(95, parser.confidence())
        assertEquals(
            listOf(
                "table#manualArrangeCourseTable",
                "new TaskActivity(...)",
                "var unitCount = N",
                "table0.marshalTable",
                "courseTableForStd",
            ),
            parser.matchedFeatures()
        )
        // 位图周数即行数 (mergeConsecutiveNodes 把块内多 index 合成单 slot):
        // 数学分析A 12 周 + 高等代数B 10 周 + 形势与政策 1 周 = 23 行
        val courses = parser.generateCourseList()
        assertEquals("南开样本出课行数不符: ${courses.map { "${it.name}@w${it.startWeek}" }}", 23, courses.size)
        assertTrue("必须含数学分析A", courses.any { it.name.contains("数学分析A") })
        assertTrue("必须含高等代数B", courses.any { it.name.contains("高等代数B") })
        assertTrue("必须含形势与政策 (线性 index 42 形态)", courses.any { it.name.contains("形势与政策") })
    }

    // -------- 反例 a: 空壳无数据 (JS 渲染前形态) --------

    @Test
    fun `nbcc 空壳页 零课 零置信度`() {
        val page = nbccFixture("nbcc-empty-shell.html")
        val parser = JwClassicEamsParser(page)
        // capture 面成功判据 needle("new TaskActivity(")已由批B JS 即停逻辑覆盖
        // (NbccClassicEamsWebViewContractTest `JS 内含 §4 字面量三件套…`), 此处锁 parser 面。
        assertEquals("空壳不得自带活动判据", 0, parser.confidence())
        assertTrue("空壳必须 0 课 (禁伪成功落库)", parser.generateCourseList().isEmpty())
        // registry 兜底面: 空壳不产生任何 ≥80 出课行 → selectBest 空, 不串型
        val (best, _) = JwParserRegistry.selectBest(page)
        assertTrue("全链裁决必须拒收空壳: $best", best.isEmpty())
    }

    // -------- 反例 b: 树维 JSON 混入, adjudicate 不串型 --------

    @Test
    fun `nbcc 树维混入JSON classic零锚 shuwei命中 裁决不串型`() {
        val json = nbccFixture("nbcc-shuwei-mixed.json")
        val classic = JwClassicEamsParser(json)
        assertEquals("树维 JSON 不得给 classic 任何置信度", 0, classic.confidence())
        assertTrue("classic 面对 JSON 必须 0 课不崩", classic.generateCourseList().isEmpty())

        val shuwei = JwShuweiParser(json)
        assertEquals("activities marker 必须命中 90", 90, shuwei.confidence())
        assertEquals(2, shuwei.generateCourseList().size)

        val (best, attempts) = JwParserRegistry.selectBest(json)
        val classicRow = attempts.first { it.type == JwProtocol.TYPE_CLASSIC_EAMS }
        assertEquals(0, classicRow.courseCount)
        assertEquals(0, classicRow.confidence)
        assertEquals("裁决赢家必须是树维 2 课, 不许串型", 2, best.size)
        assertTrue(best.any { it.name.startsWith("毛泽东思想") && it.type == 0 && it.startWeek == 1 && it.endWeek == 8 })
        assertTrue(best.any { it.name == "体育健康-1" && it.type == 2 && it.startWeek == 2 && it.endWeek == 8 })
    }

    // -------- 反例 c: 移动 UA SPA 壳 — 错误入口清晰失败, 钉钉时代形态不复现 --------

    @Test
    fun `nbcc 移动SPA壳 判型null 锚零命中 WRONG_PAGE`() {
        val shell = nbccFixture("nbcc-mobile-spa-shell.html")
        // 判型层: HTML 级零命中 → null 走兜底 (批A 只锚了 URL 面, HTML 面本就不该认识它)
        assertNull(JwImportViewModel.detectProtocolFromHtmlForTest(shell))
        // capture 层: 容器锚零命中 → 照 Xju 先例以 FrameSnapshot 单帧走裁决
        assertTrue(
            "SPA 壳不得命中任何容器锚",
            com.lingion.sleepy.ui.screen.imports.FrameTraversalTree.findAnchors(shell).isEmpty()
        )
        val snapshots = com.lingion.sleepy.ui.screen.imports.FrameSnapshot.fromJson(
            """{"ok":true,"url":"https://webvpn.nbcc.edu.cn/","depth":0,"frames":[{"name":"(top)","src":"https://webvpn.nbcc.edu.cn/","depth":0,"path":[],"html":${org.json.JSONObject.quote(shell)},"blocked":""}]}"""
        )
        val r = com.lingion.sleepy.ui.screen.imports.FrameTraversalTree.selectBestFrame(snapshots)
        assertEquals(
            "无锚非登录壳必须 WRONG_PAGE 清晰失败, 绝不落伪成功",
            com.lingion.sleepy.ui.screen.imports.FrameCaptureStatus.WRONG_PAGE, r.status
        )
    }
}
