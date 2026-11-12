package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WeekGridWideSettingsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun bind(id: Int, receiver: Class<out android.appwidget.AppWidgetProvider>) {
        shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(id,
            AppWidgetProviderInfo().apply { provider = ComponentName(context, receiver) })
    }

    @Test fun receiverFixesLayoutAndLegacyModeCannotOverrideIt() {
        bind(21, WeekGridVerticalWidgetProvider::class.java)
        bind(22, WeekGridHorizontalWidgetProvider::class.java)
        context.getSharedPreferences("widget_wide_grid", Context.MODE_PRIVATE).edit()
            .putString("mode_21", "HORIZONTAL").putString("mode_22", "ORIGINAL").apply()
        assertEquals(WeekGridWideMode.VERTICAL, WeekGridWideSettingsStore.read(context, 21).mode)
        assertEquals(WeekGridWideMode.HORIZONTAL, WeekGridWideSettingsStore.read(context, 22).mode)
        assertEquals(WeekGridWideMode.VERTICAL, WeekGridVerticalWidgetProvider().layoutMode)
        assertEquals(WeekGridWideMode.HORIZONTAL, WeekGridHorizontalWidgetProvider().layoutMode)
        assertEquals(listOf("VERTICAL", "HORIZONTAL"), WeekGridWideMode.entries.map { it.name })
    }

    @Test fun onlyLabelSidePersistsIndependentlyAndDeletionClearsIt() {
        bind(21, WeekGridHorizontalWidgetProvider::class.java)
        bind(22, WeekGridHorizontalWidgetProvider::class.java)
        WeekGridWideSettingsStore.write(context, 21, WeekGridWideSettings(WeekGridWideMode.VERTICAL, WeekGridWideLabelSide.RIGHT))
        assertEquals(WeekGridWideSettings(WeekGridWideMode.HORIZONTAL, WeekGridWideLabelSide.RIGHT), WeekGridWideSettingsStore.read(context, 21))
        assertEquals(WeekGridWideLabelSide.LEFT, WeekGridWideSettingsStore.read(context, 22).labelSide)
        assertFalse(context.getSharedPreferences("widget_wide_grid", Context.MODE_PRIVATE).contains("mode_21"))
        WeekGridHorizontalWidgetProvider().onDeleted(context, intArrayOf(21))
        assertEquals(WeekGridWideLabelSide.LEFT, WeekGridWideSettingsStore.read(context, 21).labelSide)
    }

    @Test fun invalidLabelFallsBackWithoutChangingFixedLayout() {
        bind(23, WeekGridHorizontalWidgetProvider::class.java)
        context.getSharedPreferences("widget_wide_grid", Context.MODE_PRIVATE).edit()
            .putString("side_23", "future_side").apply()
        assertEquals(WeekGridWideSettings(WeekGridWideMode.HORIZONTAL), WeekGridWideSettingsStore.read(context, 23))
    }
}
