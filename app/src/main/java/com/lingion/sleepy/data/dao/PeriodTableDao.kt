package com.lingion.sleepy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lingion.sleepy.data.entity.PeriodTableEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodTableDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(table: PeriodTableEntity): Long

    /** v7.10 撤回恢复用 — 整批重插 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tables: List<PeriodTableEntity>)

    @Update
    suspend fun update(table: PeriodTableEntity)

    @Query("DELETE FROM period_tables WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** v7.10 撤回恢复用 — 清空全表 */
    @Query("DELETE FROM period_tables")
    suspend fun deleteAll()

    @Query("SELECT * FROM period_tables WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PeriodTableEntity?

    @Query("SELECT * FROM period_tables WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<PeriodTableEntity?>

    @Query("SELECT * FROM period_tables ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PeriodTableEntity>>

    @Query("SELECT * FROM period_tables ORDER BY createdAt DESC")
    suspend fun getAll(): List<PeriodTableEntity>

    @Query("SELECT COUNT(*) FROM period_tables")
    suspend fun count(): Int

    /** 引用计数 — 管理页显示绑定数量。 */
    @Query("SELECT COUNT(*) FROM time_tables WHERE periodTableId = :id")
    suspend fun boundTableCount(id: Long): Int

    /** 引用此时间表的全部课程表 id。 */
    @Query("SELECT id FROM time_tables WHERE periodTableId = :id")
    suspend fun boundTableIds(id: Long): List<Long>
}
