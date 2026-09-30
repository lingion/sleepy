# 导入草稿箱与退出确认 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在教务导入中自动保存未完成的导入快照，在导入弹窗右上角提供草稿箱入口，并在离开流程前确认是否保留或删除草稿。

**Architecture:** 使用 Room 新增独立的 `ImportDraftEntity`/DAO 保存已解析课程与配置状态，不污染正式课表表；教务导入 Activity 在解析成功后写入草稿、配置变更时更新草稿，恢复时重新进入配置确认页。`ImportSheet` 在标题栏右上角提供圆形草稿箱按钮，点击打开草稿列表并通过回调恢复或删除指定草稿。Activity 的所有退出路径统一经过确认状态，用户选择保留草稿、删除草稿或继续导入。

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room 2.7.0, Room migrations, JUnit 4 / existing Android unit-test setup.

## Global Constraints

- 不使用 `fallbackToDestructiveMigration`；Room schema 从 version 6 升到 7，并登记完整 migration。
- 草稿只保存导入快照，不创建或修改正式 `time_tables` / `courses`，直到用户确认正式导入。
- 课程数据必须可无损恢复：保存全部解析课程字段、学校信息、时间表 JSON、智能课间配置、开始日期、课表名和当前步骤。
- 成功正式导入后删除对应草稿；解析失败且没有课程结果时不得伪造“可恢复课程草稿”。
- 所有教务导入退出动作都必须经过确认；配置确认页点“返回”也不能静默丢弃快照。
- UI 纯色块无描边；圆形草稿箱入口使用现有 Material Icons 线性图标和主题色 token。
- 不新增第三方依赖；不修改用户已有的非本任务工作树改动。
- 每个任务独立验证后再提交小步 commit；commit 作者固定 `lingion <lingion@hrbeu.edu.cn>`，禁止 Co-Authored-By 和自动关闭 issue 关键词。

---

## 文件与职责映射

- Create `app/src/main/java/com/lingion/sleepy/data/entity/ImportDraftEntity.kt`: 草稿 Room entity 与可序列化快照字段。
- Create `app/src/main/java/com/lingion/sleepy/data/dao/ImportDraftDao.kt`: 草稿增删改查、按更新时间排序。
- Modify `app/src/main/java/com/lingion/sleepy/data/AppDatabase.kt`: 注册 entity/DAO，version 6 → 7。
- Modify `app/src/main/java/com/lingion/sleepy/data/Migrations.kt`: v6 → v7 建表 SQL 与索引/约束。
- Modify `app/src/main/java/com/lingion/sleepy/SleepyApp.kt`: 暴露草稿 DAO/repository 访问点，遵循现有数据库初始化模式。
- Create `app/src/main/java/com/lingion/sleepy/data/repository/ImportDraftRepository.kt`: 快照创建、更新、恢复、删除及过期清理。
- Create `app/src/test/java/com/lingion/sleepy/data/ImportDraftMigrationTest.kt`: v6 → v7 schema migration。
- Create `app/src/test/java/com/lingion/sleepy/data/ImportDraftRepositoryTest.kt`: 草稿 CRUD、排序、删除和课程快照完整性。
- Modify `app/src/main/java/com/lingion/sleepy/ui/screen/imports/ImportSheet.kt`: 标题栏右上角圆形草稿箱按钮、列表弹窗、恢复/删除回调。
- Create `app/src/main/java/com/lingion/sleepy/ui/screen/imports/ImportDraftSheet.kt`: 草稿列表 UI 与空状态/恢复/删除交互，避免继续膨胀 ImportSheet。
- Modify `app/src/main/java/com/lingion/sleepy/ui/screen/manage/ManagementPage.kt`: 将草稿恢复回调接回教务导入 Activity 启动路径（若现有导航只支持无参启动，则增加 draftId 参数）。
- Modify `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwImportActivity.kt`: 草稿创建/更新/恢复、退出确认、成功删除。
- Modify `app/src/main/res/values/strings.xml` and all existing locale string files: 草稿箱、恢复、删除、退出确认、保留草稿文案。
- Create/modify focused tests under `app/src/test/java/com/lingion/sleepy/ui/screen/imports/`: 状态机契约测试，锁定退出分支和恢复分支。

