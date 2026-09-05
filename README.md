<p align="center">
  <img src="docs/logo.png" width="120">
</p>

<h1 align="center">Sleepy · 轻课表</h1>

<p align="center">
  A clean, Material You Android schedule/timetable app built with Kotlin + Jetpack Compose.<br>
  Material You 设计的纯净 Android 课程表 App — 多视图 · 教务导入 · 桌面小组件 · HSV 自定义配色
</p>

<p align="center">
  <a href="https://github.com/lingion/sleepy/releases"><img src="https://img.shields.io/github/v/release/lingion/sleepy?style=flat-square&label=version" alt="Latest Release"></a>
  <img src="https://img.shields.io/github/stars/lingion/sleepy?style=flat-square&logo=github" alt="Stars">
  <img src="https://img.shields.io/github/downloads/lingion/sleepy/total?style=flat-square" alt="Downloads">
  <img src="https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/lang-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square" alt="Compose">
  <img src="https://img.shields.io/badge/license-GPL--3.0-blue?style=flat-square" alt="License">
  <img src="https://img.shields.io/badge/minSDK-26_(Android_8.0)-green?style=flat-square" alt="Min SDK">
</p>

<p align="center">
  <a href="README.md">中文</a> · <a href="README_EN.md">English</a> · <a href="https://github.com/lingion/sleepy/releases">Download APK</a> · <a href="docs/adapt-kit/README.md">让你的学校支持教务直连</a>
</p>

---

> **Keywords (SEO):** Android schedule app, 课程表, 课表, timetable, university schedule, Jetpack Compose, Material You, Chinese university academic system import, wisedu, 强智教务, 正方教务, URP, home screen widget, HSV color picker, 开源课表, 开源课程表

---

## 截图一览

<p align="center">
  <img src="docs/screenshots/01-schedule-week.png" width="30%">
  <img src="docs/screenshots/02-schedule-grid.png" width="30%">
  <img src="docs/screenshots/03-today.png" width="30%">
</p>
<p align="center">
  <img src="docs/screenshots/widget-weekgrid.png" width="22%">
  <img src="docs/screenshots/widget-weeklist.png" width="22%">
  <img src="docs/screenshots/widget-today.png" width="22%">
  <img src="docs/screenshots/widget-twoday.png" width="22%">
</p>
<p align="center">
  <img src="docs/screenshots/22-reminder.png" width="30%">
  <img src="docs/screenshots/20-import-bottomsheet.png" width="30%">
  <img src="docs/screenshots/21-about.png" width="30%">
</p>

<p align="center">
  Android 8.0+ · 包名 <code>com.lingion.sleepy</code>
</p>

---

## 概要

| 项 | 值 |
|---|---|
| 包名 | `com.lingion.sleepy` |
| 最低 SDK | `26` (Android 8.0；OPPO SeedlingSupportSDK 3.0.7 要求) |
| 目标 SDK | `37` |
| 架构 | arm64-v8a / armeabi-v7a / x86_64 |
| 语言 | zh-CN · zh-TW · en · ja · es |

Sleepy 乃 Android 课程表工具。主旨：**轻、快、准**。支持教务直连导入、多格式解析、五类桌面 Widget、每日课程通知、深色模式和多套主题配色。版本号与当前支持的学校目录以应用内「关于」和 GitHub Releases 为准。

### v1.0.49

- 新增广东医科大学、广州医科大学和吉林工商学院教务直连。
- 新增 `sleepy-v1` 原生纯文本导入导出格式，旧格式继续保留。
- 新增超星综合教务个人课表解析，支持周次拆分、连续节次合并和 HTML 字段清理。
- 修正 179 所学校目录中的 60 条入口或协议记录，并处理死链和重复条目。
- 武汉理工大学入口改走 `forceCas`，导入前自动切换学生角色；采集器增加重试和采集日志。
- 关于页新增独立的开源声明页面，列出 26 个上游项目及许可证。

---

## 三视图

课表主页内置三种视图，顶部一键切换。

| 视图 | 截图 |
|---|---|
| **周视图**（7 日横排 × N 节） | <p align="left"><img src="docs/screenshots/01-schedule-week.png" width="280"></p> |
| **网格视图**（时间网格 · 课程色块） | <p align="left"><img src="docs/screenshots/02-schedule-grid.png" width="280"></p> |
| **今日视图**（底部"今日"Tab · 当日课程） | <p align="left"><img src="docs/screenshots/03-today.png" width="280"></p> |

