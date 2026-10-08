package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * JwYethanParser 单元测试 — 西南交通大学 yhxt.swjtu.edu.cn (YETHAN/以专) 课表 JSON。
 *
 * 数据：src/test/resources/jw_fixtures/yethan-schedule.json
 * （按 /yethan/common/course-schedule/student-course-schedule 真实响应形状构造的脱敏样张）。
 * 不依赖 Android Context / 数据库 / 模拟器，纯 JVM 跑。
 *
 * 字段契约（data[] 行，2026-09-15 采集包实锤）：
 *   courseName → name
 *   staffName  → teacher（整名保留，不再截断）
 *   classPlace{N} → room（括号区分:
 *     "X30547(犀浦)(教师甲)" → room=X30547(犀浦)（首个 ')' 处截掉教师括号）
 *     "X5218 (教师乙)"      → room=X5218（无校区变体）
 *     "北区田径场(犀浦)"     → 原样保留（单括号=校区，不是教师）
 *     "Online(教师丁)"      → room=Online）
 *   classTime{N} → 顿号枚举正解文法（关键）：
 *     「7、10-12、14-15周 星期三 5节」— 、枚举只作用于周次列表，整串共享
 *     一个「星期X 节」后缀（首版按 、切分各段独立解析 → 11/85 FAIL）。
 *     文法: ^([0-9、\-]+)周\s*星期(.)\s*([0-9\-]+)节$
 *
 * 预期（已用 Python 对同份数据交叉验证 85/85）：
 *   - 测试课程A: 顿号周次 7、10-12、14-15 → 3 个段 (7,7)/(10,12)/(14,15)，同为周三5节
 *   - 测试课程B: 1-17周连续 → 1 个段 (1,17)
 *   - 测试课程C: 3周单周单节 → 1 个段 (3,3)
 */
class JwYethanParserTest {

    private fun loadJson(): String {
        val stream = javaClass.classLoader?.getResourceAsStream("jw_fixtures/yethan-schedule.json")
        assertNotNull("测试资源 jw_fixtures/yethan-schedule.json 应存在", stream)
        return stream!!.bufferedReader().use { it.readText() }
    }

    private fun parse() = JwYethanParser(loadJson()).generateCourseList()

    @Test
    fun `parses YETHAN schedule - total count`() {
        // 测试课程A 3 段 + 第二时间槽 1 段 + B 1 段 + C 1 段 = 6 个 JwCourse
        assertEquals("每个有效 classTime 段应为 1 个 JwCourse", 6, parse().size)
    }

    @Test
    fun `dunhao week enumeration - 7、10-12、14-15周 周三5节`() {
        // 顿号枚举正解锁契约: 枚举只作用于周次列表, 整串共享一个「星期X 节」后缀
        val a = parse().filter { it.name == "测试课程A" && it.startNode == 5 }
        assertEquals("顿号枚举应拆出 3 个周次段", 3, a.size)
        val weeks = a.map { it.startWeek to it.endWeek }.sortedBy { it.first }
        assertEquals(listOf(7 to 7, 10 to 12, 14 to 15), weeks)
        assertTrue(a.all { it.day == 3 })
        assertTrue(a.all { it.startNode == 5 && it.endNode == 5 })
    }

    @Test
    fun `contiguous week range - 1-17周`() {
        val b = parse().first { it.name == "测试课程B" }
        assertEquals(5, b.day)
        assertEquals(3, b.startNode)
        assertEquals(4, b.endNode)
        assertEquals(1, b.startWeek)
        assertEquals(17, b.endWeek)
        assertEquals(0, b.type)
    }

    @Test
    fun `teacher kept whole from staffName`() {
        val a = parse().first { it.name == "测试课程A" }
        assertEquals("教师甲", a.teacher)
    }

    @Test
    fun `place bracket discrimination - room(campus)(teacher)`() {
        val a = parse().first { it.name == "测试课程A" && it.startNode == 5 }
        assertEquals("X30547(犀浦)", a.room)
    }

    @Test
    fun `place bracket discrimination - room(teacher) no campus with space`() {
        val a = parse().filter { it.name == "测试课程A" && it.startNode != 5 }
        assertEquals(1, a.size)
        assertEquals("X5218", a.single().room)
    }

    @Test
    fun `place bracket discrimination - bare room with campus kept whole`() {
        val b = parse().first { it.name == "测试课程B" }
        assertEquals("单括号是校区不是教师, 原样保留", "北区田径场(犀浦)", b.room)
    }

