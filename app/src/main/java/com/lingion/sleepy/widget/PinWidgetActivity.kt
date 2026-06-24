package com.lingion.sleepy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity

/**
 * 透明 activity：启动后立即 requestPinAppWidget，让系统 dialog 显示在 home screen 上方。
 * adb 启动 → 系统"添加小组件"dialog → adb uiautomator 自动点"添加"。
 */
class PinWidgetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 透明窗口 — 不显示任何 UI
        window.attributes.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)

        val awm = AppWidgetManager.getInstance(this)
        val cn = ComponentName(this, WeekGridWidgetReceiver::class.java)
        val supported = awm.isRequestPinAppWidgetSupported
        Log.d("PinWidget", "supported=$supported, provider=$cn")

        if (supported) {
            val result = awm.requestPinAppWidget(cn, null, null)
            Log.d("PinWidget", "result=$result")
        }
        // 延迟 finish — 给 launcher 时间显示 dialog
        window.decorView.postDelayed({ finish() }, 3000)
    }
}
