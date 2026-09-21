package com.lingion.sleepy.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNoticeCoreTest {
    private fun update(version: String, available: Boolean = true) =
        UpdateInfo(version, "notes", "url", available)

    @Test
    fun new_update_is_visible_without_dismissal() {
        assertTrue(UpdateNoticeCore.isVisible(update("1.2.0"), null))
    }

    @Test
    fun dismissal_hides_only_the_same_version() {
        assertFalse(UpdateNoticeCore.isVisible(update("1.2.0"), "1.2.0"))
        assertTrue(UpdateNoticeCore.isVisible(update("1.3.0"), "1.2.0"))
    }

    @Test
    fun no_update_or_blank_version_is_not_visible() {
        assertFalse(UpdateNoticeCore.isVisible(update("1.2.0", available = false), null))
        assertFalse(UpdateNoticeCore.isVisible(update("", available = true), null))
        assertFalse(UpdateNoticeCore.isVisible(null, null))
    }
}
