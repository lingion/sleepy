# Spec: Issue #23 混合课时自动模式与手动模式互通

## Objective

修复 issue #23 的第二次反馈：自动节次目前只有一个全局课程时长，无法表达“多数 45 分钟、少数 30 分钟”的真实作息；用户从手动模式切到自动模式时，已有的手动修改也会被默认配置覆盖。

目标是让课程时长和课间采用同一套分组模型，并让手动节次表与自动配置互相可推导：

- 自动模式可配置主课程时长，以及多个带颜色的课程时长分组。
- 每个课程时长分组可分配到具体节次，交互与现有大小课间完全一致。
- 手动模式改动任意节次的起止时间后，切到自动模式能从实际时间识别出各类课程时长与位置。
- 自动模式改动后回写手动节次表；再次切换不丢失改动。
- 第 0 节和第 N+1 节等 edge 节次不参与主课程时长推断。
- 本轮所有标准网格行仍然等高；不修改现有渲染高度策略。

## Confirmed decisions

1. 课程时长分组完全镜像课间分组：有分钟数、`isLong`、可选标签、颜色、位置分配和删除/索引重映射。
2. 主课程时长使用一个默认值；非主流时长作为覆盖分组。未分配位置使用主课程时长。
3. 从手动时间表推断时，所有可解析课程时长都参与统计；众数作为主课程时长，其余不同分钟数成为覆盖组。
4. 课间配置也从实际相邻节次的时间间隔推断；自动模式中用户修改的课间分钟数再次切换时按实际 rows 重新识别。
5. edge 节次完全排除在课程时长和课间推断之外。
6. 旧的 `smartConfigJson` 缺少新字段时必须按默认值兼容解码。
7. 导出/导入的逐节时间表是事实来源；不能要求外部格式携带 smart config。

## User-visible model

自动模式结构：

```text
输入
├─ 主课程时长       [45] 分钟
├─ 总节数           [12] 节
└─ 第一节开始       [08:00]

课程时长分配
├─ [短课时 30 分钟]  [删除]
│  节次: [1] [2] [3] [4] [5*] [6] [7] ...   (* = 该组占用)
└─ [+ 添加短课时] [+ 添加长课时]

课间分配
├─ [小课间 10 分钟]  [删除]
│  位置: [1.5*] [2.5] [3.5] [4.5] ...
└─ [大课间 30 分钟]  [删除]
   位置: [1.5] [2.5] [3.5*] [4.5] ...

预览                                            <- 逐行由 derive() 生成
第1节 08:00 ~ 08:45        45 分钟 (主课时)
     ↓ 10 分钟 小课间      (1.5 = 小课间组)
第2节 08:55 ~ 09:40        45 分钟 (主课时)
     ↓ 0 分钟连续          (2.5 未分配任何课间组)
第3节 09:40 ~ 10:25        45 分钟 (主课时)
     ↓ 30 分钟 大课间      (3.5 = 大课间组)
第4节 10:55 ~ 11:40        45 分钟 (主课时)
     ↓ 0 分钟连续
第5节 11:40 ~ 12:10        30 分钟 (短课时 30 分钟组)
...
```

图中三处机制同时生效且互不冲突：位置 `1.5` 属小课间组、`3.5` 属大课间组、`2.5`/`4.5` 未分配即 0 分钟连续；节次 `5` 属短课时组，其余节用主课时 45。同一位置只能属一个课间组、同一节次只能属一个课时组，与现有课间互斥语义一致。

课程时长组使用与课间组相同的颜色语义：短课时和长课时入口、分组颜色、标签及位置卡片行为均沿用课间规则。位置卡片标签由 `1.5` 改为 `1`、`2`、`3` 等节次编号。

## Data contract

在 `SmartPeriodConfig` 中新增与 breaks 对称的数据：

```kotlin
@Serializable
data class DurationOption(
    val minutes: Int,
    val isLong: Boolean = false,
    val label: String? = null,
) {
    fun displayLabel(index: Int): String
}

data class SmartPeriodConfig(
    val startTime: String = "08:00",
    val periodMinutes: Int = 45,
    val totalPeriods: Int = 12,
    val breaks: List<BreakOption> = emptyList(),
    val transitionAssignments: List<Int?> = emptyList(),
    val durations: List<DurationOption> = emptyList(),
    val periodAssignments: List<Int?> = emptyList(),
)
```

Compatibility rules:

- New fields are optional serializable fields with empty-list defaults, so old JSON decodes unchanged.
- `periodAssignments` has effective length `max(0, totalPeriods)`; null or invalid index means `periodMinutes`.
- A duration group applies to exactly one position assignment; overlapping assignment is prevented by the same last-selection semantics as breaks.
- `derive()` calculates each period's end from its assigned duration, then applies the assigned break before the next period.
- Existing break fields and JSON meaning remain unchanged.

## Shared inference contract

Add one pure utility boundary, used by import and every manual/automatic mode transition:

```kotlin
fun inferSmartPeriodConfig(
    rows: List<TimeTableUtils.TimeSlotRow>,
    previous: SmartPeriodConfig? = null,
): SmartPeriodConfig?
```

Rules:

