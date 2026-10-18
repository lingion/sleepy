package com.lingion.sleepy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingion.sleepy.data.entity.CourseColorEntity

/**
 * 课程持久色相 DAO（spec §4）。渲染路径只读；写仅发生在 ViewModel/导入层。
 */
@Dao
interface CourseColorDao {

    /** 全表重算落库（模式切换/主题变化/种子变化/手动重排）— 同 (tableKey,groupId) 覆盖。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<CourseColorEntity>)

    /** 单课补色（导入新课/单课新增）— 幂等 REPLACE。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: CourseColorEntity)

    /** 渲染取色: groupId → hue 全量映射（课表页一次加载）。 */
    @Query("SELECT groupId, hue FROM course_colors WHERE tableKey = :tableKey")
    suspend fun getHueMap(
        tableKey: String
    ): Map<@MapColumn(columnName = "groupId") String, @MapColumn(columnName = "hue") Float>

    /** 渲染兜底查单值: 查无返回 null → 调用方按 safeArcHue 实时算（与表将含值一致）。 */
    @Query("SELECT hue FROM course_colors WHERE tableKey = :tableKey AND groupId = :groupId")
    suspend fun getHue(tableKey: String, groupId: String): Float?

    /** 课表删除时清理其全局配色行（与 courses 删除同事务，防孤儿行）。 */
    @Query("DELETE FROM course_colors WHERE tableKey = :tableKey")
    suspend fun clear(tableKey: String)
}
