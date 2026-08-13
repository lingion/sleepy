# Sleepy Android 代码审计报告 v1.0.29

**审计基准**: HEAD `c34b22f` (2026-08-07, versionName 1.0.29 / versionCode 30)
**审计日期**: 2026-08-11
**审计范围**: 79 个 Kotlin 文件 / 18704 行 (data + util + widget + ui + integration)

---

## 已修复的 bug (8 个)

### [P0] FluidCloudService 未调用 startForeground 导致系统崩溃
- **位置**: `widget/notification/FluidCloudService.kt:53-72`
- **根因**: `BeforeClassNotifyReceiver` 用 `startForegroundService()` 启动本服务。Android 12+ 要求 5 秒内必须 `startForeground()`,但 `onStartCommand` 的 early-return 分支(课程已开始 / epoch 异常)直接 `stopSelf()` 而**未先 startForeground** → 系统抛 `ForegroundServiceDidNotStartInTimeException`,进程崩溃。
- **修复**: early-return 分支补一个最小占位前台通知履行契约,随后移除。

### [P0] AddCourseScreen 删除课程强制 `!!` 空指针崩溃
- **位置**: `ui/screen/edit/AddCourseScreen.kt:478`
- **根因**: `repo.deleteCourseGroup(state.selectedTableId!!, ...)` — `selectedTableId` 为 null (并发删表 / deep-link 进入时 state 未就绪) 时直接 NPE。
- **修复**: 安全解构 `val tid = state.selectedTableId ?: return`,null 时跳过。

### [P1] ScheduleRepository.updateCourseGroup 无事务
- **位置**: `data/repository/ScheduleRepository.kt:87-91` + `data/dao/CourseDao.kt`
- **根因**: "删除同 groupId + 插入新草稿" 两步操作未包事务。进程被杀或并发导入时,删除已生效但插入未完成 → 课程数据丢失。
- **修复**: DAO 新增 `@Transaction replaceGroup(tableId, groupId, newCourses)`,Repository 改调它,保证原子性。

### [P1] JwImportViewModel.importAsNewTable 手动算 ID + 无事务
- **位置**: `data/jw/JwImportViewModel.kt:171-214`
- **根因**: (1) `newId = maxOf{id}+1` 手动算主键后用 `OnConflictStrategy.REPLACE` 插入,绕过 autoGenerate;当表被删/导入残留时 newId 与既有 id 冲突,REPLACE 触发外键 CASCADE **覆盖既有课表及其全部课程**。(2) 建 table + 插课程无事务,中断留空课表。
- **修复**: 改用 `id=0` 让 Room autoGenerate 返回真实主键;整个操作包在 `db.withTransaction { }` 里,失败回滚。

### [P1] JwUrpParser 周次列缺失时索引越界崩溃
- **位置**: `data/jw/JwUrpParser.kt:55-65`
- **根因**: `weekIdx` 未匹配到"周次"表头时为 -1,`headSize - weekIdx = headSize+1`、`acDayIdx = dayIdx+1`,后续 `tds[acDayIdx]` 越界抛 `IndexOutOfBoundsException`。
- **修复**: `weekIdx/nodeIdx/nameIdx == -1` 时跳过该表。

### [P1] AppPrefs.setBeforeClassFluidPrimary 覆盖多选字段
- **位置**: `util/AppPrefs.kt:137-141`
- **根因**: 设置主字段时同时 `putString(KEY_BEFORE_CLASS_FLUID_FIELDS, value)`,把多选字段集 (`"name,time,room,teacher"`) 覆盖成单值,用户配置的多字段组合永久丢失。
- **修复**: 只写 PRIMARY,不再写 FIELDS。

### [P1] JwNewZfParser.parseCell 丢弃多段周次
- **位置**: `data/jw/JwNewZfParser.kt:282-313`
- **根因**: `ranges.first()` 只取第一段周次。`"1-11周(单),13-16周"` 的后半段被丢弃 → 后半学期课程整段丢失。
- **修复**: `parseCell` 返回 `List<JwCourse>`,展开全部周次段;调用方 `result += parseCell(...)`。

### [P2] PinyinMatcher 土耳其语 locale 破坏搜索
- **位置**: `util/PinyinMatcher.kt:32,36,39,47-49`
- **根因**: `lowercase(Locale.getDefault())` 在土耳其语 locale 下 `'I'.lowercase() → 'ı'`,ASCII 字母不再规范化,拼音首字母搜索全部失效。
- **修复**: 全部改为 `Locale.ROOT`。 (ast_edit 批量替换 6 处)

---

## 审计中发现但未改动的设计债 (记录)

| # | 优先级 | 位置 | 说明 | 不改的原因 |
|---|---|---|---|---|
| - | P2 | `data/AppDatabase.kt:35` | `fallbackToDestructiveMigration()` 会在 schema 升级时清空用户数据 | 改为严格迁移需写 v1→v2→v3 Migration,缺旧 schema 细节;贸然改会让老用户升级崩溃。当前对老用户是"清数据但不崩",建议后续单独排期。 |
| - | P2 | `util/UpdateManager.kt:48-56` | mirror 回退分支覆盖 version,主站完全失败时 forceUpdate 不可得 | mirror 是 HTML 页面无 body 字段,本就无法获知 force 标记;主站成功时 forceUpdate 已正确。当前行为可接受。 |
| - | P2 | `data/parser/ScheduleParser.kt:284-287` | `extractIcsWeeks` 是 stub,返回硬编码 `(1,16,0)` | ICS 不含学期起点,无法反推教学周;且 ICS 导入是次要路径(用户主要走教务/JSON)。已修正注释诚实化,行为保留。 |
| - | P2 | `widget/WidgetBitmapRenderers.kt` | 中间 bitmap 不 recycle | 仅 debug 预览 Activity 使用,非生产路径。 |
| - | P2 | `widget/notification/CourseNotificationScheduler.kt:84` | cancelAll 只取消 50 个课前提醒 intent | 单日 >50 节课罕见;request code 按 index 编号,跨天可能错位。记录为后续改进。 |
| - | P1 | `util/LocaleHelper.kt:61-68` | `wrapDefault` 经 `SleepyApp.get()` 取语言,极早期 catch 回退 zh-CN | Android 生命周期保证 Application.onCreate 先于 Activity.attachBaseContext,正常路径 instance 已就绪;仅在异常 ContentProvider 触发时回退。低概率,需运行时验证。 |

---

## 验证

```
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug
→ BUILD SUCCESSFUL
→ 23 tests passed (0 failures, 0 errors):
   - JwWiseduParserTest (5)
   - JwParserTest (5)
   - SmartPeriodConfigTest (7)
   - ExportImportRoundTripTest (3)
   - VersionUtilsTest (1)
   - DateUtilsTest (2)
→ 3 APK 构建成功 (arm64-v8a / armeabi-v7a / x86_64)
```

**改动文件清单 (8 个)**:
1. `widget/notification/FluidCloudService.kt` — P0 startForeground
2. `ui/screen/edit/AddCourseScreen.kt` — P0 NPE
3. `data/dao/CourseDao.kt` — 新增 `replaceGroup` @Transaction
4. `data/repository/ScheduleRepository.kt` — 调用事务方法
5. `data/jw/JwImportViewModel.kt` — autoGenerate + withTransaction
6. `data/jw/JwUrpParser.kt` — 索引越界防护
7. `util/AppPrefs.kt` — 不覆盖多选字段
8. `data/jw/JwNewZfParser.kt` — 展开多段周次
9. `util/PinyinMatcher.kt` — Locale.ROOT
10. `data/parser/ScheduleParser.kt` — ICS 注释诚实化
