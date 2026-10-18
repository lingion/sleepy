package com.lingion.sleepy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 应用内「添加到桌面」唯一通路(设计 §4 不变量③): 预检 → requestPin → callback 闭环。
 *
 * - [PinResult.NOT_SUPPORTED] 桌面不支持快捷添加 → UI 出长按手动添加指引
 * - [PinResult.FAILED]        发起失败(如网格放不下) → 同指引兜底
 * - [PinResult.REQUESTED]     系统确认弹窗已弹出; 用户确认与否都无副作用
 *
 * callback PendingIntent 广播到对应 receiver — AppWidgetProvider 基类对未知
 * action 静默忽略,此处只为让系统把新 appWidgetId 回灌给 launcher 生命周期;
 * 管理页在 ON_RESUME 重扫桌面实例,新卡片即时可见(设计 §5 pin 成功语义)。
 *
 * adb/外部深链仍走 PinWidgetActivity(遗留透明通路,不经过本 helper)。
 */
enum class PinResult { REQUESTED, NOT_SUPPORTED, FAILED }

object WidgetPinRequest {

    /** callback action 常量(广播目标 = variant 自己的 receiver)。 */
    const val ACTION_PINNED = "com.lingion.sleepy.widget.ACTION_PINNED"

    /** callback PendingIntent 的 data 前缀 — Uri 形式避免 Intent-filter 歧义。 */
    const val PINNED_URI_PREFIX = "sleepy-widget://pinned/"

    fun request(context: Context, variant: WidgetVariantInfo): PinResult {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return PinResult.NOT_SUPPORTED

        val callback = Intent(context, variant.receiverClass).apply {
            action = ACTION_PINNED
            data = android.net.Uri.parse(PINNED_URI_PREFIX + variant.receiverClass.simpleName)
        }
        // requestCode 用类名 hash: 不同变体的 callback 互不覆盖(FLAG_UPDATE_CURRENT 语义)。
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val pi = PendingIntent.getBroadcast(
            context, variant.receiverClass.name.hashCode(), callback, flags
        )
        val cn = ComponentName(context, variant.receiverClass)
        val requested = manager.requestPinAppWidget(cn, null, pi)
        return if (requested) PinResult.REQUESTED else PinResult.FAILED
    }
}
