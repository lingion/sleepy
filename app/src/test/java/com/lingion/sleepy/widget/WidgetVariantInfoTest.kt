package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [WidgetVariantInfo] — shared metadata describing the
 * 10 widget variants. The list is the single source of truth for both the
 * refresh broadcast in [WidgetUpdater] and the management screen UI; this
 * test pins that contract.
 */
class WidgetVariantInfoTest {

    @Test
    fun `ALL_WIDGET_VARIANTS has exactly 10 entries`() {
        assertEquals(13, ALL_WIDGET_VARIANTS.size)
    }

    @Test
    fun `every variant has a unique receiver class`() {
        val classes = ALL_WIDGET_VARIANTS.map { it.receiverClass }
        assertEquals("duplicate receiver class in metadata", classes.size, classes.toSet().size)
    }

    @Test
    fun `all entries carry a positive name resource id`() {
        ALL_WIDGET_VARIANTS.forEach { v ->
            assertTrue("displayNameRes must be non-zero for ${v.receiverClass.simpleName}", v.displayNameRes != 0)
        }
    }

    @Test
    fun `all entries carry a preview drawable resource`() {
        ALL_WIDGET_VARIANTS.forEach { v ->
            assertTrue("previewImageRes must be non-zero for ${v.receiverClass.simpleName}", v.previewImageRes != 0)
        }
        assertEquals(
            "preview resources must be unique",
            ALL_WIDGET_VARIANTS.size,
            ALL_WIDGET_VARIANTS.map { it.previewImageRes }.toSet().size
        )
    }

    @Test
    fun `WidgetUpdater receiver list matches ALL_WIDGET_VARIANTS`() {
        // The refresh broadcast must not silently drop or duplicate a variant;
        // it must mirror the metadata list 1:1.
        val infoClasses = ALL_WIDGET_VARIANTS.map { it.receiverClass }.toSet()
        val updaterClasses = WidgetUpdater.remoteViewsReceiverClasses.toSet()
        assertEquals(infoClasses, updaterClasses)
    }

    @Test
    fun `metadata includes both base and small variant for each kind`() {
        // Five widget "kinds": Today / WeekList / WeekView / TwoDay / WeekGrid.
        // Each must have a base + a small variant. WeekGrid uses its own
        // provider class hierarchy (open class + subclass) so we accept either
        // WeekGridWidgetProvider or WeekGridSmallWidgetProvider as the "small"
        // form — both must be present.
        val byKind = ALL_WIDGET_VARIANTS.map { v ->
            v.receiverClass.simpleName.removeSuffix("SmallWidgetProvider")
                .removeSuffix("SmallWidgetReceiver")
                .removeSuffix("WideWidgetReceiver")
                .removeSuffix("WidgetProvider")
                .removeSuffix("WidgetReceiver")
        }
        val counts = byKind.groupingBy { it }.eachCount()
        // 设计 §7: Today/TwoDay/WeekList 三族有 M 变体 (base+small+wide=3),
        // WeekView/WeekGrid 不设 M (2)。
        val expected = mapOf(
            "Today" to 3, "TwoDay" to 3, "WeekList" to 3,
            "WeekView" to 2, "WeekGrid" to 2
        )
        assertEquals(expected.keys, counts.keys)
        counts.forEach { (kind, count) ->
            assertEquals("kind=$kind count", expected.getValue(kind), count)
        }
        // And specifically: the 5 base + 5 small must be present.
        assertNotEquals(0, byKind.count { it == "Today" })
        assertNotEquals(0, byKind.count { it == "WeekList" })
        assertNotEquals(0, byKind.count { it == "WeekView" })
        assertNotEquals(0, byKind.count { it == "TwoDay" })
        assertNotEquals(0, byKind.count { it == "WeekGrid" })
    }
}
