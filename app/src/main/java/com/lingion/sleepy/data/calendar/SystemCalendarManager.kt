package com.lingion.sleepy.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CalendarImportRecordEntity
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.HolidayRangeOps
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class CalendarImportRange { NEXT_WEEK, NEXT_MONTH, SEMESTER }

data class SystemCalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String
)

data class CalendarImportOptions(
    val calendarId: Long,
    val range: CalendarImportRange,
    val applyHolidayTransfers: Boolean,
    val reminderMinutes: Int?,
    val firstPeriodAlarmEnabled: Boolean,
    val firstPeriodAlarmMinutes: Int
)

data class CalendarImportPreviewRow(
    val date: LocalDate,
    val start: String,
    val end: String,
    val courseName: String,
    val teacher: String,
    val room: String,
    val week: Int,
    val transferred: Boolean,
    val firstPeriodAlarm: Boolean
)

data class CalendarImportPreview(
    val rows: List<CalendarImportPreviewRow>,
    val skippedInvalidTime: Int,
    val startDate: LocalDate,
    val endDateExclusive: LocalDate
)

data class CalendarImportResult(
    val inserted: Int = 0,
    val updated: Int = 0,
    val unchanged: Int = 0,
    val skippedEdited: Int = 0,
    val skippedInvalidTime: Int = 0,
    val alarmFallback: Int = 0,
    val errors: Int = 0
)

data class CalendarDeleteResult(val deleted: Int, val skippedEdited: Int, val missing: Int)

/** Direct Android Calendar Provider integration. The event-date expansion stays inside this flow. */
object SystemCalendarManager {
    private const val SLEEPY_MARKER_PREFIX = "Sleepy import key:"
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private data class EventSpec(
        val course: CourseEntity,
        val date: LocalDate,
        val week: Int,
        val start: LocalTime,
        val end: LocalTime,
        val title: String,
        val location: String,
        val description: String,
        val key: String,
        val reminderMinutes: Int?,
        val wantsAlarm: Boolean
    )

    private data class ExistingEvent(
        val id: Long,
        val calendarId: Long,
        val title: String,
        val location: String,
        val description: String,
        val startMillis: Long,
        val endMillis: Long,
        val timezone: String
    )

