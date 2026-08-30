# 时段级周次（slot-level weeks）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 编辑/新建课程时每个时段（block）携带自己的周次范围与单双周类型，回填不丢周次，冲突校验带周次维度，保存不再静默改写周次数据。

**Architecture:** 纯 UI 层改动。数据层 `CourseEntity` 每行已带 startWeek/endWeek/type，无需迁移。把 `MeetingBlockDraft` 扩展出周次字段；编辑回填的分组键扩到周次维度；保存走 block 自己的周次；冲突校验加周次相交判断（纯函数 + 单测）。

**Tech Stack:** Kotlin + Jetpack Compose（Material3），Room 持久化（只读不迁移），JUnit4 单测。

## Global Constraints

- UI 禁描边：选中态一律用色块（primaryContainer/secondaryContainer + 对勾），禁 BorderStroke/OutlinedButton（memory: ui-blocks-no-border-rule）
- 禁彩色 emoji；✓ 等功能符号可以
- 6 个 locale（values, values-en, values-es, values-ja, values-zh-rCN, values-zh-rTW）同步补字符串
- weekType 语义与 `CourseEntity.type` 一致：0=每周, 1=单周, 2=双周；`inWeek()` 判定奇偶以 startWeek 锚定（直接复用 entity 逻辑，不另造）
- 测试命令：`./gradlew :app:testDebugUnitTest`
- 提交纪律：每个任务一个 commit，小步提交

---