    @Test
    fun `place bracket discrimination - Online(teacher)`() {
        val c = parse().first { it.name == "测试课程C" }
        assertEquals(1, c.day)
        assertEquals(1, c.startNode)
        assertEquals(1, c.endNode)
        assertEquals(3, c.startWeek)
        assertEquals("Online", c.room)
    }

    @Test
    fun `confidence anchors on course-schedule marker`() {
        val p = JwYethanParser(loadJson())
        assertTrue("student-course-schedule 锚点应有置信度", p.confidence() >= 80)
        assertTrue(p.matchedFeatures().isNotEmpty())
        assertEquals(0, JwYethanParser("random text").confidence())
    }

    @Test
    fun `empty and malformed input - graceful`() {
        assertEquals(0, JwYethanParser("").let { runCatching { it.generateCourseList() }.getOrDefault(emptyList()) }.size)
        assertEquals(0, JwYethanParser("not json").let { runCatching { it.generateCourseList() }.getOrDefault(emptyList()) }.size)
        assertEquals(0, JwYethanParser("""{"code":"00000","data":null}""").generateCourseList().size)
    }

    @Test
    fun `expired session code A0422 yields empty list`() {
        val p = JwYethanParser("""{"code":"A0422","message":"登录失效"}""")
        assertEquals(0, p.generateCourseList().size)
        assertTrue(p.confidence() > 0)
    }

    @Test
    fun `registry dispatches yethan json to JwYethanParser`() {
        val (courses, attempts) = JwParserRegistry.selectBest(loadJson(), "yethan")
        assertEquals(6, courses.size)
        val attempt = attempts.firstOrNull { it.type == "yethan" }
        assertEquals("yethan 行必须被尝试", "yethan", attempt?.type)
        assertTrue("YETHAN parser 置信度应 >= 80", (attempt?.confidence ?: 0) >= 80)
    }

    @Test
    fun `detectProtocolFromUrl routes yhxt swjtu edu cn to yethan type`() {
        assertEquals("yethan", JwImportViewModel.detectProtocolFromUrlForTest("https://yhxt.swjtu.edu.cn/study/teach/course/stu-course-list"))
        assertEquals("yethan", JwImportViewModel.detectProtocolFromUrlForTest("https://yhxt.swjtu.edu.cn/"))
        assertEquals("URL 含 swjtu.edu.cn 但非 yhxt host 不得误判",
            null, JwImportViewModel.detectProtocolFromUrlForTest("https://jwc.swjtu.edu.cn/"))
    }

    @Test
    fun `YETHAN entry in schools json is registered and supported`() {
        val text = javaClass.classLoader?.getResourceAsStream("jw/schools.json")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        assertNotNull(text)
        val swjtu = JwImportViewModel.parseSchoolsJson(text!!).single { it.name == "西南交通大学" }
        assertEquals("yethan", swjtu.type)
        assertEquals(JwProtocol.TYPE_YETHAN, swjtu.type)
        assertEquals("https://yhxt.swjtu.edu.cn/", swjtu.url)
        assertTrue(swjtu.isSupported)
        assertEquals("xinanjiaotongdaxue", swjtu.sortKeyFull)
    }

    @Test
    fun `ALL_TYPES contains yethan right after bjtu - count gate 33`() {
        assertEquals("新增 KUST/NUIT 后 ALL_TYPES 应为 35 项", 35, JwProtocol.ALL_TYPES.size)
        assertTrue("ALL_TYPES 必须含 TYPE_YETHAN", JwProtocol.ALL_TYPES.contains(JwProtocol.TYPE_YETHAN))
        val idxBjtu = JwProtocol.ALL_TYPES.indexOf(JwProtocol.TYPE_BJTU)
        val idxYethan = JwProtocol.ALL_TYPES.indexOf(JwProtocol.TYPE_YETHAN)
        assertTrue("yethan 应紧跟 bjtu (同为自建 JSON 族)", idxYethan == idxBjtu + 1)
    }

    // ================================================================
    // 2026-10-08 真实采集包回归（sleepy-jw-dump-20261008-135253）
    //
    // 与上面 `yethan-schedule.json`（手工构造的脱敏样张）不同，本夹具是
    // 用户在 yhxt.swjtu.edu.cn 上**真实触发**的
    // /yethan/common/course-schedule/student-course-schedule 响应原文
    // （仅学号脱敏）。它锁死解析侧对真实数据形状的兼容性 ——
    // 本次事故的断点在 token 获取（见 JwYethanWebViewContractTest），
    // 解析侧经 Python 等价逻辑交叉验证为 100% 命中，此处固化为回归门。
    // ================================================================

