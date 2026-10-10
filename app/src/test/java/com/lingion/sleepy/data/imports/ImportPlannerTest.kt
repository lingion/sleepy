package com.lingion.sleepy.data.imports

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.*
import org.junit.Test

class ImportPlannerTest {
    private val times = TimeTableUtils.DEFAULT_TIME_JSON
    private val base = TimeTableEntity(id = 5, name = "Old", startDate = "2026-09-07", maxWeek = 16)
    private fun row(name: String = "Math", id: Long = 0, group: String = "g", table: Long = 0,
                    first: Int = 1, last: Int = 16, day: Int = 1, node: Int = 1, step: Int = 2,
                    type: Int = 0) = CourseEntity(id = id, groupId = group, tableId = table,
        courseName = name, day = day, startNode = node, step = step, startWeek = first,
        endWeek = last, type = type, color = "#FFFF0000")
    private fun source(vararg rows: CourseEntity, json: String = times,
                       authoritative: Boolean = false) = ScheduleParser.ParseResult(
        "New", "2026-09-07", rows.toList(), timeJson = json, nodesPerDay = 12,
        maxWeek = 16, groupIdsAuthoritative = authoritative)
    private fun snapshot(vararg rows: CourseEntity, table: TimeTableEntity = base,
                         periods: List<PeriodTableEntity> = emptyList(),
                         tables: List<TimeTableEntity> = listOf(table)) =
        ImportSnapshot(tables, periods, rows.toList())
    private fun config(destination: ImportDestination = ImportDestination.Existing,
                       content: ImportContent = ImportContent.Merge,
                       duplicates: DuplicatePolicy = DuplicatePolicy.Skip,
                       overlaps: OverlapPolicy = OverlapPolicy.KeepBoth,
                       replacementScope: ReplacementScope = ReplacementScope.OverlapWeeks,
                       overrides: Map<Int, ItemDecision> = emptyMap(),
                       mode: ScheduleMode = ScheduleMode.KeepBase) = ImportConfiguration(
        baseTableId = 5, destination = destination, content = content,
        duplicates = duplicates, overlaps = overlaps, replacementScope = replacementScope,
        itemOverrides = overrides, name = "Confirmed", startDate = "2026-09-07",
        maxWeek = 16, nodesPerDay = 12, timeJson = times, scheduleMode = mode,
        dateInterpretationAcknowledged = true)

    @Test fun everyDestinationContentDuplicateCombinationHasStableOperations() {
        val old = row(id = 11, table = 5)
        val duplicate = row(group = "import")
        val fresh = row("Biology", group = "other", day = 2)
        for (destination in ImportDestination.entries) for (content in ImportContent.entries)
            for (duplicates in DuplicatePolicy.entries) {
                val p = planImport(source(duplicate, fresh), snapshot(old),
                    config(destination, content, duplicates))
                assertTrue("$destination $content $duplicates: ${p.issues}", p.canSubmit)
                val oldRetained = content == ImportContent.Merge
                val skipDuplicate = oldRetained && duplicates == DuplicatePolicy.Skip
                assertEquals(if (oldRetained) 1 else 0, p.retainedCourses.size)
                assertEquals(if (skipDuplicate) 1 else 0, p.duplicates.count { it.skipped })
                assertEquals(if (skipDuplicate) 1 else 2, p.incomingCourses.size)
                assertEquals(if (destination == ImportDestination.Existing && !oldRetained) 1 else 0,
                    p.deleteCourses.size)
                if (destination == ImportDestination.New && oldRetained) {
                    assertEquals(0L, p.retainedCourses.single().id)
                    assertEquals(0L, p.retainedCourses.single().tableId)
                }
                assertEquals(if (destination == ImportDestination.New) 0L else 5L, p.finalTable.id)
                assertEquals(p.retainedCourses.size + p.incomingCourses.size, p.finalCourses.size)
            }
    }

    @Test fun replacementRemovesWholeOccurrencesOnlyInActualOverlapWeeksAndSplitsMiddle() {
        val old = row(id = 11, table = 5).copy(note = "manual", alias = "M", credit = 2.5f,
            level = 3, colorMode = 2)
        val incoming = row("Physics", first = 7, last = 8, node = 2, step = 2)
        val p = planImport(source(incoming), snapshot(old),
            config(overlaps = OverlapPolicy.ReplaceExisting))
        assertTrue(p.issues.toString(), p.canSubmit)
        assertEquals(setOf(7, 8), p.replacements.single().removedWeeks)
        val residuals = p.replacements.single().remainingRows
        assertEquals(listOf(1 to 6, 9 to 16), residuals.map { it.startWeek to it.endWeek })
        assertEquals(listOf(11L, 0L), residuals.map { it.id })
        assertTrue(residuals.all { it.note == "manual" && it.alias == "M" && it.credit == 2.5f &&
            it.level == 3 && it.groupId == "g" && it.colorMode == 2 && it.startNode == 1 && it.step == 2 })
        assertEquals(1, p.updateCourses.size)
        assertEquals(2, p.insertCourses.size)
        assertEquals(0, p.deleteCourses.size)
        assertEquals(1, p.conflicts.count { it.kind == ConflictKind.IncomingExisting })
    }

