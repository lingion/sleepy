package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T12 合约: 临沂大学条目 + schools.json status 字段接入 + 审计块过滤
 *
 * 纯 JVM 单测: 无 Android 依赖, 只依赖 org.json + JUnit4。
 * schools.json 通过 classpath 加载，由 Gradle 从主资产生成。
 */
class JwNewSchoolsTest {

    /** 加载由 Gradle 生成的学校目录测试资源。 */
    private fun loadSchoolsJson(): String = requireNotNull(
        javaClass.classLoader?.getResourceAsStream("jw/schools.json")
    ) { "缺少自动生成的 jw/schools.json，请通过 Gradle 运行测试。" }
        .bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** 走生产同源解析路径(companion static, T12 方案 A) */
    private fun parse(text: String): List<JwSchoolInfo> = JwImportViewModel.parseSchoolsJson(text)

    // -------- 1. 学校条目存在性 + 字段正确性 --------

    @Test
    fun `Zhejiang University of Finance and Economics uses student SSO login`() {
        val schools = parse(loadSchoolsJson())
        val zufe = schools.single { it.name == "浙江财经大学" }
        assertEquals("学生入口必须使用统一身份认证地址", "http://jwxt.zufe.edu.cn/sso/driotlogin", zufe.url)
        assertEquals("浙江财经大学仍使用新版正方协议", "zf_new", zufe.type)
    }

    @Test
    fun `Linyi University is the single zf_new entry under sortKey L`() {
        val schools = parse(loadSchoolsJson())
        val matches = schools.filter { it.name == "临沂大学" }
        assertEquals("临沂大学必须有且仅有一条条目", 1, matches.size)
        val lyu = matches.single()
        assertEquals("L", lyu.sortKey)
        assertEquals("zf_new", lyu.type)
        assertEquals("http://jwgl.lyu.edu.cn/jwglxt", lyu.url)
        assertTrue("至少含 lyu.edu.cn 别名", lyu.aliases.any { it == "lyu.edu.cn" })
        assertTrue("至少含 linyidaxue 别名", lyu.aliases.any { it == "linyidaxue" })
        assertEquals("linyidaxue", lyu.sortKeyFull)
        assertEquals("临沂条目默认 supported(未被 status 标灰)", JwSchoolInfo.STATUS_SUPPORTED, lyu.status)
    }

    @Test
    fun `Linyi entry aliases support search by pinyin and domain`() {
        val schools = parse(loadSchoolsJson())
        val lyu = schools.single { it.name == "临沂大学" }
        val queryTargets = listOf("lyu", "lyu.edu.cn", "linyidaxue")
        for (q in queryTargets) {
            assertTrue("alias '$q' 缺失", lyu.aliases.contains(q))
        }
        assertTrue("name 应含 '临沂'", lyu.name.contains("临沂"))
    }

    @Test
    fun `Linyi University url matches detectProtocolFromUrl jwglxt rule`() {
        val url = "http://jwgl.lyu.edu.cn/jwglxt"
        val u = url.lowercase()
        assertTrue("url 必须包含 'jwglxt' 才会被分发到 zf_new", u.contains("jwglxt"))
        assertEquals("zf_new", JwProtocol.TYPE_ZF_NEW)
        assertEquals("zf_new", schoolsParsedType())
    }

    private fun schoolsParsedType(): String {
        val schools = parse(loadSchoolsJson())
        return schools.single { it.name == "临沂大学" }.type ?: ""
    }

    @Test
    fun `Linyi entry is NOT legacy QZ fingerprint (jxd is retired)`() {
        val schools = parse(loadSchoolsJson())
        val lyu = schools.single { it.name == "临沂大学" }
        assertFalse("现行条目禁止配 qz", lyu.type == JwProtocol.TYPE_QZ)
        assertFalse("现行 url 禁止含 /jxd/", lyu.url.contains("/jxd/"))
        assertFalse("现行 url 禁止含 jsxsd", lyu.url.contains("jsxsd"))
    }

    @Test
    fun `AHYZ uses the verified import entry URL`() {
        val schools = parse(loadSchoolsJson())
        val ahyz = schools.single { it.name == "安徽第二医学院" }
        assertEquals("http://ehall.ahyz.cn/new/index.html", ahyz.url)
        assertEquals(JwProtocol.TYPE_WISEDU, ahyz.type)
    }

    // -------- 2. parseSchoolsJson 读取 status 字段 --------

