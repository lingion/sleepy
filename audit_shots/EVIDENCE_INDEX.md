# Sleepy QA Evidence — 2026-08-28

## 平台

- iOS: Simulator iPhone 14 Pro (E457BAD9-947A-469A-BD6B-0286F69267CD), Xcode + xcodebuild + xctest
- Android: 真实设备 (OPPO CPH2723IN, Android 16, 1216×2640) → 物理断开后切到模拟器 (sleepy_test AVD, Android 13, 1080×2340)

## iOS 端

### 单元 + UI 串行回归 (同 simulator, 同 bundle)

| Suite | 结果 |
|---|---|
| SleepyTests (单元) | 144 / 144 ✅ |
| UIElementAuditUITests (A1-A6 全矩阵) | 42 / 42 ✅ |
| ManageImportUITests | 7 / 7 ✅ |
| MineSettingsUITests | 13 / 13 ✅ |
| SchoolSelectUITests | 5 / 5 ✅ |
| TableEditUITests | 7 / 7 ✅ |
| SleepyUITests (核心导航) | 11 / 11 ✅ |
| WidgetArchivingUITests (5 widget 归档, log stream 已就绪) | 1 / 1 ✅ |
| **合计** | **230 / 230 ✅** |

### iOS Widget 归档证据

`xcrun simctl spawn booted log stream --predicate 'processImagePath CONTAINS "SleepyWidget"'`

- TodayWidgetRV: 6 次
- TwoDayWidgetRV: 4 次
- WeekListWidgetRV: 12 次
- WeekViewWidgetRV: 4 次
- WeekGridWidgetV19: 4 次
- Request ended success: 15 行
- failedToEncode: 0

### iOS 修复

- 根因: `AppRoot` 直接挂载 `JwImportFlow` sheet 是独立的 presentation tree，不会继承主 `NavigationStack` 上的 `SleepyThemeProvider` 与 `localCoursePalette` 环境，导致 SchoolSelect 深色下回落到 `lightScheme` 默认值。
- 修复: `SleepyApp.swift` 在 `.sheet` 内容里显式注入 `SleepyThemeProvider` 与 `.preferredColorScheme(.dark/.light)`。
- 回归: `testA6_SchoolSelect_Dark` 在 `assertDarkScreenshot` 下要求主体平均 luma < 100 (修复前 ~239, 修复后 ~30)。

### iOS 待完成 (非 iOS 阻塞)

- iOS widget 视觉截图 (需把 widget 加到 home screen, simulator 自动化窗口复杂; 当前仅做归档验证)
- iOS 浅色像素矩阵 + 浅色/深色像素对齐全套

## Android 端

### 构建 / 安装

- `./gradlew assembleDebug --offline`: BUILD SUCCESSFUL
- `app-arm64-v8a-debug.apk` 安装到设备 (后续物理断开), 然后切到模拟器 `app-x86_64-debug.apk`
- 启动 `com.lingion.sleepy/.MainActivity`, dump XML + screencap 全部成功

### Android 截图证据 (`/Users/lingion_k/Desktop/sleepy/audit_shots/emu/`)

| Tab / 路径 | 文件 | 内容 |
|---|---|---|
| 课表主屏 | A1_schedule_default_light.png | 真实 populated: 周三体育, 周六高数, 周日体育 |
| 今日 | A1_today_default_light.png | 1 门 周一高等数学 |
| 课表管理 | A1_manage_default_light.png | 导入 / 新建 / 手动 / 编辑 / 当前课表 (8 门) |
| 我的 | A1_mine_default_light.png | 课表数 2 / 课程数 3 / 当前周 1 |
| Mine → 所有课表 | A1_mine_all_tables_default_light.png | 2 张课表 (导入课表, 测试课表) |
| Mine → 导出 | A1_mine_export_default_light.png | WakeUp JSON / 分享文本 / ICS |
| Mine → 外观 | A1_mine_appearance_default_light.png | 跟随系统 / 6 色板 |
| Mine → 通用 | A1_mine_general_default_light.png | 课程显示 / 网格副信息 / 节假日灰显 / 小组件设置 |
| Mine → 关于 | A1_mine_about_default_light.png | v1.0.36-debug (37) / Lingion / 开源地址 |
| Mine → 提醒 | A1_mine_reminder_default_light.png | 开启提醒 toggle |
| ImportSheet | A1_importsheet_default_light.png | 教务直连 / 粘贴课表文本 / 从文件导入 / 4 格式 |
| 学校选择 (JW) | A1_jw_schoolselect_default_light.png | 146 所学校, 北大/化工/保定等 |
| 粘贴课表文本 | A1_mine_paste_default_light.png | paste input + 预览导入 按钮 |

### Android 五类 widget (`WidgetRenderActivity`)

| Widget | 截图 | 渲染观察 |
|---|---|---|
| today | A_widget_today.png | 课程卡: 高数/物理/英语 + 时间段 + 教室 |
| twoday | A_widget_twoday.png | 两天课程: 周三体育, 周六高数 |
| weeklist | A_widget_weeklist.png | 周列表 + 周内课程计数 |
| weekview | A_widget_weekview.png | 周次表头 3-10 + 课程块 |
| weekgrid | A_widget_weekgrid.png | 完整周课表网格, 节次 1-12 |

### Android 当前阻塞

- 物理 USB 设备在 ~17:46 后中断, 后续所有真机路径不可达
- ADB 模拟器替换后仅恢复基础 UI dump + 截图, 未完成:
  - Mine → 编辑当前课表, 通用 → 课表显示各项 toggle
  - ImportSheet → 3 个方法实际效果 (教务直连 URL, 粘贴解析, 文件 picker)
  - 所有次级页面 (AddCourse, EditTable, AllTables, Holiday, Reminder, Appearance 各子选项)
  - 全部真实点击 / 边界 (空 / malformed / 重复 / 取消)
  - 6 语言切换
  - 浅色 / 深色对比
  - 真机深色像素回归断言

## 结论

- **iOS**: 230/230 测试通过, 5 widget 归档成功, SchoolSelect 深色主题已修复并像素回归。浅色像素矩阵 + 真实 widget 桌面截图待补。
- **Android**: emulator 上完成 13 张基础 UI 截图 + 5 widget 渲染截图, 真机断连阻塞全量真机 QA。Mine 6 个子项 / ImportSheet 3 方法 / 5 widget 桌面点击 / 6 语言 / 浅深色像素回归全部未完成。
- 完整 iOS+Android 全矩阵尚未达成; 不应声称"全部 QA 完成"。