    @Test fun oddEvenAndTouchingEndpointsAreNotConflicts() {
        val odd = row(id = 11, table = 5, type = 1)
        val even = row("New", first = 2, last = 10, type = 2)
        val touching = row("After").copy(ownTime = true, startTime = "09:40", endTime = "10:00")
        val p = planImport(source(even, touching), snapshot(odd),
            config(overlaps = OverlapPolicy.ReplaceExisting))
        assertTrue(p.issues.toString(), p.canSubmit)
        assertTrue(p.conflicts.isEmpty())
        assertTrue(p.replacements.isEmpty())
    }

    @Test fun privateTimeOverridesNodeAndHalfOpenEndpoints() {
        val old = row(id = 11, table = 5).copy(ownTime = true, startTime = "08:30", endTime = "09:00")
        val p = planImport(source(row("New").copy(isIrregularTime = true, startTime = "08:59",
            endTime = "09:30")), snapshot(old), config(overlaps = OverlapPolicy.ReplaceExisting))
        assertEquals((1..16).toSet(), p.replacements.single().removedWeeks)
        assertEquals(listOf(11L), p.deleteCourses.map { it.id })
        assertEquals(1, p.finalCourses.size)
    }

    @Test fun unionOfMultipleIncomingReplacementsIsIndependentOfSourceOrder() {
        val old = row(id = 11, table = 5)
        val a = row("A", first = 2, last = 3)
        val b = row("B", first = 7, last = 8)
        val one = planImport(source(a, b), snapshot(old), config(overlaps = OverlapPolicy.ReplaceExisting))
        val two = planImport(source(b, a), snapshot(old), config(overlaps = OverlapPolicy.ReplaceExisting))
        assertEquals(setOf(2, 3, 7, 8), one.replacements.single().removedWeeks)
        assertEquals(one.replacements.single().remainingRows, two.replacements.single().remainingRows)
        assertEquals(listOf(1 to 1, 4 to 6, 9 to 16),
            one.replacements.single().remainingRows.map { it.startWeek to it.endWeek })
        assertEquals(2, one.conflicts.size)
    }

    @Test fun contradictoryPerItemDecisionsOnSameOldOccurrenceBlockSubmission() {
        val old = row(id = 11, table = 5)
        val p = planImport(source(row("A"), row("B")), snapshot(old),
            config(overlaps = OverlapPolicy.PerItem,
                overrides = mapOf(0 to ItemDecision.ReplaceExisting, 1 to ItemDecision.KeepBoth)))
        assertFalse(p.canSubmit)
        assertTrue(p.issues.any { it.key == ValidationKey.ContradictoryDecisions })
        assertEquals(2, p.conflicts.count { it.kind == ConflictKind.IncomingExisting })
    }

    @Test fun sourceInternalDuplicatesAndOverlapsAreSeparateAndNeverReplaceEachOther() {
        val a = row(group = "a")
        val duplicate = row(group = "a")
        val overlapping = row("Physics", group = "b", node = 2)
        val p = planImport(source(a, duplicate, overlapping, authoritative = true), snapshot(),
            config(destination = ImportDestination.New))
        assertTrue(p.canSubmit)
        assertEquals(listOf(1), p.skipped.map { it.incomingIndex })
        assertEquals(1, p.duplicates.count { it.otherIncomingIndex != null })
        assertEquals(3, p.conflicts.count { it.kind == ConflictKind.IncomingInternal })
        assertEquals(2, p.incomingCourses.size)
        val separateGroups = planImport(source(a, duplicate.copy(groupId = "other"), authoritative = true),
            snapshot(), config(destination = ImportDestination.New))
        assertTrue(separateGroups.skipped.isEmpty())
    }

    @Test fun retainedManualMetadataStopsDuplicateSuppressionAndGroupCollisionIsIsolated() {
        val old = row(id = 11, table = 5).copy(alias = "manual", note = "important")
        val incoming = row("Physics", group = "g", day = 2)
        val p = planImport(source(row(), incoming, authoritative = true), snapshot(old), config())
        assertEquals(0, p.skipped.size)
        assertEquals("g", p.retainedCourses.single().groupId)
        assertNotEquals("g", p.incomingCourses.last().groupId)
        assertEquals(p, planImport(source(row(), incoming, authoritative = true), snapshot(old), config()))
    }

