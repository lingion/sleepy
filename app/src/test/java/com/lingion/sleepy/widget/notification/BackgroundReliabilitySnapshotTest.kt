package com.lingion.sleepy.widget.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundReliabilitySnapshotTest {

    @Test
    fun `notification denial blocks notification transport`() {
        val snapshot = snapshot(notificationPermissionGranted = false)

        assertEquals(
            ReminderTransportState.BLOCKED_NOTIFICATION_PERMISSION,
            snapshot.reminderTransportState()
        )
    }

    @Test
    fun `missing exact alarm degrades to inexact transport instead of blocking reminder`() {
        val snapshot = snapshot(exactAlarmAllowed = false)

        assertEquals(
            ReminderTransportState.DEGRADED_INEXACT_ALARM,
            snapshot.reminderTransportState()
        )
        assertTrue(snapshot.standardNotificationAllowed())
    }

    @Test
    fun `all public prerequisites report ready`() {
        val snapshot = snapshot(
            notificationPermissionGranted = true,
            exactAlarmAllowed = true,
            widgetBound = true,
            liveCardState = VendorCapabilityState.ENABLED,
        )

        assertEquals(ReminderTransportState.READY, snapshot.reminderTransportState())
        assertTrue(snapshot.backgroundPermissionFactsSatisfied())
    }

    @Test
    fun `private vendor unknown does not block standard notification`() {
        val snapshot = snapshot(liveCardState = VendorCapabilityState.UNKNOWN)

        assertTrue(snapshot.standardNotificationAllowed())
        assertFalse(snapshot.vendorSurfaceGuaranteed())
    }

    private fun snapshot(
        vendor: LiveCardVendor = LiveCardVendor.GENERIC,
        notificationPermissionGranted: Boolean = true,
        exactAlarmAllowed: Boolean = true,
        batteryOptimizationIgnored: Boolean = true,
        widgetBound: Boolean = false,
        liveCardState: VendorCapabilityState = VendorCapabilityState.UNKNOWN,
        promotedOngoingAllowed: Boolean? = null,
    ) = BackgroundReliabilitySnapshot(
        vendor = vendor,
        notificationPermissionGranted = notificationPermissionGranted,
        exactAlarmAllowed = exactAlarmAllowed,
        batteryOptimizationIgnored = batteryOptimizationIgnored,
        widgetBound = widgetBound,
        liveCardState = liveCardState,
        promotedOngoingAllowed = promotedOngoingAllowed,
    )
}
