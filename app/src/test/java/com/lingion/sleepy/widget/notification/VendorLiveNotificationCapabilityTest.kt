package com.lingion.sleepy.widget.notification

import com.lingion.sleepy.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorLiveNotificationCapabilityTest {
    @Test
    fun `unknown is not enabled`() {
        assertNotEquals(VendorCapabilityState.ENABLED, VendorCapabilityState.UNKNOWN)
    }

    @Test
    fun `capability preserves vendor and fallback contract`() {
        val result = VendorLiveNotificationCapability(
            vendor = LiveCardVendor.OPPO,
            state = VendorCapabilityState.UNKNOWN,
            summaryRes = R.string.reminder_fluid_note,
            settingsIntents = listOf(IntentSpec("android.settings.APP_NOTIFICATION_SETTINGS")),
            fallbackToAppNotificationSettings = true
        )
        assertTrue(result.fallbackToAppNotificationSettings)
        assertEquals("android.settings.APP_NOTIFICATION_SETTINGS", result.settingsIntents.single().action)
    }
}
