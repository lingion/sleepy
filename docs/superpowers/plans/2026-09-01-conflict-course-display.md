# 网格视图课程冲突显示 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 网格视图（CardsGridView）冲突课程不再互相遮挡——完整真卡叠放、零露出课按设置画变体标记（A 叠层/B 折角/C 竖轨）、点击露出部分交换置顶、N≥3 徽标弹窗选置顶。

**Architecture:** 纯 Kotlin 引擎（`ConflictLayoutEngine`）算出每课几何+z序+标记归属，可全量单测；Compose 层（`ConflictCard.kt`）只做渲染与命中；CardsGridView 循环体换成引擎输出驱动。设置项 conflict_style 三选一（默认 rail）。

**Tech Stack:** Kotlin, Jetpack Compose, JUnit4（纯 JVM 单测）, SharedPreferences (AppPrefs 模式)。

**Spec:** `docs/superpowers/specs/2026-09-01-conflict-course-display-design.md`

## Global Constraints

- 仅动 app 内网格视图；周视图 FullWeekView、小组件、CourseDetailSheet 一律不碰。
- 主课判定 = step 降序 > startNode 升序 > id 升序，禁止引入 level。
- B 折角仅限完全重叠（same start & same step 之外一律不画折角；同起不同止走真卡露出）。
- N 徽标仅 N≥3；N≥3 时 A 合流渲染为折角+徽标形态。
- 交换状态仅内存（rememberSaveable），不持久化。
- 验证仅 build/单测/静态检查；禁跑 emulator/dev server（需真机验证时先问用户）。
- 每任务独立 commit；小步提交；UI 走 UI 纯色块无描边规则（现有 SleepyTheme 体系内取色）。
- 全量单测唯一允许失败 = JwProtocolFixtureMatrixTest（pre-existing）。

---
### Task 1: ConflictLayoutEngine — 聚簇+主课判定

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/util/ConflictLayoutEngine.kt`
- Test: `app/src/test/java/com/lingion/sleepy/util/ConflictLayoutEngineTest.kt`

**Interfaces:**
- Consumes: `CourseEntity`（data/entity，字段 startNode:Int, step:Int, day:Int, id:Long）
- Produces: 
  - `data class ConflictCluster(val day: Int, val courses: List<CourseEntity>)`
  - `object ConflictLayoutEngine { fun findClusters(courses: List<CourseEntity>): List<ConflictCluster> }` — 仅返回 size≥2 的簇，day 升序、簇内主课判定序。
  - `fun primaryOrder(courses: List<CourseEntity>): List<CourseEntity>` — step 降 > startNode 升 > id 升。

- [ ] **Step 1: 写失败测试** — 聚簇传递闭包：同一天 1-2 与 2-3 共享节2 归一簇；1-2 与 3-4 不聚簇；跨天不聚簇；单课不成簇不返回；主课判定三分量 tie-break（step 降/startNode 升/id 升）。测试用 CourseColorUtilTest.kt:28 同款 fixture。
- [ ] **Step 2: 跑测试确认失败** — `./gradlew :app:testDebugUnitTest --tests "*ConflictLayoutEngine*"` → FAIL (类不存在)。
- [ ] **Step 3: 最小实现** — 按 day 分组→簇内按 startNode 排序→线性扫相邻区间 `[s, s+step-1]` 相交合并→size≥2 过滤→输出主课判定序。
- [ ] **Step 4: 跑测试通过** — 同上命令 → PASS。
- [ ] **Step 5: Commit** — `feat: 冲突课程布局引擎——聚簇与主课判定`

### Task 2: 引擎部分 B — 零露出/标记归属/变体分配

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/util/ConflictLayoutEngine.kt`
- Test: `app/src/test/java/com/lingion/sleepy/util/ConflictLayoutEngineTest.kt`

