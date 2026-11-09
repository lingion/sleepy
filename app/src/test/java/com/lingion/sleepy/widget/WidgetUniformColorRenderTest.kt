package com.lingion.sleepy.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.component.CourseOverlayCard
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.ui.theme.ThemePresets
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Compare actual app card pixels with the bitmaps published through RemoteViews and settings previews. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h640dp-mdpi", application = SleepyApp::class)
class WidgetUniformColorRenderTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val monday = LocalDate.of(2026, 9, 28)
    private val theme = mutableStateOf(ThemePresets.KEY_DEFAULT)
    private val dark = mutableStateOf(false)
    private val grey = mutableStateOf(false)
    private val wideBackground = mutableStateOf(false)
    // Blank text keeps the sampled pixel strictly inside the production course fill.
    private val course = CourseEntity(id = 1, groupId = "uniform-color", tableId = 1,
        courseName = "", room = "", day = 1, startNode = 1, step = 4,
        startWeek = 1, endWeek = 16, color = "#FF6750A4")

    @Before fun prepare() {
        SleepyApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, context)
        AppPrefs.setWidgetColorless(context, true)
        AppPrefs.setGridLongBreakSpacing(context, false)
        AppPrefs.setGridShowSeparators(context, false)
        compose.setContent {
            SleepyThemeProvider(darkTheme = dark.value, themeKey = theme.value) {
                Box(Modifier.size(100.dp).background(if (wideBackground.value)
                    MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer)) {
                    CourseOverlayCard(course, modifier = Modifier.size(100.dp).testTag("app-course"),
                        colorless = true, hueMap = emptyMap(), useAlias = false, subInfo = "none", isGrey = grey.value)
                }
            }
        }
    }

    @Test fun regularGridMatchesAppUniformFillAcrossPresetThemes() = checkThemes { expected ->
        val bitmap = WeekGridWidgetProvider.renderBitmap(context, fixture(), 300, 320)
        assertPixel("regular", expected, bitmap, 180, 180)
    }

    @Test fun compactColorBandMatchesAppUniformFillAcrossPresetThemes() = checkThemes { expected ->
        val bitmap = WeekGridWidgetProvider.renderBitmap(context, fixture(course.copy(step = 12)), 300, 160)
        assertPixel("color band", expected, bitmap, 180, 100)
    }

    @Test fun wideVerticalMatchesAppUniformFillAcrossPresetThemes() = checkWide(WeekGridWideMode.VERTICAL)

    @Test fun wideHorizontalMatchesAppUniformFillAcrossPresetThemes() = checkWide(WeekGridWideMode.HORIZONTAL)

    @Test fun greyRegularGridUsesSameDisabledOpacityAsApp() {
        compose.runOnIdle { grey.value = true }
        checkThemes { expected ->
            val bitmap = WeekGridWidgetProvider.renderBitmap(context, fixture(), 300, 320)
            assertPixel("grey regular", expected, bitmap, 180, 180)
        }
    }

    @Test fun greyWideGridUsesSameDisabledOpacityAsApp() {
        compose.runOnIdle { grey.value = true }
        checkWide(WeekGridWideMode.VERTICAL)
    }

    private fun checkWide(mode: WeekGridWideMode) {
        compose.runOnIdle { wideBackground.value = true }
        checkThemes { expected ->
            val data = fixture()
            val settings = WeekGridWideSettings(mode)
            val now = monday.atTime(7, 0)
            val card = WeekGridWideRenderer.layout(context, data, settings, now, 300, 160).cards.single()
            val bitmap = WeekGridWideRenderer.renderBitmap(context, data, settings, now, 300, 160)
            assertPixel("wide $mode", expected, bitmap, card.bounds.centerX().toInt(), card.bounds.centerY().toInt())
        }
    }

    private fun checkThemes(check: (Int) -> Unit) {
        for (key in listOf(ThemePresets.KEY_DEFAULT, ThemePresets.KEY_SPRING, ThemePresets.KEY_OCEAN,
            ThemePresets.KEY_PEACH, ThemePresets.KEY_SLATE)) {
            for (isDark in listOf(false, true)) {
                compose.runOnIdle { theme.value = key; dark.value = isDark }
                compose.waitForIdle()
                val app = compose.onNodeWithTag("app-course").captureToImage().asAndroidBitmap()
                check(app.getPixel(app.width / 2, app.height / 2))
            }
        }
    }

    private fun fixture(row: CourseEntity = course) = WeekData(
        days = (0L..6L).map { i -> DayData(monday.plusDays(i), i.toInt() + 1,
            if (i == 0L) listOf(row) else emptyList(), TimeTableUtils.DEFAULT_TIME_JSON, isGrey = grey.value) },
        hasTable = true, isDark = dark.value, themeKey = theme.value, visibleDays = setOf(1))

    private fun assertPixel(mode: String, expected: Int, bitmap: Bitmap, x: Int, y: Int) {
        assertEquals("$mode theme=${theme.value} dark=${dark.value} grey=${grey.value}", expected, bitmap.getPixel(x, y))
    }
}
