# Sleepy Issue #22 同名课程多地点修复 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复同名课程多地点被编辑时整组覆盖丢数据的 bug,新增每节时段独立的颜色(GROUP/AUTO/CUSTOM),并堵上 `fallbackToDestructiveMigration()` 的"任意版本升清库"隐患。

**Architecture:**
- 数据层:`CourseEntity` 加 `colorMode` 字段(0/1/2),迁移 v3→v4 显式 Migration 替换 `fallbackToDestructiveMigration()`
- 业务层:`RowKey` + `RowKeyDiffer` 行级 diff/patch 替换 `replaceGroup()` 整组覆盖;`CourseColorUtil` 扩展 3 态取色
- 编辑层:`AddCourseScreen` 把 `room/teacher/note/color` 从顶层挪到每个 block(因为同名多地点课每个节次可能有不同 room/teacher)
- 渲染层:`WeekGrid/WeekList/Widget/Todo 等调用 `pickCourseColor*` 的地方切到 `colorMode` 感知版
- 导入层:`ImportSheet` 同 `groupId` 多 `room` 时弹预警

**Tech Stack:** Kotlin 1.9+, Jetpack Compose, Room 2.x(已有),Android Gradle Plugin

## Global Constraints

- **Commit 邮箱**: 只能用 `lingion@hrbeu.edu.cn`;**禁止 `Co-Authored-By: Claude` 尾注**(违反 = 用户两次明示永久禁止)
- **分支**: 直接在 `main` 上做,每个 Task 5 commit 一次
- **Spec**: `docs/superpowers/specs/2026-09-07-sleepy-issue-22-same-name-multi-location-design.md`(已 commit 在 0585884)
- **测试基线**: `app/src/test/java/com/lingion/sleepy/CourseColorUtilTest.kt`、`GroupSlotsForEditTest.kt` 已有;新测试优先沿用现有文件
- **撤回**: `UndoManager` 用 `Row 全字段 json 序列化`,新字段自动纳入快照,不动
- **`assignGroupIds`** / **`ScheduleParser` 6 处 groupId = ""** 不动(契约一)
- **`replaceGroup()` / `deleteCourseGroup()` DAO 方法** 不删(`UpdateCourseScreen` 删除按钮仍用)
- **不动**: `UndoManager`、`ConflictDetailReporter`、`SleepyTheme.colors`、`CourseColorUtil.stableHue`(旧金色 hash 路径,新逻辑加 sibling 不删)
- **数据库迁移**: 必须显式 Migration,任何 fallbackToDestructiveMigration 必须替换掉
- **现有测试**: 902+ 全绿 baseline;新增/修改后必须 `./gradlew :app:testDebugUnitTest` 全绿

---

## Phase A: 数据层基础

### Task 1: CourseEntity 加 colorMode 字段

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/data/entity/CourseEntity.kt`
- Test: `app/src/test/java/com/lingion/sleepy/data/entity/CourseEntityTest.kt`(已存在)

**Interfaces:**
- Consumes: 无
- Produces: `CourseEntity.colorMode: Int = 0`(0=GROUP,1=AUTO,2=CUSTOM)

- [ ] **Step 1: 读现有 CourseEntity.kt 确认字段顺序**

读 `app/src/main/java/com/lingion/sleepy/data/entity/CourseEntity.kt`,确认 `@ColumnInfo(name = "color") val color: String` 字段在文件第 71 行。

- [ ] **Step 2: 添加 colorMode 字段**

在 `color` 字段**之后**插入:
```kotlin
/**
 * 颜色模式:
 *   0 = GROUP — 跟随整门课程组色(默认值,旧数据全部走这个)
 *   1 = AUTO  — 自动色,golden angle 137.508°,渲染时按行号实时算
 *   2 = CUSTOM — 自定义色,color 字段存十六进制
 */
@ColumnInfo(name = "colorMode", defaultValue = "0")
val colorMode: Int = 0,
```

注释要点:必须解释 `color` 字段在 `colorMode` 三个值的语义,引用 spec §5.1。

- [ ] **Step 3: 加枚举常量供业务层引用**

在 `CourseEntity.kt` 末尾(类体外)新增:
```kotlin
/** 颜色模式常量(数据库存整数,业务层用枚举语义引用) */
object CourseColorMode {
    const val GROUP = 0
    const val AUTO = 1
    const val CUSTOM = 2
}
```

- [ ] **Step 4: 补单元测试**

在 `CourseEntityTest.kt` 末尾添加:
```kotlin
@Test fun `default colorMode is GROUP`() {
    val c = CourseEntity(
        id = 1, groupId = "g", tableId = 1, courseName = "x",
        day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 16,
        color = "#FF6750A4"
    )
    assertEquals(0, c.colorMode)
    assertEquals(CourseColorMode.GROUP, c.colorMode)
}

@Test fun `explicit colorMode CUSTOM preserved`() {
    val c = CourseEntity(
        id = 1, groupId = "g", tableId = 1, courseName = "x",
        day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 16,
        color = "#FF123456", colorMode = CourseColorMode.CUSTOM
    )
    assertEquals(2, c.colorMode)
}
```

- [ ] **Step 5: 跑测试**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests "com.lingion.sleepy.data.entity.CourseEntityTest"
```
Expected: PASS,2 个新用例绿。

- [ ] **Step 6: Commit**

```bash
git -C /Users/lingion_k/sleepy add app/src/main/java/com/lingion/sleepy/data/entity/CourseEntity.kt app/src/test/java/com/lingion/sleepy/data/entity/CourseEntityTest.kt
git -C /Users/lingion_k/sleepy -c user.name=lingion -c user.email=lingion@hrbeu.edu.cn commit -m "feat(entity): CourseEntity 加 colorMode 字段 (GROUP/AUTO/CUSTOM)"
```

---

### Task 2: CourseDao 加 insertKeepId / deleteByIds

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/data/dao/CourseDao.kt`

**Interfaces:**
- Consumes: 现有 `CourseDao` 接口
- Produces: `insertKeepId(course)`(强制按 `course.id` 插入,保留指定 id),`deleteByIds(ids)`,`updateAll(courses)`

- [ ] **Step 1: 加 insertKeepId 方法**

在 `CourseDao` 接口中,`insertAll` 之后加:
```kotlin
/** 保留指定 id 的 insert(行级 diff/patch 用,确保旧行不丢 id) */
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertKeepId(course: CourseEntity): Long
```

- [ ] **Step 2: 加 deleteByIds 批量删除**

加:
```kotlin
@Query("DELETE FROM courses WHERE id IN (:ids)")
suspend fun deleteByIds(ids: List<Long>)
```

- [ ] **Step 3: 加 updateAll**

加:
```kotlin
@Update
suspend fun updateAll(courses: List<CourseEntity>)
```

