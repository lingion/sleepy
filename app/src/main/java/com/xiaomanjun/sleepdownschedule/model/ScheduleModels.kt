@file:Suppress("unused")

package com.xiaomanjun.sleepdownschedule.model

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

// ── Enums ────────────────────────────────────────────────────────────────────

enum class WeekParity { ALL, ODD, EVEN }
enum class NotificationMode { STANDARD, LIVE_UPDATE }
enum class DefaultWallpaperStyle { KANBAN, NONE }
enum class DockAlignment { LEFT, CENTER, RIGHT }
enum class HomeStartMode { DAY, TWO_DAY, WEEK }
enum class LiveUpdateChipTextMode { LOCATION, COUNTDOWN, SHORT, NORMAL }
enum class PeriodSchemeMode { MANUAL, AUTO_MATCH }
enum class PeriodAlignmentMode { INDEX, TIME }
enum class ScheduleTermState { MANUAL, UPCOMING, ACTIVE, ENDED, INVALID }
enum class CourseCardColorMode { SOLID, GRADIENT, COLORFUL }
enum class WeekCardTextAlignment { START, CENTER, END }
enum class WeekCardContentLayout { CURRENT, CENTERED, TOP_DOWN, TOP, MIDDLE }

// ── Constants ────────────────────────────────────────────────────────────────

internal const val DefaultHomeChromeBlurScale = 1f
internal const val MinHomeChromeBlurScale = 0f
internal const val MaxHomeChromeBlurScale = 8f
internal const val DefaultHomeChromeSamplingScale = 1f
internal const val LiquidCourseCardBlurMax = 10f
internal const val SimpleCourseCardBlurMax = 24f

// ── Top-level functions ──────────────────────────────────────────────────────

internal fun courseCardBlurMaximum(glassEnabled: Boolean): Float =
    if (glassEnabled) LiquidCourseCardBlurMax else SimpleCourseCardBlurMax

internal fun normalizedHomeChromeBlurScale(value: Float): Float =
    value.coerceIn(MinHomeChromeBlurScale, MaxHomeChromeBlurScale)

fun defaultConfig(id: Int = 1): ScheduleConfigEntity = ScheduleConfigEntity(
    id = id,
    totalWeeks = 20,
    currentWeek = 1,
    notificationLeadMinutes = 15
)

fun defaultPeriods(scheduleId: Int = 1): List<PeriodEntity> = emptyList()

// ── Entities ─────────────────────────────────────────────────────────────────

@Immutable
data class CourseEntity @Ignore constructor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val teacher: String? = null,
    val location: String? = null,
    val weekday: Int = 1,
    val periods: List<Int> = emptyList(),
    val weeks: List<Int> = emptyList(),
    val weekParity: WeekParity = WeekParity.ALL,
    val note: String? = null,
    val customStartTime: String? = null,
    val customEndTime: String? = null,
    val customColorArgb: Long? = null,
    @ColumnInfo(defaultValue = "1") val scheduleId: Int = 1,
    val customPeriodTimes: String? = null,
    val originalPeriodTimes: String? = null,
    @Ignore val arrangementProjection: Any? = null,
    val independentPeriodSchemeId: Long? = null,
    val credit: Float? = null,
    val timingReviewReason: String? = null,
    val legacyTimingSourceJson: String? = null,
    val sourcePaletteIndex: Int? = null
)

@Entity(tableName = "schedule_profiles")
@Immutable
data class ScheduleProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    val isActive: Boolean = false
)

