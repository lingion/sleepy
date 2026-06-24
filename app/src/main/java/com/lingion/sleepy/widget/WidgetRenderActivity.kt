package com.lingion.sleepy.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.ComponentActivity

/**
 * 真实 Glance Widget 渲染验证。
 * 用公开 API AppWidgetHost 分配真实 widgetId + bind → 系统 RemoteViews 自动渲染。
 * ★ 跟 home screen 上的 widget 完全一致。
 */
class WidgetRenderActivity : ComponentActivity() {

    private var host: AppWidgetHost? = null
    private var allocatedId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val awm = AppWidgetManager.getInstance(this)
            val cn = ComponentName(this, WeekGridWidgetReceiver::class.java)
            val providerInfo = awm.installedProviders.find { it.provider == cn }

            if (providerInfo == null) {
                Log.e("WidgetRender", "WeekGridWidgetReceiver not found")
                redScreen(); return
            }

            host = AppWidgetHost(this, 1976)
            host?.startListening()

            allocatedId = host?.allocateAppWidgetId() ?: -1
            Log.d("WidgetRender", "allocatedId=$allocatedId")
            if (allocatedId == -1) {
                Log.e("WidgetRender", "allocateAppWidgetId failed")
                redScreen(); return
            }

            // bind → 触发 provider.onUpdate → Glance 渲染
            val bound = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                awm.bindAppWidgetIdIfAllowed(allocatedId, cn)
            } else false
            Log.d("WidgetRender", "bindAppWidgetIdIfAllowed=$bound")

            // 4×5 widget 尺寸
            val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(metrics)
            val density = metrics.density
            // ★ 用用户手机实际 widget 尺寸（Android 16, 4×5 widget）
            val widgetW_dp = 418f   // OPTION_APPWIDGET_MAX_WIDTH
            val widgetH_dp = 643f   // OPTION_APPWIDGET_MAX_HEIGHT
            val widgetW_px = (widgetW_dp * density).toInt()
            val widgetH_px = (widgetH_dp * density).toInt()
            Log.d("WidgetRender", "density=$density, ${widgetW_dp}x${widgetH_dp}dp = ${widgetW_px}x${widgetH_px}px")

            val hostView = host?.createView(this, allocatedId, providerInfo) ?: run {
                Log.e("WidgetRender", "createView failed"); redScreen(); return
            }

            val opts = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widgetW_dp.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, widgetH_dp.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widgetW_dp.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, widgetH_dp.toInt())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
                }
            }
            hostView.updateAppWidgetOptions(opts)

            val container = FrameLayout(this).apply {
                setBackgroundColor(0xFF1A1A2E.toInt())
                addView(hostView, FrameLayout.LayoutParams(widgetW_px, widgetH_px).apply {
                    gravity = android.view.Gravity.CENTER
                })
            }
            setContentView(container)
            Log.d("WidgetRender", "hostView ready")

            // Glance 异步 → 2 秒后刷新
            hostView.postDelayed({
                hostView.invalidate()
                Log.d("WidgetRender", "postDelayed refresh done")
            }, 2000)

        } catch (e: Exception) {
            Log.e("WidgetRender", "FAILED", e)
            redScreen()
        }
    }

    private fun redScreen() {
        setContentView(FrameLayout(this).apply { setBackgroundColor(0xFFFF0000.toInt()) })
    }

    override fun onStart() {
        super.onStart()
        host?.startListening()
    }

    override fun onStop() {
        super.onStop()
        host?.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        host?.stopListening()
        if (allocatedId != -1) host?.deleteAppWidgetId(allocatedId)
    }
}