- [ ] **Step 4: 编译验证**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git -C /Users/lingion_k/sleepy add app/src/main/java/com/lingion/sleepy/data/dao/CourseDao.kt
git -C /Users/lingion_k/sleepy -c user.name=lingion -c user.email=lingion@hrbeu.edu.cn commit -m "feat(dao): CourseDao 加 insertKeepId/deleteByIds/updateAll"
```

---

### Task 3: RowKey + RowKeyDiffer(TDD)

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/data/diff/RowKey.kt`
- Create: `app/src/main/java/com/lingion/sleepy/data/diff/RowKeyDiffer.kt`
- Create: `app/src/main/java/com/lingion/sleepy/data/diff/DiffResult.kt`
- Test: `app/src/test/java/com/lingion/sleepy/data/diff/RowKeyDifferTest.kt`

**Interfaces:**
- Consumes: `List<CourseEntity>`(服务端当前),`List<CourseEntity>`(用户草稿)
- Produces: `DiffResult(toInsert, toUpdate, toDelete, keptGroupIds)`

**算法约定(spec §4.2)**:
- `RowKey = (day, startNode, step, startWeek, endWeek, type, room, teacher)`
- 按 groupId 分桶,每桶内:
  - draft 有 server key 匹配 → update(保留 server 的 id,覆写其他字段)
  - draft 无匹配 → insert(用 draft 自带 id=0 让库自增;若带 id 则强制按 id 插入)
  - server 无 draft key → delete(server.id)

- [ ] **Step 1: 写 RowKey.kt**

```kotlin
package com.lingion.sleepy.data.diff

import com.lingion.sleepy.data.entity.CourseEntity

/** 行身份键 — 区分"是不是同一行"的最小集合
 *  (room/teacher 进 key,因为同名多地点要视为不同行)
 */
data class RowKey(
    val day: Int,
    val startNode: Int,
    val step: Int,
    val startWeek: Int,
    val endWeek: Int,
    val type: Int,
    val room: String,
    val teacher: String
) {
    companion object {
        fun of(c: CourseEntity) = RowKey(
            day = c.day,
            startNode = c.startNode,
            step = c.step,
            startWeek = c.startWeek,
            endWeek = c.endWeek,
            type = c.type,
            room = c.room,
            teacher = c.teacher
        )
    }
}
```

- [ ] **Step 2: 写 DiffResult.kt**

```kotlin
package com.lingion.sleepy.data.diff

import com.lingion.sleepy.data.entity.CourseEntity

/** 行级 diff 结果 */
data class DiffResult(
    /** 新行(id=0,Room 自增;若 id>0 则按 id 插入) */
    val toInsert: List<CourseEntity>,
    /** 改字段但 RowKey 不变(包含 server 原 id) */
    val toUpdate: List<CourseEntity>,
    /** 被删的 server 行 id */
    val toDelete: List<Long>,
    /** 整组没动的 groupId(用于跳过空操作) */
    val keptGroupIds: Set<String> = emptySet()
)
```

- [ ] **Step 3: 写 failing 测试用例 (red)**

`RowKeyDifferTest.kt`:
```kotlin
package com.lingion.sleepy.data.diff

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Test

private fun course(
    id: Long = 0,
    gid: String = "g1",
    table: Long = 1,
    name: String = "x",
    teacher: String = "",
    room: String = "",
    day: Int = 1,
    startNode: Int = 1,
    step: Int = 1,
    startWeek: Int = 1,
    endWeek: Int = 16,
    type: Int = 0,
    colorMode: Int = 0,
    color: String = "#FF6750A4"
) = CourseEntity(
    id = id, groupId = gid, tableId = table, courseName = name,
    teacher = teacher, room = room, note = "",
    day = day, startNode = startNode, step = step,
    startWeek = startWeek, endWeek = endWeek, type = type,
    color = color, colorMode = colorMode
)

class RowKeyDifferTest {
    @Test fun `同名 3 个不同地点,server 3 行 → diff 空(server 草稿一致)`() {
        val server = listOf(
            course(id = 1, room = "A"), course(id = 2, room = "B"), course(id = 3, room = "C")
        )
        val draft = server.map { it.copy() }
        val diff = RowKeyDiffer.diff(draft, server)
        assertEquals(emptyList<CourseEntity>(), diff.toInsert)
        assertEquals(emptyList<CourseEntity>(), diff.toUpdate)
        assertEquals(emptyList<Long>(), diff.toDelete)
    }

    @Test fun `同名 3 行 server,草稿只改 A 楼地点 → 其他 2 行不动,A 行 update`() {
        val server = listOf(
            course(id = 1, room = "A"), course(id = 2, room = "B"), course(id = 3, room = "C")
        )
        val draft = listOf(
            course(id = 1, room = "A2"),  // 改 A 楼 → A2
            course(id = 2, room = "B"),   // 保持
            course(id = 3, room = "C")
        )
        val diff = RowKeyDiffer.diff(draft, server)
        assertEquals(emptyList<CourseEntity>(), diff.toInsert)
        assertEquals(1, diff.toUpdate.size)
        assertEquals(1L, diff.toUpdate[0].id)
        assertEquals("A2", diff.toUpdate[0].room)
        assertEquals(emptyList<Long>(), diff.toDelete)
    }

    @Test fun `删 1 行 → 其他 2 行不动,删的行 id 进 toDelete`() {
        val server = listOf(
            course(id = 1, room = "A"), course(id = 2, room = "B"), course(id = 3, room = "C")
        )
        val draft = listOf(
            course(id = 1, room = "A"), course(id = 3, room = "C")
            // id=2 被删
        )
        val diff = RowKeyDiffer.diff(draft, server)
        assertEquals(emptyList<CourseEntity>(), diff.toInsert)
        assertEquals(emptyList<CourseEntity>(), diff.toUpdate)
        assertEquals(listOf(2L), diff.toDelete)
    }

    @Test fun `增 1 行 → 新行进 toInsert(带原 tableId 与 groupId)`() {
        val server = listOf(
            course(id = 1, room = "A"), course(id = 2, room = "B")
        )
        val draft = server + course(id = 0, room = "D")  // 新行 id=0
        val diff = RowKeyDiffer.diff(draft, server)
        assertEquals(1, diff.toInsert.size)
        assertEquals(0L, diff.toInsert[0].id)
        assertEquals("D", diff.toInsert[0].room)
        assertEquals("g1", diff.toInsert[0].groupId)
        assertEquals(emptyList<CourseEntity>(), diff.toUpdate)
        assertEquals(emptyList<Long>(), diff.toDelete)
    }

    @Test fun `跨 groupId 不互相影响 — A 组加行不影响 B 组`() {
        val server = listOf(
            course(id = 1, gid = "g1", room = "A"),
            course(id = 2, gid = "g2", room = "B")
        )
        val draft = listOf(
            course(id = 1, gid = "g1", room = "A"),
            course(id = 2, gid = "g2", room = "B"),
            course(id = 0, gid = "g1", room = "A2")  // 新加到 g1
        )
        val diff = RowKeyDiffer.diff(draft, server)
        assertEquals(1, diff.toInsert.size)
        assertEquals("g1", diff.toInsert[0].groupId)
        assertEquals(emptyList<Long>(), diff.toDelete)
    }

    @Test fun `row key 比较不含 colorMode 和 note(改字段不视为新行)`() {
        val server = listOf(
            course(id = 1, room = "A", colorMode = 0, color = "#FF111111")
        )
        val draft = listOf(
            course(id = 1, room = "A", colorMode = 1, color = "")  // colorMode 改了
        )
        val diff = RowKeyDiffer.diff(draft, server)
        assertEquals(emptyList<CourseEntity>(), diff.toInsert)
        assertEquals(1, diff.toUpdate.size)
        assertEquals(1L, diff.toUpdate[0].id)
    }
}
```

