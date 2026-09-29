package com.lingion.sleepy.data.migration

import com.lingion.sleepy.data.dao.CourseDao
import com.lingion.sleepy.data.dao.ImportDraftDao
import com.lingion.sleepy.data.dao.PeriodTableDao
import com.lingion.sleepy.data.dao.TimeTableDao
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.ImportDraftEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * database 模块 codec — Room 四表结构化快照。
 *
 * DTO 与实体字段一一对应 (列名稳定, ignoreUnknownKeys 向前兼容);
 * 导出冻结一次快照, 导入先完整解码再写库。
 */
object MigrationDatabaseCodec {

    @Serializable
    data class CourseRow(
        val id: Long,
        val groupId: String,
        val tableId: Long,
        val courseName: String,
        val teacher: String = "",
        val room: String = "",
        val note: String = "",
        val alias: String = "",
        val day: Int,
        val startNode: Int,
        val step: Int,
        val startWeek: Int,
        val endWeek: Int,
        val type: Int = 0,
        val color: String,
        val colorMode: Int = 0,
        val ownTime: Boolean = false,
        val isIrregularNode: Boolean = false,
        val isIrregularTime: Boolean = false,
        val startTime: String = "",
        val endTime: String = "",
        val credit: Float = 0f,
        val level: Int = 0,
    )

    @Serializable
    data class TimeTableRow(
        val id: Long,
        val name: String,
        val startDate: String,
        val maxWeek: Int = 20,
        val nodesPerDay: Int = 12,
        val timeJson: String,
        val color: String = "#FF6750A4",
        val isDefault: Boolean = false,
        val smartConfigJson: String = "",
        val createdAt: Long,
        val periodTableId: Long? = null,
        val preBindSnapshotJson: String = "",
    )

    @Serializable
    data class PeriodTableRow(
        val id: Long,
        val name: String,
        val nodesPerDay: Int = 12,
        val timeJson: String,
        val smartConfigJson: String = "",
        val createdAt: Long,
        val updatedAt: Long,
    )

    @Serializable
    data class ImportDraftRow(
        val id: String,
        val sourceType: String = "",
        val sourceUrl: String = "",
        val payloadJson: String,
        val createdAt: Long,
        val updatedAt: Long,
    )

