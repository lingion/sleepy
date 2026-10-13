package com.lingion.sleepy.data.repository

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity

internal interface NewTableImportStore {
    suspend fun atomic(block: suspend () -> Long): Long
    suspend fun periodExists(id: Long): Boolean
    suspend fun captureUndo()
    suspend fun insertPeriod(period: PeriodTableEntity): Long
    suspend fun insertTable(table: TimeTableEntity): Long
    suspend fun insertCourses(courses: List<CourseEntity>)
    suspend fun setDefault(id: Long)
}

internal suspend fun importNewTableAtomically(
    store: NewTableImportStore, table: TimeTableEntity, courses: List<CourseEntity>,
    period: PeriodTableEntity?, bindingId: Long?, keepGroups: Boolean
): Long = store.atomic {
    require(bindingId == null || store.periodExists(bindingId)) { "Selected period table no longer exists" }
    store.captureUndo()
    val periodId = bindingId ?: period?.let { store.insertPeriod(it.copy(id = 0)) }
    val tableId = store.insertTable(table.copy(id = 0, periodTableId = periodId, isDefault = false))
    val rows = courses.map { it.copy(id = 0, tableId = tableId) }
    store.insertCourses(if (keepGroups) rows else normalizedImportGroups(rows))
    store.setDefault(tableId)
    tableId
}

internal fun normalizedImportGroups(courses: List<CourseEntity>): List<CourseEntity> {
    val nameToGroupId = mutableMapOf<String, String>()
    return courses.map { c ->
        val key = c.courseName.trim().replace(Regex("\\s+"), " ").lowercase()
        val gid = nameToGroupId.getOrPut(key) { c.groupId.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString() }
        c.copy(groupId = gid)
    }
}
