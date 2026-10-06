package com.lingion.sleepy.widget.notification

/**
 * The single course-card state shared by every Android vendor renderer.
 * Vendor adapters may change presentation, never the lifecycle semantics.
 */
data class CourseLiveCardState(
    val courseName: String,
    val room: String,
    val teacher: String,
    val startTime: String,
    val notifyEpoch: Long,
    val classEpoch: Long,
    val nowEpoch: Long,
    val updateSequence: Int = 0,
    val endTime: String = "",
    val startNode: Int = 0
) {
    val progress: Int
        get() = progressPercent(notifyEpoch, classEpoch, nowEpoch)

    val isActive: Boolean
        get() = nowEpoch < classEpoch

    val primaryText: String
        get() = courseName

    val detailText: String
        get() = buildList {
            if (startTime.isNotBlank()) add(startTime)
            if (room.isNotBlank()) add(room)
            if (teacher.isNotBlank()) add(teacher)
        }.joinToString("  ·  ")

    /** 整分钟倒计时(向上取整, 至少 1)——ColorOS 流体云胶囊/卡片右侧文案用。 */
    val minutesLeft: Int
        get() = (((classEpoch - nowEpoch) + 59_999L) / 60_000L).toInt().coerceAtLeast(1)

    /** "HH:mm - HH:mm"(有结束时间时)或 "HH:mm"。 */
    val timeRangeText: String
        get() = if (endTime.isNotBlank() && endTime != startTime) "$startTime - $endTime" else startTime

    /** ColorOS 流体云正文: "14:00 - 15:50 · 实训楼307 · 叶晟"。 */
    val colorOsDetailText: String
        get() = buildList {
            if (timeRangeText.isNotBlank()) add(timeRangeText)
            if (room.isNotBlank()) add(room)
            if (teacher.isNotBlank()) add(teacher)
        }.joinToString(" · ")
}

internal fun progressPercent(notifyEpoch: Long, classEpoch: Long, nowEpoch: Long): Int {
    val total = (classEpoch - notifyEpoch).coerceAtLeast(1L)
    val elapsed = (nowEpoch - notifyEpoch).coerceIn(0L, total)
    return ((elapsed * 100L) / total).toInt().coerceIn(0, 100)
}
