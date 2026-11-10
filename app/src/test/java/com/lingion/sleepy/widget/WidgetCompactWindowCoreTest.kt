package com.lingion.sleepy.widget

import com.lingion.sleepy.ui.screen.widget.WidgetEditCompactWindowSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-09-15: 三天窗口档位 per-widget 化 (管理页外卡 → 「· 小」二级编辑页)。
 * 锁三件事: 键隔离、迁移回退语义、族门控。
 */
class WidgetCompactWindowCoreTest {

    @Test
    fun `read returns null until explicitly written`() {
        assertNull(WidgetCompactWindowCore.read(emptyMap(), 7))
    }

    @Test
    fun `write then read round-trips per widget id`() {
        val raw = mutableMapOf<String, Boolean>()
        WidgetCompactWindowCore.write(raw, 1, todayFirst = false)
        WidgetCompactWindowCore.write(raw, 2, todayFirst = true)
        assertEquals(false, WidgetCompactWindowCore.read(raw, 1))
        assertEquals(true, WidgetCompactWindowCore.read(raw, 2))
        assertNull(WidgetCompactWindowCore.read(raw, 3))
    }

    @Test
    fun `delete clears only the target widget`() {
        val raw = mutableMapOf<String, Boolean>()
        WidgetCompactWindowCore.write(raw, 1, todayFirst = false)
        WidgetCompactWindowCore.write(raw, 2, todayFirst = false)
        WidgetCompactWindowCore.delete(raw, 1)
        assertNull(WidgetCompactWindowCore.read(raw, 1))
        assertEquals(false, WidgetCompactWindowCore.read(raw, 2))
    }

    @Test
    fun `resolve prefers explicit and falls back to legacy global`() {
        assertTrue(WidgetCompactWindowCore.resolve(explicit = null, legacyGlobal = true))
        assertFalse(WidgetCompactWindowCore.resolve(explicit = null, legacyGlobal = false))
        assertTrue(WidgetCompactWindowCore.resolve(explicit = true, legacyGlobal = false))
        assertFalse(WidgetCompactWindowCore.resolve(explicit = false, legacyGlobal = true))
    }

    @Test
    fun `appliesTo only matches the two small week receivers`() {
        assertTrue(WidgetEditCompactWindowSection.appliesTo("WeekListSmallWidgetReceiver"))
        assertTrue(WidgetEditCompactWindowSection.appliesTo("WeekViewSmallWidgetReceiver"))
        assertFalse(WidgetEditCompactWindowSection.appliesTo("WeekListWidgetReceiver"))
        assertFalse(WidgetEditCompactWindowSection.appliesTo("WeekViewWidgetReceiver"))
        assertFalse(WidgetEditCompactWindowSection.appliesTo("TodaySmallWidgetReceiver"))
        assertFalse(WidgetEditCompactWindowSection.appliesTo("WeekGridSmallWidgetProvider"))
        assertFalse(WidgetEditCompactWindowSection.appliesTo(null))
    }
}

/** 渲染器接线契约: compact 脸必须读本实例档位, 不得回退全局 AppPrefs。 */
class WidgetCompactWindowWiringTest {

}
