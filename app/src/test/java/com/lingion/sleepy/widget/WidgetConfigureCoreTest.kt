package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [WidgetConfigureCore] — issue #24 Feature 1 R4/R5.
 *
 * Pins the two-path decision the configure activity makes:
 * - first add (no prefs entry) -> auto-finish with the sentinel binding (R5);
 * - any later entry (sentinel or real binding) -> editor (R4).
 *
 * The auto-finish mechanics (decorView.post + setResult) are Android-side;
 * the decision that gates them is what this suite locks down.
 */
class WidgetConfigureCoreTest {

    @Test
    fun `first add has no entry so decision is first-add`() {
        assertTrue(WidgetConfigureCore.isFirstAdd(existingBinding = null))
    }

    @Test
    fun `sentinel entry means the widget exists so decision is editor`() {
        assertFalse(
            "sentinel must count as already added - otherwise the long-press " +
                "edit route would re-auto-finish instead of opening the editor",
            WidgetConfigureCore.isFirstAdd(WidgetConfigureCore.FIRST_ADD_BINDING)
        )
    }

    @Test
    fun `real binding means the widget exists so decision is editor`() {
        assertFalse(WidgetConfigureCore.isFirstAdd(7L))
    }

    @Test
    fun `sentinel value is the stable 0L contract`() {
        assertEquals(0L, WidgetConfigureCore.FIRST_ADD_BINDING)
    }

    @Test
    fun `sentinel never leaks into the editor as an explicit binding`() {
        // The value written on first add must be translated to Default
        // by the edit screen, never shown as an explicit binding of id 0.
        assertNull(WidgetEditCore.displayBinding(WidgetConfigureCore.FIRST_ADD_BINDING))
    }
}
