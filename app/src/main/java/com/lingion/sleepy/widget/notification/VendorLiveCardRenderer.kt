package com.lingion.sleepy.widget.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.lingion.sleepy.R
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.widget.resolveSchemePublic
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject

/** Android vendor surface selected from the runtime manufacturer string. */
enum class LiveCardVendor {
    OPPO,
    ONEPLUS,
    REALME,
    XIAOMI,
    VIVO,
    IQOO,
    MEIZU,
    HUAWEI,
    HONOR,
    SAMSUNG,
    GENERIC
}

fun detectLiveCardVendor(manufacturer: String = Build.MANUFACTURER): LiveCardVendor {
    return when (manufacturer.trim().lowercase()) {
        "oppo" -> LiveCardVendor.OPPO
        "oneplus" -> LiveCardVendor.ONEPLUS
        "realme" -> LiveCardVendor.REALME
        "xiaomi", "redmi", "poco" -> LiveCardVendor.XIAOMI
        "vivo" -> LiveCardVendor.VIVO
        "iqoo" -> LiveCardVendor.IQOO
        "meizu" -> LiveCardVendor.MEIZU
        "huawei" -> LiveCardVendor.HUAWEI
        "honor" -> LiveCardVendor.HONOR
        "samsung" -> LiveCardVendor.SAMSUNG
        else -> LiveCardVendor.GENERIC
    }
}

/**
 * Builds one notification for the shared OPPO lifecycle and adds only documented
 * vendor extras. Vendor access approval remains a device/platform concern; every
 * renderer leaves the generic Android notification usable as the fallback.
 */
object VendorLiveCardRenderer {
    fun build(
        context: Context,
        state: CourseLiveCardState,
        contentIntent: PendingIntent,
        channelId: String,
        vendor: LiveCardVendor = detectLiveCardVendor()
    ): Notification {
        val primary = AppPrefs.getBeforeClassFluidPrimary(context)
        val primaryText = when (primary) {
            "name" -> state.courseName
            "time" -> state.startTime
            else -> state.room
        }.ifBlank { state.courseName }

        val style = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(state.progress)
        val isSystemDark = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val themePrimaryArgb = resolveSchemePublic(
            context,
            AppPrefs.getThemeKey(context),
            AppPrefs.isDarkMode(context, isSystemDark)
        ).primary.toArgb()
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_time)
            .setColor(themePrimaryArgb)
            .setContentTitle(state.courseName)
            .setContentText(state.detailText)
            .setSubText(state.room)
            .setStyle(style)
            .setProgress(100, state.progress, false)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(contentIntent)
            .setShortCriticalText(primaryText.take(7))

