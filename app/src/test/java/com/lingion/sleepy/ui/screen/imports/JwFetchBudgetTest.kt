package com.lingion.sleepy.ui.screen.imports
import org.junit.Assert.*
import org.junit.Test

class JwFetchBudgetTest {
    @Test fun `asynchronous execution stays active until result or timeout`() {
        var time = 0L
        val budget = JwFetchBudget { time }
        budget.begin("first", 20_000)
        time = 19_999
        assertEquals(1L, budget.remainingMs("first"))
        time = 20_000
        assertEquals(0L, budget.remainingMs("first"))
        assertTrue(budget.complete("first"))
        assertFalse(budget.complete("first"))
        assertNull(budget.remainingMs("first"))
    }

    @Test fun `user interaction pauses only the remaining execution budget`() {
        var time = 0L
        val budget = JwFetchBudget { time }
        budget.begin("first", 120_000)
        time = 30_000
        budget.setWaiting("first", true)
        time = 500_000
        budget.setWaiting("first", true)
        assertNull(budget.remainingMs("first"))
        budget.setWaiting("first", false)
        assertEquals(90_000L, budget.remainingMs("first"))
        time += 89_999
        assertEquals(1L, budget.remainingMs("first"))
        budget.setWaiting("first", false)
        time++
        assertEquals(0L, budget.remainingMs("first"))
    }

    @Test fun `replaced cancelled and duplicate responses cannot complete the new task`() {
        var time = 0L
        val budget = JwFetchBudget { time }
        budget.begin("old", 20_000)
        budget.begin("new", 120_000)
        assertFalse(budget.complete("old"))
        budget.setWaiting("old", true)
        time = 20_000
        assertEquals(100_000L, budget.remainingMs("new"))
        budget.cancel()
        assertFalse(budget.complete("new"))
        assertNull(budget.remainingMs("new"))
    }
}
