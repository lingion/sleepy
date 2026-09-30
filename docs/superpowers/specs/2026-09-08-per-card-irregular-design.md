# Spec: 逐卡非常规节次/时间 (Per-Card Irregular Node & Time)

> 2026-09-08 · issue#23 重构 · 用户逐条对话定稿

## 1. 目标

把 issue#23 的「课程级总开关 + 全局起止时间」模型推翻，改为**每张节次卡片（MeetingBlock / CourseEntity 行）两个独立可选项**：

- **非常规节次**：把当前卡片绑定到一个边缘节次槽位（0 / −1 / N+1 / N+2 …）；
- **非常规时间**：当前卡片拥有自己的实际起止时间，覆盖节次默认时间。

两个选项相互独立、均非必选、均按卡片独立保存。一门课 100 个节次可以 100 种配置。

## 2. 语义规则（用户定稿）

### 2.1 节次槽位 = 合法可复用槽位

- 边缘节次是课表 timeJson 的真实结构（已有 `edge=before/after` 元数据），不是临时标记。
- **同一节次编号可被多门课复用**（5 个早自习全部登记为第 0 节），不按编号去重。
- 槽位默认时间只在**第一次注册**时填写；之后所有引用它的课继承它。
- 已有槽位的时间可由用户通过卡片上的编辑图标统一修改（全局生效，影响所有引用课程）。

### 2.2 候选节次集合（边界扩展规则）

记标准节次 1..N，当前边缘槽位集合 E：

| 课表现状 | 候选集合 |
|----------|----------|
| 1..N | 新建 0、新建 N+1 |
| 0..N | 复用 0、新建 −1、新建 N+1 |
| −1..N+1 | 复用 −1、复用 0、新建 −2、新建 N+2 |
| −2..N+2 | 夹在 −2/−2+1 之间禁止跳号 |

- 复用 = 把课登记到已有槽位；新建 = 向边界外扩 1。
- 候选 = 全部已有边缘槽位 + 两侧各一个「新建下一节」。
- **禁止跳号**：候选只含「已有槽位」和「紧贴边界的下一个」，不允许 −2 直接到 0 空洞。

### 2.3 时间覆盖不受槽位窗口限制

- 第 0 节注册为 08:00–08:30，某课可选它并把实际时间覆盖为 08:45–09:15，完全合法。
- 后端只存课程实际开始/结束；槽位默认时间不构成任何校验窗口。

### 2.4 三个时间输入 B 规则联动

勾选非常规时间后，卡片提供**开始时间 / 结束时间 / 持续时长（分钟）** 三个可编辑输入：

- 编辑开始或结束 → 持续时长 = 结束−开始，自动重算；
- 编辑持续时长 → 结束 = 开始+时长，自动重算；
- 后端只存开始+结束；时长永远为派生值。
- 结束必须晚于开始（不支持跨零点）；格式校验沿用 `irregular_time_format` / `irregular_time_order`。

### 2.5 冲突 = 标准冲突

- 每门课按自身实际时间区间参与现有冲突判断，无任何特殊路径。
- 第 0 节课撞第 1 节课 = 普通冲突，按冲突渲染/登记。

### 2.6 边缘槽位课固定单节

- 非常规节次卡片 step 固定为 1（不允许 0–1 节连排跨边缘+标准节次）。
- 标准 1..N 节卡片照旧支持 step 连排。
- 需要多节 = 建多张卡片。

## 3. 数据模型

### 3.1 CourseEntity 新增两列（行级）

```kotlin
@ColumnInfo(name = "isIrregularNode", defaultValue = "0") val isIrregularNode: Boolean = false,
@ColumnInfo(name = "isIrregularTime", defaultValue = "0") val isIrregularTime: Boolean = false,
```

- 保存时按实际 startNode 是否为边缘编号回写 `isIrregularNode` anti-drift 回写（timeJson 为真理源）。
- **迁移 v4→v5**：两列 `INTEGER NOT NULL DEFAULT 0`；`ownTime=1` 旧行 `isIrregularTime=1`。

### 3.2 边缘槽位 = timeJson（无新表）

