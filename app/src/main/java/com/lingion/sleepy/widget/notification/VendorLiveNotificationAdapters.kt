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

/** API 36+ 标准实时更新 (promoted ongoing) 授权页 — ColorOS 16 流体云官方开关在此。 */
const val ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS =
    "android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"


/**
 * Stable order for all vendor families: promotion, channel, vendor, application.
 * 2026-10-10 用户实测 OPPO/ColorOS 16: 通道页不承载流体云授权 — 流体云走原生
 * Android 16 Live Updates (promoted ongoing) 通道, 官方授权页是
 * ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS, 必须排最前; 通道页退居其后。
 * promoted 页仅 API 36+ 存在, 低版本 launch 时 ActivityNotFound 逐级回退。
 */
fun vendorSettingsSpecs(vendor: LiveCardVendor): List<IntentSpec> =
    vendorSettingsSpecs(vendor, android.os.Build.VERSION.SDK_INT)

/** sdkInt 注入版 — JVM 契约测试锁顺序用 (JVM 上 Build.VERSION.SDK_INT 恒为 0)。 */
fun vendorSettingsSpecs(vendor: LiveCardVendor, sdkInt: Int): List<IntentSpec> = buildList {
    if (sdkInt >= 36 &&
        vendor in OPPO_LIVE_UPDATES_VENDOR_FAMILY
    ) {
        add(IntentSpec(ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS))
    }
    add(IntentSpec(ACTION_CHANNEL_NOTIFICATION_SETTINGS))
    vendorSettingsAction(vendor)?.let { add(IntentSpec(it)) }
    add(IntentSpec(ACTION_APP_NOTIFICATION_SETTINGS))
}

/** OPPO 家族 ColorOS 16+ 流体云 = 原生 Live Updates, 授权走标准 promoted 设置页。 */
private val OPPO_LIVE_UPDATES_VENDOR_FAMILY = setOf(
    LiveCardVendor.OPPO, LiveCardVendor.ONEPLUS, LiveCardVendor.REALME
)

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
