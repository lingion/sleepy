package com.lingion.sleepy.data.repository

import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.diff.DiffResult
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.data.undo.UndoSnapshot
import com.lingion.sleepy.SleepyApp
import androidx.room.withTransaction
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.ConflictLayoutEngine
import com.lingion.sleepy.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import com.lingion.sleepy.util.AppClock

/**
 * 课表仓库 — 业务数据访问的唯一入口。
 *
 * UI 层只调这个类，不直接碰 DAO。
 */
class ScheduleRepository(private val db: AppDatabase) {

    private val courseDao = db.courseDao()
    private val tableDao = db.timeTableDao()
    private val periodTableDao = db.periodTableDao()

    // ========== v7.10.16 单级撤回 ==========

    val canUndo: Boolean get() = UndoManager.hasSnapshot
    val canRedo: Boolean get() = UndoManager.hasRedoSnapshot

    /**
     * 公开写方法执行前调用 — 拍下改动前的全库状态。
     * 复合动作(如导入)入口先 [UndoManager.beginBatch], 批内只保首个快照=动作前时点。
     */
    private suspend fun captureForUndo() {
        UndoManager.capture(
            tables = tableDao.getAll(),
            courses = courseDao.getAll(),
            defaultTableId = tableDao.getDefault()?.id,
            periodTables = periodTableDao.getAll()
        )
    }

    /** 取一份当前库态 — 内部用: 给 undo / redo 的反向槽用。 */
    private suspend fun snapshotCurrent(): UndoSnapshot = UndoSnapshot(
        periodTables = periodTableDao.getAll(),
        tables = tableDao.getAll(),
        courses = courseDao.getAll(),
        defaultTableId = tableDao.getDefault()?.id
    )

    private suspend fun applySnapshot(snap: UndoSnapshot) {
        db.withTransaction {
            courseDao.deleteAll()
            tableDao.deleteAll()
            // issue#40: 恢复顺序 period_tables → time_tables → courses。
            // time_tables.periodTableId 指向 period_tables.id — 先插 periodTables
            // 保证引用目标先存在; 先课程后课表会触发外键约束闪退。
            periodTableDao.deleteAll()
            periodTableDao.insertAll(snap.periodTables)
            tableDao.insertAll(snap.tables)
            courseDao.insertAll(snap.courses)
            snap.defaultTableId?.let { tableDao.setDefault(it) }
        }
    }

    /** 撤回最近一次改动: 事务内清三表→按外键顺序重插快照→恢复 default → 刷 widget/通知。false = 无可撤回 */
    suspend fun restoreLastSnapshot(): Boolean {
        val undoSnap = UndoManager.poll() ?: return false
        // 把"撤回前"库态(=当前库态)落入 redo 槽, 用户点 redo 时再恢复回去。
        val redoSnap = snapshotCurrent()
        UndoManager.restoring = true
        try {
            applySnapshot(undoSnap)
        } finally {
            UndoManager.restoring = false
        }
        UndoManager.recordRedo(redoSnap)
        onDataChanged()
        pruneDefaultTopPrefs()
        return true
    }

    /** 取消最近一次撤回: redo 槽快照恢复回库, undo 槽收回当前库态。false = 无可取消。 */
    suspend fun redoLastUndo(): Boolean {
        val redoSnap = UndoManager.pollRedo() ?: return false
        val undoSnap = snapshotCurrent()
        UndoManager.restoring = true
        try {
            applySnapshot(redoSnap)
        } finally {
            UndoManager.restoring = false
        }
        // 把"刚恢复到的 redo 起点之前那一刻"重新填回 undo 槽,
        // 用户再次点撤回应能回到"被我们撤回来的那次修改之前的库态"。
        UndoManager.reinsertForRedoSymmetry(undoSnap)
        onDataChanged()
        pruneDefaultTopPrefs()
        return true
    }

    // ========== TimeTable ==========

    fun observeAllTables(): Flow<List<TimeTableEntity>> = tableDao.observeAll()

    fun observeTable(id: Long): Flow<TimeTableEntity?> = tableDao.observeById(id)

    suspend fun getAllTables(): List<TimeTableEntity> = tableDao.getAll()

    suspend fun getTable(id: Long): TimeTableEntity? = tableDao.getById(id)