- [ ] **Step 4: 跑测试 → 期待 FAIL(red)**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests "com.lingion.sleepy.data.diff.RowKeyDifferTest"
```
Expected: FAIL with "Unresolved reference: RowKeyDiffer"

- [ ] **Step 5: 写 RowKeyDiffer 实现(green)**

```kotlin
package com.lingion.sleepy.data.diff

import com.lingion.sleepy.data.entity.CourseEntity

object RowKeyDiffer {

    fun diff(drafts: List<CourseEntity>, server: List<CourseEntity>): DiffResult {
        val inserts = mutableListOf<CourseEntity>()
        val updates = mutableListOf<CourseEntity>()
        val deletes = mutableListOf<Long>()

        // 按 groupId 分桶
        val serverByGroup = server.groupBy { it.groupId }
        val draftByGroup = drafts.groupBy { it.groupId }

        val allGroups = serverByGroup.keys + draftByGroup.keys

        for (gid in allGroups) {
            if (gid.isBlank()) {
                // 旧 groupId="" 兜底(契约一保护): 整组 insert(走原 insertAll 路径)
                val s = serverByGroup[gid].orEmpty()
                val d = draftByGroup[gid].orEmpty()
                // 视为全新一批,draft 全 insert,server 全 delete
                inserts += d.map { it.copy(id = 0) }
                deletes += s.map { it.id }
                continue
            }

            val s = serverByGroup[gid].orEmpty()
            val d = draftByGroup[gid].orEmpty()

            val sKeyToCourse: Map<RowKey, CourseEntity> =
                s.associateBy(RowKey::of)
            val dKeyToCourse: Map<RowKey, CourseEntity> =
                d.associateBy(RowKey::of)

            for ((key, draftCourse) in dKeyToCourse) {
                val serverCourse = sKeyToCourse[key]
                if (serverCourse == null) {
                    inserts += draftCourse.copy(id = 0)
                } else if (fieldsDiffer(serverCourse, draftCourse)) {
                    updates += draftCourse.copy(id = serverCourse.id)
                }
                // else: 完全相同 → 跳过
            }
            for ((key, serverCourse) in sKeyToCourse) {
                if (key !in dKeyToCourse) {
                    deletes += serverCourse.id
                }
            }
        }

        return DiffResult(
            toInsert = inserts,
            toUpdate = updates,
            toDelete = deletes
        )
    }

    /** 比较除 id/groupId/RowKey 包含的字段外的其他字段是否相同 */
    private fun fieldsDiffer(a: CourseEntity, b: CourseEntity): Boolean {
        if (a.courseName != b.courseName) return true
        if (a.note != b.note) return true
        if (a.color != b.color) return true
        if (a.colorMode != b.colorMode) return true
        if (a.ownTime != b.ownTime) return true
        if (a.startTime != b.startTime) return true
        if (a.endTime != b.endTime) return true
        if (a.credit != b.credit) return true
        if (a.level != b.level) return true
        if (a.tableId != b.tableId) return true
        return false
    }
}
```

- [ ] **Step 6: 跑测试 → 期待 PASS(green)**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests "com.lingion.sleepy.data.diff.RowKeyDifferTest"
```
Expected: PASS,6 个用例全绿。

- [ ] **Step 7: Commit**

```bash
git -C /Users/lingion_k/sleepy add app/src/main/java/com/lingion/sleepy/data/diff app/src/test/java/com/lingion/sleepy/data/diff
git -C /Users/lingion_k/sleepy -c user.name=lingion -c user.email=lingion@hrbeu.edu.cn commit -m "feat(diff): RowKey + RowKeyDiffer 行级 diff/patch (TDD 6 用例绿)"
```

---

### Task 4: ScheduleRepository.applyDiff 事务化

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/data/repository/ScheduleRepository.kt`

**Interfaces:**
- Consumes: `DiffResult`(来自 RowKeyDiffer)
- Produces: `suspend fun applyDiff(tableId: Long, diff: DiffResult)` — 一个 db.withTransaction 内完成

- [ ] **Step 1: 注入 withTransaction**

确认 `ScheduleRepository.kt:8` 已 import `androidx.room.withTransaction`。

- [ ] **Step 2: 加 applyDiff 方法**

在 `replaceCourses` 之后追加:
```kotlin
/**
 * 行级 diff/patch 落库(整组替换的替代方案 — 编辑同名多地点时用)
 * 事务;执行前 captureForUndo 已在 caller 做完。
 */
suspend fun applyDiff(tableId: Long, diff: DiffResult) {
    if (diff.toDelete.isNotEmpty()) courseDao.deleteByIds(diff.toDelete)
    if (diff.toUpdate.isNotEmpty()) courseDao.updateAll(diff.toUpdate)
    if (diff.toInsert.isNotEmpty()) courseDao.insertAll(diff.toInsert)
    onDataChanged()
}
```

要点:不包 `db.withTransaction { }`,因为 `onDataChanged()` 里有 `WidgetUpdater` 与 `NotificationScheduler` 的 IO 调用,**不应该**放进事务,避免持锁阻塞。但单条 SQL(insertAll/updateAll/deleteByIds)在 SQL 层已经是事务边界。

- [ ] **Step 3: 暴露必要的查询方法**

在 `CourseDao` 接口(Task 2 已加 updateAll/insertAll/deleteByIds,无需新增)和 `ScheduleRepository` 中加 helper:

```kotlin
/** 编辑前: 拿全表当前所有行(供 RowKeyDiffer.diff 用) */
suspend fun getCourses(tableId: Long): List<CourseEntity> =
    courseDao.getByTable(tableId)
```

注:`getCourses(tableId)` 在 §124 已存在,无需新增。

- [ ] **Step 4: 编译验证**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 全测试基线**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest
```
Expected: 全部 902+ 测试 PASS(Task 3 新增 6 个也在内)。

- [ ] **Step 6: Commit**

```bash
git -C /Users/lingion_k/sleepy add app/src/main/java/com/lingion/sleepy/data/repository/ScheduleRepository.kt
git -C /Users/lingion_k/sleepy -c user.name=lingion -c user.email=lingion@hrbeu.edu.cn commit -m "feat(repo): applyDiff 行级落库(整组替换的替代)"
```

---

