# Plan: 启动默认页面设置(issue #7)

用户诉求: app 启动时默认打开"网格"而非固定"周视图"(周视图需要手动切换)。在通用设置里加一个启动页选择。

## 现状事实(已核实)

- `MainActivity.kt:167` — `var currentTab by remember { mutableStateOf(Tab.Schedule) }`,硬编码启动 tab=课表
- `ScheduleScreen.kt:79` — `var viewMode by remember { mutableStateOf(ViewMode.Full) }`,硬编码启动视图=周视图(ViewMode.Full);ViewMode 是 ScheduleScreen.kt:66 的私有枚举 Full/Cards
- 用户说的"周视图/网格"就是 ScheduleScreen 内的 ViewMode 切换(SegmentedSwitcher,标签来自 R.string.view_full/view_cards),不是底部 Tab 切换
- 偏好存取: `AppPrefs.kt` SharedPreferences,KEY 常量 + get/set 对成对出现(参照 getGridSubInfo/setGridSubInfo:174-179 模式)
- 通用设置页: `GeneralSettingsScreen.kt`,"课程显示"分节(119 行 SectionHeader appearance_section_display)下已有 displayMode/gridSubInfo/visibleDays/showDate 等 SettingsCard 折叠卡,单选项组件 = DisplayModeOption(SettingsCards.kt:94)
- 语言资源: values/{,en,es,ja,zh-rCN,zh-rTW}/strings.xml 六份

## 方案

新增偏好 `startup_view`("full" | "cards",默认 "full" 保持现状),通用设置"课程显示"分节里加一张 SettingsCard 单选卡。ScheduleScreen 初始化 viewMode 时读该偏好。切换器手动切换仍即时生效(仅影响启动默认)。

## Task 1: AppPrefs 启动视图键 + ScheduleScreen 读取

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/util/AppPrefs.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleScreen.kt`

**Interfaces:**
- `AppPrefs.getStartView(ctx): String` → "full"/"cards",默认 "full"
- `AppPrefs.setStartView(ctx, v: String)` → require(v == "full" || v == "cards"),参照 setGridSubInfo 的 require 校验
- KEY: `const val KEY_START_VIEW = "start_view"`
- ScheduleScreen.kt:79 改为 `var viewMode by remember { mutableStateOf(if (AppPrefs.getStartView(context) == "cards") ViewMode.Cards else ViewMode.Full) }`
  注意: context 声明在 82 行(`val context = ...LocalContext.current`),启动读取需上移 context 声明或在 viewMode 初始化处直接取 LocalContext.current,保持可编译

- [ ] Step 1: AppPrefs 加 KEY + get/set(带 require 校验,注释风格对齐现有中文行注释)
- [ ] Step 2: ScheduleScreen 初始 viewMode 读偏好
- [ ] Step 3: `./gradlew :app:compileDebugKotlin` 通过(静态验证,🚫不装模拟器不跑 app)

## Task 2: 通用设置加"启动页"单选卡 + 六语言字符串

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/mine/GeneralSettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`, `values-en/strings.xml`, `values-es/strings.xml`, `values-ja/strings.xml`, `values-zh-rCN/strings.xml`, `values-zh-rTW/strings.xml`

**Interfaces:**
- 新字符串 4 个(key 名): `settings_start_view`(卡标题) / `settings_start_view_full`(选项:周视图) / `settings_start_view_cards`(选项:网格) / `settings_start_view_sub`(副文案:仅影响启动时打开的页面)
  - zh-CN: 启动默认页 / 周视图 / 网格 / 仅影响启动时进入的视图,随时可在课表页顶部切换
  - en: Default view on launch / Week view / Grid / Only affects the view shown at launch; switch anytime from the schedule top bar
  - zh-rTW/ja/es 对应翻译
  - 选项 label 直接复用 view_full/view_cards 亦可,但独立 key 便于将来文案分化——用独立 key
- GeneralSettingsScreen "课程显示"分节内(显示日期卡之后)加:
  `SettingsCard(title = stringResource(R.string.settings_start_view), expanded = "startView" in expandedSections, onToggle = { toggleSection("startView") })`,内部两个 DisplayModeOption(full/cards),选中态 = 本地 state,点击即 `AppPrefs.setStartView(context, ...)` 持久化(参照 displayMode 卡模式;该偏好不涉及小组件,无需 refreshWidgets)

- [ ] Step 1: 六份 strings.xml 加 4 个 key(放 settings_display_mode 附近,顺序一致)
- [ ] Step 2: GeneralSettingsScreen 加卡 + state + 持久化
- [ ] Step 3: `./gradlew :app:compileDebugKotlin` 通过 + `./gradlew :app:processDebugResources` 资源校验通过

## Self-Review

- 两任务边界清晰: Task 1 数据+读取,Task 2 设置 UI;无循环依赖
- 默认 "full" = 完全兼容现状,老用户无感
- 手动切换 SegmentSwitcher 逻辑(136 行 onSelect = { viewMode = it })不动,只改初值来源
- 🚫 不启动模拟器/app;只做编译+资源校验
- 无占位符;全部文件路径/行号/key 名已核实(2026-09-01 当前 HEAD aedc62c)
