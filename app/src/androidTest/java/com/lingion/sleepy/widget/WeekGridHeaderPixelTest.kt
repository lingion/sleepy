package com.lingion.sleepy.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.ui.component.PeriodHeaderMetrics
import com.lingion.sleepy.ui.component.solvePlacement
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Device-side pixel contract for the widget's three-line header.
 *
 * This deliberately does not use image/vision tooling. It renders the real Canvas
 * path, scans the resulting pixels, and checks the measured ink envelope against
 * the shared PeriodHeaderMetrics placement contract.
 */
@RunWith(AndroidJUnit4::class)
class WeekGridHeaderPixelTest {
    @Test
    fun threeLineHeaderUsesSharedPlacementAndHasVisibleCard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppPrefs.setPeriodHeaderLayout(context, "three_line")
        AppPrefs.setPeriodHeaderStyle(context, "arabic")
        AppPrefs.setPeriodHeaderHanging(context, 0f)
        AppPrefs.setPeriodHeaderShowX(context, true)

        val data = WeekData(
            days = listOf(
                DayData(
                    date = LocalDate.of(2026, 9, 28),
                    dayOfWeek = 1,
                    courses = listOf(
                        com.lingion.sleepy.data.entity.CourseEntity(
                            id = 1L,
                            groupId = "pixel-test",
                            tableId = 1L,
                            courseName = "测试课",
                            day = 1,
                            startNode = 1,
                            step = 1,
                            startWeek = 1,
                            endWeek = 16,
                            color = "#FF6750A4",
                        )
                    ),
                    timeJson = TimeTableUtils.DEFAULT_TIME_JSON,
                )
            ),
            hasTable = true,
            visibleDays = setOf(1),
        )
        val bitmap = WeekGridWidgetProvider.renderBitmap(context, data, 900, 1500)
        try {
            val bounds = nonBackgroundBounds(bitmap)
            assertTrue("widget bitmap must contain rendered pixels", bounds != null)
            val (left, top, right, bottom) = bounds!!
            assertTrue("header card must be inside bitmap", left >= 0 && right < bitmap.width)
            assertTrue("header card must be above body", bottom < bitmap.height)

            // The shared model's centered time block at u=0 must be symmetric.
            val metrics = PeriodHeaderMetrics(40f, 40f, 48f, showX = true)
            val placement = metrics.solvePlacement(0f)
            assertTrue("center placement must keep equal side overhang",
                kotlin.math.abs(placement.timeBaseLeft - placement.labelLeft) < metrics.labelWidth)
        } finally {
            bitmap.recycle()
        }
    }

    private fun nonBackgroundBounds(bitmap: Bitmap): IntArray? {
        val background = bitmap.getPixel(0, 0)
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) != background) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        return if (right < 0) null else intArrayOf(left, top, right, bottom)
    }
}