### Task 5: AppDatabase 替换 fallbackToDestructiveMigration + 注册 v3→v4 迁移

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/data/AppDatabase.kt`
- Create: `app/src/main/java/com/lingion/sleepy/data/Migrations.kt`

**Interfaces:**
- Consumes: `Migration(3, 4)` 显式 ALTER TABLE
- Produces: `AppDatabase.get()` 不再 fallbackToDestructiveMigration,版本升 3 → 4

- [ ] **Step 1: 创建 Migrations.kt**

```kotlin
package com.lingion.sleepy.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 迁移清单 — 任何 schema 改动必须在此登记,绝不允许 fallbackToDestructiveMigration 暗清库。
 *
 * v3 → v4: 加 courses.colorMode 字段(同名课程多地点 #22 修复)
 *   - 列: INTEGER NOT NULL DEFAULT 0
 *   - 旧库所有行 colorMode=0(GROUP 模式)→ 渲染行为完全不变
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            ALTER TABLE courses
            ADD COLUMN colorMode INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
    }
}

/** 当前已注册的全部 Migration,AppDatabase.Companion.get() 链入 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_3_4)
```

- [ ] **Step 2: 修改 AppDatabase.kt**

完整重写为:
```kotlin
package com.lingion.sleepy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lingion.sleepy.data.dao.CourseDao
import com.lingion.sleepy.data.dao.TimeTableDao
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity

@Database(
    entities = [CourseEntity::class, TimeTableEntity::class],
    version = 4,                              // 3 → 4: 加 courses.colorMode
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun timeTableDao(): TimeTableDao

    companion object {
        private const val DB_NAME = "sleepy.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    // 严禁 fallbackToDestructiveMigration — 任意版本升清空用户课表
                    .build()
                    .also { instance = it }
            }
        }
    }
}
```

要点:删 `.fallbackToDestructiveMigration()`,替换为 `.addMigrations(*ALL_MIGRATIONS)`。version 改 3 → 4。

- [ ] **Step 3: 编译验证**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 全测试基线**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest
```
Expected: 全绿。`Courses179CrossValidationTest` 等可能加载 Room in-memory 库,确保不因 migration 缺失报错。

- [ ] **Step 5: Commit**

```bash
git -C /Users/lingion_k/sleepy add app/src/main/java/com/lingion/sleepy/data/AppDatabase.kt app/src/main/java/com/lingion/sleepy/data/Migrations.kt
git -C /Users/lingion_k/sleepy -c user.name=lingion -c user.email=lingion@hrbeu.edu.cn commit -m "fix(db): 替换 fallbackToDestructiveMigration 为显式 Migration 3→4"
```

---

## Phase B: 颜色模型

### Task 6: CourseColorUtil 扩展 3 态取色 + GoldenAngleColor

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/util/CourseColorUtil.kt`
- Test: `app/src/test/java/com/lingion/sleepy/CourseColorUtilTest.kt`(已存在)

**Interfaces:**
- Consumes: `CourseEntity`(含 colorMode)、`List<CourseEntity>`(同 groupId 行)、`isDark`、`neutralColor`、`colorless`
- Produces: `pickCourseColorCompose/Int(row, groupRows, isDark, neutral, colorless)`(签名扩展 groupRows)

- [ ] **Step 1: 在 CourseColorUtil 末尾追加 GoldenAngleColor 子对象**

```kotlin
/**
 * 一行内的自动色算法 — 黄金角 137.508° 在 HSV 色环上按行号推进。
 * 不落库: 仅用 row 自身 id + 同组行 id 顺序 + 组色源色相 算色相,确定性重算。
 */
object GoldenAngleColor {
    private const val GOLDEN_ANGLE_DEG = 137.508f
    private const val SATURATION_LIGHT = 0.55f
    private const val SATURATION_DARK = 0.40f
    private const val LIGHTNESS_LIGHT = 0.82f
    private const val LIGHTNESS_DARK = 0.28f

    /**
     * @param row 目标行(只读,用于取 id)
     * @param groupRows 同 groupId 的所有行(含 row 自身),调用方负责保证
     * @param groupSourceColorHex 组色源行的 color 字段值(可空/可空字符串)
     */
    fun forRow(row: CourseEntity, groupRows: List<CourseEntity>, groupSourceColorHex: String?): Float {
        val baseHue = parseHexHue(groupSourceColorHex)
        val sorted = groupRows.sortedBy { it.id }
        val idx = sorted.indexOfFirst { it.id == row.id }
        val safeIdx = if (idx < 0) 0 else idx
        val hue = (((baseHue + safeIdx * GOLDEN_ANGLE_DEG) % 360f) + 360f) % 360f
        return hue
    }

    fun hsl(hue: Float, isDark: Boolean): Pair<Float, Float> =
        (if (isDark) SATURATION_DARK else SATURATION_LIGHT) to
            (if (isDark) LIGHTNESS_DARK else LIGHTNESS_LIGHT)

    private fun parseHexHue(hex: String?): Float {
        if (hex.isNullOrBlank()) return 0f
        return runCatching {
            val argb = android.graphics.Color.parseColor(hex)
            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(argb, hsl)
            hsl[0]
        }.getOrDefault(0f)
    }
}
```

- [ ] **Step 2: 找组色源**

在同一文件中加 helper:
```kotlin
/**
 * 组色源 — 同 groupId 内 colorMode=GROUP 中 id 最小的行的 color 字段
 * 若组内无 colorMode=GROUP(理论上 UI 阻止,防御性),退化为 id 最小的行的 color
 */
fun groupSourceColorHex(groupRows: List<CourseEntity>): String {
    val source = groupRows
        .filter { it.colorMode == com.lingion.sleepy.data.entity.CourseColorMode.GROUP }
        .minByOrNull { it.id }
        ?: groupRows.minByOrNull { it.id }
    return source?.color ?: ""
}
```

- [ ] **Step 3: 扩展 pickCourseColorCompose/Int 签名**

将现有两个方法签名替换为:
```kotlin
fun pickCourseColorCompose(
    row: CourseEntity,
    groupRows: List<CourseEntity>,
    isDark: Boolean,
    neutralColor: Color,
    colorless: Boolean = false
): Color

fun pickCourseColorInt(
    row: CourseEntity,
    groupRows: List<CourseEntity>,
    isDark: Boolean,
    neutralColorInt: Int,
    colorless: Boolean = false
): Int
```

实现逻辑:
```kotlin
fun pickCourseColorCompose(
    row: CourseEntity,
    groupRows: List<CourseEntity>,
    isDark: Boolean,
    neutralColor: Color,
    colorless: Boolean = false
): Color {
    return when (row.colorMode) {
        com.lingion.sleepy.data.entity.CourseColorMode.CUSTOM -> {
            runCatching { Color(android.graphics.Color.parseColor(row.color)) }
                .getOrElse { neutralColor }
        }
        com.lingion.sleepy.data.entity.CourseColorMode.AUTO -> {
            if (colorless) neutralColor
            else {
                val hue = GoldenAngleColor.forRow(row, groupRows, groupSourceColorHex(groupRows))
                val (s, l) = GoldenAngleColor.hsl(hue, isDark)
                hslToColor(hue, s, l)
            }
        }
        else -> {  // GROUP(0) 或异常值 fallback
            // 旧 hasCustomColor 路径(只在 GROUP 模式有意义 — AUTO 模式下 color 字段无意义)
            if (row.colorMode == com.lingion.sleepy.data.entity.CourseColorMode.GROUP && hasCustomColor(row)) {
                runCatching { return Color(android.graphics.Color.parseColor(row.color)) }
            }
            if (colorless) return neutralColor
            val hue = stableHue(row.groupId)
            val s = if (isDark) S_DARK else S_LIGHT
            val l = if (isDark) L_DARK else L_LIGHT
            hslToColor(hue, s, l)
        }
    }
}
```

`pickCourseColorInt` 同形态(Int 路径),不再展开。

- [ ] **Step 4: 加 failing 测试**

在 `CourseColorUtilTest.kt` 末尾:
```kotlin
@Test fun `pickCourseColorCompose GROUP 模式回退到 stableHue(无 groupRows 也能算)`() {
    val c = course(color = "#FF6750A4", colorMode = CourseColorMode.GROUP)
    val color = CourseColorUtil.pickCourseColorCompose(
        row = c, groupRows = listOf(c),
        isDark = false, neutralColor = Color.Gray, colorless = false
    )
    assertNotNull(color)
}

