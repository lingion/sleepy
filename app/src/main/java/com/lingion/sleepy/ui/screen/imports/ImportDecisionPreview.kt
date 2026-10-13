package com.lingion.sleepy.ui.screen.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.imports.*
import com.lingion.sleepy.data.parser.ScheduleParser

@Composable
internal fun ImportDecisionSourceSection(source: ScheduleParser.ParseResult) {
    ImportDecisionSection(stringResource(R.string.id_source)) {
        Text(stringResource(R.string.id_source_count, source.courses.size))
        source.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (source.droppedLines.isNotEmpty()) {
            Text(stringResource(R.string.id_dropped_count, source.droppedLines.size), color = MaterialTheme.colorScheme.error)
            source.droppedLines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ImportDecisionCourse(course: CourseEntity, timeJson: String, maxWeek: Int) {
    val days = stringArrayResource(R.array.day_names)
    val weeks = (course.startWeek..course.endWeek).filter { it > 0 && course.inWeek(it) }.toSet()
    val time = importDecisionCourseTime(course, timeJson)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(course.courseName, style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.id_course_arrangement,
            days.getOrElse(course.day - 1) { stringResource(R.string.id_invalid_day) },
            importDecisionWeekRanges(weeks).ifBlank { stringResource(R.string.id_none) },
            if (time != null) "${time.first}–${time.second}" else stringResource(R.string.id_course_nodes, course.startNode, course.startNode + course.step - 1)),
            style = MaterialTheme.typography.bodySmall)
        if (weeks.any { it > maxWeek }) Text(stringResource(R.string.id_issue_week), color = MaterialTheme.colorScheme.error)
        if (course.teacher.isNotBlank()) Text(stringResource(R.string.id_teacher, course.teacher), style = MaterialTheme.typography.bodySmall)
        if (course.room.isNotBlank()) Text(stringResource(R.string.id_room, course.room), style = MaterialTheme.typography.bodySmall)
        if (course.note.isNotBlank()) Text(stringResource(R.string.id_note, course.note), style = MaterialTheme.typography.bodySmall)
        if (course.alias.isNotBlank()) Text(stringResource(R.string.id_alias, course.alias), style = MaterialTheme.typography.bodySmall)
        if (course.credit != 0f) Text(stringResource(R.string.id_credit, course.credit.toString()), style = MaterialTheme.typography.bodySmall)
        if (course.level != 0) Text(stringResource(R.string.id_level, course.level), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ImportDecisionIncomingCourse(plan: ImportPlan, course: CourseEntity) {
    val sourceTime = plan.source.timeJson.ifBlank { plan.source.periodTable?.timeJson.orEmpty() }
    val hasSourceTime = importDecisionCourseTime(course, sourceTime) != null
    val timeJson = if (hasSourceTime) sourceTime else plan.finalPeriodTable?.timeJson ?: plan.finalTable.timeJson
    if (!hasSourceTime && importDecisionCourseTime(course, timeJson) != null) {
        Text(stringResource(R.string.id_source_time_fallback), style = MaterialTheme.typography.bodySmall)
    }
    ImportDecisionCourse(course, timeJson, plan.source.maxWeek.takeIf { it > 0 } ?: Int.MAX_VALUE)
}

private fun ImportPlan.existingCourse(existingId: Long?, existingIndex: Int?): CourseEntity? {
    val old = snapshot.courses.filter { it.tableId == configuration.baseTableId }
        .sortedWith(compareBy<CourseEntity> { it.id }.thenBy { it.groupId }.thenBy { it.courseName })
    return existingId?.let { id -> old.firstOrNull { it.id == id } } ?: existingIndex?.let { old.getOrNull(it) }
}

@Composable
internal fun ImportDecisionPerItemSection(plan: ImportPlan, onChange: (ImportConfiguration) -> Unit) {
    if (plan.configuration.overlaps != OverlapPolicy.PerItem) return
    val hasExistingCourses = plan.configuration.content == ImportContent.Merge &&
        plan.snapshot.courses.any { it.tableId == plan.configuration.baseTableId }
    ImportDecisionSection(stringResource(R.string.id_per_item)) {
        Text(stringResource(R.string.id_per_item_help), style = MaterialTheme.typography.bodySmall)
        plan.source.courses.forEachIndexed { index, course ->
            Column(Modifier.fillMaxWidth().testTag("import-source-$index"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.id_source_record, index + 1), style = MaterialTheme.typography.labelLarge)
                ImportDecisionIncomingCourse(plan, course)
                plan.conflicts.filter { it.incomingIndex == index || it.otherIncomingIndex == index }.forEach { conflict ->
                    val other = if (conflict.kind == ConflictKind.IncomingInternal) {
                        val otherIndex = if (conflict.incomingIndex == index) conflict.otherIncomingIndex else conflict.incomingIndex
                        plan.source.courses.getOrNull(otherIndex ?: -1)?.courseName
                    } else plan.existingCourse(conflict.existingId, conflict.existingIndex)?.courseName
                    Text(stringResource(if (conflict.kind == ConflictKind.IncomingInternal) R.string.id_conflict_source else R.string.id_conflict_existing,
                        other ?: stringResource(R.string.id_missing), importDecisionWeekRanges(conflict.weeks)),
                        style = MaterialTheme.typography.bodySmall)
                }
                if (plan.duplicates.any { it.incomingIndex == index && it.skipped }) {
                    Text(stringResource(R.string.id_duplicate_priority), style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val decisions = if (hasExistingCourses) ItemDecision.entries
                        else listOf(ItemDecision.Skip, ItemDecision.KeepBoth)
                    decisions.forEach { decision ->
                        ImportDecisionChoice(stringResource(when (decision) {
                            ItemDecision.Skip -> R.string.id_item_skip
                            ItemDecision.KeepBoth -> R.string.id_item_keep
                            ItemDecision.ReplaceExisting -> R.string.id_item_replace
                        }), plan.configuration.itemOverrides[index] == decision,
                            { onChange(plan.configuration.copy(itemOverrides = plan.configuration.itemOverrides + (index to decision))) },
                            Modifier.testTag("import-item-$index-${decision.name}"))
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportDecisionDetail(title: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    ImportDecisionSecondaryButton(stringResource(if (expanded) R.string.id_hide_detail else R.string.id_show_detail, title),
        { expanded = !expanded })
    if (expanded) content()
}

@Composable
internal fun ImportDecisionPreviewSection(plan: ImportPlan) {
    val finalTime = plan.finalPeriodTable?.timeJson ?: plan.finalTable.timeJson
    val maxWeek = plan.finalTable.maxWeek
    val originalTable = importDecisionBase(plan.configuration, plan.snapshot)
    val originalTime = originalTable?.timeJson.orEmpty()
    val originalMaxWeek = originalTable?.maxWeek ?: Int.MAX_VALUE
    ImportDecisionSection(stringResource(R.string.id_preview)) {
        Text(stringResource(R.string.id_preview_counts, plan.incomingCount, plan.retainedCount, plan.skippedCount,
            plan.finalCourses.size))
        Text(stringResource(R.string.id_final_table, plan.finalTable.name, plan.finalTable.startDate, maxWeek))
        plan.finalPeriodTable?.let { period ->
            Text(stringResource(when (plan.periodOperation) {
                PeriodOperation.None -> R.string.id_period_reuse
                PeriodOperation.Insert -> R.string.id_period_insert
                PeriodOperation.Update -> R.string.id_period_update
            }, period.name))
        }
        val affected = importDecisionAffectedTables(plan.configuration, plan.snapshot)
        if (affected.isNotEmpty()) Text(stringResource(R.string.id_affected_tables, affected.joinToString(", ")),
            color = MaterialTheme.colorScheme.error)
        plan.issues.forEach { issue ->
            val message = importDecisionIssueText(issue)
            Text(if (issue.incomingIndex != null) stringResource(R.string.id_record_issue, issue.incomingIndex + 1, message) else message,
                color = if (issue.blocking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (plan.skipped.isNotEmpty()) ImportDecisionDetail(stringResource(R.string.id_skipped_title, plan.skippedCount)) {
            plan.skipped.forEach { skipped ->
                Text(stringResource(R.string.id_skip_reason, skipped.incomingIndex + 1, stringResource(when (skipped.reason) {
                    SkipReason.Duplicate -> R.string.id_reason_duplicate
                    SkipReason.Overlap -> R.string.id_reason_overlap
                    SkipReason.PerItem -> R.string.id_reason_item
                })))
                plan.source.courses.getOrNull(skipped.incomingIndex)?.let { ImportDecisionIncomingCourse(plan, it) }
            }
        }
        if (plan.deleteCourses.isNotEmpty()) ImportDecisionDetail(stringResource(R.string.id_deleted_title, plan.deleteCourses.size)) {
            plan.deleteCourses.forEach { ImportDecisionCourse(it, originalTime, originalMaxWeek) }
        }
        if (plan.replacements.isNotEmpty()) ImportDecisionDetail(stringResource(R.string.id_replaced_title, plan.replacements.size)) {
            plan.replacements.forEach { replacement ->
                ImportDecisionCourse(replacement.original, originalTime, originalMaxWeek)
                Text(stringResource(R.string.id_removed_weeks, importDecisionWeekRanges(replacement.removedWeeks)))
                Text(stringResource(R.string.id_remaining_weeks,
                    importDecisionWeekRanges(replacement.remainingWeeks).ifBlank { stringResource(R.string.id_none) }))
                replacement.remainingRows.forEach { ImportDecisionCourse(it, finalTime, maxWeek) }
            }
        }
        if (plan.duplicates.isNotEmpty()) ImportDecisionDetail(stringResource(R.string.id_duplicate_title, plan.duplicates.size)) {
            plan.duplicates.forEach { duplicate ->
                val other = duplicate.otherIncomingIndex?.let { plan.source.courses.getOrNull(it)?.courseName }
                    ?: plan.existingCourse(duplicate.existingId, duplicate.existingIndex)?.courseName
                Text(stringResource(if (duplicate.skipped) R.string.id_duplicate_skipped else R.string.id_duplicate_kept,
                    duplicate.incomingIndex + 1, other ?: stringResource(R.string.id_missing)))
            }
        }
        if (plan.conflicts.isNotEmpty()) ImportDecisionDetail(stringResource(R.string.id_conflicts_title, plan.conflictCount)) {
            plan.conflicts.forEach { conflict ->
                val incoming = plan.source.courses.getOrNull(conflict.incomingIndex)?.courseName ?: stringResource(R.string.id_missing)
                val other = conflict.otherIncomingIndex?.let { plan.source.courses.getOrNull(it)?.courseName }
                    ?: plan.existingCourse(conflict.existingId, conflict.existingIndex)?.courseName
                    ?: stringResource(R.string.id_missing)
                Text(stringResource(if (conflict.kind == ConflictKind.IncomingInternal) R.string.id_edge_source else R.string.id_edge_existing,
                    incoming, other, importDecisionWeekRanges(conflict.weeks)))
                if (conflict.confidence == RelationConfidence.Uncertain) Text(stringResource(R.string.id_uncertain),
                    color = MaterialTheme.colorScheme.error)
            }
        }
        ImportDecisionDetail(stringResource(R.string.id_final_rows, plan.finalCourses.size)) {
            plan.finalCourses.forEach { ImportDecisionCourse(it, finalTime, maxWeek) }
        }
    }
}

@Composable
private fun importDecisionIssueText(issue: ImportIssue): String = stringResource(when (issue.key) {
    ValidationKey.MissingBaseTable -> R.string.id_issue_base
    ValidationKey.InvalidName -> R.string.id_issue_name
    ValidationKey.InvalidDate -> R.string.id_issue_date
    ValidationKey.DateNormalized -> R.string.id_normalized_date
    ValidationKey.DateInterpretationAcknowledgementRequired -> R.string.id_issue_ack
    ValidationKey.InvalidMaxWeek -> R.string.id_issue_max_week
    ValidationKey.InvalidNodesPerDay -> R.string.id_issue_nodes
    ValidationKey.CourseWeekOutOfRange -> R.string.id_issue_week
    ValidationKey.InvalidCourseDay -> R.string.id_invalid_day
    ValidationKey.InvalidCourseNode -> R.string.id_issue_course_node
    ValidationKey.InvalidCourseTime -> R.string.id_issue_course_time
    ValidationKey.UnknownIncomingIndex -> R.string.id_issue_source_index
    ValidationKey.UnresolvedPerItemDecision -> R.string.id_issue_item
    ValidationKey.ContradictoryDecisions -> R.string.id_issue_contradiction
    ValidationKey.UncertainDestructiveOverlap -> R.string.id_uncertain
    ValidationKey.MissingSelectedPeriodTable, ValidationKey.MissingBoundPeriodTable -> R.string.id_issue_period
    ValidationKey.InvalidSchedule -> R.string.id_issue_schedule
    ValidationKey.InvalidPeriodScope -> R.string.id_issue_scope
    ValidationKey.DuplicateCourseId -> R.string.id_issue_ids
    else -> R.string.id_issue_generic
})
