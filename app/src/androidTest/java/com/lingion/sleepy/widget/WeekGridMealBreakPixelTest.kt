package com.lingion.sleepy.widget

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.MealBreakPreviewFixture
import com.lingion.sleepy.util.AppPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/** Pixel contract for the real Canvas renderer, also exports a local PNG preview. */
@RunWith(AndroidJUnit4::class)
class WeekGridMealBreakPixelTest {
    @Test
    fun exportsFiveDayWidgetPreview() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val oldSeparators = AppPrefs.isGridShowSeparators(context)
        val oldSpacing = AppPrefs.isGridLongBreakSpacing(context)
        AppPrefs.setGridShowSeparators(context, true)
        AppPrefs.setGridLongBreakSpacing(context, true)
        try {
            val monday = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(1)
            val days = (1..5).map { day ->
                DayData(monday.plusDays((day - 1).toLong()), day,
                    MealBreakPreviewFixture.courses.filter { it.day == day }, MealBreakPreviewFixture.timeJson)
            }
            val bitmap = WeekGridWidgetProvider.renderBitmap(context,
                WeekData(days = days, hasTable = true, visibleDays = setOf(1, 2, 3, 4, 5)), 1080, 1800)
            try {
                FileOutputStream(File(context.cacheDir, "sleepy-widget-five-day.png")).use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally {
                bitmap.recycle()
            }
        } finally {
            AppPrefs.setGridShowSeparators(context, oldSeparators)
            AppPrefs.setGridLongBreakSpacing(context, oldSpacing)
        }
    }

    @Test
    fun longBreakHasTwoHorizontalEdgesAndNoDayDividerInside() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val oldSeparators = AppPrefs.isGridShowSeparators(context)
        val oldLongBreakSpacing = AppPrefs.isGridLongBreakSpacing(context)
        AppPrefs.setGridShowSeparators(context, true)
        AppPrefs.setGridLongBreakSpacing(context, true)

        val timeJson = """[
            {"node":1,"start":"08:00","end":"08:45"},
            {"node":2,"start":"09:00","end":"09:45"},
            {"node":3,"start":"10:00","end":"10:45"},
            {"node":4,"start":"14:00","end":"14:45"}
        ]"""
        val courses = listOf(
            course(id = 1, name = "上午课程", node = 1),
            course(id = 2, name = "下午课程", node = 4)
        )
        val monday = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(1)
        val data = WeekData(
            days = listOf(DayData(monday, 1, courses, timeJson)),
            hasTable = true,
            visibleDays = setOf(1)
        )
        val width = 900
        val height = 1500
        val density = context.resources.displayMetrics.density
        val dp = { value: Float -> (value * density).roundToInt() }
        var bitmap: Bitmap? = null
        try {
            val rendered = WeekGridWidgetProvider.renderBitmap(context, data, width, height)
            bitmap = rendered

            val outerPad = dp(6f)
            val timeW = dp(40f)
            val gapW = dp(2.5f)
            val gapH = dp(1.5f).toFloat()
            val headH = dp(56f)
            val bodyTop = (outerPad + headH).toFloat()
            val bodyH = height - dp(6f) * 2 - headH
            val totalBodyGap = dp(1.5f) * (4 + 1) + dp(4f)
            val slotH = ((bodyH - totalBodyGap) / 4).toFloat().coerceAtLeast(dp(3f).toFloat())
            val mealExtra = dp(4f).toFloat()
            fun rowTop(index: Int): Float = bodyTop + gapH + index * (slotH + gapH) +
                if (index >= 3) mealExtra else 0f

            val bandTop = rowTop(2) + slotH
            val bandBottom = rowTop(3)
            val dividerX = outerPad + timeW + gapW
            val bodyW = width - outerPad * 2
            val totalGapW = gapW * 2
            val dayW = ((bodyW - timeW - totalGapW) / 1).toFloat().coerceAtLeast(dp(20f).toFloat())
            val rightDividerX = (dividerX + dayW).roundToInt()
            val beforeBreakY = (rowTop(1) + slotH / 2f).roundToInt()
            val insideBreakY = ((bandTop + bandBottom) / 2f).roundToInt()
            val horizontalProbeX = outerPad + timeW + (gapW / 2f).roundToInt()

            assertTrue("test schedule must produce a visible meal band", bandBottom > bandTop)
            for (columnDividerX in listOf(dividerX, rightDividerX)) {
                assertNotEquals(
                    "day divider should be visible in ordinary rows",
                    rendered.getPixel(columnDividerX, beforeBreakY + 3),
                    rendered.getPixel(columnDividerX + 3, beforeBreakY + 3)
                )
                assertEquals(
                    "day divider must stop inside the meal band",
                    rendered.getPixel(columnDividerX - 3, insideBreakY),
                    rendered.getPixel(columnDividerX, insideBreakY)
                )
            }
            assertNotEquals(
                "meal band needs a top boundary",
                rendered.getPixel(horizontalProbeX, bandTop.roundToInt()),
                rendered.getPixel(horizontalProbeX, bandTop.roundToInt() - 3)
            )
            assertNotEquals(
                "meal band needs a bottom boundary",
                rendered.getPixel(horizontalProbeX, bandBottom.roundToInt()),
                rendered.getPixel(horizontalProbeX, bandBottom.roundToInt() + 3)
            )

            val preview = File(context.cacheDir, "sleepy-meal-break-grid.png")
            FileOutputStream(preview).use { output ->
                assertTrue("PNG preview must be written", rendered.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            println("MEAL_BREAK_PREVIEW=${preview.absolutePath}")
        } finally {
            bitmap?.recycle()
            AppPrefs.setGridShowSeparators(context, oldSeparators)
            AppPrefs.setGridLongBreakSpacing(context, oldLongBreakSpacing)
        }
    }

    private fun course(id: Long, name: String, node: Int) = CourseEntity(
        id = id,
        groupId = "meal-break-pixel-$id",
        tableId = 1L,
        courseName = name,
        day = 1,
        startNode = node,
        step = 1,
        startWeek = 1,
        endWeek = 16,
        color = "#FF6750A4"
    )
}
