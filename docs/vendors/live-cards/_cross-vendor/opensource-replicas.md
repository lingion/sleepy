[evidence=B] https://github.com/appsfolder/livebridge · https://github.com/1812z/HyperIsland · https://github.com/D4vidDf/HyperBridge · https://github.com/abh80/smart-edge · https://github.com/cengiztoru/JetIsland 等(GitHub API 检索 2026-09-18, 星数/活跃度为当日快照)

# 跨厂商 · opensource-replicas.md

> 证据等级: B(GitHub 仓库元数据+README 落盘, 星数与日期为抓取快照)。这些项目**不是厂商官方通道**——除路线三外均无法获得系统级岛位, 用于设计参考/快速原型/能力补齐。

## 一、仓库清单(按星数)

| 仓库 | ★ | 语言 | 最后活跃 | 路线 | 一句话 |
|---|---|---|---|---|---|
| appsfolder/**livebridge** | 1073 | Dart/Flutter | 2026-07 | **③ Live Updates 桥接** | 普通通知→Android 16 Live Updates, 集成 HyperIsland-ToolKit, 需 Android 16+ |
| 1812z/**HyperIsland** | 520 | Kotlin | 2026-09(活跃) | **② LSPosed 模块** | HyperOS 3/4 超级岛增强: 下载管理器拓展/焦点通知适配/玻璃材质 |
| D4vidDf/**HyperBridge** | 381 | Kotlin | 2026-09(活跃) | ② LSPosed 模块 | 小米系桥接增强 |
| Angel-Studio/MaterialYou-Dynamic-Island | 300 | — | — | ① 悬浮窗 | Material You 风格岛 |
| abh80/smart-edge | 234 | Kotlin | 2022(停更) | ① 悬浮窗 | 早期代表, NotificationListener+边缘条 |
| agupta07505/SmartIsland | 179 | — | — | ① 悬浮窗 | 通用复刻 |
| EvanKoe/expressive-cutout | 144 | — | 2025+ | ③ | Material Expressive 打孔屏岛 |
| cengiztoru/JetIsland | 118 | Kotlin | 2022(停更) | ① 悬浮窗 | Jetpack Compose 教学级实现 |
| TheSerphh/NothingLand | 87 | — | — | ① | Nothing OS 风格 |
| clearw5/Dynamic-Island.js | 15 | JS | — | Web | 网页模拟 |
| NoobDigital/react-native-dynamic-island | 4 | RN | — | ① | RN 组件 |
| MarcoZorn/lost-island | 2 | — | — | 桌面 | 跨桌面环境 |

## 二、三派技术路线

| 路线 | 原理 | 代表 | 权限/门槛 | 能否进系统岛位 | 对 Sleepy 价值 |
|---|---|---|---|---|---|
| ① 悬浮窗+NotificationListenerService | 监听自家/任意通知 → WindowManager 悬浮条/弹层模拟岛 | smart-edge, JetIsland, SmartIsland | 通知访问权限+悬浮窗权限 | ❌ 纯视觉模拟, 无锁屏/AOD/系统集成 | 低(不进系统=无厂商形态优势) |
| ② LSPosed/Xposed 模块 | hook 系统通知框架, 把普通通知注入厂商岛通道(HyperOS 焦点通知/超级岛) | HyperIsland, HyperBridge | **root/解锁+LSPosed**, 仅极客用户 | ⚠️ 可借系统通道但依赖 root | 极低(不能要求课表用户 root) |
| ③ Android 16 Live Updates 原生 | 用官方 Live Updates API 真实进系统胶囊 | livebridge, expressive-cutout | Android 16+(API 36+) | ✅ 官方位 | **高**: 一次接入, 所有 Android 16+ 机型(含 ColorOS 16 双兼容)生效 |

## 三、路线③为何是分水岭

- Google 在 Android 16 将"灵动岛式"实时更新标准化(Live Updates, 通知 API 扩展);
- **ColorOS 16 官方确认双兼容**(自研流体云+Live Updates 并行, 2025-10 ODC25 口径);
- livebridge 证明"存量普通通知 → Live Updates"的自动桥接可行(★1073 说明需求真实存在);
- 对 Sleepy: 主通知链路若重构为 Live Updates 优先, 则小米/OPPO(Android 16+)/未来各家**无需逐厂申请**, 厂商私有协议(vivo superx/华为 ArkTS)退化为增强层。

## 四、合规与稳定性风险

| 风险 | 说明 |
|---|---|
| 路线② 违反保修/安全性 | root+LSPosed, 仅个人玩机, 不可作产品依赖 |
| 路线① 后台保活 | 通知监听常驻+悬浮窗, 省电策略下易掉, 厂商审核可能拒 |
| 路线③ 版本碎片 | Android 16 渗透率 2026 仍低, 需与厂商私有通道长期共存(双轨) |
| 名称/外观侵权 | "灵动岛/Dynamic Island"为 Apple 用语, 应用商店文案避免直接使用 |

## 五、Sleepy 行动建议

1. 设计期: 参考 HyperIsland 的焦点通知适配清单(哪些系统应用场景值得进岛)与 livebridge 的状态机;
2. 工程期: 通知层抽象成 `LiveCardTransport` 接口, 厂商实现(vivo superx/小米 focus.param/魅族 live.*)+ `Android16LiveUpdates` 实现并存, 运行时按 `Build.VERSION`/厂商探测选择;
3. 不依赖路线①②出货。
