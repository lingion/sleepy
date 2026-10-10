package com.lingion.sleepy.widget.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class VendorLiveNotificationSettingsTest {
    @Test
    fun `application page always last for all vendors`() {
        LiveCardVendor.entries.forEach { vendor ->
            val specs = vendorSettingsSpecs(vendor, sdkInt = 36)
            assertEquals(ACTION_APP_NOTIFICATION_SETTINGS, specs.last().action)
        }
    }

    @Test
    fun `oppo family puts promotion page first on api 36 plus`() {
        // 2026-10-10 用户实测 OPPO/ColorOS 16: 流体云授权在系统「实时更新」页
        // (ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS), 通道页不承载该授权 —
        // promoted 页必须排第一, 后面按 channel → vendor → app 回退。
        LiveCardVendor.entries
            .filter { it in setOf(LiveCardVendor.OPPO, LiveCardVendor.ONEPLUS, LiveCardVendor.REALME) }
            .forEach { vendor ->
                val specs = vendorSettingsSpecs(vendor, sdkInt = 36)
                assertEquals(4, specs.size)
                assertEquals(ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS, specs[0].action)
                assertEquals(ACTION_CHANNEL_NOTIFICATION_SETTINGS, specs[1].action)
                assertEquals("com.coloros.notificationmanager.action.NOTIFICATION_SETTINGS", specs[2].action)
                assertEquals(ACTION_APP_NOTIFICATION_SETTINGS, specs[3].action)
            }
    }

    @Test
    fun `non oppo vendors keep channel first even on api 36 plus`() {
        // 非 OPPO 家族不插 promoted 页 — 流体云授权不等于他们的厂商开关
        val specs = vendorSettingsSpecs(LiveCardVendor.XIAOMI, sdkInt = 36)
        assertEquals(ACTION_CHANNEL_NOTIFICATION_SETTINGS, specs[0].action)
    }

    @Test
    fun `oppo family below api 36 keeps channel first`() {
        // pre-36 没有 promoted 设置页, 保持原顺序
        val specs = vendorSettingsSpecs(LiveCardVendor.OPPO, sdkInt = 35)
        assertEquals(ACTION_CHANNEL_NOTIFICATION_SETTINGS, specs[0].action)
        assertEquals(3, specs.size)
    }

    @Test
    fun `default order is channel then vendor then application`() {
        LiveCardVendor.entries.forEach { vendor ->
            val specs = vendorSettingsSpecs(vendor, sdkInt = 0)
            assertEquals(ACTION_CHANNEL_NOTIFICATION_SETTINGS, specs.first().action)
        }
    }
}