    @Test
    fun `parseSchoolsJson reads explicit status values`() {
        val mini = """
            [
              {"sortKey": "Z", "name": "测试A", "url": "https://a.edu/", "type": "zf_new", "aliases": [], "sortKeyFull": "a"},
              {"sortKey": "Z", "name": "测试B", "url": "https://b.edu/", "type": "qz", "aliases": [], "sortKeyFull": "b", "status": "pending"},
              {"sortKey": "Z", "name": "测试C", "url": "https://c.edu/", "type": "urp", "aliases": [], "sortKeyFull": "c", "status": "legacy"},
              {"sortKey": "Z", "name": "测试D", "url": "https://d.edu/", "type": "wisedu", "aliases": [], "sortKeyFull": "d", "status": "grad_supported"},
              {"sortKey": "Z", "name": "测试E", "url": "https://e.edu/", "type": "urp_new", "aliases": [], "sortKeyFull": "e", "status": "grad_pending"}
            ]
        """.trimIndent()
        val parsed = parse(mini)
        assertEquals(5, parsed.size)
        assertEquals(JwSchoolInfo.STATUS_SUPPORTED, parsed[0].status)
        assertEquals(JwSchoolInfo.STATUS_PENDING, parsed[1].status)
        assertEquals(JwSchoolInfo.STATUS_LEGACY, parsed[2].status)
        assertEquals(JwSchoolInfo.STATUS_GRAD_SUPPORTED, parsed[3].status)
        assertEquals(JwSchoolInfo.STATUS_GRAD_PENDING, parsed[4].status)
        assertTrue(parsed[0].isSupported)
        assertFalse(parsed[1].isSupported)
        assertFalse(parsed[2].isSupported)
        assertTrue(parsed[3].isSupported)
        assertFalse(parsed[4].isSupported)
        assertFalse(parsed[0].isGrad)
        assertTrue(parsed[3].isGrad)
        assertTrue(parsed[4].isGrad)
    }

    @Test
    fun `parseSchoolsJson defaults status to supported when missing or blank`() {
        val mini = """
            [
              {"sortKey": "Z", "name": "测试X", "url": "https://x.edu/", "type": "zf_new", "aliases": [], "sortKeyFull": "x"},
              {"sortKey": "Z", "name": "测试Y", "url": "https://y.edu/", "type": "qz", "aliases": [], "sortKeyFull": "y", "status": ""}
            ]
        """.trimIndent()
        val parsed = parse(mini)
        assertEquals(2, parsed.size)
        assertEquals(JwSchoolInfo.STATUS_SUPPORTED, parsed[0].status)
        assertEquals(JwSchoolInfo.STATUS_SUPPORTED, parsed[1].status)
    }

    @Test
    fun `parseSchoolsJson normalizes unknown status values to pending`() {
        val mini = """
            [{"sortKey": "Z", "name": "测试Z", "url": "https://z.edu/", "type": "qz", "aliases": [], "sortKeyFull": "z", "status": "garbage_value"}]
        """.trimIndent()
        val parsed = parse(mini)
        assertEquals(1, parsed.size)
        assertEquals(JwSchoolInfo.STATUS_PENDING, parsed[0].status)
        assertFalse(parsed[0].canImport)
    }

    @Test
    fun `catalog identity fetch switch and unverified status survive loading`() {
        val school = parse("""[{"name":"测试学校","catalogId":"source:123","status":"unverified","url":"https://jw.example.edu/","type":"qz","enableFetch":true}]""").single()
        assertEquals("source:123", school.stableKey)
        assertTrue(school.enableFetch)
        assertTrue(school.isUnverified)
        assertTrue(school.canImport)
        assertFalse(school.isSupported)
    }

    @Test fun `null absent and unknown protocols cannot enable unverified entries`() {
        val parsed = parse("""[
            {"name":"Null","type":null,"url":"https://jw.example.edu/","status":"unverified"},
            {"name":"Absent","url":"https://jw.example.edu/","status":"unverified"},
            {"name":"Unknown","type":"future_protocol","url":"https://jw.example.edu/","status":"unverified"}
        ]""")
        assertEquals(null, parsed[0].type)
        assertEquals(null, parsed[1].type)
        assertTrue(parsed.none { it.canImport })
    }

    // -------- 3. 审计块过滤 --------

    @Test
    fun `parseSchoolsJson skips audit block entries whose keys start with underscore`() {
        val mini = """
            [
              {"sortKey": "L", "name": "临沂大学", "url": "http://jwgl.lyu.edu.cn/jwglxt", "type": "zf_new", "aliases": ["lyu"], "sortKeyFull": "linyidaxue"},
              {"__audit_status_2026_08_30__": {"__note__": "T12 audit snapshot", "nxdomain": [{"name": "X大学", "url": "http://nx.edu/", "type": "qf"}]}},
              {"_legacy_entry": {"name": "Y大学"}}
            ]
        """.trimIndent()
        val parsed = parse(mini)
        assertEquals("审计块必须被过滤, 只剩 1 条运行时条目", 1, parsed.size)
        assertEquals("临沂大学", parsed[0].name)
        assertFalse(parsed.any { it.name == "X大学" })
        assertFalse(parsed.any { it.name == "Y大学" })
    }

    // -------- 4. 排序约束 --------

