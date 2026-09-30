# Sleepy Issue #22 — 同名课程多地点导入与编辑修复

- 文档位置: `docs/superpowers/specs/2026-09-07-sleepy-issue-22-same-name-multi-location-design.md`
- 创建日期: 2026-09-07
- 关联 issue: <https://github.com/lingion/sleepy/issues/22>
- 状态: 设计稿,待用户审阅

---

## 1. 问题陈述

### 1.1 用户报告

同一门课程名(如"大学物理"),在不同上课时间被分配到不同教室/不同教师。学生从教务系统导入时,会按 (课程名,节次,周次,教室,教师) 出现多条记录,这些记录**应该同时存在,各自独立**,但因为现有代码按课程名共享一个 `groupId`,编辑时一组中任意一节都会把整组一起改写。

### 1.2 复现路径

1. 导入课表,使"大学物理"出现 3 行(分别在 A 楼、B 楼、C 楼)
2. 进入编辑页,改其中任意一行的地点
3. 保存后,另两行被误删,只剩当前编辑行

### 1.3 根本原因

`ScheduleRepository.updateCourseGroup()`(`ScheduleRepository.kt:176`)采用"删除整组 + 整组重插"的模式:
```kotlin
courseDao.replaceGroup(tableId, groupId, newCourses)
```
`AddCourseScreen.kt:254-265` 的保存路径:
```kotlin
val gid = editingCourse.groupId
val toInsert = fixedDrafts.map { it.copy(groupId = gid) }
repo.deleteCourseGroup(tid, gid)
repo.insertCourses(toInsert)
```
用户编辑一节时,把同一 `groupId` 下未被触碰的两节当作"陈旧数据"一并删除。

---

## 2. 设计目标

| 目标 | 说明 |
|---|---|
| 同名同 `groupId` | 不破分区身份(契约一) |
| 每节(节次+地点+教师)独立 | 编辑一节不影响其余节 |
| 每节可独立配色 | 跟随组色 / 自动色 / 自定义色 |
| 自动色稳定 | 不每次启动随机换色 |
| 旧库迁移平滑 | 默认值兼容现有行为 |
| 撤回能力不丢失 | 现有 UndoManager 路径继续生效 |

---

## 3. 数据模型变更

### 3.1 `CourseEntity` 新增字段

```kotlin
@Entity(tableName = "courses", ...)
data class CourseEntity(
    // ... 既有字段 ...
    @ColumnInfo(name = "colorMode", defaultValue = "0")
    val colorMode: Int = 0,   // 0=GROUP, 1=AUTO, 2=CUSTOM
    // color 字段语义调整:
    //   colorMode=0 → 忽略(使用组色)
    //   colorMode=1 → 忽略(渲染时自动计算)
    //   colorMode=2 → 必需,保存用户选择的十六进制
)
```

### 3.2 Room 迁移

```kotlin
val MIGRATION_XX_YY = object : Migration(XX, YY) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            ALTER TABLE courses
            ADD COLUMN colorMode INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
    }
}
```

数据库 `versionCode` 升一档,登记到 `AppDatabase.MIGRATIONS` 列表。

旧数据所有行 `colorMode=0`(跟随组色),行为与升级前完全一致,无需回填脚本。

### 3.3 解析器与分组

- `ScheduleParser.kt` 6 处 `groupId = ""` 初始化**保持不变**(parser 输出按课程名的真实身份)
- `ScheduleRepository.assignGroupIds()` 按"同名同 groupId"逻辑**保持不变**(契约一)
- 不引入新字段来标记"地点/教师"

---

## 4. 编辑保存:行级 Diff/Patch

### 4.1 行身份键 `RowKey`

```kotlin
data class RowKey(
    val day: Int,
    val startNode: Int,
    val step: Int,
    val startWeek: Int,
    val endWeek: Int,
    val type: Int,
    val room: String,
    val teacher: String
)
```

任何不在键里的字段(如 `color`,`note`,`colorMode`,`credit`)都属于"非身份"字段,允许直接覆写。

### 4.2 Diff 算法

```kotlin
data class DiffResult(
    val toInsert: List<CourseEntity>,       // 新行
    val toUpdate: List<CourseEntity>,       // 改字段但键不变
    val toDelete: List<Long>,               // 被移除的行 id
    val keptGroupIds: Set<String>           // 整组都没动的 groupId
)
```

按 groupId 分桶:

```text
对每个 groupId:
    server = 服务端当前该 groupId 下所有行
    draft  = 用户编辑草稿中该 groupId 下所有行
    serverKeys = { RowKey(s) for s in server }
    draftKeys  = { RowKey(d) for d in draft }

    for d in draft:
        if RowKey(d) in serverKeys:
            update(server[RowKey(d)], d.copy(id = server[RowKey(d)].id, groupId = ...))
        else:
            insert(d.copy(groupId = ...))
    for s in server:
        if RowKey(s) not in draftKeys:
            delete(s.id)
```

