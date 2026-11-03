package com.lingion.sleepy.widget

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h800dp-port-mdpi", application = com.lingion.sleepy.SleepyApp::class)
class WeekGridWideShapeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun threeImageOptionsStayInExactOrderAndSelectionStaysOnPage() {
        var settings by mutableStateOf(WeekGridWideSettings())
        val images = WideGridPreviews(WideGridPreviewStatus.READY, WeekGridWidePreviewLoader.displayOrder.map {
            WideGridPreview(it, WeekData(emptyList(), true), Bitmap.createBitmap(300, 160, Bitmap.Config.ARGB_8888))
        })
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                WideGridShapeOptions(settings, images,
                    onModeChange = { settings = settings.copy(mode = it) },
                    onLabelSideChange = { settings = settings.copy(labelSide = it) })
            }
        }
        val order = listOf("ORIGINAL", "VERTICAL", "HORIZONTAL")
        val tops = order.map { name ->
            compose.onNodeWithTag("wide-shape-$name").assertExists().fetchSemanticsNode().boundsInRoot.top
        }
        assertTrue(tops[0] < tops[1] && tops[1] < tops[2])
        order.forEach { compose.onNodeWithTag("wide-preview-$it", useUnmergedTree = true).assertExists() }
        compose.onNodeWithTag("wide-shape-VERTICAL").assertIsSelected()
        compose.onNodeWithTag("wide-label-LEFT").assertDoesNotExist()
        compose.onNodeWithTag("wide-shape-HORIZONTAL").performClick().assertIsSelected()
        compose.onNodeWithTag("wide-label-RIGHT").performClick().assertIsSelected()
        assertEquals(WeekGridWideLabelSide.RIGHT, settings.labelSide)
        compose.onNodeWithTag("wide-shape-ORIGINAL").performClick().assertIsSelected()
        compose.onNodeWithTag("wide-label-LEFT").assertDoesNotExist()
    }

    @Test fun instanceEditShowsLiveThreePreviewsInlineAndSelects() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<com.lingion.sleepy.SleepyApp>()
        androidx.work.WorkManager.initialize(app, androidx.work.Configuration.Builder().build())
        com.lingion.sleepy.SleepyApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        val db = androidx.room.Room.inMemoryDatabaseBuilder(app, com.lingion.sleepy.data.AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val repo = com.lingion.sleepy.data.repository.ScheduleRepository(db)
        com.lingion.sleepy.SleepyApp::class.java.getDeclaredField("database\$delegate").apply { isAccessible = true }.set(app, lazyOf(db))
        com.lingion.sleepy.SleepyApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible = true }.set(app, lazyOf(repo))
        val info = android.appwidget.AppWidgetProviderInfo().apply {
            provider = android.content.ComponentName(app, WeekGridWideWidgetProvider::class.java)
        }
        org.robolectric.Shadows.shadowOf(android.appwidget.AppWidgetManager.getInstance(app)).addBoundWidget(81, info)
        kotlinx.coroutines.runBlocking { repo.getAllTables() }
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                com.lingion.sleepy.ui.screen.widget.WidgetEditScreen(81, {})
            }
        }
        compose.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.onAllNodesWithTag("wide-preview-ORIGINAL", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        for (mode in listOf("ORIGINAL", "VERTICAL", "HORIZONTAL")) {
            compose.onNodeWithTag("wide-preview-$mode", useUnmergedTree = true).assertExists()
        }
        compose.onNodeWithTag("wide-shape-ORIGINAL").performScrollTo().performClick().assertIsSelected()
        assertEquals(WeekGridWideMode.ORIGINAL, WeekGridWideSettingsStore.read(app, 81).mode)
    }

    @Test fun wideGridSectionOnlyAppearsForWideReceiver() {
        var receiver by mutableStateOf<String?>(null)
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                WidgetEditWideGridSection.Content(WidgetEditScope(1, null, emptyList(), {},
                    receiverSimpleName = receiver))
            }
        }
        for (name in listOf(null, "WeekGridWidgetProvider", "WeekGridSmallWidgetProvider")) {
            compose.runOnIdle { receiver = name }
            compose.onNodeWithTag("wide-shape-VERTICAL").assertDoesNotExist()
        }
        compose.runOnIdle { receiver = "WeekGridWideWidgetProvider" }
        compose.onNodeWithTag("wide-shape-VERTICAL").assertExists()
    }
}
