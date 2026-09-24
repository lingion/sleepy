package com.lingion.sleepy.widget.notification

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.lingion.sleepy.R

const val ACTION_APP_NOTIFICATION_SETTINGS = Settings.ACTION_APP_NOTIFICATION_SETTINGS
const val ACTION_CHANNEL_NOTIFICATION_SETTINGS = Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS

/** Stable order for all vendor families: vendor candidate, channel, application. */
fun vendorSettingsSpecs(vendor: LiveCardVendor): List<IntentSpec> = buildList {
    vendorSettingsAction(vendor)?.let { add(IntentSpec(it)) }
    add(IntentSpec(ACTION_CHANNEL_NOTIFICATION_SETTINGS))
    add(IntentSpec(ACTION_APP_NOTIFICATION_SETTINGS))
}

private fun vendorSettingsAction(vendor: LiveCardVendor): String? = when (vendor) {
    // These are candidates only. Availability is checked by resolveActivity at launch time.
    LiveCardVendor.OPPO, LiveCardVendor.ONEPLUS, LiveCardVendor.REALME ->
        "com.coloros.notificationmanager.action.NOTIFICATION_SETTINGS"
    LiveCardVendor.VIVO, LiveCardVendor.IQOO ->
        "com.vivo.notification.action.NOTIFICATION_SETTINGS"
    LiveCardVendor.XIAOMI ->
        "miui.intent.action.APP_PERM_EDITOR"
    LiveCardVendor.MEIZU ->
        "com.meizu.safe.security.SHOW_APPSEC"
    LiveCardVendor.HUAWEI ->
        "com.huawei.systemmanager.APP_CONTROL"
    LiveCardVendor.HONOR ->
        "com.hihonor.systemmanager.APP_CONTROL"
    LiveCardVendor.SAMSUNG, LiveCardVendor.GENERIC -> null
}

interface VendorNotificationAdapter : VendorLiveNotificationAdapter {
    val vendor: LiveCardVendor
}

private class DefaultVendorNotificationAdapter(
    override val vendor: LiveCardVendor
) : VendorNotificationAdapter {
    override fun inspect(context: Context): VendorLiveNotificationCapability {
        val notificationState = if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) VendorCapabilityState.NOTIFICATION_PERMISSION_REQUIRED else null
        return VendorLiveNotificationCapability(
            vendor = vendor,
            state = notificationState ?: VendorCapabilityState.UNKNOWN,
            summaryRes = when (notificationState) {
                VendorCapabilityState.NOTIFICATION_PERMISSION_REQUIRED ->
                    R.string.reminder_fluid_status_notification_required
                else -> R.string.reminder_fluid_status_unknown
            },
            settingsIntents = vendorSettingsSpecs(vendor),
            fallbackToAppNotificationSettings = true
        )
    }

    override fun settingsIntents(context: Context): List<Intent> = vendorSettingsSpecs(vendor).map { spec ->
        Intent(spec.action).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, CourseNotificationScheduler.CHANNEL_FLUID)
        }
    }
}

fun vendorAdapterFor(vendor: LiveCardVendor): VendorNotificationAdapter =
    DefaultVendorNotificationAdapter(vendor)
