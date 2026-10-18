package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 未验证学校「列表静默 → 进入前确认 → 成功后报告」回路契约（2026-10-18 用户要求落地）。
 *
 * 三条不变量:
 * ① SchoolRow 列表: unverified 不再渲染状态徽章 — 列表静默, 待验证不刷屏;
 * ② 点进入口: unverified 仍弹 catalogNotice 确认弹窗（点进去之前保留确认, 用户明确要求保留）;
 * ③ 导入成功: unverified 先弹报告弹窗（复制报告 + 加 QQ 群 + 完成）, 支持校维持原静默 finish。
 *
 * 剪贴板链路边界（报告不被覆盖是回路成立的前提）:
 * 「复制报告」只写剪贴板不拉起 QQ;「加入 QQ 群」只拉起不写剪贴板 —
 * AboutScreen.joinQqGroup 先复制群号再拉起, 若照搬会在用户复制报告后把报告挤出剪贴板。
 *
 * 仓库无 Robolectric/Compose UI 测试 — 声明式接线读源头文件等价于读编译产物
 * （先例: ImportDraftWiringContractTest）。
 */
class JwUnverifiedReportLoopContractTest {

    private val srcRoot = sequenceOf(
        File("app/src/main/java/com/lingion/sleepy/"),
        File("src/main/java/com/lingion/sleepy/"),
    ).first { it.isDirectory }

    private fun activitySource(): String =
        File(srcRoot, "ui/screen/imports/JwImportActivity.kt").readText()

    private fun selectSource(): String =
        File(srcRoot, "ui/screen/imports/SchoolSelectScreen.kt").readText()

    /** 提取 [marker] 起始的函数源码区间 — 截到下一个函数声明标记(各 fn 声明行首列后缩进固定)。 */
    private fun region(src: String, marker: String, until: String): String {
        val start = src.indexOf(marker)
        assertTrue("marker not found: $marker", start >= 0)
        val end = src.indexOf(until, start + marker.length).takeIf { it >= 0 } ?: src.length
        return src.substring(start, end)
    }

    @Test
    fun `unverified school no longer renders a status badge in the list`() {
        val badge = region(selectSource(), "private fun SchoolStatusBadge", "private fun UrlDirectRow")
        assertFalse("unverified 徽章分支仍在渲染 — 列表应静默", badge.contains("STATUS_UNVERIFIED"))
        assertFalse("grad_unverified 徽章分支仍在渲染", badge.contains("STATUS_GRAD_UNVERIFIED"))
        // 其余状态徽章必须保留: pending/研究生/旧版仍有各自 chip
        assertTrue(badge.contains("STATUS_PENDING"))
        assertTrue(badge.contains("STATUS_GRAD_SUPPORTED"))
        assertTrue(badge.contains("STATUS_LEGACY"))
    }

    @Test
    fun `unverified school keeps the pre-entry confirmation dialog`() {
        val act = activitySource()
        assertTrue("点进 unverified 学校的 catalogNotice 确认触发被删除", act.contains("catalogNotice = school"))
        assertTrue(
            "确认弹窗 mayTry 判定被改写 — unverified+可导入必须仍走确认",
            act.contains("val mayTry = school.isUnverified && school.canImport")
        )
    }

    @Test
    fun `import success for unverified routes through the report dialog`() {
        val act = activitySource()
        // 成功出口统一收口到 finishOrReport: 定义 1 + 两条应用路径(append/importAsNew) ≥2 调用
        val routings = Regex("finishOrReport\\(").findAll(act).count()
        assertTrue("finishOrReport 接线不足(期望 >=3, 实际 $routings)", routings >= 3)
        assertTrue("报告文本未经 JwImportReport 生成", act.contains("JwImportReport.build("))
        assertTrue("报告弹窗状态缺失", act.contains("reportDialogSchool"))
        // 收口仍是 completeImport → importFinished → finish() 原链路
        assertTrue("完成动作必须仍调用 completeImport 收口", act.contains("completeImport()"))
        // 已验证学校不得被拖进报告弹窗: finishOrReport 内先判 isUnverified, 否则直接 completeImport
        val finishFn = region(act, "suspend fun finishOrReport", "\n                }")
        assertTrue("finishOrReport 必须先判 isUnverified", finishFn.contains("school.isUnverified"))
        assertTrue("非 unverified 路径必须直达 completeImport", finishFn.contains("completeImport()"))
    }

    @Test
    fun `copy report and join qq keep disjoint clipboard paths`() {
        val act = activitySource()
        val copyFn = region(act, "fun copyTextToClipboard", "\n                fun ")
        assertTrue(copyFn.contains("setPrimaryClip"))
        assertFalse("复制报告不得拉起 QQ(混入 startActivity = 用户没得选)", copyFn.contains("startActivity"))
        val joinFn = region(act, "fun launchQqGroupJoin", "\n                fun ")
        assertTrue("拉起链必须复用 QqJoin URI 家族", joinFn.contains("QqJoin.groupCardUri"))
        assertTrue(joinFn.contains("startActivity"))
        assertFalse("加入 QQ 群不得改写剪贴板 — 剪贴板此刻装着待粘贴的报告", joinFn.contains("setPrimaryClip"))
    }
}