    @Test fun missingTimeBlocksDestructiveReplacementInsteadOfDeletingByNodeGuess() {
        val oldTable = base.copy(timeJson = "[]", nodesPerDay = 2)
        val old = row(id = 11, table = 5, node = 2)
        val p = planImport(source(row("Physics", node = 2), json = "[]"),
            snapshot(old, table = oldTable), config(overlaps = OverlapPolicy.ReplaceExisting,
                mode = ScheduleMode.UseIncoming).copy(timeJson = "[]", nodesPerDay = 2))
        assertFalse(p.canSubmit)
        assertTrue(p.conflicts.any { it.confidence == RelationConfidence.Uncertain })
        assertTrue(p.issues.any { it.key == ValidationKey.UncertainDestructiveOverlap })
        assertTrue(p.deleteCourses.isEmpty())
    }

    @Test fun invalidDateMaxWeekNodeAndOverrideAreStructuredBlockingIssues() {
        val p = planImport(source(row(first = 17, last = 18)), snapshot(),
            config().copy(startDate = "2026-02-31", maxWeek = 0,
                itemOverrides = mapOf(99 to ItemDecision.Skip)))
        assertFalse(p.canSubmit)
        assertTrue(p.issues.map { it.key }.containsAll(listOf(ValidationKey.InvalidDate,
            ValidationKey.InvalidMaxWeek, ValidationKey.CourseWeekOutOfRange,
            ValidationKey.UnknownIncomingIndex)))
    }

    @Test fun dateIsNormalizedToMondayAndCoordinateChangeRequiresAcknowledgement() {
        val normalized = planImport(source(row()), snapshot(),
            config().copy(startDate = "2026-09-09"))
        assertEquals("2026-09-07", normalized.finalTable.startDate)
        assertTrue(normalized.issues.any { it.key == ValidationKey.DateNormalized })
        val changed = planImport(source(row()), snapshot(),
            config().copy(startDate = "2026-09-14", dateInterpretationAcknowledged = false))
        assertFalse(changed.canSubmit)
        assertTrue(changed.issues.any { it.key == ValidationKey.DateInterpretationAcknowledgementRequired })
    }

    @Test fun scheduleBindingDefaultsToThisTableCopyAndNewKeepBaseClonesBoundPeriod() {
        val period = PeriodTableEntity(id = 8, name = "Shared", timeJson = times)
        val bound = base.copy(periodTableId = 8, timeJson = "[]")
        val snap = snapshot(row(id = 11, table = 5), table = bound, periods = listOf(period))
        val updated = planImport(source(row("New")), snap,
            config(mode = ScheduleMode.Edit).copy(timeJson = times))
        assertEquals(PeriodOperation.Insert, updated.periodOperation)
        assertEquals(0L, updated.finalPeriodTable?.id)
        assertEquals("[]", updated.finalTable.timeJson)
        assertEquals(bound.nodesPerDay, updated.finalTable.nodesPerDay)
        assertNull(updated.finalTable.periodTableId)
        val keepBase = planImport(source(), snap,
            config(destination = ImportDestination.Existing, content = ImportContent.Merge))
        assertEquals("[]", keepBase.finalTable.timeJson)
        assertEquals(bound.nodesPerDay, keepBase.finalTable.nodesPerDay)
        assertEquals(8L, keepBase.finalTable.periodTableId)
        assertEquals(period, keepBase.finalPeriodTable)
        assertEquals(PeriodOperation.None, keepBase.periodOperation)
        assertEquals(8L, snap.periodTables.single().id)
        val newTable = planImport(source(row("New")), snap,
            config(destination = ImportDestination.New))
        assertEquals(PeriodOperation.Insert, newTable.periodOperation)
        assertEquals(0L, newTable.finalPeriodTable?.id)
        assertEquals(PeriodBinding.PlannedPeriod, newTable.periodBinding)
        val shared = planImport(source(row("New")), snap,
            config(mode = ScheduleMode.Edit).copy(sharedScope = SharedScope.Shared, timeJson = times))
        assertEquals(PeriodOperation.Update, shared.periodOperation)
        assertEquals(8L, shared.finalPeriodTable?.id)
    }

    @Test fun boundKeepBasePreservesRawColumnsAndPreBindSnapshot() {
        val rawTime = """[{"node":1,"start":"06:00","end":"06:45"}]"""
        val raw = TimeTableEntity.snapshotForBind(base.copy(timeJson = rawTime,
            nodesPerDay = 1, smartConfigJson = "raw-smart"), 8)
        val owner = PeriodTableEntity(id = 8, name = "Shared", timeJson = times,
            nodesPerDay = 12, smartConfigJson = "owner-smart")
        val snap = snapshot(table = raw, periods = listOf(owner))
        val config = ImportConfiguration.forExisting(raw, owner)
        val p = planImport(source(), snap, config)
        assertTrue(p.issues.toString(), p.canSubmit)
        assertEquals(raw, p.finalTable)
        assertEquals(owner, p.finalPeriodTable)
        assertEquals(PeriodOperation.None, p.periodOperation)
        assertEquals(PeriodBinding.Existing, p.periodBinding)
    }

