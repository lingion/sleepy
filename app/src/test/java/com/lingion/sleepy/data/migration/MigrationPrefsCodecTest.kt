package com.lingion.sleepy.data.migration

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯 JVM fake: SharedPreferences 是接口 + unitTests.isReturnDefaultValues=true, 可直接实现。 */
private class FakePrefs : SharedPreferences {
    val map = LinkedHashMap<String, Any?>()

    override fun getAll(): Map<String, Any?> = map.toMap()
    override fun getString(key: String, defValues: String?): String? = map[key] as? String ?: defValues
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        val v = map[key] as? Set<String> ?: return defValues
        return v.toMutableSet()
    }
    override fun getInt(key: String, defValue: Int): Int = (map[key] as? Int) ?: defValue
    override fun getLong(key: String, defValue: Long): Long = (map[key] as? Long) ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = (map[key] as? Float) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
    override fun contains(key: String): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class FakeEditor : SharedPreferences.Editor {
        val staged = LinkedHashMap<String, Any?>()
        var doClear = false
        override fun putString(key: String, value: String?) = apply { staged[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { staged[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { staged[key] = value }
        override fun putLong(key: String, value: Long) = apply { staged[key] = value }
        override fun putFloat(key: String, value: Float) = apply { staged[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { staged[key] = value }
        override fun remove(key: String) = apply { staged[key] = null }
        override fun clear() = apply { doClear = true }
        override fun apply() = commitForReal()
        override fun commit(): Boolean { commitForReal(); return true }
        private fun commitForReal() {
            if (doClear) map.clear()
            for ((k, v) in staged) if (v == null) map.remove(k) else map[k] = v
        }
    }
}

class MigrationPrefsCodecTest {

    @Test
    fun `roundtrip preserves all six value types`() {
        val src = FakePrefs()
        src.edit()
            .putBoolean("dark", true)
            .putInt("weekCount", 20)
            .putLong("installedAt", 1727000000000L)
            .putFloat("fontScale", 1.25f)
            .putString("tableId", "42")
            .putStringSet("quickKeys", setOf("a", "b"))
            .commit()

        val snapshot = MigrationPrefsCodec.PrefsSnapshot(files = mapOf("sleepy_prefs" to MigrationPrefsCodec.collect(src)))
        val text = MigrationPrefsCodec.encode(snapshot)
        val decoded = MigrationPrefsCodec.decode(text)

        val dst = FakePrefs()
        MigrationPrefsCodec.apply(dst, decoded.files.getValue("sleepy_prefs"), merge = false)

        assertEquals(true, dst.getBoolean("dark", false))
        assertEquals(20, dst.getInt("weekCount", 0))
        assertEquals(1727000000000L, dst.getLong("installedAt", 0L))
        assertEquals(1.25f, dst.getFloat("fontScale", 0f))
        assertEquals("42", dst.getString("tableId", ""))
        assertEquals(setOf("a", "b"), dst.getStringSet("quickKeys", null))
    }

    @Test
    fun `overwrite mode clears keys absent from package`() {
        val src = FakePrefs()
        src.edit().putString("keep", "v").commit()
        val snapshot = MigrationPrefsCodec.PrefsSnapshot(files = mapOf("f" to MigrationPrefsCodec.collect(src)))

        val local = FakePrefs()
        local.edit().putString("stale", "old").commit()
        MigrationPrefsCodec.apply(local, snapshot.files.getValue("f"), merge = false)

        assertEquals("v", local.getString("keep", ""))
        assertEquals(null, local.getString("stale", null))
        assertTrue(!local.contains("stale"))
    }

    @Test
    fun `merge mode keeps local-only keys and overrides same-name keys`() {
        val incoming = MigrationPrefsCodec.PrefsFileSnapshot(
            entries = mapOf("shared" to MigrationPrefsCodec.PrefValue(MigrationPrefsCodec.PrefType.STRING, string = "new")),
        )
        val local = FakePrefs()
        local.edit().putString("shared", "old").putString("localOnly", "mine").commit()

        MigrationPrefsCodec.apply(local, incoming, merge = true)

        assertEquals("new", local.getString("shared", ""))
        assertEquals("mine", local.getString("localOnly", ""))
    }

    @Test
    fun `prefs file map matches design module boundaries`() {
        assertEquals(listOf("sleepy_prefs", "custom_themes"), MigrationPrefsFileMap.PREFERENCES)
        assertEquals(
            listOf("widget_bindings", "widget_scroll_prefs", "widget_compact_window_prefs", "widget_today_nav"),
            MigrationPrefsFileMap.WIDGETS,
        )
        assertTrue(MigrationPrefsFileMap.PERSISTED_STATE.isEmpty())
        // debug_import 调试文件不出现在任何模块
        val all = MigrationPrefsFileMap.PREFERENCES + MigrationPrefsFileMap.WIDGETS
        assertTrue(all.none { it == "debug_import" })
    }
}
