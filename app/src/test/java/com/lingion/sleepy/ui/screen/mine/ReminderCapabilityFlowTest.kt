package com.lingion.sleepy.ui.screen.mine

import com.lingion.sleepy.widget.notification.VendorCapabilityState
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderCapabilityFlowTest {
    @Test
    fun `notification permission is checked before vendor settings`() {
        assertEquals(
            listOf("notification", "vendor"),
            capabilityInspectionOrder(notificationPermissionGranted = false)
        )
    }

    @Test
    fun `vendor settings failure falls through to application notification settings`() {
        assertEquals(
            "android.settings.APP_NOTIFICATION_SETTINGS",
            lastResolvableActionFor(emptySet())
        )
    }

    @Test
    fun `unknown vendor state keeps standard notification allowed`() {
        assertEquals(true, standardNotificationAllowed(VendorCapabilityState.UNKNOWN))
    }
}
