package com.lingion.sleepy.widget.notification

import android.Manifest
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.lingion.sleepy.widget.TodaySmallWidgetReceiver
import com.lingion.sleepy.widget.TodayWidgetReceiver
import com.lingion.sleepy.widget.TwoDaySmallWidgetReceiver
import com.lingion.sleepy.widget.TwoDayWidgetReceiver
import com.lingion.sleepy.widget.WeekGridSmallWidgetProvider

/**
 * Reads public Android reliability facts in one place.
 * A failed private vendor probe remains UNKNOWN and never disables standard notifications.
 */
object BackgroundReliabilityProbe {

    fun snapshot(context: Context): BackgroundReliabilitySnapshot {
        val appContext = context.applicationContext
        val vendor = detectLiveCardVendor()
        val liveCard = runCatching {
            vendorAdapterFor(vendor).inspect(appContext)
        }.getOrNull()?.state ?: VendorCapabilityState.UNKNOWN

        return BackgroundReliabilitySnapshot(
            vendor = vendor,
            notificationPermissionGranted = notificationPermissionGranted(appContext),
            exactAlarmAllowed = exactAlarmAllowed(appContext),
            batteryOptimizationIgnored = batteryOptimizationIgnored(appContext),
            widgetBound = widgetBound(appContext),
            liveCardState = liveCard,
            promotedOngoingAllowed = promotedOngoingAllowed(appContext),
        )
    }

    private fun promotedOngoingAllowed(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < 36) return null
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            ?: return false
        return runCatching { manager.canPostPromotedNotifications() }.getOrDefault(false)
    }

    private fun notificationPermissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun exactAlarmAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
                ?.canScheduleExactAlarms() == true

    private fun batteryOptimizationIgnored(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return runCatching {
            power.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)
    }

    private fun widgetBound(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return WIDGET_PROVIDERS.any { provider ->
            manager.getAppWidgetIds(ComponentName(context, provider)).isNotEmpty()
        }
    }

    private val WIDGET_PROVIDERS = listOf(
        TodayWidgetReceiver::class.java,
        TodaySmallWidgetReceiver::class.java,
        TwoDayWidgetReceiver::class.java,
        TwoDaySmallWidgetReceiver::class.java,
        WeekGridSmallWidgetProvider::class.java,
    )
}