    /** Back up and append in one transaction; a failed write leaves no partial backup. */
    internal suspend fun appendImportedCourses(
        tableId: Long,
        incoming: List<CourseEntity>,
        incomingTimeJson: String,
        incomingNodes: Int,
        backupOriginal: Boolean,
        appendConflicts: Boolean,
        authoritativeGroups: Boolean,
        backupName: String,
    ): Int {
        val added = db.withTransaction {
            val table = tableDao.getById(tableId) ?: error("Target timetable no longer exists")
            val bound = table.periodTableId?.let { periodTableDao.getById(it) }
            val effective = table.hydratedWith(bound)
            val existing = courseDao.getByTable(tableId)
            val extended = com.lingion.sleepy.util.CourseImportPolicy.mergeTimeJson(
                effective.timeJson, incomingTimeJson,
                maxOf(incomingNodes, incoming.maxOfOrNull { it.startNode + it.step - 1 } ?: 0)
            )
            val accepted = com.lingion.sleepy.util.CourseImportPolicy.selectIncoming(
                incoming, existing, appendConflicts, extended
            )
            if (accepted.isEmpty()) return@withTransaction 0
            captureForUndo()
            if (backupOriginal) {
                val names = tableDao.getAll().map { it.name }
                val periods = periodTableDao.getAll().map { it.name }
                val name = com.lingion.sleepy.util.TimeTableUtils.suggestUniqueName(backupName, names, periods)
                // Freeze the effective bell schedule so a later shared-schedule update cannot change the backup.
                val backupId = tableDao.insert(effective.copy(
                    id = 0, name = name, isDefault = false, periodTableId = null,
                    preBindSnapshotJson = "", createdAt = AppClock.millis(), reminderEnabled = false
                ))
                courseDao.insertAll(existing.map { it.copy(id = 0, tableId = backupId) })
            }
            // Imported group IDs must not merge with a group already present in the target.
            val groups = mutableMapOf<String, String>()
            val prepared = (if (authoritativeGroups) accepted else assignGroupIds(accepted)).map {
                it.copy(id = 0, tableId = tableId,
                    groupId = if (it.groupId.isBlank()) java.util.UUID.randomUUID().toString()
                        else groups.getOrPut(it.groupId) { java.util.UUID.randomUUID().toString() })
            }
            courseDao.insertAll(prepared)
            com.lingion.sleepy.util.CourseColorWriteGlue.persistNewGroupHues(
                SleepyApp.get(), db.courseColorDao(), prepared
            )
            val maxNode = com.lingion.sleepy.util.TimeTableUtils.parseTimeSlotRows(extended)
                .maxOfOrNull { it.node } ?: effective.nodesPerDay
            if (bound != null && extended != bound.timeJson) {
                periodTableDao.update(bound.copy(timeJson = extended, nodesPerDay = maxNode, updatedAt = AppClock.millis()))
            }
            tableDao.update(table.copy(
                maxWeek = maxOf(table.maxWeek, accepted.maxOf { it.endWeek }),
                timeJson = if (bound == null) extended else table.timeJson,
                nodesPerDay = if (bound == null) maxNode else table.nodesPerDay,
            ))
            // The import result, never its backup, remains the active timetable.
            tableDao.setDefault(tableId)
            prepared.size
        }
        if (added > 0) onDataChanged()
        return added
    }

    suspend fun getDefaultTable(): TimeTableEntity? = tableDao.getDefault()

    suspend fun insertTable(table: TimeTableEntity): Long {
        captureForUndo()
        val id = tableDao.insert(table)
        if (table.isDefault || tableDao.count() == 1) {
            tableDao.setDefault(id)
        }
        return id
    }

    /** New timetable, optional private bell schedule, courses and default switch commit together. */
    suspend fun importNewTable(
        table: TimeTableEntity,
        courses: List<CourseEntity>,
        periodTable: com.lingion.sleepy.data.entity.PeriodTableEntity?,
        bindPeriodTableId: Long? = null,
        keepGroups: Boolean = false
    ): Long {
        val id = importNewTableAtomically(object : NewTableImportStore {
            override suspend fun atomic(block: suspend () -> Long): Long = db.withTransaction { block() }
            override suspend fun periodExists(id: Long) = periodTableDao.getById(id) != null
            override suspend fun captureUndo() = captureForUndo()
            override suspend fun insertPeriod(period: com.lingion.sleepy.data.entity.PeriodTableEntity): Long {
                val name = com.lingion.sleepy.util.TimeTableUtils.suggestUniqueName(
                    period.name, tableDao.getAll().map { it.name } + table.name,
                    periodTableDao.getAll().map { it.name }
                )
                return periodTableDao.insert(period.copy(name = name))
            }
            override suspend fun insertTable(table: TimeTableEntity): Long {
                val name = com.lingion.sleepy.util.TimeTableUtils.suggestUniqueName(
                    table.name, tableDao.getAll().map { it.name }, periodTableDao.getAll().map { it.name }
                )
                return tableDao.insert(table.copy(name = name))
            }
            override suspend fun insertCourses(courses: List<CourseEntity>) { courseDao.insertAll(courses) }
            override suspend fun setDefault(id: Long) = tableDao.setDefault(id)
            // 认亲（spec §2.4）: 事务内读导入前全库名册; 新课 hue 同事务落库。
            override suspend fun knownNameRoster() = knownNameToGroupId(courseDao.getAll())
            override suspend fun persistNewGroupColors(courses: List<CourseEntity>) {
                com.lingion.sleepy.util.CourseColorWriteGlue.persistNewGroupHues(
                    SleepyApp.get(), db.courseColorDao(), courses
                )
            }
        }, table, courses, periodTable, bindPeriodTableId, keepGroups)
        onDataChanged()
        return id
    }

