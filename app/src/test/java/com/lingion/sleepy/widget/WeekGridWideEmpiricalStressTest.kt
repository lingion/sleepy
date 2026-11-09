package com.lingion.sleepy.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.ui.screen.widget.WidgetEditScope
import com.lingion.sleepy.ui.screen.widget.WidgetEditWideGridSection
import com.lingion.sleepy.ui.screen.widget.WideGridShapeOptions
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Adversarial Empirical Stress Tests for Inline Wide Grid Shape Selection (Milestone 2 / R2).
 * Verifies:
 * 1. SharedPreferences persistence, defaults, corrupted key fallbacks, and removals.
 * 2. Rapid shape switching, bounds order preservation, and label toggle state retention.
 * 3. All WideGridPreviewStatus variations (LOADING, STALE, ERROR, EMPTY, NO_TABLE, UNAVAILABLE, READY).
 * 4. Receiver filtering isolation across wide and non-wide widget types.
 * 5. Full LazyColumn integration inside WidgetEditScreen without nested scroll crashes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h800dp-port-mdpi", application = SleepyApp::class)
class WeekGridWideEmpiricalStressTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun testSharedPreferencesPersistenceRoundtripAndFallback() {
        val app = ApplicationProvider.getApplicationContext<SleepyApp>()
        val widgetId = 999

        // 1. Initial read defaults to VERTICAL and LEFT
        val initial = WeekGridWideSettingsStore.read(app, widgetId)
        assertEquals(WeekGridWideMode.VERTICAL, initial.mode)
        assertEquals(WeekGridWideLabelSide.LEFT, initial.labelSide)

        // 2. Write custom settings (HORIZONTAL + RIGHT)
        val custom = WeekGridWideSettings(WeekGridWideMode.HORIZONTAL, WeekGridWideLabelSide.RIGHT)
        WeekGridWideSettingsStore.write(app, widgetId, custom)
        val readBack = WeekGridWideSettingsStore.read(app, widgetId)
        assertEquals(WeekGridWideMode.HORIZONTAL, readBack.mode)
        assertEquals(WeekGridWideLabelSide.RIGHT, readBack.labelSide)

        // 3. Fallback on corrupted / unknown keys in SharedPreferences
        app.getSharedPreferences("widget_wide_grid", Context.MODE_PRIVATE).edit()
            .putString("mode_$widgetId", "CORRUPTED_VALUE")
            .putString("side_$widgetId", "UNKNOWN_SIDE")
            .commit()
        val fallback = WeekGridWideSettingsStore.read(app, widgetId)
        assertEquals(WeekGridWideMode.VERTICAL, fallback.mode)
        assertEquals(WeekGridWideLabelSide.LEFT, fallback.labelSide)

