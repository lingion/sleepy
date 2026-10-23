package com.lingion.sleepy.data.repository

import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity

internal interface PeriodTableDeletionStore {
    suspend fun atomic(block: suspend () -> Boolean): Boolean
    suspend fun getPeriod(id: Long): PeriodTableEntity?
    suspend fun getBoundTables(id: Long): List<TimeTableEntity>
    suspend fun captureUndo()
    suspend fun updateTable(table: TimeTableEntity)
    suspend fun deletePeriod(id: Long)
}

/** Freeze the current shared schedule into each timetable before removing its source. */
internal suspend fun deletePeriodTableAtomically(
    store: PeriodTableDeletionStore,
    id: Long,
    onlyIfUnbound: Boolean = false,
): Boolean = store.atomic {
    val period = store.getPeriod(id) ?: return@atomic false
    val tables = store.getBoundTables(id)
    // Abandoning an unsaved editor must not delete a schedule adopted by another timetable.
    if (onlyIfUnbound && tables.isNotEmpty()) return@atomic false
    store.captureUndo()
    tables.forEach { table ->
        store.updateTable(table.hydratedWith(period).copy(
            periodTableId = null,
            preBindSnapshotJson = "",
        ))
    }
    store.deletePeriod(id)
    true
}
