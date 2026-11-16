package com.lingion.sleepy.ui.screen.imports

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ImportPeriodValidationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun course(node: Int = 1, step: Int = 1) = CourseEntity(
        tableId = 0, groupId = "", courseName = "实验", day = 2,
        startNode = node, step = step, startWeek = 13, endWeek = 16, color = "#123456",
    )
    private fun row(node: Int, start: String, end: String) = TimeTableUtils.TimeSlotRow(node, start, end)

    @Test fun overlappingRowsCannotBeConfirmedEvenWhenEachRowHasValidClocks() {
        val rows = listOf(row(1, "21:35", "22:20"), row(2, "21:45", "22:30"))
        assertEquals(
            TimeTableUtils.validatePeriodRows(rows).errorMessage(context),
            importPeriodValidationError(context, listOf(course()), rows),
        )
    }

    @Test fun outOfRangeClockCannotBeConfirmed() {
        val rows = listOf(row(1, "24:00", "25:00"))
        assertEquals(
            TimeTableUtils.validatePeriodRows(rows).errorMessage(context),
            importPeriodValidationError(context, listOf(course()), rows),
        )
    }

    @Test fun deletingARequiredPeriodStillReportsItBeforeSaving() {
        val rows = listOf(row(1, "08:30", "09:15"))
        assertEquals(context.getString(R.string.slot_time_required, 2L),
            importPeriodValidationError(context, listOf(course(1, 2)), rows))
    }

    @Test fun validTouchingPeriodsAndActualTimeCourseCanBeConfirmed() {
        val rows = listOf(row(1, "08:30", "09:15"), row(2, "09:15", "10:00"))
        val actual = course(13).copy(ownTime = true, startTime = "18:00", endTime = "19:00")
        assertNull(importPeriodValidationError(context, listOf(course(1, 2), actual), rows))
    }
}