@Test fun `pickCourseColorCompose AUTO 模式 同组 3 行 baseHue=200 idx 0/1/2 hue 200/337/115`() {
    val srcColor = "#FFFF0000"  // 红, hue=0
    val rows = listOf(
        course(id = 1, groupId = "g", color = srcColor, colorMode = CourseColorMode.GROUP),
        course(id = 2, groupId = "g", color = "", colorMode = CourseColorMode.AUTO),
        course(id = 3, groupId = "g", color = "", colorMode = CourseColorMode.AUTO),
        course(id = 4, groupId = "g", color = "", colorMode = CourseColorMode.AUTO)
    )
    val row0 = rows[0]; val row1 = rows[1]; val row2 = rows[2]; val row3 = rows[3]
    // baseHue from srcColor=红 ≈ 0
    val h1 = GoldenAngleColor.forRow(row1, rows, srcColor)
    val h2 = GoldenAngleColor.forRow(row2, rows, srcColor)
    val h3 = GoldenAngleColor.forRow(row3, rows, srcColor)
    // idx 1: (0 + 1*137.508) % 360 = 137.508
    // idx 2: (0 + 2*137.508) % 360 = 275.016
    // idx 3: (0 + 3*137.508) % 360 = 412.524 % 360 = 52.524
    assertEquals(137.508f, h1, 0.5f)
    assertEquals(275.016f, h2, 0.5f)
    assertEquals(52.524f, h3, 0.5f)
}

@Test fun `GoldenAngleColor 确定性 — 相同输入产出相同 hue`() {
    val rows = listOf(
        course(id = 5, groupId = "g", color = "#FF112233", colorMode = CourseColorMode.GROUP),
        course(id = 10, groupId = "g", color = "", colorMode = CourseColorMode.AUTO)
    )
    val r = rows[1]
    val h1 = GoldenAngleColor.forRow(r, rows, "#FF112233")
    val h2 = GoldenAngleColor.forRow(r, rows, "#FF112233")
    assertEquals(h1, h2, 0.001f)
}

@Test fun `GoldenAngleColor baseHue 边界 — baseHue=350 idx=2 hue=265`() {
    val rows = listOf(
        course(id = 1, groupId = "g", color = "#FFFF00C8", colorMode = CourseColorMode.GROUP),  // hue≈350
        course(id = 2, groupId = "g", color = "", colorMode = CourseColorMode.AUTO)
    )
    val r = rows[1]
    val h = GoldenAngleColor.forRow(r, rows, "#FFFF00C8")
    // idx=1, baseHue≈350, hue = ((350 + 1*137.508) % 360 + 360) % 360 = ((487.508 % 360) + 360) % 360 = 127.508
    assertTrue(h > 100f && h < 150f)
}

@Test fun `pickCourseColorCompose CUSTOM 模式用 row.color 直接返回`() {
    val c = course(color = "#FF112233", colorMode = CourseColorMode.CUSTOM)
    val color = CourseColorUtil.pickCourseColorCompose(
        row = c, groupRows = listOf(c),
        isDark = false, neutralColor = Color.Gray
    )
    assertEquals(0xFF112233.toInt(), color.value.toArgb())
}
```

辅助函数:
```kotlin
private fun course(
    id: Long = 1,
    groupId: String = "g",
    color: String = "#FF6750A4",
    colorMode: Int = CourseColorMode.GROUP
): CourseEntity = CourseEntity(
    id = id, groupId = groupId, tableId = 1, courseName = "x",
    teacher = "", room = "", note = "",
    day = 1, startNode = 1, step = 1,
    startWeek = 1, endWeek = 16, type = 0,
    color = color, colorMode = colorMode
)
```

- [ ] **Step 5: 跑测试 → red(先失败,因为扩展签名会破坏现有调用)**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests "com.lingion.sleepy.CourseColorUtilTest"
```
Expected: 现有用例 compile error(签名不匹配)→ 这就是 RED 状态;正常。

- [ ] **Step 6: 跑全套测试**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest
```
Expected: 其他测试因编译错误 cascade 失败。需要 Phase C 之后(把所有调用点改成新签名)才能 PASS。

继续到 Task 7。

---

### Task 7: 渲染层调用点全切到新签名(groupRows 参数)

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/table/WeekGrid.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/table/WeekList.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/widget/WidgetBitmapRenderers.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/widget/WeekGridWidgetProvider.kt`
- 其它调用 `pickCourseColor*` 的文件(grep 找)

- [ ] **Step 1: grep 全部调用点**

```bash
cd /Users/lingion_k/sleepy && grep -rn "pickCourseColor" app/src/main/java
```
列出来的每个文件都要跟进。

- [ ] **Step 2: WeekGrid.kt 切签名**

读 `WeekGrid.kt` 找到 `pickCourseColorCompose(course, ...)` 调用处。改前要拿同 groupId 的所有行。最简方案:在 `WeekGrid` 外层有 `val allCourses: List<CourseEntity>`(week table 全表课程),调用前构造 `groupRows = allCourses.filter { it.groupId == course.groupId }`。

- [ ] **Step 3: WeekList.kt 同 Step 2**

- [ ] **Step 4: WidgetBitmapRenderers.kt 同 Step 2**

注意 widget 内已经有自己的 `allCourses` 缓存,直接复用。

- [ ] **Step 5: WeekGridWidgetProvider.kt 同 Step 2**

- [ ] **Step 6: 编译验证**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 7: 全测试基线**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest
```
Expected: 全绿(含 Task 6 新增用例)。

- [ ] **Step 8: Commit**

```bash
git -C /Users/lingion_k/sleepy add app/src/main/java/com/lingion/sleepy/util/CourseColorUtil.kt app/src/main/java/com/lingion/sleepy/ui/screen/table app/src/main/java/com/lingion/sleepy/widget app/src/test/java/com/lingion/sleepy/CourseColorUtilTest.kt
git -C /Users/lingion_k/sleepy -c user.name=lingion -c user.email=lingion@hrbeu.edu.cn commit -m "feat(color): CourseColorUtil 扩展 3 态取色 + 渲染层全切新签名"
```

---

## Phase C: 编辑器 UI

### Task 8: AddCourseScreen 把 room/teacher/note/color 下沉到每个 block

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt`(1398 行)

