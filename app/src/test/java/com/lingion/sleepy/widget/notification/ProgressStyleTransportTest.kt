package com.lingion.sleepy.widget.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressStyleTransportTest {

    @Test
    fun `progress style carries the current course progress`() {
        val style = progressStyleFor(42)

        assertEquals(42, style.progress)
        assertEquals(100, style.progressMax)
        assertTrue(style.isStyledByProgress)
    }

    @Test
    fun `progress is clamped to the public percent range`() {
        assertEquals(0, progressStyleFor(-1).progress)
        assertEquals(100, progressStyleFor(101).progress)
    }
}
