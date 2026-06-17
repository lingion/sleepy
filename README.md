# Sleepy

Android 本地课程表应用。多格式导入课表，查本周、查今天、手动补课。

## 导入课表

支持以下格式自动识别：

| 格式 | 来源 |
|------|------|
| WakeUp 分享文本 | WakeUp 课表 App 分享 |
| WakeUp JSON | WakeUp 导出 JSON |
| ICS (iCalendar) | 教务系统导出 |
| CSV | 表格类课表 |
| HTML | 网页课表 |
| 纯文本 | 固定格式文本 |

导入前展示预览，确认后写入数据库。导入链强制确认两项语义信息：

- 第一周开始日期
- 每节课起止时间

避免"导入成功但周次对不上"的问题。

## 课表管理

支持多张课表并存，在"所有课表"中切换。每张课表可编辑标题、学期开始日期、总周数、节次时间表。

导入时可选择覆盖当前课表、追加到当前课表、或创建新课表。

## 查看课表

三种视图：

| 视图 | 内容 |
|------|------|
| 周视图 | 一周 7 天卡片 + 每日课程详情列表 |
| 网格视图 | 7×5 时段网格，课程卡片着色 |
| 今日 | 当天课程列表，含当前时段高亮 |

周视图顶部 WeekStrip 展示 7 天摘要小卡片：星期 + 课程数芯片 + 前三门课名。网格视图按时段×星期排列课程卡片，卡片颜色按课程名匹配。

## 课程编辑

- 手动添加课程：课程名、老师、地点、笔记、开始周、结束周、时段
- 从课程详情页进入编辑
- 支持长按拖动调整时段
- 课程详情底部弹出面板，展示完整信息

## 导出

支持三种导出格式：

- WakeUp JSON（可导入其他课表 App）
- WakeUp 分享文本
- ICS 日历（可导入系统日历）

## 小组件

提供"今日课程"桌面小组件，基于 Glance AppWidget，展示当天课程列表。

## 通知

通过 WorkManager 调度每日课程提醒通知。每周一上午根据当前课表重新排期，每节课前发送提醒。

## 深色模式

支持浅色 / 深色，遵循 Material You 色彩体系。切换即时生效。

## 数据

全部数据存储在设备本地，使用 Room 数据库。不上传、不同步、不需要账号。

## 技术

| 组件 | 选型 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 数据库 | Room |
| 偏好存储 | DataStore |
| 小组件 | Glance |
| 通知 | WorkManager |
| 导航 | Navigation Compose |
| JSON | kotlinx.serialization |
| 图片 | Coil |
| 最低 API | 24 (Android 7.0) |
| 目标 API | 35 (Android 15) |

## 项目结构

```
app/src/main/java/com/lingion/sleepy/
  data/
    entity/        CourseEntity, TimeTableEntity
    dao/           CourseDao, TimeTableDao
    repository/    ScheduleRepository
    parser/        ScheduleParser (import), ScheduleExporter (export)
  ui/
    screen/
      schedule/    课表首页 (ScheduleScreen + ViewModel)
      today/       今日页 (TodayScreen)
      imports/     导入页 (ImportScreen)
      edit/        手动添加课程 (AddCourseScreen)
      mine/        我的 (MineScreen, EditTableScreen, AllTablesScreen)
      manage/      课表管理 (ManagementPage)
    component/     WeekStrip, CourseTableView, CourseDetailSheet 等
    theme/         Material You 主题 & 课程调色板
  widget/
    TodayWidget.kt            桌面小组件
    CourseNotificationScheduler.kt  通知调度
  util/             工具类 (DateUtils, TimeTableUtils)
```

## 构建

需要 JDK 17+ 和 Android SDK 35。

```bash
./gradlew assembleDebug
```

产出两个 ABI 的 APK：

- `app-arm64-v8a-debug.apk` — 手机
- `app-x86_64-debug.apk` — 模拟器

## 安装

```bash
# 模拟器
adb install -r app/build/outputs/apk/debug/app-x86_64-debug.apk

# 手机
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

## 版本

最新发布：`v1.0.4` — [GitHub Releases](https://github.com/lingion/sleepy/releases)

每个 Release 附带 APK 直接下载安装。

## License

GPL-3.0