1. Filter to standard nodes `1..N`; exclude rows marked `edgeClass != null`.
2. Parse valid `HH:mm` start/end pairs. If any standard row needed for inference is blank, reversed, overlapping, or otherwise invalid, return null rather than silently inventing times.
3. Compute each period duration as `end - start`; choose the most frequent duration as `periodMinutes`. Ties use the earliest standard node's duration, making the result deterministic.
4. Every distinct non-primary duration produces one `DurationOption` and assignments for its matching nodes. Preserve matching `label`/`isLong` from `previous` by minutes when possible; otherwise classify shorter-than-primary as short and longer-than-primary as long.
5. Compute each transition as `next.start - current.end`. Zero means continuous; each distinct positive value produces one `BreakOption` and transition assignments. Preserve previous labels and `isLong` by minutes where possible.
6. Preserve `startTime` from the first standard row and set `totalPeriods` to the standard-row count.
7. Return a config whose `derive()` reproduces the inferred rows' start/end values for all standard nodes.
8. The function does not write to the database and does not inspect courses.

The mode switch must use this contract:

```text
manual rows --infer--> smart config --derive--> manual rows
```

It must not seed automatic mode with a fresh `SmartPeriodConfig()` when rows already contain valid times. This removes the current 45-minute overwrite.

## Architecture and affected boundaries

- `SmartPeriodConfig.kt`: duration option, assignments, effective assignment helpers, and per-period derivation.
- `TimeTableUtils.kt`: pure inference function and any small parsing helpers; edge filtering stays here so all callers share the rule.
- `SmartPeriodEditor.kt`: duration group controls that mirror break group controls; use a focused reusable assignment-group renderer rather than duplicating index-remapping logic.
- `TimeSlotEditor.kt`: infer when entering auto mode from current rows; derive back to rows when auto config changes. Existing period-table tab behavior remains unchanged.
- `EditTableScreen.kt`, `PeriodTableEditScreen.kt`, `ImportSheet.kt`, `JwImportActivity.kt`: replace default-only seeding with the shared inference function, retaining stored smart config when it is valid and intentionally preserved.
- `strings.xml` locale files: add duration-specific labels and accessibility/content descriptions for all supported locales.
- Existing native export/parser code: no format change required. Its `N/Pn` times already provide the source rows used for inference.

ASCII mode flow:

```text
┌──────────────┐   edit rows    ┌───────────────┐
│ 手动节次表    │ ─────────────> │ 当前 rows 真源 │
└──────┬───────┘                └──────┬────────┘
       │ 切自动 / inferSmartPeriodConfig│
       v                                │
┌──────────────┐   derive()             │
│ 自动配置      │ ──────────────────────┘
│ 主课时        │
│ 课程时长分组  │
│ 课间分组      │
└──────────────┘
```

## Error and compatibility behavior

- Invalid or incomplete manual rows keep the user in manual mode and show the existing validation error path; no guessed 45-minute schedule is written.
- A malformed stored `smartConfigJson` falls back to inference from current rows, then to the existing default only when inference is impossible and there are no usable rows.
- Existing configurations with no duration groups continue to derive exactly as before: every period uses `periodMinutes`.
- Edge rows remain in `timeJson` and manual editing, but never alter the inferred primary duration or duration-group assignments.
- Standard row rendering remains equal-height. `timeToFractionalRows` and render slot weights are out of scope.

## Testing strategy

Focused unit tests in `app/src/test/java/com/lingion/sleepy/data/entity/SmartPeriodConfigTest.kt` and a new inference test file should cover:

- default config derives the existing all-primary-duration schedule;
- duration assignments derive mixed 45/30 rows in the correct order;
- invalid duration assignments fall back to the primary duration;
- break and duration assignment indexes remain valid after deleting a group;
- inference identifies a 45-minute majority and a 30-minute minority;
- inference identifies multiple duration groups and multiple break groups;
- ties are deterministic;
- inference excludes before/after edge rows;
- inference followed by derive reproduces all valid standard rows;
- old JSON without duration fields decodes and preserves old output;
- invalid/blank rows return null rather than silently overwriting them.

Run focused tests with:

```bash
cd ~/sleepy
./gradlew :app:testDebugUnitTest --tests 'com.lingion.sleepy.data.entity.SmartPeriodConfigTest'
```

Before implementation completion run:

```bash
cd ~/sleepy
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

## Boundaries

- Always: keep inference pure; preserve old JSON defaults; exclude edge rows; keep standard grid rows equal-height; test manual -> auto -> manual round trips.
- Ask first: changing export format; changing database schema; changing grid rendering heights; adding dependencies; modifying unrelated issue #40 binding behavior.
- Never: silently replace valid manual times with default 45-minute values; include edge rows in primary-duration statistics; delete existing break semantics; rewrite or remove legacy import formats.

## Success criteria

1. A 12-period schedule with eleven 45-minute periods and one 30-minute period switches manual -> automatic and displays a primary 45-minute group plus a 30-minute group assigned to the correct period.
2. Automatic -> manual -> automatic preserves all valid standard period start/end times and all break gaps.
3. Existing old `smartConfigJson` and schedules without duration groups retain their current derived output.
4. Edge nodes before period 1 and after the standard range do not create duration groups or change the primary duration.
5. The focused and full unit-test commands pass; lint introduces no new errors.

## Open questions

None for this scope. Visual row-height scaling is explicitly deferred.
