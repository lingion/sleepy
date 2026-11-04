package com.lingion.sleepy.ui.screen.mine

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.lingion.sleepy.data.entity.SmartPeriodConfig
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Keep raw, possibly incomplete input intact when visiting the holiday child page.
internal val TimeSlotRowsSaver = listSaver<SnapshotStateList<TimeTableUtils.TimeSlotRow>, String>(
    save = { rows -> rows.flatMap { listOf(it.node.toString(), it.start, it.end, it.edgeClass?.name.orEmpty()) } },
    restore = { values ->
        mutableStateListOf<TimeTableUtils.TimeSlotRow>().apply {
            addAll(values.chunked(4).map { row ->
                TimeTableUtils.TimeSlotRow(
                    node = row[0].toInt(), start = row[1], end = row[2],
                    edgeClass = row[3].takeIf { it.isNotEmpty() }?.let { enumValueOf(it) },
                )
            })
        }
    },
)

internal val SmartPeriodConfigSaver = Saver<SmartPeriodConfig, String>(
    save = { Json.encodeToString(it) },
    restore = { Json.decodeFromString<SmartPeriodConfig>(it) },
)
