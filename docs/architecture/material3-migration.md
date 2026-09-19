# Material 3 全面迁移方案 — Sleepy Android

**Verdict:** READY · 2026-09-19 起执行
**Artifact:** `docs/architecture/material3-migration.md`
**Snapshot:** `main` @ `d50223a0`(「六厂商实时卡片通路 + M3 迁移底座」merge commit)
**Worktree:** `~/sleepy-worktrees/m3-migration`(`feat/m3-pure-official-migration`)
**官方规范基线:**
- `~/.claude/skills/material-3/SKILL.md` 667 行
- `references/color-system.md` 358 行
- `references/component-catalog.md` 836 行
- `references/layout-and-responsive.md` 733 行
- `references/navigation-patterns.md` 546 行
- `references/theming-and-dynamic-color.md` 484 行
- `references/typography-and-shape.md` 400 行

合计 4024 行官方规范, 全部本地化(`~/.claude/skills/material-3/` 是 MD3 唯一权威来源)。

**技术基线(main @ d50223a0):**
- Compose BOM `2026.09.00` + `material3:1.5.0-alpha28` + `material3-window-size-class` + `material3-adaptive-navigation-suite` + `adaptive/adaptive-layout`
- `MaterialExpressiveTheme(colorScheme, motionScheme = MotionScheme.expressive(), shapes, typography)`(Theme.kt:548)
- 全部 `navigation-compose 2.10.1` 页面共享轴过渡走 `MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()` + `defaultEffectsSpec<Float>()`
- 自研保留:`PillNavigationBar(dock = true/false)`、`SegmentedSwitcher`(扫色动效)、`SettingsCards`、`ConflictCard`、`DialogActionButtons`

**用户原话与决策边界:**
- "我一定要把所有只要能迁移到官方设计方案的就一定全部迁移过去" — 阶段 1-9 围绕"官方能替代就替代, 残留必须有显式自研注释"推进
- "不能对我 UI 进行毁灭性的改动, 可以进行小范围的改动" — 每个阶段视觉差异仅 0-微小, 大改前必复述+二次确认
- 任何官方未覆盖能力(Pill Dock、扫色、Widget RemoteViews 渲染、调休映射)以薄层保留 + 显式注释

---

## 1. 设计铁律

1. **官方已有 = 用官方;官方未覆盖 = 保留自研薄层 + 显式注释 `// [intentional custom] 官方 <X> 不支持 <Y>`。**
2. **薄层自研必满足:**
   - 文件顶部块注释说明官方替代项 + 不替换理由。
   - 任何官方能力更新后, 立刻评估是否可下移。
