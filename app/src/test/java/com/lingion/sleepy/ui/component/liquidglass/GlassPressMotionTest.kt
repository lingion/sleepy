package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.*
import org.junit.Test

class GlassPressMotionTest {
    @Test fun shortNativeTapTurnsAroundBeforeTheHeldSizeWithoutLosingVelocity() {
        val motion = GlassPressMotion(prominent = true)
        motion.press()
        repeat(8) { motion.tick(.008) }
        val velocity = motion.lift.velocity
        val releasedAt = motion.lift.value
        motion.release()
        assertEquals(velocity, motion.lift.velocity, 0f)
        assertEquals(releasedAt, motion.lift.value, 0f)
        var peak = releasedAt
        repeat(200) { motion.tick(.008); peak = maxOf(peak, motion.lift.value) }
        assertTrue("short taps retain a partial rise: $peak", peak in .3f.. .95f)
        assertEquals(0f, motion.lift.value, 0f)
        assertFalse(motion.isRunning)
    }

    @Test fun regularAndProminentButtonsHaveDifferentMeasuredRiseCurves() {
        val regular = GlassPressMotion()
        val prominent = GlassPressMotion(prominent = true)
        regular.press(); prominent.press()
        repeat(10) { regular.tick(.008); prominent.tick(.008) }
        assertTrue(regular.lift.value > prominent.lift.value)
        var peak = regular.lift.value
        repeat(150) { regular.tick(.008); prominent.tick(.008); peak = maxOf(peak, regular.lift.value) }
        assertTrue("held native buttons have a small overshoot", peak in 1.05f..1.12f)
        assertEquals(1f, regular.lift.value, 0f)
        assertEquals(1f, prominent.lift.value, 0f)
        regular.release(); regular.finish()
        assertEquals(0f, regular.lift.value, 0f)
        assertFalse(regular.isRunning)
    }

    @Test fun buttonForegroundFadesContinuouslyAndRestoresAfterAnInterruptedTap() {
        val motion = GlassPressMotion()
        motion.press()
        repeat(8) { motion.tick(.008) }
        val alphaAtRelease = glassPressedContentAlpha(.5f, motion.lift.value)
        assertTrue("a short press must already lower foreground contrast", alphaAtRelease in .5f.. .95f)
        motion.release()
        assertEquals("release must preserve the visible alpha", alphaAtRelease,
            glassPressedContentAlpha(.5f, motion.lift.value), 0f)
        repeat(200) {
            motion.tick(.008)
            assertTrue(glassPressedContentAlpha(.5f, motion.lift.value) in .5f..1f)
            assertEquals("ordinary surfaces must keep opaque content", 1f,
                glassPressedContentAlpha(1f, motion.lift.value), 0f)
        }
        assertEquals(1f, glassPressedContentAlpha(.5f, motion.lift.value), 0f)
        assertEquals(.5f, glassPressedContentAlpha(.5f, 1.12f), 0f)
        assertEquals(.8f, glassPressedContentAlpha(.8f, 1.12f), 0f)
        assertEquals(1f, glassPressedContentAlpha(.5f, -.1f), 0f)
    }
}