    @Test fun sharedEditChangesPeriodButNeverOverwritesBoundRawColumns() {
        val raw = TimeTableEntity.snapshotForBind(base.copy(timeJson = "[]",
            nodesPerDay = 2, smartConfigJson = "raw-smart"), 8)
        val owner = PeriodTableEntity(id = 8, name = "Shared", timeJson = times,
            smartConfigJson = "owner-smart")
        val selectedTime = """[{"node":1,"start":"10:00","end":"10:45"}]"""
        val p = planImport(source(), snapshot(table = raw, periods = listOf(owner)),
            ImportConfiguration.forExisting(raw, owner).copy(
                scheduleMode = ScheduleMode.Edit, sharedScope = SharedScope.Shared,
                timeJson = selectedTime, nodesPerDay = 1, smartConfigJson = "edited-smart"))
        assertTrue(p.issues.toString(), p.canSubmit)
        assertEquals(raw, p.finalTable)
        assertEquals(PeriodOperation.Update, p.periodOperation)
        assertEquals(selectedTime, p.finalPeriodTable?.timeJson)
        assertEquals("edited-smart", p.finalPeriodTable?.smartConfigJson)
    }

    @Test fun firstBindCapturesRawColumnsAndRebindDoesNotRefreshSnapshot() {
        val raw = base.copy(timeJson = "[]", nodesPerDay = 2, smartConfigJson = "manual")
        val firstPeriod = PeriodTableEntity(id = 8, name = "Shared", timeJson = times)
        val secondPeriod = firstPeriod.copy(id = 9, name = "Other")
        val first = planImport(source(), snapshot(table = raw, periods = listOf(firstPeriod, secondPeriod)),
            ImportConfiguration.forExisting(raw).copy(scheduleMode = ScheduleMode.BindExisting,
                selectedPeriodTableId = 8))
        assertEquals(TimeTableEntity.snapshotForBind(raw, 8), first.finalTable)
        val rebound = planImport(source(),
            snapshot(table = first.finalTable, periods = listOf(firstPeriod, secondPeriod)),
            ImportConfiguration.forExisting(first.finalTable, firstPeriod).copy(
                scheduleMode = ScheduleMode.BindExisting, selectedPeriodTableId = 9))
        assertEquals(first.finalTable.copy(periodTableId = 9), rebound.finalTable)
        assertEquals(first.finalTable.preBindSnapshotJson, rebound.finalTable.preBindSnapshotJson)
    }

    @Test fun newKeepBaseClonesEffectiveScheduleWithoutInheritingOriginalBindingSnapshot() {
        val raw = TimeTableEntity.snapshotForBind(base.copy(timeJson = "[]",
            nodesPerDay = 2, smartConfigJson = "raw-smart"), 8)
        val owner = PeriodTableEntity(id = 8, name = "Shared", timeJson = times,
            smartConfigJson = "owner-smart")
        val p = planImport(source(), snapshot(table = raw, periods = listOf(owner)),
            ImportConfiguration.forExisting(raw, owner).copy(destination = ImportDestination.New,
                content = ImportContent.ImportOnly, name = "New clone"))
        assertTrue(p.issues.toString(), p.canSubmit)
        assertEquals(TimeTableEntity.snapshotForBind(p.finalTable, 0L).preBindSnapshotJson,
            p.finalTable.preBindSnapshotJson)
        assertNotEquals(raw.preBindSnapshotJson, p.finalTable.preBindSnapshotJson)
        assertEquals(owner.timeJson, p.finalTable.timeJson)
        assertEquals(owner.nodesPerDay, p.finalTable.nodesPerDay)
        assertEquals(owner.smartConfigJson, p.finalTable.smartConfigJson)
        assertNull(p.finalTable.periodTableId)
        assertEquals(PeriodBinding.PlannedPeriod, p.periodBinding)
        assertEquals(owner.timeJson, p.finalPeriodTable?.timeJson)
        assertEquals(owner.smartConfigJson, p.finalPeriodTable?.smartConfigJson)
    }

