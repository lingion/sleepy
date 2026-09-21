package com.lingion.sleepy.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 调试用 Activity：渲染 4 个桌面 Widget 样式到屏幕，并保存为 PNG 用于 README 截图。
 *
 * 通过 Intent extra 指定要渲染哪个 widget：
 *  - `widget=today` (250x180 dp)
 *  - `widget=twoday` (320x220 dp)
 *  - `widget=weeklist` (320x200 dp)
 *  - `widget=weekgrid` (250x300 dp)
 *  - 缺省 = weekgrid
 *
 * 真实数据来源：当前课表（表 1 = 2026 春学期，HEU 13 节真实课表）。
 */
class WidgetRenderActivity : Activity() {

    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val which = intent.getStringExtra("widget") ?: "weekgrid"
        // 设计 §7 三档重定档 (评审 #19): L=300×250, M=300×160
        val (wDp, hDp) = when (which) {
            "today" -> 300f to 250f
            "twoday" -> 300f to 250f
            "weeklist" -> 300f to 250f
            "today_wide", "twoday_wide", "weeklist_wide" -> 300f to 160f
            else -> 300f to 250f
        }
        Log.d(TAG, "rendering widget=$which, size=${wDp}x${hDp}dp")

        // FrameLayout: 居中 ImageView 展示 widget bitmap
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF1A1A2E.toInt())
        }
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Widget Preview"
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            setMargins(0, 0, 0, 0)
        }
        root.addView(img, params)
        setContentView(root)

        scope.launch {
            try {
                val bmp = renderWidgetBitmap(which, wDp, hDp)
                img.setImageBitmap(bmp)
            } catch (e: Throwable) {
                Log.e(TAG, "render failed", e)
                img.setBackgroundColor(Color.RED)
            }
        }
    }

    private suspend fun renderWidgetBitmap(which: String, wDp: Float, hDp: Float): android.graphics.Bitmap {
        return when (which) {
            "today" -> {
                WidgetBitmapRenderers.renderToday(
                    this,
                    TodayWidgetReceiver.loadDataSync(this, AppWidgetManager.INVALID_APPWIDGET_ID),
                    wDp, hDp
                )
            }
            "twoday" -> {
                WidgetBitmapRenderers.renderTwoDay(
                    this,
                    TwoDayWidgetReceiver.loadDataSync(this, AppWidgetManager.INVALID_APPWIDGET_ID),
                    wDp, hDp
                )
            }
            "weeklist" -> {
                WidgetBitmapRenderers.renderWeekList(
                    this,
                    WeekListWidgetReceiver.loadDataSync(this, AppWidgetManager.INVALID_APPWIDGET_ID),
                    wDp, hDp
                )
            }
            else -> {
                // Debug 预览不是真实 widget 实例 — 传 INVALID_APPWIDGET_ID 让 binding 查找短路, 落到默认表
                val data = WeekGridWidgetProvider.loadWeekData(
                    this, AppWidgetManager.INVALID_APPWIDGET_ID
                )
                val density = resources.displayMetrics.density
                val w = (wDp * density).toInt()
                val h = (hDp * density).toInt()
                WeekGridWidgetProvider.renderBitmap(this, data, w, h)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WidgetRender"

        /** 启动入口：渲染指定 widget */
        fun start(activity: android.app.Activity, which: String) {
            activity.startActivity(
                android.content.Intent(activity, WidgetRenderActivity::class.java)
                    .putExtra("widget", which)
            )
        }
    }
}
