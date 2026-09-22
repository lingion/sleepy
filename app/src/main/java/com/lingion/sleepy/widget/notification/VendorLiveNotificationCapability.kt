package com.lingion.sleepy.widget.notification

/**
 * The state that can be established for a vendor's enhanced notification surface.
 * UNKNOWN is a real terminal state: private vendor eligibility must never be guessed.
 */
enum class VendorCapabilityState {
    ENABLED,
    DISABLED,
    NOTIFICATION_PERMISSION_REQUIRED,
    SETTINGS_REQUIRED,
    NOT_SUPPORTED,
    UNKNOWN
}

/** A settings candidate kept as data so ordering and fallback can be tested. */
data class IntentSpec(
    val action: String,
    val extras: Map<String, String> = emptyMap()
)

data class VendorLiveNotificationCapability(
    val vendor: LiveCardVendor,
    val state: VendorCapabilityState,
    val summaryRes: Int,
    val settingsIntents: List<IntentSpec>,
    val fallbackToAppNotificationSettings: Boolean
)
