package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.imports.DuplicatePolicy
import com.lingion.sleepy.data.imports.ImportContent
import com.lingion.sleepy.data.imports.ImportDestination
import com.lingion.sleepy.data.imports.ImportPreset
import com.lingion.sleepy.data.imports.ImportSnapshot
import com.lingion.sleepy.data.imports.ItemDecision
import com.lingion.sleepy.data.imports.OverlapPolicy
import com.lingion.sleepy.data.imports.ScheduleMode
import com.lingion.sleepy.data.imports.SharedScope
import com.lingion.sleepy.data.parser.ScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ImportDecisionStateTest {
    private val timeJson = """[{"node":1,"start":"08:00","end":"08:45"},{"node":2,"start":"08:55","end":"09:40"}]"""
    private val today = LocalDate.of(2026, 10, 7)
    private val period = PeriodTableEntity(id = 10, name = "Morning", nodesPerDay = 2, timeJson = timeJson)
    private val table = TimeTableEntity(id = 4, name = "Semester", startDate = "2026-09-07", nodesPerDay = 2,
        timeJson = "[]", periodTableId = 10, isDefault = true)
    private fun source(date: String = "2026-09-07", json: String = timeJson) = ScheduleParser.ParseResult(
        tableName = "Semester", startDate = date, courses = emptyList(), timeJson = json, nodesPerDay = 2, maxWeek = 16
    )
    private fun snapshot() = ImportSnapshot(listOf(table), listOf(period), emptyList())
    private fun configuration() = createImportDecisionConfiguration(source(), snapshot(), 4, "Imported", "Schedule", today)

    @Test fun `existing default skips overlaps and duplicates and uses hydrated base schedule`() {
        val result = configuration()
        assertEquals(ImportDestination.Existing, result.destination)
        assertEquals(ImportContent.Merge, result.content)
        assertEquals(DuplicatePolicy.Skip, result.duplicates)
        assertEquals(OverlapPolicy.SkipIncoming, result.overlaps)
        assertEquals(ScheduleMode.KeepBase, result.scheduleMode)
        assertEquals(timeJson, result.timeJson)
        assertEquals(2, result.nodesPerDay)
        assertEquals("Semester", result.name)
        assertTrue(result.isDefault)
    }

    @Test fun `no target selects new import only even when unrelated tables exist`() {
        val result = createImportDecisionConfiguration(source(), snapshot(), 0, "Imported", "Schedule", today)
        assertEquals(ImportDestination.New, result.destination)
        assertEquals(ImportContent.ImportOnly, result.content)
        assertEquals(0L, result.baseTableId)
        assertEquals(ScheduleMode.UseIncoming, result.scheduleMode)
        assertEquals("Semester2", result.name)
        assertEquals("Schedule", result.periodName)
    }

    @Test fun `missing source date seeds the actual current Monday instead of an epoch`() {
        val result = createImportDecisionConfiguration(source(date = ""), ImportSnapshot(emptyList(), emptyList(), emptyList()),
            0, "Imported", "Schedule", today)
        assertEquals("2026-10-05", result.startDate)
        assertFalse(result.dateInterpretationAcknowledged)
    }

    @Test fun `invalid source schedule starts blank editable slots and never parser fallback times`() {
        val result = createImportDecisionConfiguration(source(json = "broken"), snapshot(), 0, "Imported", "Schedule", today)
        assertEquals(ScheduleMode.Edit, result.scheduleMode)
        val rows = importDecisionSlots(result.timeJson)
        assertNotNull(rows)
        assertEquals(2, rows!!.size)
        assertTrue(rows.all { it.start.isEmpty() && it.end.isEmpty() })
        assertNull(importDecisionIncomingSchedule(source(json = "[{\"node\":1}]")))
        assertNull(importDecisionIncomingSchedule(source(json = "[]")))
        assertNull(importDecisionSlots("broken"))
    }

    @Test fun `legacy slots without nodes use array position while retaining declared clocks`() {
        val legacy = """[{"start":"08:10","end":"08:55"},{"start":"09:05","end":"09:50"}]"""
        val rows = importDecisionSlots(legacy)!!
        assertEquals(listOf(1, 2), rows.map { it.node })
        assertEquals(listOf("08:10", "09:05"), rows.map { it.start })
        assertEquals(listOf("08:55", "09:50"), rows.map { it.end })
        assertEquals(2, importDecisionIncomingSchedule(source(json = legacy))!!.nodesPerDay)
        assertNull(importDecisionSlots("""[{"start":"08:10"}]"""))
        assertNull(importDecisionSlots("""[{"end":"08:55"}]"""))
        assertNull(importDecisionSlots("""[{"node":"invalid","start":"08:10","end":"08:55"}]"""))
        assertNull(importDecisionSlots("""[{"node":2,"start":"08:10","end":"08:55"},{"start":"09:05","end":"09:50"}]"""))
        assertNull(importDecisionIncomingSchedule(source(json = """[{"start":"","end":""}]""")))
    }

    @Test fun `import only always selects an available overlap policy`() {
        val current = configuration()
        val changed = changeImportDecisionContent(ImportContent.ImportOnly, current)
        assertEquals(OverlapPolicy.KeepBoth, changed.overlaps)
        assertEquals(OverlapPolicy.KeepBoth, createImportDecisionConfiguration(source(), snapshot(), 0,
            "Imported", "Schedule", today).overlaps)
        assertEquals(OverlapPolicy.KeepBoth, applyImportDecisionPreset(ImportPreset.ReplaceExistingCourses,
            current, source(), snapshot(), "Imported", "Schedule", today).overlaps)
        val legacy = current.copy(content = ImportContent.ImportOnly, overlaps = OverlapPolicy.ReplaceExisting)
        assertEquals(OverlapPolicy.KeepBoth, decodeImportDecisionConfiguration(encodeImportDecisionConfiguration(legacy))!!.overlaps)
        assertEquals(OverlapPolicy.KeepBoth, changeImportDecisionDestination(ImportDestination.New,
            legacy, source(), snapshot(), "Imported", "Schedule", today).overlaps)
        assertEquals(OverlapPolicy.PerItem, changeImportDecisionContent(ImportContent.ImportOnly,
            current.copy(overlaps = OverlapPolicy.PerItem)).overlaps)
    }

    @Test fun `presets fill configuration only and clear source record overrides`() {
        val draft = configuration().copy(itemOverrides = mapOf(0 to ItemDecision.ReplaceExisting),
            overlaps = OverlapPolicy.PerItem, duplicates = DuplicatePolicy.Keep)
        val add = applyImportDecisionPreset(ImportPreset.AddToExisting, draft, source(), snapshot(), "Imported", "Schedule", today)
        assertEquals(ImportDestination.Existing, add.destination)
        assertEquals(ImportContent.Merge, add.content)
        assertEquals(OverlapPolicy.SkipIncoming, add.overlaps)
        assertEquals(DuplicatePolicy.Skip, add.duplicates)
        assertEquals(ScheduleMode.KeepBase, add.scheduleMode)
        assertTrue(add.itemOverrides.isEmpty())
        val replace = applyImportDecisionPreset(ImportPreset.ReplaceExistingCourses, draft, source(), snapshot(), "Imported", "Schedule", today)
        assertEquals(ImportContent.ImportOnly, replace.content)
        val fresh = applyImportDecisionPreset(ImportPreset.NewWithImportedCourses, draft, source(), snapshot(), "Imported", "Schedule", today)
        assertEquals(ImportDestination.New, fresh.destination)
        assertEquals(ImportContent.ImportOnly, fresh.content)
        assertEquals(ScheduleMode.UseIncoming, fresh.scheduleMode)
        assertTrue(fresh.itemOverrides.isEmpty())
        assertEquals(mapOf(0 to ItemDecision.ReplaceExisting), draft.itemOverrides)
    }

    @Test fun `changing destination preserves independent content and policies`() {
        val current = configuration().copy(content = ImportContent.Merge, duplicates = DuplicatePolicy.Keep,
            overlaps = OverlapPolicy.KeepBoth, itemOverrides = mapOf(2 to ItemDecision.Skip))
        val result = changeImportDecisionDestination(ImportDestination.New, current, source(), snapshot(), "Imported", "Schedule", today)
        assertEquals(ImportContent.Merge, result.content)
        assertEquals(DuplicatePolicy.Keep, result.duplicates)
        assertEquals(OverlapPolicy.KeepBoth, result.overlaps)
        assertEquals("Semester2", result.name)
        assertTrue(result.itemOverrides.isEmpty())
    }

    @Test fun `configuration string round trip retains all decisions and multilingual metadata`() {
        val draft = configuration().copy(name = "秋 • 秋学期", periodName = "午後の時間割", startDate = "2026-10-05",
            destination = ImportDestination.New, content = ImportContent.Merge, overlaps = OverlapPolicy.PerItem,
            itemOverrides = mapOf(0 to ItemDecision.Skip, 7 to ItemDecision.KeepBoth, 11 to ItemDecision.ReplaceExisting),
            scheduleMode = ScheduleMode.BindExisting, selectedPeriodTableId = 10, sharedScope = SharedScope.Shared,
            dateInterpretationAcknowledged = true)
        assertEquals(draft, decodeImportDecisionConfiguration(encodeImportDecisionConfiguration(draft)))
        assertNull(decodeImportDecisionConfiguration("invalid draft"))
    }

    @Test fun `new schedule name suggestions include the pending course table name`() {
        val result = createImportDecisionConfiguration(source(), ImportSnapshot(emptyList(), emptyList(), emptyList()),
            0, "Imported", "Semester", today)
        assertEquals("Semester", result.name)
        assertEquals("Semester2", result.periodName)
    }

    @Test fun `shared edit names every bound table and private edit affects no other table`() {
        val another = table.copy(id = 5, name = "Evening")
        val snap = snapshot().copy(tables = listOf(table, another))
        val shared = configuration().copy(scheduleMode = ScheduleMode.Edit, sharedScope = SharedScope.Shared)
        assertEquals(listOf("Semester", "Evening"), importDecisionAffectedTables(shared, snap))
        assertTrue(importDecisionAffectedTables(shared.copy(sharedScope = SharedScope.ThisTable), snap).isEmpty())
    }

    @Test fun `submit gate is synchronous blocks double click and cannot resubmit a committed plan`() {
        val gate = ImportDecisionSubmitGate()
        assertFalse(gate.begin(isCurrentPlan = false))
        assertTrue(gate.begin(isCurrentPlan = true))
        assertFalse(gate.begin(isCurrentPlan = true))
        gate.finish(committed = false)
        assertTrue(gate.begin(isCurrentPlan = true))
        gate.finish(committed = true)
        assertFalse(gate.begin(isCurrentPlan = true))
        assertTrue(gate.committed)
    }
}
