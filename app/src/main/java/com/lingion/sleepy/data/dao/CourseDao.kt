package com.lingion.sleepy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lingion.sleepy.data.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(course: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(courses: List<CourseEntity>): List<Long>

    @Update
    suspend fun update(course: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM courses WHERE tableId = :tableId")
    suspend fun deleteByTableId(tableId: Long)

    @Query("DELETE FROM courses WHERE tableId = :tableId AND groupId = :groupId")
    suspend fun deleteByGroupId(tableId: Long, groupId: String)

    /** v7.10.16 撤回恢复用 — 清空全表 */
    @Query("DELETE FROM courses")
    suspend fun deleteAll()

    /** v7.10.16 撤回快照用 — 全库课程 */
    @Query("SELECT * FROM courses")
    suspend fun getAll(): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CourseEntity?

    @Query("SELECT * FROM courses WHERE tableId = :tableId ORDER BY day, startNode, startWeek")
    fun observeByTable(tableId: Long): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE tableId = :tableId AND day = :day ORDER BY startNode")
    fun observeByTableAndDay(tableId: Long, day: Int): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE tableId = :tableId AND day = :day ORDER BY startNode")
    suspend fun getByTableAndDayOnce(tableId: Long, day: Int): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE tableId = :tableId ORDER BY day, startNode, startWeek")
    suspend fun getByTable(tableId: Long): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE tableId = :tableId AND groupId = :groupId")
    suspend fun getByGroupId(tableId: Long, groupId: String): List<CourseEntity>

    @Query("SELECT COUNT(*) FROM courses WHERE tableId = :tableId")
    suspend fun countByTable(tableId: Long): Int

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun totalCount(): Int

    /** 整表导入（覆盖式，原子事务） */
    @Transaction
    suspend fun replaceAll(tableId: Long, courses: List<CourseEntity>) {
        deleteByTableId(tableId)
        insertAll(courses)
    }

    /** 编辑课程组：删除同 groupId 全部记录，再插入新记录（原子事务） */
    @Transaction
    suspend fun replaceGroup(tableId: Long, groupId: String, newCourses: List<CourseEntity>) {
        deleteByGroupId(tableId, groupId)
        insertAll(newCourses)
    }
}
