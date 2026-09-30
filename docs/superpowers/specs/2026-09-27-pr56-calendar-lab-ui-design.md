# PR #56 实验室网格开关与日历导出体验 — 设计文档

日期: 2026-09-27 · 状态: 讨论收敛，待实现 · 基线: PR #56 `0955f1f5de92db849cd2cc84e2abf8ea17a797d6`

## 0. 目标与范围

在 PR #56 的独立分支上完成三项用户体验调整：

1. 将网格分隔线与长课间留白拆为两个互不依赖的实验室开关，默认关闭。
2. 保留 PR #56 的课表网格、小组件和日历导出功能，不改变课程数据模型和日历导入核心逻辑。
3. 让系统日历导出权限流程与 Sleepy 现有 Material 3 设置/导入预览界面一致，并在授权完成后自动进入配置，而不是再次展示旧授权弹窗。

范围外：不合并到 `main`，不 push、tag、发布；不引入新的 UI 或权限依赖；不改变日历事件的去重、编辑保护、删除保护和批次模型。

## 1. 用户行为与状态契约

### 1.1 实验室开关

- `网格分隔线` 与 `长课间留白` 是两个独立的 Boolean 设置。
- 两者默认值均为 `false`，写入 `AppPrefs` 后跨进程重启保持。
- 分隔线开关只控制额外网格线；不控制今天背景高亮。
- 长课间留白开关只控制 `MealBreakDetector` 结果是否进入行高和位置计算。
- 组合行为必须完整支持：`关闭/关闭`、`开/关`、`关/开`、`开/开`。
- 任一设置改变后，当前课程表重新组合，且已添加的桌面小组件收到数据刷新通知。

### 1.2 日历权限状态机

导出页是权限状态机的唯一入口，配置弹窗只接收已授权状态：

```text
点击「导出到系统课表」
          │
          ├─ 已同时拥有 READ_CALENDAR + WRITE_CALENDAR
          │       └─ 载入目标课表 → 打开日历配置弹窗
          │
          └─ 缺少任一权限
                  └─ 启动系统权限请求
                          │
                          ├─ 回调后实际重查权限 = 已授权
                          │       └─ 自动载入目标课表 → 自动打开配置弹窗
                          │
                          └─ 回调后实际重查权限 = 未授权
                                  └─ 不保留授权弹窗，停留在 ExportScreen
                                      下次明确点击导出动作时再次请求
```

- 不根据权限请求回调中的单个 Boolean 猜测最终状态，回调后必须调用 `SystemCalendarManager.hasCalendarPermissions` 实际检查。
- 用户拒绝后不显示旧授权解释弹窗、不自动跳系统设置、不自动重试。
- 用户允许后不要求再点一次“授权”或“继续”，直接进入配置弹窗。
- 配置弹窗关闭后回到 ExportScreen；再次点击导出动作按当前真实权限重新分流。
- 已授权但当前无可写日历时，配置弹窗显示现有空状态/引导，不重新请求权限。

### 1.3 日历弹窗视觉契约

弹窗保留 PR #56 的配置能力：目标日历、导出范围、调休映射、普通提醒、首节课闹钟、预览、导入和删除托管事件；只重组视觉与权限入口。

统一使用现有组件和色彩语义：

```text
┌─────────────────────────────────────┐
│ 导出到系统课表                       │  Material 3 AlertDialog
│ 将课程写入系统日历                   │  onSurface / onSurfaceVariant
│                                     │
│ ┌──────┐ ┌──────┐ ┌──────┐          │  PreviewMetricCard
│ │ 课程 │ │日期  │ │提醒  │          │  primary/secondary/tertiaryContainer
│ └──────┘ └──────┘ └──────┘          │
│                                     │
│ ┌─────────────────────────────────┐ │  surfaceContainer 信息块
│ │ 日历  [选择目标日历]              │ │
│ └─────────────────────────────────┘ │
│ ┌─────────────────────────────────┐ │
│ │ 范围  [下周 / 下月 / 本学期]      │ │  SettingsFlatCard / SettingToggleRow
│ │ 调休映射                 [开关]   │ │
│ │ 普通提醒                 [开关]   │ │
│ │ 首节课闹钟               [开关]   │ │
│ └─────────────────────────────────┘ │
│                         取消  导入  │  DialogActionButtons
└─────────────────────────────────────┘
```

- 遵循 `ImportPreviewDialog` 的标题、正文、指标卡、surfaceContainer 信息层级。
- 选项行复用 `SettingToggleRow` 的标签、副标题和 Material `Switch`；不手写另一套开关样式。
- 按钮顺序和危险操作颜色沿用现有 `DialogActionButtons` 约定。
- 所有新文字进入现有多语言资源，不在 Kotlin 中硬编码用户可见文案。

## 2. 技术方案

### 2.1 持久化

在 `AppPrefs` 增加两组 key/getter/setter，默认 false：

- `KEY_GRID_SHOW_SEPARATORS` / `isGridShowSeparators` / `setGridShowSeparators`
- `KEY_GRID_LONG_BREAK_SPACING` / `isGridLongBreakSpacing` / `setGridLongBreakSpacing`

命名可按现有仓库惯例调整，但语义必须一一对应，不能用一个复合值承载两个开关。

### 2.2 课程表和小组件渲染

