# Sleepy · 轻课表 FAQ

> 这份 FAQ 只写当前代码和公开发布物能核实的事实，更新时间：2026-09-22。完整的页面说明、导入格式和适配记录见 [项目 Wiki](https://github.com/lingion/sleepy/wiki)。

## 项目是什么

### Sleepy 和 WakeUp 课程表是什么关系？
没有隶属关系。Sleepy 是独立的开源 Android 项目，有自己的代码库和维护者。两者只在数据格式层面兼容：Sleepy 可以导入 WakeUp 导出的 JSON / 分享文本，也可以导出 WakeUp 兼容 JSON。

### Sleepy 免费吗？有广告或内购吗？
免费，许可证是 GPL-3.0。没有广告、订阅、内购或付费等级。公开 APK 从 [GitHub Releases](https://github.com/lingion/sleepy/releases) 下载。

### 需要注册账号吗？
不需要账号、邮箱、手机号或学号。打开应用后可以直接手动建表，或导入已有课表。

### 会收集或上传数据吗？
课表和设置存储在本机。项目不集成 Firebase Analytics、Crashlytics、Sentry、Bugsnag、AppCenter、Bugly 等分析或崩溃上报 SDK，也不要求登录云端账号。教务导入时，网络请求只在用户主动同步学校教务的流程中发生。

### 谁维护？在哪里反馈问题？
项目由 Lingion 维护。Bug、新学校适配和功能建议请发到 [GitHub Issues](https://github.com/lingion/sleepy)；讨论可发到 [Discussions](https://github.com/lingion/sleepy/discussions)。提交教务问题时不要附账号、密码、验证码、Cookie、token 或个人课表。

## 安装与版本

### 支持什么 Android 版本？
最低 Android 8.0（API 26），target / compile SDK 为 37。当前代码中的应用包名是 `com.lingion.sleepy`。

### 当前版本是多少？
当前构建基线是 `versionName 1.0.57`、`versionCode 63`。发布版本以 [GitHub Releases](https://github.com/lingion/sleepy/releases) 页面为准。

### 提供哪些 APK ABI？
发布构建分别提供 `arm64-v8a`、`armeabi-v7a` 和 `x86_64` 三种 ABI。前两种用于 ARM 设备，x86_64 主要用于模拟器和相应设备。

### 有 iOS、Windows 或网页版本吗？
截至本 FAQ 更新时间，没有公开的原生 iOS、Windows、macOS、Linux 或 Web 版本。Sleepy 是 Android 应用。

## 导入与导出

### 我的学校不在名单里怎么办？
在 [Issues](https://github.com/lingion/sleepy/issues/new?template=school_adaptation.yml) 使用学校适配模板，提供学校教务系统地址、失败页面和操作步骤。不要提交任何登录凭据或未脱敏的个人数据。当前学校清单见仓库中的 [`docs/schools-list.md`](https://github.com/lingion/sleepy/blob/main/docs/schools-list.md)。

### 教务导入如何工作？账号密码会被保存吗？
应用在设备上的 WebView 中打开学校教务登录页，由用户自己输入凭据，再解析返回的课表页面或接口数据。Sleepy 不把教务账号密码写入课表数据库。导入失败时，排查材料也必须按项目适配指南脱敏。

### 支持哪些导入格式？
当前解析器支持 WakeUp 分享文本、WakeUp JSON、iCalendar / ICS、CSV、HTML 表格、制表符分隔纯文本和 Sleepy 原生 `sleepy-v1` 格式。旧的 WakeUp 数据格式继续兼容。

### `sleepy-v1` 是什么？
Sleepy 的原生纯文本交换格式，包含 `chk` 完整性字段，用于在导入导出时校验内容。它不是对旧 WakeUp 格式的替代要求。

### 可以导出什么？
课表可以导出为 WakeUp 兼容 JSON、WakeUp 分享文本、ICS 和 `sleepy-v1`。导出文件写入 `Download/Sleepy/`，随后可以调用系统分享面板。ICS 可交给系统日历、Google Calendar 或其他支持 iCalendar 的应用。

## 课表、冲突与作息表

### 能同时保存多张课表吗？
可以。多张课表独立保存，每张表可以有自己的开学日期、最大周数和节次时间表。

### 同一时间有两门课会丢课吗？
不会因为界面只显示一层就删除数据。冲突布局保留重叠课程，点击被遮挡区域可以查看其他课程；手动添加冲突课程时会展示星期、节次、周次和冲突课程信息。

### 撤回能撤回什么？
撤回最近一次课表数据修改。切换当前课表不是数据修改，不会被当作一条课程编辑记录。

### 节次时间可以自动计算吗？
可以。作息表支持手动逐节填写，也支持根据首节时间、每节时长、节数和课间模板推算时间。不同课表可以绑定不同作息表。

## 小组件

### 有哪些小组件？
当前有 Today、TwoDay、WeekList、WeekView 和 WeekGrid 五个 widget 家族，按桌面尺寸提供固定尺寸变体，渲染使用同步 `RemoteViews + Canvas`。

### 为什么 OPPO / 一加 / ColorOS 桌面上的 widget 可能空白？
旧版本使用 Glance 异步 SessionWorker 时，部分 ColorOS 桌面会冻结该工作线程。v1.0.29 起主渲染路径改为同步 RemoteViews + Canvas。若仍不刷新，检查桌面权限、省电限制和通知权限，并先手动刷新 widget。

### Widget 颜色和应用不一致怎么办？
主题颜色来自应用的主题设置，课程颜色按稳定的黄金角 HSL 分布生成。刷新应用主题后，应用会广播 widget 更新；如果桌面仍显示旧内容，移除并重新添加 widget，或检查系统对后台和桌面组件的限制。

## 提醒

### 可以提前多少分钟提醒？
课前提醒支持输入 1 到 999 分钟，不限于几个固定选项。每日提醒和课前提醒在「我的 → 提醒」中分别设置。

### 提醒是云推送吗？
不是。提醒在设备本地调度，使用 AlarmManager，并在 Android 版本允许的范围内提供精确与非精确路径；设备重启或应用更新后由 BootReceiver 恢复调度。

### 关闭提醒后还会收到吗？
应用的提醒总开关关闭后不会主动安排新的提醒。Android 系统通知权限仍由系统设置控制；若通知被系统或厂商省电策略拦截，需要在系统设置里恢复权限。

## 主题与外观

### 支持深色模式和系统动态色吗？
「我的 → 外观与主题」支持跟随系统、浅色和深色模式。Android 12 及以上可以使用 Material You 壁纸动态色；不支持动态色的设备使用预设主题色。

### 能自定义主题颜色吗？
可以创建和编辑自定义主题，设置主色、次色、第三强调色以及表面色调。编辑当前正在使用的自定义主题并保存后，颜色会立即重新应用，不需要先点击其他主题卡片再点回来。

### 选中主题卡片会改变卡片尺寸吗？
选中指示器使用固定尺寸槽位，选中和未选中状态应保持相同卡片布局尺寸；选中态通过颜色层级和对勾表达。

## 开发、测试与贡献

### 如何编译？
需要 JDK 17：

```sh
git clone https://github.com/lingion/sleepy.git
cd sleepy
./gradlew assembleDebug
```

Windows 使用 `gradlew.bat assembleDebug`。Debug APK 位于 `app/build/outputs/apk/debug/`。

### 如何跑测试？

```sh
./gradlew testDebugUnitTest
```

测试源码位于 `app/src/test/`，覆盖解析器、课表数据、冲突布局、周次计算、widget 逻辑、主题契约和导入导出等模块。

### 如何贡献？
先读 `CONTRIBUTING.md` 和对应项目说明，再提交一个边界清楚的变更。新学校适配必须补协议证据、脱敏 fixture 和测试。不要把真实账号、教务会话或个人课表放入仓库。

## 相关链接

- [项目主页](https://github.com/lingion/sleepy)
- [APK Releases](https://github.com/lingion/sleepy/releases)
- [Wiki](https://github.com/lingion/sleepy/wiki)
- [Issues](https://github.com/lingion/sleepy/issues)
- [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html)
