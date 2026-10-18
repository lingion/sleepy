package com.lingion.sleepy.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * 课程持久色相表（spec docs/spec-course-color-harmony-2026-10-18.md §3.1）。
 *
 * 颜色绑定 groupId（课程身份标识），跨导入/改名稳定 —— 重导入经认亲继承
 * 旧 groupId 即命中旧 hue；改名同 row 同 gid 不动。
 *
 * tableKey 现阶段恒为 GLOBAL_TABLE_KEY：hue 跟课组走、与课表无关；
 * 主键保留该维度，未来「每课表独立配色」直接启用，不动 schema。
 *
 * 写路径仅 ViewModel/导入层（spec §2.3），渲染层纯读；CLASSIC 档不读本表。
 */
@Entity(tableName = "course_colors", primaryKeys = ["tableKey", "groupId"])
data class CourseColorEntity(
    @ColumnInfo(name = "tableKey") val tableKey: String = GLOBAL_TABLE_KEY,
    @ColumnInfo(name = "groupId") val groupId: String,
    @ColumnInfo(name = "hue") val hue: Float,
) {
    companion object {
        const val GLOBAL_TABLE_KEY = "__global__"
    }
}
