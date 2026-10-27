package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WakeUp 6.6.2 UA 栈在 JwWebViewLoginScreen 的接线契约 (issue #236 全栈照抄)。
 *
 * 纯函数面 (双态串/尾保留/三态裁决) 由 ClassicEamsWebVpnTest ⑥ 锁语义; 本文件锁
 * Composable 侧接线本身 — 照抄不接线 = 没抄。源码扫描模式 (SepXrwStripInterceptorContractTest
 * 同型 loadSource, 兼容项目根/app/ 双测试工作目录):
 *   1) 桌面分支走 desktopUserAgent(现值) — WakeUp createWebView 尾保留组合;
 *   2) Chrome/121 旧串与 DESKTOP_USER_AGENT 常量全仓清零 — 照抄排他性闸;
 *   3) 初值 = resolveDesktopUaInitial(三态存值, 校表白名单) — 用户显式选择跨会话优先;
 *   4) toggle 落盘 setWebviewDesktopUa — 不写盘则「跨会话保持」是空话;
 *   5) 默认移动态不伪装 (WakeUp createWebView if-eqz 语义: 桌面 OFF 且从未切换 = 不碰
 *      UA, 保持内核默认), 用户显式手机态 → WAKEUP_MOBILE_SPOOF_UA (toggle 路径伪装)。
 */
class WakeupUaStackWiringContractTest {

    private fun loadSource(filename: String): String {
        val rel = "app/src/main/java/com/lingion/sleepy/ui/screen/imports/$filename"
        val userDir = System.getProperty("user.dir") ?: ""
        val fromAppDir = if (userDir.endsWith("/app"))
            "$userDir/src/main/java/com/lingion/sleepy/ui/screen/imports/$filename"
        else "$userDir/$rel"
        return sequenceOf(java.io.File(rel), java.io.File(fromAppDir))
            .firstOrNull { it.isFile }?.readText()
            ?: error("Unable to load $filename source. Tried: $rel, $fromAppDir")
    }

    private val screen by lazy { loadSource("JwWebViewLoginScreen.kt") }

    @Test
    fun `桌面分支走 WakeUp 组合函数 尾保留语义接线`() {
        // apply 块内右侧先求值: 读到的 userAgentString = 内核默认 (含标记才保留尾)。
        assertTrue(
            "桌面 UA 必须经 ClassicEamsWebVpn.desktopUserAgent 组合 (尾保留照抄)",
            screen.contains(
                "userAgentString = ClassicEamsWebVpn.desktopUserAgent(userAgentString)"
            )
        )
    }

    @Test
    fun `chrome121 旧桌面 UA 全文件清零`() {
        assertFalse(
            "Chrome/121 旧串必须退役 (用户令照抄 WakeUp Chrome/99 栈)", screen.contains("Chrome/121")
        )
        assertFalse("DESKTOP_USER_AGENT 常量必须随旧串删除", screen.contains("DESKTOP_USER_AGENT"))
    }

    @Test
    fun `初值走三态裁决 用户存值覆盖白名单`() {
        assertTrue(
            "桌面态三态源 = AppPrefs.getWebviewDesktopUa (null=从未切换)",
            screen.contains("mutableStateOf(AppPrefs.getWebviewDesktopUa(ctx))")
        )
        assertTrue(
            "有效态必须经 resolveDesktopUaInitial(存值, 校表白名单) 裁决",
            screen.contains(
                "ClassicEamsWebVpn.resolveDesktopUaInitial(desktopChoice, schoolDefaultDesktop)"
            )
        )
    }

    @Test
    fun `toggle 点击必须落盘持久化`() {
        assertTrue(
            "UA toggle 必须写 AppPrefs.setWebviewDesktopUa (WakeUp putBoolean+apply 同型)",
            screen.contains("AppPrefs.setWebviewDesktopUa(ctx, newChoice)")
        )
    }

    @Test
    fun `默认移动态不伪装 显式手机态走华为伪装串`() {
        // WakeUp 语义闸: 伪装只发生在桌面态与用户显式手机态; 从未切换 (null) 的 WebView
        // 保持内核默认 UA — createWebView `if-eqz isDesktopMode` 跳过全部 UA 代码同构。
        // 三态必须一路穿透到工厂参数 (Boolean? 而非 Boolean), 否则 null 态被压成 false。
        assertTrue(
            "工厂签名必须收三态 desktopChoice: Boolean?",
            screen.contains("desktopChoice: Boolean?")
        )
        val branch = screen.substringAfter("else if (desktopChoice == false) {").take(300)
        assertTrue(
            "显式手机态必须落 WAKEUP_MOBILE_SPOOF_UA (toggle 路径伪装照抄)。分支体:\n$branch",
            branch.contains("userAgentString = ClassicEamsWebVpn.WAKEUP_MOBILE_SPOOF_UA")
        )
        assertFalse(
            "null 默认态不得出现伪装赋值分支",
            screen.contains("desktopChoice == null")
        )
    }
}
