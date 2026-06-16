package com.lingion.sleepy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lingion.sleepy.data.entity.TimeTableEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeTableDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(table: TimeTableEntity): Long

    @Update
    suspend fun update(table: TimeTableEntity)

    @Query("DELETE FROM time_tables WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM time_tables WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TimeTableEntity?

    @Query("SELECT * FROM time_tables WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<TimeTableEntity?>

    @Query("SELECT * FROM time_tables ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TimeTableEntity>>

    @Query("SELECT * FROM time_tables ORDER BY createdAt DESC")
    suspend fun getAll(): List<TimeTableEntity>

    @Query("SELECT COUNT(*) FROM time_tables")
    suspend fun count(): Int

    @Query("UPDATE time_tables SET isDefault = (id = :id)")
    suspend fun setDefault(id: Long)

    @Query("SELECT * FROM time_tables WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): TimeTableEntity?
}