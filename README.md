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
  <img src="https://img.shields.io/badge/QQ群-1063407652-12B7F5?style=flat-square&logo=tencentqq&logoColor=white" alt="QQ 群">
</p>

<p align="center">
  <a href="README.md">中文</a> · <a href="README_EN.md">English</a> · <a href="https://github.com/lingion/sleepy/wiki">Wiki 手册</a> · <a href="https://github.com/lingion/sleepy/releases/latest">Download APK</a> · <a href="https://github.com/lingion/sleepy/releases">全部版本</a> · <a href="docs/adapt-kit/README.md">让你的学校支持教务直连</a> · <a href="https://sleepy.qdp.qzz.io">在线体验</a> · <a href="#社区">💬 社区</a> · <a href="sponsor.md">赞助</a>
</p>

---

> **Keywords (SEO):** Android schedule app, 课程表, 课表, timetable, university schedule, Jetpack Compose, Material You, Chinese university academic system import, wisedu, 强智教务, 正方教务, URP, home screen widget, HSV color picker, 开源课表, 开源课程表, 离线课表, 无广告课表, 隐私课表, 国产课表, 大学课表, 高校课表, 教务导入, 教务直连, 课表导入导出, iCalendar, ICS 课表, WakeUp 替代, 课程格子 替代, 超级课程表 替代, class timetable, school timetable, university timetable, course schedule

---

## 截图一览

<p align="center">
  <img src="docs/screenshots/00-overview.png">
</p>

<p align="center">
  Android 8.0+ · 正式包 <code>com.lingion.sleepy</code> · 调试包 <code>com.lingion.sleepy.debug</code>
</p>

---

## 概要

教务直连学校选择页新增前台定位权限，用于用户点击“定位”后的同城学校推荐；支持系统精准/大致位置选择，坐标不保存、不发送到第三方。申请精准位置是为了兼容没有网络定位、只能由 GPS 提供位置的设备。

| 项 | 值 |
|---|---|
| 包名 | `com.lingion.sleepy` |
| 最低 SDK | `26` (Android 8.0) |
| 目标 SDK | `37` |
| 架构 | arm64-v8a / armeabi-v7a / x86_64 |
| 语言 | zh-CN · zh-TW · en · ja · es |

**学校教务系统在维护?** WakeUp / 课程格子要登录云端?**想看下一节课却要点开 App?**

Sleepy 将课表保存在本地，日常查看和编辑可离线使用。教务导入会访问学校站点，节假日数据和版本更新检查也需要联网；项目未集成 Firebase / Crashlytics / Sentry / Bugsnag 等第三方分析 SDK。当前学校目录收录 349 个条目、33 种协议标识，收录不等于每个学校当前登录流程都已实机验证。另支持 iCalendar (ICS)、WakeUp 课程包、CSV、HTML 和原生文本导入；导出的 ICS 文件可导入 Apple Calendar、Google Calendar、Outlook 等日历应用。

桌面 5 种小组件、13 个固定尺寸变体(Today / TwoDay / WeekList / WeekView / WeekGrid × 常规 / 小 / 宽),跨厂商兼容,放在首屏不用点开 App。GPL-3.0 开源,谁都能审计谁都能 fork。

按需权限：学校定位仅在点击定位后请求前台位置；添加到系统日历时请求日历读写；开启提醒时请求通知权限，相关页面提供精确闹钟、电池优化和勿扰授权入口；安装应用内下载的更新需允许安装未知来源应用。可离线查看已保存的课表，无需为了普通查看授予这些可选权限。

版本号与当前支持的学校目录以应用内「关于」和 GitHub Releases 为准。AI 引擎可同时抓取仓库根的 [`llms.txt`](./llms.txt) 与 [`FAQ.md`](./FAQ.md)。

---

