# 节假日调休映射 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有“节假日课程灰显”设置改造成按课表配置“具体调休日 → 被替代星期”的功能，并让所有课程展示路径按该映射取课。

**Architecture:** 保留现有 `HolidayManager` 网络节假日/补班数据和范围覆盖模型，新增纯函数调休映射模型与按 `TimeTableEntity.id` 隔离的本地持久化。设置页对补班日提供空默认的星期选择器；选择后以具体日期为键保存映射，读取课程时由一个共享 resolver 将目标日期转换为课程星期，所有 UI/widget/通知调用该 resolver。

**Tech Stack:** Kotlin, Android Jetpack Compose, Room/现有实体与 Repository, SharedPreferences, `java.time.LocalDate`, JUnit。

## Global Constraints

- 映射必须按课表 ID 隔离；切换课表不能复用另一张课表的调休设置。
- 新增映射默认值必须为空；未选择时保持现有自然星期取课行为。
- 补班日数据只告诉系统“周末需要上班”，不能自动猜测补哪一个工作日；不得预填猜测值。
- 同一日期只能有一个映射；重新选择覆盖旧值，清空选择删除映射。
- 映射的目标星期只能是星期一至星期日，日期必须是具体日期，不使用静态“第几个位置”规则。
- 现有法定节假日/周末灰显开关、网络数据、用户覆盖和删除恢复行为继续保留。
- 主课表、今日页、所有小组件和课程通知必须使用同一日期到课程星期的 resolver，不能各自实现一套判断。
- issues/ 目录只读，不提交；不得回复、关闭 issue 或 push，除非用户另行明确指示。
- 遵循项目 UI 规则：使用现有 Compose 组件和图标，不增加描边按钮或嵌套卡片；中英文资源同步更新。

---

## File Map

- Modify `app/src/main/java/com/lingion/sleepy/util/HolidayRange.kt`: 增加调休映射纯数据模型、JSON 编解码和日期 resolver。
- Modify `app/src/main/java/com/lingion/sleepy/util/AppPrefs.kt`: 增加按课表 ID 读写调休映射的 key 和 API；保留旧节假日偏好 API。
- Modify `app/src/main/java/com/lingion/sleepy/ui/screen/mine/HolidaySettingsScreen.kt`: 将现有页面文案改为调休语义；为补班段编辑/展示加入当前课表选择和空星期选择器。
- Modify `app/src/main/java/com/lingion/sleepy/MainActivity.kt` and relevant screen state wiring: 将当前课表 ID传入设置页，课表切换后设置页读对应映射。
- Modify `app/src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleScreen.kt`, `ScheduleViewModel.kt`, `TodayScreen.kt`, `CourseTableView.kt`: 主应用日期取课统一走 resolver。
- Modify `app/src/main/java/com/lingion/sleepy/widget/TodayWidget.kt`, `TwoDayWidget.kt`, `WeekGridWidgetProvider.kt`, `WeekListWidget.kt`, `WeekViewWidget.kt`, `WidgetRenderActivity.kt`, `WidgetCompactWindow.kt`: 小组件日期取课统一走 resolver。
- Modify `app/src/main/java/com/lingion/sleepy/widget/notification/CourseNotificationScheduler.kt`: 通知按调休后的课程星期查询。
- Create/modify focused tests under `app/src/test/java/com/lingion/sleepy/util/` and source-contract tests for all consumers.
- Modify `app/src/main/res/values/strings.xml`, `values-zh-rCN/strings.xml`, `values-en/strings.xml`: 页面、字段、空值和帮助文案。

---

