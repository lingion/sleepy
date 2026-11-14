package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.PeriodTableEntity

/**
 * Returns the combination only when it is usable against the confirmed base rows; otherwise "".
 * An unusable seasonal rule must not block the whole import: courses still import on the base schedule.
 */
internal fun usableComboScheduleJson(confirmedTimeJson: String, comboScheduleJson: String): String {
    if (comboScheduleJson.isBlank()) return ""
    return runCatching { EffectivePeriodResolver.validate(confirmedTimeJson, comboScheduleJson) }
        .fold(onSuccess = { comboScheduleJson }, onFailure = { "" })
}

/** A selected existing schedule stays shared; otherwise confirmed rows own the new schedule. */
internal fun importedPeriodTableDraft(
    bindingId: Long?, name: String, confirmedTimeJson: String, comboScheduleJson: String = ""
): PeriodTableEntity? = if (bindingId != null) null else PeriodTableEntity(
    name = name,
    nodesPerDay = TimeTableUtils.parseTimeSlotRows(confirmedTimeJson).size.coerceAtLeast(1),
    timeJson = confirmedTimeJson,
    comboScheduleJson = usableComboScheduleJson(confirmedTimeJson, comboScheduleJson)
)