        when (vendor) {
            LiveCardVendor.XIAOMI -> addXiaomiExtras(builder, state, contentIntent, context)
            LiveCardVendor.VIVO, LiveCardVendor.IQOO -> addVivoExtras(builder, state, contentIntent, context)
            LiveCardVendor.MEIZU -> addMeizuExtras(builder, state, contentIntent, context, themePrimaryArgb)
            LiveCardVendor.OPPO, LiveCardVendor.ONEPLUS, LiveCardVendor.REALME,
            LiveCardVendor.HUAWEI, LiveCardVendor.HONOR, LiveCardVendor.SAMSUNG,
            LiveCardVendor.GENERIC -> Unit
        }
        return builder.build()
    }

    private fun addXiaomiExtras(
        builder: NotificationCompat.Builder,
        state: CourseLiveCardState,
        contentIntent: PendingIntent,
        context: Context
    ) {
        val params = JSONObject().apply {
            put("protocol", 1)
            put("business", "schedule")
            put("islandFirstFloat", true)
            put("enableFloat", false)
            put("updatable", true)
            put("timeout", 60)
            put("sequence", state.updateSequence)
            put("ticker", state.courseName)
            put("aodTitle", "${state.startTime} ${state.courseName}")
            put("param_island", JSONObject().apply {
                put("islandProperty", 1)
                put("islandTimeout", 3600)
                put("bigIslandArea", JSONObject().apply {
                    put("imageTextInfoLeft", JSONObject().apply {
                        put("type", 1)
                        put("picInfo", JSONObject().apply {
                            put("type", 1)
                            put("pic", "miui.focus.pic_start")
                        })
                        put("miui.focus.paramtextInfo", JSONObject().apply {
                            put("frontTitle", state.startTime)
                            put("title", state.courseName)
                            put("content", state.room)
                            put("useHighLight", false)
                        })
                    })
                })
                put("smallIslandArea", JSONObject().apply {
                    put("picInfo", JSONObject().apply {
                        put("type", 1)
                        put("pic", "miui.focus.pic_end")
                    })
                })
            })
            put("baseInfo", JSONObject().apply {
                put("title", state.courseName)
                put("content", state.detailText)
                put("type", 1)
            })
        }
        val pics = Bundle().apply {
            putParcelable("miui.focus.pic_start", Icon.createWithResource(context, R.drawable.ic_notification_time))
            putParcelable("miui.focus.pic_end", Icon.createWithResource(context, R.drawable.ic_notifications))
        }
        builder.setExtras(Bundle().apply {
            putString("miui.focus.param", JSONObject().apply {
                put("param_v2", params)
            }.toString())
            putBundle("miui.focus.pics", pics)
        })
    }

    private fun addVivoExtras(
        builder: NotificationCompat.Builder,
        state: CourseLiveCardState,
        contentIntent: PendingIntent,
        context: Context
    ) {
        val extras = Bundle().apply {
            putInt(
                "notification.superx.operation",
                when {
                    !state.isActive -> 2
                    state.updateSequence <= 1 -> 0
                    else -> 1
                }
            )
            putBoolean("notification.superx.showNotify", true)
            putInt("notification.superx.template", 2)
            putString("notification.superx.scene", "METTING")
            putInt("notification.superx.changedRecord", state.updateSequence)
            putParcelable("notification.superx.clickResp", contentIntent)
            putBundle("notification.superx.baseInfos", Bundle().apply {
                putCharSequence("title", state.courseName)
                putCharSequence("content", state.detailText)
                putInt("progressState", if (state.isActive) 0 else 1)
            })
            putBundle("notification.superx.infos", Bundle().apply {
                putInt("progress", state.progress)
                putParcelableArrayList("nodeIcon", arrayListOf(
                    Icon.createWithResource(context, R.drawable.ic_notification_time),
                    Icon.createWithResource(context, R.drawable.ic_notifications)
                ))
            })
            putBundle("notification.superx.shortInfos", Bundle().apply {
                putParcelable("image", Icon.createWithResource(context, R.drawable.ic_notification_time))
                putParcelable("imageClickResp", contentIntent)
                putCharSequence("describeShort", state.courseName)
                putCharSequence("coreInfoShort", state.room)
            })
            putBundle("notification.superx.capsule", Bundle().apply {
                putInt("state", if (state.isActive) 1 else 0)
                putParcelable("icon", Icon.createWithResource(context, R.drawable.ic_notification_time))
                putString("content", "${state.courseName} ${state.progress}%")
            })
        }
        builder.setExtras(extras)
    }

    private fun addMeizuExtras(
        builder: NotificationCompat.Builder,
        state: CourseLiveCardState,
        contentIntent: PendingIntent,
        context: Context,
        themeColor: Int
    ) {
        val capsule = Bundle().apply {
            putInt("notification.live.capsuleStatus", 1)
            putInt("notification.live.capsuleType", 5)
            putString("notification.live.capsuleContent", "${state.courseName} ${state.progress}%")
            putParcelable("notification.live.capsuleIcon", Icon.createWithResource(context, R.drawable.ic_notification_time))
            putInt("notification.live.capsuleBgColor", themeColor)
            putInt("notification.live.capsuleContentColor", 0xFFFFFFFF.toInt())
        }
        builder.setExtras(Bundle().apply {
            putBoolean("is_live", true)
            putInt("notification.live.operation", if (state.updateSequence <= 1) 0 else 1)
            putInt("notification.live.type", 2)
            putBundle("notification.live.capsule", capsule)
        })
    }
}