    /** Reminder settings own this field; never overwrite timetable metadata. */
    suspend fun setTableReminderEnabled(id: Long, enabled: Boolean) {
        val changed = db.withTransaction {
            val current = tableDao.getById(id) ?: return@withTransaction false
            if (current.reminderEnabled == enabled) return@withTransaction false
            captureForUndo()
            tableDao.setReminderEnabled(id, enabled) > 0
        }
        if (changed) onDataChanged()
    }

    suspend fun updateTable(table: TimeTableEntity) {
        captureForUndo()
        tableDao.update(table)
        onDataChanged()
    }

    /**
     * 2026-09-23 症状3修复: 编辑课表保存 — 只写课程表自身元数据(name/startDate/maxWeek),
     * 绑定关系与兼容列原样保留。
     *
     * 场景: 课表绑定了共享作息表时, 用户同时改了名字/日期/周次与绑定关系;
     * updateTable 整行覆盖会带着旧 periodTableId 抹掉并发的 bind 写入(或反之),
     * 两个独立 launch 的竞态会让"改名不生效"复发。本方法从库内现值合成目标行,
     * 按字段挑着写, 绝不触碰 periodTableId/preBindSnapshotJson/timeJson 等列。
     *
     * [writeTimeDomain]=true 时同步写 timeJson/smartConfigJson/nodesPerDay — 仅用于
     * 解绑场景: 用户编辑的是旧源兼容列, 解绑后兼容列是真值。
     */
    suspend fun updateTableMetadata(
        table: TimeTableEntity,
        writeTimeDomain: Boolean = false
    ) {
        val current = tableDao.getById(table.id) ?: return
        captureForUndo()
        val merged = current.copy(
            name = table.name,
            startDate = table.startDate,
            maxWeek = table.maxWeek,
            timeJson = if (writeTimeDomain) table.timeJson else current.timeJson,
            smartConfigJson = if (writeTimeDomain) table.smartConfigJson else current.smartConfigJson,
            nodesPerDay = if (writeTimeDomain) table.nodesPerDay else current.nodesPerDay
        )
        if (merged != current) tableDao.update(merged)
        onDataChanged()
    }

    /**
     * 2026-09-23 症状3修复: 编辑课表保存 — 元数据 + 共享作息表内容, 单事务原子双写。
     *
     * 场景: 课表绑定共享作息表, 用户改了名字/日期/周次且同时改了节次内容;
     * 两条独立写(updateTable + updatePeriodTableContent)顺序不保证, 撤回快照
     * 也可能只拍到一半。本方法一次快照一次事务同时落两行, 要么都成要么都不成。
     */
    suspend fun updateTableMetadataWithPeriodTable(
        table: TimeTableEntity,
        periodTable: com.lingion.sleepy.data.entity.PeriodTableEntity
    ) {
        val current = tableDao.getById(table.id) ?: return
        if (periodTableDao.getById(periodTable.id) == null) return
        captureForUndo()
        db.withTransaction {
            tableDao.update(
                current.copy(
                    name = table.name,
                    startDate = table.startDate,
                    maxWeek = table.maxWeek
                )
            )
            periodTableDao.update(
                periodTable.copy(updatedAt = AppClock.millis())
            )
        }
        onDataChanged()
    }

    /**
     * 2026-09-23 症状3修复: 编辑课表保存 — 元数据 + 换绑, 单事务原子完成。
     *
     * 换绑时编辑区的 slotRows 已随 pendingBind 切到目标作息表 — 用户编辑的是目标表
     * 内容, 由 [periodContent] 一并落回目标 period_tables; 解绑(periodTableId=null)
     * 时用户编辑的是本表兼容列, 时间域从 [table] 落回解绑后的兼容列。
     * 与 bindPeriodTable 不同: 那条不落元数据, 这条一并落, 一次撤回整步回退。
     */
    suspend fun updateTableMetadataAndBind(
        table: TimeTableEntity,
        periodTableId: Long?,
        periodContent: com.lingion.sleepy.data.entity.PeriodTableEntity? = null
    ) {
        val current = tableDao.getById(table.id) ?: return
        if (periodTableId != null && periodTableDao.getById(periodTableId) == null) return
        if (periodContent != null && periodTableDao.getById(periodContent.id) == null) return
        if (current.periodTableId == periodTableId && periodContent == null) {
            // 绑定关系与目标内容都没变 — 退化为纯元数据写; 解绑态写时间域(兼容列即真值)
            updateTableMetadata(table, writeTimeDomain = periodTableId == null)
            return
        }
        captureForUndo()
        db.withTransaction {
            val next = when {
                current.periodTableId == null && periodTableId != null ->
                    TimeTableEntity.snapshotForBind(current, periodTableId)
                current.periodTableId != null && periodTableId == null ->
                    // 解绑: 先恢复绑定前快照, 再叠用户本次编辑的时间域(编辑区展示的
                    // 就是兼容列内容, 用户改动必须是解绑后的真值)
                    TimeTableEntity.restoredForUnbind(current).copy(
                        timeJson = table.timeJson,
                        smartConfigJson = table.smartConfigJson,
                        nodesPerDay = table.nodesPerDay
                    )
                else -> current.copy(periodTableId = periodTableId)
            }
            tableDao.update(
                next.copy(
                    name = table.name,
                    startDate = table.startDate,
                    maxWeek = table.maxWeek
                )
            )
            periodContent?.let {
                periodTableDao.update(it.copy(updatedAt = AppClock.millis()))
            }
        }
        onDataChanged()
    }