---

## Task 1: Room 草稿数据模型与迁移

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/data/entity/ImportDraftEntity.kt`
- Create: `app/src/main/java/com/lingion/sleepy/data/dao/ImportDraftDao.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/data/AppDatabase.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/data/Migrations.kt`
- Test: `app/src/test/java/com/lingion/sleepy/data/ImportDraftMigrationTest.kt`

**Interfaces:**
- Produces `ImportDraftEntity` with stable `id`, `sourceKind`, `schoolName`, `schoolUrl`, `schoolType`, `coursesJson`, `startDate`, `timeJson`, `smartConfigJson`, `tableName`, `stage`, `createdAt`, `updatedAt`.
- Produces `ImportDraftDao.observeAll(): Flow<List<ImportDraftEntity>>`, `get(id: Long)`, `upsert(draft)`, `delete(id)`, `deleteAll()`.

- [ ] **Step 1: Write the failing migration test**

```kotlin
@Test
fun `v6 to v7 creates import drafts table without changing timetable tables`() {
    helper.createDatabase(TEST_DB, 6).apply { close() }
    helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)
}
```

Assert the `import_drafts` columns include the complete snapshot fields, `id` is the primary key, and existing `time_tables`/`courses` remain present.

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests '*ImportDraftMigrationTest'`

Expected: FAIL because version 7 and `MIGRATION_6_7` do not exist.

- [ ] **Step 3: Implement entity, DAO, database registration, and migration**

Use Room `@Entity(tableName = "import_drafts")`, `@PrimaryKey(autoGenerate = true) val id: Long = 0`, `TEXT NOT NULL DEFAULT ''` for strings, `INTEGER NOT NULL` for timestamps, and `stage` values limited by repository constants. Add `ImportDraftEntity::class` and `ImportDraftDao` to `AppDatabase`, set version 7, and register `MIGRATION_6_7` in `ALL_MIGRATIONS`.

- [ ] **Step 4: Run the focused test and verify it passes**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests '*ImportDraftMigrationTest'`

Expected: PASS with no destructive migration.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingion/sleepy/data/entity/ImportDraftEntity.kt app/src/main/java/com/lingion/sleepy/data/dao/ImportDraftDao.kt app/src/main/java/com/lingion/sleepy/data/AppDatabase.kt app/src/main/java/com/lingion/sleepy/data/Migrations.kt app/src/test/java/com/lingion/sleepy/data/ImportDraftMigrationTest.kt
git commit -m "feat(import): add Room storage for unfinished import drafts"
```

## Task 2: 草稿 Repository 与快照完整性测试

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/data/repository/ImportDraftRepository.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/SleepyApp.kt`
- Test: `app/src/test/java/com/lingion/sleepy/data/ImportDraftRepositoryTest.kt`

**Interfaces:**
- `ImportDraftRepository.saveParsedDraft(source, courses, config): Long`
- `ImportDraftRepository.updateConfig(id, config)`
- `ImportDraftRepository.list(): Flow<List<ImportDraftEntity>>`
- `ImportDraftRepository.get(id: Long): ImportDraftEntity?`
- `ImportDraftRepository.delete(id: Long)`
- `ImportDraftRepository.deleteAll()`

- [ ] **Step 1: Write failing repository tests**

Test that saving a parsed draft round-trips every course field and configuration field, list order is newest first, updating configuration changes only draft fields, and deleting one draft leaves other drafts intact.

- [ ] **Step 2: Run focused tests and verify red**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests '*ImportDraftRepositoryTest'`

Expected: FAIL because repository and serialization contract do not exist.

- [ ] **Step 3: Implement repository**

Serialize courses with the existing project JSON conventions rather than Java serialization; use a single explicit `ImportDraftConfig` mapping so restore cannot silently omit `alias`, irregular-time, or group fields. Use `Dispatchers.IO` for DAO calls and keep list ordering in the DAO query (`ORDER BY updatedAt DESC`).

- [ ] **Step 4: Run focused tests and verify green**