特性：
- 左/右滑周切换器，实时算周次
- **v1.0.19：手指左右滑动切换周次**（HorizontalPager），周视图与网格视图均支持
- **v1.0.21：提醒功能重做** — 独立提醒页面，master toggle 权限流（拒绝可重试），每日提醒（自定义时间 + 动态内容），每节课前提醒（自由输入分钟数，胶囊型输入框），新增「关于」页面（版本/作者/开源声明）
- 课程按"起止周+单双周+起止节"自动过滤当前周
- 点击课程卡片弹出详情底部弹窗

---

## 多课表管理

可同时管理多张独立课表，每张表拥有自己的节次时间表、开学日期、最大周数。

<p align="left">
  <img src="docs/screenshots/05-all-tables.png" width="280">
  <img src="docs/screenshots/04-mine.png" width="280">
</p>

> 左：所有课表列表（齿轮进入编辑）
> 右：我的页面（统计 + 入口）

新建/编辑课表必填项：名称、开始日期、最大周数、节次时间表（手动或自动模式）。

---

## 节次配置（手动 / 自动双模式）

v1.0.16 引入智能节次编辑器。手动模式逐节设起止；自动模式输入每节时长 + 总节数 + 首节时间 + 课间模板，自动推算全部时间。

<p align="left">
  <img src="docs/screenshots/06-edit-table-manual.png" width="280">
  <img src="docs/screenshots/07-edit-table-auto.png" width="280">
</p>

> 左：手动模式（展开的 12 节 HEU 真实节次）
> 右：自动模式（每节时长 / 总节数 / 首节 / 大小课间模板 / 预览切换）

自动模式特性：
- 跨组互斥（同一 transition 不能同时属大/小课间）
- 默认 0 分钟连续，无间隔亦合法
- 卡片网格多选，点击直观反馈

---

## 教务系统导入

入口在底栏「课表管理」→「导入课表」弹窗。现有导入入口都会先预览，再由你确认导入；不会静默覆盖现有课表。

<p align="left">
  <img src="docs/screenshots/20-import-bottomsheet.png" width="320">
</p>

| 入口 | 流程 |
|---|---|
| **教务直连** | 选择已收录学校，或在搜索框输入教务 URL → WebView 登录 → 自动抓取课表 → 预览 → 导入 |
| **粘贴课表文本** | 粘贴任意格式文本 → 自动识别格式 → 预览 → 导入 |
| **从文件导入** | 选择 `.json` / `.ics` / `.csv` / `.html` 文件 → 预览 → 导入 |

### 教务协议示例

协议目录会随学校适配持续增加。下面列出代码中使用的协议族和代表性变体，不能替代应用内当前学校目录：

| 协议 | 说明 |
|---|---|
| `wisedu` | 金智教务（JSON API 直连，如哈尔滨工程大学） |
| `qz` / `qz_old` / `qz_crazy` / `qz_br` / `qz_with_node` | 强智教务（5 变体） |
| `zf` / `zf_1` / `zf_new` | 正方教务（3 变体） |
| `urp` / `urp_new` | URP 教务（2 变体） |
| `cf` | 青果教务 |
| `classic_eams` | 经典金智 EAMS（电子科大/上财/湖南师大/南航 等） |
| `eams5` | supwisdom EAMS5（合工大/安徽大学/矿大北京） |
| `whut` | 武汉理工大学（金智变体） |
| `cqu` | 重庆大学统一门户 |
| `seu` | 东南大学 |
| `zju` | 浙江大学 |
| `ustc` | 中国科学技术大学 |
| `scu` | 四川大学 |
| `neu` | 东北大学 |
| `hnust` | 湖南科技大学教务 |
| `hniu` | 湖南信息职业技术学院教务 |
| `pku` | 北京大学 |
| `bnuz` | 北师珠 |
| `chaoxing` | 超星综合教务（当前接入吉林工商学院个人课表） |

### 学校目录与自定义 URL

学校目录会随版本更新。已收录学校可以直接选择；如果学校暂时不在目录中，也可以在搜索框输入教务系统 URL，Sleepy 会尝试根据 URL 识别协议，再打开该地址进行登录和导入。能否成功取到课表取决于学校页面、登录方式和对应解析器。

