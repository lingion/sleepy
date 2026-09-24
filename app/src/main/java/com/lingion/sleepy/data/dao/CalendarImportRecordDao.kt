package com.lingion.sleepy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingion.sleepy.data.entity.CalendarImportRecordEntity

@Dao
interface CalendarImportRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CalendarImportRecordEntity): Long

    @Query("SELECT * FROM calendar_import_records WHERE tableId = :tableId")
    suspend fun forTable(tableId: Long): List<CalendarImportRecordEntity>

    @Query("SELECT * FROM calendar_import_records WHERE tableId = :tableId AND courseId = :courseId AND eventDate = :eventDate AND calendarId = :calendarId LIMIT 1")
    suspend fun find(tableId: Long, courseId: Long, eventDate: String, calendarId: Long): CalendarImportRecordEntity?

    @Query("DELETE FROM calendar_import_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM calendar_import_records WHERE tableId = :tableId")
    suspend fun deleteForTable(tableId: Long)
}
