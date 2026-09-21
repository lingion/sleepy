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

    /**
     * v1.0.16 自动模式（智慧节次）配置 JSON。空串表示该表走手动模式（timeJson）。
     */
    @ColumnInfo(name = "smartConfigJson") val smartConfigJson: String = "",

    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis(),

    /**
     * issue#40 绑定的独立时间节次表 (period_tables.id)。null = 未绑定(用旧 timeJson 兼容列)。
     * 课程表 → 时间节次表的单向引用; 一张时间节次表可被多张课程表引用。
     * 修改所绑时间表 = 本表立即按新作息解释节次; 课程行 startNode/step 不重算。
     */
    @ColumnInfo(name = "periodTableId") val periodTableId: Long? = null,

    /**
     * C2(2026-09-20): 换绑前本表自身作息快照 — 绑定时的 timeJson/nodesPerDay/smartConfigJson
     * 三元组 JSON 包络({t,n,s}), 解绑时恢复此快照再清空。
     * 修复 issue#40 用户数据破坏: 旧实现换绑时把目标表内容镜像覆盖本表兼容列,
     * 解绑后"使用本表作息"读到的是镜像副本, 用户手工作息永久丢失。
     * 快照仅当 periodTableId 由 null → 非 null 时写入一次; 绑→绑换目标不刷新快照。
     */
    @ColumnInfo(name = "preBindSnapshotJson", defaultValue = "") val preBindSnapshotJson: String = ""
) {
    /**
     * issue#40 有效时间表水合(设计 §5.1): 绑定存在 → 节次时间/智慧节次/节次数
     * 全部以所绑 periodTable 为准覆盖本表对应字段(兼容列保留不动, 仅作回退);
     * 未绑定或传入 null → 原样返回(读旧兼容列, 行为与升级前一致)。
     *
     * 渲染/通知/widget 路径统一经此函数取"节次→时间"的真源;
     * 课程行按 startNode/step 节次绑定, 不因水合而变。
     */
    fun hydratedWith(periodTable: PeriodTableEntity?): TimeTableEntity =
        if (periodTable == null) this else copy(
            nodesPerDay = periodTable.nodesPerDay,
            timeJson = periodTable.timeJson,
            smartConfigJson = periodTable.smartConfigJson
        )

    companion object {
        /**
         * C2: 首次绑定(null → 非 null)时产出的新实体 — 兼容列不变,
         * 只写 periodTableId 并把绑定前的 {t,n,s} 三元组存进快照列。
         */
        fun snapshotForBind(table: TimeTableEntity, targetId: Long): TimeTableEntity =
            table.copy(
                periodTableId = targetId,
                preBindSnapshotJson = org.json.JSONObject()
                    .put("t", table.timeJson)
                    .put("n", table.nodesPerDay)
                    .put("s", table.smartConfigJson)
                    .toString()
            )

        /**
         * C2: 解绑(非 null → null)时的新实体 — 快照存在则恢复绑定前兼容列再清空;
         * 快照缺失(空/损坏, 只可能来自旧版本升级/历史脏数据)按原样解绑,
         * 兼容列保持绑定期间最后一次已知值(旧行为)。
         */
        fun restoredForUnbind(table: TimeTableEntity): TimeTableEntity {
            val snapshot = if (table.preBindSnapshotJson.isBlank()) null
                else runCatching { org.json.JSONObject(table.preBindSnapshotJson) }.getOrNull()
            if (snapshot == null) return table.copy(periodTableId = null, preBindSnapshotJson = "")
            return table.copy(
                periodTableId = null,
                nodesPerDay = snapshot.optInt("n", table.nodesPerDay),
                timeJson = snapshot.optString("t", table.timeJson),
                smartConfigJson = snapshot.optString("s", table.smartConfigJson),
                preBindSnapshotJson = ""
            )
        }
    }
}