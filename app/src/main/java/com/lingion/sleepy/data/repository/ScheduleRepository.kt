package com.lingion.sleepy.data.repository

import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow

/**
 * 课表仓库 — 业务数据访问的唯一入口。
 *
 * UI 层只调这个类，不直接碰 DAO。
 */
class ScheduleRepository(private val db: AppDatabase) {

    private val courseDao = db.courseDao()
    private val tableDao = db.timeTableDao()

    // ========== TimeTable ==========

    fun observeAllTables(): Flow<List<TimeTableEntity>> = tableDao.observeAll()

    fun observeTable(id: Long): Flow<TimeTableEntity?> = tableDao.observeById(id)

    suspend fun getAllTables(): List<TimeTableEntity> = tableDao.getAll()

    suspend fun getTable(id: Long): TimeTableEntity? = tableDao.getById(id)

    suspend fun getDefaultTable(): TimeTableEntity? = tableDao.getDefault()

    suspend fun insertTable(table: TimeTableEntity): Long {
        val id = tableDao.insert(table)
        if (table.isDefault || tableDao.count() == 1) {
            tableDao.setDefault(id)
        }
        return id
    }

    suspend fun updateTable(table: TimeTableEntity) = tableDao.update(table)

    suspend fun deleteTable(id: Long) = tableDao.deleteById(id)

    suspend fun setDefault(id: Long) = tableDao.setDefault(id)

    suspend fun tableCount(): Int = tableDao.count()

    // ========== Course ==========

    fun observeCourses(tableId: Long): Flow<List<CourseEntity>> =
        courseDao.observeByTable(tableId)

    fun observeCoursesByDay(tableId: Long, day: Int): Flow<List<CourseEntity>> =
        courseDao.observeByTableAndDay(tableId, day)

    suspend fun getCoursesByDayOnce(tableId: Long, day: Int): List<CourseEntity> =
        courseDao.getByTableAndDayOnce(tableId, day)

    suspend fun getCourses(tableId: Long): List<CourseEntity> = courseDao.getByTable(tableId)

    suspend fun getCourse(id: Long): CourseEntity? = courseDao.getById(id)

    suspend fun insertCourse(course: CourseEntity): Long {
        val id = courseDao.insert(course)
        WidgetUpdater.notifyDataChanged(SleepyApp.get())
        return id
    }

    suspend fun insertCourses(courses: List<CourseEntity>): List<Long> {
        // 自动给每门课分配 groupId：同名+同tableId 的课程共享一个 groupId
        val withGroupIds = assignGroupIds(courses)
        val ids = courseDao.insertAll(withGroupIds)
        WidgetUpdater.notifyDataChanged(SleepyApp.get())
        return ids
    }

    suspend fun updateCourse(course: CourseEntity) {
        courseDao.update(course)
        WidgetUpdater.notifyDataChanged(SleepyApp.get())
    }

    /** 查同 groupId 下所有课程（用于编辑回填，按时段分 block） */
    suspend fun getGroupCourses(tableId: Long, groupId: String): List<CourseEntity> =
        courseDao.getByGroupId(tableId, groupId)

    /** 编辑课程组：删除同 groupId 全部记录，插入新草稿 */
    suspend fun updateCourseGroup(tableId: Long, groupId: String, newCourses: List<CourseEntity>) {
        courseDao.deleteByGroupId(tableId, groupId)
        courseDao.insertAll(newCourses)
        WidgetUpdater.notifyDataChanged(SleepyApp.get())
    }

    suspend fun deleteCourse(id: Long) {
        courseDao.deleteById(id)
        WidgetUpdater.notifyDataChanged(SleepyApp.get())
    }

    /** 删除同 groupId 全部记录 */
    suspend fun deleteCourseGroup(tableId: Long, groupId: String) {
        courseDao.deleteByGroupId(tableId, groupId)
        WidgetUpdater.notifyDataChanged(SleepyApp.get())
    }

    suspend fun countCourses(tableId: Long): Int = courseDao.countByTable(tableId)

    suspend fun totalCourseCount(): Int = courseDao.totalCount()

    /** 覆盖式导入（先删后插） */
    suspend fun replaceCourses(tableId: Long, courses: List<CourseEntity>) {
        val withGroupIds = assignGroupIds(courses)
        courseDao.replaceAll(tableId, withGroupIds)
        WidgetUpdater.notifyDataChanged(SleepyApp.get())
    }

    /**
     * 给列表中的课程分配 groupId：
     * - groupId 为空的按 courseName 分组，每组生成一个 UUID
     * - groupId 非空的保持不变（手动编辑时已有 groupId）
     */
    private fun assignGroupIds(courses: List<CourseEntity>): List<CourseEntity> {
        val nameToGroupId = mutableMapOf<String, String>()
        return courses.map { c ->
            if (c.groupId.isNotBlank()) {
                c
            } else {
                val gid = nameToGroupId.getOrPut(c.courseName) { java.util.UUID.randomUUID().toString() }
                c.copy(groupId = gid)
            }
        }
    }
}