package com.lingion.sleepy.widget.notification

import org.junit.Assert.*
import org.junit.Test

class FluidNotificationSessionTest {
    private class Harness {
        var time = 0L
        val pending = linkedMapOf<Runnable, Long>()
        val posts = mutableListOf<Pair<Long, Int>>()
        var ends = 0
        val session = FluidNotificationSession({ time },
            { task, delay -> pending[task] = time + delay }, { pending.remove(it) },
            { posts.add(time to it) }, { ends++ })
        fun advance(to: Long) {
            while (true) {
                val next = pending.minByOrNull { it.value } ?: break
                if (next.value > to) break
                pending.remove(next.key)
                time = next.value
                next.key.run()
            }
            time = to
        }
    }
    @Test fun colorOsRetainsInitialReinforcementAndRegularUpdates() {
        val h = Harness()
        h.session.start(120000, true)
        h.advance(15000)
        assertEquals(listOf(0L to 1, 600L to 2, 15000L to 3), h.posts)
    }
    @Test fun vivoHasNoSubTenSecondReinforcement() {
        assertFalse(needsFluidFirstPostReinforcement(LiveCardVendor.VIVO))
        assertFalse(needsFluidFirstPostReinforcement(LiveCardVendor.IQOO))
        val h = Harness()
        h.session.start(120000, needsFluidFirstPostReinforcement(LiveCardVendor.VIVO))
        h.advance(14999)
        assertEquals(listOf(0L to 1), h.posts)
    }
    @Test fun reinforcementIsLimitedToColorOsFamily() {
        for (vendor in LiveCardVendor.entries) {
            assertEquals(vendor in listOf(LiveCardVendor.OPPO, LiveCardVendor.ONEPLUS,
                LiveCardVendor.REALME), needsFluidFirstPostReinforcement(vendor))
        }
    }
    @Test fun stopCancelsEveryCallbackAndRejectsAlreadyDequeuedCallback() {
        val h = Harness()
        h.session.start(120000, true)
        val stale = h.pending.keys.toList()
        h.session.stop()
        assertTrue(h.pending.isEmpty())
        stale.forEach { it.run() }
        h.advance(120000)
        assertEquals(listOf(0L to 1), h.posts)
        assertEquals(0, h.ends)
    }
    @Test fun replacingSessionResetsCreateAndRejectsOldWork() {
        val h = Harness()
        h.session.start(120000, true)
        val stale = h.pending.keys.toList()
        h.advance(100)
        h.session.start(60000, false)
        stale.forEach { it.run() }
        h.advance(15100)
        assertEquals(listOf(0L to 1, 100L to 1, 15100L to 2), h.posts)
        assertEquals(1, h.pending.size)
    }
    @Test fun naturalEndCancelsWithoutSubTenSecondTerminalUpdate() {
        val h = Harness()
        h.session.start(20000, false)
        h.advance(120000)
        assertEquals(listOf(0L to 1, 15000L to 2), h.posts)
        assertEquals(1, h.ends)
        assertTrue(h.pending.isEmpty())
    }
    @Test fun expiredStartFinishesWithoutCreatingNotificationSession() {
        val h = Harness()
        h.session.start(0, true)
        assertTrue(h.posts.isEmpty())
        assertTrue(h.pending.isEmpty())
        assertEquals(1, h.ends)
    }
    @Test fun repeatedActiveCourseReusesSessionButChangedOrExpiredCourseDoesNot() {
        val current = FluidNotificationIdentity(1000, 61000, 1, "Math")
        assertTrue(canReuseFluidSession(current, current.copy(), true, false, 2000))
        assertFalse(canReuseFluidSession(current, current.copy(courseName = "Physics"), true, false, 2000))
        assertFalse(canReuseFluidSession(current, current.copy(startNode = 2), true, false, 2000))
        assertFalse(canReuseFluidSession(current, current.copy(classEpoch = 62000), true, false, 2000))
        assertFalse(canReuseFluidSession(current, current, true, false, 61000))
        assertFalse(canReuseFluidSession(current, current, true, true, 2000))
        assertFalse(canReuseFluidSession(current, current, false, false, 2000))
    }
}

