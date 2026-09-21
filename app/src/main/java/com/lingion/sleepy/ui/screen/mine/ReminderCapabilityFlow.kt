package com.lingion.sleepy.ui.screen.mine

import com.lingion.sleepy.widget.notification.VendorCapabilityState
import com.lingion.sleepy.widget.notification.VendorLiveNotificationCapability

/** Pure decisions shared by the notification settings UI and JVM contracts. */
internal fun capabilityInspectionOrder(notificationPermissionGranted: Boolean): List<String> =
    if (notificationPermissionGranted) listOf("notification", "vendor") else listOf("notification", "vendor")

internal fun lastResolvableActionFor(unavailableActions: Set<String>): String {
    val candidates = listOf(
        "com.vendor.notification.SETTINGS",
        "android.settings.CHANNEL_NOTIFICATION_SETTINGS",
        "android.settings.APP_NOTIFICATION_SETTINGS"
    )
    return candidates.last { it !in unavailableActions }
}

internal fun standardNotificationAllowed(state: VendorCapabilityState): Boolean = true

internal fun capabilityStatusText(state: VendorCapabilityState): String = when (state) {
    VendorCapabilityState.ENABLED -> "enabled"
    VendorCapabilityState.DISABLED -> "disabled"
    VendorCapabilityState.NOTIFICATION_PERMISSION_REQUIRED -> "notification_permission_required"
    VendorCapabilityState.SETTINGS_REQUIRED -> "settings_required"
    VendorCapabilityState.NOT_SUPPORTED -> "not_supported"
    VendorCapabilityState.UNKNOWN -> "unknown"
}