    @Test fun finalScheduleDefinesActualClockForBothOldAndIncomingAndBoundSelection() {
        val early = """[{"node":1,"start":"08:00","end":"08:45"},{"node":2,"start":"08:55","end":"09:40"}]"""
        val late = """[{"node":1,"start":"10:00","end":"10:45"},{"node":2,"start":"10:55","end":"11:40"}]"""
        val old = row(id = 11, table = 5, step = 1)
        val incoming = row("Physics", node = 1, step = 1)
        val newSchedule = planImport(source(incoming, json = late),
            snapshot(old, table = base.copy(timeJson = early)),
            config(overlaps = OverlapPolicy.ReplaceExisting, mode = ScheduleMode.UseIncoming))
        assertEquals(1, newSchedule.conflicts.count { it.kind == ConflictKind.IncomingExisting })
        assertEquals(setOf(1), newSchedule.replacements.single().removedWeeks.take(1).toSet())
        val bound = PeriodTableEntity(id = 9, name = "Later", nodesPerDay = 2, timeJson = late)
        val selected = planImport(source(incoming, json = early),
            snapshot(old, table = base.copy(timeJson = early), periods = listOf(bound)),
            config(mode = ScheduleMode.BindExisting, overlaps = OverlapPolicy.ReplaceExisting)
                .copy(selectedPeriodTableId = 9))
        assertEquals(early, selected.finalTable.timeJson)
        assertEquals(late, selected.finalPeriodTable?.timeJson)
        assertEquals(9L, selected.finalTable.periodTableId)
        assertEquals(1, selected.conflicts.count { it.kind == ConflictKind.IncomingExisting })
    }

    @Test fun skippedDuplicateDoesNotReplaceItsRetainedOldRow() {
        val old = row(id = 11, table = 5)
        val p = planImport(source(row()), snapshot(old),
            config(overlaps = OverlapPolicy.ReplaceExisting))
        assertEquals(listOf(SkippedIncoming(0, SkipReason.Duplicate)), p.skipped)
        assertTrue(p.replacements.isEmpty())
        assertTrue(p.deleteCourses.isEmpty())
        assertEquals(listOf(old), p.retainedCourses)
    }

    @Test fun keepDuplicateRetainsExactIncomingMultiplicityAndFreshIds() {
        val same = row(id = 71, table = 999)
        val p = planImport(source(same, same), snapshot(),
            config(destination = ImportDestination.New, duplicates = DuplicatePolicy.Keep))
        assertEquals(2, p.insertCourses.size)
        assertTrue(p.insertCourses.all { it.id == 0L && it.tableId == 0L })
    }

    @Test fun skippedFirstDuplicateDoesNotSuppressNextAcceptedRepresentative() {
        val same = row()
        val p = planImport(source(same, same, same), snapshot(),
            config(overlaps = OverlapPolicy.PerItem, overrides = mapOf(0 to ItemDecision.Skip)))
        assertTrue(p.issues.toString(), p.canSubmit)
        assertEquals(listOf(SkippedIncoming(0, SkipReason.PerItem),
            SkippedIncoming(2, SkipReason.Duplicate)), p.skipped)
        assertEquals(1, p.incomingCourses.size)
        assertEquals(listOf(1), p.duplicates.filter { it.skipped }.map { it.otherIncomingIndex })
    }

    @Test fun keepingIncomingDuplicatesPreservesMultiplicityAlongsideExistingDuplicate() {
        val old = row(id = 11, table = 5)
        val same = row(id = 71, table = 999)
        val parsed = source(same, same)
        val p = planImport(parsed, snapshot(old), config(duplicates = DuplicatePolicy.Keep))
        assertTrue(p.issues.toString(), p.canSubmit)
        assertEquals(listOf(old), p.retainedCourses)
        assertEquals(2, p.insertCourses.size)
        assertEquals(3, p.finalCourses.size)
        assertTrue(p.insertCourses.all { it.id == 0L && it.tableId == 5L })
        assertTrue(p.duplicates.none { it.skipped })
        assertTrue(p.skipped.isEmpty())
        assertEquals(listOf(same, same), parsed.courses)
    }

    @Test fun perItemKeepAndReplaceDisjointWeeksOnlyContradictUnderWholeRowReplacement() {
        val old = row(id = 11, table = 5)
        val parsed = source(row("Replace", first = 2, last = 3), row("Keep", first = 7, last = 8))
        val configuration = config(overlaps = OverlapPolicy.PerItem,
            overrides = mapOf(0 to ItemDecision.ReplaceExisting, 1 to ItemDecision.KeepBoth))
        val p = planImport(parsed, snapshot(old), configuration)
        assertTrue(p.issues.toString(), p.canSubmit)
        assertEquals(setOf(2, 3), p.replacements.single().removedWeeks)
        assertTrue(p.retainedCourses.any { it.inWeek(7) && it.inWeek(8) })
        val whole = planImport(parsed, snapshot(old), configuration.copy(replacementScope = ReplacementScope.WholeRow))
        assertFalse(whole.canSubmit)
        assertTrue(whole.issues.any { it.key == ValidationKey.ContradictoryDecisions && it.courseId == 11L })
    }