### 4.3 调用替换

`AddCourseScreen.kt:254-265` 当前代码:
```kotlin
val gid = editingCourse.groupId
val toInsert = fixedDrafts.map { it.copy(groupId = gid) }
repo.deleteCourseGroup(tid, gid)
repo.insertCourses(toInsert)
```

替换为:
```kotlin
val allGroupIds = repo.getAllGroupIdsForEditing(editingCourse.tableId, fixedDrafts)
val diff = RowKeyDiffer.diff(repo.getTableCourses(editingCourse.tableId), fixedDrafts)
repo.applyDiff(editingCourse.tableId, diff)
```

要求: `applyDiff` 在一个 `db.withTransaction { }` 块中完成,撤回快照依旧在事务外层一次性捕获(沿用 `captureForUndo()`)。

### 4.4 影响面

- `ScheduleRepository` 增加 `applyDiff(tableId, diff)`,事务化
- `CourseDao` 增加 `insertKeepId`,`deleteByIds(ids)`,批量 update 可走 `updateAll`
- 现有 `updateCourseGroup` / `deleteCourseGroup` 路径**保留**(`UpdateCourseScreen.kt`、`MinePage` 的"删除一节"操作),不删

---

## 5. 颜色模型

### 5.1 三态

| `colorMode` | 名称 | `color` 字段 | 渲染取色 |
|---|---|---|---|
| `0` | GROUP 跟随组色 | 忽略 | 组色 |
| `1` | AUTO 自动色 | 忽略 | 实时计算 |
| `2` | CUSTOM 自定义色 | 必需,十六进制 | 字段值 |

### 5.2 组色定义

```text
groupId 内 colorMode=0 的所有行,
    按 id 升序,
    第 1 行(最小 id)的 color 字段 = 组色源
```

必须保证**每个 groupId 至少存在一行 colorMode=0**。UI 阻止用户关闭最后一行的开关。

### 5.3 自动色算法(Golden Angle)

黄金角 `137.508°`(= `360° × (1 − 1/φ)`, `φ` 为黄金比),在 HSV 色环上按行号推进,得到视觉上"颜色跨度最大"的色相分布。

```kotlin
object GoldenAngleColor {
    private const val GOLDEN_ANGLE_DEG = 137.508f
    private const val SATURATION = 0.55f
    private const val LIGHTNESS = 0.65f

    /**
     * 计算一行的自动色。
     * - groupRows: 该行所属 groupId 的所有行(含自身),必须 id 升序
     * - groupSourceColorHex: 组色源的 color 字段值(已含 #AARRGGBB)
     */
    fun forRow(
        groupRows: List<CourseEntity>,
        groupSourceColorHex: String
    ): Int {
        val baseHue = parseHexHue(groupSourceColorHex)  // 0-360
        val sorted = groupRows.sortedBy { it.id }
        val indexInGroup = sorted.indexOfFirst { it.id == row.id }
        val hue = (((baseHue + indexInGroup * GOLDEN_ANGLE_DEG) % 360f) + 360f) % 360f
        return Color.hsl(hue, SATURATION, LIGHTNESS).toArgb()
    }
}
```

要点:

1. **不落库**: `colorMode=1` 行的 `color` 字段保持空串
2. **确定性**: 同 groupId + 同 id 顺序,算法每次结果一致
3. **组色变则自动跟随**: 改组色 → 组色源 hue 变 → 自动行 hue 都变(因为 baseHue 来自组色源)
4. **稳定性来源**: 行级 diff/patch 保留原行 id,不重插。旧行 id 不变 → 自动色不变

### 5.4 渲染统一入口

```kotlin
object EffectiveColor {
    fun of(row: CourseEntity, groupRows: List<CourseEntity>): Int = when (row.colorMode) {
        0 -> groupColor(groupRows)                                // 跟随
        1 -> GoldenAngleColor.forRow(row, groupSourceColor(groupRows))  // 自动
        2 -> Color.parseColor(row.color)                          // 自定义
        else -> groupColor(groupRows)                             // 防御:旧数据异常值
    }

    private fun groupColor(groupRows: List<CourseEntity>): Int {
        val source = groupRows.firstOrNull { it.colorMode == 0 } ?: groupRows.minBy { it.id }
        val hex = source?.color
        return if (hex.isNullOrBlank()) DEFAULT_ARGB else Color.parseColor(hex)
    }

    private fun groupSourceColor(groupRows: List<CourseEntity>): String {
        val source = groupRows.firstOrNull { it.colorMode == 0 } ?: groupRows.minBy { it.id }
        return source?.color ?: ""
    }
}
```

