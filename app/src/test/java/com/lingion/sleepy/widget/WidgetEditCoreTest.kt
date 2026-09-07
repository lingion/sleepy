package com.lingion.sleepy.widget

import com.lingion.sleepy.data.entity.TimeTableEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [WidgetEditCore]. Mirrors the [WidgetBindingCoreTest]
 * pattern: shape and behavior are pinned without Android or Robolectric.
 * The Android [WidgetEditViewModel] facade is a thin wrapper that delegates
 * all non-trivial logic to this core.
 */
class WidgetEditCoreTest {

    private fun table(
        id: Long,
        name: String = "T$id",
        isDefault: Boolean = false
    ) = TimeTableEntity(
        id = id,
        name = name,
        startDate = "2026-09-01",
        isDefault = isDefault
    )

    // ---- filterAvailableTables ----

    @Test
    fun `filterAvailableTables returns empty when input is empty`() {
        val result = WidgetEditCore.filterAvailableTables(emptyList()) { 0 }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filterAvailableTables drops tables with zero courses`() {
        val a = table(1)
        val b = table(2)
        val c = table(3)
        val counts = mapOf(1L to 0, 2L to 5, 3L to 0)
        val result =
            WidgetEditCore.filterAvailableTables(listOf(a, b, c)) { counts[it] ?: 0 }
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `filterAvailableTables keeps all when every table has courses`() {
        val a = table(1)
        val b = table(2)
        val counts = mapOf(1L to 3, 2L to 7)
        val result =
            WidgetEditCore.filterAvailableTables(listOf(a, b)) { counts[it] ?: 0 }
        assertEquals(listOf(1L, 2L), result.map { it.id })
    }

    @Test
    fun `filterAvailableTables preserves input order`() {
        val a = table(1)
        val b = table(2)
        val c = table(3)
        // counts equal so the result must reflect input order, not count order
        val counts = mapOf(1L to 5, 2L to 5, 3L to 5)
        val result =
            WidgetEditCore.filterAvailableTables(listOf(c, a, b)) { counts[it] ?: 0 }
        assertEquals(listOf(3L, 1L, 2L), result.map { it.id })
    }

    @Test
    fun `filterAvailableTables invokes countCourses once per table`() {
        val a = table(1)
        val b = table(2)
        var calls = 0
        WidgetEditCore.filterAvailableTables(listOf(a, b)) {
            calls++
            1
        }
        assertEquals(2, calls)
    }

    // ---- initialBinding ----

    @Test
    fun `initialBinding returns null when key absent`() {
        val raw = mutableMapOf<String, Long>()
        assertNull(WidgetEditCore.initialBinding(raw, 42))
    }

    @Test
    fun `initialBinding returns value when key present`() {
        val raw = mutableMapOf<String, Long>("app_widget_42" to 7L)
        assertEquals(7L, WidgetEditCore.initialBinding(raw, 42))
    }

    @Test
    fun `initialBinding ignores foreign keys`() {
        val raw = mutableMapOf<String, Long>("other" to 1L, "app_widget_1" to 9L)
        // widget id 42 absent; must not pick up unrelated entries
        assertNull(WidgetEditCore.initialBinding(raw, 42))
    }

    // ---- applyBindingChange ----

    @Test
    fun `applyBindingChange with non-null writes key`() {
        val raw = mutableMapOf<String, Long>()
        WidgetEditCore.applyBindingChange(raw, 42, 7L)
        assertEquals(7L, raw["app_widget_42"])
    }

    @Test
    fun `applyBindingChange with non-null overwrites existing value`() {
        val raw = mutableMapOf<String, Long>("app_widget_42" to 1L)
        WidgetEditCore.applyBindingChange(raw, 42, 9L)
        assertEquals(9L, raw["app_widget_42"])
    }

    @Test
    fun `applyBindingChange with null deletes key`() {
        val raw = mutableMapOf<String, Long>("app_widget_42" to 7L)
        WidgetEditCore.applyBindingChange(raw, 42, null)
        assertNull(raw["app_widget_42"])
    }

    @Test
    fun `applyBindingChange null on empty map is a no-op`() {
        val raw = mutableMapOf<String, Long>()
        WidgetEditCore.applyBindingChange(raw, 42, null)
        assertTrue(raw.isEmpty())
    }

    @Test
    fun `applyBindingChange non-null followed by null clears the entry`() {
        val raw = mutableMapOf<String, Long>()
        WidgetEditCore.applyBindingChange(raw, 42, 7L)
        WidgetEditCore.applyBindingChange(raw, 42, null)
        assertNull(WidgetEditCore.initialBinding(raw, 42))
    }
}
