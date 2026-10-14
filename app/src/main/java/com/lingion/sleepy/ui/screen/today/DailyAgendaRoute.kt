package com.lingion.sleepy.ui.screen.today

import java.time.LocalDate

/** Calendar dates, rather than relative day offsets, keep old notification taps stable. */
internal object DailyAgendaRoute {
    const val EXTRA_DATE = "agenda_date"
    fun identity(date: LocalDate): String = "sleepy://daily-agenda/$date"
    fun parse(date: String?): LocalDate? = date?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    }
}
