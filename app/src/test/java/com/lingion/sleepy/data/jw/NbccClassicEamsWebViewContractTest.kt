package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * NBCC classic_eams 形态B 跨语言 invariant 契约测试 (批B brief 测试②, SOP 铁律3)。
 *
 * 方法论逐句照抄 HfutPortalEams5WebViewContractTest:
 *   读源文件文本 → 定位 `private const val CLASSIC_EAMS_FETCH_JS = """` 提取 JS 体 →
 *   对 §4 常量表每个 pattern/needle/模板/默认值断言 JS 内逐字符出现。
 * Kotlin 侧 pattern 从 ClassicEamsWebVpn.kt 源文本 marker 提取(不依赖反射)。
 * 容差仅一条(§4 invariant 段原文口径): JS 字面量外层 /…/flags 与 Kotlin 三引号壳,
 * 以及 JS 字面量体内必需的 `\/` 转义 — 除此之外逐字符相等。
 * unitCount 类判据不在 JS 出现(JS 不解码, 解码唯一落点 parser)→ 不锁, v1.9-② 同口径。
 */
class NbccClassicEamsWebViewContractTest {

    private fun loadSourceFile(path: String): String {
        for (candidate in listOf("src/main/java/$path", "app/src/main/java/$path")) {
            val f = File(candidate)
            if (f.exists()) return f.readText(Charsets.UTF_8)
        }
        error("source file not found: $path")
    }

    private fun extractConstBody(src: String, marker: String): String {
        val start = src.indexOf(marker)
        assertTrue("marker missing: $marker", start >= 0)
        val bodyStart = start + marker.length
        val bodyEnd = src.indexOf("\"\"\"", bodyStart)
        assertTrue("const body not terminated: $marker", bodyEnd > bodyStart)
        return src.substring(bodyStart, bodyEnd)
    }

    private fun fetchJs(): String = extractConstBody(
        loadSourceFile("com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
        "private const val CLASSIC_EAMS_FETCH_JS = \"\"\""
    )

    private fun constantsSource(): String =
        loadSourceFile("com/lingion/sleepy/data/jw/ClassicEamsWebVpn.kt")

    /** 从常量文件源文本按 marker 提取 raw-string 常量值(字符串体本身, 不含引号壳)。 */
    private fun constValue(name: String): String {
        val marker = "val $name = \"\"\""
        return extractConstBody(constantsSource(), marker)
    }

    /**
     * 断言 Kotlin pattern ≡ JS regex literal: JS 体内必须原样出现
     * `/<Kotlin pattern, /→\/>/`。容差即 §4 invariant 段声明的唯一差异
     * (JS 字面量壳 + 体内必需的 `\/` 转义), 其余逐字符相等。
     */
    private fun assertPatternIdentical(kotlinName: String, js: String) {
        val kotlin = constValue(kotlinName)
        val asJsLiteral = "/" + kotlin.replace("/", "\\/") + "/"
        assertTrue(
            "跨语言 invariant: $kotlinName 与 JS regex literal 必须逐字符相等(容差仅 \\/), 期望 JS 含 $asJsLiteral",
            js.contains(asJsLiteral)
        )
    }

    @Test
    fun `JS 与 Kotlin 五条 pattern 逐字符一致`() {
        val js = fetchJs()
        for (name in listOf(
            "CLASSIC_EAMS_IDS_RE",
            "CLASSIC_EAMS_IDS_INPUT_RE",
            "CLASSIC_EAMS_SEMESTER_CAL_RE",
            "CLASSIC_EAMS_SEMESTER_INPUT_RE",
            "CLASSIC_EAMS_PROJECT_RE",
        )) assertPatternIdentical(name, js)
    }

    @Test
    fun `JS 与 Kotlin 登录态判据 pattern 逐字符一致`() {
        assertPatternIdentical("CLASSIC_EAMS_LOGIN_URL_RE", fetchJs())
    }

    @Test
    fun `JS 内含 §4 字面量三件套与 endpoint 与模板前缀`() {
        val js = fetchJs()
        assertTrue("JS 必须带成功判据 needle", js.contains("\"" + constValue("CLASSIC_EAMS_TASK_ACTIVITY_NEEDLE") + "\""))
        assertTrue("JS 必须带 frame 锚点", js.contains("courseTableForStd"))
        assertTrue("JS 必须 POST §4 endpoint", js.contains(constValue("CLASSIC_EAMS_ACTION_PATH")))
        // 反串表单纪律在 JS 代码面: 小写双锚定位作用域 (frame + 表单名)
        assertTrue("JS 必须含小写 frame 锚 coursetableforstd", js.contains("coursetableforstd"))
        assertTrue("JS 必须含小写表单锚 coursetableform", js.contains("coursetableform"))
        // 候选体 = PARAM_TEMPLATE 去 %s 的固定前缀逐字符在 JS 出现(两形态)
        val full = constValue("CLASSIC_EAMS_PARAM_TEMPLATE")
        val noProject = full.replace("project.id=%s&", "")
        assertTrue("JS 拼接体缺全模板前缀", js.contains(full.substringBefore("project.id")))
        assertTrue("JS 拼接体缺去-project 模板前缀", js.contains(noProject.substringBefore("semester.id")))
        assertTrue("JS 必须用 §4 project 缺省值", js.contains("|| '1'"))
    }

    @Test
    fun `JS 形态B 零凭据接触与回调通道复用`() {
        val js = fetchJs()
        assertTrue("JS 不得出现 password 读取 (§2-G6)", !js.contains("password") && !js.contains("passwd"))
        assertTrue("JS 必须走 onWiseduResult 复用通道", js.contains("window.__sleepyBridge.onWiseduResult"))
        // 反串表单纪律: JS 必须从锚点切作用域再提参, 禁整篇文档直取第一个 addInput
        assertTrue("JS 必须作用域切段 (substring(bind))", js.contains(".substring(bind)"))
        // 门户停留 hint (批B Ruling 修订: §3.3 两条件块 + hint 在 JS 内部)
        assertTrue("JS 必须有门户动线 hint 文案", js.contains("请先在门户进入"))
    }

    @Test
    fun `常量文件与 JS 同存 CaptureBar 分支与桌面UA初值落点`() {
        val screen = loadSourceFile("com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt")
        assertTrue("CaptureBar 必须落两条件分支 (批B Ruling 修订)",
            screen.contains("if (school.type == JwProtocol.TYPE_CLASSIC_EAMS && school.enableFetch) {"))
        assertTrue("desktopUa 初值必须接 initialDesktopUa (#236 照抄后作为三态裁决的校表默认输入)",
            screen.contains("val schoolDefaultDesktop = initialDesktopUa(school)"))
    }
}
