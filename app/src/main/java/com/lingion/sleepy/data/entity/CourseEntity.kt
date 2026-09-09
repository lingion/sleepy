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

    /** 课程组 ID — 同一门课的所有节次共享，编辑/删除时按此操作 */
    @ColumnInfo(name = "groupId") val groupId: String,

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

    /**
     * issue#26 课程别名 — 可选;空串 = 处处显示原名。
     * 组级属性: 同 groupId 所有行共享同一别名(编辑页保存时整组覆盖)。
     * 仅"展示"场景(周视图/网格视图/小组件)按各自设置取用;
     * 详情页/导入预览/通知/导出等"身份"场景一律原名。
     */
    @ColumnInfo(name = "alias", defaultValue = "") val alias: String = "",

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
     * 周次类型: 0=每周, 1=单周(第1/3/5周), 2=双周(第2/4/6周), 3=按周次列实际指定的周
     * (用于"只在某一两周上"的单次实验课; 解析器在类型列缺失/不明时默认填 3 而非 0,
     * 避免把"周次=6 单次实验"误标成"每周都上")
     */
    @ColumnInfo(name = "type") val type: Int = 0,

    /**
     * 颜色 (ARGB Hex, 例如 "#FF6750A4")
     *
     * 字段语义随 colorMode 而异:
     *   - colorMode = GROUP  : 整门课程的"组色",所有节次共享(原行为)
     *   - colorMode = AUTO   : 本字段无意义;渲染时按 golden angle 137.508° 重算
     *   - colorMode = CUSTOM : 本字段存用户选定的十六进制
     */
    @ColumnInfo(name = "color") val color: String,

    /**
     * 颜色模式 (issue#22 同名课程多地点修复新增):
     *   0 = GROUP  — 跟随整门课程组色 (默认,旧数据全部走这个)
     *   1 = AUTO   — 自动色,渲染时按 row 自身 id 相对同组行序号实时算
     *   2 = CUSTOM — 自定义色,color 字段存十六进制
     *
     * 详见 docs/superpowers/specs/2026-09-07-sleepy-issue-22-same-name-multi-location-design.md §5.1
     */
    @ColumnInfo(name = "colorMode", defaultValue = "0") val colorMode: Int = 0,

    /**
     * 是否自定义时间 (即 startTime/endTime 由用户设置而非系统)
     * 保留字段以兼容 WakeUp 旧 db; 与 [isIrregularTime] 永远同值 (§5 同步契约)
     */
    @ColumnInfo(name = "ownTime") val ownTime: Boolean = false,

    /**
     * issue#23 逐卡重构: 本卡片是否勾选「非常规节次」— 绑定边缘节次槽位 (0/-1/N+1...)。
     * timeJson 为真理源 (编号本身可推导), 此标志为行级回写, 保存时按实际编号回填, 防漂移。
     */
    @ColumnInfo(name = "isIrregularNode", defaultValue = "0") val isIrregularNode: Boolean = false,

    /**
     * issue#23 逐卡重构: 本卡片是否勾选「非常规时间」— startTime/endTime 为该卡覆盖值,
     * 不受槽位默认时间窗口约束 (§2.3)。
     */
    @ColumnInfo(name = "isIrregularTime", defaultValue = "0") val isIrregularTime: Boolean = false,

    /** 自定义开始时间 (HH:mm), 仅 ownTime/isIrregularTime=true 时使用 */
    @ColumnInfo(name = "startTime") val startTime: String = "",

    /** 自定义结束时间 (HH:mm), 仅 ownTime/isIrregularTime=true 时使用 */
    @ColumnInfo(name = "endTime") val endTime: String = "",

    @ColumnInfo(name = "credit") val credit: Float = 0f,

    @ColumnInfo(name = "level") val level: Int = 0
) {
    /** 第 N 周是否上这门课 */
    fun inWeek(week: Int): Boolean {
        if (week < startWeek || week > endWeek) return false
        return when (type) {
            0 -> true  // 每周
            1 -> week % 2 == 1  // 单周
            2 -> week % 2 == 0  // 双周
            3 -> true  // 按周次列实际指定的周; 上面已限定 [startWeek, endWeek] 区间
            else -> true  // 防御性: 旧数据可能存了非法 type, 不丢失
        }
    }

    /** "第 3-4 节" 或 "18:30-20:55" — 本地化版本（推荐 UI 使用） */
    fun nodeString(context: android.content.Context): String =
        if (ownTime && startTime.isNotBlank() && endTime.isNotBlank()) {
            "$startTime-$endTime"
        } else {
            context.getString(
                com.lingion.sleepy.R.string.course_node_format,
                "$startNode-${startNode + step - 1}"
            )
        }

    /** "3-4节" 或 "18:30-20:55" — 本地化版本（推荐 UI 使用） */
    fun shortNodeString(context: android.content.Context): String =
        if (ownTime && startTime.isNotBlank() && endTime.isNotBlank()) {
            "$startTime-$endTime"
        } else {
            context.getString(
                com.lingion.sleepy.R.string.course_period_range,
                startNode,
                startNode + step - 1
            )
        }

    /**
     * 渲染前归一化：ownTime=true 的课根据时间表反算等效 startNode/step，
     * 使其在网格中正确定位。ownTime=false 的课原样返回。
     */
    fun normalizeNode(timeJson: String): CourseEntity {
        // issue#23: 边缘槽位卡的网格位置 = 槽位编号本身, 禁止按时间重映射
        if (isIrregularNode) return this
        if (!ownTime || startTime.isBlank() || endTime.isBlank()) return this
        val mapped = com.lingion.sleepy.util.TimeTableUtils.timeToNode(startTime, endTime, timeJson)
            ?: return this
        return copy(startNode = mapped.first, step = mapped.second)
    }
}

/** 颜色模式常量(数据库存整数,业务层用枚举语义引用,免去 magic number) */
object CourseColorMode {
    const val GROUP = 0
    const val AUTO = 1
    const val CUSTOM = 2
}