**Interfaces:**
- 现状: `room`/`teacher`/`note`/`courseColor` 是顶层 `var by remember` state
- 目标: 每个 `MeetingBlockDraft` 持有自己的 `room`/`teacher`/`note`/`color`/`colorMode`;顶层默认值从 `editingCourse` 继承(每个 block 创建时填)

- [ ] **Step 1: 在 MeetingBlockDraft 加字段**

```kotlin
private class MeetingBlockDraft(
    val id: Int,
    val days: androidx.compose.runtime.snapshots.SnapshotStateList<Int>,
    initialMode: MeetingInputMode,
    startNode: Int,
    step: Int,
    startTime: String,
    endTime: String,
    startWeek: Int = 1,
    endWeek: Int = 16,
    weekType: Int = 0,
    // 下沉自顶层的字段(每个 block 独立)
    var room: String = "",
    var teacher: String = "",
    var note: String = "",
    var color: String = "",       // "" = GROUP 模式;非空 = AUTO/CUSTOM 视 colorMode
    var colorMode: Int = 0        // 0=GROUP 1=AUTO 2=CUSTOM
) {
    // 既有 var ...
    var roomState by mutableStateOf(room)
    var teacherState by mutableStateOf(teacher)
    var noteState by mutableStateOf(note)
    var colorState by mutableStateOf(color)
    var colorModeState by mutableStateOf(colorMode)
    var clamped by mutableStateOf(false)
}
```

要点:同时保留构造参数(给初始化用)和 `*State` mutableState(给 UI 用),UI 改 state,save 时读 state。

- [ ] **Step 2: 删除顶层 room/teacher/note/courseColor 字段**

删 `AddCourseScreen.kt:140-147`:
```kotlin
var teacher by remember(editingCourse?.id) { mutableStateOf(editingCourse?.teacher ?: "") }
var room by remember(editingCourse?.id) { mutableStateOf(editingCourse?.room ?: "") }
var note by remember(editingCourse?.id) { mutableStateOf(editingCourse?.note ?: "") }
var courseColor by remember(editingCourse?.id) { ... }
```

替换为:删掉,改用 block 内的字段。

- [ ] **Step 3: initialMeetingBlock 接受默认 room/teacher/note/color**

```kotlin
private fun initialMeetingBlock(
    course: CourseEntity?,
    fallbackRoom: String = "",
    fallbackTeacher: String = "",
    fallbackNote: String = "",
    fallbackColor: String = "",
    fallbackColorMode: Int = 0
): MeetingBlockDraft {
    if (course == null) {
        return MeetingBlockDraft(
            id = 1,
            days = androidx.compose.runtime.snapshots.SnapshotStateList(1),
            initialMode = MeetingInputMode.ByNode,
            startNode = 1, step = 2,
            startTime = "08:00", endTime = "09:40",
            room = fallbackRoom, teacher = fallbackTeacher,
            note = fallbackNote, color = fallbackColor, colorMode = fallbackColorMode
        )
    }
    return MeetingBlockDraft(
        id = 1,
        days = mutableStateListOf(course.day),
        initialMode = if (course.ownTime) MeetingInputMode.ByClock else MeetingInputMode.ByNode,
        startNode = course.startNode, step = course.step,
        startTime = course.startTime.ifBlank { "08:00" },
        endTime = course.endTime.ifBlank { "09:40" },
        startWeek = course.startWeek, endWeek = course.endWeek,
        weekType = course.type,
        room = course.room, teacher = course.teacher,
        note = course.note, color = course.color, colorMode = course.colorMode
    )
}
```

- [ ] **Step 4: groupSlotsForEdit 回填每 block 用各自课程的 room/teacher**

```kotlin
internal fun groupSlotsForEdit(courses: List<CourseEntity>): List<List<CourseEntity>> =
    courses.groupBy { c ->
        "${c.ownTime}|${c.startNode}|${c.step}|${c.startTime}|${c.endTime}|${c.startWeek}|${c.endWeek}|${c.type}|${c.room}|${c.teacher}"
    }.values.toList()
```

要点:room/teacher 进分组 key,确保同名同周次不同地点回填成多个 block(而非合并)。

- [ ] **Step 5: LaunchedEffect 中用每个 course 的字段初始化 block:
```kotlin
LaunchedEffect(editingCourse?.groupId) {
    val eg = editingCourse
    if (eg != null && eg.groupId.isNotBlank()) {
        val tid = state.selectedTableId ?: return@LaunchedEffect
        val groupCourses = SleepyApp.get().repository.getGroupCourses(tid, eg.groupId)
        if (groupCourses.isNotEmpty()) {
            val slots = groupSlotsForEdit(groupCourses)
            meetingBlocks.clear()
            var bid = 1
            for (courses in slots) {
                val first = courses.first()
                meetingBlocks.add(MeetingBlockDraft(
                    id = bid++,
                    days = mutableStateListOf<Int>().apply {
                        addAll(courses.map { it.day }.distinct().sorted())
                    },
                    initialMode = if (first.ownTime) MeetingInputMode.ByClock else MeetingInputMode.ByNode,
                    startNode = first.startNode,
                    step = first.step,
                    startTime = first.startTime.ifBlank { "08:00" },
                    endTime = first.endTime.ifBlank { "09:40" },
                    startWeek = first.startWeek,
                    endWeek = first.endWeek,
                    weekType = first.type,
                    room = first.room,
                    teacher = first.teacher,
                    note = first.note,
                    color = first.color,
                    colorMode = first.colorMode
                ))
            }
        }
    }
}
```

- [ ] **Step 6: MeetingBlockEditor 加 room/teacher/note 编辑 UI**

在 `MeetingBlockEditor` 内(节次时段 `周次` 段之上)插入三个 TextField(room / teacher / note),值绑 `block.roomState` / `block.teacherState` / `block.noteState`,onValueChange 设回 state。

- [ ] **Step 7: 编译验证**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:compileDebugKotlin
```
Expected: 可能仍红(performSave 还在用顶层 room/teacher/note/courseColor)。继续 Step 8。

- [ ] **Step 8: 删顶层 Room/Teacher/Note/Color 的 UI 渲染**

删 `AddCourseScreen.kt:383-410` 整段(Room/Teacher/Note/Color 四个 TextField),因为这些字段已经下沉到 block 内。

- [ ] **Step 9: 编译验证**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:compileDebugKotlin
```
Expected: 仍可能红(performSave)。继续 Task 9。

