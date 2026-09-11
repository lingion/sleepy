package com.lingion.sleepy.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.lingion.sleepy.ui.screen.widget.WidgetEditScreen
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.AppPrefs

/**
 * Launcher entry point for configuring an individual widget.
 *
 * Two paths:
 * - First add (no binding for [appWidgetId]): writes a sentinel binding
 *   (0L = "follow default") and finishes OK. No UI, no DB query, no
 *   main-thread blocking — keeps third-party launchers (lawnchair, OEM
 *   forks, "Launcher" apps) happy even when their configure flow is flaky.
 *   Receivers already fall back via resolveBoundTable(0)=null →
 *   resolveCurrentTable(), so the sentinel is functionally identical to
 *   "no binding" at render time.
 * - Re-edit (binding exists): shows [WidgetEditScreen] so the user can pick
 *   a different table.
 */
class WidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        Log.i(TAG, "onCreate appWidgetId=$appWidgetId")
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.w(TAG, "no EXTRA_APPWIDGET_ID — canceling")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // First-add path: write sentinel binding (follow default) and
        // finish OK. Pure SharedPreferences write — no DB, no runBlocking.
        // Defer finish 600ms: OPPO ColorOS launcher 启动 configure 是异步的
        // (bind 已建 binding, result 回调后补)。decorView.post 一帧即 finish
        // 时 result 可能在 OPPO 的 add 流程处理前送达 → add 被回滚 →
        // "拖到桌面上直接就没了" (2026-09-10 真机报告) + 模拟器 id=6 僵尸
        // binding 复现 (binding 在但 launcher 树从未 apply RemoteViews)。
        // 600ms 给足 launcher 侧 result 处理的裕量, 用户不可感知 (无 UI)。
        val existingBinding = WidgetBindingStore.get(this, appWidgetId)
        if (WidgetConfigureCore.isFirstAdd(existingBinding)) {
            WidgetBindingStore.put(this, appWidgetId, WidgetConfigureCore.FIRST_ADD_BINDING)
            Log.i(TAG, "first-add: wrote sentinel binding for $appWidgetId, finishing OK in 600ms")
            window.decorView.postDelayed({
                if (!isFinishing) finishWithResult(Activity.RESULT_OK)
            }, 600L)
            return
        }

        // Re-edit path: show the table picker.
        Log.i(TAG, "re-edit: existing binding=$existingBinding, showing edit screen")
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithResult(Activity.RESULT_OK)
            }
        })

        val themeKey = AppPrefs.getThemeKey(this)
        setContent {
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val dark = AppPrefs.isDarkMode(this@WidgetConfigureActivity, systemDark)
            SleepyThemeProvider(darkTheme = dark, themeKey = themeKey) {
                WidgetEditScreen(
                    widgetId = appWidgetId,
                    onBack = { finishWithResult(Activity.RESULT_OK) }
                )
            }
        }
    }

    private fun finishWithResult(resultCode: Int) {
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(resultCode, result)
        finish()
    }

    private companion object {
        const val TAG = "SleepyWidgetCfg"
    }
}
