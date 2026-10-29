# 厂商适配资料导航

本目录按能力域集中保存厂商适配资料。内容主要采集于 2026 年 9 月，各文档标注的系统版本、来源日期与证据等级是判断适用范围的依据。

> 本文件只做两件事: ①指向五个能力域目录(内容各自独立,互不重复) ②给出跨域的施工优先级与缺口总账。
> 具体事实一律在各域目录内,本文件不承载调研正文。

## 五个能力域(每域一文件夹,域内按厂商分文件)

| 域 | 目录 | 回答的问题 |
|---|---|---|
| 负一屏 | [negative-one-screen/](./negative-one-screen/INDEX.md) | 第三方怎样把内容放上负一屏 |
| 长按菜单 | [long-press-menu/](./long-press-menu/INDEX.md) | 长按图标弹的菜单第三方能改什么 |
| 桌面形态与变形 | [desktop-morph/](./desktop-morph/INDEX.md) | 桌面能以什么形态存在、能被用户怎么拖长 |
| 官方设计规范 | [oem-design-specs/](./oem-design-specs/INDEX.md) | 各家规范在哪、是否还在更新 |
| 开源与社区 | [oem-open-source/](./oem-open-source/INDEX.md) | 逆向证据与示例仓库在哪 |

相邻既有资产: [live-cards/](./live-cards/INDEX.md)(灵动岛类实时信息卡片) · [widget-vendor-specs/](./widget-vendor-specs/INDEX.md)(AppWidget 在各家启动器的兼容坑) · [oppo-coloros-fluid-cloud.md](./oppo-coloros-fluid-cloud.md)(OPPO UPK 底稿)

## 接入优先级(第三方 app 实际可走通程度)

| 优先级 | 厂商 | 动作 | 门槛 |
|---|---|---|---|
| 1 | **vivo** | 标准 AppWidget + 3 meta-data + 2×2/4×2/4×4 → 上架 vivo 商店 → 邮件申请 → origin-card 上传 | 全自助,文档最全最新 |
| 2 | **小米** | 原生 AppWidget 免审核直用;要负一屏露出走 MIUI 小部件(邮件报备 + widget.xiaomi.com 审核);超级岛走提报制 | 中 |
| 3 | **华为** | 鸿蒙原生 Form Kit 卡片(必须 2×2)+ AGC 服务直达上负一屏(审批 1h);Android APK 在 NEXT 无形态 | 需鸿蒙原生 |
| 4 | **荣耀** | 安卓卡片 = AppWidget + `com.hihonor.widget.type=honorcard`;负一屏露出需商务 BD;灵动胶囊媒体类免开发 | 负一屏白名单 |
| 5 | **OPPO** | 标准 AppWidget 可上负一屏插件位(id=13335);Pantanal/Seedling 专用通道按原资料需商务授权码(UPK);Sleepy 已实现 Android 16 Live Updates 通道，见[接入状态](./oppo-coloros-fluid-cloud.md);图标变形无声明 API | 专用通道商务主导；Live Updates 需设备授权与验证 |
| 6 | **魅族** | 仅标准 AppWidget + shortcuts + 状态栏歌词 | 几乎无门 |

## 跨域结论(各域详版见对应 INDEX)
- **长按菜单**: 只有 vivo(图标 ≤4 + 组件气泡 ≤4)与华为(卡片长按自定义区)给了官方扩展位。
- **图标拖长变形**: 只有 OPPO 有官方记载(1×2/2×1/2×2,**1×3 无官方档位**),且为系统行为无声明 API;vivo 是组件级拖拽。
- **实时信息卡片(灵动岛类)**: 华为实况窗 Kit 开放 > 荣耀灵动胶囊(媒体免开发) ≈ 小米超级岛(提报制) ≈ vivo 原子通知(履约白名单) ≈ OPPO Pantanal 流体云(UPK+商务) > 魅族无 → 详版 [live-cards/](./live-cards/INDEX.md)。此为原平台调研比较；Sleepy 另已实现 OPPO 家族的 Android Live Updates 通道，展示仍需逐机验证。

## 缺口总账(需实测/工单,无公开数据)
- OPPO: 图标变形能否声明额外档位(1×3)→ 开放平台工单;ColorOS 15/16 卡片设计规范更新版;智能侧边栏接入。
- 荣耀/OPPO: 商务准入通过率与周期。
- 华为/荣耀: shortcuts 数量上限与菜单分组;华为/荣耀拖拽变形。
- 小米: MIUI 大图标适配文档;"超级小卡";小爱建议免费 SDK;独立设计规范站。
- 魅族: 负一屏 / Aicy / 桌面卡片 / 小窗 3.0 第三方接入;现行设计规范站。
