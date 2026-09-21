package com.lingion.sleepy.widget.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class VendorLiveNotificationSettingsTest {
    @Test
    fun `settings order is vendor then channel then application`() {
        LiveCardVendor.entries.forEach { vendor ->
            val specs = vendorSettingsSpecs(vendor)
            assertEquals(ACTION_CHANNEL_NOTIFICATION_SETTINGS, specs[specs.size - 2].action)
            assertEquals(ACTION_APP_NOTIFICATION_SETTINGS, specs.last().action)
        }
    }
}