### Task 1: 周次相交纯函数 + 单元测试

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/util/WeekRangeOverlap.kt`
- Test: `app/src/test/java/com/lingion/sleepy/util/WeekRangeOverlapTest.kt`

**Interfaces:**
- Consumes: 无（独立纯函数）
- Produces: `fun weekRangesOverlap(aStart: Int, aEnd: Int, aType: Int, bStart: Int, bEnd: Int, bType: Int): Boolean` — 判断两个周次区间（含单双周类型）是否存在至少一个公共上课周。type: 0=每周 1=单周 2=双周。单双周奇偶以**绝对周号**判定（与 `CourseEntity.inWeek` 一致）。

- [ ] **Step 1: 写失败测试**

```kotlin
package com.lingion.sleepy.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekRangeOverlapTest {

    // 核心场景：1-5周每周 vs 6-10周每周 — 不相交（用户报告的课）
    @Test
    fun disjointWeeklyRanges_noOverlap() {
        assertFalse(weekRangesOverlap(1, 5, 0, 6, 10, 0))
    }

    // 区间重叠 + 都是每周 → 冲突
    @Test
    fun overlappingWeeklyRanges_overlap() {
        assertTrue(weekRangesOverlap(1, 5, 0, 3, 8, 0))
    }

    // 区间相交但奇偶错开（1-5单周 vs 2-8双周 公共周 2,4 都不命中）→ 不冲突
    @Test
    fun oddVsEven_disjoint() {
        assertFalse(weekRangesOverlap(1, 5, 1, 2, 8, 2))
    }

    // 区间相交且同为单周 → 冲突（公共奇数周 3,5）
    @Test
    fun oddVsOdd_overlap() {
        assertTrue(weekRangesOverlap(1, 5, 1, 3, 8, 1))
    }

    // 1-5每周 vs 3-8单周：公共周 3,5 是奇数命中 → 冲突
    @Test
    fun weeklyVsOdd_overlap() {
        assertTrue(weekRangesOverlap(1, 5, 0, 3, 8, 1))
    }

    // 2-6每周 vs 1-5双周：公共周 2,4 偶数命中 → 冲突
    @Test
    fun weeklyVsEven_overlap() {
        assertTrue(weekRangesOverlap(2, 6, 0, 1, 5, 2))
    }

    // 1-5双周 vs 6-10单周 区间不相交
    @Test
    fun evenVsOdd_disjointRanges() {
        assertFalse(weekRangesOverlap(1, 5, 2, 6, 10, 1))
    }

    // 边界：5-5 与 5-10 相交于第5周
    @Test
    fun singleWeekBoundary_overlap() {
        assertTrue(weekRangesOverlap(5, 5, 0, 5, 10, 0))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*WeekRangeOverlapTest*"`
Expected: 编译失败 `Unresolved reference: weekRangesOverlap`

- [ ] **Step 3: 最小实现**

```kotlin
package com.lingion.sleepy.util

/** 判断两个周次区间（含单双周类型）是否有公共上课周。
 *  type: 0=每周 1=单周 2=双周；奇偶按绝对周号判定，与 CourseEntity.inWeek 一致。 */
fun weekRangesOverlap(
    aStart: Int, aEnd: Int, aType: Int,
    bStart: Int, bEnd: Int, bType: Int
): Boolean {
    val lo = maxOf(aStart, bStart)
    val hi = minOf(aEnd, bEnd)
    if (lo > hi) return false
    fun hits(week: Int, type: Int): Boolean = when (type) {
        1 -> week % 2 == 1
        2 -> week % 2 == 0
        else -> true
    }
    // 两个 type 在任一公共周同时命中即相交；步进 2 已覆盖全部奇偶组合
    for (week in lo..hi) {
        if (hits(week, aType) && hits(week, bType)) return true
    }
    return false
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*WeekRangeOverlapTest*"`
Expected: PASS（8 个用例全绿）

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingion/sleepy/util/WeekRangeOverlap.kt app/src/test/java/com/lingion/sleepy/util/WeekRangeOverlapTest.kt
git commit -m "feat(edit): 周次相交纯函数 — 区间+单双周奇偶判定"
```

---

### Task 2: MeetingBlockDraft 加周次字段 + 保存走 block 值

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt`

**Interfaces:**
- Consumes: Task 1 的 `weekRangesOverlap`（本 task 不用，仅字段扩展）
- Produces:
  - `MeetingBlockDraft` 新增 `var startWeek: Int`、`var endWeek: Int`、`var weekType: Int`（mutableStateOf，UI 可观察）
  - 构造点 4 处需补参：`initialMeetingBlock()`（默认 1/16/0；编辑时若能拿到课程先取其值）、新增时段按钮（默认 1/16/0）、编辑回填 LaunchedEffect（Task 3 接管）
  - `buildCourseEntity(...)` 改签名：删除外部 `startWeek/endWeek` 参数，新增 `block.startWeek/block.endWeek/block.weekType` 直接消费

- [ ] **Step 1: MeetingBlockDraft 加字段**

`MeetingBlockDraft` 类体内加（构造参数 + mutableStateOf）：

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
    weekType: Int = 0
) {
    var mode by mutableStateOf(initialMode)
    var startNode by mutableStateOf(startNode)
    var step by mutableStateOf(step)
    var startTime by mutableStateOf(startTime)
    var endTime by mutableStateOf(endTime)
    var startWeek by mutableStateOf(startWeek)
    var endWeek by mutableStateOf(endWeek)
    var weekType by mutableStateOf(weekType)
}
```

- [ ] **Step 2: buildCourseEntity 改为消费 block 周次**

`buildCourseEntity` 删掉 `startWeek: Int, endWeek: Int` 两个参数，CourseEntity 构造处改为：

```kotlin
        startWeek = block.startWeek,
        endWeek = block.endWeek,
        type = block.weekType,
```

- [ ] **Step 3: 保存按钮处调用点适配**

保存 onClick 里：
- 删除 `normalizedStartWeek/normalizedEndWeek` 计算
- `buildCourseEntity(...)` 调用删去 `startWeek = normalizedStartWeek, endWeek = normalizedEndWeek` 两个实参

- [ ] **Step 4: initialMeetingBlock 与新增按钮补默认值**

`initialMeetingBlock(course)`：非 null 分支用 `course.startWeek`/`course.endWeek`/`course.type`；null 分支默认 `startWeek = 1, endWeek = 16, weekType = 0`。
新增时段按钮 `MeetingBlockDraft(...)` 调用补 `startWeek = 1, endWeek = 16, weekType = 0`（显式写出，可读）。

- [ ] **Step 5: 编译 + 全量测试回归**

Run: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（此时 UI 还没有周次控件，但保存/回填数据面已正确）

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt
git commit -m "feat(edit): MeetingBlockDraft 携带周次 — 保存不再硬编码 type=0/统一周次"
```

---

### Task 3: 编辑回填分组键带周次 — 不再丢周次

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt`

**Interfaces:**
- Consumes: Task 2 的 `MeetingBlockDraft` 周次字段
- Produces: 抽出的纯函数 `fun groupSlotsForEdit(courses: List<CourseEntity>): List<List<CourseEntity>>`（internal，供单测）。分组键 `(ownTime, startNode, step, startTime, endTime, startWeek, endWeek, type)`。

- [ ] **Step 1: 写失败测试**

Create `app/src/test/java/com/lingion/sleepy/ui/screen/edit/GroupSlotsForEditTest.kt`：
（`CourseEntity` 直接构造；所有未提及字段用默认值或最小合法值）

```kotlin
package com.lingion.sleepy.ui.screen.edit

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupSlotsForEditTest {

    private fun course(
        startNode: Int, step: Int,
        startWeek: Int, endWeek: Int, type: Int,
        day: Int = 1, ownTime: Boolean = false,
        startTime: String = "", endTime: String = ""
    ) = CourseEntity(
        groupId = "g", tableId = 1L, courseName = "课", day = day,
        startNode = startNode, step = step,
        startWeek = startWeek, endWeek = endWeek, type = type,
        color = "#FF6750A4", ownTime = ownTime, startTime = startTime, endTime = endTime
    )

    // 用户报告场景：1-5周1-3节 + 6-10周5-7节 → 2 组，周次保留
    @Test
    fun userReportedCase_twoGroups() {
        val courses = listOf(
            course(1, 3, 1, 5, 0),
            course(5, 3, 6, 10, 0)
        )
        val groups = groupSlotsForEdit(courses)
        assertEquals(2, groups.size)
    }

    // 同节次同周次的两天 → 1 组
    @Test
    fun sameSlotDifferentDays_oneGroup() {
        val courses = listOf(
            course(1, 2, 1, 16, 0, day = 1),
            course(1, 2, 1, 16, 0, day = 3)
        )
        assertEquals(1, groupSlotsForEdit(courses).size)
    }

    // 同节次但周次不同 → 2 组（旧逻辑会错误合并）
    @Test
    fun sameNodeDifferentWeeks_twoGroups() {
        val courses = listOf(
            course(1, 2, 1, 8, 0),
            course(1, 2, 9, 16, 0)
        )
        assertEquals(2, groupSlotsForEdit(courses).size)
    }

    // 同节次同周次但单双周不同 → 2 组
    @Test
    fun sameRangeDifferentType_twoGroups() {
        val courses = listOf(
            course(1, 2, 1, 16, 1),
            course(1, 2, 1, 16, 2)
        )
        assertEquals(2, groupSlotsForEdit(courses).size)
    }

    // ownTime 课按时间区分
    @Test
    fun ownTimeDifferentTimes_twoGroups() {
        val courses = listOf(
            course(1, 2, 1, 16, 0, ownTime = true, startTime = "08:00", endTime = "09:40"),
            course(1, 2, 1, 16, 0, ownTime = true, startTime = "10:00", endTime = "11:40")
        )
        assertEquals(2, groupSlotsForEdit(courses).size)
    }

    // 组内 days 去重聚合的原料：同组两条 day=1/day=3
    @Test
    fun groupMembersPreserved() {
        val courses = listOf(
            course(1, 2, 1, 16, 0, day = 1),
            course(1, 2, 1, 16, 0, day = 3),
            course(5, 2, 1, 16, 0, day = 1)
        )
        val groups = groupSlotsForEdit(courses)
        assertEquals(2, groups.size)
        assertEquals(2, groups.first { it[0].startNode == 1 }.size)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*GroupSlotsForEditTest*"`
Expected: 编译失败 `Unresolved reference: groupSlotsForEdit`

- [ ] **Step 3: 抽函数 + 回填改调用**

把 LaunchedEffect 内分组逻辑抽成 internal 函数（放 AddCourseScreen.kt 顶层 private 区）：

```kotlin
/** 编辑回填：按完整时段特征分组。周次/单双周参与分组，
 *  保证「同节次不同周次」回填成两个 block 而不是被错误合并。 */
internal fun groupSlotsForEdit(courses: List<CourseEntity>): List<List<CourseEntity>> =
    courses.groupBy { c ->
        "${c.ownTime}|${c.startNode}|${c.step}|${c.startTime}|${c.endTime}|${c.startWeek}|${c.endWeek}|${c.type}"
    }.values.toList()
```

LaunchedEffect 内替换：

```kotlin
                val slots = groupSlotsForEdit(groupCourses)
```

分组循环内建 block 时补周次字段：

```kotlin
                    meetingBlocks.add(MeetingBlockDraft(
                        id = bid++,
                        days = androidx.compose.runtime.mutableStateListOf<Int>().apply {
                            addAll(courses.map { it.day }.distinct().sorted())
                        },
                        initialMode = if (first.ownTime) MeetingInputMode.ByClock else MeetingInputMode.ByNode,
                        startNode = first.startNode,
                        step = first.step,
                        startTime = first.startTime.ifBlank { "08:00" },
                        endTime = first.endTime.ifBlank { "09:40" },
                        startWeek = first.startWeek,
                        endWeek = first.endWeek,
                        weekType = first.type
                    ))
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*GroupSlotsForEditTest*"`
Expected: PASS（6 个用例全绿）

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt app/src/test/java/com/lingion/sleepy/ui/screen/edit/GroupSlotsForEditTest.kt
git commit -m "fix(edit): 编辑回填分组键带周次 — 同节次不同周次不再被合并丢失"
```

---

### Task 4: 冲突校验加周次维度

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt`
- Test: `app/src/test/java/com/lingion/sleepy/util/WeekRangeOverlapTest.kt`（Task 1 已覆盖纯函数，此处只接 UI）

**Interfaces:**
- Consumes: Task 1 `weekRangesOverlap`；Task 2 block 周次字段
- Produces: `validateCourseDraft` 冲突判定改四维：星期重叠 ∧ 时间重叠 ∧ 周次相交。纯函数已测，UI 接线无新单测（现有 JwImport 等回归保障）。

- [ ] **Step 1: 校验循环加周次判断**

`validateCourseDraft` 的两两冲突循环内，时间重叠判定通过后加一道闸：

```kotlin
            if (firstRange.first < secondRange.second && secondRange.first < firstRange.second) {
                if (!weekRangesOverlap(
                        first.startWeek, first.endWeek, first.weekType,
                        second.startWeek, second.endWeek, second.weekType
                    )
                ) continue
                val dayText = overlapDays.sorted().joinToString(" / ") { DateUtils.localizedDay(it, context) }
                issues += ValidationIssue(
                    second.id,
                    context.getString(R.string.slot_time_overlap, i + 1, j + 1, dayText)
                )
            }
```

import 区加：`import com.lingion.sleepy.util.weekRangesOverlap`

- [ ] **Step 2: 编译 + 回归**

Run: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt
git commit -m "fix(edit): 时段冲突校验加周次维度 — 周次不相交不再误报冲突"
```

---

### Task 5: 时段卡周次 UI + 全局周次改显式应用

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt`
- Modify: 6 个 `app/src/main/res/values*/strings.xml`

**Interfaces:**
- Consumes: Task 2 block 周次字段（mutableStateOf 已可观察）
- Produces: 时段卡内周次编辑区（起/止 NumberField + 每周/单周/双周三选 SegmentButton 色块）；顶部周次卡带「应用到所有时段」按钮。新字符串 key：`slot_week_range`、`apply_to_all_slots`、`week_every`、`week_odd`、`week_even`。

- [ ] **Step 1: 补 6 locale 字符串**

values/strings.xml（基准）：

```xml
    <string name="slot_week_range">周次</string>
    <string name="apply_to_all_slots">应用到所有时段</string>
    <string name="week_every">每周</string>
    <string name="week_odd">单周</string>
    <string name="week_even">双周</string>
```

values-en：

```xml
    <string name="slot_week_range">Weeks</string>
    <string name="apply_to_all_slots">Apply to all slots</string>
    <string name="week_every">Weekly</string>
    <string name="week_odd">Odd weeks</string>
    <string name="week_even">Even weeks</string>
```

values-zh-rCN / values-zh-rTW：同基准（zh-rTW 用「週次」「套用到所有時段」「每週」「單週」「雙週」）。
values-ja：「週範囲」「すべての时段に適用」→ 正确写法「すべてのコマに適用」、「毎週」「奇数週」「偶数週」。
values-es：「Semanas」「Aplicar a todas las franjas」「Todas las semanas」「Semanas impares」「Semanas pares」。

`week_range_sub` 改写（6 locale 同步）：
- values：`整门课的默认周次；用「应用到所有时段」批量覆盖各时段`
- values-en：`Default week range for the course; use "Apply to all slots" to overwrite every slot`
- 其余 locale 对应意译。

- [ ] **Step 2: MeetingBlockEditor 加周次区**

`MeetingBlockEditor` 内，节次/时间 Row 之后加：

```kotlin
        // 周次 — 每时段独立
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NumberField(
                label = stringResource(R.string.slot_week_range) + " " + stringResource(R.string.start_week),
                value = block.startWeek,
                min = 1,
                max = 30,
                modifier = Modifier.weight(1f),
                shape = fieldShape,
                colors = fieldColors
            ) { block.startWeek = it }
            NumberField(
                label = stringResource(R.string.slot_week_range) + " " + stringResource(R.string.end_week),
                value = block.endWeek,
                min = 1,
                max = 30,
                modifier = Modifier.weight(1f),
                shape = fieldShape,
                colors = fieldColors
            ) { block.endWeek = it }
        }
        // 单双周三态 — 项目统一 SegmentedSwitcher（色块选中，禁描边规则）
        SegmentedSwitcher(
            options = listOf(
                0 to stringResource(R.string.week_every),
                1 to stringResource(R.string.week_odd),
                2 to stringResource(R.string.week_even)
            ),
            selected = block.weekType,
            onSelect = { block.weekType = it },
            modifier = Modifier.fillMaxWidth()
        )
```

- [ ] **Step 3: 顶部周次卡加显式应用按钮**

`week_range` CardSection 内 Row 之后加：

```kotlin
                    Button(
                        onClick = {
                            meetingBlocks.forEach { b ->
                                b.startWeek = startWeek
                                b.endWeek = endWeek
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight),
                        shape = SleepyTheme.Buttons.shape,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.secondaryContainer)
                    ) {
                        Text(stringResource(R.string.apply_to_all_slots), color = colors.onSecondaryContainer)
                    }
```

`week_range_sub` 文案已在 Step 1 改。

- [ ] **Step 4: 编译 + 全量测试 + 视觉验证**

Run: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL，537+ 测试全绿

真机/模拟器 smoke（有设备时）：新建课 → 时段1 默认 1-16每周 → 加时段2 改成 6-10周 → 保存 → 重进编辑确认两个时段周次各自保留、无冲突误报。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml app/src/main/res/values-es/strings.xml app/src/main/res/values-ja/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/main/res/values-zh-rTW/strings.xml
git commit -m "feat(edit): 时段卡周次编辑 UI + 全局周次显式应用按钮"
```

---

### Task 6: 全量回归 + 端到端验证

**Files:**
- 无新改动；验证 + 修漏

- [ ] **Step 1: 全量测试**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 全绿，无新增失败

- [ ] **Step 2: 用户场景核验（代码路径走查）**

按用户原始报告逐条核对：
1. 导入 1-5周1-3节 + 6-10周5-7节 → 显示正常（导入侧本就正确，不回归）
2. 进编辑页 → 两个时段卡各自带 1-5 / 6-10 周次（Task 3 分组键）
3. 不动任何东西直接保存 → 周次数据原样（Task 2 走 block 值）
4. 时段间节次/时间重叠但周次错开 → 不再报冲突（Task 4）
5. 真重叠（同周次同节次）→ 仍报冲突

- [ ] **Step 3: git status 收尾自查**

Run: `git status --short && git log --oneline -6`
Expected: 工作区干净，5 个任务 commit 齐整