- 新建槽位：现有 `insertEdgeNode(timeJson, edgeClass, start, end)`。
- 修改槽位默认时间：新增 `updateEdgeNodeTimes(timeJson, node, start, edge)`，仅边缘行可改。
- 回收：现有 `reclaimUnusedEdgeNodes`（删课/删组后无引用即回收）继续负责。

### 3.3 effectiveTime 解析（每卡独立）

```
effective(block) =
  block.isIrregularTime → block.startTime / block.endTime          (覆盖值)
  block.startNode ∈ edge slots → 槽位默认时间                       (边缘继承)
  otherwise → 标准 1..N 节次时间                                    (标准)
```

- 该函数是 validateCourseDraft / buildCourseEntity / blockRangeMinutes / ConflictDetailReporter 共用契约，
  四处必须一致（issue#23 的 effectiveTimeJson 一致性铁律延续）。

## 4. UI 重构（AddCourseScreen）

### 4.1 删除（课程级模型全部退役）

- `irregularEnabled` 总开关 + `irregularNodeStartTime/EndTime` 全局字段
- `IrregularSection`（课程级「非常规」面板：开关、课表外节次列表、全局起止时间）
- `buildCourseEntity` / `validateCourseDraft` / `blockRangeMinutes` 的全局参数

### 4.2 卡片内新增（每张 MeetingBlock 卡片）

1. **非常规节次** Switch + 条件内容：
   - 候选列表（§2.2）弹层选择器：复用槽位（编号+时间）+ 新建两侧下一节（标注「新建」）；
   - 选中已有槽位 → 显示槽位时间（置灰）+ **编辑图标** → 弹窗改默认时间（pending，保存时落库）；
   - 选中「新建」候选 → 填该节默认起止（pendingEdgeInserts 机制不变）；
   - step 锁死 1，标准节次选择器隐藏。
2. **非常规时间** Switch + 条件内容：
   - 开始/结束/时长三输入，B 规则联动；
   - 默认值预填 = 所选节次（边缘槽位或标准节次）的默认起止；
   - 无效时间格式/顺序 → 现有校验文案拦截保存。

### 4.3 编辑回填（editingCourse）

- `ownTime=true` → `isIrregularTime=true`，起止预填课程 startTime/endTime；
- startNode 为边缘编号 → `isIrregularNode=true`，选中该槽位（若槽位已被回收则退化为普通卡）；
- 其余照旧。

## 5. 渲染 / widget / 导入导出

- 网格与 widget 直接读 timeJson + 课程实际时间，边缘行天然多出上下行，无额外改动。
- 导入：旧文件无两列 → 默认 false；sleepy-v1 导出携带新字段；WakeUp db 兼容层不动。
- `ownTime` 列保留（WakeUp 兼容），写入时与 isIrregularTime 同值同步（ownTime = isIrregularTime）。

## 6. 测试策略

- 纯 JVM 单测：候选集合规则、updateEdgeNodeTimes、B 规则时间联动、effective 解析 4 组合矩阵。
- 迁移：SQL 与 entity 严格对齐，Room schema 校验兜底；升级路径人工验证（预置 v4 db）。
- 全量 `testDebugUnitTest` + `lintDebug` 0 warning（BRANCHING.md §5 合并前必跑）。
- 禁 emulator/真机（需真机验证先问用户）。

## 7. 边界情况

| 情况 | 行为 |
|------|------|
| 勾选非常规节次但未选槽位 | 校验拦截：要求选择节次 |
| 勾选非常规时间但时间无效 | `irregular_time_format` / `irregular_time_order` 拦截 |
| 槽位编辑弹窗内取消 | pending 丢弃，timeJson 不变 |
| 删光引用某槽位的课 | reclaimUnusedEdgeNodes 回收槽位（现状保留） |
| 旧数据 ownTime=true | 迁移为 isIrregularTime=true，编辑回填复选框点亮 |
| 标准节次卡 startNode 范围 | 限定 1..maxStandard（边缘号只经开关进入） |
| 跨零点时间 | 不支持，校验拦截 |

## 8. 不做的事

- 不做跨零点、不做槽位级时区、不做 UI 外的批量配置入口。
- 不改 ConflictLayoutEngine / widget 渲染管线。
- 不动教务解析器（issue#23 导入侧边缘行已兼容）。
