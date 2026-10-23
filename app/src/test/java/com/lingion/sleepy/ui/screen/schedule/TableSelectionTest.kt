package com.lingion.sleepy.ui.screen.schedule

import com.lingion.sleepy.data.entity.TimeTableEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TableSelectionTest {
    private fun table(id: Long, default: Boolean = false) = TimeTableEntity(
        id = id, name = "Table $id", startDate = "2026-09-07", isDefault = default,
    )

    @Test fun `new imported default overrides a previously selected backup`() {
        assertEquals(3L, resolveSelectedTableId(
            listOf(table(3, true), table(2), table(1)), 2, 2, true,
        ))
    }

    @Test fun `course import selects its result whether a backup was created or not`() {
        for (backup in listOf(false, true)) {
            val tables = if (backup) listOf(table(3), table(2), table(1, true))
                else listOf(table(2), table(1, true))
            assertEquals("backup=$backup", 1L, resolveSelectedTableId(tables, 2, 2, true))
        }
    }

    @Test fun `a newer backup never takes selection from the import target`() {
        assertEquals(1L, resolveSelectedTableId(listOf(table(2), table(1, true)), 1, 1, true))
        assertEquals(1L, resolveSelectedTableId(listOf(table(2), table(1, true)), 1, 1, false))
    }

    @Test fun `manual selection survives unrelated emissions until its default write finishes`() {
        assertEquals(2L, resolveSelectedTableId(listOf(table(3), table(2), table(1, true)), 2, 1, true))
        assertEquals(2L, resolveSelectedTableId(listOf(table(3), table(2, true), table(1)), 2, 1, true))
    }

    @Test fun `deleted selection falls back to default and empty tables clear it`() {
        assertEquals(1L, resolveSelectedTableId(listOf(table(3), table(1, true)), 2, 1, true))
        assertNull(resolveSelectedTableId(emptyList(), 2, 1, true))
    }

    @Test fun `restarting uses persisted import target even when backup is newest`() {
        assertEquals(1L, resolveSelectedTableId(listOf(table(2), table(1, true)), null, null, false))
    }
}
