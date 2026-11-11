package com.lingion.sleepy.widget

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.ui.theme.ThemePresets
import com.lingion.sleepy.util.AppPrefs
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 周网格小组件表头压缩契约 (用户 2026-11-09 令: 表头太高, 压缩后课程区更大):
 * - 表头高度单一事实来源 = 48dp (旧 56dp);
 * - bodyH 公式必须与表头 token 同源, 省出的高度全部进入课程主体;
 * - 渲染像素: 左上角时间格(恒 surface 色空白卡)底边落在 outerPad + 48dp。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-xhdpi", application = SleepyApp::class)
class WeekGridHeaderCompactTest {
    private val app get() = ApplicationProvider.getApplicationContext<SleepyApp>()

    @Before fun setup() {
        SleepyApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = ScheduleRepository(db)
        SleepyApp::class.java.getDeclaredField("database\$delegate")
            .apply { isAccessible = true }.set(app, lazyOf(db))
        SleepyApp::class.java.getDeclaredField("repository\$delegate")
            .apply { isAccessible = true }.set(app, lazyOf(repo))
        AppPrefs.setGridShowSeparators(app, false)
    }

    @Test fun `header height token is 48dp`() {
        assertEquals(48f, WeekGridWidgetProvider.HEADER_HEIGHT_DP, 0f)
    }

    @Test fun `body geometry shares the header token and absorbs freed space`() {
        val density = app.resources.displayMetrics.density
        val hPx = (200 * density).roundToInt()
        val (bodyH, _) = WeekGridWidgetProvider.weekGridBodyGeomPx(hPx, density, 10)
        val expected = ((200f - 12f - WeekGridWidgetProvider.HEADER_HEIGHT_DP) * density).roundToInt()
        assertEquals("bodyH = 总高 - outerPad 6x2 - 表头 token", expected, bodyH)
        val oldBody = ((200f - 12f - 56f) * density).roundToInt()
        assertTrue("压缩必须把省出的高度让给课程主体", bodyH > oldBody)
    }

    @Test fun `rendered corner header card ends at outerPad plus header token`() {
        val density = app.resources.displayMetrics.density
        val timeJson = """[{"node":1,"start":"08:00","end":"08:45"}]"""
        val monday = LocalDate.of(2026, 11, 9)
        val courses = listOf(CourseEntity(
            id = 1, groupId = "hdr-sun", tableId = 1L, courseName = "周日课", room = "A101",
            day = 7, startNode = 1, step = 1, startWeek = 1, endWeek = 16, color = "#FF6750A4"))
        val days = (1..7).map { dow ->
            DayData(
                date = monday.with(if (dow == 7) DayOfWeek.SUNDAY else DayOfWeek.of(dow)),
                dayOfWeek = dow,
                courses = if (dow == 7) courses else emptyList(),
                timeJson = timeJson,
            )
        }
        val data = WeekData(days = days, hasTable = true, showDate = true)

        val wPx = (300 * density).roundToInt()
        val hPx = (300 * density).roundToInt()
        val bmp = WeekGridWidgetProvider.renderBitmap(app, data, wPx, hPx)
        try {
            val scheme = resolveSchemePublic(app, ThemePresets.KEY_DEFAULT, false)
            fun argb(v: androidx.compose.ui.graphics.Color): Int =
                (0xFF shl 24) or ((v.red * 255).toInt() shl 16) or
                    ((v.green * 255).toInt() shl 8) or (v.blue * 255).toInt()
            val surface = argb(scheme.surface)
            val container = argb(scheme.surfaceContainer)
            assertTrue("surface 与容器底色必须可区分", surface != container)
            // 探测线 = 角落格水平中心 (距左右圆角半径 14dp=28px 远, 不受圆角影响)
            val probeX = ((6f + 20f) * density).roundToInt()
            var bottom = ((6f + 20f) * density).roundToInt()
            while (bottom < hPx && bmp.getPixel(probeX, bottom) == surface) bottom++
            val expected = ((6f + WeekGridWidgetProvider.HEADER_HEIGHT_DP) * density).roundToInt()
            assertEquals("角落表头卡底边像素 = outerPad + headH", expected, bottom)
        } finally {
            bmp.recycle()
        }
    }
}
