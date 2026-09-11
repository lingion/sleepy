package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
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
    companion object {
        const val EXTRA_WIDGET = "widget_type"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)

        val awm = AppWidgetManager.getInstance(this)
        val supported = awm.isRequestPinAppWidgetSupported
        val type = intent.getStringExtra(EXTRA_WIDGET)
        // 路由表派生自 ALL_WIDGET_VARIANTS（10/10 变体全覆盖），解析在 PinWidgetRouting（纯函数可测）。
        // 各厂商 requestPinAppWidget 行为差异见 docs/widget-vendor-specs/INDEX.md。
        val cn = android.content.ComponentName(this, PinWidgetRouting.resolveClass(type))
        Log.d("PinWidget", "supported=$supported, provider=$cn")

        if (supported) {
            val result = awm.requestPinAppWidget(cn, null, null)
            Log.d("PinWidget", "result=$result")
        }
        window.decorView.postDelayed({ finish() }, 5000)
    }
}
