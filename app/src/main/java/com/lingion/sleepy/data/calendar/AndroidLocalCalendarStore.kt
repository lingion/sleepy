package com.lingion.sleepy.data.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.util.TimeZone

internal class AndroidLocalCalendarStore(private val context: Context) : LocalCalendarStore {
    private val resolver = context.contentResolver
    private val accountName = context.packageName + ".account"
    private val accountType = CalendarContract.ACCOUNT_TYPE_LOCAL
    private val calendars = CalendarContract.Calendars.CONTENT_URI
    // Metadata writes require sync-adapter semantics AND calendar runtime permissions.
    private val syncUri = calendars.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, accountType)
        .build()

    override fun hasPermissions() = SystemCalendarManager.hasCalendarPermissions(context)

    override fun queryWritable(): List<LocalCalendarRow> = query(
        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= ? AND " +
            CalendarContract.Calendars.VISIBLE + " = 1 AND " + CalendarContract.Calendars.DELETED + " = 0",
        arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
    )

    override fun queryOwned(): List<LocalCalendarRow> = query(
        CalendarContract.Calendars.ACCOUNT_NAME + " = ? AND " + CalendarContract.Calendars.ACCOUNT_TYPE + " = ?",
        arrayOf(accountName, accountType)
    )

    private fun query(selection: String, args: Array<String>): List<LocalCalendarRow> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.DELETED,
            CalendarContract.Calendars.VISIBLE, CalendarContract.Calendars.SYNC_EVENTS
        )
        val cursor = checkNotNull(resolver.query(calendars, projection, selection, args,
            CalendarContract.Calendars.IS_PRIMARY + " DESC, " + CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " COLLATE NOCASE"))
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val name = it.getString(2).orEmpty()
                    add(LocalCalendarRow(
                        SystemCalendarInfo(it.getLong(0), it.getString(1).orEmpty().ifBlank { name },
                            name, it.getString(3).orEmpty()),
                        it.getInt(4) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                        it.getInt(5) != 0, it.getInt(6) != 0, it.getInt(7) != 0,
                        name == accountName && it.getString(3) == accountType
                    ))
                }
            }
        }
    }

    override fun enable(row: LocalCalendarRow): Boolean = resolver.update(
        ContentUris.withAppendedId(syncUri, row.info.id), ContentValues().apply {
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }, null, null
    ) == 1

    override fun insert(): SystemCalendarInfo? {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, accountType)
            put(CalendarContract.Calendars.NAME, accountName)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Sleepy")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF4285F4.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, accountName)
            put(CalendarContract.Calendars.VISIBLE, 1)
            // CalendarProvider excludes SYNC_EVENTS=0 calendars from Instances expansion.
            // ACCOUNT_TYPE_LOCAL identifies the calendar as local without a remote account.
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        return resolver.insert(syncUri, values)?.let { uri ->
            ContentUris.parseId(uri).takeIf { it > 0 }?.let { id ->
                SystemCalendarInfo(id, "Sleepy", accountName, accountType)
            }
        }
    }
}
