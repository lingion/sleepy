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

    // ---- resolveBoundTableId (lazy invalidation helper) ----

    @Test
    fun `resolveBoundTableId returns null when binding is null`() {
        val result = WidgetBindingCore.resolveBoundTableId<Long>(null) { it }
        assertNull(result)
    }

    @Test
    fun `resolveBoundTableId returns null when loader returns null (deleted table)`() {
        // Simulating a table that was deleted after the binding was created.
        // The lookup closure returning null is the signal.
        val result = WidgetBindingCore.resolveBoundTableId(42L) { _ -> null }
        assertNull(result)
    }

    @Test
    fun `resolveBoundTableId returns loaded entity when table exists`() {
        val sentinel = "table-42"
        val result = WidgetBindingCore.resolveBoundTableId(42L) { _ -> sentinel }
        assertEquals(sentinel, result)
    }

    @Test
    fun `resolveBoundTableId does not invoke loader when binding is null`() {
        var loaderCalled = false
        WidgetBindingCore.resolveBoundTableId<Long>(null) {
            loaderCalled = true
            it
        }
        assertEquals(false, loaderCalled)
    }

    @Test
    fun `WidgetTableResolver exposes resolveBoundTable`() {
        // The body of resolveBoundTable is a thin wrapper around
        // WidgetBindingCore.resolveBoundTableId which is covered by the unit
        // tests above. This pin just guards against accidental removal of the
        // Android facade method that loadDataSync will call from each receiver.
        val method = com.lingion.sleepy.widget.WidgetTableResolver::class.java
            .declaredMethods.firstOrNull { it.name == "resolveBoundTable" }
        assertNotNull("resolveBoundTable must exist on WidgetTableResolver", method)
    }

    // ---- sentinel 0L round-trip (issue #24 F1 R2: binding persists by widget id) ----

    @Test
    fun `first-add sentinel 0L round-trips as 0L by widget id`() {
        val data = mutableMapOf<String, Long>()
        WidgetBindingCore.write(data, 42, 0L)
        assertEquals(0L, WidgetBindingCore.read(data, 42))
    }

    @Test
    fun `sentinel survives the load-parse cycle (restart persistence)`() {
        // Simulates an app restart: the written map is persisted to prefs,
        // reloaded raw, and re-parsed. The sentinel must survive as 0L so the
        // configure activity counts the widget as already-added (R5) and the
        // edit screen shows follow-default instead of table id 0.
        val persisted = mutableMapOf<String, Long>()
        WidgetBindingCore.write(persisted, 42, 0L)
        // SharedPreferences returns the raw string-keyed map verbatim; parseAll
        // is the only transformation between prefs and the by-id view.
        val parsedById = WidgetBindingCore.parseAll(persisted)
        assertEquals(0L, parsedById[42])
    }

    @Test
    fun `explicit binding and sentinel coexist keyed by different ids`() {
        val data = mutableMapOf<String, Long>()
        WidgetBindingCore.write(data, 42, 0L) // follow default
        WidgetBindingCore.write(data, 43, 7L) // explicit table 7
        assertEquals(0L, WidgetBindingCore.read(data, 42))
        assertEquals(7L, WidgetBindingCore.read(data, 43))
    }

    @Test
    fun `rebind overwrites sentinel by widget id`() {
        val data = mutableMapOf<String, Long>()
        WidgetBindingCore.write(data, 42, 0L)
        WidgetBindingCore.write(data, 42, 9L)
        assertEquals(9L, WidgetBindingCore.read(data, 42))
    }

    @Test
    fun `deleting one id leaves the other id's binding intact`() {
        val data = mutableMapOf<String, Long>()
        WidgetBindingCore.write(data, 42, 7L)
        WidgetBindingCore.write(data, 43, 8L)
        WidgetBindingCore.delete(data, 42)
        assertNull(WidgetBindingCore.read(data, 42))
        assertEquals(8L, WidgetBindingCore.read(data, 43))
    }
}
