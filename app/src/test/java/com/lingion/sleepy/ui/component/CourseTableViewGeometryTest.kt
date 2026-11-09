package com.lingion.sleepy.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseTableViewGeometryTest {

    @Test
    fun prefixSum_matches_loop_accumulation_for_all_fractions_and_breaks() {
        val weights = listOf(1f, 1f, 1f, 1f, 0.5f, 1f, 1f, 1f, 0.2f, 1f, 1f, 1f)
        val breaks = setOf(3, 7) // 4节后、8节后有大课间/餐休

        // 原循环算法实现
        fun legacyNaturalSpan(r: Float): Float {
            val full = r.toInt().coerceAtMost(weights.size)
            var acc = 0f
            for (i in 0 until full) acc += weights[i]
            if (full < weights.size && r > full) acc += weights[full] * (r - full)
            return acc
        }

        fun legacyYOfRows(r: Float): Pair<Float, Int> {
            val span = legacyNaturalSpan(r)
            val full = r.toInt().coerceAtMost(weights.size)
            val crossed = breaks.count { it + 1 <= full }
            return span to crossed
        }

        // 新前缀和算法实现
        val naturalWeightPrefixSum = FloatArray(weights.size + 1).apply {
            var sum = 0f
            for (i in weights.indices) {
                sum += weights[i]
                this[i + 1] = sum
            }
        }
        val mealBreakPrefixCount = IntArray(weights.size + 1).apply {
            var count = 0
            for (i in 1..weights.size) {
                if ((i - 1) in breaks) count++
                this[i] = count
            }
        }

        fun optimizedNaturalSpan(r: Float): Float {
            val full = r.toInt().coerceAtMost(weights.size)
            var acc = naturalWeightPrefixSum[full]
            if (full < weights.size && r > full) {
                acc += weights[full] * (r - full)
            }
            return acc
        }

        fun optimizedYOfRows(r: Float): Pair<Float, Int> {
            val full = r.toInt().coerceAtMost(weights.size)
            val span = optimizedNaturalSpan(r)
            val crossed = mealBreakPrefixCount[full]
            return span to crossed
        }

        val testInputs = listOf(
            0.0f, 0.1f, 0.5f, 1.0f, 3.0f, 3.5f, 4.0f, 4.2f, 7.0f, 7.9f, 8.0f, 8.1f, 11.0f, 12.0f, 15.0f
        )

        for (input in testInputs) {
            val (legacySpan, legacyBreaks) = legacyYOfRows(input)
            val (optSpan, optBreaks) = optimizedYOfRows(input)

            assertEquals("r=$input 处的累计跨度不匹配", legacySpan, optSpan, 1e-5f)
            assertEquals("r=$input 处的餐段计数不匹配", legacyBreaks, optBreaks)
        }
    }
}
