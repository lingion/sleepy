package com.lingion.sleepy.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.util.AppClock

/**
 * 独立时间节次表 (issue#40) — "第 N 节是几点到几点"的真源, 与课程表(TimeTable)平行的层级。
 *
 * 一张时间节次表可被零张或多张课程表引用 (TimeTable.periodTableId 单向指向本表);
 * 修改本表 = 所有引用它的课程表立即按新作息解释节次。课程行按 startNode/step
 * 节次绑定, 不因本表修改而重算 (2026-09-15 用户拍板)。
 *
 * timeJson/smartConfigJson 沿用 TimeTableUtils 的 JSON 形状, 单一解析来源。
 */
@Entity(tableName = "period_tables", indices = [Index("createdAt")])
data class PeriodTableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** 用户可见名称, 如"春季作息" */
    @ColumnInfo(name = "name") val name: String,

    /** 一天的节次数 */
    @ColumnInfo(name = "nodesPerDay") val nodesPerDay: Int = 12,

    /**
     * 第几节课的上课时间表 JSON。默认值委托给 [TimeTableUtils.DEFAULT_TIME_JSON]，
     * 与 UI 渲染 / 解析器保持**单一来源**。
     */
    @ColumnInfo(name = "timeJson") val timeJson: String = TimeTableUtils.DEFAULT_TIME_JSON,

    /** 智慧节次配置 JSON。空串表示手动模式（timeJson）。 */
    @ColumnInfo(name = "smartConfigJson") val smartConfigJson: String = "",

    @ColumnInfo(name = "createdAt") val createdAt: Long = AppClock.millis(),

    @ColumnInfo(name = "updatedAt") val updatedAt: Long = AppClock.millis()
)