Run the same Gradle command; expected PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingion/sleepy/data/repository/ImportDraftRepository.kt app/src/main/java/com/lingion/sleepy/SleepyApp.kt app/src/test/java/com/lingion/sleepy/data/ImportDraftRepositoryTest.kt
git commit -m "feat(import): round-trip unfinished import snapshots"
```

## Task 3: 导入弹窗草稿箱入口与列表 UI

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/ImportDraftSheet.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/ImportSheet.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/manage/ManagementPage.kt`
- Modify: `app/src/main/res/values/strings.xml` and locale mirrors

**Interfaces:**
- `ImportDraftSheet(drafts: List<ImportDraftEntity>, onRestore: (Long) -> Unit, onDelete: (Long) -> Unit, onDismiss: () -> Unit)`.
- `ImportSheet` gains `onDraftRestore: (Long) -> Unit` and displays the button in the title row; it does not own persistence.

- [ ] **Step 1: Add UI contract test**

Add a focused Compose/state contract test asserting the draft button opens the draft sheet, an empty list renders the empty state, restore emits the selected id, and delete emits only the selected id.

- [ ] **Step 2: Run focused test and verify red**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests '*ImportDraftSheetTest'`

Expected: FAIL because the composable and callback contract do not exist.

- [ ] **Step 3: Implement title action and list**

Change the title block to a `Row` with title/subtitle on the left and a circular `IconButton` on the right. Use `Icons.Outlined.Drafts` if available in the pinned Material Icons dependency; otherwise use the closest existing outlined document icon and add a content description. Use `primaryContainer` fill, no stroke, and show newest drafts first. Each item displays school/source, course count, updated time, and Restore/Delete actions.

- [ ] **Step 4: Run focused test and inspect behavior**

Run the focused test and `./gradlew :app:assembleDebug`; expected PASS/build success. Manually verify the button is top-right, circular, and does not shift the import rows.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/imports/ImportDraftSheet.kt app/src/main/java/com/lingion/sleepy/ui/screen/imports/ImportSheet.kt app/src/main/java/com/lingion/sleepy/ui/screen/manage/ManagementPage.kt app/src/main/res/values*
git commit -m "feat(import): add draft box entry to import sheet"
```

## Task 4: 教务导入快照写入、更新与恢复

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwImportActivity.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/manage/ManagementPage.kt`
- Test: `app/src/test/java/com/lingion/sleepy/ui/screen/imports/JwImportDraftStateTest.kt`

**Interfaces:**
- `JwImportActivity` accepts optional `draftId: Long` intent extra.
- `DraftRestoreState` maps a stored draft into `SelectSchool`, `WebViewLogin`, or `ConfigureConfirm` without re-parsing or re-login when parsed courses exist.

- [ ] **Step 1: Write failing state tests**

Test these observable transitions:

```kotlin
assertEquals(Stage.ConfigureConfirm, restoreDraft(parsedDraft).stage)
assertEquals(parsedDraft.coursesJson, restoreDraft(parsedDraft).coursesJson)
assertEquals(Stage.SelectSchool, restoreDraft(metadataOnlyDraft).stage)
```

Also assert updating date/time/name keeps the same draft id and preserves courses.

- [ ] **Step 2: Run focused test and verify red**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests '*JwImportDraftStateTest'`

Expected: FAIL because the restore state mapper does not exist.

- [ ] **Step 3: Implement draft lifecycle**

After `onHtmlCaptured` returns a non-empty course list, create the draft before showing `ConfigureConfirm`. Tie `draftId` to the Activity state. Persist config changes from date/name/time/smart-period callbacks with a debounced or explicit `updateConfig` call. On restore, load the draft first; if courses are present, populate `parsedCourses`, `parsedSchool`, config fields and show confirmation directly. If only school metadata exists, resume at school/WebView stage with the URL and metadata.

- [ ] **Step 4: Run focused tests and build**

Run focused state tests and `./gradlew :app:assembleDebug`; expected PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwImportActivity.kt app/src/main/java/com/lingion/sleepy/ui/screen/manage/ManagementPage.kt app/src/test/java/com/lingion/sleepy/ui/screen/imports/JwImportDraftStateTest.kt
git commit -m "feat(import): persist and restore academic import drafts"
```

## Task 5: 退出确认与草稿最终状态

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwImportActivity.kt`
- Modify: `app/src/main/res/values/strings.xml` and locale mirrors
- Test: `app/src/test/java/com/lingion/sleepy/ui/screen/imports/JwImportExitStateTest.kt`

