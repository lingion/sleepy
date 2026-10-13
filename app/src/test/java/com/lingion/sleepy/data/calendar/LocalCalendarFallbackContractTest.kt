package com.lingion.sleepy.data.calendar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalCalendarFallbackContractTest {
    private val info = SystemCalendarInfo(42, "Sleepy", "com.lingion.sleepy.account", "LOCAL")
    private inner class Store : LocalCalendarStore {
        var permission = true
        var queryFailure = false
        var ownedFailure = false
        var insertFailure = false
        var enableResult = true
        var inserts = 0
        var enables = 0
        var queries = 0
        var rows = emptyList<LocalCalendarRow>()
        var calendars = emptyList<LocalCalendarRow>()
        override fun hasPermissions() = permission
        override fun queryWritable(): List<LocalCalendarRow> {
            queries++
            check(!queryFailure)
            return calendars
        }
        override fun queryOwned(): List<LocalCalendarRow> {
            queries++
            check(!ownedFailure)
            return rows
        }
        override fun enable(row: LocalCalendarRow): Boolean {
            enables++
            return enableResult
        }
        override fun insert(): SystemCalendarInfo? {
            inserts++
            if (insertFailure) return null
            Thread.sleep(10) // Widen the race window; the policy must hold its lock throughout.
            rows = listOf(LocalCalendarRow(info, true, false, true, true))
            return info
        }
    }

    @Test fun repeatedAndConcurrentCallsCreateExactlyOneCalendar() = runBlocking {
        val policy = LocalCalendarFallback()
        val store = Store()
        val results = (1..20).map { async(Dispatchers.Default) { policy.ensure(store) } }.awaitAll()
        assertEquals(List(20) { info }, results)
        assertEquals(info, policy.ensure(store))
        assertEquals(1, store.inserts)
    }

    @Test fun deniedPermissionDoesNotAccessProvider() = runBlocking {
        val store = Store().apply { permission = false }
        val policy = LocalCalendarFallback()
        assertNull(policy.ensure(store))
        assertTrue(policy.writable(store).isEmpty())
        assertEquals(0, store.queries)
        assertEquals(0, store.inserts)
    }

    @Test fun failedQueriesNeverAuthorizeInsert() = runBlocking {
        for (writable in listOf(true, false)) {
            val store = Store().apply { queryFailure = writable; ownedFailure = !writable }
            val policy = LocalCalendarFallback()
            assertTrue(policy.writable(store).isEmpty())
            assertEquals(0, store.inserts)
        }
    }

    @Test fun successfulEmptyQueryCreatesFallbackButExternalCalendarDoesNot() = runBlocking {
        val policy = LocalCalendarFallback()
        val empty = Store()
        assertEquals(listOf(info), policy.writable(empty))
        assertEquals(1, empty.inserts)
        val external = Store().apply { calendars = listOf(LocalCalendarRow(info.copy(id = 99), true, false, true, true)) }
        assertEquals(external.calendars.map { it.info }, policy.writable(external))
        assertEquals(0, external.inserts)
        assertEquals(1, external.queries)
    }

    @Test fun hiddenOrInstancesDisabledOwnedCalendarIsRepairedWithoutDuplicate() = runBlocking {
        for (visible in listOf(true, false)) {
            val store = Store().apply { rows = listOf(LocalCalendarRow(info, true, false, visible, false)) }
            assertEquals(info, LocalCalendarFallback().ensure(store))
            assertEquals(1, store.enables)
            assertEquals(0, store.inserts)
        }
    }

    @Test fun failedRepairAndUnusableExistingRowsDoNotDuplicate() = runBlocking {
        val repair = Store().apply {
            rows = listOf(LocalCalendarRow(info, true, false, true, false)); enableResult = false
        }
        assertNull(LocalCalendarFallback().ensure(repair))
        assertEquals(0, repair.inserts)
        for (deleted in listOf(true, false)) {
            val store = Store().apply { rows = listOf(LocalCalendarRow(info, deleted, deleted, true, true)) }
            assertNull(LocalCalendarFallback().ensure(store))
            assertEquals(0, store.inserts)
        }
    }

    @Test fun insertionFailureReturnsEmptyWithoutInventedCalendar() = runBlocking {
        val store = Store().apply { insertFailure = true }
        assertTrue(LocalCalendarFallback().writable(store).isEmpty())
        assertEquals(1, store.inserts)
    }

    @Test fun ownedRepairFailurePreservesThirdPartyWritableCalendars() = runBlocking {
        val external = info.copy(id = 99, accountName = "external")
        val store = Store().apply {
            calendars = listOf(
                LocalCalendarRow(info, true, false, true, false, owned = true),
                LocalCalendarRow(external, true, false, true, false)
            )
            enableResult = false
        }
        assertEquals(listOf(external), LocalCalendarFallback().writable(store))
        assertEquals(1, store.enables)
        assertEquals(0, store.inserts)
    }

    @Test fun visibleOwnedCalendarWithDisabledInstancesIsRepaired() = runBlocking {
        val store = Store().apply {
            calendars = listOf(LocalCalendarRow(info, true, false, true, false, owned = true))
        }
        assertEquals(listOf(info), LocalCalendarFallback().writable(store))
        assertEquals(1, store.enables)
        assertEquals(0, store.inserts)
    }
}
