package com.lingion.sleepy.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移清单 — 任何 schema 改动必须在此登记一条 Migration,
 * 然后在 [ALL_MIGRATIONS] 数组里挂上,再把 [AppDatabase] 的 version 推一档。
 *
 * 严禁 fallbackToDestructiveMigration 兜底 — 任意版本升级会清空用户课表。
 *
 * 版本变更记录:
 *   v3 → v4: 加 courses.colorMode 字段 (issue#22 同名课程多地点修复)
 *     - 列: INTEGER NOT NULL DEFAULT 0
 *     - 旧库所有行 colorMode=0 (GROUP 模式) → 渲染行为完全不变
 *   v4 → v5: 加 courses.isIrregularNode / isIrregularTime (issue#23 逐卡非常规重构)
 *     - 两列: INTEGER NOT NULL DEFAULT 0
 *     - ownTime=1 旧行 isIrregularTime=1 (旧「自定义时间」语义并入新标志)
 *     - isIrregularNode 由 startNode 是否为边缘编号推导, 保存时回写防漂移
 *   v5 → v6: 加 courses.alias (issue#26 课程别名)
 *     - 列: TEXT NOT NULL DEFAULT ''
 *     - 旧库所有行 alias='' → 处处显示原名, 行为不变
 *   v6 → v7: 加 import_drafts 导入草稿快照表 (issue#39)
 *     - 草稿由显式 id 标识, payloadJson 保持导入预览数据的演进空间
 *   v7 → v8: 独立时间节次表 (issue#40)
 *     - 新表 period_tables (id/name/nodesPerDay/timeJson/smartConfigJson/createdAt/updatedAt)
 *     - 加 time_tables.periodTableId (INTEGER, 可空)
 *     - 迁移: 每张旧课表生成一张独立时间节次表(继承 timeJson/smartConfigJson/nodesPerDay),
 *       periodTableId 指过去 — 旧用户课表不意外共享同一份作息
 *     - 旧 timeJson/smartConfigJson/nodesPerDay 保留为兼容列(异常回退)
 *   v8 → v9: C2 换绑前快照 (issue#40, 2026-09-20)
 *     - 加 time_tables.preBindSnapshotJson (TEXT NOT NULL DEFAULT '')
 *     - 旧行空串 = 无快照; 解绑回退兼容列(旧镜像语义)仅在快照缺失时兜底
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            ALTER TABLE courses
            ADD COLUMN colorMode INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
    }
}

val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE courses ADD COLUMN isIrregularNode INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE courses ADD COLUMN isIrregularTime INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE courses SET isIrregularTime = 1 WHERE ownTime = 1")
    }
}

val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_5_6_STATEMENTS.forEach { db.execSQL(it) }
    }
}

val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_6_7_STATEMENTS.forEach { db.execSQL(it) }
    }
}

val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_7_8_SCHEMA_STATEMENTS.forEach { db.execSQL(it) }

        // Keep the old timetable ids in the new table. This makes the backfill
        // deterministic and lets the following UPDATE be checked row by row.
        db.query(
            """
            SELECT id, name, nodesPerDay, timeJson, smartConfigJson, createdAt
            FROM time_tables
            ORDER BY id
            """.trimIndent()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1)
                val nodesPerDay = cursor.getInt(2)
                val timeJson = cursor.getString(3)
                val smartConfigJson = cursor.getString(4)
                val createdAt = cursor.getLong(5)
                db.execSQL(
                    """
                    INSERT INTO period_tables
                      (id, name, nodesPerDay, timeJson, smartConfigJson, createdAt, updatedAt)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(id, name, nodesPerDay, timeJson, smartConfigJson, createdAt, createdAt)
                )
                db.execSQL(
                    "UPDATE time_tables SET periodTableId = ? WHERE id = ?",
                    arrayOf(id, id)
                )
            }
        }
    }
}

/**
 * v8 → v9: C2 换绑不覆盖快照列 (2026-09-20, issue#40 用户数据破坏修复)
 *   - 加 time_tables.preBindSnapshotJson (TEXT NOT NULL DEFAULT '')
 *   - 纯加列, 旧行默认空串 = 无快照(未绑过表/旧版本升级, 行为不变)
 */
val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_8_9_STATEMENTS.forEach { db.execSQL(it) }
    }
}

/** v9 → v10: retain mappings for calendar events created by Sleepy. */
val MIGRATION_9_10: Migration = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_9_10_STATEMENTS.forEach { db.execSQL(it) }
    }
}

internal val MIGRATION_9_10_STATEMENTS: List<String> = listOf(
    """
    CREATE TABLE IF NOT EXISTS calendar_import_records (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        tableId INTEGER NOT NULL,
        courseId INTEGER NOT NULL,
        eventDate TEXT NOT NULL,
        calendarId INTEGER NOT NULL,
        eventId INTEGER NOT NULL,
        batchId TEXT NOT NULL,
        fingerprint TEXT NOT NULL,
        alarmRequested INTEGER NOT NULL,
        createdAt INTEGER NOT NULL
    )
    """.trimIndent(),
    "CREATE UNIQUE INDEX IF NOT EXISTS index_calendar_import_records_tableId_courseId_eventDate_calendarId ON calendar_import_records (tableId, courseId, eventDate, calendarId)",
    "CREATE INDEX IF NOT EXISTS index_calendar_import_records_tableId ON calendar_import_records (tableId)",
    "CREATE INDEX IF NOT EXISTS index_calendar_import_records_batchId ON calendar_import_records (batchId)"
)

/** v8→v9 的静态 schema SQL — 迁移契约测试与 Room 共用同一份 */
internal val MIGRATION_8_9_STATEMENTS: List<String> = listOf(
    "ALTER TABLE time_tables ADD COLUMN preBindSnapshotJson TEXT NOT NULL DEFAULT ''"
)

/** 当前已注册的全部 Migration — AppDatabase.Companion.get() 链入 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10
)

/** issue#26: v5→v6 逐条 SQL — 单一事实来源, CourseAliasMigrationTest 用 sqlite-jdbc 直接执行同一份 */

internal val MIGRATION_5_6_STATEMENTS: List<String> = listOf(
    "ALTER TABLE courses ADD COLUMN alias TEXT NOT NULL DEFAULT ''"
)

/** v6→v7 SQL is shared with the JVM migration contract test (issue#39 import_drafts). */
internal val MIGRATION_6_7_STATEMENTS: List<String> = listOf(
    """
    CREATE TABLE IF NOT EXISTS import_drafts (
        id TEXT NOT NULL PRIMARY KEY,
        sourceType TEXT NOT NULL DEFAULT '',
        sourceUrl TEXT NOT NULL DEFAULT '',
        payloadJson TEXT NOT NULL,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL
    )
    """.trimIndent(),
)

/** v7→v8 的静态 schema SQL — 迁移测试与 Room 共用同一份 SQL (issue#40)。 */
internal val MIGRATION_7_8_SCHEMA_STATEMENTS: List<String> = listOf(
    """
    CREATE TABLE period_tables (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        name TEXT NOT NULL,
        nodesPerDay INTEGER NOT NULL,
        timeJson TEXT NOT NULL,
        smartConfigJson TEXT NOT NULL,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL )
    """.trimIndent(),
    "CREATE INDEX index_period_tables_createdAt ON period_tables(createdAt)",
    "ALTER TABLE time_tables ADD COLUMN periodTableId INTEGER"
)
