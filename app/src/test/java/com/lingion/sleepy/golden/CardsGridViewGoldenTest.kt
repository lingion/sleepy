package com.lingion.sleepy.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.component.CardsGridView
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Compose-path pixel golden for the in-app week timetable (cards grid),
 * light + dark. Exercises the production composable headlessly via
 * Robolectric NATIVE (LayoutLib).
 *
 * today/todayDate/startDate/currentWeek are pinned — defaults call
 * LocalDate.now() and would drift the baseline day by day. The grid's
 * scrollable column lives inside a bounded Box, so a fixed viewport is a
 * valid capture surface (the outer Box constrains height; only the inner
 * Column scrolls).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class CardsGridViewGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cardsGridLight() {
        assertComposeGolden(name = "cards_grid_light.png", dark = false)
    }

    @Test
    fun cardsGridDark() {
        assertComposeGolden(name = "cards_grid_dark.png", dark = true)
    }

    private fun assertComposeGolden(name: String, dark: Boolean) {
        composeRule.setContent {
            SleepyThemeProvider(darkTheme = dark, themeKey = "default") {
                Box(modifier = Modifier.size(360.dp, 640.dp)) {
                    CardsGridView(
                        courses = fixtureCourses(),
                        timeSlots = TimeTableUtils.timeSlotsFor(TimeTableUtils.DEFAULT_TIME_JSON),
                        today = 3,
                        todayDate = LocalDate.of(2026, 9, 30),
                        startDate = "2026-09-28",
                        currentWeek = 2,
                        onCourseClick = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        GoldenCompare.assertGolden(bitmap, name)
        bitmap.recycle()
    }

    private fun fixtureCourses(): List<CourseEntity> = listOf(
        course(1, "高等数学", "g1", 1, 1, "A201"),
        course(2, "大学英语", "g2", 1, 5, "B302"),
        course(3, "程序设计", "g3", 2, 3, "实验楼105"),
        course(4, "大学物理", "g4", 3, 1, "A203"),
        course(5, "体育", "g5", 3, 7, "体育馆"),
        course(6, "工程制图", "g6", 4, 3, "C101"),
        course(7, "创新实践", "g7", 5, 9, "实验楼202"),
    )

    private fun course(id: Long, name: String, groupId: String, day: Int, node: Int, room: String) =
        CourseEntity(
            id = id,
            groupId = groupId,
            tableId = 1L,
            courseName = name,
            day = day,
            startNode = node,
            step = 2,
            startWeek = 1,
            endWeek = 16,
            room = room,
            color = "#FF6750A4",
        )
}