- [ ] **Step 10: 全测试基线**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest
```
Expected: GroupSlotsForEditTest 可能因分组 key 改了而失败;同步更新该测试或确认现有断言仍合理。

更新点:`GroupSlotsForEditTest.kt` 现有用例测试"同节次不同周次回填成两个 block"。新增一个用例测试"同节次不同地点回填成两个 block"。

- [ ] **Step 11: Commit**

```bash
git -C /Users/lingion_k/sleepy add app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt app/src/test/java/com/lingion/sleepy/ui/screen/edit/GroupSlotsForEditTest.kt
git -C /Users/lingion_k/sleepy -c user.name=lingion -c user.email=lingion@hrbeu.edu.cn commit -m "refactor(edit): room/teacher/note/color 下沉到每个 block"
```

---

### Task 9: 颜色选择器改成 3 态开关 + 自动/自定义二选一

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt`

**Interfaces:**
- 现状: `AutoColorDot(selected = courseColor.isBlank(), ...)` + `CustomColorDot(...)`,在 AddCourseScreen 顶层
- 目标: 上述 UI 移到 `MeetingBlockEditor` 内(每节时段一行),加 "与当前课使用不同颜色" Switch + 自动/自定义 SegmentedSwitcher + 改组色按钮(只在 GROUP 模式显示)+ 关闭最后一行的阻止 toast

- [ ] **Step 1: 在 MeetingBlockEditor 内添加 color section**

参考 spec §6.1 的 UI 草图,实现:

```kotlin
@Composable
private fun ColorSection(
    block: MeetingBlockDraft,
    groupSourceColor: String,  // 供"跟随组色"显示
    onColorChange: (Int /* colorMode */, String /* color */) -> Unit,
    onChangeGroupColor: () -> Unit,
    canDisableGroupMode: Boolean
) {
    val colors = SleepyTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Switch: 与当前课使用不同颜色
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = block.colorModeState != CourseColorMode.GROUP,
                onCheckedChange = { on ->
                    if (!on && !canDisableGroupMode) {
                        // 阻止: 弹 toast(具体在 caller 实现)
                        return@Switch
                    }
                    onColorChange(
                        if (on) CourseColorMode.AUTO else CourseColorMode.GROUP,
                        if (on) "" else groupSourceColor
                    )
                }
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.color_use_different))
        }

        if (block.colorModeState == CourseColorMode.GROUP) {
            // 跟随组色行
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorSwatch(hex = groupSourceColor)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.color_follow_group))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onChangeGroupColor) {
                    Text(stringResource(R.string.change_group_color))
                }
            }
        } else {
            // 自动 / 自定义 二选一
            SegmentedSwitcher(
                options = listOf(
                    CourseColorMode.AUTO to stringResource(R.string.color_auto),
                    CourseColorMode.CUSTOM to stringResource(R.string.color_custom)
                ),
                selected = if (block.colorModeState == CourseColorMode.CUSTOM) CourseColorMode.CUSTOM else CourseColorMode.AUTO,
                onSelect = { mode ->
                    onColorChange(mode, if (mode == CourseColorMode.AUTO) "" else block.colorState)
                }
            )
            // 显示预览色
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorSwatch(hex = block.colorState.ifBlank { computePreviewAutoColorHex(block, groupSourceColor) })
                Spacer(Modifier.weight(1f))
                if (block.colorModeState == CourseColorMode.CUSTOM) {
                    TextButton(onClick = { /* 打开 ColorPickerDialog */ }) {
                        Text(stringResource(R.string.choose))
                    }
                } else {
                    TextButton(onClick = { /* 强制重算 */ }) {
                        Text(stringResource(R.string.color_refresh))
                    }
                }
            }
        }
    }
}
```

要点:`canDisableGroupMode` 由 caller 计算 = 组内除此 block 外还有至少 1 个 colorMode=GROUP。

- [ ] **Step 2: 加 strings.xml 资源**

`app/src/main/res/values/strings.xml` 加:
- `color_use_different` = "与当前课使用不同的课程颜色"
- `color_follow_group` = "跟随课程色"
- `change_group_color` = "改组色"
- `color_auto` = "自动"
- `color_custom` = "自定义"
- `color_refresh` = "刷新重算"
- `color_toast_last_group` = "至少一个节次需跟随课程色"

(具体文案查现有 strings.xml 风格,确保一致)

- [ ] **Step 3: 把 AutoColorDot / CustomColorDot 替换为 ColorSection**

`MeetingBlockEditor` 内 room/teacher/note 三个 TextField 之后,加 ColorSection。

删 `AddCourseScreen.kt` 顶层 `AutoColorDot` / `CustomColorDot` 的调用,以及顶层 `ColorPickerDialog`(改成 block 内调起)。

- [ ] **Step 4: ColorPickerDialog 接受回调签名**

```kotlin
@Composable
private fun ColorPickerDialog(
    initialHex: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
)
```
保留现有实现,只确保签名一致。

- [ ] **Step 5: 编译验证**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:compileDebugKotlin
```
Expected: 可能红(performSave 仍用顶层 courseColor);继续 Task 10。

- [ ] **Step 6: Commit**

```bash
git -C /Users/lingion_k/sleepy add app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt app/src/main/res/values/strings.xml
git -C /Users/lingion_k/sleepy -c user.name=lingion -c user.email=lingion@hrbeu.edu.cn commit -m "feat(edit): 颜色选择器改为 3 态开关(跟随/自动/自定义)"
```

---

### Task 10: 保存路径从 updateCourseGroup 切到 applyDiff

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/data/repository/ScheduleRepository.kt`

**Interfaces:**
- 现状: `performSave` 编辑模式 → `repo.updateCourseGroup(tableId, gid, fixedDrafts)`(整组覆盖)
- 目标: `performSave` 编辑模式 → 计算 `RowKeyDiffer.diff(fixedDrafts, repo.getCourses(tableId))` → `repo.applyDiff(tableId, diff)`

- [ ] **Step 1: 修改 performSave 编辑分支**

替换 `AddCourseScreen.kt:253-266`:
```kotlin
if (editingCourse != null) {
    // v7.10.16+: 行级 diff/patch 替换整组覆盖 — issue#22 同名多地点
    val existing = repo.getCourses(tableId)
        .filter { it.groupId == editingCourse.groupId }
    val diff = RowKeyDiffer.diff(fixedDrafts, existing)
    repo.applyDiff(tableId, diff)
} else {
    // 新建: 所有草稿共享同一个 groupId
    val gid = java.util.UUID.randomUUID().toString()
    repo.insertCourses(fixedDrafts.map { it.copy(groupId = gid) })
}
```

要点:仅同 groupId 内的行进入 diff(其他课程不参与)。

- [ ] **Step 2: buildCourseEntity 加 colorMode 参数**

`AddCourseScreen.kt:632` `buildCourseEntity` 加:
```kotlin
private fun buildCourseEntity(
    tableId: Long,
    groupId: String,
    courseName: String,
    block: MeetingBlockDraft,        // 从顶层字段下沉,改读 block
    day: Int
): CourseEntity {
    val ownTime = block.mode == MeetingInputMode.ByClock
    return CourseEntity(
        groupId = groupId,
        tableId = tableId,
        courseName = courseName.trim(),
        teacher = block.teacherState.trim(),
        room = block.roomState.trim(),
        note = block.noteState.trim(),
        day = day,
        startNode = block.startNode,
        step = block.step,
        startWeek = block.startWeek,
        endWeek = block.endWeek,
        type = block.weekType,
        color = when (block.colorModeState) {
            com.lingion.sleepy.data.entity.CourseColorMode.CUSTOM -> block.colorState.ifBlank { "#FF6750A4" }
            com.lingion.sleepy.data.entity.CourseColorMode.AUTO -> ""  // 不落最终色
            else -> block.colorState.ifBlank { "#FF6750A4" }
        },
        colorMode = block.colorModeState,
        ownTime = ownTime,
        startTime = if (ownTime) block.startTime else "",
        endTime = if (ownTime) block.endTime else ""
    )
}
```