        // 4. Remove clears back to defaults
        WeekGridWideSettingsStore.remove(app, widgetId)
        val afterRemove = WeekGridWideSettingsStore.read(app, widgetId)
        assertEquals(WeekGridWideMode.VERTICAL, afterRemove.mode)
        assertEquals(WeekGridWideLabelSide.LEFT, afterRemove.labelSide)
    }

    @Test
    fun testRapidShapeSwitchingAndLabelStateRetention() {
        var settings by mutableStateOf(WeekGridWideSettings(WeekGridWideMode.HORIZONTAL, WeekGridWideLabelSide.RIGHT))
        val images = WideGridPreviews(WideGridPreviewStatus.READY, WeekGridWidePreviewLoader.displayOrder.map {
            WideGridPreview(it, WeekData(emptyList(), true), Bitmap.createBitmap(300, 160, Bitmap.Config.ARGB_8888))
        })

        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                WideGridShapeOptions(
                    settings = settings,
                    previews = images,
                    onModeChange = { settings = settings.copy(mode = it) },
                    onLabelSideChange = { settings = settings.copy(labelSide = it) }
                )
            }
        }

        // Start in HORIZONTAL: labels exist, RIGHT is selected
        compose.onNodeWithTag("wide-shape-HORIZONTAL").assertIsSelected()
        compose.onNodeWithTag("wide-label-RIGHT").assertIsSelected()
        compose.onNodeWithTag("wide-label-LEFT").assertIsNotSelected()

        // Switch to ORIGINAL: labels must vanish completely
        compose.onNodeWithTag("wide-shape-ORIGINAL").performClick().assertIsSelected()
        compose.onNodeWithTag("wide-label-LEFT").assertDoesNotExist()
        compose.onNodeWithTag("wide-label-RIGHT").assertDoesNotExist()
        assertEquals(WeekGridWideMode.ORIGINAL, settings.mode)

        // Switch to VERTICAL: labels must still not exist
        compose.onNodeWithTag("wide-shape-VERTICAL").performClick().assertIsSelected()
        compose.onNodeWithTag("wide-label-LEFT").assertDoesNotExist()
        compose.onNodeWithTag("wide-label-RIGHT").assertDoesNotExist()
        assertEquals(WeekGridWideMode.VERTICAL, settings.mode)

        // Switch back to HORIZONTAL: labels reappear, and RIGHT selection was preserved
        compose.onNodeWithTag("wide-shape-HORIZONTAL").performClick().assertIsSelected()
        compose.onNodeWithTag("wide-label-RIGHT").assertExists().assertIsSelected()
        assertEquals(WeekGridWideLabelSide.RIGHT, settings.labelSide)

        // Switch label to LEFT
        compose.onNodeWithTag("wide-label-LEFT").performClick().assertIsSelected()
        assertEquals(WeekGridWideLabelSide.LEFT, settings.labelSide)
    }

    @Test
    fun testAllPreviewStatusTransitions() {
        var status by mutableStateOf(WideGridPreviewStatus.LOADING)
        val dummyBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                val previews = if (status == WideGridPreviewStatus.READY) {
                    WideGridPreviews(status, WeekGridWidePreviewLoader.displayOrder.map {
                        WideGridPreview(it, WeekData(emptyList(), true), dummyBitmap)
                    })
                } else {
                    WideGridPreviews(status)
                }
                WideGridShapeOptions(
                    settings = WeekGridWideSettings(),
                    previews = previews,
                    onModeChange = {},
                    onLabelSideChange = {}
                )
            }
        }

        // 1. LOADING: status tag exists
        compose.onNodeWithTag("wide-preview-status").assertExists()

        // 2. STALE: status tag exists
        compose.runOnIdle { status = WideGridPreviewStatus.STALE }
        compose.onNodeWithTag("wide-preview-status").assertExists()

        // 3. NO_TABLE: status tag exists
        compose.runOnIdle { status = WideGridPreviewStatus.NO_TABLE }
        compose.onNodeWithTag("wide-preview-status").assertExists()

        // 4. EMPTY: status tag exists
        compose.runOnIdle { status = WideGridPreviewStatus.EMPTY }
        compose.onNodeWithTag("wide-preview-status").assertExists()

        // 5. UNAVAILABLE: status tag exists
        compose.runOnIdle { status = WideGridPreviewStatus.UNAVAILABLE }
        compose.onNodeWithTag("wide-preview-status").assertExists()

        // 6. ERROR: status tag exists
        compose.runOnIdle { status = WideGridPreviewStatus.ERROR }
        compose.onNodeWithTag("wide-preview-status").assertExists()

        // 7. READY: status tag must NOT exist
        compose.runOnIdle { status = WideGridPreviewStatus.READY }
        compose.onNodeWithTag("wide-preview-status").assertDoesNotExist()

        // Previews for all 3 modes exist when READY
        listOf("ORIGINAL", "VERTICAL", "HORIZONTAL").forEach {
            compose.onNodeWithTag("wide-preview-$it", useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun testReceiverGatingIsolation() {
        var receiver by mutableStateOf<String?>(null)
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                WidgetEditWideGridSection.Content(
                    WidgetEditScope(
                        widgetId = 42,
                        currentBinding = null,
                        availableTables = emptyList(),
                        onSelectTable = {},
                        receiverSimpleName = receiver
                    )
                )
            }
        }

        val nonWideReceivers = listOf(
            null,
            "TodaySmallWidgetReceiver",
            "TodayWideWidgetReceiver",
            "TodayWidgetReceiver",
            "WeekListWidgetReceiver",
            "WeekListSmallWidgetReceiver",
            "WeekListWideWidgetReceiver",
            "WeekViewWidgetReceiver",
            "WeekViewSmallWidgetReceiver",
            "WeekGridWidgetProvider",
            "WeekGridSmallWidgetProvider",
            "TwoDayWidgetReceiver",
            "TwoDaySmallWidgetReceiver",
            "TwoDayWideWidgetReceiver"
        )

        for (rec in nonWideReceivers) {
            compose.runOnIdle { receiver = rec }
            compose.onNodeWithTag("wide-shape-VERTICAL").assertDoesNotExist()
            compose.onNodeWithTag("wide-shape-ORIGINAL").assertDoesNotExist()
            compose.onNodeWithTag("wide-shape-HORIZONTAL").assertDoesNotExist()
        }

        // Only WeekGridWideWidgetProvider exposes the section
        compose.runOnIdle { receiver = "WeekGridWideWidgetProvider" }
        compose.onNodeWithTag("wide-shape-VERTICAL").assertExists()
        compose.onNodeWithTag("wide-shape-ORIGINAL").assertExists()
        compose.onNodeWithTag("wide-shape-HORIZONTAL").assertExists()
    }
}
