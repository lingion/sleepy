package com.lingion.sleepy.widget.notification

/**
 * Facts observed from the current device and app state.
 * This model deliberately does not claim that a vendor surface is guaranteed.
 */
data class BackgroundReliabilitySnapshot(
    val vendor: LiveCardVendor,
    val notificationPermissionGranted: Boolean,
    val exactAlarmAllowed: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val widgetBound: Boolean,
    val liveCardState: VendorCapabilityState,
    /** Null on pre-Android 16 where promoted ongoing is not available. */
    val promotedOngoingAllowed: Boolean?,
) {
    fun reminderTransportState(): ReminderTransportState = when {
        !notificationPermissionGranted -> ReminderTransportState.BLOCKED_NOTIFICATION_PERMISSION
        !exactAlarmAllowed -> ReminderTransportState.DEGRADED_INEXACT_ALARM
        else -> ReminderTransportState.READY
    }

    fun standardNotificationAllowed(): Boolean = notificationPermissionGranted

    fun backgroundPermissionFactsSatisfied(): Boolean =
        notificationPermissionGranted && exactAlarmAllowed && batteryOptimizationIgnored

    fun vendorSurfaceGuaranteed(): Boolean = liveCardState == VendorCapabilityState.ENABLED
}

enum class ReminderTransportState {
    READY,
    DEGRADED_INEXACT_ALARM,
    BLOCKED_NOTIFICATION_PERMISSION,
}
