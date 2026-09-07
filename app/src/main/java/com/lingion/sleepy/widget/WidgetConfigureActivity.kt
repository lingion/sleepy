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

/**
 * Launcher entry point for configuring an individual widget.
 *
 * The launcher supplies [AppWidgetManager.EXTRA_APPWIDGET_ID]. Keep this
 * activity separate from MainActivity so the native launcher edit flow does
 * not depend on the app's overlay navigation stack.
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