    /**
     * issue#28 P3: 编辑课表保存 — timeJson 变更时课程按绝对时间自适应新节次。
     *
     * 节次编号语义 = "该节在新表上的钟点", 表变了编号必须跟着变, 否则改完 16→12 节
     * 课程还停在 13-16 节。ownTime 课自带绝对时间(timeToNode 直接定位), 普通课按
     * remapCourseNodes 重排; 无法映射的课保持原节次。与 updateTable 同为单动作
     * 撤回单元(首快照 = 改表前)。
     */
    suspend fun updateTableRemappingCourses(table: TimeTableEntity) {
        captureForUndo()
        val oldJson = tableDao.getById(table.id)?.timeJson.orEmpty()
        tableDao.update(table)
        if (table.timeJson != oldJson) {
            val courses = courseDao.getByTable(table.id)
            val remapped = courses.map { c ->
                if (c.ownTime) {
                    val mapped = com.lingion.sleepy.util.TimeTableUtils.timeToNode(
                        c.startTime, c.endTime, table.timeJson
                    )
                    if (mapped != null) c.copy(startNode = mapped.first, step = mapped.second) else c
                } else {
                    val (node, step) = com.lingion.sleepy.util.TimeTableUtils.remapCourseNodes(
                        c.startNode, c.step, oldJson, table.timeJson
                    )
                    c.copy(startNode = node, step = step)
                }
            }
            val changed = remapped.filterIndexed { i, c -> c != courses[i] }
            if (changed.isNotEmpty()) courseDao.updateAll(changed)
        }
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
        // issue#44: 同步清该表调休映射(SharedPreferences 孤儿 key 防残留)
        try { AppPrefs.clearHolidayTransfers(SleepyApp.get(), id) } catch (_: Exception) {}
        if (orphanCourseIds.isNotEmpty()) {
            SleepyApp.get().notificationScheduler.cancelCourseAlarms(orphanCourseIds)
        }
        onDataChanged()
    }

    suspend fun setDefault(id: Long) {
        // v7.10.16i 不捕获快照: 切表(选择哪个表是当前表)是导航动作,不是课表数据改动 —
        // 捕获会让撤回键亮起、点了把用户切回原表(用户 2026-09-03「撤回键不是返回键」)。
        // 导入建新表路径的快照由同批内的 insertTable/insertCourses 捕获, 不受影响。
        tableDao.setDefault(id)
        onDataChanged()
    }

    // ========== PeriodTable (issue#40 独立时间节次表) ==========

    fun observeAllPeriodTables(): Flow<List<com.lingion.sleepy.data.entity.PeriodTableEntity>> =
        periodTableDao.observeAll()

    suspend fun getAllPeriodTables(): List<com.lingion.sleepy.data.entity.PeriodTableEntity> =
        periodTableDao.getAll()

    suspend fun getPeriodTable(id: Long): com.lingion.sleepy.data.entity.PeriodTableEntity? =
        periodTableDao.getById(id)

    /** 某时间节次表被多少张课程表绑定。 */
    suspend fun periodTableBoundCount(id: Long): Int = periodTableDao.boundTableCount(id)

    /**
     * 甲案 (设计文档 §4.1): 绑定到指定作息表的全部课表 —
     * 弹窗"另有 N 张课表绑定"提示 + 三选项作用域语义共用。
     */
    suspend fun getTablesBoundTo(periodTableId: Long): List<TimeTableEntity> =
        tableDao.getAllBoundTo(periodTableId)

    /**
     * 有效时间节次表解析(设计 §5.1): 绑定表存在 → 返回它;
     * 绑定指向已删除的 id(悬空引用, 仅可能来自旧数据/导入)或未绑定 → null, 调用方回退旧兼容列。
     */
    suspend fun effectivePeriodTable(tableId: Long): com.lingion.sleepy.data.entity.PeriodTableEntity? {
        val table = tableDao.getById(tableId) ?: return null
        val boundId = table.periodTableId ?: return null
        return periodTableDao.getById(boundId)
    }