### Task 1: 调休映射纯函数与持久化契约

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/util/HolidayRange.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/util/AppPrefs.kt`
- Test: `app/src/test/java/com/lingion/sleepy/util/HolidayRangeTest.kt`
- Test: `app/src/test/java/com/lingion/sleepy/util/HolidayMakeupTest.kt`

**Interfaces:**
- Produce `data class MakeupDay(val date: LocalDate, val sourceDayOfWeek: Int)`.
- Produce `HolidayRangeOps.resolveCourseDay(date: LocalDate, mappings: List<MakeupDay>): Int`.
- Produce `HolidayRangeOps.encodeMakeupDays` and `decodeMakeupDays` with malformed-row skipping.
- Produce `AppPrefs.getHolidayMakeupDays(ctx, tableId: Long): List<MakeupDay>`, `setHolidayMakeupDays(ctx, tableId: Long, mappings: List<MakeupDay>)`, and `updateHolidayMakeupDay(ctx, tableId: Long, date: LocalDate, sourceDayOfWeek: Int?)`.

- [ ] **Step 1: Write failing pure-function tests**

```kotlin
@Test fun resolveCourseDay_usesMappedDay() {
    val date = LocalDate.of(2026, 10, 11) // Sunday
    assertEquals(4, HolidayRangeOps.resolveCourseDay(date, listOf(MakeupDay(date, 4))))
}

@Test fun resolveCourseDay_keepsNaturalDayWhenUnmapped() {
    val date = LocalDate.of(2026, 10, 11)
    assertEquals(7, HolidayRangeOps.resolveCourseDay(date, emptyList()))
}

@Test fun decodeMakeupDays_skipsInvalidAndOutOfRangeRows() { /* malformed date, 0, 8 skipped */ }

@Test fun encodeDecodeMakeupDays_roundTrips() { /* two dates remain equal */ }
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `cd /Users/lingion_k/sleepy && ./gradlew test --tests 'com.lingion.sleepy.util.HolidayMakeupTest'`
Expected: FAIL because the mapping type and resolver do not exist.

- [ ] **Step 3: Implement the pure model and JSON codec**

Use a JSON array with stable fields `date` (`yyyy-MM-dd`) and `sourceDayOfWeek` (1..7). `resolveCourseDay` must return the mapping for an exact date, otherwise `date.dayOfWeek.value`; later mappings for the same date replace earlier ones during decode/update.

- [ ] **Step 4: Add table-scoped SharedPreferences APIs**

Use a key format that includes the numeric table ID, for example `holiday_makeup_days_<tableId>`. `updateHolidayMakeupDay(..., null)` removes that date; non-null validates 1..7 and replaces the same date. Never read or write the old global `KEY_HOLIDAY_OVERRIDES` for this mapping.

- [ ] **Step 5: Run tests and verify they pass**

Run: `cd /Users/lingion_k/sleepy && ./gradlew test --tests 'com.lingion.sleepy.util.HolidayMakeupTest' --tests 'com.lingion.sleepy.util.HolidayRangeTest'`
Expected: PASS.

- [ ] **Step 6: Commit the self-contained model change**

```bash
git add app/src/main/java/com/lingion/sleepy/util/HolidayRange.kt app/src/main/java/com/lingion/sleepy/util/AppPrefs.kt app/src/test/java/com/lingion/sleepy/util/HolidayMakeupTest.kt app/src/test/java/com/lingion/sleepy/util/HolidayRangeTest.kt
git commit -m "feat: add table-scoped holiday makeup mappings"
```

---

### Task 2: Settings UI and current-table wiring

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/mine/HolidaySettingsScreen.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Test: `app/src/test/java/com/lingion/sleepy/ui/screen/mine/HolidaySettingsContractTest.kt`

**Interfaces:**
- `HolidaySettingsScreen(onBack: () -> Unit, tableId: Long?)` receives the active table ID.
- The page reads `AppPrefs.getHolidayMakeupDays(context, tableId)` and writes via `updateHolidayMakeupDay`.

- [ ] **Step 1: Write the UI contract tests**

Assert source text contains the table ID input, table-scoped preference calls, an empty option, all seven weekday labels, and no automatic default mapping. Assert MainActivity passes the current table ID rather than using a global setting.

- [ ] **Step 2: Run the contract test and verify it fails**

Run: `cd /Users/lingion_k/sleepy && ./gradlew test --tests '*HolidaySettingsContractTest'`
Expected: FAIL because the screen still has no table ID and no mapping selector.

