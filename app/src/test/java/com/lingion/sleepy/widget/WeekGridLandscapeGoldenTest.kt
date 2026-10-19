package com.lingion.sleepy.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.golden.GoldenCompare
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * JVM-side pixel golden for the landscape week grid — no emulator, no device.
 *
 * Renders the production Canvas path via Robolectric NATIVE graphics (LayoutLib,
 * same renderer as Android Studio previews), pins every input that could move a
 * pixel (locale, density, prefs, fixed historical dates), and diffs against a
 * committed baseline in app/src/test/golden/.
 *
 * Re-record deliberately:  GOLDEN_RECORD=1 ./gradlew :app:testDebugUnitTest
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-xxhdpi", application = SleepyApp::class)
class WeekGridLandscapeGoldenTest {

    @Before
    fun bindSleepyAppSingleton() {
        val app = ApplicationProvider.getApplicationContext<Context>() as SleepyApp
        val field = SleepyApp::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, app)
    }

    @Test
    fun landscapeWeekGridMatchesGolden() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        pinDeterministicPrefs(context)

        val bitmap = WeekGridWidgetProvider.renderBitmap(context, fixture(), LANDSCAPE_W_PX, LANDSCAPE_H_PX)
        try {
            GoldenCompare.assertGolden(bitmap, "week_grid_landscape_light.png")
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun landscapeWeekGridDarkMatchesGolden() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        pinDeterministicPrefs(context)

        val bitmap = WeekGridWidgetProvider.renderBitmap(
            context, fixture().copy(isDark = true), LANDSCAPE_W_PX, LANDSCAPE_H_PX,
        )
        try {
            GoldenCompare.assertGolden(bitmap, "week_grid_landscape_dark.png")
        } finally {
            bitmap.recycle()
        }
    }

    private fun pinDeterministicPrefs(context: Context) {
        AppPrefs.setPeriodHeaderLayout(context, "three_line")
        AppPrefs.setPeriodHeaderStyle(context, "arabic")
        AppPrefs.setPeriodHeaderHanging(context, 0f)
        AppPrefs.setPeriodHeaderShowX(context, true)
        AppPrefs.setThemeKey(context, "default")
    }

    private fun fixture(): WeekData {
        // 2026-09-28 (Monday) anchor week — same convention as WeekGridHeaderPixelTest.
        val monday = LocalDate.of(2026, 9, 28)
        val timeJson = TimeTableUtils.DEFAULT_TIME_JSON
        val days = (0 until 7).map { offset ->
            val dow = offset + 1
            DayData(
                date = monday.plusDays(offset.toLong()),
                dayOfWeek = dow,
                courses = sampleCourses(dow),
                timeJson = timeJson,
            )
        }
        return WeekData(
            days = days,
            hasTable = true,
            isDark = false,
            themeKey = "default",
            visibleDays = (1..7).toSet(),
        )
    }

    private fun sampleCourses(dow: Int): List<CourseEntity> = when (dow) {
        1 -> listOf(course(1, "高等数学", 1, 1, "A201"), course(2, "大学英语", 1, 5, "B302"))
        2 -> listOf(course(3, "程序设计", 2, 3, "实验楼105"))
        3 -> listOf(course(4, "大学物理", 3, 1, "A203"), course(5, "体育", 3, 7, "体育馆"))
        4 -> listOf(course(6, "工程制图", 4, 3, "C101"))
        5 -> listOf(course(7, "大学英语", 5, 5, "B302"), course(8, "创新实践", 5, 9, "实验楼202"))
        else -> emptyList()
    }

    private fun course(id: Long, name: String, day: Int, node: Int, room: String) = CourseEntity(
        id = id,
        groupId = "g$id",
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

    companion object {
        // 840x360 px at xxhdpi (density 3) = 280x120 dp landscape strip.
        private const val LANDSCAPE_W_PX = 840
        private const val LANDSCAPE_H_PX = 360
    }
}
