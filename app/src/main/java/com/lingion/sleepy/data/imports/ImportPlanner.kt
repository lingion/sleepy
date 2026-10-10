package com.lingion.sleepy.data.imports

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.util.TimeTableUtils
import java.time.DayOfWeek
import java.time.LocalDate

private data class WorkingSource(val index: Int, val course: CourseEntity)
private data class WorkingOld(val course: CourseEntity, val removed: MutableSet<Int> = linkedSetOf())

fun planImport(
    source: ScheduleParser.ParseResult,
    snapshot: ImportSnapshot,
    configuration: ImportConfiguration
): ImportPlan {
    val issues = mutableListOf<ImportIssue>()
    val base = snapshot.tables.firstOrNull { it.id == configuration.baseTableId }
    if (base == null && !(configuration.destination == ImportDestination.New &&
            configuration.content == ImportContent.ImportOnly && configuration.baseTableId == 0L))
        issues += ImportIssue(ValidationKey.MissingBaseTable, true)
    val basePeriod = base?.periodTableId?.let { id -> snapshot.periodTables.firstOrNull { it.id == id } }
    if (base?.periodTableId != null && basePeriod == null)
        issues += ImportIssue(ValidationKey.MissingBoundPeriodTable, true)
    val rawBase = base
    val effectiveBase = rawBase?.hydratedWith(basePeriod)
    val selected = configuration.selectedPeriodTableId?.let { id -> snapshot.periodTables.firstOrNull { it.id == id } }
    if (configuration.scheduleMode == ScheduleMode.BindExisting && selected == null)
        issues += ImportIssue(ValidationKey.MissingSelectedPeriodTable, true)
    if (configuration.name.isBlank()) issues += ImportIssue(ValidationKey.InvalidName, true)
    if (configuration.destination == ImportDestination.New && snapshot.tables.any {
            it.name.trim().equals(configuration.name.trim(), ignoreCase = true)
        }) issues += ImportIssue(ValidationKey.NameCollision, true)
    if (configuration.sharedScope == SharedScope.Shared && configuration.scheduleMode !in
        setOf(ScheduleMode.Edit, ScheduleMode.BindExisting))
        issues += ImportIssue(ValidationKey.InvalidPeriodScope, true)
    val parsedDate = runCatching { LocalDate.parse(configuration.startDate) }.getOrNull()
    if (parsedDate == null) issues += ImportIssue(ValidationKey.InvalidDate, true)
    val monday = parsedDate?.with(DayOfWeek.MONDAY)
    if (parsedDate != null && parsedDate != monday)
        issues += ImportIssue(ValidationKey.DateNormalized, false)
    if (parsedDate != null && !configuration.dateInterpretationAcknowledged &&
        (monday.toString() != source.startDate ||
            (configuration.destination == ImportDestination.Existing && monday.toString() != base?.startDate)))
        issues += ImportIssue(ValidationKey.DateInterpretationAcknowledgementRequired, true)
    if (configuration.maxWeek < 1) issues += ImportIssue(ValidationKey.InvalidMaxWeek, true)
    if (configuration.nodesPerDay < 1) issues += ImportIssue(ValidationKey.InvalidNodesPerDay, true)
    val finalDate = monday?.toString() ?: configuration.startDate
    val rawTable = rawBase ?: TimeTableEntity(
        id = configuration.baseTableId, name = configuration.name, startDate = finalDate,
        maxWeek = configuration.maxWeek, nodesPerDay = configuration.nodesPerDay,
        timeJson = configuration.timeJson, smartConfigJson = configuration.smartConfigJson,
        isDefault = configuration.isDefault
    )
    val oldTable = effectiveBase ?: rawTable
    val existingTargetCourses = if (base == null) emptyList() else snapshot.courses.filter { it.tableId == base.id }
        .sortedWith(compareBy<CourseEntity> { it.id }.thenBy { it.groupId }.thenBy { it.courseName })
    // Keep original IDs while resolving old-row relations and replacements. A new table
    // zeroes copied IDs only in its final insert rows, after all decisions are made.
    val oldCourses = if (configuration.content == ImportContent.Merge) existingTargetCourses else emptyList()
    val finalTableId = if (configuration.destination == ImportDestination.New) 0L else oldTable.id
    val finalTimeJson = when (configuration.scheduleMode) {
        ScheduleMode.KeepBase -> oldTable.timeJson
        ScheduleMode.BindExisting -> selected?.timeJson ?: oldTable.timeJson
        ScheduleMode.UseIncoming -> source.timeJson.takeIf { it.isNotBlank() } ?: oldTable.timeJson
        ScheduleMode.Merge -> TimeTableUtils.mergeMostComplete(oldTable.timeJson, source.timeJson,
            requiredNodeCount = source.courses.maxOfOrNull { it.startNode + it.step - 1 } ?: 0)
        ScheduleMode.Edit, ScheduleMode.CreateNew -> configuration.timeJson
    }
    val finalNodes = when (configuration.scheduleMode) {
        ScheduleMode.KeepBase -> oldTable.nodesPerDay
        ScheduleMode.BindExisting -> selected?.nodesPerDay ?: oldTable.nodesPerDay
        ScheduleMode.UseIncoming -> TimeTableUtils.maxStandardNode(finalTimeJson).takeIf { it > 0 }
            ?: source.nodesPerDay.takeIf { it > 0 } ?: oldTable.nodesPerDay
        ScheduleMode.Merge -> TimeTableUtils.maxStandardNode(finalTimeJson)
        else -> configuration.nodesPerDay
    }.coerceAtLeast(1)
    val plannedPeriodId = when {
        configuration.scheduleMode == ScheduleMode.BindExisting -> selected?.id
        configuration.destination == ImportDestination.Existing && configuration.scheduleMode == ScheduleMode.KeepBase ->
            rawBase?.periodTableId
        configuration.destination == ImportDestination.Existing && configuration.sharedScope == SharedScope.Shared &&
            configuration.scheduleMode == ScheduleMode.Edit -> basePeriod?.id
        else -> null
    }
    // Keep finalTable raw for persistence. Hydrated schedule values are used above for planning,
    // but writing them back would overwrite the compatibility columns of a bound table.
    val preserveRawColumns = finalTableId != 0L &&
        (rawBase?.periodTableId != null || configuration.scheduleMode != ScheduleMode.KeepBase)
    val rawFinalTable = rawTable.copy(
        id = finalTableId, name = configuration.name, startDate = finalDate,
        maxWeek = configuration.maxWeek, isDefault = configuration.isDefault,
        createdAt = if (finalTableId == 0L) 0L else rawTable.createdAt,
        preBindSnapshotJson = if (finalTableId == 0L) "" else rawTable.preBindSnapshotJson,
        nodesPerDay = if (preserveRawColumns) rawTable.nodesPerDay else finalNodes,
        timeJson = if (preserveRawColumns) rawTable.timeJson else finalTimeJson,
        smartConfigJson = if (preserveRawColumns) rawTable.smartConfigJson else when (configuration.scheduleMode) {
            ScheduleMode.KeepBase -> oldTable.smartConfigJson
            ScheduleMode.BindExisting -> selected?.smartConfigJson ?: oldTable.smartConfigJson
            else -> configuration.smartConfigJson
        }
    )
    val periodBase = selected ?: basePeriod
    val shouldMakePeriod = configuration.scheduleMode in setOf(ScheduleMode.UseIncoming,
        ScheduleMode.Merge, ScheduleMode.Edit, ScheduleMode.CreateNew) ||
        (configuration.destination == ImportDestination.New && periodBase != null)
    val useShared = configuration.sharedScope == SharedScope.Shared &&
        configuration.scheduleMode in setOf(ScheduleMode.Edit, ScheduleMode.BindExisting)
    val finalPeriod: PeriodTableEntity? = when {
        configuration.scheduleMode == ScheduleMode.BindExisting -> selected
        useShared -> periodBase?.copy(
            name = configuration.periodName.ifBlank { periodBase.name },
            nodesPerDay = finalNodes, timeJson = finalTimeJson,
            smartConfigJson = configuration.smartConfigJson)
        shouldMakePeriod -> PeriodTableEntity(
            id = 0, name = configuration.periodName.ifBlank {
                source.periodTable?.name?.takeIf { it.isNotBlank() } ?: configuration.name
            },
            nodesPerDay = finalNodes, timeJson = finalTimeJson,
            smartConfigJson = when (configuration.scheduleMode) {
                ScheduleMode.KeepBase -> oldTable.smartConfigJson
                ScheduleMode.BindExisting -> selected?.smartConfigJson ?: oldTable.smartConfigJson
                else -> configuration.smartConfigJson
            }, createdAt = 0L, updatedAt = 0L)
        else -> periodBase
    }
    val periodOperation = when {
        finalPeriod == null -> PeriodOperation.None
        useShared && periodBase != null -> PeriodOperation.Update
        shouldMakePeriod -> PeriodOperation.Insert
        else -> PeriodOperation.None
    }
    val periodBinding = when {
        configuration.scheduleMode == ScheduleMode.BindExisting && selected != null -> PeriodBinding.Existing
        finalPeriod != null && periodOperation == PeriodOperation.Insert -> PeriodBinding.PlannedPeriod
        finalPeriod != null -> PeriodBinding.Existing
        else -> PeriodBinding.None
    }
    val finalTable = when {
        periodBinding == PeriodBinding.PlannedPeriod &&
            (finalTableId == 0L || rawBase?.periodTableId == null) ->
            // The actual period ID is assigned by the transaction; capture the schedule now.
            TimeTableEntity.snapshotForBind(rawFinalTable, 0L).copy(periodTableId = null)
        periodBinding == PeriodBinding.Existing && plannedPeriodId != null &&
            (finalTableId == 0L || rawBase?.periodTableId == null) ->
            TimeTableEntity.snapshotForBind(rawFinalTable.copy(periodTableId = null), plannedPeriodId)
        periodBinding == PeriodBinding.None && rawBase?.periodTableId != null &&
            configuration.destination == ImportDestination.Existing ->
            TimeTableEntity.restoredForUnbind(rawFinalTable)
        else -> rawFinalTable.copy(periodTableId = plannedPeriodId)
    }
    val allSource = source.courses.mapIndexed { index, course -> WorkingSource(index,
        course.copy(id = 0L, tableId = finalTableId)) }
    configuration.itemOverrides.keys.filter { it !in allSource.map { s -> s.index }.toSet() }
        .forEach { issues += ImportIssue(ValidationKey.UnknownIncomingIndex, true, it) }

    val duplicates = mutableListOf<ImportDuplicate>()
    val skipped = mutableListOf<SkippedIncoming>()
    val conflicts = mutableListOf<ImportConflict>()
    val retainedForRelations = oldCourses
    for (i in allSource.indices) for (j in i + 1 until allSource.size) {
        val a = allSource[i]; val b = allSource[j]
        if (sameArrangement(a.course, b.course, finalTimeJson, finalTimeJson, configuration.maxWeek,
                sourceInternal = true, source.groupIdsAuthoritative)) {
            duplicates += ImportDuplicate(b.index, otherIncomingIndex = a.index, skipped = false)
        }
        relation(a.course, b.course, finalTimeJson, finalTimeJson, configuration.maxWeek)?.let { (weeks, confidence) ->
            conflicts += ImportConflict(a.index, existingIndex = null, otherIncomingIndex = b.index,
                weeks = weeks, kind = ConflictKind.IncomingInternal, confidence = confidence)
        }
    }
    for (incoming in allSource) {
        val oldMatches = retainedForRelations.mapNotNull { old ->
            if (sameArrangement(incoming.course, old, finalTimeJson, finalTimeJson, configuration.maxWeek,
                    sourceInternal = false, authoritativeGroups = false)) old else null
        }
        oldMatches.forEach { old ->
            duplicates += ImportDuplicate(incoming.index, existingId = old.id, skipped = false)
        }
        retainedForRelations.forEach { old ->
            relation(incoming.course, old, finalTimeJson, finalTimeJson, configuration.maxWeek)?.let { (weeks, confidence) ->
                conflicts += ImportConflict(incoming.index, existingId = old.id,
                    weeks = weeks, kind = ConflictKind.IncomingExisting, confidence = confidence)
            }
        }
    }
    val accepted = mutableListOf<WorkingSource>()
    for (incoming in allSource) {
        val duplicateMatches = duplicates.filter { it.incomingIndex == incoming.index &&
            (it.existingId != null || accepted.any { row -> row.index == it.otherIncomingIndex }) }
        val reason = when {
            configuration.overlaps == OverlapPolicy.PerItem &&
                configuration.itemOverrides[incoming.index] == ItemDecision.Skip -> SkipReason.PerItem
            configuration.duplicates == DuplicatePolicy.Skip && duplicateMatches.isNotEmpty() -> SkipReason.Duplicate
            configuration.overlaps == OverlapPolicy.SkipIncoming && conflicts.any {
                it.incomingIndex == incoming.index && it.kind == ConflictKind.IncomingExisting
            } -> SkipReason.Overlap
            else -> null
        }
        if (reason == null) accepted += incoming else {
            skipped += SkippedIncoming(incoming.index, reason)
            if (reason == SkipReason.Duplicate) duplicateMatches.forEach { match ->
                val index = duplicates.indexOf(match)
                duplicates[index] = match.copy(skipped = true)
            }
        }
    }

    val oldWorking = oldCourses.associate { it.id to WorkingOld(it) }.toMutableMap()
    val destructive = mutableMapOf<Long, MutableSet<Int>>()
    val keptByOld = mutableMapOf<Long, MutableList<ImportConflict>>()
    conflicts.filter { it.kind == ConflictKind.IncomingExisting &&
        it.incomingIndex !in skipped.map { skippedRow -> skippedRow.incomingIndex }.toSet()
    }.forEach { edge ->
        if (configuration.overlaps == OverlapPolicy.PerItem && configuration.itemOverrides[edge.incomingIndex] == null) {
            issues += ImportIssue(ValidationKey.UnresolvedPerItemDecision, true, edge.incomingIndex, edge.existingId)
        }
        val decision = when (configuration.overlaps) {
            OverlapPolicy.SkipIncoming -> ItemDecision.Skip
            OverlapPolicy.ReplaceExisting -> ItemDecision.ReplaceExisting
            OverlapPolicy.KeepBoth -> ItemDecision.KeepBoth
            OverlapPolicy.PerItem -> configuration.itemOverrides[edge.incomingIndex]
                ?: ItemDecision.KeepBoth
        }
        if (decision == ItemDecision.Skip) {
            if (edge.incomingIndex !in skipped.map { it.incomingIndex })
                skipped += SkippedIncoming(edge.incomingIndex, SkipReason.Overlap)
        }
        if (edge.existingId != null && decision == ItemDecision.KeepBoth)
            keptByOld.getOrPut(edge.existingId) { mutableListOf() } += edge
        if (decision == ItemDecision.ReplaceExisting && edge.existingId != null) {
            if (edge.confidence == RelationConfidence.Uncertain) {
                issues += ImportIssue(ValidationKey.UncertainDestructiveOverlap, true, edge.incomingIndex, edge.existingId)
            } else destructive.getOrPut(edge.existingId) { linkedSetOf() } += edge.weeks
        }
    }
    if (configuration.replacementScope == ReplacementScope.WholeRow)
        destructive.forEach { (oldId, _) -> oldWorking[oldId]?.removed?.addAll(activeWeeks(oldWorking.getValue(oldId).course, configuration.maxWeek)) }
    else destructive.forEach { (oldId, weeks) -> oldWorking[oldId]?.removed?.addAll(weeks) }
    keptByOld.forEach { (oldId, edges) ->
        val removed = oldWorking[oldId]?.removed.orEmpty()
        edges.firstOrNull { edge -> edge.weeks.any { it in removed } }?.let { edge ->
            issues += ImportIssue(ValidationKey.ContradictoryDecisions, true, edge.incomingIndex, oldId)
        }
    }

    val replacements = oldWorking.values.filter { it.removed.isNotEmpty() }.map { working ->
        val remaining = activeWeeks(working.course, configuration.maxWeek) - working.removed
        ImportReplacement(working.course, working.removed.toSet(), remaining,
            remainingRows(working.course, remaining))
    }.sortedBy { it.original.id }
    val retainedOriginal = oldCourses.filter { it.id !in replacements.map { r -> r.original.id }.toSet() } +
        replacements.flatMap { it.remainingRows }
    val retained = if (configuration.destination == ImportDestination.New)
        retainedOriginal.map { it.copy(id = 0L, tableId = 0L) }
    else retainedOriginal
    val finalAccepted = accepted.filter { it.index !in skipped.map { s -> s.incomingIndex }.toSet() }
        .map { it.index to it.course }
    val incoming = isolateGroups(finalAccepted, retained).map { (_, row) -> row }
    fun validateCourse(course: CourseEntity, incomingIndex: Int? = null) {
        val courseId = course.id.takeIf { incomingIndex == null }
        if (course.startWeek < 1 || course.endWeek > configuration.maxWeek || course.startWeek > course.endWeek)
            issues += ImportIssue(ValidationKey.CourseWeekOutOfRange, true, incomingIndex, courseId)
        if (course.day !in 1..7)
            issues += ImportIssue(ValidationKey.InvalidCourseDay, true, incomingIndex, courseId)
        if (course.step < 1 || (course.startNode < 1 && courseWindow(
                course.copy(ownTime = false, isIrregularTime = false), finalTimeJson) == null))
            issues += ImportIssue(ValidationKey.InvalidCourseNode, true, incomingIndex, courseId)
        if (courseWindow(course, finalTimeJson) == null)
            issues += ImportIssue(ValidationKey.InvalidCourseTime, true, incomingIndex, courseId)
    }
    retainedOriginal.forEach { course ->
        if (course.id == 0L && configuration.destination == ImportDestination.Existing &&
            replacements.none { replacement -> course in replacement.remainingRows })
            issues += ImportIssue(ValidationKey.DuplicateCourseId, true)
        validateCourse(course)
    }
    finalAccepted.forEach { (index, course) -> validateCourse(course, index) }
    if (configuration.scheduleMode == ScheduleMode.Merge) {
        val declaredNodes = (TimeTableUtils.parseNodes(oldTable.timeJson) +
            TimeTableUtils.parseNodes(source.timeJson))
            .filter { it.start < it.end }.mapTo(hashSetOf()) { it.node }
        val requiredNodes = (retained + incoming)
            .filterNot { it.ownTime || it.isIrregularTime }
            .flatMap { course ->
                if (course.step < 1) emptyList()
                else (course.startNode until course.startNode + course.step).toList()
            }.toSet()
        if ((requiredNodes - declaredNodes).isNotEmpty())
            issues += ImportIssue(ValidationKey.InvalidSchedule, true)
    }
    val finalCourses = (retained + incoming).map { it.copy(tableId = finalTableId) }
        .sortedWith(compareBy<CourseEntity> { it.day }.thenBy { it.startNode }.thenBy { it.startWeek }
            .thenBy { it.courseName }.thenBy { it.id })
    val replacementOriginalIds = replacements.map { it.original.id }.toSet()
    val update = if (configuration.destination == ImportDestination.New) emptyList()
        else replacements.flatMap { it.remainingRows }.filter { it.id != 0L }
    val inserts = if (configuration.destination == ImportDestination.New) retained + incoming
        else replacements.flatMap { it.remainingRows }.filter { it.id == 0L } + incoming
    val deletes = if (configuration.destination == ImportDestination.Existing &&
        configuration.content == ImportContent.ImportOnly) existingTargetCourses else emptyList()
    val finalIssues = issues.distinct()
    val removedOriginals = if (configuration.destination == ImportDestination.New) emptyList()
        else oldCourses.filter { it.id in replacementOriginalIds && update.none { u -> u.id == it.id } }
    return ImportPlan(source, snapshot, configuration, finalTable, finalPeriod, periodOperation, periodBinding,
        finalCourses, retained, incoming, inserts, update,
        deletes + removedOriginals,
        duplicates.distinct(), skipped.distinct().sortedBy { it.incomingIndex }, conflicts.distinct(), replacements,
        finalIssues)
}