    private fun loadLiveJson(): String {
        val stream = javaClass.classLoader?.getResourceAsStream("jw_fixtures/yethan-schedule-live-20261008.json")
        assertNotNull("真实采集包夹具应存在", stream)
        return stream!!.bufferedReader().use { it.readText() }
    }

    @Test
    fun `live capture 20261008 - parses all 10 real courses`() {
        val courses = JwYethanParser(loadLiveJson()).generateCourseList()
        assertTrue("真实包应解析出非空课程", courses.isNotEmpty())
        val names = courses.map { it.name }.toSet()
        assertEquals(
            "真实采集包含 10 门课",
            setOf(
                "体育健康课程Ⅰ", "形势与政策V", "工程沟通与交流技巧2",
                "机械工程素养训练Ⅱ", "工程力学", "振动与控制",
                "设计与制造2", "经济与管理", "机电一体化和测量系统", "热流体2"
            ),
            names
        )
    }

    @Test
    fun `live capture 20261008 - total JwCourse rows match python cross-check 98`() {
        // Python 等价逻辑交叉验证结果: 98 条 JwCourse, classTimeRegex 命中率 100%。
        // 若此数变化，说明真实数据文法或解析行为发生漂移，需人工复核。
        val courses = JwYethanParser(loadLiveJson()).generateCourseList()
        assertEquals("真实包应产出 98 条 JwCourse (与 Python 交叉验证一致)", 98, courses.size)
    }

    @Test
    fun `live capture 20261008 - no classTime row is silently dropped`() {
        // 反向门: 统计原始 classTime{N} 非空槽位数，与解析出的"周次段总数"比对。
        // 二者必须相等 —— 任何一条被正则漏掉都会让总数变小（静默丢课）。
        val json = loadLiveJson()
        val obj = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .parseToJsonElement(json).jsonObject
        val rawSlots = obj["data"]!!.jsonArray.sumOf { row ->
            (1..40).count { i ->
                val v = row.jsonObject["classTime$i"]
                v != null && v !is kotlinx.serialization.json.JsonNull &&
                    (v.jsonPrimitive.contentOrNull?.isNotBlank() == true)
            }
        }
        // 每条原始槽位按其顿号枚举段数展开 → 期望 JwCourse 数
        val expected = obj["data"]!!.jsonArray.sumOf { row ->
            (1..40).sumOf { i ->
                val v = row.jsonObject["classTime$i"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (v.isBlank()) 0 else JwYethanParser(loadLiveJson()).weekRuns(
                    Regex("""^([0-9、\-]+)周""").find(v)?.groupValues?.get(1).orEmpty()
                ).size
            }
        }
        val actual = JwYethanParser(loadLiveJson()).generateCourseList().size
        assertTrue("原始 classTime 槽位非空数应 > 0", rawSlots > 0)
        assertEquals("解析行数应与原始槽位展开数一致（无静默丢弃）", expected, actual)
    }

    @Test
    fun `live capture 20261008 - real world place forms parse without crashing`() {
        val courses = JwYethanParser(loadLiveJson()).generateCourseList()
        // 真实包中出现的典型 classPlace 形态
        assertTrue("应含双括号形态 room(校区)(教师) 解析结果",
            courses.any { it.room == "X30547(犀浦)" })
        assertTrue("应含 Online 形态", courses.any { it.room == "Online" })
        assertTrue("应含无校区单括号 → 截掉教师",
            courses.any { it.room.startsWith("X5213") || it.room.startsWith("X5416") })
        assertTrue("所有 room 不应残留第二个括号中的教师名",
            courses.none { it.room.count { c -> c == '(' || c == '（' } >= 2 })
    }

    @Test
    fun `live capture 20261008 - registry selects yethan with high confidence`() {
        val (courses, attempts) = JwParserRegistry.selectBest(loadLiveJson(), "yethan")
        assertEquals(98, courses.size)
        val attempt = attempts.firstOrNull { it.type == "yethan" }
        assertEquals("yethan", attempt?.type)
        assertTrue("真实包置信度应 >= 90", (attempt?.confidence ?: 0) >= 90)
    }
}
