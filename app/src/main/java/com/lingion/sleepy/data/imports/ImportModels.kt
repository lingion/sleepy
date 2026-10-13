package com.lingion.sleepy.data.imports

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.serialization.Serializable

@Serializable enum class ImportDestination { Existing, New }
@Serializable enum class ImportContent { ImportOnly, Merge }
@Serializable enum class DuplicatePolicy { Skip, Keep }
@Serializable enum class OverlapPolicy { SkipIncoming, ReplaceExisting, KeepBoth, PerItem }
@Serializable enum class ReplacementScope { OverlapWeeks, WholeRow }
@Serializable enum class ItemDecision { Skip, KeepBoth, ReplaceExisting }
@Serializable enum class ScheduleMode { KeepBase, UseIncoming, Merge, Edit, BindExisting, CreateNew }
@Serializable enum class SharedScope { ThisTable, Shared }

/** User-confirmed decisions. Incoming keys are zero-based indices in ParseResult.courses. */
@Serializable
data class ImportConfiguration(
    val baseTableId: Long,
    val destination: ImportDestination = ImportDestination.Existing,
    val content: ImportContent = ImportContent.Merge,
    val duplicates: DuplicatePolicy = DuplicatePolicy.Skip,
    val overlaps: OverlapPolicy = OverlapPolicy.KeepBoth,
    val replacementScope: ReplacementScope = ReplacementScope.OverlapWeeks,
    val itemOverrides: Map<Int, ItemDecision> = emptyMap(),
    val name: String,
    val startDate: String,
    val maxWeek: Int,
    val nodesPerDay: Int,
    val timeJson: String,
    val smartConfigJson: String = "",
    val scheduleMode: ScheduleMode = ScheduleMode.KeepBase,
    val selectedPeriodTableId: Long? = null,
    val periodName: String = "",
    val sharedScope: SharedScope = SharedScope.ThisTable,
    val isDefault: Boolean = false,
    val dateInterpretationAcknowledged: Boolean = false
) {
    companion object {
        fun forExisting(table: TimeTableEntity, period: PeriodTableEntity? = null): ImportConfiguration {
            val effective = table.hydratedWith(period)
            return ImportConfiguration(
                baseTableId = table.id, name = table.name, startDate = table.startDate,
                maxWeek = table.maxWeek, nodesPerDay = effective.nodesPerDay,
                timeJson = effective.timeJson, smartConfigJson = effective.smartConfigJson,
                isDefault = table.isDefault, dateInterpretationAcknowledged = true
            )
        }

        fun forNew(source: ScheduleParser.ParseResult, base: TimeTableEntity): ImportConfiguration =
            ImportConfiguration(
                baseTableId = base.id, destination = ImportDestination.New,
                name = source.tableName.ifBlank { base.name },
                startDate = source.startDate.ifBlank { base.startDate },
                maxWeek = source.maxWeek.takeIf { it > 0 } ?: base.maxWeek,
                nodesPerDay = base.nodesPerDay,
                timeJson = base.timeJson, smartConfigJson = base.smartConfigJson,
                dateInterpretationAcknowledged = false
            )
    }
}

/** Presets only populate decisions; they never perform writes. */
enum class ImportPreset { AddToExisting, ReplaceExistingCourses, NewWithImportedCourses }
fun ImportConfiguration.withPreset(preset: ImportPreset): ImportConfiguration = when (preset) {
    ImportPreset.AddToExisting -> copy(
        destination = ImportDestination.Existing, content = ImportContent.Merge,
        duplicates = DuplicatePolicy.Skip, overlaps = OverlapPolicy.KeepBoth,
        replacementScope = ReplacementScope.OverlapWeeks, itemOverrides = emptyMap())
    ImportPreset.ReplaceExistingCourses -> copy(
        destination = ImportDestination.Existing, content = ImportContent.ImportOnly,
        duplicates = DuplicatePolicy.Skip, overlaps = OverlapPolicy.ReplaceExisting,
        replacementScope = ReplacementScope.OverlapWeeks, itemOverrides = emptyMap())
    ImportPreset.NewWithImportedCourses -> copy(
        destination = ImportDestination.New, content = ImportContent.ImportOnly,
        duplicates = DuplicatePolicy.Skip, overlaps = OverlapPolicy.KeepBoth,
        replacementScope = ReplacementScope.OverlapWeeks, itemOverrides = emptyMap())
}

