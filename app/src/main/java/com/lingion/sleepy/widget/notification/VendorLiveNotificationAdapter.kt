package com.lingion.sleepy.widget.notification

import android.content.Context
import android.content.Intent

/**
 * Vendor-specific inspection and settings candidates.
 * Implementations may return UNKNOWN when the ROM exposes no public signal.
 */
interface VendorLiveNotificationAdapter {
    fun inspect(context: Context): VendorLiveNotificationCapability

    fun settingsIntents(context: Context): List<Intent>
}