    @Test fun skippedInvalidIncomingDoesNotBlockOrRequireInventedMergeSlots() {
        val invalid = row("Invalid", first = 0, last = 18, day = 9, node = -4, step = 0)
        val missingSlot = row("Missing", node = 13, step = 1)
        val valid = row("Valid")
        val parsed = source(invalid, missingSlot, valid)
        val p = planImport(parsed, snapshot(), config(mode = ScheduleMode.Merge,
            overlaps = OverlapPolicy.PerItem, overrides = mapOf(0 to ItemDecision.Skip, 1 to ItemDecision.Skip)))
        assertTrue(p.issues.toString(), p.canSubmit)
        assertEquals(listOf("Valid"), p.incomingCourses.map { it.courseName })
        assertEquals(listOf(0, 1), p.skipped.map { it.incomingIndex })
        assertEquals(listOf(invalid, missingSlot, valid), parsed.courses)
        val retained = planImport(parsed, snapshot(), config())
        assertFalse(retained.canSubmit)
        assertTrue(retained.issues.any { it.incomingIndex == 0 && it.key == ValidationKey.CourseWeekOutOfRange })
        assertTrue(retained.issues.any { it.incomingIndex == 0 && it.key == ValidationKey.InvalidCourseDay })
        assertTrue(retained.issues.any { it.incomingIndex == 0 && it.key == ValidationKey.InvalidCourseNode })
        assertTrue(retained.issues.any { it.incomingIndex == 1 && it.key == ValidationKey.InvalidCourseTime })
    }

    @Test fun overlapSkippedInvalidIncomingDoesNotBlockValidRetainedRows() {
        val old = row(id = 11, table = 5)
        val invalid = row("Invalid").copy(ownTime = true, startTime = "bad", endTime = "09:40")
        val p = planImport(source(invalid, row("Valid", day = 2)), snapshot(old),
            config(overlaps = OverlapPolicy.SkipIncoming))
        assertTrue(p.issues.toString(), p.canSubmit)
        assertEquals(listOf(SkippedIncoming(0, SkipReason.Overlap)), p.skipped)
        assertEquals(listOf(old), p.retainedCourses)
        assertEquals(listOf("Valid"), p.incomingCourses.map { it.courseName })
    }

    @Test fun retainedOldRowsStillRequireValidationButRemovedRowsDoNot() {
        val invalid = row(id = 11, table = 5, first = 0)
        val retained = planImport(source(), snapshot(invalid), config())
        assertFalse(retained.canSubmit)
        assertTrue(retained.issues.any { it.key == ValidationKey.CourseWeekOutOfRange && it.courseId == 11L })
        val removed = planImport(source(row("Replacement")), snapshot(invalid),
            config(overlaps = OverlapPolicy.ReplaceExisting))
        assertTrue(removed.issues.toString(), removed.canSubmit)
        assertEquals(listOf(invalid), removed.deleteCourses)
    }

    @Test fun firstBindToPlannedPeriodSnapshotsOriginalRawScheduleForEveryCreatingMode() {
        val rawTime = """[{"node":1,"start":"06:00","end":"06:45"}]"""
        val raw = base.copy(timeJson = rawTime, nodesPerDay = 1, smartConfigJson = "original-smart")
        for (mode in listOf(ScheduleMode.UseIncoming, ScheduleMode.CreateNew, ScheduleMode.Edit, ScheduleMode.Merge)) {
            val p = planImport(source(), snapshot(table = raw),
                config(mode = mode).copy(smartConfigJson = "incoming-smart"))
            assertTrue("$mode: ${p.issues}", p.canSubmit)
            assertEquals(PeriodBinding.PlannedPeriod, p.periodBinding)
            assertEquals(rawTime, p.finalTable.timeJson)
            assertEquals(1, p.finalTable.nodesPerDay)
            assertEquals("original-smart", p.finalTable.smartConfigJson)
            assertEquals(TimeTableEntity.snapshotForBind(raw, 0L).preBindSnapshotJson,
                p.finalTable.preBindSnapshotJson)
            assertNull(p.finalTable.periodTableId)
            val restored = TimeTableEntity.restoredForUnbind(p.finalTable.copy(periodTableId = 42))
            assertEquals(rawTime, restored.timeJson)
            assertEquals(1, restored.nodesPerDay)
            assertEquals("original-smart", restored.smartConfigJson)
            assertNotEquals(rawTime, p.finalPeriodTable?.timeJson)
        }
    }

