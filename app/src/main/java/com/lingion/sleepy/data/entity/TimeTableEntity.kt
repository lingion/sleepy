package com.lingion.sleepy.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lingion.sleepy.util.TimeTableUtils

/**
 * 课表实体 (TimeTable) — 一个课表包含多个课程
 */
@Entity(tableName = "time_tables")
data class TimeTableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    @ColumnInfo(name = "name") val name: String,

    /** 学期开始日期 (yyyy-MM-dd), 用于计算当前周次 */
    @ColumnInfo(name = "startDate") val startDate: String,

    /** 学期总周数 */
    @ColumnInfo(name = "maxWeek") val maxWeek: Int = 20,

    /** 一天的节次数 */
    @ColumnInfo(name = "nodesPerDay") val nodesPerDay: Int = 12,

    /**
     * 第几节课的上课时间表 JSON。
     * 默认值委托给 [TimeTableUtils.DEFAULT_TIME_JSON]，保持与 UI 渲染 / 解析器**单一来源**。
     */
    @ColumnInfo(name = "timeJson") val timeJson: String = TimeTableUtils.DEFAULT_TIME_JSON,

    /** 颜色主题 */
    @ColumnInfo(name = "color") val color: String = "#FF6750A4",

    /** 是否为默认课表 */
    @ColumnInfo(name = "isDefault") val isDefault: Boolean = false,

    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis()
)