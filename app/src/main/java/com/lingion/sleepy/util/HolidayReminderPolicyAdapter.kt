package com.lingion.sleepy.util

import android.content.Context
import java.time.LocalDate

/** Android boundary for the pure holiday reminder policy. */
object HolidayReminderPolicyAdapter {
    suspend fun decide(ctx: Context, date: LocalDate, tableId: Long): HolidayReminderPolicy.Result {
        val yearData = HolidayManager.getYearData(ctx, date.year)
        val holidays = yearData.entries.asSequence()
            .filter { it.type == HolidayManager.TYPE_PUBLIC_HOLIDAY }
            .map { it.date }
            .toSet()
        val workdays = yearData.entries.asSequence()
            .filter { it.type == HolidayManager.TYPE_TRANSFER_WORKDAY }
            .map { it.date }
            .toSet()
        val transfers = AppPrefs.getHolidayTransfers(ctx, tableId)
        return HolidayReminderPolicy.decide(
            date = date,
            publicHolidays = holidays,
            transferWorkdays = workdays,
            transfers = transfers,
            rulesEnabled = AppPrefs.isHolidayReminderRulesEnabled(ctx),
            publicHolidayReminder = AppPrefs.isHolidayReminderPublicHolidayEnabled(ctx),
            transferHolidayReminder = AppPrefs.isHolidayReminderTransferHolidayEnabled(ctx),
            makeupWorkdayReminder = AppPrefs.isHolidayReminderMakeupWorkdayEnabled(ctx),
            ordinaryWeekendReminder = AppPrefs.isHolidayReminderOrdinaryWeekendEnabled(ctx),
            dataAvailable = yearData.available,
        )
    }
}