注意:`teacher`/`room`/`note`/`color`/`colorMode` 全都从 block 内取,不再读顶层。

- [ ] **Step 3: performSave 内 drafts 构造**

```kotlin
val drafts = meetingBlocks.flatMap { block ->
    block.days.sorted().map { day ->
        buildCourseEntity(
            tableId = draftTableId ?: 0L,
            groupId = "",    // 编辑模式暂留 "", 由 applyDiff 走 groupId 桶
            courseName = courseName,
            block = block,
            day = day
        )
    }
}
```

编辑模式下,applyDiff 会按 groupId 分桶后再合并;新草稿必须带正确的 groupId:
```kotlin
val fixedDrafts = if (editingCourse != null) {
    drafts.map { it.copy(groupId = editingCourse.groupId) }
} else {
    drafts
}
```

- [ ] **Step 4: 编译验证**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 全测试基线**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest
```
Expected: 全绿。

- [ ] **Step 6: Commit**

```bash
git -C /Users/lingion_k/sleepy add app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt app/src/main/java/com/lingion/sleepy/data/repository/ScheduleRepository.kt
git -C /Users/lingion_k/sleepy -c user.name=lingion -c user.email=lingion@hrbeu.edu.cn commit -m "fix(edit): 保存路径走 applyDiff 行级 patch(替换整组覆盖)"
```

---

## Phase D: 导入预览与端到端

### Task 11: ImportSheet 同 groupId 多 room 预警

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/ImportSheet.kt`

**Interfaces:**
- Consumes: 解析出的 `List<CourseEntity>`(草稿)
- Produces: `ImportPreview` 多一项警告文案(不阻塞导入)

- [ ] **Step 1: 在 ImportPreview 旁加多地点警告计算**

读 ImportSheet.kt 找到 `ImportPreview` 数据类定义与构造点,加:
```kotlin
data class ImportPreview(
    // 既有字段
    val multiLocationWarnings: List<String> = emptyList()
)

// 计算 (per groupId):
val multiLocWarnings = mutableListOf<String>()
courses.groupBy { it.groupId }.forEach { (gid, cs) ->
    if (gid.isBlank()) return@forEach
    val distinctRooms = cs.map { it.room.trim() }.distinct().filter { it.isNotEmpty() }
    if (distinctRooms.size >= 2) {
        multiLocWarnings += "「${cs.first().courseName}」有 ${distinctRooms.size} 个不同上课地点,导入后会作为独立节次展示"
    }
}
```

- [ ] **Step 2: UI 渲染警告**

在 ImportPreview 列表内冲突明细之上加:
```kotlin
if (preview.multiLocationWarnings.isNotEmpty()) {
    CardSection(...) {
        Text(stringResource(R.string.import_multi_location_warning), style = titleSmall)
        preview.multiLocationWarnings.take(5).forEach { Text("• $it", style = bodySmall) }
        if (preview.multiLocationWarnings.size > 5) {
            Text(stringResource(R.string.more_unexpanded, preview.multiLocationWarnings.size - 5))
        }
    }
}
```

- [ ] **Step 3: strings.xml 加资源**

`app/src/main/res/values/strings.xml` 加:
- `import_multi_location_warning` = "部分课程存在多个上课地点"

- [ ] **Step 4: 编译 + 全测试**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest
```
Expected: 全绿。

- [ ] **Step 5: Commit**

```bash
git -C /Users/lingion_k/sleepy add app/src/main/java/com/lingion/sleepy/ui/screen/imports/ImportSheet.kt app/src/main/res/values/strings.xml
git -C /Users/lingion_k/sleepy -c user.name=lingion -c user.email=lingion@hrbeu.edu.cn commit -m "feat(import): ImportPreview 同 groupId 多地点预警"
```

---

### Task 12: 验收 + 端到端手工测试

**Files:** 无

- [ ] **Step 1: 全测试**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest
```
Expected: 全绿(902 + Task 3/6/8/10 新增 ≈ 910+ 用例)。

- [ ] **Step 2: lint**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:lintDebug
```
Expected: 无新增警告(若有,需手动排除)。

- [ ] **Step 3: assembleDebug**

```bash
cd /Users/lingion_k/sleepy && ./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 真机/模拟器手工测试矩阵**

按 spec §8.4 逐项验证:
- [ ] 导入含 3 个不同地点的同名课程 → 数据库 3 行(用 `adb shell run-as com.lingion.sleepy.debug cat /data/data/com.lingion.sleepy.debug/databases/sleepy.db > /sdcard/dump.db` 拉库)
- [ ] 编辑其中 1 行的地点 → 数据库 3 行不变(只该行被 update)
- [ ] 编辑其中 1 行的周次 → 数据库 3 行不变
- [ ] 删除其中 1 行 → 数据库剩 2 行
- [ ] 增加 1 个新节次(新 (day, node, room, teacher)) → 数据库 4 行
- [ ] 同 groupId 下,打开其中 1 行的开关 → 该行显示自动色
- [ ] 同 groupId 下,打开开关 + 选自定义 → 该行渲染自定义色
- [ ] 关闭所有开关 → UI 阻止(弹 toast)
- [ ] 撤回最近一次编辑 → 数据库回到编辑前状态
- [ ] 旧库升级:装 v1.0.50 → 升级到含本 fix 的版本 → 课仍按"组色"渲染(行为不变)

- [ ] **Step 5: 写 CHANGELOG 条目**

`/Users/lingion_k/sleepy/CHANGELOG.md` 加 issue #22 条目(中文,简短 1-3 句)。先放草稿,**等用户实测通过再最终化**。

- [ ] **Step 6: Commit 验收材料**

```bash
git -C /Users/lingion_k/sleepy add CHANGELOG.md
git -C /Users/lingion_k/sleepy -c user.name=lingion -c user.email=lingion@hrbeu.edu.cn commit -m "docs(changelog): issue#22 同名多地点修复条目"
```

---

## 验收清单(交付用户前自检)

- [ ] 12 个 Task 全部 commit 在 `main`(或当前 spec 分支)
- [ ] 全测试绿(910+)
- [ ] `app:lintDebug` 无新增警告
- [ ] `app:assembleDebug` 通过
- [ ] Task 12 Step 4 的 10 条手工用例全部 ✓
- [ ] CHANGELOG 草稿就绪
- [ ] 不存在 `Co-Authored-By: Claude` 尾注
- [ ] 不存在 `fallbackToDestructiveMigration` 调用
- [ ] Issue #22 commit 在 commit 历史中可查