data class ImportSnapshot(
    val tables: List<TimeTableEntity>,
    val periodTables: List<PeriodTableEntity>,
    val courses: List<CourseEntity>
)

enum class ValidationKey {
    MissingBaseTable, InvalidName, InvalidDate, DateNormalized,
    DateInterpretationAcknowledgementRequired, InvalidMaxWeek, InvalidNodesPerDay,
    CourseWeekOutOfRange, InvalidCourseDay, InvalidCourseNode, InvalidCourseTime,
    UnknownIncomingIndex, UnresolvedPerItemDecision, ContradictoryDecisions,
    UncertainDestructiveOverlap, MissingSelectedPeriodTable, MissingBoundPeriodTable,
    InvalidSchedule, InvalidPeriodScope, NameCollision, DuplicateCourseId
}

data class ImportIssue(
    val key: ValidationKey,
    val blocking: Boolean,
    val incomingIndex: Int? = null,
    val courseId: Long? = null
)

enum class ConflictKind { IncomingExisting, IncomingInternal }
enum class RelationConfidence { Certain, Uncertain }

data class ImportConflict(
    val incomingIndex: Int,
    val existingId: Long? = null,
    val existingIndex: Int? = null,
    val otherIncomingIndex: Int? = null,
    val weeks: Set<Int>,
    val kind: ConflictKind,
    val confidence: RelationConfidence
)

data class ImportDuplicate(
    val incomingIndex: Int,
    val existingId: Long? = null,
    val existingIndex: Int? = null,
    val otherIncomingIndex: Int? = null,
    val skipped: Boolean
)

enum class SkipReason { Duplicate, Overlap, PerItem }
data class SkippedIncoming(val incomingIndex: Int, val reason: SkipReason)

data class ImportReplacement(
    val original: CourseEntity,
    val removedWeeks: Set<Int>,
    val remainingWeeks: Set<Int>,
    val remainingRows: List<CourseEntity>
)

enum class PeriodOperation { None, Insert, Update }
enum class PeriodBinding { None, Existing, PlannedPeriod }

/** Rows are in deterministic order; ID 0 means insert. A writer replaces the target course set
 * transactionally using delete/update/insert lists, then resolves PlannedPeriod after insertion. */
data class ImportPlan(
    val source: ScheduleParser.ParseResult,
    val snapshot: ImportSnapshot,
    val configuration: ImportConfiguration,
    val finalTable: TimeTableEntity,
    val finalPeriodTable: PeriodTableEntity?,
    val periodOperation: PeriodOperation,
    val periodBinding: PeriodBinding,
    val finalCourses: List<CourseEntity>,
    val retainedCourses: List<CourseEntity>,
    val incomingCourses: List<CourseEntity>,
    val insertCourses: List<CourseEntity>,
    val updateCourses: List<CourseEntity>,
    val deleteCourses: List<CourseEntity>,
    val duplicates: List<ImportDuplicate>,
    val skipped: List<SkippedIncoming>,
    val conflicts: List<ImportConflict>,
    val replacements: List<ImportReplacement>,
    val issues: List<ImportIssue>
) {
    val canSubmit: Boolean get() = issues.none { it.blocking }
    val incomingCount: Int get() = incomingCourses.size
    val retainedCount: Int get() = retainedCourses.size
    val skippedCount: Int get() = skipped.size
    val conflictCount: Int get() = conflicts.size
    val deletedCount: Int get() = deleteCourses.size
}
