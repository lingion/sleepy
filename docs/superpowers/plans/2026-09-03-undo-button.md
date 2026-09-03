# Schedule Undo Button Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 顶栏左二放撤回按钮——任何课表数据改动(导入覆盖/追加/新课表/加课/编辑/删课/删表)可一键撤销一次。

**Architecture:** 快照式单级 undo。`ScheduleRepository` 每个公开写方法执行前自动把全库状态(tables+courses+defaultId)存进 `UndoManager` 单例单槽;`restoreLastSnapshot()` 在 Room 事务里 deleteAll+重插+恢复 default。UI 零埋点——捕获在 repo 层拦截,未来新增写路径自动覆盖。

**Tech Stack:** Room(@Transaction/@Query)、Kotlin object 单例、Compose AlertDialog(无可撤回时 toast)。

## Global Constraints

- 快照仅存内存单槽(App 进程死=撤回失效),不落盘、不进 DB
- 撤回本身也是写操作,但 restore 期间禁止再捕获(防自我覆盖)
- 撤回后 widget/通知必须刷新(走 onDataChanged 同款通道)
- 无快照时点撤回: toast「没有可撤回的操作」, 按钮不隐藏(保持布局稳定)
- 禁 Claude 署名; commit 按 slice 分开

---

### Task 1: UndoManager 单例 + 快照数据类

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/data/undo/UndoManager.kt`
- Test: `app/src/test/java/com/lingion/sleepy/data/undo/UndoManagerTest.kt`

**Interfaces:**
- Produces:
  - `data class UndoSnapshot(val tables: List<TimeTableEntity>, val courses: List<CourseEntity>, val defaultTableId: Long?)`
  - `object UndoManager { val hasSnapshot: Boolean; fun capture(...): Unit; fun poll(): UndoSnapshot?; fun clear(): Unit }`

- [ ] **Step 1: 写失败测试**

```kotlin
class UndoManagerTest {
    @Test fun `capture stores single slot and poll drains it`() {
        UndoManager.clear()
        val t = TimeTableEntity(id = 1, name = "T", startDate = "2026-09-01")
        UndoManager.capture(listOf(t), emptyList(), 1L)
        check(UndoManager.hasSnapshot)
        val snap = UndoManager.poll()
        check(snap?.defaultTableId == 1L)
        check(!UndoManager.hasSnapshot)          // poll 即清空(单级)
        check(UndoManager.poll() == null)        // 二次 poll 为空
    }
    @Test fun `capture overwrites previous snapshot`() {
        UndoManager.clear()
        UndoManager.capture(emptyList(), emptyList(), null)
        UndoManager.capture(listOf(TimeTableEntity(id = 9, name = "N", startDate = "")), emptyList(), 9L)
        check(UndoManager.poll()?.tables?.single()?.id == 9L)
    }
}
```

- [ ] **Step 2: 跑测试确认失败** — `./gradlew :app:testDebugUnitTest --tests "*.UndoManagerTest"` → 编译错(UndoManager 不存在)

- [ ] **Step 3: 最小实现**

```kotlin
package com.lingion.sleepy.data.undo

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity

/** 改动前的全库快照 — tables+courses+默认表 id。 */
data class UndoSnapshot(
    val tables: List<TimeTableEntity>,
    val courses: List<CourseEntity>,
    val defaultTableId: Long?
)

/**
 * 单级撤回的快照槽(进程内单例)。
 * repo 写方法前 capture, 撤回时 poll 取走并清空 — 单级语义: 撤回不可再撤回。
 * restore 期间以 restoring 标志抑制捕获, 防止恢复动作自身生成快照。
 */
object UndoManager {
    @Volatile private var slot: UndoSnapshot? = null
    @Volatile var restoring: Boolean = false

    val hasSnapshot: Boolean get() = slot != null

    fun capture(tables: List<TimeTableEntity>, courses: List<CourseEntity>, defaultTableId: Long?) {
        if (restoring) return
        slot = UndoSnapshot(tables, courses, defaultTableId)
    }

    fun poll(): UndoSnapshot? {
        val s = slot
        slot = null
        return s
    }

    fun clear() { slot = null }
}
```

- [ ] **Step 4: 跑测试确认通过** — 同上命令 → PASS
- [ ] **Step 5: Commit** — `feat(undo): v7.10.16 — UndoManager 单级快照槽`

### Task 2: DAO deleteAll + repo 拦截捕获与 restore

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/data/dao/TimeTableDao.kt`(加 deleteAll)
- Modify: `app/src/main/java/com/lingion/sleepy/data/dao/CourseDao.kt`(加 deleteAll)
- Modify: `app/src/main/java/com/lingion/sleepy/data/repository/ScheduleRepository.kt`

**Interfaces:**
- Consumes: Task 1 的 `UndoManager.capture/poll/restoring`
- Produces:
  - `TimeTableDao.deleteAll()`, `CourseDao.deleteAll()`
  - `ScheduleRepository.canUndo: Boolean`, `suspend fun restoreLastSnapshot(): Boolean`

- [ ] **Step 1: DAO 加全删**

TimeTableDao:
```kotlin
    @Query("DELETE FROM time_tables")
    suspend fun deleteAll()
```
CourseDao:
```kotlin
    @Query("DELETE FROM courses")
    suspend fun deleteAll()
```

- [ ] **Step 2: repo 写方法前捕获 + restore 实现**

ScheduleRepository 类体内加:

```kotlin
    // ========== v7.10.16 单级撤回 ==========

    val canUndo: Boolean get() = UndoManager.hasSnapshot

    /** 每个公开写方法执行前调用 — 拍下改动前的全库状态 */
    private suspend fun captureForUndo() {
        val tables = tableDao.getAll()
        val courses = courseDao.getByTableOfAll()
        val defaultId = tableDao.getDefault()?.id
        UndoManager.capture(tables, courses, defaultId)
    }

    /** 撤回最近一次改动: 清空两表 → 重插快照 → 恢复 default → 刷 widget/通知 */
    suspend fun restoreLastSnapshot(): Boolean {
        val snap = UndoManager.poll() ?: return false
        UndoManager.restoring = true
        try {
            restoreAll(snap)
        } finally {
            UndoManager.restoring = false
        }
        onDataChanged()
        return true
    }

    @androidx.room.Transaction
    private suspend fun restoreAll(snap: UndoSnapshot) {
        courseDao.deleteAll()
        tableDao.deleteAll()
        courseDao.insertAll(snap.courses)
        tableDao.insertAll(snap.tables)
        snap.defaultTableId?.let { tableDao.setDefault(it) }
    }
```

注: `@Transaction` 注在 private 方法上 Room 不生成代理——改为把事务体放进 CourseDao 或用 `db.withTransaction`:
```kotlin
    private suspend fun restoreAll(snap: UndoSnapshot) = androidx.room.withTransaction(db) {
        courseDao.deleteAll(); tableDao.deleteAll()
        courseDao.insertAll(snap.courses); tableDao.insertAll(snap.tables)
        snap.defaultTableId?.let { tableDao.setDefault(it) }
    }
```
`CourseDao.getByTableOfAll()` 不存在——直接 `courseDao` 需补一个 `@Query("SELECT * FROM courses") suspend fun getAll(): List<CourseEntity>`。

- [ ] **Step 3: 在以下 9 个公开写方法体首行插 `captureForUndo()`**: `insertTable` `updateTable` `deleteTable` `setDefault` `insertCourse` `insertCourses` `updateCourse` `updateCourseGroup` `deleteCourse` `deleteCourseGroup` `replaceCourses`(11 个,全插)
- [ ] **Step 4: 编译** — `./gradlew :app:compileDebugKotlin` → exit 0
- [ ] **Step 5: Commit** — `feat(undo): v7.10.16 — repo 层全量快照捕获+事务恢复`

### Task 3: 顶栏撤回按钮 + 无可撤回 toast

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleScreen.kt`
- Modify: `app/src/main/res/values*/strings.xml`(六 locale 加 `schedule_undo`「撤回」/「Nothing to undo」)

**Interfaces:**
- Consumes: Task 2 `repo.canUndo` / `repo.restoreLastSnapshot()`
- Produces: TopBar 新参数 `onUndo: () -> Unit`

- [ ] **Step 1: strings 六 locale** 在 `schedule_switch_table` 后加:
  zh: `<string name="schedule_undo">撤回</string>` / en: `Undo` / zh-rTW: `撤回` / ja: `元に戻す` / es: `Deshacer`
  另加 toast 文案 `schedule_undo_none`: zh「没有可撤回的操作」/ en `Nothing to undo` / 其余 locale 对应翻译
- [ ] **Step 2: ScheduleScreen TopBar 调用处传 `onUndo`**; TopBar 签名加 `onUndo: () -> Unit`; 左缘 Row 里 logo(切课表)右边加第二个 WeekNavButton(`Icons.AutoMirrored.Outlined.Undo`, contentDescriptionRes = R.string.schedule_undo, onClick = onUndo)
- [ ] **Step 3: 调用侧实现**:
```kotlin
    onUndo = {
        scope.launch {
            if (!viewModel.undoLastChange()) {
                android.widget.Toast.makeText(context, R.string.schedule_undo_none, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
```
ScheduleViewModel 加:
```kotlin
    /** 撤回最近一次数据改动。返回 false = 没有可撤回的操作 */
    suspend fun undoLastChange(): Boolean {
        val ok = repo.restoreLastSnapshot()
        if (ok) manualSelectDone = false   // 恢复后选中态交给 default 表
        return ok
    }
```
(调用侧 `scope` = `rememberCoroutineScope()`)
- [ ] **Step 4: 编译+全量单测** — compile + `testDebugUnitTest`;唯一允许失败=JwProtocolFixtureMatrixTest 死断言
- [ ] **Step 5: Commit** — `feat(undo): v7.10.16 — 顶栏撤回按钮(单级, 全局写操作覆盖)`

## Self-Review

- 覆盖用户三场景: 导入覆盖(repo.replaceCourses 前捕获✓)/新建课程(repo.insertCourse✓)/编辑课程(repo.updateCourseGroup/updateCourse✓); 另覆盖删课/删表/新课表/追加导入/切默认表(setDefault 单独调用也会捕获——但 setDefault 由 insertTable 等内部连带调用时会重复捕获,同值幂等无害)
- 风险: repo 内部一个业务动作连续调多个写方法(如 ImportAsNew: insertTable→insertCourses→setDefault)会捕获多次,槽被覆盖成"紧邻前一写"的快照 → 中间态快照仍完整(每次都是全库), 撤回一次回到该写之前=最终回不到最早状态? 否——最后一次捕获发生在 setDefault 前,此时表已插课程已插,撤回只回退 default 指向,表还在。**不满足用户预期(整个导入应一次撤光)。** 修正: ImportSheet 的 applyImportPreview 与 VM 的复合动作首行手动 `UndoManager.clear()+captureForUndo()`,并把 repo 拦截改为"槽已有本动作链快照则不覆盖"——落实现时用 Task 2 备注的 `beginUndoBatch()` 显式边界: 复合动作入口 begin, 单写动作自动 begin。restore 与 poll 后槽空,天然单级。
