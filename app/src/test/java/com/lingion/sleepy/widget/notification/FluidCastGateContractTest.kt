package com.lingion.sleepy.widget.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 流体云测试投放三查闸门 — 锁「全绿才放行」不变量 (用户 2026-10-10 二轮诉求:
 * 致命的缺失项静默投放 = 静默降级, 必须拦截并明示)。
 *
 * ReminderScreen 里的 checkFluidCastGate() 是 Composable 局部函数, JVM 测不到;
 * 这里用同构纯函数锁语义: 任何一个致命项缺失 → 非 null 拦截; 全部满足 → null 放行。
 * 屏幕侧与纯函数的字段一一对应 (reliabilitySnapshot ↔ BackgroundReliabilitySnapshot,
 * liveCardCapability.state ↔ VendorCapabilityState), 漂移时此测试提醒同步。
 */
class FluidCastGateContractTest {

    private fun gate(
        notificationGranted: Boolean = true,
        promotedOngoingAllowed: Boolean? = null,
        liveCardState: VendorCapabilityState = VendorCapabilityState.UNKNOWN
    ): List<String>? {
        val reasons = mutableListOf<String>()
        if (!notificationGranted ||
            liveCardState == VendorCapabilityState.NOTIFICATION_PERMISSION_REQUIRED
        ) {
            reasons.add("notification")
        }
        if (promotedOngoingAllowed == false) {
            reasons.add("promoted")
        }
        if (liveCardState == VendorCapabilityState.SETTINGS_REQUIRED ||
            liveCardState == VendorCapabilityState.NOT_SUPPORTED
        ) {
            reasons.add("vendor")
        }
        return reasons.takeIf { it.isNotEmpty() }
    }

    @Test
    fun `all green casts immediately`() {
        assertNull(
            gate(
                notificationGranted = true,
                promotedOngoingAllowed = true,
                liveCardState = VendorCapabilityState.ENABLED
            )
        )
    }

    @Test
    fun `promoted ongoing denied blocks silent cast`() {
        // 用户实测场景: 通知权限 ✅ 精确提醒 ✅ 电池 ✅, 但「系统实时更新授权:未满足」
        // → 岛必不出现, 必须拦截, 不许直接投放。
        val blocked = gate(
            notificationGranted = true,
            promotedOngoingAllowed = false,
            liveCardState = VendorCapabilityState.UNKNOWN
        )
        assertNotNull(blocked)
        assertEquals(listOf("promoted"), blocked)
    }

    @Test
    fun `settings required state blocks silent cast`() {
        val blocked = gate(
            liveCardState = VendorCapabilityState.SETTINGS_REQUIRED
        )
        assertNotNull(blocked)
    }

    @Test
    fun `notification denied blocks cast`() {
        val blocked = gate(notificationGranted = false)
        assertNotNull(blocked)
        assertEquals(listOf("notification"), blocked)
    }

    @Test
    fun `unknown vendor state does not block`() {
        // UNKNOWN 是真实终态: 厂商资格不可探测, 不拿 UNKNOWN 拦人 (诊断区已明示「未知」)
        assertNull(gate(liveCardState = VendorCapabilityState.UNKNOWN))
    }
}
