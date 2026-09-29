package com.lingion.sleepy.data.migration

import android.content.SharedPreferences
import com.lingion.sleepy.data.dao.CourseDao
import com.lingion.sleepy.data.dao.ImportDraftDao
import com.lingion.sleepy.data.dao.PeriodTableDao
import com.lingion.sleepy.data.dao.TimeTableDao

/**
 * 全量迁移编排层: 把协议层 (ZIP) 与各模块 codec (prefs/database) 接起来。
 *
 * 约束 (设计文档 §导出/导入流程):
 * - 导出选中 DATABASE 自动带上 PERSISTED_STATE 依赖闭包;
 * - 导入先完整读包校验, 坏包在写任何本机数据前被拒绝;
 * - 包内缺失的文件/模块不动本机 (设备自有设置优先于默认);
 * - CREDENTIALS 枚举保留但当前两端均无内容, 暂不进包。
 */
object MigrationExecutor {

    /** prefs 文件访问抽象, 便于纯 JVM 测试注入。 */
    fun interface PrefsStore {
        fun open(fileName: String): SharedPreferences
    }

    enum class ImportMode { OVERWRITE, MERGE }

    data class ExportResult(
        val bytes: ByteArray,
        val modules: List<MigrationModule>,
        val manifest: MigrationBackup.Manifest,
    )

    data class ImportCounts(
        val periodTables: Int = 0,
        val timeTables: Int = 0,
        val courses: Int = 0,
        val importDrafts: Int = 0,
        val prefFiles: Int = 0,
        val prefKeys: Int = 0,
    )

    data class ImportReport(
        val mode: ImportMode,
        val appliedModules: List<MigrationModule>,
        val unknownModules: List<String>,
        val counts: ImportCounts,
    )

    suspend fun export(
        selected: Set<MigrationModule>,
        prefsStore: PrefsStore,
        courseDao: CourseDao,
        timeTableDao: TimeTableDao,
        periodTableDao: PeriodTableDao,
        importDraftDao: ImportDraftDao,
        createdAt: Long = System.currentTimeMillis(),
    ): ExportResult {
        val modules = MigrationModuleDeps.resolve(selected)
            .filter { it != MigrationModule.CREDENTIALS } // 暂无内容, 不进包
            .sortedBy { it.ordinal }
        val payloads = LinkedHashMap<MigrationModule, String>()
        for (module in modules) {
            payloads[module] = when (module) {
                MigrationModule.DATABASE -> MigrationDatabaseCodec.encode(
                    MigrationDatabaseCodec.collect(courseDao, timeTableDao, periodTableDao, importDraftDao),
                )
                MigrationModule.PREFERENCES,
                MigrationModule.WIDGETS,
                MigrationModule.PERSISTED_STATE,
                -> MigrationPrefsCodec.encode(collectPrefs(prefsStore, module))
                MigrationModule.CREDENTIALS -> continue
            }
        }
        val manifest = MigrationBackup.Manifest(createdAt = createdAt, modules = payloads.keys.map { it.entryName })
        return ExportResult(
            bytes = MigrationPackageCodec.write(payloads, createdAt),
            modules = payloads.keys.toList(),
            manifest = manifest,
        )
    }

    private fun collectPrefs(prefsStore: PrefsStore, module: MigrationModule): MigrationPrefsCodec.PrefsSnapshot {
        val files = LinkedHashMap<String, MigrationPrefsCodec.PrefsFileSnapshot>()
        for (fileName in MigrationPrefsFileMap.filesOf(module)) {
            // 空 prefs 文件不进包: 无信息量, 且让接收端保留自有设置 (设备设置优先于默认)
            val prefs = prefsStore.open(fileName)
            if (prefs.all.isNotEmpty()) files[fileName] = MigrationPrefsCodec.collect(prefs)
        }
        return MigrationPrefsCodec.PrefsSnapshot(files)
    }

    suspend fun import(
        bytes: ByteArray,
        mode: ImportMode,
        prefsStore: PrefsStore,
        courseDao: CourseDao,
        timeTableDao: TimeTableDao,
        periodTableDao: PeriodTableDao,
        importDraftDao: ImportDraftDao,
    ): ImportReport {
        // 先完整校验并解码, 任何坏包都在触碰本机数据之前失败
        val content = MigrationPackageCodec.read(bytes)
        var counts = ImportCounts()
        val applied = mutableListOf<MigrationModule>()

        for ((module, payload) in content.modules.entries.sortedBy { it.key.ordinal }) {
            when (module) {
                MigrationModule.DATABASE -> {
                    val snapshot = MigrationDatabaseCodec.decode(payload)
                    when (mode) {
                        ImportMode.OVERWRITE ->
                            MigrationDatabaseCodec.applyOverwrite(snapshot, courseDao, timeTableDao, periodTableDao, importDraftDao)
                        ImportMode.MERGE ->
                            MigrationDatabaseCodec.applyMerge(snapshot, courseDao, timeTableDao, periodTableDao, importDraftDao)
                    }
                    counts = counts.copy(
                        periodTables = snapshot.periodTables.size,
                        timeTables = snapshot.timeTables.size,
                        courses = snapshot.courses.size,
                        importDrafts = snapshot.importDrafts.size,
                    )
                    applied += module
                }

                MigrationModule.PREFERENCES,
                MigrationModule.WIDGETS,
                MigrationModule.PERSISTED_STATE,
                -> {
                    val snapshot = MigrationPrefsCodec.decode(payload)
                    // 包内缺失的 prefs 文件不动本机; 包内文件按 mode 决定整文件替换或同名覆盖
                    for ((fileName, fileSnapshot) in snapshot.files) {
                        MigrationPrefsCodec.apply(prefsStore.open(fileName), fileSnapshot, merge = mode == ImportMode.MERGE)
                    }
                    counts = counts.copy(
                        prefFiles = counts.prefFiles + snapshot.files.size,
                        prefKeys = counts.prefKeys + snapshot.files.values.sumOf { it.entries.size },
                    )
                    applied += module
                }

                MigrationModule.CREDENTIALS -> continue // 未知历史内容, 忽略
            }
        }
        return ImportReport(
            mode = mode,
            appliedModules = applied.sortedBy { it.ordinal },
            unknownModules = content.unknownModules,
            counts = counts,
        )
    }
}
