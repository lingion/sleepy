package com.lingion.sleepy.widget.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
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
 *
 * Sources (corpus-grounded):
 *  - Xiaomi: ~/third-party-live-cards/xiaomi/HyperIsland-ToolKit (Apache-2.0)
 *    and docs/live-cards/xiaomi/REUSE.md + focus-notification.md (official docs)
 *  - vivo:  ~/third-party-live-cards/vivo/originos-toolkit (MIT)
 *    and docs/live-cards/vivo/REUSE.md + origin-isle-readonly PROTOCOL.md
 *  - Meizu:  ~/third-party-live-cards/meizu/Pinme (Apache-2.0)
 *    and docs/live-cards/meizu/REUSE.md
 *  - Samsung: ~/third-party-live-cards/samsung/CompressorEdge (MIT)
 *    and docs/live-cards/samsung/REUSE.md
 */
object VendorLiveCardRenderer {

    /**
     * Injects a support-probe delegate so tests can assert behavior without
     * a real Context / device. Defaults to [VendorLiveCardSupport].
     */
    interface SupportProbes {
        fun xiaomiFocusGranted(ctx: Context): Boolean
        fun xiaomiIslandFeatureFlag(): Boolean
        fun flymeLiveEnabled(ctx: Context): Boolean
        fun flymeVersion(): Int
        fun samsungNowBarFeature(ctx: Context): Boolean
        fun vivoRegisterSceneList(ctx: Context, scenes: List<String>): Boolean
    }

    private object RealSupport : SupportProbes {
        override fun xiaomiFocusGranted(ctx: Context) = VendorLiveCardSupport.xiaomiFocusGranted(ctx)
        override fun xiaomiIslandFeatureFlag() = VendorLiveCardSupport.xiaomiIslandFeatureFlag()
        override fun flymeLiveEnabled(ctx: Context) = VendorLiveCardSupport.flymeLiveEnabled(ctx)
        override fun flymeVersion() = VendorLiveCardSupport.flymeVersion()
        override fun samsungNowBarFeature(ctx: Context) = VendorLiveCardSupport.samsungNowBarFeature(ctx)
        override fun vivoRegisterSceneList(ctx: Context, scenes: List<String>) =
            VendorLiveCardSupport.vivoRegisterSceneList(ctx, scenes)
    }

    fun build(
        context: Context,
        state: CourseLiveCardState,
        contentIntent: PendingIntent,
        channelId: String,
        vendor: LiveCardVendor = detectLiveCardVendor(),
        support: SupportProbes = RealSupport,
    ): Notification {
        val primary = AppPrefs.getBeforeClassFluidPrimary(context)
        val primaryText = when (primary) {
            "name" -> state.courseName
            "time" -> state.startTime
            else -> state.room
        }.ifBlank { state.courseName }

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
            .setProgress(100, state.progress, false)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(contentIntent)
            .setShortCriticalText(primaryText.take(7))

        when (vendor) {
            LiveCardVendor.XIAOMI -> addXiaomiExtras(builder, state, context, support)
            LiveCardVendor.VIVO, LiveCardVendor.IQOO -> addVivoExtras(builder, state, contentIntent, context, support, themePrimaryArgb)
            LiveCardVendor.MEIZU -> addMeizuExtras(builder, state, contentIntent, context, themePrimaryArgb, support)
            LiveCardVendor.SAMSUNG -> addSamsungExtras(builder, state, contentIntent, context, support)
            // OPPO, ONEPLUS, REALME, HUAWEI, HONOR, GENERIC: plain ongoing notification
            // Huawei HarmonyOS Live View is ArkTS-only — cannot implement in Android Kotlin.
            // Honor Suggestions Kit is whitelist-only — falls back to standard notification.
            else -> Unit
        }
        return builder.build()
    }

