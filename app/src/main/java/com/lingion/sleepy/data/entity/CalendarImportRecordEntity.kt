package com.lingion.sleepy.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Tracks only calendar events created by Sleepy so a later sync can avoid duplicates. */
@Entity(
    tableName = "calendar_import_records",
    indices = [
        Index(value = ["tableId", "courseId", "eventDate", "calendarId"], unique = true),
        Index(value = ["tableId"]),
        Index(value = ["batchId"])
    ]
)
data class CalendarImportRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "tableId") val tableId: Long,
    @ColumnInfo(name = "courseId") val courseId: Long,
    @ColumnInfo(name = "eventDate") val eventDate: String,
    @ColumnInfo(name = "calendarId") val calendarId: Long,
    @ColumnInfo(name = "eventId") val eventId: Long,
    @ColumnInfo(name = "batchId") val batchId: String,
    @ColumnInfo(name = "fingerprint") val fingerprint: String,
    @ColumnInfo(name = "alarmRequested") val alarmRequested: Boolean,
    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis()
)