**Interfaces:**
- Consumes: Task 1 的 `findClusters`/`primaryOrder`。
- Produces:
  - `data class LaidOutCourse(val course: CourseEntity, val zRank: Int, val hidden: Boolean, val variant: ConflictVariant)`
  - `enum class ConflictVariant { NONE, STACK, FOLD, RAIL }`
  - `fun layoutCluster(cluster: ConflictCluster, style: String, topOverrideId: Long? = null): List<LaidOutCourse>` — style ∈ "stack"/"fold"/"rail"；返回全簇课，zRank 0=顶层；`hidden`=零露出；hidden 课的 variant 按规则分配；顶层课 variant=NONE。
- [ ] **Step 1: 失败测试** — 场景矩阵：完全重叠两课（hidden=true→按 style 出 STACK/FOLD/RAIL）；同起不同止（短课有自然露出 hidden=false, variant=NONE）；完全包含（1-5 内嵌 2-3，内嵌课 hidden=true）；梯形 1-3/2-4/3-5（全部 hidden=false, variant=NONE）；topOverrideId 翻转 z 序并重算 hidden；N≥3 stack→FOLD 合流；N=2 时 RAIL 单轨、N≥3 RAIL 分段（variant 值仍是 RAIL，分段数由 UI 读簇大小）。
- [ ] **Step 2: 确认失败** → **Step 3: 实现**（对每课算「节点区间减去 z 序更高课的覆盖并集」得露出集；露出空=hidden；完全重叠簇且 style=fold 或 (N≥3 且 stack) → hidden 课标 FOLD，其余 hidden 课按 style 直配）→ **Step 4: PASS** → **Step 5: Commit** — `feat: 冲突布局引擎——露出计算与变体分配`

### Task 3: 设置项 conflict_style

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/util/AppPrefs.kt`（KEY_CONFLICT_STYLE 常量 + get/set，模式照 KEY_GRID_SUB_INFO AppPrefs.kt:30,194）
- Modify: `app/src/main/res/values/strings.xml` + 5 个 locale 文件（values-en/-es/-ja/-zh-rCN/-zh-rTW）+ 各 locale 的 SettingsChoiceItem
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/mine/GeneralSettingsScreen.kt`（照 gridSubInfo 模式 GeneralSettingsScreen.kt:79,144-156 加 SettingsCard 三选项）

**Interfaces:**
- Produces: `AppPrefs.getConflictStyle(ctx): String`（默认 "rail"）+ `AppPrefs.setConflictStyle(ctx, value)`；strings key `settings_conflict_style` / `settings_conflict_stack(_sub)` / `settings_conflict_fold(_sub)` / `settings_conflict_rail(_sub)`。
- [ ] **Step 1: AppPrefs 常量+getter/setter（默认 "rail"）** → 跑 `./gradlew :app:compileDebugKotlin` 过 → commit `feat: conflict_style 偏好项`
- [ ] **Step 2: strings.xml 6 locale 补 7 条 key** → 跑 `./gradlew :app:processDebugResources` 过（StringsKeyParityTest 兜底）→ commit `feat: 冲突样式设置文案(6 locale)`
- [ ] **Step 3: GeneralSettingsScreen 加三选项 SettingsCard**（state var + Card + 3×SettingsChoiceItem，onClick 持久化）→ `./gradlew :app:compileDebugKotlin` → commit `feat: 冲突样式设置界面(三选一)`

