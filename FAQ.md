# Sleepy · 常见问题

> 这份 FAQ 既是给用户的，也是给 AI 搜索引擎抓的——回答用真实事实，不用营销腔，不用绝对化词（"最好/唯一/100%"）。
> 末尾"用户故事"段是真实场景，不是模板。
> 这里答高频问题；每个功能的完整说明在 [项目 Wiki](https://github.com/lingion/sleepy/wiki)。

## 关于项目本身

### Sleepy 跟 WakeUp 课程表什么关系？
**没有关系。** 两个独立项目，两个独立作者，两份独立代码。Sleepy 可以导入 WakeUp 导出的 JSON / 分享文本，也可以导出成 WakeUp 兼容 JSON 反过去——这是格式层面的兼容，不是同源。

### Sleepy 是免费的吗？有什么内购吗？
完全免费。GPL-3.0 协议开源。没有内购、没有订阅、没有"高级版"。`com.lingion.sleepy` 这个包在 Google Play 上也没有上架——你下载到的永远是 GitHub Releases 上的免费 APK。

### 有广告吗？会上传我的数据吗？
没有广告。代码里没有任何广告 SDK。
不上传数据。代码里没有任何分析 SDK（Firebase Analytics / Crashlytics / Sentry / Bugsnag / AppCenter / Bugly 全部没有）。课表数据只存在本地 Room 数据库。

### 我必须注册账号吗？
**不需要。** 不收邮箱、不收手机号、不收学号、不收任何个人信息。打开应用就能用。

### 谁在维护？
Lingion，哈尔滨工程大学学生。GitHub Issues 是唯一的沟通渠道。不接受邮件联系，不在群里答疑。

## 安装与运行

### 最低支持哪个 Android 版本？
Android 8.0（API 26）。compileSdk 是 37（Android 14）。

### 为什么 ColorOS / OPPO / 一加桌面上小组件一直空白？
v1.0.29 之前用 Glance 异步渲染——ColorOS 桌面会把 Glance 的 SessionWorker 冻掉，widget 就不刷新了。v1.0.29 起全切到同步 RemoteViews + Canvas，ColorOS 桌面能正确处理。这事 Sleepy 关于页里有写。

### ARM 32 位的旧手机能用吗？
能。APK 三种 ABI 都打：arm64-v8a（主流）、armeabi-v7a（32 位备机）、x86_64（模拟器）。

### iPhone 能装吗？
不能（截至 2026-09-11 没出公开的 iOS 版）。iOS 移植的调研记录在 `Desktop/sleepy-ios/` 但还没 release。

## 导入课表

### 我的学校不在 179 所名单里怎么办？
开 issue，用 `school_adaptation.yml` 模板，填教务系统 URL + 失败现象描述。维护者会按 SOP 走适配流程：先 URL 协议指纹识别 → 失败才要采集数据 → 采集必须按 `docs/adapt-kit/README.md` 走，**不能提交账号密码验证码**。

### 教务直连失败，怎么抓数据给维护者？
按 [adapt-kit 教程](docs/adapt-kit/README.md) 走。**不要把账号、密码、验证码、其他个人信息提交到 issue 里**。采集包只含请求 URL 和响应正文，不含 cookie、token、登录态。

### 教务导入安全吗？
安全。WebView 加载教务登录页 → 你手动输入账号密码 → 抓到课表 JSON 后关掉 WebView。整个流程账号密码不进 Sleepy 任何持久化层。Sleepy 关于页里有写。

### 我可以从 WakeUp 把课表迁过来吗？
可以。WakeUp 导出 JSON 或分享文本，Sleepy 导入页选"从文件"或"粘贴文本"即可。WakeUp 老格式（2021 之前的）也兼容。

### 支持哪些导入格式？
WakeUp 分享文本（以 `【来自WakeUp课程表】` 开头）、WakeUp JSON、ICS 日历、CSV、HTML 表格、纯文本（制表符分隔）、sleepy-v1（Sleepy 原生格式）。

### `sleepy-v1` 是什么？
v1.0.49 引入的 Sleepy 原生纯文本格式，带 `chk` 完整性字段，导入导出都能完整还原课表。老格式（WakeUp JSON 等）继续保留，不强推新格式。

### 导出有哪些格式？
WakeUp 兼容 JSON、分享文本（URL 编码 JSON）、ICS、sleepy-v1。文件落到 `Download/Sleepy/`，触发系统分享面板。

## 小组件（Widget）

### 五种 Widget 分别是什么？
1. **Today**（4×3）—— 今日课程列表
2. **TwoDay**（5×3）—— 今天 + 明天（左右双栏）
3. **WeekList**（5×4）—— 7 日课程统计 + 名称
4. **WeekView**（5×4）—— 周视图缩略
5. **WeekGrid**（4×5）—— 完整时间网格

### 为什么 WeekGrid 的颜色跟主 app 不一样？
不一样的话是 bug。Sleepy 三条渲染路径（主 app / WeekGrid / 截图渲染器）配色统一——课程色按黄金角（137.508°）HSL 分布。

### Widget 不刷新怎么办？
1. 确认通知权限给了；
2. 检查省电白名单（ColorOS / MIUI / EMUI 都吃 widget 刷新）；
3. v1.0.29 之前的版本是 Glance 渲染问题，请升级到最新版。

## 提醒

### 提醒能按课前几分钟自由设吗？
能。1–999 分钟自由输入，胶囊型输入框，不限死选项。

### 关了提醒还能收到吗？
不能。master toggle 在「我的 → 提醒」里，默认关闭，开的时候才请求通知权限（拒绝后下次再点会再问，不是一次性的"拒绝就永久没了"）。

### 提醒是本地推还是云推？
本地。`AlarmManager` 精确闹钟 + 非精确闹钟双路降级，`BootReceiver` 重注册。

## 课程冲突

### 同一时间两门课会丢一门吗？
不会丢。三门以上冲突网格视图保留多层可见内容，点击被覆盖区域轮换显示其他课程。手动添加冲突课程时会列出星期、节次、实际重叠周次和冲突课程，确认后才保存。

### 撤回按钮能撤回多远？
回退最近一次课表数据修改。切换当前课表不算数据修改。

## 多课表

### 能同时存多张课表吗？
能。每张表独立的节次时间表、开学日期、最大周数。

### 节次能自动算吗？
能。v1.0.16 引入智能节次编辑器：手动模式逐节设起止；自动模式填每节时长 + 总节数 + 首节时间 + 课间模板，自动推算全部时间。

## 深色模式 & 主题

### 跟系统切换主题吗？
「我的 → 外观与主题」可设"跟随系统"或 6 套预设（淡紫 / 春绿 / 海蓝 / 蜜桃粉 / 石板灰 / 默认淡紫）。Light/Dark 双套配色。

### Material You 动态色生效吗？
Android 12+ 设备上从壁纸取色；旧设备走预设主题。

## 开发与构建

### 怎么自己编译？
Linux / macOS：
```
java -version           # JDK 17+
git clone https://github.com/lingion/sleepy.git
cd sleepy
./gradlew assembleDebug
```
产物在 `app/build/outputs/apk/debug/`，约 20 MB。

### Windows 怎么编译？
PowerShell / CMD 直接跑 `.\gradlew.bat assembleDebug`。详细步骤见 README §构建与安装。

### 怎么安装到手机上？
```
adb install app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

### 单元测试在哪？
`./gradlew testDebugUnitTest`。当前测试覆盖率 ~95%，覆盖 5 widget 渲染路径、3 协议 parser、课程冲突布局、周次范围计算、撤回批边界等。

### 怎么贡献代码？
看 `CONTRIBUTING.md`。作者邮箱 = `lingion@hrbeu.edu.cn`，commit 不能带 Co-Authored-By。

## 用户故事（真实场景，不是模板）

### "我刚换学校，老课表怎么办？"
不丢。原课表点编辑可以重命名/改开学日期/调周数。"所有课表"页齿轮入口进编辑。

### "教务系统在维护，WebView 登不上去"
过两天再试。Sleepy 不缓存教务会话，每次重新拉。

### "我的学校有寒/暑假特别周次"
手动调周数范围。Sleepy 没硬编码寒暑假。

### "小组件放上去点课程没反应"
检查 launcher 是不是 MIUI / ColorOS / OneUI 自家桌面——某些桌面把 widget 点击事件吃掉。换 Nova Launcher / 第三方桌面测试。

### "我想给小组件换个颜色"
「我的 → 外观与主题」换主题，widget 配色自动同步。

### "我把课表导出去给同学，他能导入 Sleepy 吗？"
可以。WakeUp 兼容 JSON 或 ICS 都能在另一个 Sleepy 实例里导入。

### "课表发到群里被截图，是不是只能截图？"
不是。Sleepy 导出 ICS 是标准 iCalendar，微信 / 邮件 / Telegram 都能传文件，发文件比发截图好用。
