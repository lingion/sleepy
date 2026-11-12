package com.lingion.sleepy.widget

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lingion.sleepy.ui.screen.widget.WidgetEditScope
import com.lingion.sleepy.ui.screen.widget.WidgetEditWideGridSection
import com.lingion.sleepy.ui.screen.widget.WideGridPreviewContent
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

    @Test fun fixedLayoutHasOnePreviewAndOnlyHorizontalLabelSideCanChange() {
        var settings by mutableStateOf(WeekGridWideSettings(WeekGridWideMode.HORIZONTAL))
        val images = WideGridPreviews(WideGridPreviewStatus.READY, listOf(
            WideGridPreview(WeekGridWideMode.HORIZONTAL, WeekData(emptyList(), true), Bitmap.createBitmap(300, 160, Bitmap.Config.ARGB_8888))))
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                WideGridPreviewContent(settings, images,
                    onLabelSideChange = { settings = settings.copy(labelSide = it) })
            }
        }
        compose.onNodeWithTag("wide-preview-HORIZONTAL", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("wide-preview-VERTICAL", useUnmergedTree = true).assertDoesNotExist()
        for (mode in listOf("ORIGINAL", "VERTICAL", "HORIZONTAL"))
            compose.onNodeWithTag("wide-shape-$mode").assertDoesNotExist()
        compose.onNodeWithTag("wide-label-RIGHT").performClick().assertIsSelected()
        assertEquals(WeekGridWideMode.HORIZONTAL, settings.mode)
        assertEquals(WeekGridWideLabelSide.RIGHT, settings.labelSide)
        compose.runOnIdle { settings = settings.copy(mode = WeekGridWideMode.VERTICAL) }
        compose.onNodeWithTag("wide-label-LEFT").assertDoesNotExist()
        compose.onNodeWithTag("wide-label-RIGHT").assertDoesNotExist()
    }

    @Test fun instanceEditShowsOnlyItsOwnLivePreviewAndPersistsLabelSide() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<com.lingion.sleepy.SleepyApp>()
        androidx.work.WorkManager.initialize(app, androidx.work.Configuration.Builder().build())
        com.lingion.sleepy.SleepyApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        val db = androidx.room.Room.inMemoryDatabaseBuilder(app, com.lingion.sleepy.data.AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val repo = com.lingion.sleepy.data.repository.ScheduleRepository(db)
        com.lingion.sleepy.SleepyApp::class.java.getDeclaredField("database\$delegate").apply { isAccessible = true }.set(app, lazyOf(db))
        com.lingion.sleepy.SleepyApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible = true }.set(app, lazyOf(repo))
        val info = android.appwidget.AppWidgetProviderInfo().apply {
            provider = android.content.ComponentName(app, WeekGridHorizontalWidgetProvider::class.java)
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
            compose.onAllNodesWithTag("wide-preview-HORIZONTAL", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("wide-preview-VERTICAL", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("wide-shape-ORIGINAL").assertDoesNotExist()
        compose.onNodeWithTag("wide-label-RIGHT").performScrollTo().performClick().assertIsSelected()
        assertEquals(WeekGridWideLabelSide.RIGHT, WeekGridWideSettingsStore.read(app, 81).labelSide)
        assertEquals(WeekGridWideMode.HORIZONTAL, WeekGridWideSettingsStore.read(app, 81).mode)
    }

    @Test fun sectionOnlyAppliesToTheTwoNewComponents() {
        for (name in listOf(null, "WeekGridWideWidgetProvider", "WeekGridWidgetProvider", "WeekGridSmallWidgetProvider"))
            assertFalse(WidgetEditWideGridSection.appliesTo(name))
        assertTrue(WidgetEditWideGridSection.appliesTo("WeekGridVerticalWidgetProvider"))
        assertTrue(WidgetEditWideGridSection.appliesTo("WeekGridHorizontalWidgetProvider"))
    }
}
