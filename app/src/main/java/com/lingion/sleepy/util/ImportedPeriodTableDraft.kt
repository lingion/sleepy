package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.PeriodTableEntity

/** A selected existing schedule stays shared; otherwise confirmed rows own the new schedule. */
internal fun importedPeriodTableDraft(
    bindingId: Long?, name: String, confirmedTimeJson: String, comboScheduleJson: String = ""
): PeriodTableEntity? {
    if (bindingId != null) return null
    // 组合作息在导入时就必须通过校验, 否则保存后每天都会悄悄退回单一作息, 用户无从察觉。
    if (comboScheduleJson.isNotBlank()) {
        EffectivePeriodResolver.validate(confirmedTimeJson, comboScheduleJson)
    }
    return PeriodTableEntity(
        name = name,
        nodesPerDay = TimeTableUtils.parseTimeSlotRows(confirmedTimeJson).size.coerceAtLeast(1),
        timeJson = confirmedTimeJson,
        comboScheduleJson = comboScheduleJson
    )
}
