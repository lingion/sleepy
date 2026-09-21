package com.lingion.sleepy.ui.screen.mine

import com.lingion.sleepy.widget.notification.VendorCapabilityState
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderCapabilityFlowTest {
    @Test
    fun `notification permission is checked before vendor settings`() {
        assertEquals(
            listOf("notification"),
            capabilityInspectionOrder(notificationPermissionGranted = false)
        )
    }

    @Test
    fun `vendor settings failure falls through to application notification settings`() {
        assertEquals(
            "android.settings.APP_NOTIFICATION_SETTINGS",
            lastResolvableActionFor(setOf(
                "com.vendor.notification.SETTINGS",
                "android.settings.CHANNEL_NOTIFICATION_SETTINGS"
            ))
        )
    }

    @Test
    fun `missing vendor settings has no fallback action`() {
        assertEquals(
            null,
            lastResolvableActionFor(setOf(
                "com.vendor.notification.SETTINGS",
                "android.settings.CHANNEL_NOTIFICATION_SETTINGS",
                "android.settings.APP_NOTIFICATION_SETTINGS"
            ))
        )
    }

    @Test
    fun `unknown vendor state keeps standard notification allowed`() {
        assertEquals(true, standardNotificationAllowed(VendorCapabilityState.UNKNOWN))
    }

    @Test
    fun `vendor capability states do not disable standard notification`() {
        assertEquals(true, standardNotificationAllowed(VendorCapabilityState.DISABLED))
        assertEquals(true, standardNotificationAllowed(VendorCapabilityState.SETTINGS_REQUIRED))
        assertEquals(true, standardNotificationAllowed(VendorCapabilityState.NOT_SUPPORTED))
        assertEquals(false, standardNotificationAllowed(VendorCapabilityState.NOTIFICATION_PERMISSION_REQUIRED))
    }
}
