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

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CourseEntity?

    @Query("SELECT * FROM courses WHERE tableId = :tableId ORDER BY day, startNode, startWeek")
    fun observeByTable(tableId: Long): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE tableId = :tableId AND day = :day ORDER BY startNode")
    fun observeByTableAndDay(tableId: Long, day: Int): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE tableId = :tableId ORDER BY day, startNode, startWeek")
    suspend fun getByTable(tableId: Long): List<CourseEntity>

    @Query("SELECT COUNT(*) FROM courses WHERE tableId = :tableId")
    suspend fun countByTable(tableId: Long): Int

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun totalCount(): Int

    /** 整表导入（覆盖式） */
    @Transaction
    suspend fun replaceAll(tableId: Long, courses: List<CourseEntity>) {
        deleteByTableId(tableId)
        insertAll(courses)
    }
}