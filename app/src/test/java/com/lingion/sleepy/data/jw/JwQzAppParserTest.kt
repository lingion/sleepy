package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 强智移动教务 SPA 课表解析器 (type=qz_app) 单元测试。
 *
 * 数据：src/test/resources/jw/fixtures/qz_app/curriculum.sample.json — 河北资源环境职业
 * 技术学院 2026-09-09 学生回传采集包 (去敏后 fixture, PII 已清)。10 行 → 展开 20 个
 * JwCourse (每个 classWeek "a-b,c-d" 中间有 1-5 跳空 → 拆两段, 全部 type=0)。
 *
 * 跨语言 invariant 同步锁：见 JwQzAppWebViewContractTest (JS 不得含 classTime 解码)。
 */
class JwQzAppParserTest {

    private fun loadFixture(): String {
        val stream = javaClass.classLoader?.getResourceAsStream(
            "jw/fixtures/qz_app/curriculum.sample.json"
        )
        assertNotNull("fixture jw/fixtures/qz_app/curriculum.sample.json 应存在", stream)
        return stream!!.bufferedReader().use { it.readText() }
    }

    @Test
    fun `parses hebzyhj fixture - 10 rows expand to 20 JwCourse`() {
        val courses = JwQzAppParser(loadFixture()).generateCourseList()
        assertEquals("10 行 → 20 JwCourse (全部两段拆, type=0)", 20, courses.size)
        // day histogram: 周五最多(8, 商务数据+短视频两时段各 2 段 = 8), 周三/周四 4, 周一/周二 2
        val byDay = courses.groupingBy { it.day }.eachCount()
        assertEquals("周一 2", 2, byDay[1])
        assertEquals("周二 2", 2, byDay[2])
        assertEquals("周三 4", 4, byDay[3])
        assertEquals("周四 4", 4, byDay[4])
        assertEquals("周五 8", 8, byDay[5])
        assertTrue("无周末课程", byDay[6] == null && byDay[7] == null)
    }

    @Test
    fun `classTime decode invariant`() {
        // "10304" → 周一 3-4 节 (day=1, startNode=3, endNode=4)
        val p = JwQzAppParser("")
        assertEquals(listOf(Triple(1, 3, 4)), p.parseClassTime("10304"))
        assertEquals(listOf(Triple(7, 1, 1)), p.parseClassTime("701"))   // 单节 (长度 3)
        assertEquals(listOf(Triple(2, 11, 12)), p.parseClassTime("21112"))
        // 9 位连堂两段 (2026-09-11 学生采集包: ITMC 周三 1-2 + 3-4)
        assertEquals(
            listOf(Triple(3, 1, 2), Triple(3, 3, 4)),
            p.parseClassTime("301020304")
        )
        // 非法形态: 偶数剩余位 (数字对不完整), 非数字, day 越界, end<start
        assertTrue("长度 4 拒 (01+34 半对)", p.parseClassTime("1034").isEmpty())
        assertTrue("非数字拒", p.parseClassTime("1030a").isEmpty())
        assertTrue("day=0 拒", p.parseClassTime("0304").isEmpty())
        assertTrue("day=8 拒", p.parseClassTime("8304").isEmpty())
        assertTrue("end<start 拒", p.parseClassTime("14213").isEmpty())
        assertTrue("空串拒", p.parseClassTime("").isEmpty())
        assertTrue("负号拒", p.parseClassTime("-10304").isEmpty())
    }

    @Test
    fun `classWeek gap split - 广告策划与创意 1-4 then 6-19`() {
        val courses = JwQzAppParser(loadFixture()).generateCourseList()
        val ads = courses.filter { it.name == "广告策划与创意" }
        assertEquals("两段", 2, ads.size)
        val ranges = ads.map { it.startWeek to it.endWeek }.toSet()
        assertTrue("含 1-4", ranges.contains(1 to 4))
        assertTrue("含 6-19", ranges.contains(6 to 19))
        ads.forEach { assertEquals("非步 2 → type=0", 0, it.type) }
    }

    @Test
    fun `multi-segment split - 商务数据分析 双时段各两段`() {
        val courses = JwQzAppParser(loadFixture()).generateCourseList()
        // 商务数据分析: 周五 1-2 节 (1-4,6-14) → 2 段; 周五 3-4 节 (1-4,6-15) → 2 段 = 共 4 行
        val biz = courses.filter { it.name == "商务数据分析" }
        assertEquals("双时段各两段 = 4 行", 4, biz.size)
        val nodes = biz.map { it.startNode to it.endNode }.toSet()
        assertTrue("1-2 节", nodes.contains(1 to 2))
        assertTrue("3-4 节", nodes.contains(3 to 4))
        // 校验房号"Z微机室(二)A102" 完整保留 (楼栋微机室非数字 → trim 不丢)
        assertTrue("非数字房号保留", biz.all { it.room == "Z微机室（二）A102" || it.room.contains("微机室") })
        val ranges1to2 = biz.filter { it.startNode == 1 && it.endNode == 2 }
            .map { it.startWeek to it.endWeek }.toSet()
        assertTrue("1-2 节含 1-4", ranges1to2.contains(1 to 4))
        assertTrue("1-2 节含 6-14", ranges1to2.contains(6 to 14))
    }