`EffectiveColor.of()` 是渲染层唯一入口,替换所有 `Color.parseColor(course.color)`。

调用方清单(精确到文件:行):
- `ui/component/CourseBlock.kt`(若存在)
- `ui/screen/table/WeekGrid.kt`
- `ui/screen/table/WeekList.kt`
- `ui/screen/today/TodayScreen.kt`(若存在)
- `widget/`
- `ImportPreview` 预览缩略图

实施时按 grep 找到全部调用点逐处替换,不留 fallback。

---

## 6. UI 改造

### 6.1 颜色区(每节时段)

```
颜色区(switch OFF,默认):
  ●○ 与当前课使用不同的课程颜色
  跟随课程色 [#FF6750A4]                [改组色]

颜色区(switch ON,自动模式,默认选中):
  ○● 与当前课使用不同的课程颜色
  (●自动)  (○自定义)
  自动色 [#FF03DAC5]                    [刷新重算]

颜色区(switch ON,自定义模式):
  ○● 与当前课使用不同的课程颜色
  (○自动)  (●自定义)
  自定义色 [#FF03DAC5]                  [选择]
```

### 6.2 交互

| 动作 | 行为 |
|---|---|
| 首次打开开关 | 进入"自动模式",`colorMode=1`,`color` 不写 |
| 切到"自定义" | 唤起颜色选择器,保存选中值到 `color` 字段 |
| 切回"自动" | 清空 `color` 字段 |
| 关闭开关 | `colorMode=0`,`color` 字段保持(下一开关 ON 后可重选) |
| "改组色"按钮 | 颜色选择器,只改组色源行的 `color`(不动 `colorMode`) |
| "刷新重算"按钮 | 强制重算当前行自动色(算法是确定性的,等价于关→开一次;若手动刷新后颜色不变,可考虑加微小抖动偏移;本期**不做抖动**) |
| 关闭最后一行的开关 | 弹 toast "至少一个节次需跟随课程色",开关状态不变 |

### 6.3 "改组色"位置

每节时段都有"改组色"按钮 → 同一组多节都会显示;点击 → 全组用色确认对话框:
```
[颜色选择器]
确认修改组色吗?组色会影响本组所有跟随课程色的节次。
[取消] [确认]
```
避免误操作只改了一节的组色,其他节没意识到。

---

## 7. 导入 Preview 增强

`ImportSheet.kt` 已有 `CourseConflict` 与 `coursesConflict` 计算(行 756/1446),仅比对 day+week+node。

新增检测:

```text
同 groupId 内,出现 ≥2 条记录,且它们的 (room) 不同
    → 在 ImportPreview 显示警告:
    "X 个课程存在多个上课地点,导入后这些节次将作为独立行展示"
```

不影响导入流程(用户依旧可继续),仅在预览阶段提示。

---

## 8. 测试策略

### 8.1 单元测试

`RowKeyDifferTest`:
- 名称 + (day, node, step, week, type, room, teacher) 身份去重
- 同名不同地点 → 3 行保留
- 同名同地点编辑一节 → 其它行不变
- 删除一节 → 其它节保留
- 增加一节 → 新增单行
- 整组删除 → 全部 delete
- 整组新增 → 全部 insert
- 既有行更新字段(不改键) → 走 update 路径

`GoldenAngleColorTest`:
- hue=0 起点,空组 → (0, 0.55, 0.65)
- 3 行组,baseHue=200 → indexInGroup=0/1/2 的 hue 分别为 200, 337.508, 115.016
- 边界:baseHue=350, idx=2 → (-22.484 + 360) % 360 = 337.516
- 输入空 groupRows → fallback 默认色

`EffectiveColorTest`:
- `colorMode=0` + 多行组 → 用组色源色
- `colorMode=1` + 同组 → 用 GoldenAngleColor
- `colorMode=2` → 用 row.color
- `colorMode=99`(异常值)→ fallback 组色

### 8.2 DAO 集成测试

- Migration 测试: 旧版 `courses` 表(无 `colorMode`)升级后,所有行 `colorMode=0`,`color` 保留原值
- 旧版 SQLite 文件 fixture 跑迁移后,查询 `SELECT colorMode, COUNT(*) FROM courses` 验证

### 8.3 UI 集成测试

