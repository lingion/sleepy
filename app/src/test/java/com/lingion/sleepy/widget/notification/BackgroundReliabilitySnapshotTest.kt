package com.lingion.sleepy.widget.notification

import org.junit.Assert.assertEquals
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
    }

    @Test
    fun `battery exemption and widget binding do not block reminder transport`() {
        val snapshot = snapshot(batteryOptimizationIgnored = false, widgetBound = false)

        assertEquals(ReminderTransportState.READY, snapshot.reminderTransportState())
    }

    @Test
    fun `vendor capability never overrides notification permission for reminder transport`() {
        VendorCapabilityState.entries.forEach { state ->
            assertEquals(state.name, ReminderTransportState.READY,
                snapshot(liveCardState = state).reminderTransportState())
            assertEquals(state.name, ReminderTransportState.BLOCKED_NOTIFICATION_PERMISSION,
                snapshot(notificationPermissionGranted = false, liveCardState = state).reminderTransportState())
            assertEquals(state.name, ReminderTransportState.DEGRADED_INEXACT_ALARM,
                snapshot(exactAlarmAllowed = false, liveCardState = state).reminderTransportState())
            assertEquals(state.name, ReminderTransportState.BLOCKED_NOTIFICATION_PERMISSION,
                snapshot(notificationPermissionGranted = false, exactAlarmAllowed = false,
                    liveCardState = state).reminderTransportState())
        }
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
