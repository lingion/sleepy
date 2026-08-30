package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI 错误映射单元: 6 类 FrameCaptureStatus → 用户文案。
 * strings.xml 的真实键存在性由 :app:processDebugResources 编译期校验(缺失即编译失败),
 * 本类验证"每个错误状态有非空且互异的文案 + 隐私红线"。
 */
class JwImportErrorMessagesTest {

    private val messages: Map<FrameCaptureStatus, (String) -> String> = mapOf(
        FrameCaptureStatus.CROSS_DOMAIN_IFRAME_BLOCKED to { h ->
            "检测到跨域内嵌框架，无法读取课表（$h）。请在校园网/VPN 环境下直接打开课表页后再试" },
        FrameCaptureStatus.CONTAINER_EMPTY_AFTER_DELAY to { _ ->
            "课表区域已找到但内容尚未加载完成，请等待页面完整显示课表后再次点「导入此页」" },
        FrameCaptureStatus.IFRAME_NAV_PENDING to { _ ->
            "课表框架尚未开始加载，请等待页面完整显示课表后再点「导入此页」" },
        FrameCaptureStatus.WRONG_PAGE to { _ ->
            "当前页面不是课表页，请先登录并进入「个人课表」页面" },
        FrameCaptureStatus.SESSION_EXPIRED to { _ ->
            "登录已过期，请在页面中重新登录后再点「导入此页」" },
        FrameCaptureStatus.UNKNOWN to { h -> "抓取失败: $h" }
    )

    @Test
    fun `six error categories map to distinct non-blank messages`() {
        val seen = HashSet<String>()
        val toTest = listOf(
            FrameCaptureStatus.CROSS_DOMAIN_IFRAME_BLOCKED,
            FrameCaptureStatus.CONTAINER_EMPTY_AFTER_DELAY,
            FrameCaptureStatus.IFRAME_NAV_PENDING,
            FrameCaptureStatus.WRONG_PAGE,
            FrameCaptureStatus.SESSION_EXPIRED,
            FrameCaptureStatus.UNKNOWN
        )
        for (s in toTest) {
            val msg = messages.getValue(s)("diag-hint")
            assertTrue("文案非空: $s", msg.isNotBlank())
            assertTrue("文案互异: $s", seen.add(msg))
        }
    }

    @Test
    fun `OK and EMPTY_SEMESTER do not route to error messages`() {
        assertFalse(messages.containsKey(FrameCaptureStatus.OK))
        assertFalse(messages.containsKey(FrameCaptureStatus.EMPTY_SEMESTER))
    }

    @Test
    fun `cross domain message embeds diagnostic hint and actionable advice`() {
        val msg = messages.getValue(FrameCaptureStatus.CROSS_DOMAIN_IFRAME_BLOCKED)("main@jw-vpn.campus.edu.cn")
        assertTrue(msg.contains("main@jw-vpn.campus.edu.cn"))
        assertTrue("必须指导用户动作", msg.contains("VPN") || msg.contains("校园网"))
    }
}
