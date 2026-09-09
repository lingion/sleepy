package com.lingion.sleepy.widget

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pure-JVM tests for [WidgetRoutes] — the widget tap routing contract.
 *
 * Keeps the per-instance PendingIntent identity (requestCode = widgetId)
 * and the launch flags pinned so a future edit can't silently merge all
 * widget taps into one PendingIntent (which would make one widget's update
 * clobber another instance's tap action under FLAG_UPDATE_CURRENT).
 */
class WidgetRoutesTest {

    @Test
    fun `tap requestCode is the widget id`() {
        assertEquals(42, WidgetRoutes.tapRequestCode(42))
    }

    @Test
    fun `different widget instances get different pending intent request codes`() {
        assertNotEquals(
            WidgetRoutes.tapRequestCode(1),
            WidgetRoutes.tapRequestCode(2)
        )
    }

    @Test
    fun `tap flags launch or reuse the app task`() {
        // The flags must let a widget tap launch the app from the launcher
        // process (NEW_TASK) without stacking duplicate activities (CLEAR_TOP).
        val expected = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        assertEquals(expected, WidgetRoutes.TAP_FLAGS)
    }

    @Test
    fun `routing identity is stable across repeated lookups`() {
        // Same widget id must always map to the same requestCode — the tap
        // action must survive widget refreshes and process restarts (R2).
        assertEquals(
            WidgetRoutes.tapRequestCode(7),
            WidgetRoutes.tapRequestCode(7)
        )
    }
}