    @Test
    fun `field mapping - 消费行为分析 uses classroomNub as room`() {
        val courses = JwQzAppParser(loadFixture()).generateCourseList()
        val xfxw = courses.first { it.name == "消费行为分析" }
        // fixture: classroomNub=Z5-117 (周四 1-2 节)
        assertEquals("完整楼栋+房号", "Z5-117", xfxw.room)
        assertEquals("周四", 4, xfxw.day)
        assertEquals("1-2 节", 1 to 2, xfxw.startNode to xfxw.endNode)
        assertEquals("教师 胡美娜", "胡美娜", xfxw.teacher)
    }

    @Test
    fun `empty and adversarial input - graceful`() {
        // 空串 → parseToJsonElement 抛 → parser 必须 emptyList
        val empty = JwQzAppParser("").let {
            runCatching { it.generateCourseList() }.getOrDefault(emptyList())
        }
        assertEquals(0, empty.size)
        // 非 JSON → empty
        val nonJson = JwQzAppParser("not json at all").let {
            runCatching { it.generateCourseList() }.getOrDefault(emptyList())
        }
        assertEquals(0, nonJson.size)
        // 未登录 401 形态 → data 非数组 → empty (Registry 按 0 课空学期处理)
        val unauthorized = """{"code":"401","Msg":"非法访问：/student/curriculum","data":null}"""
        assertEquals(0, JwQzAppParser(unauthorized).generateCourseList().size)
        // data 为空数组 → empty
        val noGrid = """{"code":"1","Msg":"success~","data":[]}"""
        assertEquals(0, JwQzAppParser(noGrid).generateCourseList().size)
        // 顶层 data 缺 courses → empty
        val noCourses = """{"code":"1","data":[{"date":[]}]}"""
        assertEquals(0, JwQzAppParser(noCourses).generateCourseList().size)
        // courseName 空白 → 跳过, 不崩
        val blankName = """{"code":"1","data":[{"courses":[
            {"classTime":"10304","classWeek":"1-4,6-19","courseName":"   "},
            {"classTime":"10304","classWeek":"1-4,6-19","courseName":"正常课"}
        ]}]}"""
        val mixed = JwQzAppParser(blankName).generateCourseList()
        // "正常课" 1 门 × 2 周段 = 2 行; 空白名行贡献 0
        assertEquals("空白名行跳过, 仅正常课 2 行", 2, mixed.size)
        assertTrue("全部为正常课", mixed.all { it.name == "正常课" })
        // classTime 非法 → 跳过该行, 不影响其它行
        val badTime = """{"code":"1","data":[{"courses":[
            {"classTime":"X0304","classWeek":"1-4,6-19","courseName":"坏时间"},
            {"classTime":"10304","classWeek":"1-4,6-19","courseName":"好时间"}
        ]}]}"""
        val skipBad = JwQzAppParser(badTime).generateCourseList()
        // 好时间 1 门 × 2 周段 = 2 行
        assertEquals(2, skipBad.size)
        assertTrue("全部为好时间", skipBad.all { it.name == "好时间" })
    }

    @Test
    fun `parseWeekSpec handles range single and mixed tokens`() {
        val p = JwQzAppParser("")
        // 区间
        assertEquals(listOf(1, 2, 3, 4), p.parseWeekSpec("1-4"))
        // 单周
        assertEquals(listOf(3), p.parseWeekSpec("3"))
        // 混合
        assertEquals(listOf(1, 2, 3, 4, 6, 7, 8, 9, 10),
            p.parseWeekSpec("1-4,6-10"))
        // 域外忽略
        assertEquals(listOf(1, 30), p.parseWeekSpec("1,30,31,0,-1,abc,1-"))
        // 空
        assertEquals(emptyList<Int>(), p.parseWeekSpec(""))
        // 倒序区间忽略 (lo>hi)
        assertEquals(listOf(1, 2, 3), p.parseWeekSpec("1-3,5-2"))
    }

