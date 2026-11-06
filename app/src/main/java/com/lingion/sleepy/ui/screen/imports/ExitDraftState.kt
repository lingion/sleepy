package com.lingion.sleepy.ui.screen.imports

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.DialogActionButtons
import com.lingion.sleepy.ui.theme.SleepyTheme

/** The user's choice when leaving an import that may contain recoverable work. */
enum class ExitDraftChoice {
    Continue,
    KeepDraft,
    DeleteDraft,
}

/** Minimal state needed by the exit confirmation flow; persistence is deliberately outside it. */
data class ExitDraftState(
    val activeImport: Boolean = false,
    val confirmationVisible: Boolean = false,
)

sealed interface ExitDraftEvent {
    data object RequestExit : ExitDraftEvent
    data class Choose(val choice: ExitDraftChoice) : ExitDraftEvent
}

enum class ExitDraftOutcome {
    None,
    FinishDirectly,
    KeepDraft,
    DeleteDraft,
}

data class ExitDraftReduction(
    val state: ExitDraftState,
    val outcome: ExitDraftOutcome,
)

/**
 * Pure exit reducer. It does not save or delete anything; callers own those side effects.
 */
fun reduceExitDraftState(
    state: ExitDraftState,
    event: ExitDraftEvent,
): ExitDraftReduction = when (event) {
    ExitDraftEvent.RequestExit -> if (state.activeImport) {
        ExitDraftReduction(state.copy(confirmationVisible = true), ExitDraftOutcome.None)
    } else {
        ExitDraftReduction(state.copy(confirmationVisible = false), ExitDraftOutcome.FinishDirectly)
    }

    is ExitDraftEvent.Choose -> when (event.choice) {
        ExitDraftChoice.Continue ->
            ExitDraftReduction(state.copy(confirmationVisible = false), ExitDraftOutcome.None)
        ExitDraftChoice.KeepDraft ->
            ExitDraftReduction(state.copy(confirmationVisible = false), ExitDraftOutcome.KeepDraft)
        ExitDraftChoice.DeleteDraft ->
            ExitDraftReduction(state.copy(confirmationVisible = false), ExitDraftOutcome.DeleteDraft)
    }
}

/**
 * UI-only confirmation surface. Draft persistence is supplied by the caller for later integration.
 * 2026-09-16 用户: 三个裸 TextButton 既不同排又无色块背景 — 换 DialogActionButtons
 * 三键等宽色块一行排 (继续/留草稿=secondaryContainer, 退出并删除草稿=errorContainer)。
 */
@Composable
fun ExitDraftConfirmationDialog(
    onContinue: () -> Unit,
    onKeepDraft: () -> Unit,
    onDeleteDraft: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onContinue,
        title = { Text(stringResource(R.string.jw_import_exit_title)) },
        text = {
            Column {
                Text(stringResource(R.string.jw_import_exit_message))
                Spacer(Modifier.height(20.dp))
                // 继续(第三位 secondary) / 留草稿(dismiss 位 secondary) / 退出并删除(confirm 位 destructive)
                DialogActionButtons(
                    confirmText = stringResource(R.string.jw_import_exit_delete_draft),
                    onConfirm = onDeleteDraft,
                    dismissText = stringResource(R.string.jw_import_exit_continue),
                    onDismiss = onContinue,
                    thirdText = stringResource(R.string.jw_import_exit_keep_draft),
                    onThird = onKeepDraft,
                    thirdDestructive = false,
                    destructive = true,
                    dbgTagPrefix = "exit_draft"
                )
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}