    // ---------------------------------------------------------------------------
    // Xiaomi — HyperOS / MIUI Focus Notification (超级岛)
    // Corpus: HyperIsland-ToolKit (Apache-2.0) + official docs docs/live-cards/xiaomi/focus-notification.md
    // ---------------------------------------------------------------------------
    private fun addXiaomiExtras(
        builder: NotificationCompat.Builder,
        state: CourseLiveCardState,
        context: Context,
        support: SupportProbes,
    ) {
        val hasFocusPermission = support.xiaomiFocusGranted(context)
        val hasIslandFlag = support.xiaomiIslandFeatureFlag()
        // HyperOS-ToolKit requires BOTH gates: package permission and island feature.
        // A single positive signal is insufficient; otherwise a plain MIUI device
        // receives private extras that SystemUI silently ignores.
        if (!hasFocusPermission || !hasIslandFlag) return

        val params = JSONObject().apply {
            put("protocol", 1)
            put("business", "schedule")
            // islandFirstFloat: show pill on first post; enableFloat=false prevents
            // continuous expand/collapse churn (corpus: HyperIsland-ToolKit §Builder)
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
                        // Key path confirmed against official developer docs (focus-notification.md §四):
                        // param_v2.param_island.bigIslandArea.imageTextInfoLeft.
                        // miui.focus.paramtextInfo (prefix required by the protocol).
                        put("miui.focus.paramtextInfo", JSONObject().apply {
                            put("frontTitle", state.startTime)
                            put("title", state.courseName)
                            put("content", state.room)
                            put("useHighLight", false)
                        })
                    })
                    // Official focus-notification.md §五 ships a top-level picInfo *sibling*
                    // to imageTextInfoLeft — without it MIUI falls back to the system default
                    // island icon instead of the configured pic_start.
                    put("picInfo", JSONObject().apply {
                        put("type", 1)
                        put("pic", "miui.focus.pic_start")
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
            putParcelable("miui.focus.pic_start",
                Icon.createWithResource(context, R.drawable.ic_notification_time))
            putParcelable("miui.focus.pic_end",
                Icon.createWithResource(context, R.drawable.ic_notifications))
        }
        builder.setExtras(Bundle().apply {
            putString("miui.focus.param", JSONObject().apply {
                put("param_v2", params)
            }.toString())
            putBundle("miui.focus.pics", pics)
        })
    }