**Interfaces:**
- `ExitDraftChoice { Continue, KeepDraft, DeleteDraft }`.
- All back/dismiss routes call `requestExit()`; no direct `finish()` remains for an active import state.

- [ ] **Step 1: Write failing exit-state tests**

Assert `requestExit()` opens confirmation when there is active state, `Continue` keeps state and dialog closed, `KeepDraft` deletes neither draft nor parsed data and finishes, `DeleteDraft` deletes exactly the active draft and finishes, and successful import deletes the active draft before finishing.

- [ ] **Step 2: Run focused test and verify red**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests '*JwImportExitStateTest'`

Expected: FAIL because direct finish paths and exit state machine do not exist.

- [ ] **Step 3: Implement confirmation dialog and route every exit**

Use `BackHandler` plus `AlertDialog(onDismissRequest = requestExit)` around the active flow. Dialog buttons are `继续导入`, `退出并保留草稿`, and `退出并删除草稿`; the first is the default safe action. Route Activity back, selection-screen back, WebView back, configuration dismiss, and system/background exit handling through the same state machine. Keep the draft on KeepDraft and delete it only on DeleteDraft or successful final import.

- [ ] **Step 4: Run focused tests/build and verify behavior**

Run focused tests and `./gradlew :app:assembleDebug`. Manually verify system back, WebView back, configuration dialog outside tap, and accidental sheet dismissal all show the same confirmation.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwImportActivity.kt app/src/main/res/values* app/src/test/java/com/lingion/sleepy/ui/screen/imports/JwImportExitStateTest.kt
git commit -m "feat(import): confirm exit and preserve draft state"
```

## Task 6: End-to-end verification and documentation

**Files:**
- Modify: `docs/feature-baseline.md` or the repository's current feature baseline location if the import workflow section is present there.
- Test: existing import and migration suites.

- [ ] **Step 1: Run all focused regression tests**

```bash
cd /Users/lingion_k/sleepy
./gradlew :app:testDebugUnitTest --tests '*ImportDraft*' --tests '*JwImport*'
```

- [ ] **Step 2: Run full unit tests and debug build**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

- [ ] **Step 3: Verify the user flow manually**

1. Open 导入课表; confirm the circular draft icon is in the top-right.
2. Start a school import and reach parsed configuration; background/back out; confirm the exit dialog appears.
3. Choose 保留草稿; reopen 导入课表 → 草稿箱; restore; confirm course count, school, start date, time rows, table name, and smart-period settings are unchanged.
4. Choose 删除草稿; confirm the draft disappears and cannot be restored.
5. Complete import; confirm the draft is removed and the official timetable is present.

- [ ] **Step 4: Update feature baseline and commit**

```bash
git add docs/feature-baseline.md
git commit -m "docs(import): record draft recovery and exit confirmation flow"
```

---

## Checkpoints

### After Tasks 1-2
- Room migration and snapshot round-trip tests pass.
- Existing timetable tables survive a v6 → v7 migration.

### After Tasks 3-5
- Draft icon/list, restore path, and exit confirmation compile and focused tests pass.
- Every active import exit path preserves or deletes the selected draft according to the user's choice.

### Final
- Full unit suite and debug build pass.
- Manual flow proves interrupted imports can be recovered without re-login/re-parse.
- No user pre-existing files are staged.

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Parsed courses are large JSON blobs | Medium | Store one explicit compact JSON snapshot per draft; cap drafts to a small recent count only if measured size requires it. |
| Activity process death before first persistence | High | Persist immediately after parse success, before rendering confirmation. |
| Compose dialog dismissal bypasses callbacks | High | Centralize `requestExit()` and use `BackHandler` plus dialog `onDismissRequest`. |
| Locale strings drift | Medium | Add strings to all existing locale files or use default fallback where translation is not available; build resource validation. |
| Existing issue #40 period-table changes overlap | High | Rebase/inspect current branch before implementation; keep draft snapshot fields compatible with independent period-table binding. |

## Open Questions

- None for the approved first version. The confirmed design is one draft icon in the import sheet, a managed draft list, automatic persistence after successful parse, and three-choice exit confirmation.
