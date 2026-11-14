package com.lingion.sleepy.ui.nav

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface EditorCreationState {
    data object Initializing : EditorCreationState
    data class Ready(val id: Long) : EditorCreationState
    data class Saved(val id: Long) : EditorCreationState
    data class Failed(val cause: Exception) : EditorCreationState
}

/** One creation per navigation entry, including configuration changes and early Back. */
internal class EditorCreation(
    private val scope: CoroutineScope,
    restoredId: Long? = null,
    private val create: suspend () -> Long,
    private val discard: suspend (Long) -> Unit,
) {
    private val mutableState = MutableStateFlow<EditorCreationState>(
        restoredId?.let(EditorCreationState::Ready) ?: EditorCreationState.Initializing
    )
    val state = mutableState.asStateFlow()
    private var createdId: Long? = restoredId
    private var job: Job? = null
    private var closing = false
    private var saving = false
    private var saveJob: Job? = null

    fun start() {
        if (job != null || mutableState.value is EditorCreationState.Ready || closing) return
        job = scope.launch {
            try {
                val id = create()
                check(id > 0) { "Creation returned no record" }
                createdId = id
                mutableState.value = EditorCreationState.Ready(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value = EditorCreationState.Failed(failure)
            }
        }
    }

    fun tryBeginSave(): Boolean {
        if (closing || mutableState.value !is EditorCreationState.Ready) return false
        closing = true
        saving = true
        return true
    }

    /** Observe the ViewModel's write even when its editor composition goes away. */
    fun trackSave(job: Job) {
        if (!saving || saveJob != null) return
        saveJob = job
        job.invokeOnCompletion { failure ->
            scope.launch {
                if (!saving || saveJob !== job) return@launch
                if (failure == null) {
                    completeSave()
                } else {
                    saving = false
                    closing = false
                    mutableState.value = EditorCreationState.Failed(
                        failure as? Exception ?: IllegalStateException("Save failed", failure)
                    )
                }
            }
        }
    }

    /** Also used by the existing policy transaction's successful completion callback. */
    fun completeSave() {
        if (!saving || saveJob?.isCancelled == true || saveJob?.isCompleted == false) return
        val id = createdId ?: return
        saving = false
        mutableState.value = EditorCreationState.Saved(id)
    }

    fun discardAndClose(onClosed: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            // Cancelling a Room insert can lose its committed id. Let it finish, then
            // delete before revealing the parent list, including during an early Back.
            job?.join()
            try {
                createdId?.let { discard(it) }
                onClosed()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                closing = false
                mutableState.value = EditorCreationState.Failed(failure)
            }
        }
    }
}
