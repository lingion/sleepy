package com.lingion.sleepy.data.migration

import com.lingion.sleepy.data.dao.CourseDao
import com.lingion.sleepy.data.dao.ImportDraftDao
import com.lingion.sleepy.data.dao.PeriodTableDao
import com.lingion.sleepy.data.dao.TimeTableDao
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.ImportDraftEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 模拟 Room autoGenerate 语义: id==0 走自增, id!=0 保留显式 id (REPLACE)。 */
internal class FakeCourseDao : CourseDao {
    val rows = LinkedHashMap<Long, CourseEntity>()
    private var nextId = 1L

    override suspend fun insert(course: CourseEntity): Long {
        val id = if (course.id == 0L) nextId++ else course.id
        rows[id] = course.copy(id = id)
        return id
    }

    override suspend fun insertAll(courses: List<CourseEntity>): List<Long> = courses.map { insert(it) }

    override suspend fun update(course: CourseEntity) {
        rows[course.id] = course
    }

    override suspend fun updateAll(courses: List<CourseEntity>) = courses.forEach { update(it) }

    override suspend fun insertKeepId(course: CourseEntity): Long {
        rows[course.id] = course
        return course.id
    }

    override suspend fun deleteByIds(ids: List<Long>) = ids.forEach { rows.remove(it) }
    override suspend fun deleteById(id: Long) { rows.remove(id) }
    override suspend fun deleteByTableId(tableId: Long) { rows.values.removeAll { it.tableId == tableId } }
    override suspend fun deleteByGroupId(tableId: Long, groupId: String) {
        rows.values.removeAll { it.tableId == tableId && it.groupId == groupId }
    }

    override suspend fun deleteAll() = rows.clear()
    override suspend fun getAll(): List<CourseEntity> = rows.values.toList()
    override suspend fun getById(id: Long): CourseEntity? = rows[id]
    override fun observeByTable(tableId: Long): Flow<List<CourseEntity>> = flowOf(emptyList())
    override fun observeByTableAndDay(tableId: Long, day: Int): Flow<List<CourseEntity>> = flowOf(emptyList())
    override suspend fun getByTableAndDayOnce(tableId: Long, day: Int): List<CourseEntity> = emptyList()
    override suspend fun getByTable(tableId: Long): List<CourseEntity> = rows.values.filter { it.tableId == tableId }
    override suspend fun getByGroupId(tableId: Long, groupId: String): List<CourseEntity> =
        rows.values.filter { it.tableId == tableId && it.groupId == groupId }

    override suspend fun countByTable(tableId: Long): Int = getByTable(tableId).size
    override suspend fun totalCount(): Int = rows.size
}

internal class FakeTimeTableDao : TimeTableDao {
    val rows = LinkedHashMap<Long, TimeTableEntity>()
    private var nextId = 1L
    var defaultId: Long? = null

    override suspend fun insert(table: TimeTableEntity): Long {
        val id = if (table.id == 0L) nextId++ else table.id
        rows[id] = table.copy(id = id)
        return id
    }

    override suspend fun insertAll(tables: List<TimeTableEntity>) = tables.forEach { insert(it) }
    override suspend fun update(table: TimeTableEntity) {
        rows[table.id] = table
    }

    override suspend fun deleteById(id: Long) { rows.remove(id) }
    override suspend fun deleteAll() {
        rows.clear()
        defaultId = null
    }

    override suspend fun getById(id: Long): TimeTableEntity? = rows[id]
    override fun observeById(id: Long): Flow<TimeTableEntity?> = flowOf(null)
    override fun observeAll(): Flow<List<TimeTableEntity>> = flowOf(emptyList())
    override suspend fun getAll(): List<TimeTableEntity> = rows.values.toList()
    override suspend fun count(): Int = rows.size
    override suspend fun setDefault(id: Long) {
        defaultId = id
    }

    override suspend fun getDefault(): TimeTableEntity? = defaultId?.let { rows[it] }
    override suspend fun getAllBoundTo(periodTableId: Long): List<TimeTableEntity> =
        rows.values.filter { it.periodTableId == periodTableId }
}

internal class FakePeriodTableDao : PeriodTableDao {
    val rows = LinkedHashMap<Long, PeriodTableEntity>()
    private var nextId = 1L

    override suspend fun insert(table: PeriodTableEntity): Long {
        val id = if (table.id == 0L) nextId++ else table.id
        rows[id] = table.copy(id = id)
        return id
    }

