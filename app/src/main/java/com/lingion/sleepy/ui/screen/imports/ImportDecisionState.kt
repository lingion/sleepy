package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.imports.DuplicatePolicy
import com.lingion.sleepy.data.imports.ImportConfiguration
import com.lingion.sleepy.data.imports.ImportContent
import com.lingion.sleepy.data.imports.ImportDestination
import com.lingion.sleepy.data.imports.ImportPreset
import com.lingion.sleepy.data.imports.ImportSnapshot
import com.lingion.sleepy.data.imports.ImportIssue
import com.lingion.sleepy.data.imports.ValidationKey
import com.lingion.sleepy.data.imports.OverlapPolicy
import com.lingion.sleepy.data.imports.ReplacementScope
import com.lingion.sleepy.data.imports.ScheduleMode
import com.lingion.sleepy.data.imports.SharedScope
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private val importDecisionJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal fun encodeImportDecisionConfiguration(configuration: ImportConfiguration): String =
    importDecisionJson.encodeToString(configuration)

internal fun decodeImportDecisionConfiguration(serialized: String?): ImportConfiguration? =
    serialized?.takeIf { it.isNotBlank() }?.let {
        runCatching { normalizeImportDecisionConfiguration(importDecisionJson.decodeFromString<ImportConfiguration>(it)) }.getOrNull()
    }

internal fun normalizeImportDecisionConfiguration(configuration: ImportConfiguration): ImportConfiguration =
    if (configuration.content == ImportContent.ImportOnly && configuration.overlaps !in
        setOf(OverlapPolicy.KeepBoth, OverlapPolicy.PerItem)) configuration.copy(overlaps = OverlapPolicy.KeepBoth)
    else configuration

internal fun changeImportDecisionContent(content: ImportContent, current: ImportConfiguration): ImportConfiguration =
    normalizeImportDecisionConfiguration(current.copy(content = content, itemOverrides = emptyMap(),
        dateInterpretationAcknowledged = false))