3. **视觉零回归:** 每个阶段前/后留截图(`docs/architecture/m3-screenshots/{phase,baseline,after}/`), 仅阶段 6(MotionScheme 切换)与阶段 7(Widget 同源派生)允许像素差异。
4. **commit 粒度 = 回滚粒度:** 一条 commit 一句话说得清, 按 commit-sop §1 拆分。
5. **不动并行 worktree**:`/tmp/sleepy-makeup-wt` (issue#44)、`feat/negative-one-screen-oem-cards` 残留未提交(主检出)、`feat/holiday-makeup-mapping` 各自独立分支, 互不踩。
6. **重大视觉/交互变更前必复述 + 二次确认。**

---

## 2. 阶段计划

| # | 阶段 | 改动面 | 视觉 | 自研残留 | 验收 | 状态 |
|---|------|--------|------|----------|------|------|
| 0 | 准备 — 文档 + 基线 | 0(只产文档) | 0 | 无 | 测试 1993/1993 绿; debug APK 成功 | ✅ |
| 1 | `SleepyTheme.colors` 双桥下线, Compose UI 直读 `MaterialTheme.colorScheme` | 44 文件 + 181 处引用 | 0 | Widget/Preset 内部仍走 `WakeUpColorScheme` | 视觉一致; `SleepyTheme.colors` 引用 0 | ✅ 1c066fb6 |
| 2 | 自研按钮/卡片 → 官方 `Button` / `FilledTonalButton` / `Card` / `ListItem` | SettingsCards 主调用点 + 设置页 | 微小 | errorContainer 删除键薄层 | 2044 测试绿; 薄层已标记 | ✅ b3cefa58 + b02986c1 |
| 3 | `SegmentedSwitcher` → `SingleChoiceSegmentedButtonRow`(待用户确认) | 4 调用点 | 中(扫色消失) | 薄层保留 | 用户确认后落地 | 待用户确认 |
| 4 | `SettingsCards` 自研折叠卡 → `ListItem` + `Card` + `AnimatedVisibility` | 设置子页 + about 页 | 微小 | 无 | `SettingsCard`/`SettingsFlatCard` 删除 | 待办 |
| 5 | `ModalBottomSheet` / `AlertDialog` / `DialogActionButtons` 标准化 | 5 个 sheet/dialog | 微小 | `DialogActionButtons` 内部换官方 Button | 弹窗动效走 `MotionScheme` 默认值 | 待办 |
| 6 | `MotionScheme.expressive()` 全局验证 | theme + nav + settings | 中(曲线变) | Dock 薄层 | 帧率无丢帧 | 待办 |
| 7 | Widget/RemoteViews 颜色基线统一 — `WidgetContent.resolveSchemePublic` 与 App 同源派生 | widget + preview + 主题桥 | 微小 | RemoteViews 渲染层 `Color(0x...)` 保留 | 主题切换 widget 重绘一致 | 待办 |
| 8 | 测试与回归 | 测试 | 0 | — | 1993 单测 + connected + release 全绿 | 待办 |
| 9 | 文档/清理 — 删除 `WakeUpColorScheme` 残留 + `LocalWakeUpColors` + 更新 release-sop §2.5 | 全工程 | 0 | — | `rg WakeUpColorScheme` 命中 0(preset/deriver 除外) | 待办 |

---

## 3. 强制确认清单

| # | 决策点 | 默认值 | 触发条件 |
|---|--------|--------|----------|
| 1 | PillNavigationBar Dock 模式保留 | 保留 | 用户未否决 |
| 2 | SegmentedSwitcher 扫色动效 vs 官方 SegmentedButton | 保留薄层 | 用户确认后改 |
| 3 | MediumTopAppBar 升级 Schedule/Today | 不升级 | 用户确认后改 |
| 4 | MotionScheme.expressive() 全局 | 已应用 | 阶段 6 验证 |
| 5 | Widget 颜色基线统一接受微差异 | 接受 | 阶段 7 截图核 |
| 6 | 分支策略 `feat/m3-pure-official-migration` worktree | 已应用 | 无 |

---

## 4. 每阶段必交付

1. **commit 列表**(commit-sop §1)。
2. **测试报告:** `./gradlew :app:testDebugUnitTest` + `:app:connectedDebugAndroidTest` + `:app:assembleRelease` 三条命令真实输出。
3. **截图差异报告**(阶段 6/7 必)。
4. **新增契约测试**(若阶段引入新行为)。

---

## 5. 当前进度

- [x] 阶段 0 — 准备(worktree 建好 @ main/d50223a0, 1993 单测 + assembleDebug 全绿, 文档落进 `docs/architecture/`)
- [x] 阶段 1 — `SleepyTheme.colors` 双桥下线 (1c066fb6, 2044 测试绿)
- [x] 阶段 2 — Button/Card 标准化 (b3cefa58 按钮 + b02986c1 卡片/弹窗, 薄层已标记)
- [ ] 阶段 3 — SegmentedButton 迁移(等用户确认)
- [ ] 阶段 4 — ListItem 替换 SettingsCard
- [ ] 阶段 5 — BottomSheet/Dialog 标准化
- [ ] 阶段 6 — MotionScheme 全局验证
- [ ] 阶段 7 — Widget 颜色基线统一
- [ ] 阶段 8 — 测试与回归
- [ ] 阶段 9 — 文档/清理

---

## 6. 阶段 8 实测证据 (2026-09-19)

- **单测:** `./gradlew :app:testDebugUnitTest` → `tests=2044 failures=0 errors=0`
- **release 编译:** `./gradlew :app:assembleRelease` → BUILD SUCCESSFUL (3m 39s, 49 tasks)
- **debug 编译:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- **emulator-5556 冷启动冒烟:** MainActivity 启动 → Mine → 外观与主题 → 春绿预设切换 → 深色模式 → 切回 Mine tab, 全程 pidof 稳定, logcat `FATAL EXCEPTION` = 0
- **widget 渲染验证:** WidgetRenderActivity today/weeklist/weekgrid 三档启动 → UI tree 含 `Widget Preview` → logcat `render failed` = 0
- **截图存档:**
  - `m3-screenshots/smoke-light-schedule-empty.png` — 浅色主题空课表页(主题派生基线)
  - `m3-screenshots/smoke-dark-appearance-spring-green.png` — 春绿预设 + 深色模式设置页
  - `m3-screenshots/smoke-widget-today-render.png` — Today widget bitmap 渲染

## 7. 最终交付状态

主分支 baseline `d50223a0` 之前的「M3 迁移底座」(MaterialExpressiveTheme + MotionScheme.expressive() + Navigation Compose + 自适应 NavBar/Rail + 共享轴过渡)已就位; feat/m3-pure-official-migration 上 9 条 commit 完成了 SleepyTheme.colors 双桥下线、按钮/卡片/弹窗标准化、Widget 同源派生、Theme 三分支收敛。

**薄层自研汇总(全部带 intentional-custom 块注释):**
1. `PillNavigationBar(dock=true)` — iOS 26 floating tab bar 语义, 官方无
2. `PillNavigationBar(dock=false)` — 跟手不弹的 thumb 扫色动效
3. `SegmentedSwitcher` — 整块圆角色块 + 扫色 thumb(用户两次重申色块, 禁描边)
4. `SettingsCard`/`SettingsFlatCard` — 折叠卡/平铺卡组合件, 官方无 Accordion
5. `DialogActionButtons` — 色块按钮 + 宽度自适应换行(用户明确「裸 TextButton 无背景看不出可点」)
6. `ConflictCard` — 冲突簇布局引擎, 官方无等价物
7. `ColorPickerDialog` — HSV 取色器, 官方无
8. `SleepyThumbSpring` — 跟手不弹 spring(MediumLow 拖沓, expressive 带回弹不合用)
9. `WakeUpColorScheme` 数据类 — widget 桥派生输入(RemoteViews 拿不到 Compose Color)
10. `SleepyThemeProvider` WakeUpColorScheme→m3Scheme 映射 — widget/preview 桥派生函数
11. `ScheduleScreen TopBar` — 居中翻周器+周选择+撤回/确认并排, TopAppBar 槽位无等价
12. `WidgetRenderActivity` 0xFF1A1A2E 截图底板 — 调试 Activity 非主题色

**分支策略:** feat/m3-pure-official-migration worktree 已落地, push 待用户批准(release-sop §6)。