@Entity(tableName = "schedule_config")
@Immutable
data class ScheduleConfigEntity(
    @PrimaryKey val id: Int = 1,
    val totalWeeks: Int = 20,
    val currentWeek: Int = 1,
    val notificationLeadMinutes: Int = 15,
    val termStartDate: String? = null,
    val autoCurrentWeek: Boolean = true,
    @ColumnInfo(defaultValue = "'MANUAL'") val termState: ScheduleTermState = ScheduleTermState.MANUAL,
    val notificationsEnabled: Boolean = true,
    val notificationMode: NotificationMode = NotificationMode.STANDARD,
    val wallpaperUri: String? = null,
    val wallpaperBlur: Float = 0f,
    val wallpaperBrightness: Float = 1f,
    val wallpaperPortraitCenterX: Float? = 0.5f,
    val wallpaperPortraitCenterY: Float? = 0.5f,
    val wallpaperPortraitScale: Float? = 1f,
    val wallpaperLandscapeCenterX: Float? = 0.5f,
    val wallpaperLandscapeCenterY: Float? = 0.5f,
    val wallpaperLandscapeScale: Float? = 1f,
    val wallpaperSourceWidth: Int? = null,
    val wallpaperSourceHeight: Int? = null,
    val cardColorArgb: Long = 0xFFD6E9FF,
    val cardAlpha: Float = 1f,
    val courseCardBlur: Float = 18f,
    val courseCardGlassEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "1") val courseCardOutlineLightEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "0.5") val courseCardRefractionStrength: Float = 0.5f,
    @ColumnInfo(defaultValue = "1") val courseCardGaussianBlurEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "0") val courseCardColoredTextEnabled: Boolean = false,
    val courseCardFontScale: Float = 1f,
    @ColumnInfo(defaultValue = "'SOLID'") val courseCardColorMode: CourseCardColorMode = CourseCardColorMode.SOLID,
    @ColumnInfo(defaultValue = "''") val courseCardPalette: String = "",
    @ColumnInfo(defaultValue = "4293516543") val alternateCardColorArgb: Long = 0xFFD6E9FF,
    @ColumnInfo(defaultValue = "1") val alternateCardAlpha: Float = 1f,
    @ColumnInfo(defaultValue = "18") val alternateCourseCardBlur: Float = 18f,
    @ColumnInfo(defaultValue = "1") val alternateCourseCardFontScale: Float = 1f,
    @ColumnInfo(defaultValue = "'SOLID'") val alternateCourseCardColorMode: CourseCardColorMode = CourseCardColorMode.SOLID,
    @ColumnInfo(defaultValue = "''") val alternateCourseCardPalette: String = "",
    val weekCardHeightDp: Float? = null,
    @ColumnInfo(defaultValue = "1") val weekCardHeightScale: Float = 1f,
    @ColumnInfo(defaultValue = "0.5") val weekCardCornerProgress: Float = 0.5f,
    @ColumnInfo(defaultValue = "1") val weekCardShowLocation: Boolean = true,
    @ColumnInfo(defaultValue = "1") val weekCardShowTeacher: Boolean = true,
    @ColumnInfo(defaultValue = "'CENTER'") val weekCardTextAlignment: WeekCardTextAlignment = WeekCardTextAlignment.CENTER,
    @ColumnInfo(defaultValue = "'CURRENT'") val weekCardContentLayout: WeekCardContentLayout = WeekCardContentLayout.CURRENT,
    val homeTextLight: Boolean = false,
    @ColumnInfo(defaultValue = "1") val homeChromeBlurScale: Float = DefaultHomeChromeBlurScale,
    @ColumnInfo(defaultValue = "1") val homeChromeSamplingScale: Float = DefaultHomeChromeSamplingScale,
    val followSystemDarkMode: Boolean = true,
    val darkMode: Boolean = false,
    val defaultWallpaperStyle: DefaultWallpaperStyle = DefaultWallpaperStyle.NONE,
    val hideEmptyWeekends: Boolean = false,
    @ColumnInfo(defaultValue = "0") val showNonCurrentWeekCourses: Boolean = false,
    @ColumnInfo(defaultValue = "0") val weekStartsOnSunday: Boolean = false,
    @ColumnInfo(defaultValue = "''") val scheduleAdjustmentsJson: String = "",
    val dockAlignment: DockAlignment = DockAlignment.CENTER,
    val defaultHomeMode: HomeStartMode = HomeStartMode.WEEK,
    val liveUpdateActionsEnabled: Boolean = true,
    val liveUpdateChipTextMode: LiveUpdateChipTextMode = LiveUpdateChipTextMode.LOCATION,
    val classDurationMinutes: Int = 45,
    val breakDurationMinutes: Int = 10,
    @ColumnInfo(defaultValue = "0") val morningPeriodCount: Int = 4,
    @ColumnInfo(defaultValue = "0") val noonPeriodCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val afternoonPeriodCount: Int = 4,
    @ColumnInfo(defaultValue = "0") val eveningPeriodCount: Int = 4,
    val hideFromRecents: Boolean = false,
    val autoCheckUpdates: Boolean = true,
    @ColumnInfo(defaultValue = "NULL") val activePeriodSchemeId: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val activeTimetableComboId: Long? = null,
    @ColumnInfo(defaultValue = "'INDEX'") val periodAlignmentMode: PeriodAlignmentMode = PeriodAlignmentMode.INDEX,
    @ColumnInfo(defaultValue = "''") val timeSlotCoursesJson: String = ""
)

@Immutable
data class PeriodEntity(
    val periodIndex: Int = 0,
    val startTime: String = "08:00",
    val endTime: String = "08:45",
    val scheduleId: Int = 1,
    val alias: String? = null
)

@Entity(tableName = "period_schemes")
@Immutable
data class PeriodSchemeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Int = 1,
    val name: String = "",
    val mode: PeriodSchemeMode = PeriodSchemeMode.MANUAL,
    val isActive: Boolean = false,
    val classDurationMinutes: Int = 45,
    val breakDurationMinutes: Int = 10,
    val morningStartTime: String = "08:00",
    val noonStartTime: String = "12:00",
    val afternoonStartTime: String = "14:00",
    val eveningStartTime: String = "19:00",
    val specialBreaksJson: String = "{}",
    val overridesJson: String = "{}",
    @ColumnInfo(defaultValue = "0") val morningPeriodCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val noonPeriodCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val afternoonPeriodCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val eveningPeriodCount: Int = 0,
    @ColumnInfo(defaultValue = "''") val publicId: String = "",
    @ColumnInfo(defaultValue = "''") val sourceScheduleName: String = ""
)

@Entity(tableName = "period_scheme_times", primaryKeys = ["schemeId", "periodIndex"])
@Immutable
data class PeriodSchemeTimeEntity(
    val schemeId: Long = 0,
    val periodIndex: Int = 0,
    val startTime: String = "08:00",
    val endTime: String = "08:45",
    @ColumnInfo(defaultValue = "NULL") val alias: String? = null
)

fun ScheduleConfigEntity.effectiveWeekCardTextAlignment(): WeekCardTextAlignment =
    weekCardTextAlignment
