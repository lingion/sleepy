# Spec: 课程配色主题和谐化 + 色相持久化

> 状态：已批准方案（2026-10-18 对话定稿）。分支 `feat/theme-course-color-harmony`。
> 调研依据：M3 dynamic color 主题派生回避 180° 补色区；Tableau/d3 策展色板明暗变体只调明度不动色相；Google Calendar 固定色板路线（本方案取"派生"路线，另设"经典"档保留固定行为）。

## 1. Objective

解决三个已确认的用户痛点：

1. **重新导入颜色乱掉**：groupId 现为导入时随机 UUID（`JwImportViewModel.kt:716`），重导入 = 全新 UUID = 全表换色。
2. **扎眼补色**：黄金角把课色撒满 360° 色环，紫主题下必然出现黄色课胶囊（反之亦然）。
3. **课色与主题完全脱节**：只有 S/L 随亮暗切换，hue 与用户主题色零关联。

目标行为：课程色相 a) 跨导入/改名稳定持久；b) 整体偏主题大色调但每节课保持独立可辨；c) 构造上永不落入主题色区和补色禁区；d) 用户可自定义基准色；e) 老用户升级默认行为零变化。

## 2. 行为规格

### 2.1 三档模式（外观与主题 → 课程配色）

| 模式 | 锚点 anchor | 渲染取色 | 说明 |
|---|---|---|---|
| `CLASSIC`（默认，升级即此档） | 无 | 现行 `stableHue(groupId)` 实时计算，不读表 | 行为与 v1.0.61 完全一致 |
| `FOLLOW_THEME` | 当前主题 primary 的 HSL hue | 读持久表 | 主题变化触发重算 |
| `CUSTOM` | 用户选的色相 0..360 | 读持久表 | 种子变化触发重算 |

决策树优先级不变：① 单课自定义色（非哨兵）永远赢 → ② colorless → ③ 按上表取色。

### 2.2 色相分布（安全弧）

```
禁区1: [anchor-30°, anchor+30°]   主题保护带（课色不与 UI 主色混淆）
允许带A: [anchor+30°, anchor+150°)
禁区2: [anchor+150°, anchor+210°] 补色带（紫配黄事故区, M3 同源规则）
允许带B: [anchor+210°, anchor+330°)

t   = (groupId.hashCode() and Int.MAX_VALUE) % 240
hue = t < 120 ? anchor + 30 + t
              : anchor + 210 + (t - 120)      （均 mod 360）
```

- 纯函数、确定性；同 groupId 同 anchor 永远得同 hue。
- 明暗模式只切 S/L（沿用 0.55/0.82 ↔ 0.40/0.28），hue 恒定——与 Tableau 官方明暗变体做法一致。
- anchor 源：主题 primary。Compose 路径取 `WakeUpColorScheme.primary`；Widget/Canvas 路径经 themeKey 解析 ThemePreset 当前深浅的 primary——三路径必须解析出同一 anchor（契约测试锁）。

### 2.3 重算时机（写表仅在 ViewModel/导入层，渲染层纯读）

1. 模式切换（进入 FOLLOW_THEME/CUSTOM，或两档间互切）→ 全表重算写入。
2. FOLLOW_THEME 下主题变化（AppearanceScreen 保存点触发）→ 全表重算写入。
3. CUSTOM 下种子色变化 → 全表重算写入。
4. 导入新课（含新增课表）→ 先认亲再补色：见 2.4。
5. 编辑界面单课改名 → groupId 不变 → 什么都不做（颜色不变 = 规格要求）。
6. 用户点「重新编排课程配色」→ 全表重算写入（hashCode 不变则结果不变；此按钮存在是为 2.5 换绑后手动恢复，以及未来换散列函数的逃生门）。

渲染兜底：表中查无此 groupId（理论不该发生）→ 实时按 2.2 公式算，与表将含值一致，显示不失稳。

### 2.4 导入认亲（groupId 跨导入继承）

导入生成课程行时：

```
knownNameToGid = 查全库既有 CourseEntity 的 (courseName → groupId) 映射
对每个待导入课程名:
    gid = knownNameToGid[courseName] ?: UUID.randomUUID()
```

- 同名课命中旧 groupId → 持久 hue 直接命中 → 颜色跨导入不变。
- 真·新课 → 新 gid → 按当前模式算 hue 写表，其余课不动。
- 覆盖点：`JwImportViewModel.kt:716`（教务导入）、`ScheduleRepository.kt:665` 附近、`AddCourseScreen.kt:379`（单课新增，无名字命中时同样写表）。

### 2.5 已知代价（明示，不掩饰）

- 改课名后**删课重建**会按新名字重新认亲——若该名字从未出现过，得到新色。改名本身不变色（同 row 同 gid），删了重建才可能变。
- 换主题会重排课表整体颜色（用户已确认此为目标行为：「偏主题大色调」的实现方式）。

## 3. 数据模型

### 3.1 新表（不动 courses）

```kotlin
@Entity(tableName = "course_colors",
    primaryKeys = ["tableKey", "groupId"])
data class CourseColorEntity(
    val tableKey: String,   // "__global__" 或课表语义键,见下
    val groupId: String,
    val hue: Float,         // 0..360
)
```

- 持久键取 `("__global__", groupId)` 起步：groupId 全局唯一（UUID），颜色跟课组走与课表无关；未来若做「每个课表独立配色」再扩 tableKey，不动 schema。

### 3.2 Migration 11 → 12

