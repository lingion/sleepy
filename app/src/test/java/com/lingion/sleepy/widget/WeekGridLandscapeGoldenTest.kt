package com.lingion.sleepy.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

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
            assertGolden(bitmap, "week_grid_landscape_light.png")
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
            assertGolden(bitmap, "week_grid_landscape_dark.png")
        } finally {
            bitmap.recycle()
        }
    }

    private fun assertGolden(rendered: Bitmap, name: String) {
        val png = ByteArrayOutputStream().use { out ->
            rendered.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        val root = System.getProperty("sleepy.test.root") ?: error("sleepy.test.root unset")
        val golden = File(root, "app/src/test/golden/$name")
        if (System.getenv("GOLDEN_RECORD") == "1") {
            golden.parentFile.mkdirs()
            golden.writeBytes(png)
            return
        }
        assertTrue("missing golden $golden — run with GOLDEN_RECORD=1", golden.exists())
        val expected = ImageIO.read(golden)
        val actual = ImageIO.read(java.io.ByteArrayInputStream(png))
        if (expected.width != actual.width || expected.height != actual.height) {
            fail("size drift: golden ${expected.width}x${expected.height}, rendered ${actual.width}x${actual.height}")
        }
        // 2% perceptual tolerance absorbs antialias noise across toolchain
        // bumps; real layout regressions blow far past it.
        var diff = 0
        for (y in 0 until actual.height) {
            for (x in 0 until actual.width) {
                val a = actual.getRGB(x, y)
                val b = expected.getRGB(x, y)
                val far = (Math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) > TOLERANCE) ||
                    (Math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) > TOLERANCE) ||
                    (Math.abs((a and 0xFF) - (b and 0xFF)) > TOLERANCE)
                if (far) diff++
            }
        }
        val ratio = diff.toDouble() / (actual.width * actual.height)
        if (ratio > MAX_DIFF_RATIO) {
            File.createTempFile("golden-actual-", ".png").let { f ->
                f.deleteOnExit()
                ImageIO.write(actual, "png", f)
                fail("pixel diff ${"%.2f".format(ratio * 100)}% > threshold on $name (actual dumped to ${f.absolutePath})")
            }
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
        private const val TOLERANCE = 8
        private const val MAX_DIFF_RATIO = 0.02
    }
}
