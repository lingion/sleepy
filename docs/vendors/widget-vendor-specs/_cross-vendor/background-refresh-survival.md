[evidence=A] 抓取 2026-09-13 · 源URL: https://dontkillmyapp.com/ 及 huawei/xiaomi/oppo/vivo/samsung/oneplus/meizu 各厂商页 (urbandroid-team 实测数据源)

# AppWidget 后台刷新在国产 ROM 的存活实测 (DontKillMyApp)

> 来源: dontkillmyapp.com (urbandroid-team, Sleep as Android 开发者) 的持续实测与用户反馈汇总。
> 这是国内厂商后台存活差异的 A 级实测数据源; 各厂商页最后更新时间以站点为准。

## 总榜 (站点首页)

站点把厂商按"对后台处理限制的严重程度"排序, 2026-09-13 抓取时前列为:

1. Huawei (除 Nexus 6P)
2. Xiaomi (除 Android One)
3. OnePlus
4. Samsung (尤其 Android P 之后)
5. Meizu
6. Asus
7. Ulefone/RugOne
8. Oppo
9. Wiko
10. Lenovo
11. Vivo
12. realme
13. Motorola
14. Blackview
15. Tecno
16. Sony
17. Unihertz
- AOSP (Android One / Pixel / Nexus) / Nokia (含 Android One) / HTC 未见严重问题标注

通用用户侧检查项 (Android 6+): 关闭对应用的电池优化; Android 8+: 检查 Background restrictions/limits。全失败时最后手段是 root 或 adb 卸载厂商省电组件。

## Huawei / EMUI

- 传统上 EMUI 是非标准后台限制最严重的定制之一; 官方无 API 无文档。
- EMUI 4 实测: 无任何用户设置可阻止系统在 60 分钟后破坏后台处理, 由定制服务 `HwPFWService` 执行; EMUI 9+ 部分机型新增任务清理应用 `PowerGenie` (PowerGenie 存在与否机型间不一致)。
- 用户侧: 关闭"智能调优"; 设置 > 电池 > 应用启动 > 改"手动管理"并全开; 关闭 Startup manager; EMUI 6+ 还有 Power plan/Protected apps 等多层开关。
- 侧载卸载 (用户自担): `pm uninstall -k --user 0 com.huawei.powergenie`、`com.huawei.android.hwaps`。
- 开发者侧唯一记录在案的代码规避 (EMUI 4): hwPfwService 杀应用前检查 wakelock tag, 硬编码白名单 tag 为 "AudioMix"/"AudioIn"/"AudioDup"/"AudioDirectOut"/"AudioOffload"/"LocationManagerService"; 日志指纹 `PFW.HwPFWAppWakeLockPolicy ... force stop abnormal wakelock app uid`。
- 对 AppWidget 的含义: updatePeriodMillis 触发的 onUpdate 是普通广播, 完全受上述省电机制约束; 60 分钟级强杀周期意味着超过 1 小时的周期刷新在 EMUI 上不可靠。

## Xiaomi / MIUI (HyperOS 前身)

- MIUI 属于最严重组; 非标准后台限制与非标准权限均无 API 无文档, 默认设置下后台处理不正常。
- 用户侧关键开关: 最近任务下拉锁定; Autostart 自启动权限 (MIUI 14 起 Settings > Apps > Your app > App permissions > Background autostart); Boost speed 锁定; 关闭开发者选项内 MIUI Optimizations; App battery saver 改 No restriction。
- 开发者侧: Autostart 状态可用 github.com/XomaDev/MIUI-autostart 读取 (MIUI 10~14 实测), `State.DISABLED` 即可确认自启动被禁。
- 对 AppWidget 的含义: 自启动权限被禁时, widget 点击拉起 app 的 PendingIntent 路径与后台刷新都可能受限; 这与 appwidget-china-adapt.md 记录的"小米不弹 pin 确认框但要求桌面快捷方式权限"互相印证。

## Oppo / ColorOS

- 记录基于 Oppo F1S, 其他机型可能类似: 熄屏即杀后台服务 (含 accessibility service), 需同时满足: 最近任务固定 + 安全应用内 startup manager/floating app list 允许 + 关电池优化 + 前台常驻通知。
- ColorOS 6 起需在应用信息页开 Allow Auto Start-up; 每应用三档省电模式 (智能限制时应用在后台时服务暂停, 打开时立即恢复)。
- 对 AppWidget 的含义: 熄屏即杀意味着隔夜(熄屏)后的周期 widget 刷新不可依赖; 这与 Sleepy 已落地的"同步 RemoteViews + goAsync"策略一致 (减少异步窗口被杀面)。

## Vivo / OriginOS (Funtouch)

- 机制未完全揭示 (站点原文 "have not been fully uncovered yet"); 后台加载需要 Autostart 特殊权限。
- 用户侧: i Manager/设置内 Autostart 开关; Android 13 起可按应用设 Unrestricted battery usage / Background power usage restrictions; 高耗电后台允许; 电池优化白名单 ("Not optimized" 桶); 最近任务锁定。
- 开发者侧: "No known solution on dev end yet"。

## Samsung / OneUI

- Android 11 起新增默认开启的严重限制: 前台服务不允许持有 wakelock; Android Pie 后杀后台数量显著上升 (adaptive battery 比 AOSP 激进)。
- 未使用 3 天后应用将不能从后台启动 (闹钟失效) — "Put unused apps to sleep" 是三星独有的非 AOSP 杀后台特性, 某些版本周期短至 3 天。
- 2024-07 官方承诺: One UI 6.0 起 targetSdk 14 的前台服务按 AOSP 前台服务 API 政策保证运行。
- 对 AppWidget 的含义: updatePeriodMillis 周期刷新若超 3 天未触发用户交互可能被休眠; adapter 更新走 WorkManager 需注意 Deep sleeping apps 分桶。

## OnePlus / OxygenOS

- 1+5/1+6 起引入当时最严重后台限制, 且设置会被固件更新重置 (需反复重开)。
- 关键开关: 最近任务锁定 (防止电池优化被回滚); App Auto-Launch 禁用; Deep optimization/Adaptive Battery 关闭; Sleep standby optimization (睡眠时段断网, 会阻断推送)。
- 对 AppWidget 的含义: Sleep standby optimization 直接影响夜间窗口的刷新投递。

## Meizu / Flyme

- 站点判断与华为/小米同级, 排名靠后仅因装机量少。
- 用户侧: Power plan=Performance; Protected apps; Keep running after screen off; Security > Permissions > Background processes 允许。
- 开发者侧: "No known solution on the dev end"。

## 对 Sleepy 的公共结论

- AppWidget 的 onUpdate 是广播, 无豁免: 所有厂商省电机制直接作用于 widget 刷新链路。
- WorkManager 周期任务受 App Standby Buckets 约束 (官方文档明示), 不比 updatePeriodMillis 更"硬"。
- 跨厂商最大公约数: 前台可见时刷新最可靠; 熄屏/休眠桶/自启动权限是三大变量。
- 小米官方另给了一条与省电无关的刷新约束 (见 rom-framework-mods.md): HyperOS 曝光刷新机制去掉了系统定时刷新, 需 meta-data 声明 — 但该机制仅对通过小米审核的 widget 生效。