    /**
     * 绑定时间节次表的观察流 — 绑定切换/时间表内容修改都会 emit 新值,
     * 上游(如 ScheduleViewModel)合并此流实现"立即全部同步"(设计 §5.2)。
     */
    fun observeEffectivePeriodTable(tableId: Long): Flow<com.lingion.sleepy.data.entity.PeriodTableEntity?> =
        tableDao.observeById(tableId)
            .flatMapLatest { table ->
                val boundId: Long? = table?.periodTableId
                if (boundId == null) {
                    kotlinx.coroutines.flow.flowOf<com.lingion.sleepy.data.entity.PeriodTableEntity?>(null)
                } else {
                    periodTableDao.observeById(boundId)
                }
            }

    /** 新建时间节次表, 返回新 id。独立写动作 = 独立撤回单元。 */
    suspend fun insertPeriodTable(table: com.lingion.sleepy.data.entity.PeriodTableEntity): Long {
        captureForUndo()
        val now = AppClock.millis()
        val withStamp = if (table.createdAt == 0L) table.copy(createdAt = now, updatedAt = now) else table
        return periodTableDao.insert(withStamp)
    }

    /**
     * 复制时间节次表(设计 §4.2): 生成新实体, 原表与原绑定关系零改动。返回新副本 id。
     */
    suspend fun copyPeriodTable(sourceId: Long): Long {
        val src = periodTableDao.getById(sourceId) ?: return -1L
        captureForUndo()
        val copy = src.copy(
            id = 0,
            name = src.name,   // 名称原样保留, UI 层决定是否加"副本"后缀
            createdAt = AppClock.millis(),
            updatedAt = AppClock.millis()
        )
        return periodTableDao.insert(copy)
    }

    /**
     * v1.0.56 T8: 按指定名建副本 — 复制弹窗确认后调用, 名字已由 UI 层查重。
     * 返回新副本 id, 源不存在返回 -1。
     */
    suspend fun copyPeriodTableAs(sourceId: Long, newName: String): Long {
        val src = periodTableDao.getById(sourceId) ?: return -1L
        captureForUndo()
        val copy = src.copy(
            id = 0,
            name = newName,
            createdAt = AppClock.millis(),
            updatedAt = AppClock.millis()
        )
        return periodTableDao.insert(copy)
    }

    /**
     * 绑定/解绑独立作息表: 绑定是单向指针, 不改本课表的兼容列。
     *
     * 首次绑定(null → 非 null)时把本课表原有的兼容列存入 preBindSnapshotJson;
     * 换绑(非 null → 非 null)不刷新快照; 解绑时恢复首次绑定前的兼容列并清空快照。
     * 这样绑定期间实时读取目标表, 解绑后仍回到用户自己的原始作息, 不发生数据覆盖。
     * 悬空目标拒绝, 无效动作不拍撤回快照。
     */
    suspend fun bindPeriodTable(timeTableId: Long, periodTableId: Long?) {
        val table = tableDao.getById(timeTableId) ?: return
        if (periodTableId != null && periodTableDao.getById(periodTableId) == null) return
        if (table.periodTableId == periodTableId) return
        captureForUndo()
        db.withTransaction {
            val next = when {
                table.periodTableId == null && periodTableId != null ->
                    TimeTableEntity.snapshotForBind(table, periodTableId)
                table.periodTableId != null && periodTableId == null ->
                    TimeTableEntity.restoredForUnbind(table)
                else -> table.copy(periodTableId = periodTableId)
            }
            tableDao.update(next)
        }
        onDataChanged()
    }

    /**
     * 修改时间节次表内容(设计 §5.2): 更新实体+updatedAt。
     * 所有绑定课表经 effectivePeriodTable 立即读到新作息, 课程行不重算。
     */
    suspend fun updatePeriodTable(table: com.lingion.sleepy.data.entity.PeriodTableEntity) {
        captureForUndo()
        periodTableDao.update(table.copy(updatedAt = AppClock.millis()))
        onDataChanged()
    }

    /**
     * 保存独立作息表内容。绑定课表通过 hydratedWith 实时读取新值，
     * 因此这里只更新 period_tables，不再污染任何课表兼容列。
     * 返回当前绑定课表数，供 UI 预览文案使用。
     */
    suspend fun savePeriodTable(
        table: com.lingion.sleepy.data.entity.PeriodTableEntity
    ): Int {
        if (periodTableDao.getById(table.id) == null) return 0
        captureForUndo()
        val boundCount = periodTableDao.boundTableCount(table.id)
        periodTableDao.update(table.copy(updatedAt = AppClock.millis()))
        onDataChanged()
        return boundCount
    }

