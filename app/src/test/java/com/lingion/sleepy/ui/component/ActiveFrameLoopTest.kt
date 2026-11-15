package com.lingion.sleepy.ui.component

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.lingion.sleepy.ui.component.liquidglass.runActiveFrameLoop
import com.lingion.sleepy.ui.component.liquidglass.runTimestampedActiveFrameLoop
import com.lingion.sleepy.ui.component.liquidglass.ActiveFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

class ActiveFrameLoopTest {
    @Test fun timestampedFramesKeepAbsoluteTimeAndResetThePreviousFrameOnWake() {
        val clock = BroadcastFrameClock()
        val active = mutableStateOf(true)
        val frames = mutableListOf<ActiveFrame>()
        val job = CoroutineScope(Dispatchers.Unconfined + clock).launch {
            runTimestampedActiveFrameLoop({ active.value }) { frame ->
                frames += frame
                if (frames.size == 2) active.value = false
            }
        }
        try {
            clock.sendFrame(1_000_000_000L)
            clock.sendFrame(1_500_000_000L)
            assertFalse(clock.hasAwaiters)
            active.value = true
            Snapshot.sendApplyNotifications()
            clock.sendFrame(90_000_000_000L)
            assertEquals(listOf(
                ActiveFrame(1_000_000_000L, null),
                ActiveFrame(1_500_000_000L, 1_000_000_000L),
                ActiveFrame(90_000_000_000L, null),
            ), frames)
        } finally {
            job.cancel()
        }
    }

    @Test fun idleStopsFrameRequestsAndInteractionRestartsWithFreshDelta() {
        val clock = BroadcastFrameClock()
        val active = mutableStateOf(false)
        val deltas = mutableListOf<Double>()
        val job = CoroutineScope(Dispatchers.Unconfined + clock).launch {
            runActiveFrameLoop({ active.value }) { delta ->
                deltas += delta
                active.value = false
            }
        }
        try {
            assertFalse(clock.hasAwaiters)
            active.value = true
            Snapshot.sendApplyNotifications()
            assertTrue(clock.hasAwaiters)
            clock.sendFrame(1_000_000_000L)
            assertFalse(clock.hasAwaiters)
            active.value = true
            Snapshot.sendApplyNotifications()
            assertTrue(clock.hasAwaiters)
            clock.sendFrame(90_000_000_000L)
            assertEquals(listOf(0.0, 0.0), deltas)
            assertFalse(clock.hasAwaiters)
        } finally {
            job.cancel()
        }
    }

    @Test fun activeAnimationReceivesClampedDeltaAndCancellationStopsIt() {
        val clock = BroadcastFrameClock()
        val deltas = mutableListOf<Double>()
        val job = CoroutineScope(Dispatchers.Unconfined + clock).launch {
            runActiveFrameLoop({ true }) { deltas += it }
        }
        try {
            clock.sendFrame(1_000_000_000L)
            clock.sendFrame(1_020_000_000L)
            clock.sendFrame(3_000_000_000L)
            assertEquals(listOf(0.0, 0.02, 0.05), deltas)
            assertTrue(clock.hasAwaiters)
        } finally {
            job.cancel()
        }
        assertFalse(clock.hasAwaiters)
    }
}