    @Test
    fun `Linyi University is positioned between Liaoning Gongye and next L entry after sort`() {
        val parsed = parse(loadSchoolsJson())
        val sorted = parsed.sortedWith(compareBy({ it.sortKey }, { it.sortKeyFull }))
        val linyi = sorted.single { it.name == "临沂大学" }
        val liaoning = sorted.single { it.name == "辽宁工业大学" }
        val linyiIdx = sorted.indexOf(linyi)
        val liaoningIdx = sorted.indexOf(liaoning)
        assertTrue("辽宁工业大学(liaoninggongyedaxue)应排在临沂大学(linyidaxue)之前", liaoningIdx < linyiIdx)
        assertEquals("L", linyi.sortKey)
        val lGroup = sorted.filter { it.sortKey == "L" }
        // 不变量断言(随条目递增): L 组按 sortKeyFull 升序, 辽宁工业大学恒在临沂大学之前
        assertTrue("L 组应按 sortKeyFull 升序", lGroup.zipWithNext().all { (a, b) -> a.sortKeyFull <= b.sortKeyFull })
        assertTrue("辽宁工业大学应排在临沂大学之前", lGroup.indexOfFirst { it.name == "辽宁工业大学" } < lGroup.indexOfFirst { it.name == "临沂大学" })
    }

    // -------- 5. 协议分发一致性 --------

    @Test
    fun `every non-audit entry type is routable (report unknown types when T3-T8 not ready)`() {
        val knownTypes = setOf(
            JwProtocol.TYPE_QZ, JwProtocol.TYPE_QZ_OLD, JwProtocol.TYPE_QZ_CRAZY,
            JwProtocol.TYPE_QZ_BR, JwProtocol.TYPE_QZ_WITH_NODE, JwProtocol.TYPE_QZ_IEAS, JwProtocol.TYPE_QZ_APP, JwProtocol.TYPE_UCAS,
            JwProtocol.TYPE_ZF, JwProtocol.TYPE_ZF_1, JwProtocol.TYPE_ZF_NEW,
            JwProtocol.TYPE_URP, JwProtocol.TYPE_URP_NEW, JwProtocol.TYPE_WISEDU,
            JwProtocol.TYPE_CQU, JwProtocol.TYPE_CHAOXING, JwProtocol.TYPE_BOYA_PP, JwProtocol.TYPE_HNUST,
            JwProtocol.TYPE_EAMS5, JwProtocol.TYPE_SEU, JwProtocol.TYPE_ZJU,
            JwProtocol.TYPE_USTC, JwProtocol.TYPE_SCU, JwProtocol.TYPE_NEU, JwProtocol.TYPE_BJTU,
            JwProtocol.TYPE_WHUT, JwProtocol.TYPE_CLASSIC_EAMS, JwProtocol.TYPE_YETHAN,
            JwProtocol.TYPE_XJU_POST, JwProtocol.TYPE_CF_NEW,
            JwProtocol.TYPE_KUST, JwProtocol.TYPE_NUIT, JwProtocol.TYPE_THU,
            // 8e3524cc 批量晋升后 hniu 进入目录, JwParserRegistry:126 运行时路由已就位
            JwProtocol.TYPE_HNIU,
            JwProtocol.TYPE_SG_ASSET,
            // 登记型 (2026-10-28 WakeUP 6.6.2 目录吸收): FACTORIES-only 注册, supports()=真可过
            // canImport 门进 WebView, 不进 TYPE_PRIORITY/run-all 候选, 登录后自动检测已收录协议
            JwProtocol.TYPE_JW_HOME
        )
        val pendingTypes = listOf(
            "com.lingion.sleepy.data.jw.JwChengFangParser" to "cf",
            "com.lingion.sleepy.data.jw.JwPekingParser" to "pku",
            "com.lingion.sleepy.data.jw.JwBnuzParser" to "bnuz",
            "com.lingion.sleepy.data.jw.JwHnustParser" to "hnust"
        )
        val readyPending = pendingTypes.mapNotNull { (cls, type) ->
            runCatching { Class.forName(cls) }.getOrNull()?.let { type }
        }.toSet()
        // WakeUp 兼容层类型 (kingo_new/scau/cumtb/ecupl 等) 由 JwParserRegistry 运行时路由,
        // 与 SchoolsJsonConsistencyTest 同一口径.
        val acceptable = knownTypes + readyPending + JwProtocol.WAKEUP_COMPAT_TYPES

        val schools = parse(loadSchoolsJson())
        val unknown = schools.filter { it.type != null && it.type !in acceptable }
        if (unknown.isNotEmpty()) {
            println("[T12] 过期/未知 type 条目清单:")
            unknown.forEach { println("  - ${it.name} (sortKey=${it.sortKey}) type=${it.type}") }
        }
        assertTrue(
            "未知/未路由 type 条目: ${unknown.joinToString { "${it.name}=${it.type}" }}",
            unknown.isEmpty()
        )
    }

    @Test
    fun `Linyi entry is not greyed out by status and is selectable`() {
        val schools = parse(loadSchoolsJson())
        val lyu = schools.single { it.name == "临沂大学" }
        assertTrue("临沂大学必须可被选择(status=supported)", lyu.isSupported)
        assertNotNull(lyu.url)
        assertTrue(lyu.url.isNotBlank())
        assertNotNull(lyu.type)
    }
}