### Task 4: ConflictCard 渲染层 — 真 卡叠放+交换点击

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/ui/component/ConflictCard.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/ui/component/CourseTableView.kt`（CardsGridView 课程循环体 CourseTableView.kt:180-199 换成引擎驱动）

**Interfaces:**
- Consumes: Task 1/2 引擎全部输出 + `CourseColorUtil.pickCourseColorCompose` + `noRippleClickable`。
- Produces:
  - `@Composable fun ConflictClusterCard(cluster, style: String, topOverrideId: Long?, onPickTop: (Long?) -> Unit, onCourseClick: (CourseEntity) -> Unit, colW: Dp, rowH: Dp, maxNode: Int, timeW/gapW/gapH: Dp, isGrey: Boolean, modifier)` — 整簇一张，内部自绘各课。
  - 引擎封装 `fun layoutFor(courses, style, topOverrideId): List<LaidOutCourse>` 供 UI 用。
- [ ] **Step 1: 失败/占位测试**（Compose 组件不做 Robolectric 渲染断言，写 `layoutFor` 引擎封装单测：多簇输入→展平输出全课、zRank 连续、override 生效）
- [ ] **Step 2: 实现 ConflictClusterCard**：Box 内按 zRank 升序画每课完整真卡（几何=原 startNode/step 映射 colW/rowH），zRank 0 最后画。点击主体→onCourseClick(顶层课)；点击非顶层课的**露出区域**→onPickTop(该课id)。露出区域命中=该课可见区域减去更高层覆盖，Compose 里用每课 Box 叠放天然满足（后画的接住未遮挡 tap；露出区域点击落在该课自己的 Box 上）。N 徽标弹窗（N≥3 且 hidden 课存在）：小徽标锚定右上/轨区，点击 → AlertDialog 多行课名点选 → onPickTop(id)。
- [ ] **Step 3: CardsGridView 接线**：冲突课按簇打包走 ConflictClusterCard，无冲突课保持原 CourseOverlayCard 路径（回归保护）。
- [ ] **Step 4: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`** 全绿 → **Step 5: Commit** — `feat: 网格冲突卡渲染+交换点击+N徽标弹窗`

### Task 5: 三变体视觉实现 + 竖排课名

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/component/ConflictCard.kt`

**Interfaces:**
- Consumes: Task 4 骨架；WeekGrid 字符级竖排截断逻辑参考（widget/WeekGridWidgetProvider.kt:557-624 的字符处理思路，app 侧用 Compose Text 逐字符换行实现）。
- Produces: 三变体最终视觉常量集中在 ConflictCard.kt 顶部 private const。
- [ ] **Step 1: STACK**：主体右下错位 d=4dp 露 L 边（次课课色）——用 Canvas/Box 错位绘制，命中区=视觉边+12dp 内延。
- [ ] **Step 2: FOLD**：右上 12dp 切角（clip 自定义 Shape）+ flap 三角（次课课色）。
- [ ] **Step 3: RAIL**：N=2 单轨 6dp 次课课色；N≥3 轨宽加宽到 ~14dp 纵切 N-1 段，每段=对应课课色+竖排课名（Text 逐字符换行，字号自适应下限，复用竖排截断思路）。
- [ ] **Step 4: 三变体 × N=2/N≥3 组合构建通过** `./gradlew :app:assembleDebug` → **Step 5: Commit** — `feat: 三变体视觉(叠层/折角/竖轨)+N≥3竖排轨段`

### Task 6: 全量验证 + 收尾

**Files:** 无新文件；修复验证中暴露的问题。

- [ ] **Step 1: 全量单测** `./gradlew :app:testDebugUnitTest` → 除 JwProtocolFixtureMatrixTest 外全绿。
- [ ] **Step 2: release 构建** `./gradlew :app:assembleRelease`（确认 R8 无新警告阻塞）。
- [ ] **下一点：真机验证**（接触面积、竖排可读性、徽标位置、点击误触率）——项目规则：先问用户再跑模拟器/真机，不在本计划内自动执行。

## Self-Review 已过

- Spec 覆盖：§1→T1, §2→T2+T4, §3→T3+T5, §4/§5→T4, §9.5-6→T6。§9.1-4 验收 1-4 属真机验证项，依赖 T6 后置确认。
- 类型一致性：LaidOutCourse/ConflictVariant/ConflictCluster 跨任务签名已对齐。
- 占位扫描：无 TBD/“适当处理”类空话；Step 均为可执行动作。