/** Legacy array positions supply missing nodes; missing clocks never become invented times. */
internal fun importDecisionSlots(timeJson: String): List<TimeSlotRow>? = runCatching {
    importDecisionJson.parseToJsonElement(timeJson).jsonArray.mapIndexed { index, element ->
        val row = element.jsonObject
        val node = if ("node" in row) requireNotNull(row["node"]?.jsonPrimitive?.intOrNull) else index + 1
        val start = requireNotNull(row["start"]?.jsonPrimitive?.contentOrNull)
        val end = requireNotNull(row["end"]?.jsonPrimitive?.contentOrNull)
        val edge = when (row["edge"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
            null, "" -> null
            "before" -> TimeTableUtils.EdgeClass.Before
            "after" -> TimeTableUtils.EdgeClass.After
            else -> error("Unknown edge slot")
        }
        TimeSlotRow(node, start, end, edge)
    }.also { rows -> require(rows.map { it.node }.distinct().size == rows.size) }
}.getOrNull()

internal data class ImportDecisionSchedule(val timeJson: String, val nodesPerDay: Int)

internal fun importDecisionIncomingSchedule(source: ScheduleParser.ParseResult): ImportDecisionSchedule? {
    val json = source.timeJson.ifBlank { source.periodTable?.timeJson.orEmpty() }
    val rows = importDecisionSlots(json)?.takeIf { it.isNotEmpty() } ?: return null
    if (rows.any { !validImportDecisionTime(it.start, it.end) }) return null
    val standard = rows.filter { it.edgeClass == null }.sortedBy { it.node }
    if (standard.isEmpty() || standard.map { it.node } != (1..standard.size).toList()) return null
    return ImportDecisionSchedule(json, standard.size)
}

internal fun validImportDecisionTime(start: String, end: String): Boolean {
    val from = runCatching { LocalTime.parse(start) }.getOrNull() ?: return false
    val to = runCatching { LocalTime.parse(end) }.getOrNull() ?: return false
    return from < to
}

internal fun importDecisionBase(configuration: ImportConfiguration, snapshot: ImportSnapshot): TimeTableEntity? =
    snapshot.tables.firstOrNull { it.id == configuration.baseTableId }?.let { table ->
        table.hydratedWith(snapshot.periodTables.firstOrNull { it.id == table.periodTableId })
    }

private fun uniqueImportDecisionNames(
    name: String,
    source: ScheduleParser.ParseResult,
    snapshot: ImportSnapshot,
    defaultPeriodName: String
): Pair<String, String> {
    val tableNames = snapshot.tables.map { it.name }
    val periodNames = snapshot.periodTables.map { it.name }
    val tableName = TimeTableUtils.suggestUniqueName(name, tableNames, periodNames)
    val periodName = TimeTableUtils.suggestUniqueName(
        source.periodTable?.name?.takeIf { it.isNotBlank() } ?: defaultPeriodName,
        tableNames + tableName, periodNames
    )
    return tableName to periodName
}

internal fun suggestImportDecisionPeriodName(
    configuration: ImportConfiguration,
    source: ScheduleParser.ParseResult,
    snapshot: ImportSnapshot,
    defaultPeriodName: String
): String = TimeTableUtils.suggestUniqueName(
    source.periodTable?.name?.takeIf { it.isNotBlank() } ?: defaultPeriodName,
    snapshot.tables.map { it.name } + configuration.name,
    snapshot.periodTables.map { it.name }
)

internal fun createImportDecisionConfiguration(
    source: ScheduleParser.ParseResult,
    snapshot: ImportSnapshot,
    initialTargetId: Long,
    defaultTableName: String,
    defaultPeriodName: String,
    today: LocalDate = LocalDate.now()
): ImportConfiguration {
    val target = snapshot.tables.firstOrNull { it.id == initialTargetId }
    if (target != null) {
        val period = snapshot.periodTables.firstOrNull { it.id == target.periodTableId }
        return ImportConfiguration.forExisting(target, period).copy(
            destination = ImportDestination.Existing,
            content = ImportContent.Merge,
            duplicates = DuplicatePolicy.Skip,
            overlaps = OverlapPolicy.SkipIncoming,
            replacementScope = ReplacementScope.OverlapWeeks,
            scheduleMode = ScheduleMode.KeepBase,
            sharedScope = SharedScope.ThisTable,
            periodName = TimeTableUtils.suggestUniqueName(defaultPeriodName,
                snapshot.tables.map { it.name }, snapshot.periodTables.map { it.name }),
            dateInterpretationAcknowledged = importDecisionMonday(source.startDate) != null &&
                importDecisionMonday(source.startDate) == importDecisionMonday(target.startDate)
        )
    }
    val incoming = importDecisionIncomingSchedule(source)
    val names = uniqueImportDecisionNames(source.tableName.ifBlank { defaultTableName }, source, snapshot, defaultPeriodName)
    val blankNodeCount = maxOf(source.nodesPerDay,
        source.courses.maxOfOrNull { it.startNode + it.step - 1 } ?: 0, 1)
    return ImportConfiguration(
        baseTableId = 0,
        destination = ImportDestination.New,
        content = ImportContent.ImportOnly,
        duplicates = DuplicatePolicy.Skip,
        overlaps = OverlapPolicy.KeepBoth,
        replacementScope = ReplacementScope.OverlapWeeks,
        name = names.first,
        startDate = source.startDate.ifBlank { today.with(DayOfWeek.MONDAY).toString() },
        maxWeek = source.maxWeek.takeIf { it > 0 } ?: maxOf(source.courses.maxOfOrNull { it.endWeek } ?: 0, 1),
        nodesPerDay = incoming?.nodesPerDay ?: blankNodeCount,
        timeJson = incoming?.timeJson ?: TimeTableUtils.buildTimeJsonFromRows(
            (1..blankNodeCount).map { TimeSlotRow(it, "", "") }
        ),
        scheduleMode = if (incoming != null) ScheduleMode.UseIncoming else ScheduleMode.Edit,
        periodName = names.second,
        isDefault = snapshot.tables.isEmpty(),
        dateInterpretationAcknowledged = importDecisionMonday(source.startDate) != null
    )
}

/** Changing destination does not covertly choose a course, duplicate, or overlap policy. */
internal fun changeImportDecisionDestination(
    destination: ImportDestination,
    current: ImportConfiguration,
    source: ScheduleParser.ParseResult,
    snapshot: ImportSnapshot,
    defaultTableName: String,
    defaultPeriodName: String,
    today: LocalDate = LocalDate.now()
): ImportConfiguration {
    if (destination == current.destination) return normalizeImportDecisionConfiguration(current)
    val targetId = if (destination == ImportDestination.Existing) {
        current.baseTableId.takeIf { id -> snapshot.tables.any { it.id == id } }
            ?: snapshot.tables.firstOrNull()?.id ?: 0
    } else 0
    val seeded = createImportDecisionConfiguration(source, snapshot, targetId, defaultTableName, defaultPeriodName, today)
    return normalizeImportDecisionConfiguration(seeded.copy(
        destination = destination,
        baseTableId = if (destination == ImportDestination.New) current.baseTableId else targetId,
        content = current.content,
        duplicates = current.duplicates,
        overlaps = current.overlaps,
        replacementScope = current.replacementScope,
        itemOverrides = emptyMap()
    ))
}

internal fun changeImportDecisionBase(
    baseTableId: Long,
    current: ImportConfiguration,
    source: ScheduleParser.ParseResult,
    snapshot: ImportSnapshot,
    defaultTableName: String,
    defaultPeriodName: String
): ImportConfiguration {
    if (current.baseTableId == baseTableId) return normalizeImportDecisionConfiguration(current)
    val selected = createImportDecisionConfiguration(source, snapshot, baseTableId, defaultTableName, defaultPeriodName)
    return normalizeImportDecisionConfiguration(if (current.destination == ImportDestination.Existing) selected.copy(
        content = current.content,
        duplicates = current.duplicates,
        overlaps = current.overlaps,
        replacementScope = current.replacementScope,
        itemOverrides = emptyMap()
    ) else current.copy(
        baseTableId = baseTableId,
        itemOverrides = emptyMap(),
        dateInterpretationAcknowledged = false,
        sharedScope = SharedScope.ThisTable,
        selectedPeriodTableId = null,
        timeJson = if (current.scheduleMode == ScheduleMode.KeepBase) selected.timeJson else current.timeJson,
        nodesPerDay = if (current.scheduleMode == ScheduleMode.KeepBase) selected.nodesPerDay else current.nodesPerDay,
        smartConfigJson = if (current.scheduleMode == ScheduleMode.KeepBase) selected.smartConfigJson else current.smartConfigJson
    ))
}

/** Presets populate the form; submission remains a separate, single final action. */
internal fun applyImportDecisionPreset(
    preset: ImportPreset,
    current: ImportConfiguration,
    source: ScheduleParser.ParseResult,
    snapshot: ImportSnapshot,
    defaultTableName: String,
    defaultPeriodName: String,
    today: LocalDate = LocalDate.now()
): ImportConfiguration {
    val targetId = if (preset == ImportPreset.NewWithImportedCourses) 0 else
        current.baseTableId.takeIf { id -> snapshot.tables.any { it.id == id } }
            ?: snapshot.tables.firstOrNull()?.id ?: 0
    val seeded = createImportDecisionConfiguration(source, snapshot, targetId, defaultTableName, defaultPeriodName, today)
    return normalizeImportDecisionConfiguration(seeded.copy(
        destination = if (preset == ImportPreset.NewWithImportedCourses) ImportDestination.New else ImportDestination.Existing,
        content = if (preset == ImportPreset.AddToExisting) ImportContent.Merge else ImportContent.ImportOnly,
        baseTableId = targetId,
        itemOverrides = emptyMap()
    ))
}

internal fun importDecisionMetadataNeedsAttention(issues: List<ImportIssue>): Boolean = issues.any {
    it.blocking && it.key in setOf(ValidationKey.InvalidName, ValidationKey.NameCollision,
        ValidationKey.InvalidDate, ValidationKey.DateInterpretationAcknowledgementRequired,
        ValidationKey.InvalidMaxWeek, ValidationKey.CourseWeekOutOfRange)
}

internal fun importDecisionScheduleNeedsAttention(issues: List<ImportIssue>): Boolean = issues.any {
    it.blocking && it.key in setOf(ValidationKey.InvalidNodesPerDay, ValidationKey.InvalidCourseNode,
        ValidationKey.InvalidCourseTime, ValidationKey.MissingSelectedPeriodTable,
        ValidationKey.MissingBoundPeriodTable, ValidationKey.InvalidSchedule, ValidationKey.InvalidPeriodScope,
        ValidationKey.NameCollision)
}

internal fun importDecisionMonday(date: String): String? =
    runCatching { LocalDate.parse(date).with(DayOfWeek.MONDAY).toString() }.getOrNull()

/** Only genuinely shared edits list other affected tables. Binding by itself changes no owner. */
internal fun importDecisionAffectedTables(configuration: ImportConfiguration, snapshot: ImportSnapshot): List<String> {
    if (configuration.sharedScope != SharedScope.Shared || configuration.scheduleMode != ScheduleMode.Edit) return emptyList()
    val ownerId = configuration.selectedPeriodTableId
        ?: snapshot.tables.firstOrNull { it.id == configuration.baseTableId }?.periodTableId
        ?: return emptyList()
    return snapshot.tables.filter { it.periodTableId == ownerId }.map { it.name }
}

internal fun importDecisionCourseTime(course: CourseEntity, timeJson: String): Pair<String, String>? {
    if (course.ownTime || course.isIrregularTime) {
        return if (validImportDecisionTime(course.startTime, course.endTime)) course.startTime to course.endTime else null
    }
    val rows = importDecisionSlots(timeJson)?.associateBy { it.node } ?: return null
    val endNode = course.startNode + course.step - 1
    if (course.step < 1 || (course.startNode..endNode).any { rows[it] == null }) return null
    val start = rows[course.startNode]?.start ?: return null
    val end = rows[endNode]?.end ?: return null
    return if (validImportDecisionTime(start, end)) start to end else null
}

internal fun importDecisionWeekRanges(weeks: Set<Int>): String {
    val sorted = weeks.sorted()
    if (sorted.isEmpty()) return ""
    val ranges = mutableListOf<String>()
    var start = sorted.first()
    var end = start
    sorted.drop(1).forEach { week ->
        if (week == end + 1) end = week else {
            ranges += if (start == end) "$start" else "$start–$end"
            start = week
            end = week
        }
    }
    ranges += if (start == end) "$start" else "$start–$end"
    return ranges.joinToString(", ")
}

/** Acquired on the click stack, before starting a coroutine or waiting for recomposition. */
internal class ImportDecisionSubmitGate {
    var applying: Boolean = false
        private set
    var committed: Boolean = false
        private set

    @Synchronized fun begin(isCurrentPlan: Boolean): Boolean {
        if (!isCurrentPlan || applying || committed) return false
        applying = true
        return true
    }

    @Synchronized fun finish(committed: Boolean) {
        this.committed = this.committed || committed
        applying = false
    }
}
