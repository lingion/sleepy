package com.lingion.sleepy.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

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

    /** 第几节课的上课时间表 JSON: [{"node":1,"start":"08:00","end":"08:45"}, ...] */
    @ColumnInfo(name = "timeJson") val timeJson: String = DEFAULT_TIME_JSON,

    /** 颜色主题 */
    @ColumnInfo(name = "color") val color: String = "#FF6750A4",

    /** 是否为默认课表 */
    @ColumnInfo(name = "isDefault") val isDefault: Boolean = false,

    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** 默认节次时间表 (8 节制) */
        const val DEFAULT_TIME_JSON = """[
            {"node":1,"start":"08:00","end":"08:45"},
            {"node":2,"start":"08:55","end":"09:40"},
            {"node":3,"start":"10:00","end":"10:45"},
            {"node":4,"start":"10:55","end":"11:40"},
            {"node":5,"start":"14:00","end":"14:45"},
            {"node":6,"start":"14:55","end":"15:40"},
            {"node":7,"start":"16:00","end":"16:45"},
            {"node":8,"start":"16:55","end":"17:40"},
            {"node":9,"start":"19:00","end":"19:45"},
            {"node":10,"start":"19:55","end":"20:40"},
            {"node":11,"start":"20:50","end":"21:35"},
            {"node":12,"start":"21:45","end":"22:30"}
        ]"""
    }
}