    @Test fun switchingBoundTableToPlannedPeriodPreservesRawCompatibilityAndSnapshot() {
        val raw = base.copy(timeJson = "[]", nodesPerDay = 2, smartConfigJson = "original-smart")
        val owner = PeriodTableEntity(id = 8, name = "Owner", timeJson = times)
        for (bound in listOf(raw.copy(periodTableId = 8), TimeTableEntity.snapshotForBind(raw, 8))) {
            for (mode in listOf(ScheduleMode.UseIncoming, ScheduleMode.CreateNew)) {
                val p = planImport(source(), snapshot(table = bound, periods = listOf(owner)),
                    config(mode = mode).copy(smartConfigJson = "new-smart"))
                assertTrue(p.issues.toString(), p.canSubmit)
                assertEquals(PeriodBinding.PlannedPeriod, p.periodBinding)
                assertEquals(bound.timeJson, p.finalTable.timeJson)
                assertEquals(bound.nodesPerDay, p.finalTable.nodesPerDay)
                assertEquals(bound.smartConfigJson, p.finalTable.smartConfigJson)
                assertEquals(bound.preBindSnapshotJson, p.finalTable.preBindSnapshotJson)
            }
        }
    }

    @Test fun newIncomingAndCreatedPeriodSnapshotsUseTheirOwnSchedule() {
        val raw = TimeTableEntity.snapshotForBind(base.copy(timeJson = "[]",
            nodesPerDay = 2, smartConfigJson = "original-smart"), 8)
        val period = PeriodTableEntity(id = 8, name = "Owner", timeJson = times)
        val ownTimes = """[{"node":1,"start":"10:00","end":"10:45"}]"""
        for (mode in listOf(ScheduleMode.UseIncoming, ScheduleMode.CreateNew)) {
            val p = planImport(source(json = ownTimes), snapshot(table = raw, periods = listOf(period)),
                config(destination = ImportDestination.New, mode = mode).copy(
                    timeJson = ownTimes, nodesPerDay = 1, smartConfigJson = "new-smart"))
            assertTrue(p.issues.toString(), p.canSubmit)
            assertEquals(ownTimes, p.finalTable.timeJson)
            assertEquals(1, p.finalTable.nodesPerDay)
            assertEquals("new-smart", p.finalTable.smartConfigJson)
            assertEquals(TimeTableEntity.snapshotForBind(p.finalTable, 0L).preBindSnapshotJson,
                p.finalTable.preBindSnapshotJson)
            assertNotEquals(raw.preBindSnapshotJson, p.finalTable.preBindSnapshotJson)
            assertEquals(p.finalPeriodTable?.timeJson, p.finalTable.timeJson)
        }
    }

    @Test fun beforeSlotsAtZeroAndNegativeNodesAreValidForIncomingAndRetainedRows() {
        val json = TimeTableUtils.buildTimeJsonFromRows(listOf(
            TimeTableUtils.TimeSlotRow(-1, "06:00", "06:30", TimeTableUtils.EdgeClass.Before),
            TimeTableUtils.TimeSlotRow(0, "07:00", "07:30", TimeTableUtils.EdgeClass.Before),
            TimeTableUtils.TimeSlotRow(1, "08:00", "08:45")))
        val old = row("Old before", id = 11, table = 5, node = -1, step = 1)
        val p = planImport(source(row("Zero", node = 0, step = 1), row("Negative", node = -1, step = 1),
            json = json), snapshot(old, table = base.copy(timeJson = json, nodesPerDay = 1)), config())
        assertTrue(p.issues.toString(), p.canSubmit)
        assertEquals(3, p.finalCourses.size)
        assertEquals(listOf(old), p.retainedCourses)
        assertTrue(p.conflicts.all { it.confidence == RelationConfidence.Certain })
    }

    @Test fun missingReversedAndIncompleteCoveredSlotsRemainInvalid() {
        val schedules = listOf(
            """[{"node":1,"start":"08:00","end":"08:45"}]""" to row(node = 0, step = 1),
            """[{"node":-1,"start":"07:30","end":"07:00"}]""" to row(node = -1, step = 1),
            """[{"node":-1,"start":"06:00","end":"06:30"},{"node":1,"start":"08:00","end":"08:45"}]""" to row(node = -1, step = 3),
            """[{"node":1,"start":"08:00","end":"08:45"},{"node":2,"start":"10:00","end":"09:00"},{"node":3,"start":"10:30","end":"11:00"}]""" to row(node = 1, step = 3))
        for ((json, incoming) in schedules) {
            val p = planImport(source(incoming, json = json), snapshot(table = base.copy(timeJson = json)), config())
            assertFalse("$json: ${p.issues}", p.canSubmit)
            assertTrue(p.issues.any { it.key == ValidationKey.InvalidCourseTime && it.incomingIndex == 0 })
        }
    }