    @Serializable
    data class DatabaseSnapshot(
        val periodTables: List<PeriodTableRow> = emptyList(),
        val timeTables: List<TimeTableRow> = emptyList(),
        val courses: List<CourseRow> = emptyList(),
        val importDrafts: List<ImportDraftRow> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(snapshot: DatabaseSnapshot): String =
        json.encodeToString(DatabaseSnapshot.serializer(), snapshot)

    fun decode(text: String): DatabaseSnapshot =
        json.decodeFromString(DatabaseSnapshot.serializer(), text)

    // ---- 实体 ↔ DTO ----

    fun CourseEntity.toRow() = CourseRow(
        id = id, groupId = groupId, tableId = tableId, courseName = courseName,
        teacher = teacher, room = room, note = note, alias = alias, day = day,
        startNode = startNode, step = step, startWeek = startWeek, endWeek = endWeek,
        type = type, color = color, colorMode = colorMode, ownTime = ownTime,
        isIrregularNode = isIrregularNode, isIrregularTime = isIrregularTime,
        startTime = startTime, endTime = endTime, credit = credit, level = level,
    )

    fun TimeTableEntity.toRow() = TimeTableRow(
        id = id, name = name, startDate = startDate, maxWeek = maxWeek,
        nodesPerDay = nodesPerDay, timeJson = timeJson, color = color,
        isDefault = isDefault, smartConfigJson = smartConfigJson,
        createdAt = createdAt, periodTableId = periodTableId,
        preBindSnapshotJson = preBindSnapshotJson,
    )

    fun PeriodTableEntity.toRow() = PeriodTableRow(
        id = id, name = name, nodesPerDay = nodesPerDay, timeJson = timeJson,
        smartConfigJson = smartConfigJson, createdAt = createdAt, updatedAt = updatedAt,
    )

    fun ImportDraftEntity.toRow() = ImportDraftRow(
        id = id, sourceType = sourceType, sourceUrl = sourceUrl,
        payloadJson = payloadJson, createdAt = createdAt, updatedAt = updatedAt,
    )

    fun CourseRow.toEntity() = CourseEntity(
        id = id, groupId = groupId, tableId = tableId, courseName = courseName,
        teacher = teacher, room = room, note = note, alias = alias, day = day,
        startNode = startNode, step = step, startWeek = startWeek, endWeek = endWeek,
        type = type, color = color, colorMode = colorMode, ownTime = ownTime,
        isIrregularNode = isIrregularNode, isIrregularTime = isIrregularTime,
        startTime = startTime, endTime = endTime, credit = credit, level = level,
    )

    fun TimeTableRow.toEntity() = TimeTableEntity(
        id = id, name = name, startDate = startDate, maxWeek = maxWeek,
        nodesPerDay = nodesPerDay, timeJson = timeJson, color = color,
        isDefault = isDefault, smartConfigJson = smartConfigJson,
        createdAt = createdAt, periodTableId = periodTableId,
        preBindSnapshotJson = preBindSnapshotJson,
    )

    fun PeriodTableRow.toEntity() = PeriodTableEntity(
        id = id, name = name, nodesPerDay = nodesPerDay, timeJson = timeJson,
        smartConfigJson = smartConfigJson, createdAt = createdAt, updatedAt = updatedAt,
    )

    fun ImportDraftRow.toEntity() = ImportDraftEntity(
        id = id, sourceType = sourceType, sourceUrl = sourceUrl,
        payloadJson = payloadJson, createdAt = createdAt, updatedAt = updatedAt,
    )

    // ---- 收集与写入 (DAO 面) ----

    suspend fun collect(
        courseDao: CourseDao,
        timeTableDao: TimeTableDao,
        periodTableDao: PeriodTableDao,
        importDraftDao: ImportDraftDao,
    ): DatabaseSnapshot = DatabaseSnapshot(
        periodTables = periodTableDao.getAll().map { it.toRow() },
        timeTables = timeTableDao.getAll().map { it.toRow() },
        courses = courseDao.getAll().map { it.toRow() },
        importDrafts = importDraftDao.getAll().map { it.toRow() },
    )

    /**
     * 覆盖导入: 四表全量替换, 保留包内原始 ID 与关系。
     * 写入顺序遵循 FK: period_tables → time_tables → courses; import_drafts 无 FK。
     */
    suspend fun applyOverwrite(
        snapshot: DatabaseSnapshot,
        courseDao: CourseDao,
        timeTableDao: TimeTableDao,
        periodTableDao: PeriodTableDao,
        importDraftDao: ImportDraftDao,
    ) {
        importDraftDao.deleteAll()
        courseDao.deleteAll()
        timeTableDao.deleteAll()
        periodTableDao.deleteAll()
        periodTableDao.insertAll(snapshot.periodTables.map { it.toEntity() })
        timeTableDao.insertAll(snapshot.timeTables.map { it.toEntity() })
        snapshot.courses.forEach { courseDao.insertKeepId(it.toEntity()) }
        snapshot.importDrafts.forEach { importDraftDao.upsert(it.toEntity()) }
    }

    /**
     * 合并导入: 保留本机数据, 追加班内数据。
     * 表/课以新自增 ID 落库并重映射 FK (不与本地 ID 冲突);
     * 草稿主键是业务字符串 ID, 同 ID 视为同一草稿 → upsert。
     */
    suspend fun applyMerge(
        snapshot: DatabaseSnapshot,
        courseDao: CourseDao,
        timeTableDao: TimeTableDao,
        periodTableDao: PeriodTableDao,
        importDraftDao: ImportDraftDao,
    ) {
        val periodIdMap = HashMap<Long, Long>()
        for (row in snapshot.periodTables) {
            val newId = periodTableDao.insert(row.toEntity().copy(id = 0))
            periodIdMap[row.id] = newId
        }
        val tableIdMap = HashMap<Long, Long>()
        for (row in snapshot.timeTables) {
            val newId = timeTableDao.insert(
                row.toEntity().copy(
                    id = 0,
                    periodTableId = row.periodTableId?.let { periodIdMap[it] },
                )
            )
            tableIdMap[row.id] = newId
        }
        for (row in snapshot.courses) {
            courseDao.insert(row.toEntity().copy(id = 0, tableId = tableIdMap[row.tableId] ?: continue))
        }
        for (row in snapshot.importDrafts) {
            importDraftDao.upsert(row.toEntity())
        }
    }
}
