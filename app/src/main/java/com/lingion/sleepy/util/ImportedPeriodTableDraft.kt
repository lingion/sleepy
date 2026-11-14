package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.PeriodTableEntity

/** A selected existing schedule stays shared; otherwise confirmed rows own the new schedule. */
internal fun importedPeriodTableDraft(
    bindingId: Long?, name: String, confirmedTimeJson: String, comboScheduleJson: String = ""
): PeriodTableEntity? = if (bindingId != null) {
    require(comboScheduleJson.isBlank()) {
        "组合时段不能绑定已有共享作息表，请选择新建作息表"
    }
    null
} else PeriodTableEntity(
    name = name,
    nodesPerDay = TimeTableUtils.parseTimeSlotRows(confirmedTimeJson).size.coerceAtLeast(1),
    timeJson = confirmedTimeJson,
    comboScheduleJson = comboScheduleJson
)
