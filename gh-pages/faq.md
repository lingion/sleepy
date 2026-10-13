# Sleepy · 轻课表 FAQ

> 这份 FAQ 只写当前代码和公开发布物能核实的事实，更新时间：2026-10-12。完整的页面说明、导入格式和适配记录见 [项目 Wiki](https://github.com/lingion/sleepy/wiki)。

## 项目是什么

### Sleepy 和 WakeUp 课程表是什么关系？
没有隶属关系。Sleepy 是独立的开源 Android 项目，有自己的代码库和维护者。两者只在数据格式层面兼容：Sleepy 可以导入 WakeUp 导出的 JSON / 分享文本，也可以导出 WakeUp 兼容 JSON。

### Sleepy 免费吗？有广告或内购吗？
免费，许可证是 GPL-3.0。没有广告、订阅、内购或付费等级。公开 APK 从 [GitHub Releases](https://github.com/lingion/sleepy/releases) 下载。

### 需要注册账号吗？
不需要账号、邮箱、手机号或学号。打开应用后可以直接手动建表，或导入已有课表。

### 会收集或上传数据吗？
课表和设置存储在本机。项目不集成 Firebase Analytics、Crashlytics、Sentry、Bugsnag、AppCenter、Bugly 等分析或崩溃上报 SDK，也不要求登录云端账号。教务导入会访问学校站点，节假日刷新会下载公共日期数据，版本检查和下载会访问 GitHub 或项目镜像；普通查看和编辑课表可离线完成。

### 谁维护？在哪里反馈问题？
项目由 Lingion 维护。Bug、新学校适配和功能建议请发到 [GitHub Issues](https://github.com/lingion/sleepy/issues)；讨论可发到 [Discussions](https://github.com/lingion/sleepy/discussions)。提交教务问题时不要附账号、密码、验证码、Cookie、token 或个人课表。

## 安装与版本

### 支持什么 Android 版本？
最低 Android 8.0（API 26），target / compile SDK 为 37。正式应用包名为 `com.lingion.sleepy`，调试构建为 `com.lingion.sleepy.debug`，二者数据隔离。

### 当前版本是多少？
构建版本从 Git 标签派生；无可用标签时回退到发布说明。已安装版本见应用「关于」，公开发布版本以 [GitHub Releases](https://github.com/lingion/sleepy/releases) 页面为准。

### 提供哪些 APK ABI？
发布构建分别提供：

- `arm64-v8a`：绝大多数新手机
- `armeabi-v7a`：32 位 ARM 设备
- `x86_64`：常见 Android 模拟器
- `universal`：包含所有上述 ABI；不确定架构时可选

### 有 iOS、Windows 或网页版本吗？
本仓库原生应用面向 Android。另有[独立在线体验](https://sleepy.qdp.qzz.io)，不代表网页与原生 Android 的功能完全相同；原生平台支持以项目发布物为准。

## 导入与导出

### 我的学校不在名单里怎么办？
先在学校选择页搜索全名/别名，或输入教务 URL 尝试识别。仍失败时，在 [Issues](https://github.com/lingion/sleepy/issues/new?template=school_adaptation.yml) 使用学校适配模板，提供学校教务系统地址、失败页面和操作步骤。不要提交任何登录凭据或未脱敏的个人数据。当前学校清单见 [`docs/schools-list.md`](https://github.com/lingion/sleepy/blob/main/docs/schools-list.md)；清单和协议会随适配更新。

### 教务导入如何工作？账号密码会被保存吗？
应用在设备上的 WebView 中打开学校教务登录页，由用户自己输入凭据，再解析返回的课表页面或接口数据。Sleepy 不把教务账号密码写入课表数据库。导入失败时，排查材料也必须按项目适配指南脱敏。

### 支持哪些导入格式？
当前解析器支持：

- WakeUp 分享文本（`【来自WakeUp课程表】`）
- WakeUp JSON
- iCalendar / ICS
- CSV
- HTML 表格
- 制表符分隔的纯文本
- Sleepy 原生 `sleepy-v1` 格式

解析器会按内容识别格式，不需要用户先选一个内部协议名称。旧的 WakeUp 数据格式继续保留兼容。

### `sleepy-v1` 是什么？
Sleepy 的原生纯文本交换格式，包含 `chk` 完整性字段，用于在导入导出时校验内容。它不是对旧 WakeUp 格式的替代要求；已有 WakeUp JSON、分享文本和 ICS 仍可继续导入。

### 可以导出什么？
课表可以导出为 WakeUp 兼容 JSON、WakeUp 分享文本、ICS 和 `sleepy-v1`。Android 10+ 将 JSON/ICS/.sleepy 文件写入 `Download/Sleepy/`；Android 8–9 使用应用缓存并经 FileProvider 分享，分享文本使用系统分享面板。作息表可导出 JSON 或 sleepy-v1。ICS 可交给系统日历、Google Calendar 或其他支持 iCalendar 的应用。

### 可以直接写入系统日历吗？
可以。在导出页选设备上的可写日历、日期范围，预览后确认；此操作才申请日历读写权限。可应用调课和日历提醒。实验性第 1 节闹钟取决于日历应用和账户支持。

### 如何完整备份或换机？
「通用设置 → 数据迁移」导出 `.sleepybackup`，包含课表、作息表、偏好和小组件配置。恢复可选合并或覆盖；合并保留本机课表并追加，但同名偏好键采用备份值；覆盖只替换备份包含的课表与设置模块，包内缺失的偏好文件保留本机值。单张课表 JSON/ICS 不等于全量备份。

### 需要哪些权限？
学校选择的定位按钮按需请求前台位置；提醒开启时请求通知，相关页面提供精确闹钟、电池优化、勿扰访问入口；写入系统日历请求日历读写；应用内更新安装需未知来源安装权限。普通离线查看课表不要求授予这些可选权限。

## 课表、冲突与作息表

### 能同时保存多张课表吗？
可以。多张课表独立保存，每张表可以有自己的开学日期、最大周数和节次时间表。

### 同一时间有两门课会丢课吗？
不会因为界面只显示一层就删除数据。冲突布局保留重叠课程，点击被遮挡区域可以查看其他课程；手动添加冲突课程时会展示星期、节次、周次和冲突课程信息。

### 撤回能撤回什么？
撤回最近一次课表数据修改，重做可恢复刚撤回的操作。切换当前课表不是数据修改，不会被当作一条课程编辑记录。

### 节次时间可以自动计算吗？
可以。作息表支持手动逐节填写，也支持根据首节时间、每节时长、节数和课间模板推算时间。不同课表可以绑定不同作息表。

## 小组件

### 有哪些小组件？
当前有五个 widget 家族：

1. **Today**：当天课程列表
2. **TwoDay**：今天和明天的双栏视图
3. **WeekList**：一周课程清单
4. **WeekView**：周视图缩略图
5. **WeekGrid**：完整节次网格

它们按桌面尺寸提供固定尺寸变体，当前资源中有 13 个 widget 配置 XML。渲染使用同步 `RemoteViews + Canvas`。

### 为什么 OPPO / 一加 / ColorOS 桌面上的 widget 可能空白？
旧版本使用 Glance 异步 SessionWorker 时，部分 ColorOS 桌面会冻结该工作线程。v1.0.29 起主渲染路径改为同步 RemoteViews + Canvas。若仍不刷新，检查桌面权限、省电限制和通知权限，并先手动刷新 widget。

### Widget 颜色和应用不一致怎么办？
主题颜色来自应用的主题设置，课程颜色按稳定的黄金角 HSL 分布生成。刷新应用主题后，应用会广播 widget 更新；如果桌面仍显示旧内容，移除并重新添加 widget，或检查系统对后台和桌面组件的限制。

## 提醒

### 可以提前多少分钟提醒？
课前提醒支持输入 1 到 999 分钟，不限于几个固定选项。今日摘要、明日摘要和课前提醒在「我的 → 提醒」中分别设置。

### 节假日、调休和上课勿扰怎么设置？
节假日与调课映射在通用设置中管理，各课表单独保存调课。提醒页独立配置日期规则，并可在授予系统勿扰访问后启用上课自动勿扰、下课恢复。流体云属于实验能力，显示效果受系统和厂商支持影响。

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
需要 JDK 17。Linux / macOS：

```sh
git clone https://github.com/lingion/sleepy.git
cd sleepy
./gradlew assembleDebug
```

Windows 使用 `gradlew.bat assembleDebug`。Debug APK 位于 `app/build/outputs/apk/debug/`，ABI 拆分构建会生成对应 ABI 文件。

### 如何跑测试？

```sh
./gradlew testDebugUnitTest
```

测试源码位于 `app/src/test/`，覆盖解析器、课表数据、冲突布局、周次计算、widget 逻辑、主题契约和导入导出等模块。不要把本机真实账号、教务会话或个人课表放入 fixture。

### 如何贡献？
先读 `CONTRIBUTING.md` 和对应的项目 SOP，再提交一个边界清楚的变更。新学校适配必须补协议证据、脱敏 fixture 和测试。提交作者邮箱使用项目约定的 `lingion@hrbeu.edu.cn`，commit 不添加 Claude 的署名尾注。

## 相关链接

- 源码：[github.com/lingion/sleepy](https://github.com/lingion/sleepy)
- APK：[GitHub Releases](https://github.com/lingion/sleepy/releases)
- Wiki：[github.com/lingion/sleepy/wiki](https://github.com/lingion/sleepy/wiki)
- Issue：[github.com/lingion/sleepy/issues](https://github.com/lingion/sleepy/issues)
- 许可证：[GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html)
