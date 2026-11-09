package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 权限与后台运行卡片排版契约 (ref #48 群内报障 · #320 重排回归):
 * 状态行必须紧凑排列 — 行内不得再挂固定 vertical padding,
 * 否则相邻行之间会顶出「假空行」; 小组件说明行必须紧跟小组件状态行。
 */
class ReminderReliabilityCardContractTest {
    private val source: String by lazy {
        sequenceOf(
            System.getProperty("sleepy.test.root")?.let { java.io.File(it, "app/src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt") },
            java.io.File("src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt"),
            java.io.File("app/src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt")
        ).filterNotNull().firstOrNull { it.isFile }?.readText()
            ?: error("Unable to load ReminderScreen.kt source")
    }

    private val cardBody: String by lazy {
        source.substringAfter("private fun ReminderReliabilityCard(")
            .let { body -> body.substringBefore("\n@Composable\nprivate fun", body) }
    }

    @Test
    fun `status rows stay tight - no fixed vertical padding reintroduced`() {
        assertFalse(
            "reliability status rows must not carry fixed vertical padding (phantom blank lines, #48)",
            cardBody.contains("padding(vertical")
        )
    }

    @Test
    fun `clickable status rows keep full-width tap targets`() {
        // 分支裁定(2026-11-09 用户令): 这三行是可点击项, 不是按钮 — 走 noRippleClickable,
        // 点击区仍是全宽 Text 本身, 禁回归为局部小按钮。
        assertTrue(cardBody.contains("Modifier.fillMaxWidth()"))
        assertTrue(cardBody.contains("noRippleClickable(onNotificationPermission)"))
        assertTrue(cardBody.contains("noRippleClickable(onExactAlarmSettings)"))
        assertTrue(cardBody.contains("noRippleClickable(onBatterySettings)"))
    }

    @Test
    fun `status rows must carry no glass optical material`() {
        // 用户 2026-11-09: 诊断行不应带任何液态玻璃效果。
        assertFalse(cardBody.contains("glassControl"))
        assertFalse(cardBody.contains("noRippleGlassClickable"))
    }

    @Test
    fun `widget note renders right after widget status row`() {
        val widgetRow = cardBody.indexOf("R.string.reminder_reliability_widget,")
        val note = cardBody.indexOf("R.string.settings_reminder_widget_optional")
        assertTrue("widget status row missing", widgetRow >= 0)
        assertTrue("widget note must follow the widget status row", note > widgetRow)
    }
}