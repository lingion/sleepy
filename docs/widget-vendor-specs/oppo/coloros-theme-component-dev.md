[evidence=A] 抓取 2026-09-13 · 源URL: https://open.oppomobile.com/wiki/doc/detail?id=10216 / 11377 / 11378 / 11380 / 10970 / 10824 (官方 API 直采)

# ColorOS 主题组件 / 主题商店开发补充

(与已有 theme-component-dev-notes.md 的分工: 那份是多彩引擎 adb 调试与预览实操(B 级, 博客园转载); 本份是主题商店业务与审核规范的官方正文。两者不重叠。)

## 主题商店业务(主题商店, doc 10970)

- 主题商店定位(国际站原文, developers.oppomobile.com): "OPPO Theme Store is a personalization app developed by OPPO. It provides a selection of themes, font, wallpapers (static and dynamic) and video ringtones… available in over 110 countries/regions. Its most active markets include India, Indonesia, Thailand, Vietnam, the Philippines, Malaysia…"
- 主题商店是 ColorOS 主题组件(多彩引擎)/字体/壁纸/息屏的独立上架通道, 与 Android AppWidget 无关; Sleepy 不参与此流程(与 INDEX.md 适配目标澄清一致)。

## 主题组件 SDK 通道

- 多彩引擎(com.heytap.colorfulengine)是主题组件的运行载体; 组件包通过 adb push 到 `/sdcard/Android/data/com.heytap.colorfulengine/files/widget` 预览(详见 theme-component-dev-notes.md)。
- 官方示例模板包与组件包格式由主题商店文档(运营图/制作规范)定义; 文档中心 menu 树中主题商店 doc=10970。

## 主题商店审核相关规范(官方文档 id 索引, 经 ES 搜索 API 核实存在)

- 10097 主题制作流程 / 10099 主题设计审核标准 / 10100 主题测试审核规范 / 10111 主题售后处理规范 / 10117 字体测试规范
- 11134 主题引擎制作规范 / 10996 主题编辑器功能介绍
- 11371 运营图使用场景及制作规范(小组件主题要求: "针对小组件主题, 不能直接用小组件样式设计, 需要利用小组件的主视觉元素单独制作上述要求的图片; 图片尺寸为 1440*2560, 会根据机型不同进行等比例拉伸裁剪"; 顶部和底部不可侵犯区尽量减少核心元素展示)
- 10824/10828/10829 息屏(上架规范: 命名有实际意义/无特殊符号/≤10 字符; 评级分高/中/低三档 — 高级要求"细腻 国际感 大气 留白和谐 简约 颜色搭配给人舒适感 动画流畅不卡顿"; 简介不得与实际内容不一致)
- 11138 资源标签规范 / 11343 设计师主页运营规范 / 10788 入驻流程说明

## 卡片审核规范全文要点(负一屏卡片审核规范, doc 10216 — 官方原文, 与 widget QA 最相关的部分)

- 接入准备: 法律资质 + OPPO 开放平台帐号; 审核使用线上环境。
- 暂不接入的外部卡片类型: 信息流、应用商店/游戏商店、广告类。
- 命名: 卡片组/卡片 ≤ 8 字, 禁特殊字符("*"&"), 禁前置数字/英文(防置顶), 多规格用【名称_规格】(微博热搜-小/中/大); 同一厂商不同规格包装统一(一致性); 卡片介绍 ≤ 15 字(ColorOS 13 版本后上线)。
- 功能异常审核不通过项(与桌面 widget QA 通用): 添加失败/内容空白、无法删除或删除后异常、频繁刷新闪动、账号绑定状态错误、内容不能显示、点击不跳转或落地页错、内容缺失/模糊/遮挡重叠、热区点击无反应或报错。
- 恶意行为: 潜在病毒、恶意扣费(未经二次确认)、频繁自动联网耗流量、未有效曝光时启动 GPS/蓝牙、被操作后下载 APP、内容与关联应用无实质联系。
- 展示: 图片不可引用快应用资源、不可变形拉伸模糊、ICON 不可非法/侵权/与关联快应用不匹配; 不得包含广告内容; 不得违反平台专有性(不可含其他应用市场名称、其他手机品牌标志如 "Mate" 系列)。
- 下架常见问题: 关联快应用下架导致点击无法跳转、落地页错误、开启恶意广告插件、内容违法、功能故障。
- 违规处理: 上线后不符规范 OPPO 有权立即下架并要求修改至合规再重新上架; 违规可暂停接入乃至取消快应用开发者资格。

## 物光引擎(PhysRay, doc 11377/11378/11380 — 与图形 SDK 相关, 非小组件)

- 定位: OPPO 平台高性能图形/计算 SDK, 基于 Vulkan 1.1, VK_KHR_ray_query 硬件加速 + 软光追, 推荐 Find X5 Pro 及更新系列。
- SDK 在 OPPO Github 开放下载: https://github.com/OPPO-OpenPlatform/physray_mp_sdk
- 隐私条款: 仅拥有合法使用权利; 不得出售/转让/转授权代码 API 工具; 违者终止使用并禁封开发者帐号。

## 图形处理与系统能力文档入口(menu 树核实)

- 图形处理: 物光引擎服务 doc=11377, AR 服务 doc=10268
- 系统能力: 动效能力 doc=13771, Hyper Boost doc=10749, 网络能力 doc=11422, 窗口能力 doc=12510, 系统资源服务 doc=13210, 手写笔服务 doc=13305
