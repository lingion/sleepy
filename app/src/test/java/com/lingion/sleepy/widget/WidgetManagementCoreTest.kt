package com.lingion.sleepy.widget

import com.lingion.sleepy.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [WidgetManagementCore] — issue #24 Feature 1
 * (R1 list, R2 per-id binding display, R6 distinct bindings visible).
 *
 * The ViewModel keeps Android specifics (AppWidgetManager enumeration,
 * Room lookups, Dispatchers) and delegates all row-building decisions
 * to the core under test here.
 */
class WidgetManagementCoreTest {

    private fun variant(
        receiverClass: Class<out android.appwidget.AppWidgetProvider>,
        nameRes: Int = R.string.widget_today_label
    ) = WidgetVariantInfo(receiverClass, nameRes)

    private fun ids(vararg ids: Int): IntArray = ids

    private val weekGrid = variant(WeekGridWidgetProvider::class.java, R.string.widget_week_grid_label)
    private val today = variant(TodayWidgetReceiver::class.java, R.string.widget_today_label)
    private val twoday = variant(TwoDayWidgetReceiver::class.java, R.string.widget_twoday_label)

    @Test
    fun `no placed widgets yields empty list`() {
        val rows = WidgetManagementCore.buildPlacedItems(
            variants = listOf(weekGrid, today),
            idsFor = { intArrayOf() },
            bindingFor = { null },
            tableNameFor = { null }
        )
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `one variant with multiple ids produces one row per id`() {
        val rows = WidgetManagementCore.buildPlacedItems(
            variants = listOf(today),
            idsFor = { ids(11, 7) },
            bindingFor = { null },
            tableNameFor = { null }
        )
        assertEquals(listOf(11, 7), rows.map { it.widgetId })
        assertTrue(rows.all { it.variant === today })
    }

    @Test
    fun `rows follow variant metadata order`() {
        val rows = WidgetManagementCore.buildPlacedItems(
            variants = listOf(weekGrid, today, twoday),
            idsFor = { variant -> if (variant === today) ids(1) else intArrayOf() },
            bindingFor = { null },
            tableNameFor = { null }
        )
        // only today has any ids, but the row must carry today's variant
        assertEquals(1, rows.size)
        assertEquals(today, rows[0].variant)
    }

    @Test
    fun `bound table id resolves to its display name`() {
        val rows = WidgetManagementCore.buildPlacedItems(
            variants = listOf(today),
            idsFor = { ids(1) },
            bindingFor = { 5L },
            tableNameFor = { id -> if (id == 5L) "数学课表" else null }
        )
        assertEquals("数学课表", rows.single().tableName)
    }

    @Test
    fun `absent binding renders as null (follow default)`() {
        val rows = WidgetManagementCore.buildPlacedItems(
            variants = listOf(today),
            idsFor = { ids(1) },
            bindingFor = { null },
            tableNameFor = { error("must not be invoked when binding is absent") }
        )
        assertNull(rows.single().tableName)
    }

    @Test
    fun `deleted-table binding renders as null without throwing`() {
        // bound id points at a table that was deleted (lazy invalidation)
        val rows = WidgetManagementCore.buildPlacedItems(
            variants = listOf(today),
            idsFor = { ids(1) },
            bindingFor = { 42L },
            tableNameFor = { null /* repo.getTable returned null */ }
        )
        assertNull(rows.single().tableName)
    }

    @Test
    fun `two widgets bound to different tables render different names (R6)`() {
        val rows = WidgetManagementCore.buildPlacedItems(
            variants = listOf(today, twoday),
            idsFor = { variant ->
                when {
                    variant === today -> ids(1)
                    variant === twoday -> ids(2)
                    else -> intArrayOf()
                }
            },
            bindingFor = { id -> if (id == 1) 10L else 20L },
            tableNameFor = { bound -> if (bound == 10L) "本人课表" else "室友课表" }
        )
        assertEquals(2, rows.size)
        val byId = rows.associateBy { it.widgetId }
        assertEquals("本人课表", byId[1]!!.tableName)
        assertEquals("室友课表", byId[2]!!.tableName)
    }

    @Test
    fun `widget id is the row identity passed through verbatim`() {
        // Same widget id repeated across two variants must NOT collapse; the
        // row identity is widgetId, but ids come from each variant's system
        // enumeration and are independent. Pin that nothing aggregates.
        val rows = WidgetManagementCore.buildPlacedItems(
            variants = listOf(today, twoday),
            idsFor = { variant ->
                when {
                    variant === today -> ids(7)
                    variant === twoday -> ids(7)
                    else -> intArrayOf()
                }
            },
            bindingFor = { null },
            tableNameFor = { null }
        )
        assertEquals(2, rows.size)
        assertEquals(setOf(today, twoday), rows.map { it.variant }.toSet())
    }
}