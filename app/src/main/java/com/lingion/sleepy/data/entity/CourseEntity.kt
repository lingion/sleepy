package com.lingion.sleepy.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 课程实体 — 与 WakeUp 原版 CourseBean schema 兼容（type / day / startNode / step / startWeek / endWeek / color / tableId）
 * 但去掉了 ownTime/level/credit/note 等冗余字段，新加 note/teacher/room 用于显示。
 */
@Entity(
    tableName = "courses",
    indices = [Index("tableId"), Index("day"), Index("startWeek", "endWeek")],
    foreignKeys = [
        ForeignKey(
            entity = TimeTableEntity::class,
            parentColumns = ["id"],
            childColumns = ["tableId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** 所属课表 ID */
    @ColumnInfo(name = "tableId") val tableId: Long,

    /** 课程名 */
    @ColumnInfo(name = "courseName") val courseName: String,

    /** 教师 */
    @ColumnInfo(name = "teacher") val teacher: String = "",

    /** 教室 */
    @ColumnInfo(name = "room") val room: String = "",

    /** 备注 */
    @ColumnInfo(name = "note") val note: String = "",

    /** 周几 1-7 (周一=1) */
    @ColumnInfo(name = "day") val day: Int,

    /** 开始节次 (1-based, 1 = 第 1 节) */
    @ColumnInfo(name = "startNode") val startNode: Int,

    /** 持续节数 (例如 2 节连上) */
    @ColumnInfo(name = "step") val step: Int,

    /** 起始周 */
    @ColumnInfo(name = "startWeek") val startWeek: Int,

    /** 结束周 */
    @ColumnInfo(name = "endWeek") val endWeek: Int,

    /**
     * 周次类型: 0=每周, 1=单周, 2=双周
     */
    @ColumnInfo(name = "type") val type: Int = 0,

    /**
     * 颜色 (ARGB Hex, 例如 "#FF6750A4")
     */
    @ColumnInfo(name = "color") val color: String,

    /**
     * 是否自定义时间 (即 startTime/endTime 由用户设置而非系统)
     * 保留字段以兼容 WakeUp 旧 db
     */
    @ColumnInfo(name = "ownTime") val ownTime: Boolean = false,

    /** 自定义开始时间 (HH:mm), 仅 ownTime=true 时使用 */
    @ColumnInfo(name = "startTime") val startTime: String = "",

    /** 自定义结束时间 (HH:mm), 仅 ownTime=true 时使用 */
    @ColumnInfo(name = "endTime") val endTime: String = "",

    @ColumnInfo(name = "credit") val credit: Float = 0f,

    @ColumnInfo(name = "level") val level: Int = 0
) {
    /** 第 N 周是否上这门课 */
    fun inWeek(week: Int): Boolean {
        if (week < startWeek || week > endWeek) return false
        return when (type) {
            0 -> true  // 每周
            1 -> week % 2 == 1 || week == startWeek || week == endWeek  // 单周
            2 -> week % 2 == 0 || week == startWeek || week == endWeek  // 双周
            else -> true
        }
    }

    /** "第 3-4 节" 或 "18:30-20:55" */
    val nodeString: String
        get() = if (ownTime && startTime.isNotBlank() && endTime.isNotBlank()) {
            "$startTime-$endTime"
        } else {
            "第 $startNode - ${startNode + step - 1} 节"
        }

    /** "3-4节" 或 "18:30-20:55" */
    val shortNodeString: String
        get() = if (ownTime && startTime.isNotBlank() && endTime.isNotBlank()) {
            "$startTime-$endTime"
        } else {
            "$startNode-${startNode + step - 1}节"
        }
}