- `data/Migrations.kt` 登记 `MIGRATION_11_12`：CREATE TABLE。
- **不回填**：CLASSIC 档不读表；首次切入派生档时全量计算写入。
- schema JSON（`/schemas`）随之提交，与 10→11 先例同格式。
- 严禁 `fallbackToDestructiveMigration`（`AppDatabase.kt:45` 红线维持）。

## 4. 代码落点

| 文件 | 改动 |
|---|---|
| `util/CourseColorUtil.kt` | 新增纯函数 `safeArcHue(anchor, groupId)`、`resolveAnchor(...)`；四入口 `pickCourseColor*` 增加可选 `persistedHue: Float?` 参数（null=CLASSIC/兜底实时算） |
| `data/entity/CourseColorEntity.kt` | 新建 |
| `data/dao/CourseColorDao.kt` | 新建：`upsertAll` / `getHueMap` / `getHue` |
| `data/AppDatabase.kt` + `data/Migrations.kt` | entity+dao 注册、11→12 |
| `data/AppPrefs.kt` | 键 `courseColorMode`（Int, 默认 CLASSIC）、`courseColorSeedHue`（Float, 默认 -1） |
| `data/jw/JwImportViewModel.kt` L714-717 | 认亲 + 按模式写表（在事务内） |
| `data/repository/ScheduleRepository.kt` L665 区 | 同上（导入落库通路对齐） |
| `ui/screen/edit/AddCourseScreen.kt` L379 | 新课写表 |
| `ui/screen/mine/AppearanceScreen.kt` | 「课程配色」区：三档 + 色相滑条 + 试算胶囊预览 + 重排按钮（布局见 §5）；主题切换回调里触发重算 |
| ViewModel 层（`ThemeViewModel` 或就近） | 重算全表逻辑：读 groupIds → 算 hue → upsertAll |

## 5. UI（外观与主题页新增区）

```
┌ 外观与主题 ────────────────────────────┐
│ 主题 / 深浅模式          [现有,不动]      │
│ ─────────────────────────────────────  │
│ 课程配色                                 │
│   ◉ 不跟随主题           ← 默认,老行为   │
│   ○ 跟随主题色                           │
│   ○ 自定义基准色    [色相滑条 ●──── ]     │
│   ┌ 预览 ────────────────────────┐      │
│   │ 高数 英语 大物 体育 实验       │ ← 按当前档试算 │
│   └──────────────────────────────┘      │
│   [重新编排所有课程配色]                  │
└────────────────────────────────────────┘
```

- 预览胶囊取当前课表前 5 个不同 groupId 的课名，用纯函数直接算，不依赖 DB 写成功。
- 6 语 strings 齐全（en/zh 为主，其余按现有 AppearanceScreen 键的 locale 文件同步）。

## 6. Testing Strategy

纯函数层（`app/src/test/.../CourseColorHarmonyTest.kt`）：

1. **禁区不变量**：anchor ∈ {0..360 每 15°} × groupId ∈ 200 个固定串 → 断言 hue ∉ [anchor-30,anchor+30]∪[anchor+150,anchor+210]（mod 环上）。红绿验证：临时放宽禁区必须挂。
2. **确定性**：同 (anchor, groupId) 两次计算逐 bit 相等。
3. **明暗只动 S/L**：FOLLOW_THEME 下 isDark 切换 → hue 断言不变。
4. **三路径 anchor 一致**：Compose 与 Canvas 路径解析同一主题 → anchor 相等。

DAO/migration 层（Robolectric 或 Room 测试先例目录）：

5. 11→12 升级后表存在、旧数据无损（沿用本仓既有 migration 测试模式）。
6. upsert 幂等：同 (gid,hue) 写两次无变化。

认亲层（ViewModel bridge 测试先例，如 `JwImportViewModelBridgeTest`）：

7. 二次导入同名课 → groupId 继承 → hue 不变；新课只增不改。

渲染层契约：

8. CLASSIC 档渲染结果与 v1.0.61 的 `stableHue` 逐值相等（升级零变化的机器证明）。

## 7. Success Criteria

- [ ] 升级安装（不切模式）：课表颜色与旧版逐像素一致（测试 8）。
- [ ] 删表重导入同名课：颜色不变（测试 7 + 真机核验）。
- [ ] 编辑改名：颜色不变。
- [ ] 紫/黄任意主题组合：肉眼无补色撞课（禁区不变量测试 1）。
- [ ] 切深浅模式：课色色相稳定，只明度饱和度变（测试 3）。
- [ ] 外观页三档 + 滑条 + 预览 + 重排按钮可用，6 语文案齐。
- [ ] `./gradlew :app:testDebugUnitTest` 全绿；assembleRelease 三 ABI 通过。

## 8. Boundaries

- **Always**：迁移先登记再升 version；schema JSON 提交；写表只在 ViewModel/导入层，渲染纯读；双语 strings 同步。
- **Ask first**：禁区角度若实测区分度不足需收窄（改常量前问）；未来按课表独立配色启用 tableKey 语义。
- **Never**：`fallbackToDestructiveMigration`；渲染路径写 DB；覆盖用户未提交的主 checkout 工作区。

## 9. 实施顺序（依赖序）

1. Entity/DAO/Migration 11→12 + 测试 5/6（地基，独立可测）
2. 纯函数 `safeArcHue/resolveAnchor` + 测试 1/2/3/4/8（不依赖 DB）
3. AppPrefs 两键 + 渲染入口改造（四入口接 persistedHue）
4. 认亲：导入两处 + AddCourseScreen + 测试 7
5. 重算触发：AppearanceScreen 三档 UI + 预览 + 重排 + 主题切换挂钩
6. 6 语文案 + 全量测试 + assembleRelease + APK 真机核验
