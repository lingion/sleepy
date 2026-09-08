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

        // First-add path: write sentinel binding (0L = follow default) and
        // finish OK. Pure SharedPreferences write — no DB, no runBlocking.
        // Defer finish to after the current frame (decorView.post) so stubborn
        // third-party launchers (lawnchair, OEM forks) that inspect activity
        // state in onActivityResult still see a fully-resumed activity.
        val existingBinding = WidgetBindingStore.get(this, appWidgetId)
        if (existingBinding == null) {
            WidgetBindingStore.put(this, appWidgetId, 0L)
            Log.i(TAG, "first-add: wrote sentinel binding for $appWidgetId, finishing OK")
            window.decorView.post {
                if (!isFinishing) finishWithResult(Activity.RESULT_OK)
            }
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
