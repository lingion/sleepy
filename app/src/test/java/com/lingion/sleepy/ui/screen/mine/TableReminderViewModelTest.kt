package com.lingion.sleepy.ui.screen.mine

import android.os.Looper
import com.lingion.sleepy.data.entity.TimeTableEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TableReminderViewModelTest {
    private val off = TimeTableEntity(id = 7L, name = "Test", startDate = "2026-09-01", reminderEnabled = false)
    private val on = off.copy(reminderEnabled = true)
    private val source = MutableStateFlow(listOf(off))
    private val saved = CompletableDeferred<Unit>()
    private val writes = mutableListOf<Pair<Long, Boolean>>()
    private var failures = 0

    private fun model() = TableReminderViewModel(source) { id, enabled ->
        writes += id to enabled
        saved.await()
    }
    private fun pumpMain() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun successfulWriteCannotClearPendingBeforeThisUiConsumesTheEcho() {
        val vm = model()
        vm.setEnabled(7L, true) { failures++ }
        pumpMain()
        saved.complete(Unit)
        pumpMain()
        assertTrue(7L in vm.pending)
        // Three subsequent UI observations still carry the old Room snapshot.
        repeat(3) {
            vm.acknowledgeObservedTables(listOf(off))
            pumpMain()
            assertTrue(7L in vm.pending)
        }
        source.value = listOf(on)
        pumpMain()
        assertTrue("a second Flow subscriber must not acknowledge ahead of the UI", 7L in vm.pending)
        vm.acknowledgeObservedTables(listOf(on))
        assertFalse(7L in vm.pending)
        assertEquals(0, failures)
    }

    @Test fun uiEchoBeforeWriterReturnsStillWaitsForSuccessfulCompletion() {
        val vm = model()
        vm.setEnabled(7L, true) { failures++ }
        pumpMain()
        vm.acknowledgeObservedTables(listOf(on))
        assertTrue(7L in vm.pending)
        saved.complete(Unit)
        pumpMain()
        assertTrue(7L in vm.pending)
        vm.acknowledgeObservedTables(listOf(on))
        assertFalse(7L in vm.pending)
    }

    @Test fun failedWriteExplicitlyReleasesPendingWithoutAValueEcho() {
        val vm = model()
        vm.setEnabled(7L, true) { failures++ }
        pumpMain()
        saved.completeExceptionally(IllegalStateException("write failed"))
        pumpMain()
        assertFalse(7L in vm.pending)
        assertEquals(1, failures)
    }

    @Test fun cancelledWriteReleasesPendingWithoutReportingASaveFailure() {
        val vm = model()
        vm.setEnabled(7L, true) { failures++ }
        pumpMain()
        saved.completeExceptionally(CancellationException("cancelled"))
        pumpMain()
        assertFalse(7L in vm.pending)
        assertEquals(0, failures)
    }

    @Test fun deletedRowReleasesPendingAndLateCompletionCannotRestoreIt() {
        val vm = model()
        vm.setEnabled(7L, true) { failures++ }
        pumpMain()
        vm.acknowledgeObservedTables(emptyList())
        assertFalse(7L in vm.pending)
        saved.complete(Unit)
        pumpMain()
        assertTrue(vm.pending.isEmpty())
    }

    @Test fun oneWriteRemainsTheLimitWhileWaitingForUiAcknowledgement() {
        val vm = model()
        vm.setEnabled(7L, true) { failures++ }
        pumpMain()
        vm.setEnabled(7L, false) { failures++ }
        saved.complete(Unit)
        pumpMain()
        vm.setEnabled(7L, false) { failures++ }
        pumpMain()
        assertEquals(listOf(7L to true), writes)
        assertTrue(7L in vm.pending)
    }
}