    /** 删除前将当前完整作息保存到各绑定课表内；复制、解绑和删除为同一个撤回单元。 */
    suspend fun deletePeriodTable(id: Long, onlyIfUnbound: Boolean = false): Boolean {
        val deleted = deletePeriodTableAtomically(object : PeriodTableDeletionStore {
            override suspend fun atomic(block: suspend () -> Boolean) = db.withTransaction { block() }
            override suspend fun getPeriod(id: Long) = periodTableDao.getById(id)
            override suspend fun getBoundTables(id: Long) = tableDao.getAllBoundTo(id)
            override suspend fun captureUndo() = captureForUndo()
            override suspend fun updateTable(table: TimeTableEntity) = tableDao.update(table)
            override suspend fun deletePeriod(id: Long) = periodTableDao.deleteById(id)
        }, id, onlyIfUnbound)
        if (deleted) onDataChanged()
        return deleted
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

    /** issue#40: 全库课程 — 时间节次表保存预览须统计【所有】绑定表的课, 不只当前选中表 */
    suspend fun getAllCourses(): List<CourseEntity> = courseDao.getAll()

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
        // 认亲（spec §2.4）: 全库同名课 → 继承旧 groupId → 持久 hue 命中 → 颜色跨导入不变。
        val withGroupIds = assignGroupIds(courses, knownNameToGroupId(courseDao.getAll()))
        val ids = courseDao.insertAll(withGroupIds)
        com.lingion.sleepy.util.CourseColorWriteGlue.persistNewGroupHues(
            SleepyApp.get(), db.courseColorDao(), withGroupIds
        )
        onDataChanged()
        return ids
    }

    /**
     * sleepy-v1 (§3.4 契约一): groupId 已由解析端权威生成(按文档内 token 分区),
     * 落库绕过 assignGroupIds — 否则同名不同 token 的分区会被静默合并, 分区往返被破坏。
     */
    suspend fun insertCoursesKeepingGroups(courses: List<CourseEntity>): List<Long> {
        captureForUndo()
        val ids = courseDao.insertAll(courses)
        onDataChanged()
        return ids
    }

    /** 覆盖式导入(保留解析端 groupId), 配合 insertCoursesKeepingGroups 的 sleepy-v1 路径 */
    suspend fun replaceCoursesKeepingGroups(tableId: Long, courses: List<CourseEntity>) {
        captureForUndo()
        courseDao.replaceAll(tableId, courses)
        onDataChanged()
        pruneDefaultTopPrefs()
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
        val course = courseDao.getById(id) ?: return
        courseDao.deleteById(id)
        reclaimUnusedEdgeNodes(course.tableId)
        onDataChanged()
        pruneDefaultTopPrefs()
    }

    suspend fun getCoursesForDate(table: TimeTableEntity, date: java.time.LocalDate): List<CourseEntity> =
        com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.coursesForDate(
            date, table.startDate, table.maxWeek, courseDao.getByTable(table.id),
            AppPrefs.getHolidayTransfers(SleepyApp.get(), table.id)
        )

    suspend fun saveCourseDraft(
        drafts: List<CourseEntity>, editingGroup: String?, expectedCourses: List<CourseEntity>,
        expectedTable: TimeTableEntity, timeJson: String,
        expectedTransfers: List<com.lingion.sleepy.util.HolidayTransferEntry>,
    ): Boolean {
        val saved = db.withTransaction {
            val table = tableDao.getById(expectedTable.id) ?: return@withTransaction false
            val period = table.periodTableId?.let { periodTableDao.getById(it) }
            if (table.hydratedWith(period) != expectedTable || courseDao.getByTable(table.id) != expectedCourses ||
                AppPrefs.getHolidayTransfers(SleepyApp.get(), table.id) != expectedTransfers) return@withTransaction false
            captureForUndo()
            if (timeJson != expectedTable.timeJson) {
                if (period != null) periodTableDao.update(period.copy(timeJson = timeJson))
                else tableDao.update(table.copy(timeJson = timeJson))
            }
            if (editingGroup != null) {
                val diff = com.lingion.sleepy.data.diff.RowKeyDiffer.diff(drafts, expectedCourses.filter { it.groupId == editingGroup })
                if (diff.toDelete.isNotEmpty()) courseDao.deleteByIds(diff.toDelete)
                if (diff.toUpdate.isNotEmpty()) courseDao.updateAll(diff.toUpdate)
                if (diff.toInsert.isNotEmpty()) courseDao.insertAll(diff.toInsert)
                mergeMatchingCourseRows(table.id, editingGroup)
            } else {
                val rows = assignGroupIds(drafts, knownNameToGroupId(courseDao.getAll()))
                courseDao.insertAll(rows)
                com.lingion.sleepy.util.CourseColorWriteGlue.persistNewGroupHues(SleepyApp.get(), db.courseColorDao(), rows)
            }
            reclaimUnusedEdgeNodes(table.id)
            true
        }
        if (saved) { pruneDefaultTopPrefs(); onDataChanged() }
        return saved
    }

