package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [WidgetBindingCore] — the Android-free binding logic.
 * Behavioral coverage of the [WidgetBindingStore] Android facade (load/save
 * to SharedPreferences) is implicitly covered by Android SDK contract;
 * the Android wrapper is a one-liner per core operation.
 */
class WidgetBindingCoreTest {

    @Test
    fun `prefs file name is stable contract widget_bindings`() {
        assertEquals("widget_bindings", WidgetBindingCore.PREFS_NAME)
    }

    @Test
    fun `key prefix and key shape are stable contract`() {
        assertEquals("app_widget_", WidgetBindingCore.KEY_PREFIX)
        assertEquals("app_widget_42", WidgetBindingCore.key(42))
    }

    @Test
    fun `read returns null when binding absent`() {
        assertNull(WidgetBindingCore.read(emptyMap(), 42))
    }

    @Test
    fun `write then read returns same tableId`() {
        val data = mutableMapOf<String, Long>()
        WidgetBindingCore.write(data, 42, 7L)
        assertEquals(7L, WidgetBindingCore.read(data, 42))
    }

    @Test
    fun `delete makes read return null`() {
        val data = mutableMapOf<String, Long>()
        WidgetBindingCore.write(data, 42, 7L)
        WidgetBindingCore.delete(data, 42)
        assertNull(WidgetBindingCore.read(data, 42))
    }

    @Test
    fun `different widget ids do not collide`() {
        val data = mutableMapOf<String, Long>()
        WidgetBindingCore.write(data, 42, 7L)
        WidgetBindingCore.write(data, 43, 8L)
        assertEquals(7L, WidgetBindingCore.read(data, 42))
        assertEquals(8L, WidgetBindingCore.read(data, 43))
    }

    @Test
    fun `parseAll maps widget ids to tableIds ignoring foreign keys`() {
        val parsed = WidgetBindingCore.parseAll(
            mapOf(
                "app_widget_1" to 100L,
                "app_widget_2" to 200L,
                "other_key" to 999L,
                "app_widget_abc" to 1L // non-int id — ignored
            )
        )
        assertEquals(setOf(1, 2), parsed.keys)
        assertEquals(100L, parsed[1])
        assertEquals(200L, parsed[2])
    }

    @Test
    fun `parseAll on empty map returns empty map`() {
        assertTrue(WidgetBindingCore.parseAll(emptyMap()).isEmpty())
    }

    @Test
    fun `key with negative id still round-trips`() {
        // AppWidget ids are non-negative in practice, but the store must not
        // crash on a malformed input; ensure round-trip stability.
        val data = mutableMapOf<String, Long>()
        WidgetBindingCore.write(data, -1, 5L)
        assertEquals("app_widget_-1", WidgetBindingCore.key(-1))
        assertEquals(5L, WidgetBindingCore.read(data, -1))
    }

    @Test
    fun `WidgetBindingStore exposes the expected public API`() {
        // Compile-time API contract; reflection just gives us a sanity check.
        val methods = WidgetBindingStore::class.java.declaredMethods.map { it.name }.toSet()
        assertNotNull(methods)
        assertTrue("get missing", "get" in methods)
        assertTrue("put missing", "put" in methods)
        assertTrue("remove missing", "remove" in methods)
        assertTrue("getAll missing", "getAll" in methods)
    }
}