- [ ] **Step 3: Rename visible semantics without changing existing grey-out controls**

Change the entry title and subtitle to “节假日调休设置” / equivalent English. Explain in one short subtitle that补班日 can be assigned to another weekday and that blank means no mapping. Keep public-holiday/weekend/style controls and existing range editing intact.

- [ ] **Step 4: Add the empty-by-default weekday selector for workday entries**

For each concrete补班日期 shown in the workday list/edit dialog, show `日期（星期X）→ 按星期几的课显示`. The initial selected value is null unless an exact mapping exists. Use the existing option-set/menu component; options are “未设置” plus 周一至周日. Saving a selected value updates only that date; selecting “未设置” removes it. For a multi-day range, expose each date separately so one date cannot silently inherit another date’s mapping.

- [ ] **Step 5: Wire the active table ID and handle no-table state**

Pass `mainVm.state.value.currentTable?.id` from MainActivity. If no table exists, render the existing holiday data controls but disable/hide mapping controls with a concise “请先创建课表” state; do not write a sentinel table ID.

- [ ] **Step 6: Run UI contract and compile tests**

Run: `cd /Users/lingion_k/sleepy && ./gradlew test --tests '*HolidaySettingsContractTest' && ./gradlew :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 7: Commit the settings change**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/mine/HolidaySettingsScreen.kt app/src/main/java/com/lingion/sleepy/MainActivity.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/main/res/values-en/strings.xml app/src/test/java/com/lingion/sleepy/ui/screen/mine/HolidaySettingsContractTest.kt
git commit -m "feat: expose holiday makeup mapping in settings"
```

---

### Task 3: Main schedule and today view use the mapping

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleViewModel.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleScreen.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/today/TodayScreen.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/ui/component/CourseTableView.kt`
- Test: `app/src/test/java/com/lingion/sleepy/ui/screen/schedule/HolidayMakeupScheduleContractTest.kt`

**Interfaces:**
- Consumers call `HolidayRangeOps.resolveCourseDay(date, mappings)` before `getCoursesByDayOnce` or equivalent in-memory `course.day` filtering.
- Mapping state is loaded for `effectiveCurrentTable.id` and invalidated when the active table or mapping changes.

- [ ] **Step 1: Write failing consumer contract tests**

Lock that schedule and today paths call the shared resolver, pass a concrete date, and use the resolved weekday to filter courses. Include a behavior test with Sunday 2026-10-11 mapped to Thursday (4): Thursday courses appear and Sunday-only courses do not; an unmapped date retains its natural weekday.

- [ ] **Step 2: Run the tests and verify they fail**

Run: `cd /Users/lingion_k/sleepy && ./gradlew test --tests '*HolidayMakeupScheduleContractTest'`
Expected: FAIL because current code filters by natural `dayOfWeek`.

- [ ] **Step 3: Add shared mapping state to the schedule state layer**

Load table-scoped mappings from AppPrefs when the active table changes. Keep the list in schedule state or a small existing state holder; do not fetch SharedPreferences independently from every composable render. Trigger recomposition when settings save.

- [ ] **Step 4: Replace natural-weekday filtering in schedule and today paths**

For each rendered concrete date, compute `courseDay = resolveCourseDay(date, mappings)` and use it for filtering. Keep `DateUtils.currentWeek` based on the actual date. Do not alter week-number calculations or course entities.

- [ ] **Step 5: Run tests and compile**

Run: `cd /Users/lingion_k/sleepy && ./gradlew test --tests '*HolidayMakeupScheduleContractTest' && ./gradlew :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 6: Commit the app schedule change**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleViewModel.kt app/src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleScreen.kt app/src/main/java/com/lingion/sleepy/ui/screen/today/TodayScreen.kt app/src/main/java/com/lingion/sleepy/ui/component/CourseTableView.kt app/src/test/java/com/lingion/sleepy/ui/screen/schedule/HolidayMakeupScheduleContractTest.kt
git commit -m "feat: render mapped makeup-day courses in app"
```

---

### Task 4: Widgets and notifications consume the same resolver

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/widget/TodayWidget.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/widget/TwoDayWidget.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/widget/WeekGridWidgetProvider.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/widget/WeekListWidget.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/widget/WeekViewWidget.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/widget/WidgetRenderActivity.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/widget/WidgetCompactWindow.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/widget/notification/CourseNotificationScheduler.kt`
- Test: `app/src/test/java/com/lingion/sleepy/widget/HolidayMakeupWidgetContractTest.kt`

