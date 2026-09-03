package com.lingion.sleepy.data.repository

import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.SleepyApp
import androidx.room.withTransaction
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

    // ========== v7.10.16 单级撤回 ==========

    val canUndo: Boolean get() = UndoManager.hasSnapshot

    /**
     * 公开写方法执行前调用 — 拍下改动前的全库状态。
     * 复合动作(如导入)入口先 [UndoManager.beginBatch], 批内只保首个快照=动作前时点。
     */
    private suspend fun captureForUndo() {
        UndoManager.capture(
            tables = tableDao.getAll(),
            courses = courseDao.getAll(),
            defaultTableId = tableDao.getDefault()?.id
        )
    }

    /** 撤回最近一次改动: 事务内清两表→重插快照→恢复 default → 刷 widget/通知。false = 无可撤回 */
    suspend fun restoreLastSnapshot(): Boolean {
        val snap = UndoManager.poll() ?: return false
        UndoManager.restoring = true
        try {
            db.withTransaction {
                courseDao.deleteAll()
                tableDao.deleteAll()
                // 先插 tables 再插 courses — courses.tableId 有外键指向 time_tables.id,
                // 顺序颠倒(先课程后课表)会触发外键约束 SQLiteConstraintException 闪退
                tableDao.insertAll(snap.tables)
                courseDao.insertAll(snap.courses)
                snap.defaultTableId?.let { tableDao.setDefault(it) }
            }
        } finally {
            UndoManager.restoring = false
        }
        onDataChanged()
        return true
    }

    // ========== TimeTable ==========

    fun observeAllTables(): Flow<List<TimeTableEntity>> = tableDao.observeAll()

    fun observeTable(id: Long): Flow<TimeTableEntity?> = tableDao.observeById(id)

    suspend fun getAllTables(): List<TimeTableEntity> = tableDao.getAll()

    suspend fun getTable(id: Long): TimeTableEntity? = tableDao.getById(id)

    suspend fun getDefaultTable(): TimeTableEntity? = tableDao.getDefault()

    suspend fun insertTable(table: TimeTableEntity): Long {
        captureForUndo()
        val id = tableDao.insert(table)
        if (table.isDefault || tableDao.count() == 1) {
            tableDao.setDefault(id)
        }
        return id
    }

    suspend fun updateTable(table: TimeTableEntity) {
        captureForUndo()
        tableDao.update(table)
        onDataChanged()
    }

    suspend fun deleteTable(id: Long) {
        captureForUndo()
        // 删除前先取该表全部课程 id：tableDao.deleteById 靠外键 CASCADE 级联删课程，
        //   删完后这些 id 已不在库里，scheduleAll → cancelAll 按"现存课程"枚举 cancel 不到它们，
        //   当天已排的课程级课前闹钟（RC_BEFORE_CLASS_BASE+cid）会残留到点继续响。
        //   因此必须在删除前捕获 id 列表，删除后对这些"孤儿 id"显式取消闹钟。
        val orphanCourseIds = courseDao.getByTable(id).map { it.id }
        tableDao.deleteById(id)
        if (orphanCourseIds.isNotEmpty()) {
            SleepyApp.get().notificationScheduler.cancelCourseAlarms(orphanCourseIds)
        }
        onDataChanged()
    }

    suspend fun setDefault(id: Long) {
        captureForUndo()
        tableDao.setDefault(id)
        onDataChanged()
    }

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
        captureForUndo()
        val id = courseDao.insert(course)
        onDataChanged()
        return id
    }

    suspend fun insertCourses(courses: List<CourseEntity>): List<Long> {
        captureForUndo()
        // 导入时以规范化课程名为身份；时间、教师、教室只属于课程的一个时段。
        val withGroupIds = assignGroupIds(courses)
        val ids = courseDao.insertAll(withGroupIds)
        onDataChanged()
        return ids
    }

    suspend fun updateCourse(course: CourseEntity) {
        captureForUndo()
        courseDao.update(course)
        onDataChanged()
    }

    /** 查同 groupId 下所有课程（用于编辑回填，按时段分 block） */
    suspend fun getGroupCourses(tableId: Long, groupId: String): List<CourseEntity> =
        courseDao.getByGroupId(tableId, groupId)

    /** 编辑课程组：原子地删除同 groupId 全部记录并插入新草稿（DAO 层 @Transaction）。
     *  防呆: groupId 空串(早期版本导入的存量数据)禁止走组替换 — 否则 DELETE WHERE groupId=''
     *  会把该表全部空组课程一起删掉。空组时退化为逐条插入。 */
    suspend fun updateCourseGroup(tableId: Long, groupId: String, newCourses: List<CourseEntity>) {
        captureForUndo()
        if (groupId.isBlank()) {
            courseDao.insertAll(newCourses)
            onDataChanged()
            return
        }
        courseDao.replaceGroup(tableId, groupId, newCourses)
        onDataChanged()
    }

    suspend fun deleteCourse(id: Long) {
        captureForUndo()
        courseDao.deleteById(id)
        onDataChanged()
    }

    /** 删除同 groupId 全部记录。防呆: 空 groupId 拒删(否则整表空组课程全没了) */
    suspend fun deleteCourseGroup(tableId: Long, groupId: String) {
        captureForUndo()
        if (groupId.isBlank()) return
        courseDao.deleteByGroupId(tableId, groupId)
        onDataChanged()
    }

    suspend fun countCourses(tableId: Long): Int = courseDao.countByTable(tableId)

    suspend fun totalCourseCount(): Int = courseDao.totalCount()

    /** 覆盖式导入（先删后插） */
    suspend fun replaceCourses(tableId: Long, courses: List<CourseEntity>) {
        captureForUndo()
        val withGroupIds = assignGroupIds(courses)
        courseDao.replaceAll(tableId, withGroupIds)
        onDataChanged()
    }

    /**
     * 数据变更后：刷新所有 widget，并在提醒开启时重排通知（含流体云）。
     * 修复：之前只刷 widget 不重排通知，导致编辑课表后课前提醒/流体云仍按旧时间。
     */
    private suspend fun onDataChanged() {
        val app = SleepyApp.get()
        WidgetUpdater.notifyDataChanged(app)
        try {
            app.notificationScheduler.scheduleAll()
        } catch (_: Throwable) {
            // 提醒未开启或调度失败不应影响写操作本身
        }
    }

    private fun assignGroupIds(courses: List<CourseEntity>): List<CourseEntity> {
        val nameToGroupId = mutableMapOf<String, String>()
        return courses.map { c ->
            val key = c.courseName.trim().replace(Regex("\\s+"), " ").lowercase()
            val gid = nameToGroupId.getOrPut(key) { c.groupId.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString() }
            c.copy(groupId = gid)
        }
    }
}
