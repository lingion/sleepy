package com.lingion.sleepy.util

import java.time.DayOfWeek
import java.time.LocalDate

/** Pure date classification and reminder gating for holiday reminders. */
object HolidayReminderPolicy {
    enum class Category {
        NORMAL_WEEKDAY,
        PUBLIC_HOLIDAY,
        TRANSFER_HOLIDAY,
        MAKEUP_WORKDAY,
        ORDINARY_WEEKEND,
        DATA_UNAVAILABLE,
    }

    data class Result(
        val category: Category,
        val allowReminder: Boolean,
    )

    fun decide(
        date: LocalDate,
        publicHolidays: Set<LocalDate>,
        transferWorkdays: Set<LocalDate>,
        transfers: List<HolidayTransferEntry>,
        rulesEnabled: Boolean,
        publicHolidayReminder: Boolean,
        transferHolidayReminder: Boolean,
        makeupWorkdayReminder: Boolean,
        ordinaryWeekendReminder: Boolean,
        dataAvailable: Boolean,
    ): Result {
        if (!rulesEnabled) {
            return Result(category = classify(date, publicHolidays, transferWorkdays, transfers), allowReminder = true)
        }
        if (!dataAvailable) {
            return Result(Category.DATA_UNAVAILABLE, allowReminder = true)
        }

        val category = classify(date, publicHolidays, transferWorkdays, transfers)
        val allowReminder = when (category) {
            Category.NORMAL_WEEKDAY -> true
            Category.PUBLIC_HOLIDAY -> publicHolidayReminder
            Category.TRANSFER_HOLIDAY -> transferHolidayReminder
            Category.MAKEUP_WORKDAY -> makeupWorkdayReminder
            Category.ORDINARY_WEEKEND -> ordinaryWeekendReminder
            Category.DATA_UNAVAILABLE -> true
        }
        return Result(category, allowReminder)
    }

    private fun classify(
        date: LocalDate,
        publicHolidays: Set<LocalDate>,
        transferWorkdays: Set<LocalDate>,
        transfers: List<HolidayTransferEntry>,
    ): Category = when {
        date in transferWorkdays -> Category.MAKEUP_WORKDAY
        date in publicHolidays && transfers.any { it.sourceDate == date } -> Category.TRANSFER_HOLIDAY
        date in publicHolidays -> Category.PUBLIC_HOLIDAY
        date.dayOfWeek.value >= DayOfWeek.SATURDAY.value -> Category.ORDINARY_WEEKEND
        else -> Category.NORMAL_WEEKDAY
    }
}
