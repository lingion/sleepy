package com.lingion.sleepy.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-mdpi", application = SleepyApp::class)
class WeekGridWideRenderTest {
    private val monday = LocalDate.of(2026, 9, 28)
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun bindApplication() {
        SleepyApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, context)
    }

    @Test fun verticalHasSevenColumnsFourFullRowsAndNoWeekdayHeader() {
        val layout = WeekGridWideRenderer.layout(context, fixture(), WeekGridWideSettings(), monday.atTime(7, 0), 300, 160)
        assertEquals(7, layout.dates.size)
        assertEquals(4, layout.window.count)
        assertEquals(0, layout.window.startIndex)
        assertEquals(6f, layout.body.top, .01f)
        assertTrue(layout.cards.all { it.bounds.top >= layout.body.top && it.bounds.bottom <= layout.body.bottom })
    }

    @Test fun fourPeriodCourseHasVisibleNameAndRoomInLastSingleRow() {
        val data = fixture()
        val now = monday.atTime(10, 45) // third individual period has just ended
        val layout = WeekGridWideRenderer.layout(context, data, WeekGridWideSettings(), now, 300, 160)
        assertEquals(3, layout.window.startIndex)
        val card = layout.cards.single()
        assertTrue(card.bounds.height() < 40f)
        assertFalse(card.title.text.isEmpty())
        assertFalse(card.room.text.isEmpty())
        assertTrue(card.title.baseline > card.bounds.top)
        assertTrue(card.room.baseline > card.title.baseline)
        assertTrue(card.room.baseline <= card.bounds.bottom)
        val bitmap = WeekGridWideRenderer.renderBitmap(context, data, WeekGridWideSettings(), now, 300, 160)
        // Compare production pixels against an identical card rendered without text.
        val empty = fixture(name = "", room = "")
        val background = WeekGridWideRenderer.renderBitmap(context, empty, WeekGridWideSettings(), now, 300, 160)
        assertTrue("title must actually draw ink in its surviving row", changedPixels(bitmap, background, card.title.bounds) > 0)
        assertTrue("room must actually draw ink in its surviving row", changedPixels(bitmap, background, card.room.bounds) > 0)
        export(bitmap, "vertical-last-row.png")
    }

    @Test fun fourSingleCoursesAreFourCardsInsteadOfOneLongCourse() {
        val data = fixture().let { week -> week.copy(days = week.days.mapIndexed { i, day ->
            if (i == 0) day.copy(courses = (1..4).map { course(it.toLong(), "Course $it", it, 1, "A$it") }) else day
        }) }
        val layout = WeekGridWideRenderer.layout(context, data, WeekGridWideSettings(), monday.atTime(7, 0), 300, 160)
        assertEquals(4, layout.cards.size)
        assertEquals(4, layout.cards.map { it.course.id }.distinct().size)
    }

    @Test fun horizontalSidesPreserveDateOrderAndTransposeTimeAxis() {
        val dates = WeekGridWideWindowCore.visibleDates(monday.plusDays(6), WeekGridWideMode.HORIZONTAL)
        val data = fixture().copy(days = dates.map { date -> DayData(date, date.dayOfWeek.value,
            listOf(course(date.toEpochDay(), "横向课", 1, 4, "A201")), TimeTableUtils.DEFAULT_TIME_JSON) })
        for (side in WeekGridWideLabelSide.entries) {
            val settings = WeekGridWideSettings(WeekGridWideMode.HORIZONTAL, side)
            val layout = WeekGridWideRenderer.layout(context, data, settings, dates.first().atTime(9, 0), 300, 160)
            assertEquals(dates, layout.dates)
            assertEquals(3, layout.cards.size)
            assertTrue(layout.body.top > 6f)
            assertTrue(layout.cards.first().bounds.width() > layout.cards.first().bounds.height())
            if (side == WeekGridWideLabelSide.LEFT) assertTrue(layout.body.left > 6f) else assertTrue(layout.body.right < 294f)
            export(WeekGridWideRenderer.renderBitmap(context, data, settings, dates.first().atTime(9, 0), 600, 320), "horizontal-${side.name.lowercase()}.png")
        }
    }

    @Test fun largerHeightAddsOnlyCompleteRowsAndDarkModeRenders() {
        val layout = WeekGridWideRenderer.layout(context, fixture(), WeekGridWideSettings(), monday.atTime(7, 0), 300, 240)
        assertTrue(layout.window.count > 4)
        val bitmap = WeekGridWideRenderer.renderBitmap(context, fixture().copy(isDark = true), WeekGridWideSettings(), monday.atTime(7, 0), 300, 160)
        assertEquals(300, bitmap.width)
        export(bitmap, "vertical-dark.png")
    }

    @Test fun defaultPreviewUsesProductionRendererAtSixPixelsPerDp() {
        val data = fixture().let { week -> week.copy(days = week.days.mapIndexed { index, day ->
            day.copy(courses = when (index) {
                0 -> listOf(course(1, "高等数学", 1, 4, "A201"))
                1 -> (1..4).map { course((10 + it).toLong(), listOf("英语", "物理", "程序设计", "体育")[it - 1], it, 1, "B20$it") }
                2 -> listOf(course(21, "线性代数", 1, 2, "C101"), course(22, "工程制图", 3, 2, "C102"))
                3 -> listOf(course(31, "大学物理", 2, 2, "实验室"))
                4 -> listOf(course(41, "程序设计", 1, 2, "机房"), course(42, "英语", 4, 1, "A301"))
                else -> emptyList()
            })
        }) }
        val metrics = context.resources.displayMetrics
        val old = metrics.density
        try {
            metrics.density = 6f
            export(WeekGridWideRenderer.renderBitmap(context, data, WeekGridWideSettings(), monday.atTime(7, 0), 1800, 960), "preview.png")
            export(WeekGridWideRenderer.renderBitmap(context, data,
                WeekGridWideSettings(WeekGridWideMode.HORIZONTAL), monday.atTime(7, 0), 1800, 960), "preview-horizontal.png")
        } finally { metrics.density = old }
    }

    @Test fun visibleDateSelectionUsesEachActualDateAcrossSemesterBoundaries() {
        val table = com.lingion.sleepy.data.entity.TimeTableEntity(id = 1, name = "T", startDate = "2026-09-28", maxWeek = 1)
        val recurring = course(1, "Recurring", 1, 1, "A")
        val fixedMonday = course(2, "Fixed", 1, 1, "B").copy(fixedDate = "2026-09-28")
        val futureFixed = course(3, "Future", 1, 1, "C").copy(fixedDate = "2026-10-12")
        val rows = listOf(recurring, fixedMonday, futureFixed)
        fun source(today: LocalDate): WidgetWeekSource {
            val display = com.lingion.sleepy.util.WeekDisplayResolver.resolve(table.startDate, table.maxWeek, today.atTime(8, 0), rows, table.timeJson, false)
            return WidgetWeekSource(table, rows, display)
        }
        fun selected(s: WidgetWeekSource, date: LocalDate) = WeekGridWideWidgetProvider.coursesForDate(s, date)
        assertEquals(listOf(1L), selected(source(monday.minusWeeks(1)), monday.minusWeeks(1)).map { it.id })
        assertEquals(listOf(1L, 2L), selected(source(monday.plusWeeks(1)), monday).map { it.id }.sorted())
        assertEquals(listOf(3L), selected(source(monday.plusWeeks(2)), monday.plusWeeks(2)).map { it.id })
    }

    @Test fun partialInvalidTimesKeepPhysicalRowsAndStopSchedulingAtMissingEnd() {
        val json = """[{"node":1,"start":"08:00","end":"08:45"},{"node":3,"start":"bad","end":"09:40"},{"node":5,"start":"10:00","end":""},{"node":7,"start":"11:00","end":"11:45"},{"node":9,"start":"12:00","end":"12:45"},{"node":11,"start":"13:00","end":"13:45"}]"""
        val periods = WeekGridWideRenderer.periods(json)
        assertEquals(listOf(1, 3, 5, 7, 9, 11), periods.map { it.node })
        assertEquals(WidePeriod(3, null, 580), periods[1])
        assertEquals(WidePeriod(5, 600, null), periods[2])
        assertEquals(monday.atTime(8, 45), WidgetBoundaryScheduler.nextWidePeriodBoundary(periods, monday.atTime(8, 0), 4))
        assertNull(WidgetBoundaryScheduler.nextWidePeriodBoundary(periods, monday.atTime(9, 40), 4))
        val data = fixture().let { week -> week.copy(days = week.days.map { it.copy(timeJson = json) }) }
        val layout = WeekGridWideRenderer.layout(context, data, WeekGridWideSettings(), monday.atTime(8, 0), 300, 160)
        assertEquals(4, layout.window.count)
        assertEquals(6, layout.periods.size)
    }

    @Test fun customTimeUsesFractionalPhysicalRowGeometryInBothModes() {
        val custom = course(1, "Custom", 1, 1, "A201").copy(ownTime = true, isIrregularTime = true, startTime = "08:20", endTime = "08:30")
        val data = fixture().let { week -> week.copy(days = week.days.mapIndexed { i, day -> day.copy(courses = if (i == 0) listOf(custom) else emptyList()) }) }
        for (mode in WeekGridWideMode.entries) {
            val layout = WeekGridWideRenderer.layout(context, data, WeekGridWideSettings(mode), monday.atTime(7, 0), 300, 160)
            val card = layout.cards.single()
            val start = if (mode == WeekGridWideMode.VERTICAL) card.bounds.top - layout.body.top else card.bounds.left - layout.body.left
            val size = if (mode == WeekGridWideMode.VERTICAL) card.bounds.height() else card.bounds.width()
            assertEquals(layout.periodExtent * 20f / 45f + 1f, start, .1f)
            assertEquals(layout.periodExtent * 10f / 45f - 2f, size, .1f)
        }
    }

    @Test fun narrowConflictCardsDrawARealTitleGlyphAndRoom() {
        val first = course(1, "Mathematics", 1, 1, "A201")
        val second = course(2, "Physics", 1, 1, "B202")
        fun data(a: CourseEntity, b: CourseEntity) = fixture().let { week -> week.copy(days = week.days.mapIndexed { i, day -> day.copy(courses = if (i == 0) listOf(a, b) else emptyList()) }) }
        val populated = data(first, second)
        val layout = WeekGridWideRenderer.layout(context, populated, WeekGridWideSettings(), monday.atTime(7, 0), 300, 160)
        assertEquals(2, layout.cards.size)
        val bitmap = WeekGridWideRenderer.renderBitmap(context, populated, WeekGridWideSettings(), monday.atTime(7, 0), 300, 160)
        val blank = WeekGridWideRenderer.renderBitmap(context, data(first.copy(courseName = "", room = ""), second.copy(courseName = "", room = "")), WeekGridWideSettings(), monday.atTime(7, 0), 300, 160)
        for (card in layout.cards) {
            assertTrue("actual title character required: ${card.title.text}", card.title.text.any { it.isLetterOrDigit() })
            assertTrue("actual room character required: ${card.room.text}", card.room.text.any { it.isLetterOrDigit() })
            assertTrue(changedPixels(bitmap, blank, card.title.bounds) > 0)
            assertTrue(changedPixels(bitmap, blank, card.room.bounds) > 0)
        }
    }

    private fun changedPixels(a: Bitmap, b: Bitmap, bounds: android.graphics.RectF): Int {
        var changed = 0
        for (y in bounds.top.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(a.height))
            for (x in bounds.left.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(a.width))
                if (a.getPixel(x, y) != b.getPixel(x, y)) changed++
        return changed
    }
    private fun export(bitmap: Bitmap, name: String) {
        val file = File("build/reports/week-grid-wide/$name").apply { requireNotNull(parentFile).mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun fixture(name: String = "高等数学", room: String = "A201"): WeekData = WeekData(
        days = (0L..6L).map { i -> DayData(monday.plusDays(i), i.toInt() + 1,
            if (i == 0L) listOf(course(1, name, 1, 4, room)) else emptyList(), TimeTableUtils.DEFAULT_TIME_JSON) },
        hasTable = true,
    )
    private fun course(id: Long, name: String, node: Int, step: Int, room: String) = CourseEntity(
        id = id, groupId = "g$id", tableId = 1, courseName = name, day = 1, startNode = node,
        step = step, startWeek = 1, endWeek = 16, room = room, color = "#FF6750A4",
    )
}
