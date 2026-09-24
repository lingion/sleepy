package com.lingion.sleepy.widget.notification

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * Vendor live-card support probes. Every probe is read-only, defensive
 * (`try/catch`, default false), and MUST be safe to call from the main
 * thread once per post. Probes that hit system providers run on a
 * background thread inside [VendorLiveCardRenderer]; the renderer is
 * the only call site.
 *
 * Probe inventory (truth must remain stable across the renderer contract):
 *  - [xiaomiFocusGranted]: `content://miui.statusbar.notification.public` "canShowFocus"
 *  - [xiaomiIslandFeatureFlag]: `persist.sys.feature.island` via reflection
 *  - [flymeLiveEnabled]: `content://com.android.systemui.notification.provider` "isNotificationLiveEnabled"
 *  - [flymeVersion]: parsed from `Build.DISPLAY` (e.g. "Flyme 11.2.0")
 *  - [samsungNowBarFeature]: `com.samsung.feature.nowbar`
 *  - [vivoNotificationManagerBridge]: NotificationManager reflectively registers the
 *    `setSuperXInfosSceneList` listener (vivo whitelist is per-package, not per-app, so this
 *    only forwards the call; whether vivo accepts is unknown and reported as unknown).
 *
 * Sources: see docs/live-cards/{xiaomi,vivo,meizu,samsung}/REUSE.md and the cloned corpus.
 */
object VendorLiveCardSupport {

    private const val TAG = "VendorLiveCardSupport"

    fun xiaomiFocusGranted(context: Context): Boolean {
        return try {
            val uri = Uri.parse("content://miui.statusbar.notification.public")
            val bundle: Bundle? = context.contentResolver.call(
                uri, "canShowFocus", null,
                Bundle().apply { putString("package", context.packageName) }
            )
            bundle?.getBoolean("canShowFocus", false) ?: false
        } catch (t: Throwable) {
            Log.w(TAG, "canShowFocus probe failed", t)
            false
        }
    }

    @SuppressLint("BlockedPrivateApi")
    fun xiaomiIslandFeatureFlag(): Boolean {
        return try {
            val method = Class.forName("android.os.SystemProperties")
                .getDeclaredMethod("getBoolean", String::class.java, Boolean::class.java)
            method.invoke(null, "persist.sys.feature.island", false) as? Boolean ?: false
        } catch (t: Throwable) {
            false
        }
    }

    fun flymeLiveEnabled(context: Context): Boolean {
        if (context.checkSelfPermission("flyme.permission.READ_NOTIFICATION_LIVE_STATE")
            != PackageManager.PERMISSION_GRANTED) {
            // 与 provider probe 失败区分开：这里=manifest 未声明/系统未授予，
            // 不打异常栈，但留一条 warning 便于真机排查"胶囊从不出现"。
            Log.w(TAG, "flyme.permission.READ_NOTIFICATION_LIVE_STATE not granted; live path closed")
            return false
        }
        return try {
            val uri = Uri.parse("content://com.android.systemui.notification.provider")
            val call: Bundle? = context.contentResolver.call(
                uri, "isNotificationLiveEnabled", null, null as Bundle?
            )
            call?.getBoolean("result", false) ?: false
        } catch (t: Throwable) {
            Log.w(TAG, "isNotificationLiveEnabled probe failed", t)
            false
        }
    }

    fun flymeVersion(): Int {
        val display = Build.DISPLAY ?: return -1
        // Flyme 11+ enables the live-notification provider. Build.DISPLAY variants include
        // "Flyme 11.2.0", "FlymeOS 11", older lowercase "flyme 10" — match case-insensitive.
        val match = Regex("(?i)flyme\\s*([0-9]+)").find(display) ?: return -1
        return match.groupValues.getOrNull(1)?.toIntOrNull() ?: -1
    }

    fun samsungNowBarFeature(context: Context): Boolean {
        return try {
            context.packageManager.hasSystemFeature("com.samsung.feature.nowbar")
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Calls NotificationManager.setSuperXInfosSceneList reflectively. vivo grants
     * per-package, so this is best-effort: Sleepy may not be in vivo's allow-list
     * regardless of which scene names it requests. The renderer falls back to a
     * plain notification when the call returns false / throws.
     */
    @SuppressLint("BlockedPrivateApi")
    fun vivoRegisterSceneList(context: Context, scenes: List<String>): Boolean {
        return try {
            val method = Class.forName("android.app.NotificationManager")
                .getMethod(
                    "setSuperXInfosSceneList",
                    MutableList::class.java,
                    MutableList::class.java,
                    MutableList::class.java,
                    MutableList::class.java,
                )
            val enabled = MutableList(scenes.size) { "true" }
            val allowed = MutableList(scenes.size) { "true" }
            val packages = MutableList(scenes.size) { context.packageName }
            method.invoke(
                context.getSystemService("notification"),
                scenes, enabled, packages, allowed,
            )
            true
        } catch (t: Throwable) {
            Log.w(TAG, "setSuperXInfosSceneList probe failed", t)
            false
        }
    }
}