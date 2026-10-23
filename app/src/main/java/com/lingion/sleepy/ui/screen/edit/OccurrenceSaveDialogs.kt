package com.lingion.sleepy.ui.screen.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.CourseOccurrences
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.HolidayTransferEntry
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** A decision belongs to the suspended immutable save draft, not the live form. */
internal class OccurrenceSaveDialogs(private val string: (Int, Array<out Any>) -> String) {
    constructor(context: android.content.Context) : this({ id, args -> context.getString(id, *args) })
    private fun text(id: Int, vararg args: Any) = string(id, args)
    data class Question(val title: String, val message: String, val choices: List<String>, val reply: CancellableContinuation<Int?>, val destructive: Boolean)
    var question by mutableStateOf<Question?>(null)
        private set

    suspend fun ask(title: String, message: String, vararg choices: String, destructive: Boolean = false): Int? = suspendCancellableCoroutine { reply ->
        question = Question(title, message, choices.toList(), reply, destructive)
        reply.invokeOnCancellation { question = null }
    }

    fun answer(index: Int?) {
        val q = question ?: return
        question = null
        if (q.reply.isActive) q.reply.resume(index)
    }

    /** Split only the dates affected by a holiday decision; ordinary repeated ranges stay compact. */
    suspend fun destinations(drafts: List<CourseEntity>, table: TimeTableEntity, transfers: List<HolidayTransferEntry>): List<CourseEntity>? {
        val result = mutableListOf<CourseEntity>()
        for (draft in drafts) {
            if (draft.fixedDate != null) { result += draft; continue }
            var remaining = listOf(draft)
            val affected = (transfers.map { it.sourceDate } + transfers.map { it.targetDate }).distinct().sorted()
            for (date in affected) {
                val week = DateUtils.currentWeek(table.startDate, date)
                if (DateUtils.semesterStatus(table.startDate, table.maxWeek, date) != DateUtils.SemesterStatus.IN_RANGE ||
                    draft.day != date.dayOfWeek.value || !draft.inWeek(week)) continue
                val incoming = transfers.lastOrNull { it.targetDate == date }
                val outgoing = transfers.lastOrNull { it.sourceDate == date }
                val resolved = if (incoming != null) {
                    when (ask(text(R.string.occurrence_destination_title), text(R.string.occurrence_destination_message, draft.courseName, date, incoming.sourceDate),
                        text(R.string.occurrence_follow_transfer), text(R.string.occurrence_direct))) {
                        // The displayed target day replaces its regular timetable with the source day's classes.
                        // Keep the primary action consistent with that reverse lookup.
                        0 -> {
                            require(DateUtils.semesterStatus(table.startDate, table.maxWeek, incoming.sourceDate) == DateUtils.SemesterStatus.IN_RANGE) {
                                text(R.string.occurrence_invalid_date)
                            }
                            CourseOccurrences.atDate(draft, incoming.sourceDate, table, fixed = false)
                        }
                        1 -> {
                            if (ask(text(R.string.occurrence_overwritten_title),
                                text(R.string.occurrence_overwritten_message, draft.courseName, date, incoming.sourceDate),
                                text(R.string.occurrence_direct_anyway), destructive = true) == null) return null
                            // Direct scheduling belongs to the target's original timetable, which the transfer hides.
                            CourseOccurrences.atDate(draft, date, table, fixed = false)
                        }
                        else -> return null
                    }
                } else {
                    if (ask(text(R.string.occurrence_transfer_title), text(R.string.occurrence_transfer_message, draft.courseName, date, outgoing!!.targetDate), text(R.string.occurrence_continue)) == null) return null
                    CourseOccurrences.atDate(draft, date, table, fixed = false)
                }
                remaining = remaining.flatMap { row ->
                    if (row.inWeek(week)) CourseOccurrences.without(row, date, table.startDate) else listOf(row)
                }
                result += resolved
            }
            result += remaining
        }
        return result
    }

    suspend fun confirmConflicts(details: List<String>): Boolean = details.isEmpty() ||
        ask(text(R.string.conflict_detail_title), details.joinToString("\n"), text(R.string.conflict_detail_save_anyway), destructive = true) != null
}

@Composable
internal fun OccurrenceSaveDialogHost(dialogs: OccurrenceSaveDialogs) {
    val q = dialogs.question ?: return
    AlertDialog(
        onDismissRequest = { dialogs.answer(null) },
        title = { Text(q.title) },
        text = { Column {
            Text(q.message, Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()))
            Spacer(Modifier.height(16.dp))
            com.lingion.sleepy.ui.component.DialogActionButtons(
                confirmText = q.choices.first(), onConfirm = { dialogs.answer(0) },
                dismissText = stringResource(R.string.conflict_detail_go_back), onDismiss = { dialogs.answer(null) },
                thirdText = q.choices.getOrNull(1), onThird = { dialogs.answer(1) }, destructive = q.destructive
            )
        } },
        confirmButton = {},
    )
}
