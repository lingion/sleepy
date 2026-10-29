@file:Suppress("unused")

package com.xiaomanjun.sleepdownschedule

// Re-export constants from model package at root package level
internal const val DefaultHomeChromeBlurScale = com.xiaomanjun.sleepdownschedule.model.DefaultHomeChromeBlurScale
internal const val MinHomeChromeBlurScale = com.xiaomanjun.sleepdownschedule.model.MinHomeChromeBlurScale
internal const val MaxHomeChromeBlurScale = com.xiaomanjun.sleepdownschedule.model.MaxHomeChromeBlurScale
internal const val DefaultHomeChromeSamplingScale = com.xiaomanjun.sleepdownschedule.model.DefaultHomeChromeSamplingScale
internal const val LiquidCourseCardBlurMax = com.xiaomanjun.sleepdownschedule.model.LiquidCourseCardBlurMax
internal const val SimpleCourseCardBlurMax = com.xiaomanjun.sleepdownschedule.model.SimpleCourseCardBlurMax

internal fun courseCardBlurMaximum(glassEnabled: Boolean): Float =
    com.xiaomanjun.sleepdownschedule.model.courseCardBlurMaximum(glassEnabled)

internal fun normalizedHomeChromeBlurScale(value: Float): Float =
    com.xiaomanjun.sleepdownschedule.model.normalizedHomeChromeBlurScale(value)

// Typealiases for entity types used across the codebase
typealias CourseEntity = com.xiaomanjun.sleepdownschedule.model.CourseEntity
typealias ScheduleConfigEntity = com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
typealias ScheduleProfileEntity = com.xiaomanjun.sleepdownschedule.model.ScheduleProfileEntity
typealias PeriodEntity = com.xiaomanjun.sleepdownschedule.model.PeriodEntity
typealias CourseCardColorMode = com.xiaomanjun.sleepdownschedule.model.CourseCardColorMode
typealias WeekParity = com.xiaomanjun.sleepdownschedule.model.WeekParity
typealias NotificationMode = com.xiaomanjun.sleepdownschedule.model.NotificationMode
typealias DefaultWallpaperStyle = com.xiaomanjun.sleepdownschedule.model.DefaultWallpaperStyle
typealias DockAlignment = com.xiaomanjun.sleepdownschedule.model.DockAlignment
typealias HomeStartMode = com.xiaomanjun.sleepdownschedule.model.HomeStartMode
typealias ScheduleTermState = com.xiaomanjun.sleepdownschedule.model.ScheduleTermState

// BuildConfig is provided by BuildConfig.kt stub in this package