- `CourseTableView`：读取两个开关；`drawBehind` 中的额外分隔线由分隔线开关控制，`mealGapExtra` 与行位置计算由长课间开关控制。
- 今天背景高亮路径不受分隔线开关影响，开关关闭时仍保留原 PR #56 之前的高亮行为。
- 两个开关都关闭时，行几何必须等于基线几何；不可因 detector 被调用而产生隐形留白。
- `WeekGridWidgetProvider`：同样独立控制线条和 `mealBreakAfterRows`；长课间关闭时传入空集合和零额外间距；今天背景仍独立绘制。
- 设置更新后调用现有 `WidgetUpdater.notifyDataChanged`（或仓库中同等标准刷新入口），不新增广播协议。

### 2.3 导出页权限入口

`ExportScreen` 保存一个仅用于 UI 的状态：是否显示已授权后的 `CalendarImportDialog` 以及当前加载的课程。导出项点击时：

1. 读取当前选中的目标课表。
2. 实际检查双权限。
3. 已授权则加载课程并显示配置弹窗。
4. 未授权则用 `ActivityResultContracts.RequestMultiplePermissions` 请求 READ/WRITE。
5. 回调后重新检查双权限；已授权则执行同一加载/显示函数，未授权则清除弹窗状态并留在页面。

加载课程与权限分流保持在 ExportScreen，避免 CalendarImportDialog 在授权前短暂显示或持有无效状态。

### 2.4 配置弹窗重构

`CalendarImportDialog` 删除权限说明/请求按钮分支，入参前置为已授权状态；保留日历列表为空、加载中、预览和导入状态。这样系统授权 UI 只出现一次，配置 UI 只出现一次。

## 3. 文件结构

修改：

- `app/src/main/java/com/lingion/sleepy/util/AppPrefs.kt`
- `app/src/main/java/com/lingion/sleepy/ui/screen/mine/GeneralSettingsScreen.kt`
- `app/src/main/java/com/lingion/sleepy/ui/component/CourseTableView.kt`
- `app/src/main/java/com/lingion/sleepy/widget/WeekGridWidgetProvider.kt`
- `app/src/main/java/com/lingion/sleepy/ui/screen/mine/ExportScreen.kt`
- `app/src/main/java/com/lingion/sleepy/ui/screen/mine/CalendarImportDialog.kt`
- `app/src/main/res/values/strings.xml`
- 现有 `values-*` 日历/设置资源文件

测试：

- `app/src/test/java/com/lingion/sleepy/util/AppPrefsTest.kt`（若已有同类文件则合并到现有测试）
- `app/src/test/java/com/lingion/sleepy/util/MealBreakDetectorTest.kt` 或新增纯逻辑组合测试
- `app/src/test/java/com/lingion/sleepy/widget/WidgetDegradationLadderTest.kt`
- ExportScreen/权限状态若现有架构不支持 Compose UI 单测，则以纯权限决策函数测试覆盖，UI 通过构建和人工设备测试验证

## 4. 测试策略

### 自动化

- AppPrefs：新 key 默认 false，true/false 写入可读回，两个 key 互不影响。
- 网格逻辑：四种开关组合；关闭长课间时 detector 结果不改变几何；关闭分隔线时不绘制额外线；今天背景逻辑仍存在；两者关闭时基线尺寸不变。
- 小组件：四种组合中 body 边界不越界；长课间关闭时额外计数为零；分隔线开关不改变 slot 几何。
- 权限决策：已授权直达配置、未授权发起请求、授权回调后自动配置、拒绝后停留导出页且无配置弹窗。
- 回归：PR #56 已有日历迁移、detector、widget 测试保持通过。

### 人工设备验证

- 真机验证系统权限对话框的首次授权、部分拒绝、全部拒绝、系统设置后返回、进程重建。
- 真机验证导出页点击路径不出现重复授权弹窗，授权后直接出现配置弹窗。
- 真机验证四种实验室开关组合的课程表和小组件；检查今天背景、分隔线、长课间留白的独立性。
- 不以模拟器点击坐标替代 Android 系统权限和厂商桌面行为验证。

## 5. 边界

- Always：遵循现有 Material 3 组件；用户可见文本走资源；修改后跑聚焦测试和构建；保持分支独立。
- Ask first：合并到 `main`、push、tag、GitHub Release、改变日历数据删除策略、添加依赖或修改 CI。
- Never：删除用户数据库/日历事件；绕过系统权限；把拒绝后的授权弹窗强行留在页面；提交密钥；加入 `Co-Authored-By: Claude` 或自动关闭 issue 的 commit 关键词。

## 6. 成功标准

1. 两个实验室开关默认关闭、可独立切换、持久化，并在设置页明确分成两行。
2. 网格和小组件支持四种组合；分隔线与长课间留白互不影响；今天背景不被分隔线开关关闭。
3. 已授权点击导出直接打开配置；未授权只出现系统权限请求；授权成功自动打开配置；拒绝后停留 ExportScreen，下一次点击才重试。
4. 日历配置弹窗视觉层级与现有 ImportPreviewDialog/SettingsCards 一致，功能能力不回退。
5. 聚焦单测、全量单测、lint 和 debug APK 构建通过；输出 APK 从本分支构建并完成 SHA-256 校验。
6. 分支保留在 `feat/pr56-calendar-lab-ui`，不合并 `main`，不 push、不 tag、不发布。

## 7. 未决问题

无。权限拒绝策略已确定为：拒绝后停留 ExportScreen，不保留旧授权弹窗，下次点击导出动作时重试。