    // ---------------------------------------------------------------------------
    // vivo / iQOO — OriginOS Atomic Notification (原子通知 / OriginIsland)
    // Corpus: originos-toolkit (MIT) + origin-isle-readonly PROTOCOL.md (read-only reference)
    // Key constraints (PROTOCOL.md §4):
    //   - channel must be IMPORTANCE_HIGH (true in CourseNotificationScheduler)
    //   - contentTitle/contentText must be non-empty (true: courseName + detailText)
    //   - operation=0 on create, operation=2 on end
    //   - displays bitmask 65809 required for actual pill surface
    //   - clickResp (PendingIntent) required
    //   - ShortInfos (describeShort/coreInfoShort) are for card expansion parity
    //   - Scene NAVIGATION is the only one confirmed working on vivo allow-list
    // ---------------------------------------------------------------------------
    private fun addVivoExtras(
        builder: NotificationCompat.Builder,
        state: CourseLiveCardState,
        contentIntent: PendingIntent,
        context: Context,
        support: SupportProbes,
        themeColor: Int,
    ) {
        // Attempt scene registration (best-effort; vivo may not grant our package).
        // NAVIGATION is the only confirmed scene on vivo's allow-list (corpus:
        // origin-isle PROTOCOL.md §1). The return value tells us whether vivo
        // actually accepted the registration; if not, the island bundle below
        // becomes noise so we drop it. Without this gate we post extras that
        // the system silently ignores — see VendorLiveCardSupport
        // vivoRegisterSceneList.
        val registered = support.vivoRegisterSceneList(
            context, listOf("NAVIGATION", "METTING", "TIMER", "TRAIN")
        )
        if (!registered) return

        // ShortInfos: flat keys inside the sub-bundle (origin-isle constants verified).
        val shortInfos = Bundle().apply {
            putString("notification.superx.shortInfos.describeShort", state.courseName)
            putString("notification.superx.shortInfos.coreInfoShort", state.room)
            putParcelable("notification.superx.shortInfos.image",
                Icon.createWithResource(context, R.drawable.ic_notification_time))
            putParcelable("notification.superx.shortInfos.imageClickResp", contentIntent)
        }

        // Infos (expanded card body, template 2 = progress).
        val infos = Bundle().apply {
            putInt("notification.superx.infos.progress", state.progress)
            putParcelableArrayList("notification.superx.infos.nodeIcon", arrayListOf(
                Icon.createWithResource(context, R.drawable.ic_notification_time),
                Icon.createWithResource(context, R.drawable.ic_notifications),
            ))
        }

        val extras = Bundle().apply {
            putInt(
                "notification.superx.operation",
                when {
                    !state.isActive -> 2   // graceful end
                    state.updateSequence <= 1 -> 0  // create
                    else -> 1               // update
                }
            )
            putBoolean("notification.superx.showNotify", true)
            putInt("notification.superx.template", 2)   // progress card
            // NAVIGATION is the only confirmed working scene on vivo's allow-list
            // (origin-isle PROTOCOL.md §1). METTING/TIMER/TRAIN are also requested
            // in registration; whether vivo grants them is unknown — the extras still
            // post as a plain notification when all scene requests are denied.
            putString("notification.superx.scene", "NAVIGATION")
            putInt("notification.superx.changedRecord", state.updateSequence)
            putInt(
                "notification.superx.displays",
                65809   // notification(1) | lockscreen(16) | statusbar(256) | AOD(65536)
            )
            putBoolean("notification.superx.islandNotify", true)
            putParcelable("notification.superx.clickResp", contentIntent)
            putBundle("notification.superx.baseInfos", Bundle().apply {
                putCharSequence("notification.superx.baseInfos.title", state.courseName)
                putCharSequence("notification.superx.baseInfos.content", state.detailText)
                putInt("notification.superx.baseInfos.progressState", if (state.isActive) 0 else 1)
            })
            putBundle("notification.superx.infos", infos)
            putBundle("notification.superx.shortInfos", shortInfos)
            // OriginOS reads the top-level notification.superx.island bundle for the
            // atomic-island surface. We deliberately do NOT also write a
            // notification.superx.capsule bundle: per corpus (OriginIslandTemplates.kt)
            // capsule is a different rendering template than island, and posting both
            // forces the system to pick a winner. Pick island (the live-card feature).
            putBundle("notification.superx.island", Bundle().apply {
                putInt("island.superx.leftTemplate", 1)
                putInt("island.superx.rightTemplate", 2)
                putBoolean("island.superx.forceShow", true)
                putBoolean("island.superx.showBarWhenCard", false)
                putInt("island.superx.click", 0)
                putParcelable("island.superx.clickResp", contentIntent)
                putBundle("island.superx.leftInfo", Bundle().apply {
                    putParcelable("island.superx.leftInfo.icon",
                        Icon.createWithResource(context, R.drawable.ic_notification_time))
                    // leftInfo.content is rendered in the small left template — keep it short
                    putString("island.superx.leftInfo.content", state.startTime)
                })
                putBundle("island.superx.rightInfo", Bundle().apply {
                    putInt("island.superx.rightInfo.progressValue", state.progress)
                    putInt("island.superx.rightInfo.progressState", if (state.isActive) 0 else 1)
                    // progressColor: corpus passes accentColor → progressColor on every
                    // PROGRESS template. Without it the framework defaults the bar color
                    // and visually breaks themed brands.
                    putInt("island.superx.rightInfo.progressColor", themeColor)
                    // progressContent is the right-side label; keep it compact (room only).
                    putString("island.superx.rightInfo.progressContent", state.room)
                    putParcelable("island.superx.rightInfo.clickResp", contentIntent)
                })
            })
        }
        builder.setExtras(extras)
    }

