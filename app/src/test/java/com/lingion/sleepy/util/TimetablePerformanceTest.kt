package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.system.measureNanoTime

/** 固定工作量的本地对照基准，不把机器时延阈值当正确性断言。 */
class TimetablePerformanceTest {
    @Test fun pairedParsingBaselineAndCacheHit() {
        // 同一 JVM、同一输入交替测量；两边均反射调用，避免调用开销不对称。
        // 私有未缓存实现就是缓存前的解析主体，仅用于本地测量。
        for (name in listOf("parseNodes", "parseTimeSlotRows")) {
            val cached = TimeTableUtils::class.java.declaredMethods.single {
                it.name.substringBefore('$') == name && it.parameterCount == 1 &&
                    java.lang.reflect.Modifier.isPublic(it.modifiers)
            }.apply { isAccessible = true }
            val uncached = TimeTableUtils::class.java.getDeclaredMethod(name + "Uncached", String::class.java)
                .apply { isAccessible = true }
            val json = TimeTableUtils.DEFAULT_TIME_JSON
            fun batch(method: java.lang.reflect.Method) {
                var count = 0
                repeat(2_000) { count += (method.invoke(TimeTableUtils, json) as List<*>).size }
                assertEquals(24_000, count)
            }
            repeat(10) { batch(uncached); batch(cached) }
            val before = mutableListOf<Double>()
            val after = mutableListOf<Double>()
            repeat(9) { index ->
                val order = if (index % 2 == 0) listOf(uncached, cached) else listOf(cached, uncached)
                order.forEach { method ->
                    val elapsed = measureNanoTime { batch(method) } / 1_000_000.0
                    (if (method == uncached) before else after).add(elapsed)
                }
            }
            println("PAIRED $name 2000 parses uncached=$before cached=$after medians=${before.sorted()[4]}/${after.sorted()[4]} ms")
        }
    }

    @Test fun repeatedEffectiveTimesForWidgetsAndReminders() {
        fun batch(): Int {
            var checksum = 0
            repeat(2_000) {
                checksum += TimeTableUtils.effectiveCourseTime(false, "", "", 1, 2,
                    TimeTableUtils.DEFAULT_TIME_JSON)!!.second.length
            }
            return checksum
        }
        repeat(3) { assertEquals(10_000, batch()) }
        val samples = List(7) { measureNanoTime { assertEquals(10_000, batch()) } / 1_000_000.0 }
        println("PERF effective 2000 lookups ms=$samples median=${samples.sorted()[3]}")
    }

    @Test fun repeatedTimetableLookups() {
        val json = TimeTableUtils.DEFAULT_TIME_JSON
        fun batch(): Long {
            var checksum = 0L
            repeat(2_000) {
                val node = TimeTableUtils.timeToNode("08:10", "09:30", json)!!
                checksum += node.first + node.second
                checksum += TimeTableUtils.courseTimeParts(1, 2, json)!!.second.length
            }
            return checksum
        }
        repeat(3) { assertEquals(16_000L, batch()) }
        val samples = List(7) { measureNanoTime { assertEquals(16_000L, batch()) } / 1_000_000.0 }
        println("PERF timetable 4000 lookups ms=$samples median=${samples.sorted()[3]}")
    }
}
