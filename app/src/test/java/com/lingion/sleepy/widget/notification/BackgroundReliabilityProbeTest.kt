package com.lingion.sleepy.widget.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundReliabilityProbeTest {

    @Test
    fun `probe facts are represented in the reliability snapshot`() {
        val probe = FakeProbe(
            notificationPermissionGranted = true,
            exactAlarmAllowed = false,
            batteryOptimizationIgnored = true,
            widgetBound = false,
        )
        val snapshot = probe.snapshotForTest()

        assertEquals(true, snapshot.notificationPermissionGranted)
        assertEquals(false, snapshot.exactAlarmAllowed)
        assertEquals(true, snapshot.batteryOptimizationIgnored)
        assertEquals(false, snapshot.widgetBound)
        assertEquals(LiveCardVendor.GENERIC, snapshot.vendor)
    }

    @Test
    fun `snapshot is derived only from probed facts`() {
        val probe = FakeProbe(
            notificationPermissionGranted = true,
            exactAlarmAllowed = true,
            batteryOptimizationIgnored = false,
            widgetBound = true,
        )
        val snapshot = probe.snapshotForTest()

        assertEquals(ReminderTransportState.READY, snapshot.reminderTransportState())
        assertEquals(false, snapshot.backgroundPermissionFactsSatisfied())
        assertEquals(false, snapshot.vendorSurfaceGuaranteed())
    }

    private class FakeProbe(
        private val notificationPermissionGranted: Boolean,
        private val exactAlarmAllowed: Boolean,
        private val batteryOptimizationIgnored: Boolean,
        private val widgetBound: Boolean,
    ) {
        fun snapshotForTest(): BackgroundReliabilitySnapshot =
            BackgroundReliabilitySnapshot(
                vendor = LiveCardVendor.GENERIC,
                notificationPermissionGranted = notificationPermissionGranted,
                exactAlarmAllowed = exactAlarmAllowed,
                batteryOptimizationIgnored = batteryOptimizationIgnored,
                widgetBound = widgetBound,
                liveCardState = VendorCapabilityState.UNKNOWN,
                promotedOngoingAllowed = null,
            )
    }
}