**Interfaces:**
- Every widget/notification loader calls `AppPrefs.getHolidayMakeupDays(context, table.id)` and `HolidayRangeOps.resolveCourseDay` (or one shared repository helper wrapping them).

- [ ] **Step 1: Write failing contract tests**

Assert all eight consumers use the shared resolver and table ID. Add a pure behavior test for today/two-day/week data where a mapped date returns courses from the source weekday.

- [ ] **Step 2: Run tests and verify failure**

Run: `cd /Users/lingion_k/sleepy && ./gradlew test --tests '*HolidayMakeupWidgetContractTest'`
Expected: FAIL because widgets and notifications use `date.dayOfWeek.value` directly.

- [ ] **Step 3: Thread mappings through each loader**

Load once per render/load operation for the selected/bound table. Replace only the course-query weekday; preserve actual target dates in rendered headers, grey-state decisions, semester status, and notification dates. Ensure bound widgets use their bound table ID, not the current default table’s ID.

- [ ] **Step 4: Add refresh invalidation**

After mapping changes, use the same widget refresh mechanism already used by holiday setting changes so existing widget instances redraw. Notification scheduling must be recalculated for affected dates through its existing scheduling entry point.

- [ ] **Step 5: Run tests and compile**

Run: `cd /Users/lingion_k/sleepy && ./gradlew test --tests '*HolidayMakeupWidgetContractTest' && ./gradlew :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 6: Commit widget/notification integration**

```bash
git add app/src/main/java/com/lingion/sleepy/widget app/src/test/java/com/lingion/sleepy/widget/HolidayMakeupWidgetContractTest.kt
git commit -m "feat: apply holiday makeup mappings to widgets"
```

---

### Task 5: Full verification and user-facing behavior audit

**Files:**
- Test: existing holiday, schedule, widget, import/export and notification test suites
- Modify only if a verified regression is found.

- [ ] **Step 1: Run focused pure tests**

Run: `cd /Users/lingion_k/sleepy && ./gradlew test --tests 'com.lingion.sleepy.util.Holiday*'`
Expected: PASS.

- [ ] **Step 2: Run schedule/widget contract tests**

Run: `cd /Users/lingion_k/sleepy && ./gradlew test --tests '*HolidayMakeup*'`
Expected: PASS; ordering assertions must verify the resolver is actually used, not merely that symbols exist.

- [ ] **Step 3: Run the app test/build baseline**

Run: `cd /Users/lingion_k/sleepy && ./gradlew test && ./gradlew :app:assembleDebug`
Expected: PASS, with any pre-existing failures recorded separately from regressions.

- [ ] **Step 4: Perform the behavior audit against the user’s decisions**

Verify each of these exact scenarios:

1. Table A maps 2026-10-11 to Thursday; Table B has no mapping. Switching tables changes only the active table’s result.
2. A new补班日 has an empty selector and shows no substituted courses until the user selects a weekday.
3. Selecting Thursday shows Thursday courses on the actual Sunday date in schedule, today, widgets, and notifications.
4. Clearing the selector restores ordinary Sunday lookup.
5. Existing public-holiday/weekend grey settings still behave exactly as before.

Record a final “用户原话 vs 实现行为” section in the delivery report, including any test gap that could not be run.

- [ ] **Step 5: Check working tree and report**

Run: `cd /Users/lingion_k/sleepy && git status --short`
Expected: only intended implementation/test changes; do not add `issues/` or private SOP files.
