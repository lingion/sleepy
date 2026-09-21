package com.lingion.sleepy.widget.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorLiveNotificationAdaptersTest {
    @Test
    fun `manufacturer aliases map to vendor families`() {
        assertEquals(LiveCardVendor.OPPO, detectLiveCardVendor("OPPO"))
        assertEquals(LiveCardVendor.ONEPLUS, detectLiveCardVendor("OnePlus"))
        assertEquals(LiveCardVendor.REALME, detectLiveCardVendor("realme"))
        assertEquals(LiveCardVendor.XIAOMI, detectLiveCardVendor("Redmi"))
        assertEquals(LiveCardVendor.VIVO, detectLiveCardVendor("vivo"))
        assertEquals(LiveCardVendor.IQOO, detectLiveCardVendor("iQOO"))
        assertEquals(LiveCardVendor.MEIZU, detectLiveCardVendor("Meizu"))
        assertEquals(LiveCardVendor.HUAWEI, detectLiveCardVendor("HUAWEI"))
        assertEquals(LiveCardVendor.HONOR, detectLiveCardVendor("HONOR"))
        assertEquals(LiveCardVendor.GENERIC, detectLiveCardVendor("Acme"))
    }

    @Test
    fun `every vendor family has an adapter`() {
        LiveCardVendor.entries.forEach { vendor ->
            assertEquals(vendor, vendorAdapterFor(vendor).vendor)
        }
    }

    @Test
    fun `vendor settings specs always end with generic notification fallbacks`() {
        LiveCardVendor.entries.forEach { vendor ->
            val specs = vendorSettingsSpecs(vendor)
            assertTrue(specs.any { it.action == ACTION_APP_NOTIFICATION_SETTINGS })
            assertTrue(specs.any { it.action == ACTION_CHANNEL_NOTIFICATION_SETTINGS })
        }
    }
}