    /** Compare the preview snapshot inside the write transaction; never overwrite a changed schedule. */
    suspend fun saveOccurrence(
        occurrence: com.lingion.sleepy.util.CourseOccurrence,
        replacement: CourseEntity?,
        expectedCourses: List<CourseEntity>,
        expectedTable: TimeTableEntity,
        expectedTransfers: List<com.lingion.sleepy.util.HolidayTransferEntry>,
    ): Boolean {
        val saved = db.withTransaction {
            val table = tableDao.getById(expectedTable.id) ?: return@withTransaction false
            val effective = table.hydratedWith(table.periodTableId?.let { periodTableDao.getById(it) })
            if (effective != expectedTable || courseDao.getByTable(table.id) != expectedCourses ||
                AppPrefs.getHolidayTransfers(SleepyApp.get(), table.id) != expectedTransfers ||
                courseDao.getById(occurrence.course.id) != occurrence.course) return@withTransaction false
            val remaining = com.lingion.sleepy.util.CourseOccurrences.without(
                occurrence.course, occurrence.sourceDate, table.startDate
            )
            captureForUndo()
            courseDao.deleteById(occurrence.course.id)
            courseDao.insertAll(remaining + listOfNotNull(replacement?.copy(
                id = if (remaining.isEmpty()) occurrence.course.id else 0,
                tableId = table.id, groupId = occurrence.course.groupId
            )))
            if (replacement != null) mergeMatchingCourseRows(table.id, occurrence.course.groupId)
            reclaimUnusedEdgeNodes(table.id)
            true
        }
        if (saved) {
            pruneDefaultTopPrefs()
            onDataChanged()
        }
        return saved
    }

    /** Runs inside the edit transaction, sharing its undo snapshot and data-change notification. */
    private suspend fun mergeMatchingCourseRows(tableId: Long, groupId: String) {
        if (groupId.isBlank()) return
        val original = courseDao.getByGroupId(tableId, groupId)
        val merged = com.lingion.sleepy.util.CourseOccurrences.mergeMatchingRows(original)
        val retainedIds = merged.map { it.id }.toSet()
        val removed = original.filter { it.id !in retainedIds }.map { it.id }
        val updates = merged.filter { row -> original.firstOrNull { it.id == row.id } != row }
        if (removed.isNotEmpty()) courseDao.deleteByIds(removed)
        if (updates.isNotEmpty()) courseDao.updateAll(updates)
    }

    /** 删除同 groupId 全部记录。防呆: 空 groupId 拒删(否则整表空组课程全没了) */
    suspend fun deleteCourseGroup(tableId: Long, groupId: String) {
        captureForUndo()
        if (groupId.isBlank()) return
        courseDao.deleteByGroupId(tableId, groupId)
        reclaimUnusedEdgeNodes(tableId)
        onDataChanged()
        pruneDefaultTopPrefs()
    }

    /**
     * issue#23 修: 删课/删组后扫描该表 timeJson, 回收所有无人引用的边缘节次节点.
     * 直接走 tableDao.update 绕过 updateTable 的 captureForUndo — 撤回时上层
     * (deleteCourse 等)已捕获了"删前完整快照(含完整 timeJson)", 撤回 = 回到删前,
     * 课程和节点都复原, 此处不应再叠加中间快照.
     */
    private suspend fun reclaimUnusedEdgeNodes(tableId: Long) {
        val table = tableDao.getById(tableId) ?: return
        val period = table.periodTableId?.let { periodTableDao.getById(it) }
        val timeJson = period?.timeJson ?: table.timeJson
        // Shared slots may still be referenced by a different timetable.
        val owners = if (period == null) listOf(table) else tableDao.getAll().filter { it.periodTableId == period.id }
        val remaining = owners.flatMap { courseDao.getByTable(it.id) }
        val used = mutableSetOf<Int>()
        remaining.forEach { c ->
            val effStart = if (c.ownTime) {
                com.lingion.sleepy.util.TimeTableUtils.timeToNode(
                    c.startTime, c.endTime, timeJson
                )?.first ?: c.startNode
            } else c.startNode
            val end = (effStart + c.step - 1).coerceAtLeast(effStart)
            for (n in effStart..end) used.add(n)
            // 防御: 存储的 startNode 也算"用户意图", 即便 normalize 后位置不同
            // 也保留节点 — 防止误回收导致课崩.
            val storedEnd = c.startNode + c.step - 1
            for (n in c.startNode..storedEnd) used.add(n)
        }
        val reclaimed = com.lingion.sleepy.util.TimeTableUtils.reclaimUnusedEdgeNodes(
            timeJson, used
        )
        if (reclaimed != timeJson) {
            if (period != null) periodTableDao.update(period.copy(timeJson = reclaimed))
            else tableDao.update(table.copy(timeJson = reclaimed))
        }
    }