    // ---------------------------------------------------------------------------
    // Meizu / Flyme — Flyme Live Notification
    // Corpus: Pinme (Apache-2.0) + lightxin (MIT) + starSchedule (Apache-2.0)
    // Conflicts documented in meizu/REUSE.md:
    //   type: 2 (early/demos) vs 10 (later implementations)
    //   capsuleType: 1, 3, 5 — semantics unknown; we use 5 (minimum-demo consensus)
    //   Capsule icon+colors: Pinme, lightxin, starSchedule all agree
    // ---------------------------------------------------------------------------
    private fun addMeizuExtras(
        builder: NotificationCompat.Builder,
        state: CourseLiveCardState,
        contentIntent: PendingIntent,
        context: Context,
        themeColor: Int,
        support: SupportProbes,
    ) {
        val flymeVer = support.flymeVersion()
        val liveEnabled = support.flymeLiveEnabled(context)
        // Require Flyme 11+ and the live notification switch to be on.
        if (flymeVer < 11 || !liveEnabled) return

        val capsule = Bundle().apply {
            putInt("notification.live.capsuleStatus", 1)
            putInt("notification.live.capsuleType", 5)  // community consensus; semantics not public
            putString("notification.live.capsuleContent", "${state.courseName} ${state.progress}%")
            putParcelable("notification.live.capsuleIcon",
                Icon.createWithResource(context, R.drawable.ic_notification_time))
            // Flyme expects an opaque capsule background; alpha=0 would make the
            // luminance-based foreground choice unreadable on the system surface.
            val opaqueThemeColor = themeColor or 0xFF000000.toInt()
            putInt("notification.live.capsuleBgColor", opaqueThemeColor)
            putInt(
                "notification.live.capsuleContentColor",
                if (Color.luminance(opaqueThemeColor) > 0.7f) Color.BLACK else Color.WHITE
            )
        }
        builder.setExtras(Bundle().apply {
            putBoolean("is_live", true)
            putInt("notification.live.operation",
                if (state.updateSequence <= 1) 0 else 1)
            putInt("notification.live.type", 2)   // community majority; corpus: Pinme, ChillEast, guanji
            putBundle("notification.live.capsule", capsule)
        })
    }

    // ---------------------------------------------------------------------------
    // Samsung — One UI Live Updates
    // Corpus: CompressorEdge (MIT, Android 16) + NowBrief (no license, extras ref)
    // + yetanotherbusapp (AGPL-3.0, extras reference)
    // Two-layer approach:
    //   Layer 1 (guaranteed): Android 16 promoted ongoing + ProgressStyle — standard API
    //   Layer 2 (best-effort): Samsung-specific android.ongoingActivityNoti.* extras
    //     These require One UI 8 (Android 16) on the device; the feature flag
    //     detection is the only gate. CompressorEdge §samsungNowBarExtras is MIT.
    // ---------------------------------------------------------------------------
    private fun addSamsungExtras(
        builder: NotificationCompat.Builder,
        state: CourseLiveCardState,
        contentIntent: PendingIntent,
        context: Context,
        support: SupportProbes,
    ) {
        // Only attempt Samsung extras when the device declares the feature.
        if (!support.samsungNowBarFeature(context)) return

        val samsungExtras = Bundle().apply {
            putInt("android.ongoingActivityNoti.style", 1)          // progress style
            putString("android.ongoingActivityNoti.primaryInfo", state.courseName)
            putString("android.ongoingActivityNoti.secondaryInfo", state.detailText)
            putString("android.ongoingActivityNoti.nowbarPrimaryInfo", state.courseName)
            putString("android.ongoingActivityNoti.nowbarSecondaryInfo", state.detailText)
        }
        // Samsung is the only vendor branch that writes extras here, so the builder
        // has no prior extras — a plain set is the whole story, no merge needed.
        builder.setExtras(samsungExtras)
    }
}