    override suspend fun insertAll(tables: List<PeriodTableEntity>) = tables.forEach { insert(it) }
    override suspend fun update(table: PeriodTableEntity) {
        rows[table.id] = table
    }

    override suspend fun deleteById(id: Long) { rows.remove(id) }
    override suspend fun deleteAll() = rows.clear()
    override suspend fun getById(id: Long): PeriodTableEntity? = rows[id]
    override fun observeById(id: Long): Flow<PeriodTableEntity?> = flowOf(null)
    override fun observeAll(): Flow<List<PeriodTableEntity>> = flowOf(emptyList())
    override suspend fun getAll(): List<PeriodTableEntity> = rows.values.toList()
    override suspend fun count(): Int = rows.size
    override suspend fun boundTableCount(id: Long): Int = 0
    override suspend fun boundTableIds(id: Long): List<Long> = emptyList()
}

internal class FakeImportDraftDao : ImportDraftDao {
    val rows = LinkedHashMap<String, ImportDraftEntity>()

    override suspend fun insert(draft: ImportDraftEntity) {
        rows[draft.id] = draft
    }

    override suspend fun update(draft: ImportDraftEntity) {
        rows[draft.id] = draft
    }

    override suspend fun upsert(draft: ImportDraftEntity) {
        rows[draft.id] = draft
    }

    override suspend fun getById(id: String): ImportDraftEntity? = rows[id]
    override fun observeById(id: String): Flow<ImportDraftEntity?> = flowOf(null)
    override fun observeAll(): Flow<List<ImportDraftEntity>> = flowOf(emptyList())
    override suspend fun getAll(): List<ImportDraftEntity> = rows.values.toList()
    override suspend fun deleteById(id: String) { rows.remove(id) }
    override suspend fun deleteAll() = rows.clear()
}

class MigrationDatabaseCodecTest {

    private fun sampleCourse(tableId: Long, name: String = "高数") = CourseEntity(
        id = 101, groupId = "g1", tableId = tableId, courseName = name, teacher = "张三",
        room = "11#301", note = "", alias = "math", day = 1, startNode = 1, step = 2,
        startWeek = 1, endWeek = 16, type = 1, color = "#FF6750A4", colorMode = 2,
        ownTime = false, isIrregularNode = true, isIrregularTime = false,
        startTime = "08:00", endTime = "09:40", credit = 3.5f, level = 1,
    )

    private fun sampleSnapshot() = MigrationDatabaseCodec.DatabaseSnapshot(
        periodTables = listOf(
            MigrationDatabaseCodec.PeriodTableRow(id = 9, name = "默认作息", timeJson = "[{}]", createdAt = 1, updatedAt = 2),
        ),
        timeTables = listOf(
            MigrationDatabaseCodec.TimeTableRow(
                id = 5, name = "大三上", startDate = "2026-09-01", timeJson = "[]",
                color = "#FF6750A4", createdAt = 3, periodTableId = 9,
            ),
        ),
        courses = listOf(
            MigrationDatabaseCodec.CourseRow(
                id = 101, groupId = "g1", tableId = 5, courseName = "高数", day = 1,
                startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#FF6750A4",
            ),
        ),
        importDrafts = listOf(
            MigrationDatabaseCodec.ImportDraftRow(id = "draft-1", payloadJson = "{}", createdAt = 4, updatedAt = 5),
        ),
    )

    @Test
    fun `entity to row and back preserves every field`() {
        val entity = sampleCourse(tableId = 5)
        val roundtrip = MigrationDatabaseCodec.run { entity.toRow().toEntity() }
        assertEquals(entity, roundtrip)
    }

    @Test
    fun `json roundtrip preserves snapshot`() {
        val snapshot = sampleSnapshot()
        val text = MigrationDatabaseCodec.encode(snapshot)
        val decoded = MigrationDatabaseCodec.decode(text)
        assertEquals(snapshot, decoded)
        // 显式校验关键关系字段
        assertEquals(9L, decoded.timeTables.single().periodTableId)
        assertEquals(5L, decoded.courses.single().tableId)
        assertEquals("draft-1", decoded.importDrafts.single().id)
    }

