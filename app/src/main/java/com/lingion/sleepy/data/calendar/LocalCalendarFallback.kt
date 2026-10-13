package com.lingion.sleepy.data.calendar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class LocalCalendarRow(
    val info: SystemCalendarInfo,
    val writable: Boolean,
    val deleted: Boolean,
    val visible: Boolean,
    val syncEvents: Boolean,
    val owned: Boolean = false
)

internal interface LocalCalendarStore {
    fun hasPermissions(): Boolean
    // Null provider cursors and other query failures must throw; empty means success.
    fun queryWritable(): List<LocalCalendarRow>
    fun queryOwned(): List<LocalCalendarRow>
    fun enable(row: LocalCalendarRow): Boolean
    fun insert(): SystemCalendarInfo?
}

/** Serializes the full check/create operation across callers in this process. */
internal class LocalCalendarFallback {
    private val mutex = Mutex()

    suspend fun ensure(store: LocalCalendarStore): SystemCalendarInfo? = safely(null) {
        mutex.withLock {
            if (!store.hasPermissions()) null else ensureLocked(store)
        }
    }

    suspend fun writable(store: LocalCalendarStore): List<SystemCalendarInfo> = safely(emptyList()) {
        mutex.withLock {
            if (!store.hasPermissions()) return@withLock emptyList()
            val calendars = store.queryWritable().mapNotNull { row ->
                if (row.owned && !row.syncEvents) {
                    if (safely(false) { store.enable(row) }) row.info else null
                } else row.info
            }
            if (calendars.isNotEmpty()) calendars else listOfNotNull(ensureLocked(store))
        }
    }

    private fun ensureLocked(store: LocalCalendarStore): SystemCalendarInfo? {
        val owned = store.queryOwned()
        val row = owned.firstOrNull { it.writable && !it.deleted }
        if (row != null) {
            if ((!row.visible || !row.syncEvents) && !store.enable(row)) return null
            return row.info
        }
        // Existing deleted/read-only calendars do not authorize creating a duplicate.
        return if (owned.isEmpty() && store.hasPermissions()) store.insert() else null
    }

    private suspend fun <T> safely(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        fallback
    }
}
