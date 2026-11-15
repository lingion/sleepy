package com.lingion.sleepy.ui.nav

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorCreationTest {
    @Test
    fun repeatedStartsInsertOnceWhileBlockedAndAfterReady() = runBlocking {
        withTimeout(5_000) {
            val started = CompletableDeferred<Unit>()
            val inserted = CompletableDeferred<Long>()
            var createCalls = 0
            val creation = EditorCreation(
                scope = this,
                create = {
                    createCalls++
                    started.complete(Unit)
                    inserted.await()
                },
                discard = { error("A visible editor must not be discarded") },
            )

            repeat(3) { creation.start() }
            started.await()
            assertEquals(1, createCalls)
            assertSame(EditorCreationState.Initializing, creation.state.value)

            inserted.complete(42L)
            assertEquals(EditorCreationState.Ready(42L), creation.state.first { it is EditorCreationState.Ready })
            creation.start()
            yield()
            assertEquals(1, createCalls)
        }
    }

    @Test
    fun earlyBackWaitsForInsertAndDeletionBeforeRevealingParent() = runBlocking {
        withTimeout(5_000) {
            val started = CompletableDeferred<Unit>()
            val inserted = CompletableDeferred<Long>()
            val discardStarted = CompletableDeferred<Long>()
            val discarded = CompletableDeferred<Unit>()
            val closed = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val creation = EditorCreation(
                scope = this,
                create = {
                    events += "insert started"
                    started.complete(Unit)
                    inserted.await().also { events += "insert finished" }
                },
                discard = { id ->
                    events += "delete started"
                    discardStarted.complete(id)
                    discarded.await()
                    events += "delete finished"
                },
            )

            creation.start()
            started.await()
            creation.discardAndClose {
                events += "parent revealed"
                closed.complete(Unit)
            }
            yield()
            assertFalse(closed.isCompleted)
            assertFalse(discardStarted.isCompleted)

            inserted.complete(42L)
            assertEquals(42L, discardStarted.await())
            assertFalse(closed.isCompleted)
            discarded.complete(Unit)
            closed.await()
            assertEquals(
                listOf("insert started", "insert finished", "delete started", "delete finished", "parent revealed"),
                events,
            )
        }
    }

    @Test
    fun repeatedBackClosesAndDiscardsOnce() = runBlocking {
        withTimeout(5_000) {
            val started = CompletableDeferred<Unit>()
            val inserted = CompletableDeferred<Long>()
            val closed = CompletableDeferred<Unit>()
            var discardCalls = 0
            var closeCalls = 0
            val creation = EditorCreation(
                scope = this,
                create = {
                    started.complete(Unit)
                    inserted.await()
                },
                discard = { id ->
                    assertEquals(42L, id)
                    discardCalls++
                },
            )
            val onClosed = {
                closeCalls++
                closed.complete(Unit)
                Unit
            }

            creation.start()
            started.await()
            repeat(3) { creation.discardAndClose(onClosed) }
            inserted.complete(42L)
            closed.await()
            creation.discardAndClose(onClosed)
            yield()
            assertEquals(1, discardCalls)
            assertEquals(1, closeCalls)
        }
    }

    @Test
    fun failedCreationRemainsClosableWithoutDeletingAnUnrelatedRecord() = runBlocking {
        withTimeout(5_000) {
            val failure = IOException("Insert failed")
            val closed = CompletableDeferred<Unit>()
            var createCalls = 0
            var discardCalls = 0
            val creation = EditorCreation(
                scope = this,
                create = {
                    createCalls++
                    throw failure
                },
                discard = { discardCalls++ },
            )

            creation.start()
            val failed = creation.state.first { it is EditorCreationState.Failed } as EditorCreationState.Failed
            assertSame(failure, failed.cause)
            creation.start()
            creation.discardAndClose { closed.complete(Unit) }
            closed.await()
            assertEquals(1, createCalls)
            assertEquals(0, discardCalls)
        }
    }

    @Test
    fun restoredRecordIsReusedAndCanBeDiscardedWithoutInsertingAgain() = runBlocking {
        withTimeout(5_000) {
            val closed = CompletableDeferred<Unit>()
            val discarded = mutableListOf<Long>()
            var createCalls = 0
            val creation = EditorCreation(
                scope = this,
                restoredId = 73L,
                create = {
                    createCalls++
                    99L
                },
                discard = { discarded += it },
            )

            assertEquals(EditorCreationState.Ready(73L), creation.state.value)
            repeat(3) { creation.start() }
            yield()
            assertEquals(0, createCalls)
            creation.discardAndClose { closed.complete(Unit) }
            closed.await()
            assertEquals(listOf(73L), discarded)
            assertEquals(0, createCalls)
        }
    }

    @Test
    fun backBeforeFirstFramePreventsADeferredStartFromInserting() = runBlocking {
        withTimeout(5_000) {
            val closed = CompletableDeferred<Unit>()
            var createCalls = 0
            var discardCalls = 0
            val creation = EditorCreation(
                scope = this,
                create = {
                    createCalls++
                    42L
                },
                discard = { discardCalls++ },
            )

            creation.discardAndClose { closed.complete(Unit) }
            creation.start()
            closed.await()
            creation.start()
            yield()
            assertTrue(closed.isCompleted)
            assertEquals(0, createCalls)
            assertEquals(0, discardCalls)
        }
    }

    @Test
    fun acceptedSavePreventsBackFromDiscardingTheRecordOrClosingAgain() = runBlocking {
        withTimeout(5_000) {
            var discardCalls = 0
            var closeCalls = 0
            val creation = EditorCreation(
                scope = this,
                restoredId = 42L,
                create = { error("A restored record must not be inserted again") },
                discard = { discardCalls++ },
            )

            assertTrue(creation.tryBeginSave())
            repeat(3) { creation.discardAndClose { closeCalls++ } }
            assertFalse(creation.tryBeginSave())
            yield()
            assertEquals(0, discardCalls)
            assertEquals(0, closeCalls)
            assertEquals(EditorCreationState.Ready(42L), creation.state.value)
        }
    }

    @Test
    fun backAcceptedBeforeSavePreventsSavingDuringAndAfterDeletion() = runBlocking {
        withTimeout(5_000) {
            val discardStarted = CompletableDeferred<Unit>()
            val deletionAllowed = CompletableDeferred<Unit>()
            val closed = CompletableDeferred<Unit>()
            val creation = EditorCreation(
                scope = this,
                restoredId = 42L,
                create = { error("A restored record must not be inserted again") },
                discard = { id ->
                    assertEquals(42L, id)
                    discardStarted.complete(Unit)
                    deletionAllowed.await()
                },
            )

            creation.discardAndClose { closed.complete(Unit) }
            assertFalse(creation.tryBeginSave())
            discardStarted.await()
            assertFalse(creation.tryBeginSave())
            assertFalse(closed.isCompleted)
            deletionAllowed.complete(Unit)
            closed.await()
            assertFalse(creation.tryBeginSave())
        }
    }

    @Test
    fun failedDeletionKeepsItsRecordIdAndRetriesCleanupBeforeClosing() = runBlocking {
        withTimeout(5_000) {
            val failure = IOException("Delete failed")
            val deletedIds = mutableListOf<Long>()
            val closed = CompletableDeferred<Unit>()
            var closeCalls = 0
            val creation = EditorCreation(
                scope = this,
                restoredId = 42L,
                create = { error("Cleanup retry must not insert another record") },
                discard = { id ->
                    deletedIds += id
                    if (deletedIds.size == 1) throw failure
                },
            )

            creation.discardAndClose { closeCalls++ }
            val failed = creation.state.first { it is EditorCreationState.Failed } as EditorCreationState.Failed
            assertSame(failure, failed.cause)
            assertEquals(0, closeCalls)
            creation.discardAndClose {
                closeCalls++
                closed.complete(Unit)
            }
            closed.await()
            assertEquals(listOf(42L, 42L), deletedIds)
            assertEquals(1, closeCalls)
        }
    }

    @Test
    fun saveCompletionSurvivesTheEditorScopeBeingCancelled() = runBlocking {
        withTimeout(5_000) {
            val write = CompletableDeferred<Unit>()
            val editorJob = Job(coroutineContext[Job])
            val editorScope = CoroutineScope(coroutineContext + editorJob)
            var editorCompletionCalled = false
            var discardCalls = 0
            val creation = EditorCreation(
                scope = this,
                restoredId = 42L,
                create = { error("Saving must reuse its record") },
                discard = { discardCalls++ },
            )

            assertTrue(creation.tryBeginSave())
            creation.trackSave(write)
            editorScope.launch {
                write.join()
                editorCompletionCalled = true
                creation.completeSave()
            }
            yield()
            assertEquals(EditorCreationState.Ready(42L), creation.state.value)
            editorJob.cancelAndJoin()
            write.complete(Unit)

            assertEquals(EditorCreationState.Saved(42L), creation.state.first { it is EditorCreationState.Saved })
            assertFalse(editorCompletionCalled)
            creation.discardAndClose { error("A saved record must not be discarded") }
            creation.completeSave()
            yield()
            assertEquals(EditorCreationState.Saved(42L), creation.state.value)
            assertEquals(0, discardCalls)
        }
    }

    @Test
    fun failedWriteUnlocksBackAndRetainsTheIdForDiscard() = runBlocking {
        withTimeout(5_000) {
            val write = CompletableDeferred<Unit>()
            val failure = IOException("Write failed")
            val closed = CompletableDeferred<Unit>()
            val discarded = mutableListOf<Long>()
            val creation = EditorCreation(
                scope = this,
                restoredId = 42L,
                create = { error("A failed write must not reinsert its record") },
                discard = { discarded += it },
            )

            assertTrue(creation.tryBeginSave())
            creation.trackSave(write)
            write.completeExceptionally(failure)
            // Job.join does not propagate the write failure. A stale page callback
            // must not convert that failed write into a successful save.
            write.join()
            creation.completeSave()
            val failed = creation.state.first { it is EditorCreationState.Failed } as EditorCreationState.Failed
            assertSame(failure, failed.cause)
            assertFalse(creation.tryBeginSave())
            creation.discardAndClose { closed.complete(Unit) }
            closed.await()
            assertEquals(listOf(42L), discarded)
        }
    }

    @Test
    fun policyCompletionMarksSavedOnceWithoutASeparateTrackedJob() = runBlocking {
        withTimeout(5_000) {
            val creation = EditorCreation(
                scope = this,
                restoredId = 42L,
                create = { error("Policy save must reuse its record") },
                discard = { error("A saved record must not be discarded") },
            )

            assertTrue(creation.tryBeginSave())
            creation.completeSave()
            creation.completeSave()
            assertEquals(EditorCreationState.Saved(42L), creation.state.value)
            assertFalse(creation.tryBeginSave())
            creation.discardAndClose { error("A completed save must not close twice") }
            yield()
        }
    }
}