    @Test
    fun `collect reads all four tables`() = runBlocking {
        val courseDao = FakeCourseDao()
        val timeTableDao = FakeTimeTableDao()
        val periodTableDao = FakePeriodTableDao()
        val importDraftDao = FakeImportDraftDao()
        periodTableDao.insert(PeriodTableEntity(name = "作息", timeJson = "[]", createdAt = 1, updatedAt = 1))
        timeTableDao.insert(TimeTableEntity(name = "表", startDate = "2026-09-01", timeJson = "[]", createdAt = 1))
        courseDao.insert(sampleCourse(tableId = 1).copy(id = 0))
        importDraftDao.insert(ImportDraftEntity(id = "d", payloadJson = "{}", createdAt = 1, updatedAt = 1))

        val snapshot = MigrationDatabaseCodec.collect(courseDao, timeTableDao, periodTableDao, importDraftDao)
        assertEquals(1, snapshot.periodTables.size)
        assertEquals(1, snapshot.timeTables.size)
        assertEquals(1, snapshot.courses.size)
        assertEquals(1, snapshot.importDrafts.size)
    }

    @Test
    fun `overwrite restores original ids and relations`() = runBlocking {
        val snapshot = sampleSnapshot()
        val courseDao = FakeCourseDao()
        val timeTableDao = FakeTimeTableDao()
        val periodTableDao = FakePeriodTableDao()
        val importDraftDao = FakeImportDraftDao()
        // 本机已有脏数据, 应被清空
        courseDao.insert(sampleCourse(tableId = 99).copy(id = 500))
        timeTableDao.insert(TimeTableEntity(name = "旧表", startDate = "", timeJson = "[]", createdAt = 1).copy(id = 500))

        MigrationDatabaseCodec.applyOverwrite(snapshot, courseDao, timeTableDao, periodTableDao, importDraftDao)

        assertEquals(9L, periodTableDao.rows.keys.single())
        assertEquals(5L, timeTableDao.rows.keys.single())
        assertEquals(9L, timeTableDao.rows.getValue(5L).periodTableId)
        assertEquals(101L, courseDao.rows.keys.single())
        assertEquals(5L, courseDao.rows.getValue(101L).tableId)
        assertEquals("draft-1", importDraftDao.rows.keys.single())
    }

    @Test
    fun `merge remaps ids so package rows never collide with local rows`() = runBlocking {
        val snapshot = sampleSnapshot()
        val courseDao = FakeCourseDao()
        val timeTableDao = FakeTimeTableDao()
        val periodTableDao = FakePeriodTableDao()
        val importDraftDao = FakeImportDraftDao()
        // 本机已有 id=9 的作息 / id=5 的表 / id=101 的课, 与包内完全同 id
        periodTableDao.insert(PeriodTableEntity(name = "本地作息", timeJson = "[]", createdAt = 1, updatedAt = 1).copy(id = 9))
        timeTableDao.insert(TimeTableEntity(name = "本地表", startDate = "", timeJson = "[]", createdAt = 1).copy(id = 5))
        courseDao.insert(sampleCourse(tableId = 5, name = "本地课").copy(id = 101))

        MigrationDatabaseCodec.applyMerge(snapshot, courseDao, timeTableDao, periodTableDao, importDraftDao)

        // 本机数据原样保留
        assertEquals("本地作息", periodTableDao.rows.getValue(9L).name)
        assertEquals("本地表", timeTableDao.rows.getValue(5L).name)
        assertEquals("本地课", courseDao.rows.getValue(101L).courseName)
        // 包内行全部落在新 id 上, FK 重映射到新 period/table id
        val newPeriodId = periodTableDao.rows.keys.single { it != 9L }
        val newTableId = timeTableDao.rows.keys.single { it != 5L }
        val newCourseId = courseDao.rows.keys.single { it != 101L }
        assertEquals(newPeriodId, timeTableDao.rows.getValue(newTableId).periodTableId)
        assertEquals(newTableId, courseDao.rows.getValue(newCourseId).tableId)
        assertEquals("高数", courseDao.rows.getValue(newCourseId).courseName)
    }

    @Test
    fun `merge skips orphan course whose table is not in package`() = runBlocking {
        val snapshot = sampleSnapshot().copy(
            courses = listOf(sampleSnapshot().courses.single().copy(tableId = 404)),
            timeTables = emptyList(),
            periodTables = emptyList(),
        )
        val courseDao = FakeCourseDao()
        val timeTableDao = FakeTimeTableDao()
        val periodTableDao = FakePeriodTableDao()
        val importDraftDao = FakeImportDraftDao()

        MigrationDatabaseCodec.applyMerge(snapshot, courseDao, timeTableDao, periodTableDao, importDraftDao)

        assertEquals(0, courseDao.rows.size)
        assertNull(timeTableDao.getDefault())
    }
}