    fun hasCalendarPermissions(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun writableCalendars(context: Context): List<SystemCalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasCalendarPermissions(context)) return@withContext emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE
        )
        val result = mutableListOf<SystemCalendarInfo>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1",
                arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    result += SystemCalendarInfo(
                        id = cursor.getLong(0),
                        displayName = cursor.getString(1).orEmpty().ifBlank { cursor.getString(2).orEmpty() },
                        accountName = cursor.getString(2).orEmpty(),
                        accountType = cursor.getString(3).orEmpty()
                    )
                }
            }
        }
        result
    }

    fun buildPreview(
        context: Context,
        table: TimeTableEntity,
        courses: List<CourseEntity>,
        options: CalendarImportOptions,
        today: LocalDate = LocalDate.now()
    ): CalendarImportPreview {
        val (from, until) = selectedDateRange(table, options.range, today)
        val (specs, invalid) = buildEventSpecs(context, table, courses, options, from, until)
        val alarms = firstPeriodAlarmKeys(specs)
        val rows = specs.map { spec ->
            CalendarImportPreviewRow(spec.date, spec.start.format(timeFormatter), spec.end.format(timeFormatter),
                spec.title, spec.course.teacher, spec.location, spec.week,
                spec.description.contains("调课安排："), alarms[spec.date] == spec.key)
        }
        return CalendarImportPreview(rows, invalid, from, until)
    }

    suspend fun import(
        context: Context,
        table: TimeTableEntity,
        courses: List<CourseEntity>,
        options: CalendarImportOptions,
        batchId: String
    ): CalendarImportResult = withContext(Dispatchers.IO) {
        if (!hasCalendarPermissions(context)) return@withContext CalendarImportResult(errors = 1)
        val calendars = writableCalendars(context)
        val calendar = calendars.firstOrNull { it.id == options.calendarId }
            ?: return@withContext CalendarImportResult(errors = 1)
        val preview = buildPreview(context, table, courses, options)
        val startInclusive = preview.startDate
        val endExclusive = preview.endDateExclusive
        val (specs, _) = buildEventSpecs(context, table, courses, options, startInclusive, endExclusive)
        val alarmKeys = firstPeriodAlarmKeys(specs)
        val db = AppDatabase.get(context)
        var inserted = 0; var updated = 0; var unchanged = 0; var skippedEdited = 0
        var skippedInvalid = preview.skippedInvalidTime; var alarmFallback = 0; var errors = 0
        val existingRecords = db.calendarImportRecordDao().forTable(table.id)
            .filter { it.calendarId == calendar.id && LocalDate.parse(it.eventDate) >= startInclusive && LocalDate.parse(it.eventDate) < endExclusive }
            .associateBy { "${it.courseId}:${it.eventDate}" }
        for (spec in specs) {
            val key = "${spec.course.id}:${DateUtils.fullDate(spec.date)}"
            val record = existingRecords[key]
            val wantsAlarm = alarmKeys[spec.date] == spec.key
            val desiredMethod = if (wantsAlarm) CalendarContract.Reminders.METHOD_ALARM
                else if (spec.reminderMinutes != null) CalendarContract.Reminders.METHOD_ALERT else null
            val minutes = if (wantsAlarm) options.firstPeriodAlarmMinutes.coerceIn(0, 999) else spec.reminderMinutes

            if (record != null) {
                val old = readEvent(context, record.eventId)
                if (old == null) {
                    db.calendarImportRecordDao().deleteById(record.id)
                } else if (fingerprint(old, readReminder(context, old.id)) != record.fingerprint) {
                    skippedEdited++
                    continue
                } else {
                    if (eventMatchesSpec(old, spec, calendar.id) &&
                        remindersMatch(readReminder(context, old.id), desiredMethod, minutes, wantsAlarm)) {
                        unchanged++
                        continue
                    }
                    val updatedEvent = writeEventValues(context, old.id, spec, calendar.id)
                    if (!updatedEvent) { errors++; continue }
                    val finalMethod = replaceReminder(context, old.id, desiredMethod, minutes, wantsAlarm)
                    if (wantsAlarm && finalMethod != CalendarContract.Reminders.METHOD_ALARM) alarmFallback++
                    if (desiredMethod != null && finalMethod == null) errors++
                    val after = readEvent(context, old.id)
                    if (after == null) { errors++; continue }
                    db.calendarImportRecordDao().upsert(record.copy(
                        batchId = batchId,
                        fingerprint = fingerprint(after, readReminder(context, old.id)),
                        alarmRequested = wantsAlarm
                    ))
                    updated++
                    continue
                }
            }

            // Recover an insert that succeeded immediately before a process death by finding its stable description marker.
            val recovered = findEventByMarker(context, calendar.id, spec.key)
            if (recovered != null) {
                if (!eventMatchesSpec(recovered, spec, calendar.id)) {
                    skippedEdited++
                    continue
                }
                val finalMethod = replaceReminder(context, recovered.id, desiredMethod, minutes, wantsAlarm)
                if (wantsAlarm && finalMethod != CalendarContract.Reminders.METHOD_ALARM) alarmFallback++
                if (desiredMethod != null && finalMethod == null) errors++
                db.calendarImportRecordDao().upsert(recordFor(table.id, spec, calendar.id, recovered.id, batchId,
                    fingerprint(recovered, readReminder(context, recovered.id)), wantsAlarm))
                unchanged++
                continue
            }

            val eventId = insertEvent(context, spec, calendar.id)
            if (eventId == null) { errors++; continue }
            val finalMethod = replaceReminder(context, eventId, desiredMethod, minutes, wantsAlarm)
            if (wantsAlarm && finalMethod != CalendarContract.Reminders.METHOD_ALARM) alarmFallback++
            if (desiredMethod != null && finalMethod == null) errors++
            val created = readEvent(context, eventId)
            if (created == null) { errors++; continue }
            db.calendarImportRecordDao().upsert(recordFor(table.id, spec, calendar.id, eventId, batchId,
                fingerprint(created, readReminder(context, eventId)), wantsAlarm))
            inserted++
        }
        CalendarImportResult(inserted, updated, unchanged, skippedEdited, skippedInvalid, alarmFallback, errors)
    }

    suspend fun deleteManagedForTable(context: Context, tableId: Long): CalendarDeleteResult = withContext(Dispatchers.IO) {
        if (!hasCalendarPermissions(context)) return@withContext CalendarDeleteResult(0, 0, 0)
        val dao = AppDatabase.get(context).calendarImportRecordDao()
        val records = dao.forTable(tableId)
        var deleted = 0; var edited = 0; var missing = 0
        records.forEach { record ->
            val event = readEvent(context, record.eventId)
            if (event == null) {
                dao.deleteById(record.id); missing++
            } else if (fingerprint(event, readReminder(context, event.id)) == record.fingerprint) {
                val count = runCatching {
                    context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id), null, null)
                }.getOrDefault(0)
                if (count > 0) { dao.deleteById(record.id); deleted++ }
            } else edited++
        }
        CalendarDeleteResult(deleted, edited, missing)
    }

    private fun insertEvent(context: Context, spec: EventSpec, calendarId: Long): Long? = runCatching {
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, eventValues(spec, calendarId))
            ?: return null
        ContentUris.parseId(uri)
    }.getOrNull()

    private fun writeEventValues(context: Context, id: Long, spec: EventSpec, calendarId: Long): Boolean = runCatching {
        context.contentResolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), eventValues(spec, calendarId), null, null) > 0
    }.getOrDefault(false)

    private fun eventValues(spec: EventSpec, calendarId: Long) = ContentValues().apply {
        val zone = ZoneId.systemDefault()
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, spec.title)
        put(CalendarContract.Events.EVENT_LOCATION, spec.location)
        put(CalendarContract.Events.DESCRIPTION, spec.description)
        put(CalendarContract.Events.DTSTART, spec.date.atTime(spec.start).atZone(zone).toInstant().toEpochMilli())
        put(CalendarContract.Events.DTEND, spec.date.atTime(spec.end).atZone(zone).toInstant().toEpochMilli())
        put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
        put(CalendarContract.Events.ALL_DAY, 0)
        put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
    }

    private fun replaceReminder(context: Context, eventId: Long, method: Int?, minutes: Int?, alarmRequested: Boolean): Int? {
        runCatching {
            context.contentResolver.delete(CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
        }
        if (method == null || minutes == null) return null
        fun insertReminder(candidate: Int): Boolean = runCatching {
            val values = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.METHOD, candidate)
                put(CalendarContract.Reminders.MINUTES, minutes)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values) != null
        }.getOrDefault(false)
        if (insertReminder(method)) return method
        if (alarmRequested && method == CalendarContract.Reminders.METHOD_ALARM && insertReminder(CalendarContract.Reminders.METHOD_ALERT)) {
            return CalendarContract.Reminders.METHOD_ALERT
        }
        return null
    }

    private fun readEvent(context: Context, id: Long): ExistingEvent? = runCatching {
        context.contentResolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), arrayOf(
            CalendarContract.Events._ID, CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.DESCRIPTION, CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND, CalendarContract.Events.EVENT_TIMEZONE
        ), null, null, null)?.use { c ->
            if (!c.moveToFirst()) null else ExistingEvent(
                c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getString(4).orEmpty(),
                c.getLong(5), c.getLong(6), c.getString(7).orEmpty()
            )
        }
    }.getOrNull()

    private fun readReminder(context: Context, eventId: Long): List<Pair<Int, Int>> = runCatching {
        context.contentResolver.query(CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.MINUTES),
            "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()), null)?.use { c ->
            buildList { while (c.moveToNext()) add(c.getInt(0) to c.getInt(1)) }.sortedWith(compareBy({ it.first }, { it.second }))
        }
    }.getOrNull() ?: emptyList()

    private fun findEventByMarker(context: Context, calendarId: Long, key: String): ExistingEvent? = runCatching {
        val marker = "$SLEEPY_MARKER_PREFIX$key"
        context.contentResolver.query(CalendarContract.Events.CONTENT_URI, arrayOf(
            CalendarContract.Events._ID, CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.DESCRIPTION, CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND, CalendarContract.Events.EVENT_TIMEZONE
        ), "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DESCRIPTION} LIKE ?",
            arrayOf(calendarId.toString(), "%$marker%"), null)?.use { c ->
            if (!c.moveToFirst()) null else ExistingEvent(c.getLong(0), c.getLong(1), c.getString(2).orEmpty(),
                c.getString(3).orEmpty(), c.getString(4).orEmpty(), c.getLong(5), c.getLong(6), c.getString(7).orEmpty())
        }
    }.getOrNull()

    private fun recordFor(tableId: Long, spec: EventSpec, calendarId: Long, eventId: Long, batchId: String,
                          fingerprint: String, alarmRequested: Boolean) = CalendarImportRecordEntity(
        tableId = tableId, courseId = spec.course.id, eventDate = DateUtils.fullDate(spec.date), calendarId = calendarId,
        eventId = eventId, batchId = batchId, fingerprint = fingerprint, alarmRequested = alarmRequested
    )

    private fun fingerprint(event: ExistingEvent, reminder: List<Pair<Int, Int>>): String {
        val raw = listOf(event.calendarId, event.title, event.location, event.description, event.startMillis,
            event.endMillis, event.timezone, reminder.joinToString(",") { "${it.first}:${it.second}" }).joinToString("\u001f")
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun eventMatchesSpec(event: ExistingEvent, spec: EventSpec, calendarId: Long): Boolean {
        val zone = ZoneId.systemDefault()
        return event.calendarId == calendarId && event.title == spec.title && event.location == spec.location &&
            event.description == spec.description &&
            event.startMillis == spec.date.atTime(spec.start).atZone(zone).toInstant().toEpochMilli() &&
            event.endMillis == spec.date.atTime(spec.end).atZone(zone).toInstant().toEpochMilli() && event.timezone == zone.id
    }

    private fun remindersMatch(
        actual: List<Pair<Int, Int>>,
        expectedMethod: Int?,
        expectedMinutes: Int?,
        allowAlarmFallback: Boolean
    ): Boolean = if (expectedMethod == null || expectedMinutes == null) {
        actual.isEmpty()
    } else {
        actual == listOf(expectedMethod to expectedMinutes) ||
            (allowAlarmFallback && expectedMethod == CalendarContract.Reminders.METHOD_ALARM &&
                actual == listOf(CalendarContract.Reminders.METHOD_ALERT to expectedMinutes))
    }

    private fun selectedDateRange(table: TimeTableEntity, range: CalendarImportRange, today: LocalDate): Pair<LocalDate, LocalDate> {
        val semesterStart = DateUtils.mondayOf(LocalDate.parse(table.startDate))
        val semesterEnd = semesterStart.plusWeeks(table.maxWeek.toLong())
        val start = maxOf(today, semesterStart)
        val requestedEnd = when (range) {
            CalendarImportRange.NEXT_WEEK -> today.plusDays(7)
            CalendarImportRange.NEXT_MONTH -> today.plusMonths(1)
            CalendarImportRange.SEMESTER -> semesterEnd
        }
        return start to minOf(requestedEnd, semesterEnd)
    }

    private fun buildEventSpecs(
        context: Context,
        table: TimeTableEntity,
        courses: List<CourseEntity>,
        options: CalendarImportOptions,
        from: LocalDate,
        until: LocalDate
    ): Pair<List<EventSpec>, Int> {
        if (!from.isBefore(until)) return emptyList<EventSpec>() to 0
        val transfers = if (options.applyHolidayTransfers) AppPrefs.getHolidayTransfers(context, table.id) else emptyList()
        val specs = mutableListOf<EventSpec>()
        var invalid = 0
        var date = from
        while (date.isBefore(until)) {
            val week = DateUtils.currentWeek(table.startDate, date)
            val classDay = if (transfers.isEmpty()) date.dayOfWeek.value
                else HolidayRangeOps.HolidayTransferOps.effectiveDayOfWeek(date, transfers)
            courses.asSequence().filter { it.day == classDay && it.inWeek(week) }.forEach { course ->
                val interval = TimeTableUtils.effectiveCourseTime(course.isIrregularTime, course.startTime,
                    course.endTime, course.startNode, course.step, table.timeJson)
                val start = interval?.first?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                val end = interval?.second?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                if (start == null || end == null || !end.isAfter(start)) {
                    invalid++
                } else {
                    val transferred = classDay != date.dayOfWeek.value
                    val marker = "$SLEEPY_MARKER_PREFIX${table.id}:${course.id}:${DateUtils.fullDate(date)}"
                    val description = buildString {
                        append("第${week}周 · 第${course.startNode}—${course.startNode + course.step - 1}节")
                        if (course.teacher.isNotBlank()) append("\n教师：${course.teacher}")
                        if (course.note.isNotBlank()) append("\n备注：${course.note}")
                        if (transferred) append("\n调课安排：按${DateUtils.chineseDay(classDay)}课程")
                        append("\n$marker")
                    }
                    specs += EventSpec(course, date, week, start, end, course.courseName, course.room,
                        description, marker.removePrefix(SLEEPY_MARKER_PREFIX),
                        options.reminderMinutes?.coerceIn(0, 999),
                        options.firstPeriodAlarmEnabled && course.startNode <= 1 &&
                            course.startNode + course.step > 1 && start.isBefore(LocalTime.NOON))
                }
            }
            date = date.plusDays(1)
        }
        return specs to invalid
    }

    private fun firstPeriodAlarmKeys(specs: List<EventSpec>): Map<LocalDate, String> =
        specs.filter { it.wantsAlarm }.groupBy { it.date }
            .mapValues { (_, rows) -> rows.minWith(compareBy<EventSpec>({ it.start }, { it.course.id })).key }
}
