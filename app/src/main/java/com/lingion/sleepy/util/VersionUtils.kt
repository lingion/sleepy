package com.lingion.sleepy.util

/** Semantic comparison for release names such as v1.2.3 and 1.2.3-debug. */
object VersionUtils {
    private fun parts(version: String): List<Int> =
        Regex("\\d+").findAll(version.removePrefix("v")).map { it.value.toInt() }.toList()
            .ifEmpty { listOf(0) }

    fun compare(left: String, right: String): Int {
        val a = parts(left)
        val b = parts(right)
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }
}
