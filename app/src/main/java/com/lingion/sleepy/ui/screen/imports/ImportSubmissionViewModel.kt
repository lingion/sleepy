package com.lingion.sleepy.ui.screen.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingion.sleepy.data.imports.ImportPlan
import com.lingion.sleepy.data.repository.ImportApplyResult
import com.lingion.sleepy.data.repository.ScheduleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ImportSubmissionState(
    val applying: Boolean = false,
    val result: ImportApplyResult? = null
)

/** Submission outlives a dialog composition; JW drafts are consumed in the same database commit. */
internal class ImportSubmissionViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(ImportSubmissionState())
    val state = mutableState.asStateFlow()

    fun submit(repository: ScheduleRepository, plan: ImportPlan, draftId: String?) {
        val previous = mutableState.value
        if (previous.applying || previous.result is ImportApplyResult.Applied) return
        mutableState.value = ImportSubmissionState(applying = true)
        viewModelScope.launch {
            withContext(NonCancellable) {
                val result = try {
                    withContext(Dispatchers.IO) { repository.applyImportPlan(plan, draftId) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    ImportApplyResult.Failed(failure.message.orEmpty())
                }
                mutableState.value = ImportSubmissionState(result = result)
            }
        }
    }

    fun consumeRetryableResult() {
        if (mutableState.value.result !is ImportApplyResult.Applied) {
            mutableState.value = ImportSubmissionState()
        }
    }
}
