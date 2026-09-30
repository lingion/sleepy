package com.lingion.sleepy

import com.lingion.sleepy.util.AppPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridLabPreferencesContractTest {
    @Test
    fun lab_flags_are_distinct_and_default_off() {
        assertNotEquals(AppPrefs.KEY_GRID_SHOW_SEPARATORS, AppPrefs.KEY_GRID_LONG_BREAK_SPACING)
        assertFalse(AppPrefs.DEFAULT_GRID_SHOW_SEPARATORS)
        assertFalse(AppPrefs.DEFAULT_GRID_LONG_BREAK_SPACING)
    }

    @Test
    fun lab_flags_have_stable_storage_keys() {
        assertTrue(AppPrefs.KEY_GRID_SHOW_SEPARATORS == "grid_show_separators")
        assertTrue(AppPrefs.KEY_GRID_LONG_BREAK_SPACING == "grid_long_break_spacing")
    }
}
