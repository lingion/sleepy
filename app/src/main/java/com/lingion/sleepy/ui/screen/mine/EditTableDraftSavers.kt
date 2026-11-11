package com.lingion.sleepy.ui.screen.mine

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.lingion.sleepy.data.entity.SmartPeriodConfig
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TIME_SLOT_ROWS_DRAFT_V2 = "time-slot-rows:v2"

// Keep raw input and its insertion lifecycle intact when visiting the holiday child page.
internal val TimeSlotRowsSaver = listSaver<SnapshotStateList<TimeTableUtils.TimeSlotRow>, String>(
    save = { rows ->
        listOf(TIME_SLOT_ROWS_DRAFT_V2) + rows.flatMap {
            listOf(it.node.toString(), it.start, it.end, it.edgeClass?.name.orEmpty(), it.pendingManualInsertion.toString())
        }
    },
    restore = { values ->
        // Legacy drafts begin with a numeric node, so the version marker cannot be
        // mistaken for either a four-column row or an ambiguous multiple of five.
        val versioned = values.firstOrNull() == TIME_SLOT_ROWS_DRAFT_V2
        val fields = if (versioned) values.drop(1) else values
        val columns = if (versioned) 5 else 4
        if (fields.size % columns != 0) null else runCatching {
            mutableStateListOf<TimeTableUtils.TimeSlotRow>().apply {
                addAll(fields.chunked(columns).map { row ->
                    val restored = TimeTableUtils.TimeSlotRow(
                        node = row[0].toInt(), start = row[1], end = row[2],
                        edgeClass = row[3].takeIf { it.isNotEmpty() }?.let { enumValueOf(it) },
                    )
                    if (versioned) restored.copy(pendingManualInsertion = row[4].toBooleanStrict()) else restored
                })
            }
        }.getOrNull()
    },
)

internal val SmartPeriodConfigSaver = Saver<SmartPeriodConfig, String>(
    save = { Json.encodeToString(it) },
    restore = { Json.decodeFromString<SmartPeriodConfig>(it) },
)