    @Test fun firstImportNewWithoutExistingBaseIsValid() {
        val p = planImport(source(row()), snapshot(tables = emptyList()),
            config(destination = ImportDestination.New, content = ImportContent.ImportOnly).copy(baseTableId = 0))
        assertTrue(p.issues.toString(), p.canSubmit)
        assertEquals(0L, p.finalTable.id)
        assertEquals(0L, p.finalTable.createdAt)
    }

    @Test fun newMergeWithoutBaseIsBlocked() {
        val p = planImport(source(row()), snapshot(tables = emptyList()),
            config(destination = ImportDestination.New, content = ImportContent.Merge).copy(baseTableId = 0))
        assertFalse(p.canSubmit)
        assertTrue(p.issues.any { it.key == ValidationKey.MissingBaseTable && it.blocking })
    }

    @Test fun newMergeReplacesOnlyOverlappingBaseRowAndCopiesOtherBaseRow() {
        val overlapping = row(id = 11, table = 5).copy(note = "keep metadata")
        val other = row("Biology", id = 12, table = 5, day = 2, group = "other")
        val incoming = row("Physics", first = 7, last = 8, node = 2)
        val p = planImport(source(incoming), snapshot(overlapping, other),
            config(destination = ImportDestination.New, content = ImportContent.Merge,
                overlaps = OverlapPolicy.ReplaceExisting))

        assertTrue(p.issues.toString(), p.canSubmit)
        assertEquals(listOf(11L), p.replacements.map { it.original.id })
        assertEquals(setOf(7, 8), p.replacements.single().removedWeeks)
        assertEquals(listOf(1 to 6, 9 to 16), p.retainedCourses.filter { it.courseName == "Math" }
            .map { it.startWeek to it.endWeek })
        assertEquals(listOf(other.courseName), p.retainedCourses.filter { it.day == 2 }.map { it.courseName })
        assertTrue(p.retainedCourses.all { it.id == 0L && it.tableId == 0L })
        assertTrue(p.retainedCourses.filter { it.courseName == "Math" }.all { it.note == "keep metadata" })
        assertEquals(4, p.insertCourses.size)
        assertTrue(p.insertCourses.all { it.id == 0L && it.tableId == 0L })
        assertTrue(p.updateCourses.isEmpty())
        assertTrue(p.deleteCourses.isEmpty())
        assertEquals(4, p.finalCourses.size)
    }

    @Test fun presetsClearStalePerItemAndOverlapDecisions() {
        val stale = config(overlaps = OverlapPolicy.PerItem,
            overrides = mapOf(4 to ItemDecision.Skip)).withPreset(ImportPreset.AddToExisting)
        assertTrue(stale.itemOverrides.isEmpty())
        assertEquals(OverlapPolicy.KeepBoth, stale.overlaps)
    }

    @Test fun repeatPlanningProducesEqualPlansIncludingInsertTimestamps() {
        val parsed = source(row())
        val snap = snapshot()
        val configuration = config(destination = ImportDestination.New, mode = ScheduleMode.CreateNew)
        assertEquals(planImport(parsed, snap, configuration), planImport(parsed, snap, configuration))
    }

    @Test fun mergeDoesNotAcceptInventedDefaultTimesForRequiredCourseNodes() {
        val baseMissing = base.copy(timeJson = "[]", nodesPerDay = 2)
        val sourceMissing = source(row("Late", node = 13, step = 1), json = "[]")
        val p = planImport(sourceMissing, snapshot(table = baseMissing),
            config(mode = ScheduleMode.Merge).copy(timeJson = "[]", nodesPerDay = 2))
        assertFalse(p.canSubmit)
        assertTrue(p.issues.any { it.key == ValidationKey.InvalidSchedule && it.blocking })
    }

    @Test fun scheduleUsesMaximumStandardNodeNotJsonRowCountForEdgeSlots() {
        val json = TimeTableUtils.buildTimeJsonFromRows(listOf(
            TimeTableUtils.TimeSlotRow(0, "07:00", "07:30", TimeTableUtils.EdgeClass.Before),
            TimeTableUtils.TimeSlotRow(1, "08:00", "08:45"),
            TimeTableUtils.TimeSlotRow(2, "08:55", "09:40"),
            TimeTableUtils.TimeSlotRow(3, "10:00", "10:45", TimeTableUtils.EdgeClass.After)))
        val p = planImport(source(row(node = 3, step = 1), json = json), snapshot(),
            config(destination = ImportDestination.New, mode = ScheduleMode.UseIncoming))
        assertEquals(2, p.finalTable.hydratedWith(p.finalPeriodTable).nodesPerDay)
        assertTrue(p.canSubmit)
    }
}
