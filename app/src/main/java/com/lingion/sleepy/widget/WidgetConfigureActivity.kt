package com.lingion.sleepy.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.lingion.sleepy.ui.screen.widget.WidgetEditScreen
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.runBlocking

/**
 * Launcher entry point for configuring an individual widget.
 *
 * Two paths:
 * - First add (no binding for [appWidgetId]): silently binds the current default
 *   table and finishes OK. No UI is shown, so third-party launchers (lawnchair
 *   etc.) that mishandle the configure flow can still complete widget add.
 * - Re-edit (binding exists): shows [WidgetEditScreen] so the user can pick a
 *   different table.
 */
class WidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // First-add path: silently bind default table and finish OK.
        // Keeps third-party launchers happy — no UI, no friction.
        val existingBinding = WidgetBindingStore.get(this, appWidgetId)
        if (existingBinding == null) {
            val defaultTableId = runCatching {
                runBlocking { WidgetTableResolver.resolveCurrentTable()?.id }
            }.getOrNull()
            if (defaultTableId != null) {
                WidgetBindingStore.put(this, appWidgetId, defaultTableId)
            }
            finishWithResult(Activity.RESULT_OK)
            return
        }

        // Re-edit path: show the table picker.
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
}
