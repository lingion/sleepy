package com.lingion.sleepy.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WeekGridWideSettingsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun independentInstancesAndDeletedIdReturnToVerticalDefault() {
        val settings = WeekGridWideSettings(WeekGridWideMode.HORIZONTAL, WeekGridWideLabelSide.RIGHT)
        WeekGridWideSettingsStore.write(context, 21, settings)
        assertEquals(settings, WeekGridWideSettingsStore.read(context, 21))
        assertEquals(WeekGridWideSettings(), WeekGridWideSettingsStore.read(context, 22))
        WeekGridWideSettingsStore.remove(context, 21)
        assertEquals(WeekGridWideSettings(), WeekGridWideSettingsStore.read(context, 21))
    }

    @Test fun threeFormsRoundTripByNameNotOrdinalAndStayIndependent() {
        val names = listOf("ORIGINAL", "VERTICAL", "HORIZONTAL")
        assertEquals(names, WeekGridWideMode.entries.map { it.name })
        names.forEachIndexed { index, name ->
            val mode = WeekGridWideMode.valueOf(name)
            WeekGridWideSettingsStore.write(context, 100 + index, WeekGridWideSettings(mode))
        }
        names.forEachIndexed { index, name ->
            assertEquals(name, WeekGridWideSettingsStore.read(context, 100 + index).mode.name)
            assertEquals(name, context.getSharedPreferences("widget_wide_grid", Context.MODE_PRIVATE).getString("mode_${100 + index}", null))
        }
        assertEquals(WeekGridWideMode.VERTICAL, WeekGridWideSettingsStore.read(context, 999).mode)
    }

    @Test fun invalidValuesResolveToSafeDefaults() {
        context.getSharedPreferences("widget_wide_grid", Context.MODE_PRIVATE).edit()
            .putString("mode_23", "future_mode").putString("side_23", "future_side").apply()
        assertEquals(WeekGridWideSettings(), WeekGridWideSettingsStore.read(context, 23))
    }

    @Test fun changingModeDoesNotLoseSelectedLabelSide() {
        val selected = WeekGridWideSettings(WeekGridWideMode.HORIZONTAL, WeekGridWideLabelSide.RIGHT)
        WeekGridWideSettingsStore.write(context, 24, selected)
        WeekGridWideSettingsStore.write(context, 24, WeekGridWideSettingsStore.read(context, 24).copy(mode = WeekGridWideMode.VERTICAL))
        assertEquals(WeekGridWideLabelSide.RIGHT, WeekGridWideSettingsStore.read(context, 24).labelSide)
    }
}
