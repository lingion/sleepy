@file:Suppress("unused")

package com.xiaomanjun.sleepdownschedule.model

const val MulticolorCourseCardArgb = 0x00000000L

val DefaultCourseCardPalette: List<Long> = listOf(
    0xFF64B5F6L,
    0xFFF48FB1L,
    0xFF81C784L,
    0xFFFFD166L,
    0xFFB39DDBL,
    0xFF4DD0E1L
)

fun encodeCourseCardPalette(colors: List<Long>): String =
    colors.joinToString(",") { it.toString() }

fun decodeCourseCardPalette(value: String): List<Long> =
    if (value.isBlank()) emptyList()
    else value.split(",").mapNotNull { it.trim().toLongOrNull() }

internal fun courseCardPerceptualDistance(first: Long, second: Long): Double = 0.0