- 编辑页: 同名 3 节,关闭一节开关 → 其他两节不受影响,关闭后渲染正确
- 编辑页: 同名 3 节,打开一节开关 → 该行显示自动色(可计算预期值)
- 编辑页: 同名 3 节,切换"自定义" + 选 `#FF123456` → 该行渲染为该色
- 编辑页: 试图关闭所有开关 → 弹 toast,开关状态不变
- 编辑页: 改组色 → 所有跟随行颜色变化,自动行 hue 漂移但仍 deterministic

### 8.4 验收用例

- [ ] 导入含 3 个不同地点的同名课程 → 数据库 3 行
- [ ] 编辑其中 1 行的地点 → 数据库 3 行不变(只该行被 update)
- [ ] 编辑其中 1 行的周次 → 数据库 3 行不变
- [ ] 删除其中 1 行 → 数据库剩 2 行
- [ ] 增加 1 个新节次(新 (day,node,room,teacher)) → 数据库 4 行
- [ ] 同 groupId 下,打开其中 1 行的开关,选自定义 → 渲染自定义色,其他行渲染组色
- [ ] 关闭所有开关 → UI 阻止
- [ ] 撤回最近一次编辑 → 数据库回到编辑前状态(UndoManager 已捕获)
- [ ] 旧库升级后,所有课仍按"组色"渲染(行为不变)

---

## 9. 影响面与风险

### 9.1 文件改动清单(预估)

| 类型 | 文件 |
|---|---|
| 新增 | `data/diff/RowKey.kt` |
| 新增 | `data/diff/RowKeyDiffer.kt` |
| 新增 | `data/diff/DiffResult.kt` |
| 新增 | `ui/util/ColorModel.kt`(或 `ui/color/EffectiveColor.kt`) |
| 新增 | `ui/color/GoldenAngleColor.kt` |
| 新增 | `ui/screen/edit/ColorModeSelector.kt` |
| 新增 | `app/src/androidTest/.../RowKeyDifferTest.kt` |
| 新增 | `app/src/test/.../GoldenAngleColorTest.kt` |
| 新增 | `app/src/test/.../EffectiveColorTest.kt` |
| 修改 | `data/entity/CourseEntity.kt`(+1 字段) |
| 修改 | `data/AppDatabase.kt`(迁移 + versionCode) |
| 修改 | `data/repository/ScheduleRepository.kt`(`applyDiff`) |
| 修改 | `data/dao/CourseDao.kt`(`insertKeepId`,`deleteByIds`) |
| 修改 | `ui/screen/edit/AddCourseScreen.kt`(颜色区 + 保存路径) |
| 修改 | `ui/screen/edit/UpdateCourseScreen.kt`(颜色区,如存在) |
| 修改 | `ui/screen/imports/ImportSheet.kt`(同名多地点提示) |
| 修改 | `ui/screen/table/WeekGrid.kt`(取色统一走 EffectiveColor) |
| 修改 | `ui/screen/table/WeekList.kt` |
| 修改 | `widget/*.kt` |

### 9.2 风险与缓解

| 风险 | 缓解 |
|---|---|
| 行级 diff 误判身份,新行被识别为"改" | RowKey 显式排除 `colorMode`/`note`/`credit` 等非身份字段 |
| 自动色每次启动漂移 | id 稳定 + 算法确定性;不落库但可重算 |
| 旧数据升级后渲染色变 | `colorMode` 默认 0,行为不变 |
| Undo 撤回破坏新引入字段 | 快照本来就是 row 全字段 json 序列化,自动含 `colorMode` |
| parser 输出 groupId 空串混进新逻辑 | parser 输出不分组;`assignGroupIds` 按 name 兜底,保持不变 |
| 改组色只改一节,其他节没意识到 | "改组色" 走确认对话框,文案明示 |

---

## 10. 不在本期范围

- 自动色算法的"抖动刷新"(确定性重算后色不变,无视觉反馈)→ 后续版本
- "改组色"批量应用到已脱离开关的行 → 后续版本
- 同一节次多个教师并行(目前 UI 不支持)
- v1 格式 / sleepy-v1 格式的 parser 路径变更(本期只动 row-level diff,parser 不动)
- 旧"线上+线下合并"丢失信息的回溯

---

## 11. 设计不变量

1. `assignGroupIds` 按课程名(规范化:trim + collapse whitespace + lowercase)分桶,同一名字同一 `groupId`
2. `groupId` 不被 UI 编辑修改
3. RowKey 不被 UI 编辑修改
4. `colorMode` 只能是 0/1/2;异常值 fallback 到 0
5. 每 `groupId` 必须至少一行 `colorMode=0`(UI 强制)
6. 自动色**不落最终色值**,只落模式 + 依赖 id 顺序的稳定性
7. 编辑保存走 row-level diff/patch,不走"整组替换"
8. 撤回快照包含 `colorMode`(自动由 `Row 全字段 json 序列化` 覆盖)