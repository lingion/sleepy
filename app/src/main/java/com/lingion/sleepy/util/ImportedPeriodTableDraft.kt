package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.PeriodTableEntity

/** A selected existing schedule stays shared; otherwise confirmed rows own the new schedule. */
internal fun importedPeriodTableDraft(
    bindingId: Long?, name: String, confirmedTimeJson: String
): PeriodTableEntity? = if (bindingId != null) null else PeriodTableEntity(
    name = name,
    nodesPerDay = TimeTableUtils.parseTimeSlotRows(confirmedTimeJson).size.coerceAtLeast(1),
    timeJson = confirmedTimeJson
)
