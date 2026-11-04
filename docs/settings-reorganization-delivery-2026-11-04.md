# Sleepy 设置重组交付核验

本次设置重组覆盖 90 项迁移、排序及保留记录。本文列出各项代码落点、固定顺序、自动化验证结果和实机验证边界。

## 目录与顺序不变量

```text
我的
|-- 课表管理：所有课表、作息表、节假日与调休
|-- 应用设置
|   |-- 提醒
|   |-- 外观
|   |   |-- 外观模式、主题色彩、底部导航、课程配色
|   |   `-- 课表显示：共同、周视图、网格、节次表头、冲突课程、节假日显示
|   |-- 通用：语言、性能、编辑操作
|   `-- 小组件
|-- 数据：备份与恢复
`-- 关于
```

底栏样式紧邻底栏主题，前者在上。小组件紧跟通用。两栏布局方式和晚间起始时间跟随各自父开关。保存位于删除之前。共享日期编辑位于调休映射之后。

## 90 项逐项落点

| 编号 | 原项目 | 实际落点 | 处理 | 主要文件 |
|---|---|---|---|---|
| G01 | 课表显示 / 课程时间显示 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| G02 | 课表显示 / 网格卡片副信息 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| G03 | 主页显示设置 / 周视图两栏显示 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| G04 | 两栏显示 / 按天对半分、按课程数平衡 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| G05 | 主页显示设置 / 周视图隐藏无课日 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| G06 | 主页显示设置 / 周视图显示别名 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| G07 | 主页显示设置 / 网格视图显示别名 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| G08 | 主页显示设置 / 网格视图表头显示日期 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| G09 | 主页显示设置 / 显示周视图/网格视图切换栏 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| G10 | 课表显示 / 显示星期 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| G11 | 课表显示 / 节假日调休设置 | 节假日与调休 | 已迁移、重排或补齐入口 | `HolidaySettingsScreen.kt` |
| G12 | 画面与导航 / 启动默认页 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| G13 | 画面与导航 / 高刷新率 | 通用 / 性能 | 保留原功能和作用域，核对新分组 | `GeneralSettingsScreen.kt` |
| G14 | 画面与导航 / 底栏样式：贴底、悬浮 | 外观 / 底部导航 | 已迁移、重排或补齐入口 | `AppearanceScreen.kt` |
| G15 | 语言 / Language | 通用 / 语言 | 保留原功能和作用域，核对新分组 | `GeneralSettingsScreen.kt` |
| G16 | 实验室 / 课表自适应行高 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| G17 | 实验室 / 双指捏放调整行高 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| G18 | 实验室 / 自动收起空白晚间 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| G19 | 自动收起空白晚间 / 晚间起始时间 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| G20 | 实验室 / 小组件竖排标点优化 | 小组件 / 高级选项 | 已迁移、重排或补齐入口 | `WidgetSettingsScreen.kt` |
| G21 | 实验室 / 显示网格分隔线 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| G22 | 实验室 / 长课间留白 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| G23 | 实验室 / 撤回保留步数 | 通用 / 编辑操作 | 已迁移、重排或补齐入口 | `GeneralSettingsScreen.kt` |
| G24 | 数据迁移 / 导出全量迁移配置 | 备份与恢复 | 已迁移、重排或补齐入口 | `BackupScreen.kt` |
| G25 | 数据迁移 / 从备份导入 | 备份与恢复 | 已迁移、重排或补齐入口 | `BackupScreen.kt` |
| A01 | 主题色彩在深浅模式之前 | 外观 | 已迁移、重排或补齐入口 | `AppearanceScreen.kt` |
| A02 | 系统主题、预设、自定义、新建卡 | 外观 | 保留原功能和作用域，核对新分组 | `AppearanceScreen.kt` |
| A03 | 外观中的 Dock 主题 | 外观 | 已迁移、重排或补齐入口 | `AppearanceScreen.kt` |
| A04 | 课程配色 | 外观 | 保留原功能和作用域，核对新分组 | `AppearanceScreen.kt` |
| A05 | 自定义色相、课程预览、重新配色 | 外观 | 已迁移、重排或补齐入口 | `AppearanceScreen.kt` |
| A06 | 外观页内联的网格缩放 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayScreen.kt` |
| A07 | 外观页内联的周视图缩放 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayScreen.kt` |
| A08 | 外观页内联的圆角比例 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayScreen.kt` |
| A09 | 外观页内联的冲突样式及三种参数 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayScreen.kt` |
| A10 | 节次表头为外观一级分组及子页 | 外观 / 课表显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayScreen.kt` |
| A11 | 节次表头：可选样式预览在前，随后布局及细项 | 外观 / 课表显示 / 节次表头 | 已迁移、重排或补齐入口 | `PeriodHeaderSettingsScreen.kt` |
| A12 | 旧式表头隐藏时间后仍提供时间对齐 | 外观 / 课表显示 / 节次表头 | 已迁移、重排或补齐入口 | `PeriodHeaderSettingsScreen.kt` |
| A13 | 自定义主题：名称、预览、随机、主色生成、手工编辑 | 外观 / 自定义主题 | 已迁移、重排或补齐入口 | `CustomThemeEditorScreen.kt` |
| A14 | 自定义主题编辑及取色弹窗 | 外观 / 自定义主题 | 保留原功能和作用域，核对新分组 | `CustomThemeEditorScreen.kt` |
| H01 | 法定节假日课程灰显 | 外观 / 课表显示 / 节假日显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| H02 | 周末课程灰显 | 外观 / 课表显示 / 节假日显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| H03 | 忽略补班日 | 外观 / 课表显示 / 节假日显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| H04 | 灰显样式：灰色、删除线 | 外观 / 课表显示 / 节假日显示 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt` |
| H05 | 年份、数据源、刷新及失败状态 | 节假日与调休 | 已迁移、重排或补齐入口 | `HolidaySettingsScreen.kt` |
| H06 | 调休说明、局部课表选择 | 节假日与调休 | 已迁移、重排或补齐入口 | `HolidaySettingsScreen.kt` |
| H07 | 放假日逐日映射、补班日期候选 | 节假日与调休 | 保留原功能和作用域，核对新分组 | `HolidaySettingsScreen.kt` |
| H08 | 添加自定义日期、编辑假期段、开始/结束日期、名称 | 节假日与调休 | 已迁移、重排或补齐入口 | `HolidaySettingsScreen.kt` |
| H09 | 失效映射、已删除日期段、清除与恢复 | 节假日与调休 | 已迁移、重排或补齐入口 | `HolidaySettingsScreen.kt` |
| H10 | 提醒页的调休设置跳转 | 提醒 / 节假日与调休入口 | 保留原功能和作用域，核对新分组 | `ReminderScreen.kt` |
| R01 | 开启提醒 | 提醒 | 保留原功能和作用域，核对新分组 | `ReminderScreen.kt` |
| R02 | 课表提醒子页 | 提醒 / 需要提醒的课表 | 已迁移、重排或补齐入口 | `TableReminderScreen.kt` |
| R03 | 每节课前提醒 | 提醒 | 已迁移、重排或补齐入口 | `ReminderScreen.kt` |
| R04 | 横幅、流体云形态 | 提醒 | 保留原功能和作用域，核对新分组 | `ReminderScreen.kt` |
| R05 | 流体云主显示内容 | 提醒 | 保留原功能和作用域，核对新分组 | `ReminderScreen.kt` |
| R06 | 流体云测试、结束投放、厂商实况能力、实况授权、去设置 | 提醒 | 已迁移、重排或补齐入口 | `ReminderScreen.kt` |
| R07 | 通知权限、精确提醒、电池优化诊断 | 提醒 | 已迁移、重排或补齐入口 | `ReminderScreen.kt` |
| R08 | 小组件绑定诊断 | 提醒 | 保留原功能和作用域，核对新分组 | `ReminderScreen.kt` |
| R09 | 每日提醒、今日摘要、明日预告 | 提醒 | 已迁移、重排或补齐入口 | `ReminderScreen.kt` |
| R10 | 按日期设置提醒 | 提醒 | 已迁移、重排或补齐入口 | `ReminderScreen.kt` |
| R11 | 四类日期说明重复 | 提醒 | 已迁移、重排或补齐入口 | `ReminderScreen.kt` |
| R12 | 上课自动勿扰 | 提醒 | 保留原功能和作用域，核对新分组 | `ReminderScreen.kt` |
| W01 | 小组件独立入口 | 我的 / 通用之后 | 保留原功能和作用域，核对新分组 | `MineScreen.kt` |
| W02 | 添加或管理桌面小组件在开关组之后 | 小组件 | 已迁移、重排或补齐入口 | `WidgetSettingsScreen.kt` |
| W03 | 小组件统一课程底色 | 小组件 | 保留原功能和作用域，核对新分组 | `WidgetSettingsScreen.kt` |
| W04 | 周视图小组件分隔线 | 小组件 | 保留原功能和作用域，核对新分组 | `WidgetSettingsScreen.kt` |
| W05 | 没课时显示最近有课的一天 | 外观 / 课表显示，小组件保留跳转 | 已迁移、重排或补齐入口 | `ScheduleDisplayGroups.kt、WidgetSettingsScreen.kt` |
| W06 | 小组件竖排标点优化 | 小组件 | 已迁移、重排或补齐入口 | `WidgetSettingsScreen.kt` |
| W07 | 刷新所有小组件 | 小组件 | 保留原功能和作用域，核对新分组 | `WidgetSettingsScreen.kt` |
| W08 | 管理子页：已放置组件列表、添加入口 | 小组件 / 管理 | 已迁移、重排或补齐入口 | `WidgetManagementScreen.kt` |
| W09 | 添加画廊：组件家族、展开尺寸变体 | 小组件 / 添加画廊 | 保留原功能和作用域，核对新分组 | `WidgetGalleryScreen.kt` |
| W10 | 编辑组件：呈现的课表 | 小组件 / 编辑实例 | 保留原功能和作用域，核对新分组 | `WidgetEditScreen.kt、WidgetEditScrollSection.kt` |
| W11 | 编辑组件：课程名显示 | 小组件 / 编辑实例 | 保留原功能和作用域，核对新分组 | `WidgetEditScreen.kt、WidgetEditScrollSection.kt` |
| W12 | 编辑组件：三天窗口 | 小组件 / 编辑实例 | 已迁移、重排或补齐入口 | `WidgetEditScreen.kt、WidgetEditScrollSection.kt` |
| W13 | 编辑组件：4×2 网格布局及星期标签侧边 | 小组件 / 编辑实例 | 已迁移、重排或补齐入口 | `WidgetEditScreen.kt、WidgetEditScrollSection.kt` |
| W14 | 编辑组件：强制滚动 | 小组件 / 编辑实例 | 已迁移、重排或补齐入口 | `WidgetEditScreen.kt、WidgetEditScrollSection.kt` |
| O01 | 所有课表 | 所有课表 | 已迁移、重排或补齐入口 | `AllTablesScreen.kt、EditTableScreen.kt` |
| O02 | 编辑课表 | 编辑课表 | 已迁移、重排或补齐入口 | `EditTableScreen.kt` |
| O03 | 作息表列表 | 作息表 | 保留原功能和作用域，核对新分组 | `PeriodTablesScreen.kt` |
| O04 | 编辑作息表 | 编辑作息表 | 保留原功能和作用域，核对新分组 | `PeriodTableEditScreen.kt` |
| O05 | 手动和自动节次编辑 | 编辑作息表 / 手动与自动节次 | 保留原功能和作用域，核对新分组 | `TimeSlotEditor.kt` |
| O06 | 作息修改冲突处理弹层 | 编辑作息表 / 保存冲突处理 | 保留原功能和作用域，核对新分组 | `PeriodTableEditScreen.kt` |
| O07 | 课程清单 | 课程清单 | 保留原功能和作用域，核对新分组 | `CourseListScreen.kt` |
| O08 | 全量备份与恢复 | 备份与恢复 | 已迁移、重排或补齐入口 | `BackupScreen.kt` |
| O09 | 导出课表/作息表 | 管理 / 导出 | 已迁移、重排或补齐入口 | `ExportScreen.kt` |
| O10 | 课表导出选项 | 管理 / 导出 / 课表 | 已迁移、重排或补齐入口 | `ExportScreen.kt` |
| O11 | 作息表导出 | 管理 / 导出 / 作息表 | 保留原功能和作用域，核对新分组 | `ExportScreen.kt` |
| O12 | 系统日历导入弹窗 | 管理 / 导出 / 日历导入 | 保留原功能和作用域，核对新分组 | `ExportScreen.kt` |
| O13 | 关于：自动检查更新在页面底部 | 关于 / 版本与更新 | 已迁移、重排或补齐入口 | `AboutScreen.kt` |
| O14 | 关于其余项目 | 关于 | 已迁移、重排或补齐入口 | `AboutScreen.kt` |
| O15 | 开源声明及更新日志子内容 | 关于 / 声明与更新日志 | 保留原功能和作用域，核对新分组 | `AboutScreen.kt、LicenseScreen.kt` |

## 需求与实现行为

| 需求 | 对应实现 |
|---|---|
| 底栏设置归入外观 | 底部导航组承接原偏好与应用层回调，先样式后主题。 |
| 整理所有设置子页 | 上表覆盖 90 项，包含迁移、重排及保留项。 |
| 子页跳转保持编辑上下文 | 入口携带课表标识，返回时保留未保存草稿，共享作息保存仍需确认修改范围。 |

## 验证记录

完成条件为全部 90 项有唯一落点且入口接通，原偏好及数据作用域保留，相关测试和静态检查完成。三个条件均已完成，验证日期为 2026 年 11 月 4 日。

已核验的独立检查：

- 原方案与交付表的编号集合一致，恰好 90 项，没有漏项或重复编号。
- `./gradlew --offline :app:testDebugUnitTest --console=plain --max-workers=2` 成功结束。XML 报告共 437 个测试套件、3,691 项测试，其中 3,689 项通过、2 项跳过、零失败及错误。两项跳过均为 WakeUP 协议验证，分别需要 `SLEEPY_WAKEUP_XINPUT` 与 `SLEEPY_WAKEUP_LIVE` 环境条件，不属于本次设置迁移测试。
- `:app:lintDebug` 完成，XML 报告统计为零错误、576 条警告、40 条提示。没有把仍存在的警告描述为全部清零；涉及本次修改文件的条目为默认语言环境、窗口尺寸读取、数值状态装箱和 KTX 写法提示。
- `python3 scripts/test_settings_appearance_contract.py` 通过 8 项；`python3 scripts/check_reminder_settings_structure.py` 通过 8 项。
- 新增的四组设置资源共 45 个键，在六套语言目录中一致，XML 解析与重复键检查通过。
- `EditTableDraftRestoreTest` 的三个 Compose 交互及保存恢复用例通过。共享作息用例曾临时禁用保存前同步，真实失败于缺少确认弹层；恢复修复后通过，并核验确认前数据库不变以及选择只修改当前课表后的结果。
- 独立审查覆盖全部 90 项；发现的恢复草稿与共享作息策略不同步问题已经修复，定向复核关闭了该问题。
- `git diff --check` 通过。

单测报告位于 `app/build/reports/tests/testDebugUnitTest/index.html`，lint 报告位于 `app/build/reports/lint-results-debug.html`。源码契约锁定保存前同步草稿、读取最新策略及确认顺序，交互测试覆盖确认前数据库不变和确认后的实际保存。

验证边界：Compose 交互在 Robolectric 中执行。状态恢复用例通过保存状态容器与清除内存策略模拟生命周期差异，未执行手机上的真实进程终止与重启。本次没有完成手机视觉或系统权限交互验证。
