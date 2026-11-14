package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockInputClockTest {
    private data class Input(val name: String, val press: Boolean = false)
    private fun frame(ms: Long, previousMs: Long?) = ActiveFrame(ms * 1_000_000L, previousMs?.times(1_000_000L))

    @Test fun callbackWithAFrameTimestampBeforeReceiptOnlyAdvancesTheOldTrajectory() {
        val clock = DockInputClock<Input> { it.press }
        val applied = mutableListOf<String>()
        var elapsed = 0.0
        clock.enqueue(Input("new-press", true), 110_000_000L, sampleUptimeMillis = 80)

        clock.advance(frame(108, 100), { elapsed += it }, { applied += it.name })
        assertEquals(.008, elapsed, 1e-12)
        assertTrue(applied.isEmpty())
        assertTrue(clock.hasPending)
        clock.advance(frame(116, 108), { elapsed += it }, {
            assertEquals(.016, elapsed, 1e-12)
            applied += it.name
        })
        assertEquals(listOf("new-press"), applied)
        assertFalse(clock.hasPending)
    }

    @Test fun eventsWithinAFrameSplitElapsedTimeButAPressEstablishesOneOrigin() {
        val clock = DockInputClock<Input> { it.press }
        val applied = mutableListOf<Pair<String, Double>>()
        var elapsed = 0.0
        clock.enqueue(Input("move"), 103_000_000L)
        clock.enqueue(Input("press", true), 104_000_000L)
        clock.enqueue(Input("up"), 105_000_000L)
        clock.enqueue(Input("next-press", true), 106_000_000L)
        clock.advance(frame(108, 100), { elapsed += it }, { applied += it.name to elapsed })
        assertEquals(listOf("move", "press", "up", "next-press"), applied.map { it.first })
        listOf(.003, .008, .008, .008).zip(applied).forEach { (expected, actual) ->
            assertEquals(expected, actual.second, 1e-12)
        }
        assertEquals(.008, elapsed, 1e-12)
    }

    @Test fun equalReceiptTimesRetainInputOrder() {
        val clock = DockInputClock<Input> { it.press }
        val applied = mutableListOf<String>()
        listOf(Input("down", true), Input("up"), Input("next-down", true)).forEach {
            clock.enqueue(it, 100_000_000L)
        }
        clock.advance(frame(108, 100), {}, { applied += it.name })
        assertEquals(listOf("down", "up", "next-down"), applied)
    }

    @Test fun wakingDoesNotIntegrateTimeSpentWithoutAnActiveFrameLoop() {
        val clock = DockInputClock<Input> { it.press }
        var elapsed = 0.0
        clock.enqueue(Input("new-press", true), 90_000_000_000L)
        clock.advance(ActiveFrame(90_010_000_000L, null), { elapsed += it }, {})
        assertEquals(0.0, elapsed, 0.0)
    }
}
