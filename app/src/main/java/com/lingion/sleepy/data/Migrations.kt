package com.lingion.sleepy.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移清单 — 任何 schema 改动必须在此登记一条 Migration,
 * 然后在 [ALL_MIGRATIONS] 数组里挂上,再把 [AppDatabase] 的 version 推一档。
 *
 * 严禁在 [AppDatabase] 用 `.fallbackToDestructiveMigration()` 兜底 — 任意版本升
 * 会清空用户课表。这是 pre-existing 隐患,issue#22 修复同时堵上。
 *
 * 版本变更记录:
 *   v3 → v4: 加 courses.colorMode 字段 (issue#22 同名课程多地点修复)
 *     - 列: INTEGER NOT NULL DEFAULT 0
 *     - 旧库所有行 colorMode=0 (GROUP 模式) → 渲染行为完全不变
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            ALTER TABLE courses
            ADD COLUMN colorMode INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
    }
}

/** 当前已注册的全部 Migration — AppDatabase.Companion.get() 链入 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_3_4)