    @Test
    fun `weekRuns single contiguous and step-2 collapse and multi-split`() {
        val p = JwQzAppParser("")
        // 单连续 → type=0
        assertEquals(listOf(Triple(1, 4, 0)), p.weekRuns(listOf(1, 2, 3, 4)))
        // 单元素 → type=0
        assertEquals(listOf(Triple(3, 3, 0)), p.weekRuns(listOf(3)))
        // 步 2 整体等差: 奇起 → 单周 type=1
        assertEquals(listOf(Triple(1, 9, 1)), p.weekRuns(listOf(1, 3, 5, 7, 9)))
        // 步 2 偶起 → 双周 type=2
        assertEquals(listOf(Triple(2, 10, 2)), p.weekRuns(listOf(2, 4, 6, 8, 10)))
        // 多段 → 逐段 type=0
        assertEquals(
            listOf(Triple(1, 4, 0), Triple(6, 19, 0)),
            p.weekRuns(listOf(1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19)),
        )
        // 空
        assertEquals(emptyList<Triple<Int, Int, Int>>(), p.weekRuns(emptyList()))
    }

    @Test
    fun `confidence anchors - classWeekDetails with classTime is 90`() {
        val strong = """
            {"code":"1","data":[{"courses":[
                {"classWeekDetails":",1,2,3,4,","classTime":"10304","courseName":"x"}
            ]}]}
        """.trimIndent()
        assertEquals(90, JwQzAppParser(strong).confidence())
    }

    @Test
    fun `confidence anchors - classWeek with classTime is 85`() {
        val ok = """
            {"code":"1","data":[{"courses":[
                {"classWeek":"1-4,6-19","classTime":"10304","courseName":"x"}
            ]}]}
        """.trimIndent()
        assertEquals(85, JwQzAppParser(ok).confidence())
    }

    @Test
    fun `confidence anchors - no markers is 0`() {
        assertEquals(0, JwQzAppParser("""{"code":"1"}""").confidence())
        // 只有 classTime 缺 classWeek 系
        assertEquals(0, JwQzAppParser("""{"classTime":"10304"}""").confidence())
        // 只有 classWeek 缺 classTime
        assertEquals(0, JwQzAppParser("""{"classWeek":"1-4"}""").confidence())
    }

    @Test
    fun `matchedFeatures list returns only present markers`() {
        val full = """{"classWeekDetails":",1,","classTime":"10304","courses":[],"code":"1","Msg":"x"}"""
        val p = JwQzAppParser(full)
        val feats = p.matchedFeatures()
        assertTrue("classWeekDetails", feats.contains("classWeekDetails"))
        assertTrue("classTime", feats.contains("classTime"))
        assertTrue("courses[]", feats.contains("courses[]"))
        assertTrue("code/Msg envelope", feats.contains("code/Msg envelope"))
    }

    @Test
    fun `classroomNub field is full room code Z5-117 not just building`() {
        val courses = JwQzAppParser(loadFixture()).generateCourseList()
        // 抽样: 广告策划与创意 房号应为 Z5-117 (楼栋+房号完整, 不是被裁剪的 "Z5")
        val ads = courses.first { it.name == "广告策划与创意" }
        assertEquals("完整房号楼栋+房号", "Z5-117", ads.room)
    }


    // ===== 逐周合并形态 (2026-09-11 用户反馈: week= 空 只回当前周, 后续周课程整门丢失) =====

    /** 构造单周响应: data:[{date:[…], courses:[…]}] — week=N 时学校端只回该周出现的课。 */
    private fun weekPayload(week: Int, coursesJson: String): String = """
        {"code":"1","Msg":"success~","data":[{"date":[
            {"xqmc":"一","mxrq":"2026-09-07","zc":"$week","xqid":"1","rq":"07"}],
         "courses":[$coursesJson]}],"needClassName":1,"needClassRoomNub":1}
    """.trimIndent()

    @Test
    fun `merged multi-week source - dedupes identical rows across week responses`() {
        // 同一门课 (10 节大课, classWeek "1-4,6-19") 在 20 次周请求里各出现一次,
        // 行内容完全一致 → 只展开一次 (20 JwCourse 而非 40)
        val row = """{"courseName":"大学物理","teacherName":"张三",
            "classroomNub":"Z5-117","classTime":"10304","classWeek":"1-4,6-19",
            "classWeekDetails":",1,2,3,4,6,7,8,9,10,11,12,13,14,15,16,17,18,19"}"""
        val source = """{"weeks":[${(1..20).joinToString(",") { w ->
            weekPayload(w, row)
        }}]}"""
        val courses = JwQzAppParser(source).generateCourseList()
        assertEquals("20 周响应里同一行只保留一份", 2, courses.size)
        assertEquals(1, courses[0].startWeek)
        assertEquals(4, courses[0].endWeek)
        assertEquals(6, courses[1].startWeek)
        assertEquals(19, courses[1].endWeek)
    }

