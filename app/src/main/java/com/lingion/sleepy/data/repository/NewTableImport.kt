package com.lingion.sleepy.data.repository

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity

internal interface NewTableImportStore {
    suspend fun atomic(block: suspend () -> Long): Long
    suspend fun periodExists(id: Long): Boolean
    suspend fun captureUndo()
    suspend fun insertPeriod(period: PeriodTableEntity): Long
    suspend fun insertTable(table: TimeTableEntity): Long
    suspend fun insertCourses(courses: List<CourseEntity>)
    suspend fun setDefault(id: Long)

    /** 认亲名册（spec §2.4）— 默认空 = v1.0.61 行为; Room 适配覆盖为全库查询。 */
    suspend fun knownNameRoster(): Map<String, String> = emptyMap()

    /** 派生档新课补色落库 — 默认无操作; Room 适配覆盖写 course_colors。 */
    suspend fun persistNewGroupColors(courses: List<CourseEntity>) {}
}

internal suspend fun importNewTableAtomically(
    store: NewTableImportStore, table: TimeTableEntity, courses: List<CourseEntity>,
    period: PeriodTableEntity?, bindingId: Long?, keepGroups: Boolean
): Long = store.atomic {
    require(bindingId == null || store.periodExists(bindingId)) { "Selected period table no longer exists" }
    store.captureUndo()
    val periodId = bindingId ?: period?.let { store.insertPeriod(it.copy(id = 0)) }
    val tableId = store.insertTable(table.copy(id = 0, periodTableId = periodId, isDefault = false))
    val rows = courses.map { it.copy(id = 0, tableId = tableId) }
    val grouped = if (keepGroups) rows else normalizedImportGroups(rows, store.knownNameRoster())
    store.insertCourses(grouped)
    store.persistNewGroupColors(grouped)
    store.setDefault(tableId)
    tableId
}

internal fun normalizedImportGroups(
    courses: List<CourseEntity>,
    knownNameToGroup: Map<String, String> = emptyMap()
): List<CourseEntity> {
    val nameToGroupId = mutableMapOf<String, String>()
    return courses.map { c ->
        val key = importNameKey(c.courseName)
        val gid = nameToGroupId.getOrPut(key) {
            // 认亲（spec §2.4）: 全库同名课 → 继承旧 groupId → 持久 hue 直接命中,
            // 颜色跨导入/跨表不变。命中优先级: 全库名册 > 行自带 gid（sleepy-v1
            // token 分区等权威来源）> 新 UUID。
            knownNameToGroup[key]
                ?: c.groupId.takeIf { it.isNotBlank() }
                ?: java.util.UUID.randomUUID().toString()
        }
        c.copy(groupId = gid)
    }
}

/** 认亲/分组共用身份 key — trim + 空白折叠 + 小写（v7.10.x 导入身份口径单点）。 */
internal fun importNameKey(courseName: String): String =
    courseName.trim().replace(Regex("\\s+"), " ").lowercase()

/**
 * 全库 (规范化课程名 → groupId) 名册 — 认亲输入（spec §2.4）。
 * 同名冲突取首个（调用方按 id 升序喂 getAll() → 确定性）。
 * key 与 normalizedImportGroups 同一口径。
 */
internal fun knownNameToGroupId(existingRows: List<CourseEntity>): Map<String, String> {
    val map = LinkedHashMap<String, String>()
    for (row in existingRows) {
        if (row.groupId.isBlank()) continue
        val key = importNameKey(row.courseName)
        if (key.isNotEmpty()) map.putIfAbsent(key, row.groupId)
    }
    return map
}