    /**
     * 行级 diff/patch 落库(替代 [updateCourseGroup] 整组覆盖, issue#22 同名多地点修复):
     *   - toDelete 行按 id 批量删除
     *   - toUpdate 行按 id 批量覆盖(保留 RowKey)
     *   - toInsert 行批量新增(id=0 让 Room 自增)
     *
     * v7.10.16v 撤回修复(用户 2026-09-10 报"编辑课程后没有撤回按钮"): 本方法自带
     * [captureForUndo] — issue#22 接手时原契约"调用前必须已 capture"无任何调用方
     * 履行, 编辑课程从此不产生快照。
     */
    suspend fun applyDiff(tableId: Long, diff: DiffResult) {
        captureForUndo()
        if (diff.toDelete.isNotEmpty()) courseDao.deleteByIds(diff.toDelete)
        if (diff.toUpdate.isNotEmpty()) courseDao.updateAll(diff.toUpdate)
        if (diff.toInsert.isNotEmpty()) courseDao.insertAll(diff.toInsert)
        // issue#23 修: 编辑删行也要回收(否则编辑掉 edge 上的 block 后节点残留)
        if (diff.toDelete.isNotEmpty()) reclaimUnusedEdgeNodes(tableId)
        onDataChanged()
    }

    /**
     * 改组色(issue#22 spec §6.2 "改组色"按钮的落库路径):
     * 把选中色写到同 groupId 所有 colorMode=GROUP 行的 color 字段。
     *
     * 只动 GROUP 行 — AUTO 行 hue 源来自组色源(groupSourceColorHex),组色变则
     * 自动行跟着变(spec §5.2"组色变则自动跟随"),不需要写;CUSTOM 行独立色不跟组。
     * 落所有 GROUP 行而非只落最小 id 行:groupSourceColorHex 取同组 GROUP 模式
     * 最小 id 行的 color,任一 GROUP 行带上色即成立;全组写一致值还能避免
     * RowKeyDiffer 把"只改了最小 id 行"之外的 GROUP 行 diff 出假更新。
     */
    suspend fun setGroupSourceColor(tableId: Long, groupId: String, hex: String) {
        captureForUndo()
        if (groupId.isBlank()) return
        val group = courseDao.getByGroupId(tableId, groupId)
            .filter { it.colorMode == com.lingion.sleepy.data.entity.CourseColorMode.GROUP }
        if (group.isEmpty()) return
        courseDao.updateAll(group.map { it.copy(color = hex) })
        onDataChanged()
    }

    suspend fun countCourses(tableId: Long): Int = courseDao.countByTable(tableId)

    suspend fun totalCourseCount(): Int = courseDao.totalCount()

    /** 覆盖式导入（先删后插） */
    suspend fun replaceCourses(tableId: Long, courses: List<CourseEntity>) {
        captureForUndo()
        // 认亲（spec §2.4）: 名册在删前取 — 被覆盖表自己的旧同名行也是认亲对象,
        // 重导入同名课 groupId/hue 双继承 → 覆盖导入颜色不变。
        val withGroupIds = assignGroupIds(courses, knownNameToGroupId(courseDao.getAll()))
        courseDao.replaceAll(tableId, withGroupIds)
        com.lingion.sleepy.util.CourseColorWriteGlue.persistNewGroupHues(
            SleepyApp.get(), db.courseColorDao(), withGroupIds
        )
        onDataChanged()
        pruneDefaultTopPrefs()
    }

    /**
     * v7.10.16p: 课程集变化后清理指向已失效课程的置顶偏好 —
     * repId 已删/键已不存在(锚课被删·簇解体)的条目静默失效还会画出幽灵图层选项,
     * 这里按现存课全量校验删除。删课/删组/覆盖导入/撤销四条写路径都会走到。
     */
    private suspend fun pruneDefaultTopPrefs() {
        val ctx = SleepyApp.get()
        val stored = AppPrefs.getConflictDefaultTop(ctx)
        if (stored.isEmpty()) return
        val allCourses = courseDao.getAll()
        // 用户报障 2026-09-10: liveKeys 与网格聚簇同一时间域, 偏好不被节点域误删
        val timeJson = tableDao.getDefault()?.timeJson
        val pruned = ConflictLayoutEngine.pruneConflictDefaultTop(stored, allCourses, timeJson)
        if (pruned.size != stored.size) {
            AppPrefs.setConflictDefaultTop(ctx, pruned)
        }
    }

    /**
     * 数据变更后：刷新所有 widget，并在提醒开启时重排通知（含流体云）。
     * 修复：之前只刷 widget 不重排通知，导致编辑课表后课前提醒/流体云仍按旧时间。
     */
    private suspend fun onDataChanged() {
        val app = SleepyApp.get()
        WidgetUpdater.notifyDataChanged(app)
        try {
            com.lingion.sleepy.widget.notification.ReminderRescheduler.request()
        } catch (_: Throwable) {
            // 提醒未开启或调度失败不应影响写操作本身
        }
    }

    private fun assignGroupIds(
        courses: List<CourseEntity>,
        knownNameToGroup: Map<String, String> = emptyMap()
    ): List<CourseEntity> = normalizedImportGroups(courses, knownNameToGroup)
}