    @Test
    fun `merged multi-week source - keeps course that only appears in a later week`() {
        // 只在第 8-10 周上的课: 当前周 (week=1) 响应里根本没有 →
        // 旧逻辑 (只取 data.firstOrNull) 整门丢失, 新逻辑必须捞到
        val w1 = weekPayload(1, """{"courseName":"高等数学","teacherName":"李四",
            "classroomNub":"J2-201","classTime":"20102","classWeek":"1-19",
            "classWeekDetails":",1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19"}""")
        val w8 = weekPayload(8, """{"courseName":"大学物理实验","teacherName":"王五",
            "classroomNub":"S1-302","classTime":"50506","classWeek":"8-10",
            "classWeekDetails":",8,9,10"}""")
        val source = """{"weeks":[$w1,$w8]}"""
        val courses = JwQzAppParser(source).generateCourseList()
        val names = courses.map { it.name }.toSet()
        assertTrue(
            "只存在于后续周的课必须被保留 (旧 firstOrNull 逻辑整门丢失): got $names",
            "大学物理实验" in names
        )
        val lab = courses.filter { it.name == "大学物理实验" }
        assertEquals(1, lab.size)
        assertEquals(8, lab[0].startWeek)
        assertEquals(10, lab[0].endWeek)
    }

    @Test
    fun `legacy single-payload source - data array without weeks wrapper still parses`() {
        // 旧形态 (直接 POST 单响应 / 采集包 fixture) 不回归
        val courses = JwQzAppParser(loadFixture()).generateCourseList()
        assertEquals(20, courses.size)
    }

    @Test
    fun `empty weeks array and non-object elements are skipped`() {
        val source = """{"weeks":[]}"""
        assertTrue(JwQzAppParser(source).generateCourseList().isEmpty())
        val junk = """{"weeks":[null,"x",3,{"data":{"courses":[]}}]}"""
        assertTrue(JwQzAppParser(junk).generateCourseList().isEmpty())
    }


    // ===== 多周合并 + 连堂多段 classTime (2026-09-11 学生三周采集包实锤) =====

    private fun loadMultiweekFixture(): String {
        val stream = javaClass.classLoader?.getResourceAsStream(
            "jw/fixtures/qz_app/curriculum.multiweek.sample.json"
        )
        assertNotNull("fixture jw/fixtures/qz_app/curriculum.multiweek.sample.json 应存在", stream)
        return stream!!.bufferedReader().use { it.readText() }
    }

    @Test
    fun `multiweek real captures - 9 courses 29 JwCourse no loss`() {
        val courses = JwQzAppParser(loadMultiweekFixture()).generateCourseList()
        assertEquals(29, courses.size)
        val names = courses.map { it.name }.toSet()
        assertEquals(
            "9 门课全保留 (ITMC 曾因 9 位 classTime 被丢)",
            setOf(
                "ITMC市场营销沙盘模拟（二）",
                "商务数据分析",
                "客户关系管理",
                "广告策划与创意",
                "数字营销",
                "消费行为分析",
                "短视频策划与制作",
                "管理学",
                "财务管理（市场营销专业）",
            ),
            names,
        )
    }

    @Test
    fun `multiweek real captures - ITMC two back-to-back segments on Wednesday`() {
        val courses = JwQzAppParser(loadMultiweekFixture()).generateCourseList()
        val itmc = courses.filter { it.name == "ITMC市场营销沙盘模拟（二）" }
        // classTime "301020304" = 周三 1-2 + 3-4 两段; classWeek 11-19 → 2 段周次 = 2 JwCourse
        val segs = itmc.map { Triple(it.day, it.startNode, it.endNode) }.toSet()
        assertEquals("周三 1-2 与 3-4 两段都在", setOf(Triple(3, 1, 2), Triple(3, 3, 4)), segs)
        itmc.forEach {
            assertEquals("周次 11-19", 11, it.startWeek)
            assertEquals(19, it.endWeek)
        }
    }

    @Test
    fun `multiweek real captures - cross-week duplicate rows collapsed, distinct rooms kept`() {
        val courses = JwQzAppParser(loadMultiweekFixture()).generateCourseList()
        // 广告策划与创意: 周一3-4 (Z5-117) + 周二5-6 (Z5-103, 7-15) — 两行不同教室都保留
        val ad117 = courses.filter { it.name == "广告策划与创意" && it.room == "Z5-117" }
        val ad103 = courses.filter { it.name == "广告策划与创意" && it.room == "Z5-103" }
        assertTrue("Z5-117 行保留", ad117.isNotEmpty())
        assertTrue("Z5-103 行保留 (只在 7-15 周响应出现)", ad103.isNotEmpty())
        assertEquals(7, ad103[0].startWeek)
        assertEquals(15, ad103[0].endWeek)
    }
}
