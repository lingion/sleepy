package com.lingion.sleepy.data.repository

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.imports.*
import kotlinx.coroutines.CancellationException

/** Compare every persisted column, independently of DAO query ordering. */
internal fun canonicalImportSnapshot(snapshot: ImportSnapshot): ImportSnapshot = snapshot.copy(
    tables = snapshot.tables.sortedBy { it.id },
    periodTables = snapshot.periodTables.sortedBy { it.id },
    courses = snapshot.courses.sortedBy { it.id }
)

internal data class ImportWriteSet(
    val table: TimeTableEntity,
    val insertPeriod: PeriodTableEntity?,
    val updatePeriod: PeriodTableEntity?,
    val insertCourses: List<CourseEntity>,
    val updateCourses: List<CourseEntity>,
    val deleteCourseIds: List<Long>,
    val setDefault: Boolean,
    val hasChanges: Boolean
)

/**
 * Rebuild the complete confirmed plan before trusting any public data-class output. The write set
 * comes from its final rows, so unchanged rows keep their IDs and new/copy rows can only insert.
 * Generated commit timestamps and optional default selection do not turn an actual no-op into a write.
 */
internal fun validatedImportWriteSet(plan: ImportPlan, current: ImportSnapshot): ImportWriteSet? {
    if (!plan.canSubmit) return null
    val rebuilt = planImport(plan.source, current, plan.configuration)
    if (!rebuilt.canSubmit || rebuilt != plan.copy(snapshot = current)) return null
    val creating = plan.configuration.destination == ImportDestination.New
    val oldTable = current.tables.firstOrNull { it.id == plan.configuration.baseTableId }
    if (!creating && oldTable == null) return null
    if (plan.finalTable.id != if (creating) 0L else oldTable!!.id) return null

    val oldRows = if (creating) emptyList() else current.courses.filter { it.tableId == oldTable!!.id }
    val oldById = oldRows.associateBy { it.id }
    val retained = plan.finalCourses.filter { it.id != 0L }
    if (retained.map { it.id }.distinct().size != retained.size ||
        retained.any { it.id !in oldById } ||
        plan.finalCourses.any { it.tableId != plan.finalTable.id }) return null
    val retainedIds = retained.map { it.id }.toSet()
    val deleted = oldRows.filter { it.id !in retainedIds }.map { it.id }.sorted()
    val updated = retained.filter { it != oldById[it.id] }
    val inserted = plan.finalCourses.filter { it.id == 0L }

    val insertPeriod = if (plan.periodOperation == PeriodOperation.Insert) plan.finalPeriodTable else null
    if (plan.periodOperation == PeriodOperation.Insert && insertPeriod?.id != 0L) return null
    val updatePeriod = if (plan.periodOperation == PeriodOperation.Update) {
        val proposed = plan.finalPeriodTable ?: return null
        val old = current.periodTables.firstOrNull { it.id == proposed.id } ?: return null
        proposed.copy(updatedAt = old.updatedAt).takeIf { it != old }
    } else null
    when (plan.periodBinding) {
        PeriodBinding.None -> if (plan.finalTable.periodTableId != null) return null
        PeriodBinding.Existing -> if (current.periodTables.none { it.id == plan.finalTable.periodTableId }) return null
        PeriodBinding.PlannedPeriod -> if (insertPeriod == null) return null
    }

    // isDefault is an independent request to select the destination; false leaves navigation alone.
    val selectDefault = plan.configuration.isDefault || (creating && current.tables.isEmpty())
    val table = plan.finalTable.copy(isDefault = if (creating) selectDefault else oldTable!!.isDefault)
    val defaultChanges = selectDefault && (creating || current.tables.any { it.isDefault != (it.id == table.id) })
    return ImportWriteSet(table, insertPeriod, updatePeriod, inserted, updated, deleted, defaultChanges,
        creating || table != oldTable || insertPeriod != null || updatePeriod != null ||
            inserted.isNotEmpty() || updated.isNotEmpty() || deleted.isNotEmpty() || defaultChanges)
}

/** Run all post-commit stages even when one fails; never reinterpret a committed write as Failed. */
internal suspend fun importPostCommitWarnings(vararg actions: Pair<String, suspend () -> Unit>): List<String> {
    val warnings = mutableListOf<String>()
    for ((label, action) in actions) {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            warnings += "$label: ${failure.message?.takeIf { it.isNotBlank() } ?: failure.javaClass.simpleName}"
        }
    }
    return warnings
}
