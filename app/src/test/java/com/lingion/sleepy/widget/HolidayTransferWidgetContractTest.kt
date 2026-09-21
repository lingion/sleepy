package com.lingion.sleepy.widget

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue#44 调休映射合约锁: widget / 通知必须用共享 helper,
 * 不能直接取 date.dayOfWeek.value 或 DateUtils.todayDayOfWeek。
 */
class HolidayTransferWidgetContractTest {

    private fun src(rel: String): String = sequenceOf(
        java.io.File("app/$rel"),
        java.io.File("/tmp/sleepy-makeup-wt/app/$rel"),
        java.io.File("/Users/lingion_k/sleepy/app/$rel")
    ).firstOrNull { it.isFile }?.readText() ?: error("Unable to load $rel")

    private val consumers = listOf(
        "src/main/java/com/lingion/sleepy/widget/TodayWidget.kt",
        "src/main/java/com/lingion/sleepy/widget/TwoDayWidget.kt",
        "src/main/java/com/lingion/sleepy/widget/WeekViewWidget.kt",
        "src/main/java/com/lingion/sleepy/widget/WeekListWidget.kt",
        "src/main/java/com/lingion/sleepy/widget/WeekGridWidgetProvider.kt",
        "src/main/java/com/lingion/sleepy/widget/WidgetRenderActivity.kt",
        "src/main/java/com/lingion/sleepy/widget/WidgetCompactWindow.kt",
        "src/main/java/com/lingion/sleepy/widget/notification/CourseNotificationScheduler.kt"
    )

    @Test
    fun all_consumers_use_shared_effectiveDayOfWeek() {
        consumers.forEach { rel ->
            val text = src(rel)
            assertTrue(
                "$rel must use HolidayTransferHelper.effectiveDayOfWeek (or VM courseDayFor)",
                text.contains("effectiveDayOfWeek")
            )
        }
    }
}