如果 URL 识别或解析仍不成功，再提交适配申请。优先提供教务 URL 和失败现象；需要进一步定位时，再按 **[适配采集教程](docs/adapt-kit/README.md)** 提供页面或网络数据。不要在 issue 中提交账号、密码、验证码或其他个人信息。也可以直接 [开一个适配申请](https://github.com/lingion/sleepy/issues/new?template=school_adaptation.yml)。

### 支持的文本格式

| 格式 | 识别特征 |
|---|---|
| **WakeUp 分享文本** | 以 `【来自WakeUp课程表】` 开头 |
| **WakeUp JSON** | Sleepy / WakeUp 导出的 `.json` |
| **ICS 日历** | 标准 iCalendar，可从学校教务处导出 |
| **CSV 文件** | 含表头的 `.csv`，逗号分隔 |
| **HTML 表格** | `<table>` 形式，识别表头后逐行解析 |
| **纯文本** | 一行一课，制表符分隔 |
| **sleepy-v1** | Sleepy 原生纯文本格式，带 `chk` 完整性字段和确定性记录 |

> 所有导入路径都先生成预览，确认后才写入课表。

---

## 课程编辑

<p align="left">
  <img src="docs/screenshots/18-add-course.png" width="280">
  <img src="docs/screenshots/08-course-detail.png" width="280">
</p>

> 左：手动添加/编辑单条课程
> 右：点击周视图课程卡片弹出详情底部弹窗

字段：课名 · 教师 · 教室 · 备注 · 星期 · 起止节 · 起止周 · 单双周类型 · 课程色。

---

## 导出课表

支持多种导出格式；文件型格式会保存到设备的 `Download/Sleepy/`，并触发系统分享面板。具体格式以当前版本导出页为准。

<p align="left">
  <img src="docs/screenshots/19-export.png" width="280">
</p>

> 截图：导出页（在"我的 → 导出课表"）

四种导出格式：

| 格式 | 用途 | 实现 |
|---|---|---|
| **WakeUp 兼容 JSON** | 完整课表结构，可被 WakeUp 课表等同类 App 直接导入 | `ScheduleExporter.exportWakeUpJson` |
| **分享文本** | 短文本格式（URL 编码 JSON），可粘贴到任何聊天工具 | `ScheduleExporter.exportWakeUpShareText` |
| **ICS 日历** | 标准 iCalendar 格式，可导入系统日历 / Google / Apple Calendar | `ScheduleExporter.exportIcs` |
| **sleepy-v1** | Sleepy 原生纯文本格式，可再次导入或编辑 | `SleepyNativeExporter` |

文件路径：传统文件格式使用 `Download/Sleepy/sleepy_<表名>_<时间戳>.{json|ics}`；`sleepy-v1` 通过系统分享面板导出。

旧版 WakeUp 分享文本仍可继续导入。

---

## 桌面 Widget（5 类）

五类 Widget，WorkManager 定时刷新。布局尺寸与各 launcher 自适应。

| Widget | 默认尺寸 | 用途 | 截图 |
|---|---|---|---|
| **Today** | 4×3 cell（250×180dp） | 今日课程列表 | <p align="left"><img src="docs/screenshots/widget-today.png" width="240"></p> |
| **TwoDay** | 5×3 cell（320×220dp） | 今天 + 明天（左右双栏） | <p align="left"><img src="docs/screenshots/widget-twoday.png" width="240"></p> |
| **WeekList** | 5×4 cell（320×200dp） | 7 日课程统计 + 名称 | <p align="left"><img src="docs/screenshots/widget-weeklist.png" width="240"></p> |
| **WeekView** | 5×4 cell（320×200dp） | 周视图缩略（无彩色胶囊，纯主题色） | 无独立截图 |
| **WeekGrid** | 4×5 cell（250×360dp） | 完整时间网格 + 课程块 | <p align="left"><img src="docs/screenshots/widget-weekgrid.png" width="200"></p> |

实现要点：
- 全部 5 类：v1.0.29 起为同步 RemoteViews + Canvas 渲染（OPPO 等深度定制 launcher 会冻结 Glance 的异步 SessionWorker，导致卡片不刷新，故整体移植）
- 配色与 app 主题实时同步（深色模式 + 5 主题预设）
- **三条渲染路径（主 app / WeekGrid / 截图渲染器）配色完全统一**：课程色按黄金角 (137.508°) HSL 分布，以课程所属分组哈希映射色相，均匀铺开且每门课稳定唯一
- 刷新机制：对全部 5 个 receiver 广播 `APPWIDGET_UPDATE`（系统级）+ WorkManager 每 15 分钟定时刷新

---

## 冲突课程和撤回

同一时间有多门课程时，网格视图保留两层可见内容，点击被覆盖区域可以轮换显示其他课程；三门以上的冲突不会静默丢失。叠层样式支持任意层数。

手动添加或编辑课程遇到冲突时，提示会列出星期、节次、实际重叠周次和冲突课程。确认后才保存，返回修改则不写入。

顶栏提供撤回操作，用于回退最近一次课表数据修改。切换当前课表不会被当作数据修改记录。

---

## OPPO 流体云

v1.0.37 起课前提醒支持流体云样式：`FluidCloudService` 前台服务以 `NotificationCompat.ProgressStyle` 展示课程名、时间、教室，进度条随上课时间推进，状态栏持续可见（设置项默认关闭）。OPPO/一加 Seedling 卡片的接入材料已备好但**尚未接入构建**。

- OPPO `SeedlingSupportSDK-lite 3.0.7` AAR 在 `app/libs/`（要求 minSdk 26），gradle 尚未声明依赖，`SeedlingCardWidgetProvider` 未实现
- UPK 源工程见 [`oppo-fluid-cloud-upk/`](oppo-fluid-cloud-upk/)（API 2.0，`immediate` 触发，`notification/statusbar` 入口）
- `identifier`、`intent` 等 OPPO 分配值在工程中保留为 `REPLACE_WITH_OPPO_*` 占位，详见该目录 README

---

## 课程通知 & 提醒

入口在「我的」→「提醒」跳独立页面。master toggle 默认关闭，点击开启时请求通知权限——拒绝则回弹关闭，再点再问（非一次性）。

<p align="left">
  <img src="docs/screenshots/22-reminder.png" width="320">
</p>

| 子功能 | 触发时机 | 内容（动态生成） |
|---|---|---|
| **每日提醒** | 每天指定时间 | `今日 15 号 您有 3 节课 第一节课 高等数学 于 08:00 在 教学楼A101 上课` |
| **每节课前提醒** | 每节课前 N 分钟 | `下节课 高等数学 于 08:00 在 教学楼A101 上课` |

- 课前分钟数**自由输入**（1–999，胶囊型输入框，非硬编码选项）
- 提醒内容**动态查询当天课表**，无课则推送「今日 X 号，今天没有课程」
- 通知通过 `AlarmManager` 精确/非精确双路降级，Android 12+ 兼容
- `BootReceiver` 重注册（开机/更新后自动恢复）
- 开关状态本地持久化（`AppPrefs`，SharedPreferences）
- v1.0.21 起 master 关闭时**不再弹权限**（避免首次启动骚扰），所有权限请求都从 ReminderScreen 发起

---

## 关于

入口在「我的」→「关于」跳独立页面，集中展示版本、作者、开源信息。

<p align="left">
  <img src="docs/screenshots/21-about.png" width="320">
</p>

| 区块 | 内容 |
|---|---|
| **版本信息** | 版本号 + 构建号（`BuildConfig.VERSION_NAME` / `BuildConfig.VERSION_CODE`） |
| **作者** | Lingion，点击跳 GitHub 主页 |
| **开源地址** | github.com/lingion/sleepy，点击可跳转 |
| **开源声明** | GPL-3.0 协议说明、26 个上游项目的许可证和参考范围 |

关于页还提供版本更新检查。检查到新版本时先展示完整更新说明，确认后才下载；下载过程可取消。

---

## 深色模式 & 主题

多套预设 + 跟随系统，每套含 Light/Dark 配色方案，在「我的」→「外观与主题」（`AppearanceScreen`）切换。

<p align="left">
  <img src="docs/screenshots/11-theme.png" width="280">
</p>

| 主题 | 风格 |
|---|---|
| 默认淡紫 | Material 3 紫色调 |
| 春绿 | 抹茶植物 |
| 海蓝 | 沉静冷调 |
| 蜜桃粉 | 暖橙 |
| 石板灰 | 中性冷淡 |
| 跟随系统 | 自动适配 |

---

## 课表管理总览

<p align="left">
  <img src="docs/screenshots/09-manage.png" width="280">
</p>

---

## 技术栈

```
language        = Kotlin 2.1.10
ui              = Jetpack Compose (BOM 2024.10.00) + Material 3
navigation      = Navigation Compose 2.8.3
storage         = Room 2.7.0 (KSP)
prefs           = SharedPreferences (AppPrefs)；DataStore 1.1.1 已声明未使用
serialization   = kotlinx-serialization-json 1.6.3
html_parser     = jsoup 1.18.1
widgets         = RemoteViews + Canvas（同步渲染；Glance 依赖已删除）
background      = WorkManager 2.9.1
image           = Coil Compose 2.7.0
splash          = Core Splash Screen 1.0.1
core_ktx        = AndroidX Core 1.17.0
build           = AGP 9.1.0 + Gradle 9.3.1 (Kotlin DSL)
java_compat     = 17
```

---

## 项目结构

```
sleepy/
├── app/src/main/
│   ├── java/com/lingion/sleepy/
│   │   ├── MainActivity.kt              # 单 Activity 入口
│   │   ├── SleepyApp.kt                # Application（DI、通知调度器）
│   │   ├── data/
│   │   │   ├── AppDatabase.kt          # Room 数据库
│   │   │   ├── dao/                    # 课程 / 课表 DAO
│   │   │   ├── entity/                 # Course / TimeTable / SmartPeriodConfig
│   │   │   ├── jw/                     # 教务系统导入（含 chaoxing/classic_eams/eams5/whut）
│   │   │   ├── parser/                 # ScheduleParser + SleepyNativeParser/Exporter
│   │   │   └── repository/             # ScheduleRepository
│   │   ├── ui/
│   │   │   ├── component/              # CourseTableView / CourseDetailSheet /
│   │   │   │                           # SmartPeriodEditor / TimeSlotEditor /
│   │   │   │                           # PillNavigationBar / SegmentedSwitcher
│   │   │   ├── screen/
│   │   │   │   ├── schedule/           # 周视图 + 网格视图
│   │   │   │   ├── today/              # 今日视图
│   │   │   │   ├── edit/               # 课程编辑
│   │   │   │   ├── imports/            # 教务导入 + 文本导入 + 学校选择
│   │   │   │   ├── manage/             # 课程管理
│   │   │   │   └── mine/               # 我的 / 所有课表 / 编辑课表 / 主题 / 导出
│   │   │   └── theme/                  # Theme + ThemePresets（5 套配色）
│   │   ├── util/                       # AppPrefs / DateUtils / LocaleHelper / TimeTableUtils
│   │   └── widget/                     # 5 类 widget + WidgetRenderActivity + ScrollStripService
│   └── res/
│       ├── values/                     # 默认资源 (zh-CN)
│       ├── values-zh-rCN/              # 中文
│       ├── values-zh-rTW/              # 繁體
│       ├── values-en/                  # English
│       ├── values-ja/                  # 日本語
│       ├── values-es/                  # Español
│       └── xml/                        # 5 个 widget 配置 + 网络/备份规则
├── docs/screenshots/                   # README 截图
├── assets/                             # logo 原图存档
├── build.gradle.kts                     # 根构建
├── app/build.gradle.kts                 # App 模块
├── settings.gradle.kts
├── gradle.properties
└── LICENSE                              # GPL-3.0
```

---

## 构建 & 安装

### 前置

```bash
java -version           # JDK 17+

sdkmanager "platforms;android-37" "build-tools;37.0.0"
```

### 编译

```bash
git clone https://github.com/lingion/sleepy.git
cd sleepy

# Debug（x86_64 模拟器 / arm64 真机），产物约 20MB
./gradlew assembleDebug
```

### 安装

```bash
adb install app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
# 或 x86_64 模拟器
adb install app/build/outputs/apk/debug/app-x86_64-debug.apk
```

> ABI 分包：arm64-v8a（真机主流）、armeabi-v7a（32 位备机）、x86_64（模拟器）。自动匹配设备架构。

---

## Documentation

- **[Operation Guide](https://blog.qdp.qzz.io/docs/sleepy/overview)** — step-by-step user manual covering installation, import, widgets, themes, and troubleshooting
- **[Technical Write-up](https://blog.qdp.qzz.io/sleepy-material-you-schedule)** — architecture deep-dive: schedule parser engine, gold-angle HSL, Wisedu reverse-engineering, widget rendering pipeline

---

## License

[GPL-3.0](LICENSE)

Sleepy 使用 GPL-3.0 发布。教务导入适配、协议研究和课表格式工作参考了多个开源项目；完整的 26 项项目名称、许可证和参考范围见应用内「我的」→「关于」→「开源声明」。

---

<p align="center">
  <sub>构建无壳，自由自在。</sub>
</p>