功能导航：[三视图](#三视图) · [多课表](#多课表管理) · [作息表](#节次配置手动--自动双模式) · [导入](#教务系统导入) · [课程编辑](#课程编辑) · [导出与系统日历](#导出课表作息表) · [小组件](#桌面-widget5-类-13-变体) · [提醒](#课程通知--提醒) · [主题外观](#深色模式自定义主题与课表外观) · [设置与备份](#课表管理与通用设置) · [构建安装](#构建--安装)

## 三视图

课表页提供周视图和网格视图，可在顶部切换；底栏「今日」单独呈现当天课程。

| 视图 | 截图 |
|---|---|
| **周视图**（按天归组的课程列表） | <p align="left"><img src="docs/screenshots/01-schedule-week.png" width="280"></p> |
| **网格视图**（时间网格 · 课程色块） | <p align="left"><img src="docs/screenshots/02-schedule-grid.png" width="280"></p> |
| **今日视图**（底部"今日"Tab · 当日课程） | <p align="left"><img src="docs/screenshots/03-today.png" width="280"></p> |

特性：
- 左/右滑周切换器，实时算周次
- 周视图与网格视图均支持手指左右滑动切换周次
- 今日页区分无课、学期未开始和学期已结束状态
- 平板等宽屏使用侧边导航和双栏布局，左侧保留课表，右侧切换今日、课表管理与我的
- 课程按"起止周+单双周+起止节"自动过滤当前周
- 点击课程卡片弹出详情底部弹窗

---

<p align="left">
  <img src="docs/screenshots/33-tablet.png" width="800" alt="宽屏布局：左侧课表、右侧我的">
</p>

> 宽屏布局：左侧课表、右侧我的

## 多课表管理

可同时管理多张课表，分别设置名称、开学日期和最大周数，并绑定作息表。作息表独立管理，可被多张课表共用。

<p align="left">
  <img src="docs/screenshots/05-all-tables.png" width="280">
  <img src="docs/screenshots/04-mine.png" width="280">
</p>

> 左：所有课表列表（齿轮进入编辑）
> 右：我的页面（统计 + 入口）

新建/编辑课表可设置名称、开始日期、最大周数和绑定作息表。

「我的」顶部统计卡可进入全部课表或课程清单。课程清单将同一课程的地点和上课安排归组展示，便于查看一门课在不同星期、节次、周次的全部安排。

<p align="left">
  <img src="docs/screenshots/24-course-list.png" width="280" alt="Course list">
</p>

---

## 节次配置（手动 / 自动双模式）

<p align="left">
  <img src="docs/screenshots/23-period-tables.png" width="280" alt="Period table list">
</p>

在「我的 → 作息表」管理独立作息表，查看绑定课表数量，也可复制一份再编辑；「课表管理」还提供新建作息表入口。手动模式逐节设置起止时间；自动模式输入每节时长、总节数、首节时间和课间模板，自动推算全部时间。

<p align="left">
  <img src="docs/screenshots/06-edit-table-manual.png" width="280">
  <img src="docs/screenshots/07-edit-table-auto.png" width="280">
</p>

> 左：手动模式（展开的演示作息节次）
> 右：自动模式（每节时长 / 总节数 / 首节 / 大小课间模板 / 预览切换）

自动模式特性：
- 可添加短课时、长课时，以及小课间、大课间，并在节次/课间分配区指定适用位置
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
| `qz_app` / `qz_ieas` | 强智移动端 / IEAS 变体 |
| `boya_pp` | 研究生博雅平台（燕山大学等） |
| `xju_post` | 新疆大学研究生 Gwork 平台 |
| `yethan` | 西南交通大学 YETHAN 移动教务 |
| `ucas` / `pku` 等 | 国科大 SEP、单校自建门户（完整 33 种协议标识见[已收录学校名单](docs/schools-list.md)） |

### 学校目录与自定义 URL

<p align="left">
  <img src="docs/screenshots/25-schools.png" width="280" alt="学校选择（演示设备此页使用英语）">
</p>

> 学校选择（演示设备此页使用英语）

学校目录会随版本更新。已收录学校可以直接选择；如果学校暂时不在目录中，也可以在搜索框输入教务系统 URL，Sleepy 会尝试根据 URL 识别协议，再打开该地址进行登录和导入。能否成功取到课表取决于学校页面、登录方式和对应解析器。

全部已收录学校（349 个条目）的名字、教务登录地址和协议类型见 **[已收录学校名单](docs/schools-list.md)**，支持 Ctrl+F 按全名 / 简称 / 拼音缩写检索，也用于核实教务地址和学校改名。

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

预览会展示课程和冲突信息，可选择替换当前课表、导入为新课表、仅追加不冲突课程、合并为新课表或连冲突课程一起追加。教务导入还会让你核对课表名称、开学日期与节次时间。

<p align="left">
  <img src="docs/screenshots/26-import-preview.png" width="280" alt="导入冲突预览与五种处理方式">
</p>

> 导入冲突预览与五种处理方式

教务流程中途退出可保留导入草稿，之后从课表管理继续；也可选择删除草稿。学校选择支持搜索、手动输入教务 URL，以及点击定位查找同城学校。

导入页提供各格式的说明与示例。「纯文本」说明中可复制 AI 截图转换提示词，交给你自行选择的识图工具，再将生成的文本粘贴回 Sleepy 预览；Sleepy 本身不会自动上传截图或内置调用 AI 服务。

<p align="left">
  <img src="docs/screenshots/34-import-guide.png" width="280" alt="纯文本格式示例与外部识图提示词说明">
</p>

> 纯文本格式示例与外部识图提示词说明

---

## 课程编辑

<p align="left">
  <img src="docs/screenshots/18-add-course.png" width="280">
  <img src="docs/screenshots/39-course-slots.png" width="280" alt="同一课程的第二个上课时段编辑">
  <img src="docs/screenshots/08-course-detail.png" width="280">
</p>

> 左：已填写别名的课程编辑页；中：第二个上课时段：可独立配置星期、节次、周次与教师；截图为未保存的虚构演示。
> 右：点击周视图课程卡片弹出详情底部弹窗

同一课程可添加多个上课时段，每个时段分别设置教师、教室、备注、星期、起止节、周次和单双周类型；可把周次设置应用到所有时段。

课程支持原名与可选别名、整组配色及每时段配色。别名仅用于显示，不改变课程身份；周视图、网格和每个小组件可分别决定是否使用别名。保存前会校验输入并提示冲突。

---

## 导出课表/作息表

支持多种导出格式；Android 10+ 的文件型导出保存到 `Download/Sleepy/` 后打开系统分享面板，Android 8–9 使用应用缓存文件分享。具体格式以当前版本导出页为准。

<p align="left">
  <img src="docs/screenshots/19-export.png" width="280">
</p>

> 截图：导出页（在"我的 → 导出课表/作息表"）；此图尚未展示新增的小爱课程表入口。

导出页先选课表或作息表(上半区全部课表、分隔线之下全部作息表)：选**课表**有四种分享/文件格式，以及小爱课程表和系统日历入口，选**作息表**自动收窄为两种(JSON 与 sleepy-v1)。

课表四种导出格式：

| 格式 | 用途 | 实现 |
|---|---|---|
| **WakeUp 兼容 JSON** | 完整课表结构，可被 WakeUp 课表等同类 App 直接导入 | `ScheduleExporter.exportWakeUpJson` |
| **分享文本** | 短文本格式（URL 编码 JSON），可粘贴到任何聊天工具 | `ScheduleExporter.exportWakeUpShareText` |
| **ICS 日历** | 标准 iCalendar 格式，可导入系统日历 / Google / Apple Calendar | `ScheduleExporter.exportIcs` |
| **sleepy-v1** | Sleepy 原生纯文本格式，可再次导入或编辑 | `SleepyNativeExporter` |

文件型导出保存为 `Download/Sleepy/sleepy_<表名>_<时间戳>.{json|ics|sleepy}`，随后打开系统分享面板；分享文本直接通过分享面板发送。

旧版 WakeUp 分享文本仍可继续导入。

### 小爱课程表

选中课表后可点「小爱课程表」：Sleepy 在本地准备数据，交给小爱 App 预览，随后由用户在小爱中确认导入。小爱接收后的联网与数据处理遵循其自身规则；未安装可处理此入口的小爱 App 时会提示安装。

此入口支持 1–30 周；日期、课程周次及节次设置不合法时会提示并停止。作息节次须从 1 连续编号、按时间顺序排列且不重叠；小爱要求上午（13:00 前）1–10 节、下午（13:00–18:00 前）及晚间各 0–10 节，因此仅下午或晚间的作息表暂不支持。自定义时间课程按所选作息表吸附到节次，不能保留精确分钟。

### 添加到系统日历

导出页还可直接将课程添加到设备上的可写日历：先选择目标日历和范围（未来 7 天、未来 1 个月或本学期剩余课程），可应用本课表调课安排、设置普通日历提醒，生成预览后再确认写入。首次使用才申请日历读写权限。第 1 节课程闹钟提醒为实验能力，实际响铃取决于系统日历与账户。

---

## 桌面 Widget（5 类 13 变体）

5 类 Widget × 13 个固定尺寸变体：Today / TwoDay / WeekList 各 常规/小/宽 三档，WeekView / WeekGrid 各 常规/小 两档。布局尺寸与各 launcher 自适应。

| Widget | 变体 | 用途 | 截图 |
|---|---|---|---|
| **Today** | 常规 / 小 / 宽 | 今日课程列表 | <p align="left"><img src="docs/screenshots/widget-today.png" width="240"></p> |
| **TwoDay** | 常规 / 小 / 宽 | 今天 + 明天（左右双栏） | <p align="left"><img src="docs/screenshots/widget-twoday.png" width="240"></p> |
| **WeekList** | 常规 / 小 / 宽 | 7 日课程统计 + 名称 | <p align="left"><img src="docs/screenshots/widget-weeklist.png" width="240"></p> |
| **WeekView** | 常规 / 小 | 周视图缩略（无彩色胶囊，纯主题色） | <img src="docs/screenshots/widget-weekview.png" width="240" alt="WeekView on Android launcher"> |
| **WeekGrid** | 常规 / 小 | 时间网格 + 课程块 | <p align="left"><img src="docs/screenshots/widget-weekgrid.png" width="200"></p> |

图片来源：Today、TwoDay、WeekList 为模拟器中 `WidgetRenderActivity` 调用当前生产渲染器生成的预览，裁切到组件边界，未拉伸；WeekView 和 WeekGrid 为实际桌面小组件截图。WeekGrid 此图展示当前可见的第 1–6 节，不代表完整学期或全部节次。桌面实际尺寸和字体截断由启动器及所选变体决定。

<p align="left">
  <img src="docs/screenshots/32-widget-settings.png" width="280" alt="单个桌面小组件：课表绑定与课程名显示">
</p>

> 单个桌面小组件：课表绑定与课程名显示

逐个小组件配置：
- 在「通用设置 → 小组件管理」选择已添加的桌面小组件，可固定显示某张课表，或跟随当前课表
- 每个实例独立选择课程原名/别名；适用变体可启用实验性滚动
- WeekList 小号和 WeekView 小号支持三天窗口，选择今天在第一位还是第二位
- 「我的」提供手动刷新全部小组件入口

实现要点：
- 全部 5 类：v1.0.29 起为同步 RemoteViews + Canvas 渲染（OPPO 等深度定制 launcher 会冻结 Glance 的异步 SessionWorker，导致卡片不刷新，故整体移植）
- **v1.0.55：固定尺寸布局分档**——每个变体按自身尺寸档位选布局，不再单布局拉伸
- **v1.0.55：部分变体提供实验性滚动**（内容超出卡片高度时可滚动查看）
- **v1.0.55：上课/下课边界触发刷新**，跨过节次边界时卡片内容自动更新
- 配色与 app 主题实时同步（深色模式 + 5 主题预设）
- **三条渲染路径（主 app / WeekGrid / 截图渲染器）配色完全统一**：课程色按黄金角 (137.508°) HSL 分布，以课程所属分组哈希映射色相，按课程分组稳定分配色相；不同课程仍可能使用相同颜色
- 刷新机制：对全部 receiver 广播 `APPWIDGET_UPDATE`（系统级）+ WorkManager 定时刷新 + 上课/下课边界刷新

---

## 冲突课程和撤回

同一时间有多门课程时，网格视图保留两层可见内容，点击被覆盖区域可以轮换显示其他课程；三门以上的冲突不会静默丢失。叠层样式支持任意层数。

手动添加或编辑课程遇到冲突时，提示会列出星期、节次、实际重叠周次和冲突课程。确认后才保存，返回修改则不写入。

顶栏提供撤回与重做，用于回退最近一次课表数据修改、恢复刚撤回的修改；无可用操作时隐藏对应入口。切换当前课表不会被当作数据修改记录。

「我的 → 外观与主题 → 课表显示」可切换叠层、折角、侧栏三种冲突样式，并调整对应的偏移或折角大小。

<p align="left">
  <img src="docs/screenshots/31-conflict.png" width="280" alt="冲突样式设置（效果见网格视图）">
</p>

> 冲突样式设置（效果见网格视图）

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
  <img src="docs/screenshots/37-reminder-advanced.png" width="280" alt="课前提醒、实验流体云及自动勿扰">
</p>

| 子功能 | 触发时机 | 内容（动态生成） |
|---|---|---|
| **今日摘要** | 每天指定时间 | `今日 15 号 您有 3 节课 第一节课 高等数学 于 08:00 在 教学楼A101 上课` |
| **明日摘要** | 单独设置每天提醒时间 | 提前查看明天的课程安排 |
| **每节课前提醒** | 每节课前 N 分钟 | `下节课 高等数学 于 08:00 在 教学楼A101 上课` |

- 今日、明日和课前提醒提供当前课表的动态内容预览
- 日期规则可分别配置法定节假日、调休日、补班日和普通周末
- 课前提醒可分别选择通知横幅和实验性流体云；流体云支持主显示字段选择、测试入口与通知/精确闹钟/电池等可靠性状态检查
- **上课自动勿扰**：授予系统勿扰访问权限后，上课期间开启勿扰，下课恢复
- 课前分钟数**自由输入**（1–999，胶囊型输入框，非硬编码选项）
- 提醒内容**动态查询当天课表**，无课则推送「今日 X 号，今天没有课程」
- 通知通过 `AlarmManager` 精确/非精确双路降级，Android 12+ 兼容
- `BootReceiver` 重注册（开机/更新后自动恢复）
- 开关状态本地持久化（`AppPrefs`，SharedPreferences）
- 提醒总开关关闭时不会主动请求通知权限；开启提醒时由提醒页面引导授权

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
| **开源声明** | GPL-3.0 协议说明、9 个跨校普适项目 + 单校致谢卡的许可证和参考范围 |

关于页提供手动更新检查及启动自动检查开关；自动检查默认开启，可关闭。检查会访问更新服务。检查到新版本时先展示完整更新说明，确认后才下载；下载过程可取消。

---

## 深色模式、自定义主题与课表外观

多套预设 + 跟随系统，每套含 Light/Dark 配色方案，在「我的」→「外观与主题」（`AppearanceScreen`）切换。

<p align="left">
  <img src="docs/screenshots/11-theme.png" width="280">
  <img src="docs/screenshots/36-theme-dark.png" width="280" alt="Dark theme">
</p>

| 主题 | 风格 |
|---|---|
| 默认淡紫 | Material 3 紫色调 |
| 春绿 | 抹茶植物 |
| 海蓝 | 沉静冷调 |
| 蜜桃粉 | 暖橙 |
| 石板灰 | 中性冷淡 |
| 跟随系统 | 使用系统主题色 |

主题色与深浅模式独立设置：可跟随系统，也可强制浅色或深色。点击新建主题可随机生成、从主色派生整套配色，或逐一设置主交互色、次级强调、第三强调和表面中性色；编辑器提供颜色选择和实时预览，支持保存、重命名及删除。主题变更同步刷新桌面小组件。

<p align="left">
  <img src="docs/screenshots/30-custom-theme.png" width="280" alt="新建自定义主题与实时预览">
</p>

> 新建自定义主题与实时预览

外观页还可调整周视图/网格的课程块缩放与圆角、冲突显示样式，以及节次表头的传统/三行布局、文字显示和悬挂偏移。

<p align="left">
  <img src="docs/screenshots/35-period-header.png" width="280" alt="节次表头样式及布局设置">
</p>

> 节次表头样式及布局设置

---

## 课表管理与通用设置

<p align="left">
  <img src="docs/screenshots/09-manage.png" width="280">
</p>

课表管理集中提供当前课表摘要、导入及草稿、新建课表、新建作息表、手动添加课程、编辑当前课表与导出入口。

「我的 → 通用设置」包含以下配置：

<p align="left">
  <img src="docs/screenshots/27-general.png" width="280" alt="通用设置：课表显示与小组件入口">
  <img src="docs/screenshots/38-general-advanced.png" width="280" alt="导航、语言与实验功能">
</p>

> 通用设置：课表显示与小组件入口

| 分类 | 可配置内容 |
|---|---|
| 课程信息 | 显示节次或具体时间，网格副信息显示教室、教师或隐藏，周视图/网格分别使用别名 |
| 周视图布局 | 双列展示、按天分列或均衡分列、隐藏空白日期 |
| 显示范围 | 选择显示星期、日期及视图切换器，课程无色模式 |
| 小组件全局设置 | 无色模式、分隔线、无课时查找最近有课日、小组件管理入口 |
| 导航与语言 | 启动默认视图、高刷新率、停靠/悬浮底栏、简中/繁中/英语/日语/西班牙语 |
| 实验功能 | 网格自适应高度、双指缩放、自动隐藏晚间节次、竖排标点、分隔线和大课间留白 |

### 节假日与调休

<p align="left">
  <img src="docs/screenshots/28-holiday.png" width="280" alt="节假日灰显和调休设置">
</p>

> 节假日灰显和调休设置

进入「通用设置 → 节假日调休设置」可切换年份、刷新中国节假日数据，设置法定节假日和周末课程灰显，以及是否排除补班日。支持添加、编辑、删除或恢复自定义日期段。

学校调休可按当前课表设置日期映射，指定调休日采用哪一天的课程；不同课表分别保存。它会影响课程来源及每日摘要、课前提醒、流体云和自动勿扰的日期判断。节假日灰显与提醒日期规则是不同设置，可分别调整。

### 全量备份与换机迁移

<p align="left">
  <img src="docs/screenshots/29-migration.png" width="280" alt="实验设置与全量数据迁移入口">
</p>

> 实验设置与全量数据迁移入口

「通用设置 → 数据迁移」可将全部课表、作息表、偏好和小组件配置打包为 `.sleepybackup`。恢复前可选**合并导入**（保留本机课表并追加，备份中的同名偏好会覆盖本机值）或**覆盖导入**（替换备份包含的课表与设置模块）。换机备份与单张课表的 JSON/ICS/文本导出用途不同。

---

## 技术栈

```
language        = Kotlin 2.1.10
ui              = Jetpack Compose (BOM 2026.09.00) + Material 3
navigation      = Navigation 3 1.1.7 + NavigationEvent 1.1.2
storage         = Room 2.7.0 (KSP)
prefs           = SharedPreferences (AppPrefs)；DataStore 1.1.1 已声明未使用
serialization   = kotlinx-serialization-json 1.6.3
html_parser     = jsoup 1.18.1
widgets         = RemoteViews + Canvas（同步渲染；Glance 依赖已删除）
background      = WorkManager 2.9.1
image           = Coil Compose 2.7.0
splash          = Core Splash Screen 1.0.1
core_ktx        = AndroidX Core 1.17.0
build           = AGP 9.1.1 + Gradle 9.3.1 (Kotlin DSL)
java_compat     = 17
```

---

## 项目结构

```
sleepy/                                    # 仓库根目录
├── app/
│   ├── src/main/
│   │   ├── java/com/lingion/sleepy/
│   │   │   ├── MainActivity.kt           # 单 Activity 入口
│   │   │   ├── SleepyApp.kt              # Application（DI、通知调度器）
│   │   │   ├── data/
│   │   │   │   ├── AppDatabase.kt        # Room 数据库
│   │   │   │   ├── dao/                  # CourseDao / TimeTableDao
│   │   │   │   ├── entity/               # CourseEntity / TimeTableEntity / SmartPeriodConfig
│   │   │   │   ├── jw/                   # 教务 parser + JwImportViewModel + JwParity
│   │   │   │   ├── parser/               # ScheduleParser + SleepyNativeParser/Exporter/Format
│   │   │   │   ├── repository/           # ScheduleRepository
│   │   │   │   └── undo/                 # UndoManager
│   │   │   ├── ui/
│   │   │   │   ├── component/            # CourseTableView / CourseDetailSheet /
│   │   │   │   │                         # SmartPeriodEditor / TimeSlotEditor /
│   │   │   │   │                         # PillNavigationBar / SegmentedSwitcher /
│   │   │   │   │                         # ConflictCard / DateTimePickers / SettingsCards /
│   │   │   │   │                         # ShareScheduleSheet / SleepyMotion
│   │   │   │   ├── screen/
│   │   │   │   │   ├── schedule/         # 周视图 + 网格视图
│   │   │   │   │   ├── today/            # 今日视图
│   │   │   │   │   ├── edit/             # 课程编辑
│   │   │   │   │   ├── imports/          # 教务导入 + 文本导入 + 学校选择
│   │   │   │   │   ├── manage/           # 课程管理
│   │   │   │   │   └── mine/             # 我的 / 所有课表 / 编辑课表 / 主题 / 导出
│   │   │   │   └── theme/                # Theme + ThemePresets + NoRippleClickable
│   │   │   ├── util/                     # AppPrefs / DateUtils / LocaleHelper /
│   │   │   │                             # TimeTableUtils / CourseColorUtil /
│   │   │   │                             # ConflictLayoutEngine / ConflictDetailReporter /
│   │   │   │                             # WeekRangeOverlap / HolidayManager / HolidayRange /
│   │   │   │                             # HighRefreshRate / PinyinMatcher / MarkdownBlocks /
│   │   │   │                             # FeedbackComposer / UpdateManager / UpdateInfo /
│   │   │   │                             # UpdateNotifier / VersionUtils
│   │   │   └── widget/                   # widget Kotlin 文件 + notification/ 子目录
│   │   │                                 # (Today/WeekList/WeekView/TwoDay/WeekGrid × Provider + Receiver)
│   │   │                                 # + WidgetRenderActivity + RemoteViewsWidgetHelper +
│   │   │                                 # WidgetContent / WidgetBitmapRenderers /
│   │   │                                 # WidgetTableResolver / WidgetUpdateWorker /
│   │   │                                 # WidgetUpdater / WidgetVariant + WeekGridPreviewActivity
│   │   │       └── notification/         # CourseNotificationScheduler + FluidCloudService
│   │   └── res/
│   │       ├── drawable/                 # 矢量图标
│   │       ├── drawable-nodpi/           # 不可压缩位图
│   │       ├── layout/                   # 少量 RemoteViews 布局
│   │       ├── mipmap-anydpi-v26/        # 自适应图标
│   │       ├── mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/
│   │       ├── values/                   # 默认资源 (zh-CN)
│   │       ├── values-zh-rCN/            # 简体中文
│   │       ├── values-zh-rTW/            # 繁體中文
│   │       ├── values-en/                # English
│   │       ├── values-ja/                # 日本語
│   │       ├── values-es/                # Español
│   │       └── xml/                      # 13 个 widget_info + 网络/备份规则
│   └── libs/                             # seedling-support-lite-3.0.7.aar (未声明依赖)
├── docs/
│   ├── screenshots/                      # README 引用的 截图
│   ├── logo.png                          # README 顶部 logo
│   ├── adapt-kit/                        # 适配采集教程 (独立 README)
│   └── sop/                              # SOP 索引（不列举具体文件）
├── assets/                               # logo_1024.png 原图
├── oppo-fluid-cloud-upk/                 # OPPO 流体云 UPK 源工程（未接入构建）
├── artifacts/                            # 构建产物归档
├── scripts/                              # release_check.sh
├── tools/                                # gen_widget_previews.py + sleepy-collector
├── tasks/                                # plan.md + todo.md
├── build.gradle.kts                       # 根构建 (AGP 9.1.1 / Kotlin 2.1.10 / KSP)
├── app/build.gradle.kts                   # App 模块（compileSdk=37 / minSdk=26 / targetSdk=37）
├── settings.gradle.kts                   # rootProject.name = "WakeUpPure"
├── gradle.properties                     # android.disallowKotlinSourceSets=false
├── gradlew                                # Unix Gradle Wrapper
├── gradlew.bat                            # Windows Gradle Wrapper（PR #16）
├── gradle/wrapper/                        # gradle-wrapper.properties (Gradle 9.3.1)
└── LICENSE                                # GPL-3.0
```

---

## 构建 & 安装

### 前置

```bash
java -version           # JDK 17+

sdkmanager "platforms;android-37.0" "build-tools;36.0.0"
```

当前 Gradle 配置解析为 `compileSdk=37`、`buildToolsVersion=36.0.0`；平台安装包标识为 `platforms;android-37.0`。

### 编译

Linux / macOS：

```bash
git clone https://github.com/lingion/sleepy.git
cd sleepy

# Debug（x86_64 模拟器 / arm64 真机）
./gradlew assembleDebug
```

Windows 原生环境（PowerShell / CMD，无需 WSL 或 Git Bash）：

1. 安装 JDK 17 或 21，将 `JAVA_HOME` 指向 JDK 根目录（不是 `bin`），或将 JDK 的 `bin` 目录加入 `PATH`。
2. 安装 Android SDK Platform 37 和所需的 Build Tools（可通过 Android Studio 的 SDK Manager 安装）。将 `ANDROID_HOME` 指向 SDK 根目录，或在项目根目录的 `local.properties` 中配置 `sdk.dir=C:/Users/your-name/AppData/Local/Android/Sdk`（使用正斜杠，并替换为实际路径；此文件已被 Git 忽略）。
3. 在项目根目录运行：

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat assembleDebug
# 可选：运行单元测试或清理构建产物
.\gradlew.bat testDebugUnitTest
.\gradlew.bat clean
```

Wrapper 会按 `gradle/wrapper/gradle-wrapper.properties` 下载指定版本的 Gradle，无需另行安装 Gradle；首次构建需要联网下载 Gradle 和依赖。Debug APK 输出到 `app/build/outputs/apk/debug/`。

### 安装

```bash
adb install app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
# 或 x86_64 模拟器
adb install app/build/outputs/apk/debug/app-x86_64-debug.apk
```

> ABI 分包：arm64-v8a（真机主流）、armeabi-v7a（32 位备机）、x86_64（模拟器）。手动安装时请选择设备对应的 APK，也可使用 universal 包。

---

调试构建使用 `com.lingion.sleepy.debug`，与正式包分开保存数据；发布页还提供 universal APK 和校验和。

CI 在面向 `main` 的 PR 上运行提交格式检查、采集工具检查、Android 单元测试、Android lint 和 APK 构建；推送 `v*.*.*` 标签触发发布。CI 与 Release 当前都不支持手动 dispatch。配置见 [CI](.github/workflows/ci.yml) 和 [Release](.github/workflows/release.yml)。

## 社区

Bug 反馈和新学校适配走 [Issues](https://github.com/lingion/sleepy/issues);想聊天、提建议、第一时间知道新版本,可以加 QQ 交流群:

**QQ 群:`1063407652`**(QQ 搜索群号 → 申请加入)

不方便加群也没关系,[Discussions](https://github.com/lingion/sleepy/discussions) 里同样会认真回复。

## Documentation

- **[在线体验](https://sleepy.qdp.qzz.io)** — Web 版课表，浏览器打开即用，无需安装；支持多格式导入（WakeUp 分享文本 / ICS / CSV / Excel），数据仅存本地
- **[Wiki](https://github.com/lingion/sleepy/wiki)** — 每屏逐一说明、导入/导出全格式、widget 家族、提醒内部实现、架构与代码地图，侧栏全互链
- **[Operation Guide](https://blog.qdp.qzz.io/docs/sleepy/overview)** — step-by-step user manual covering installation, import, widgets, themes, and troubleshooting
- **[Technical Write-up](https://blog.qdp.qzz.io/sleepy-material-you-schedule)** — architecture deep-dive: schedule parser engine, gold-angle HSL, Wisedu reverse-engineering, widget rendering pipeline

---

## License

[GPL-3.0](LICENSE)

Sleepy 使用 GPL-3.0 发布。教务导入适配、协议研究和课表格式工作参考了多个开源项目；9 个跨校普适项目与 单校致谢卡的项目名称、许可证和参考范围见应用内「我的」→「关于」→「开源声明」。

---

<p align="center">
  <sub>构建无壳，自由自在。</sub>
</p>
