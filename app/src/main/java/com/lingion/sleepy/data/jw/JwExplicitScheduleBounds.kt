package com.lingion.sleepy.data.jw

/** Shared allocation guard for explicit coordinates, not a school's timetable limit. */
internal const val EXPLICIT_SCHEDULE_RESOURCE_BOUND = 366

internal fun explicitScheduleEnd(first: Int, length: Int): Int? {
    if (first !in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND || length !in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND) return null
    if (first > EXPLICIT_SCHEDULE_RESOURCE_BOUND - length + 1) return null
    return first + length - 1
}

internal fun explicitSchedulePair(group: Int): IntRange? {
    if (group !in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND / 2) return null
    return group * 2 - 1..group * 2
}
