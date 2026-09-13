[evidence=A] 抓取 2026-09-13 · 源URL: https://developer.samsung.com/galaxy-store/launch.html (+ .../distribution-guide.html / overview.html / seller.samsungapps.com 注册流程页 · developer.samsung.com Galaxy Store 官方原文)

# 三星 Galaxy Store 上架要求 (Seller Portal / 审核流程)

> 与魅族 appstore-review 对应的三星侧文档。Galaxy Store 分发指南 (App Distribution Guide) 全文为硬性审核条款, 无 AppWidget 专属条款 (小组件随 APK 一起审核)。

## 一、入驻流程 (overview.html)

- 三步: ① 注册 Samsung 账号 ② 注册 Seller Portal (https://seller.samsungapps.com) ③ 申请 commercial seller status (商业卖家身份)。
- **商业卖家身份是分发免费/付费应用的前置**; 仅做 manager account 不需要商业卖家身份。
- 注册注意 (官方 tips): 商业卖家申请需私有/公司域名邮箱 (公共域名如 gmail 需说明理由); Samsung 账号的 Country/Region 决定 Seller Portal 国家, 注册后不可改。
- Seller 类型: Private Seller (个人) / Corporate Seller (公司名义)。
- 商业验证: D-U-N-S 最简 (美国/加拿大免费申请, 其他地区可能付费); 无 D-U-N-S 用 DBA 等文件; **全部文件必须英文 (韩国人可韩文)**; D-U-N-S 资料更新全球生效 2-5 个工作日 (国际 2 周), 等 5-10 个工作日后重交。
- 银行信息: PayPal 最简; 银行国家必须与 Seller Portal 国家一致; 验证不过则申请不批。
- 审批时长: D-U-N-S 验证最多 10 个工作日; 国际银行验证最多 10 个工作日; 超过 2-3 周未回复可联系 Seller Portal 团队 (Help -> Contact us)。

## 二、应用注册流程 (launch.html)

Seller Portal 注册 tab 流程 (原文结构):
1. **App Information tab**: 基础数据/元数据/图片/年龄分级/语言/法务与支持 URL; 必填项带红星; `Import My App` 可从 Play Store 导入已有应用 (Easy App Registration)。
2. **Binary tab**: 二进制类型选择/上传/支持设备选择; **AAB 上传时 Galaxy Store 必须管理签名 key**。
3. **Country/Region & Price tab**: 价格/支付方式/分发国家; 本地价汇率 = KEB Hana 银行前一工作日汇率。
4. **Publication tab**: 发布时间 (初审通过即发/定时发布/手动); **Staged rollout 仅支持 Android 应用** (控制首发国家与用户百分比, 可暂停修复)。
5. **In App Purchase tab**: 需先上传二进制 + 集成 Samsung IAP SDK; 实时商品管理可免审更新; 价格模板/CSV 批量上传。
6. **Data Safety tab**: 数据收集与共享声明 (展示在详情页)。
7. **Review tab**: 给审核团队的评论; **需要登录的应用必须提供测试账号凭证**。
8. **App Promotion tab**: 申请 Discover tab 编辑推荐 (韩国/美国)。

## 三、App Distribution Guide (审核硬条款, distribution-guide.html)

> 三星保留发布/暂扣/下架全权; 应用符合三星政策但违反某国法律/习俗时, 可从该国的发布中移除。

### 3.1 Performance (应用操作)

- 1.1.1 安装/启动/终止/卸载必须无错误成功; 1.1.2 功能不得崩溃或引发功能问题; 1.1.3 不得含隐藏功能; 1.1.4 不得提交试用/beta 二进制; 1.1.5 需登录的应用须提供测试账号; 1.1.6 不得含恶意软件/病毒。
- **1.1.7 应用不得生成图标快捷方式或捆绑 (icon shortcuts or bundles)** — 桌面快捷图标相关硬条款。
- **1.1.8 不得发起或支持自动更新** (应用内自更新被禁)。
- 1.1.9 不得干扰其他应用行为; 1.1.10 推荐三星 IAP。
- 1.2 Usability: 1.2.4 图形必须可见; **1.2.5 文本必须可读且不截断/不变形**; **1.2.6 应用屏幕必须填满设备显示屏 (fill the device display screen)**; 1.2.8 应用内不得提供应用下载。
- 1.3 Metadata: 1.3.2 多国发布时元数据必须支持英语为默认语言; 1.3.3 预览图/截图/描述必须准确展示应用功能; 1.3.7 **元数据不得推广其他应用商店或移动平台**; 1.3.8 URL 不得引发功能问题。
- 1.4 Hardware compatibility: 1.4.1 静音模式不得发声; **1.4.2 不得更改用户设备默认设置**; 1.4.3 不得重启设备; 1.4.6 旋转设备/插拔配件时不得崩溃; 1.4.7 不得过度耗电/发热。

### 3.2 App content and behavior (内容)

- 2.1 性内容 / 2.2 暴力 / 2.3 烟酒毒品 / 2.4 诽谤与粗俗 / 2.5 游戏与赌博 (韩国 19+ 游戏需 GRAC 证书) / 2.6 UGC (须有过滤机制 + IP 侵权解决措施 + 举报渠道)。
- 2.7 广告: 2.7.1 广告必须明确标识且不损害可用性; **2.7.2 必须提供清晰可见的关闭和跳过按钮**; 2.7.4 禁止内容清单 (含**未经用户同意的系统通知/推送通知**、政治通信、非法内容等)。

### 3.3 Legal (法务)

- 3.1 隐私: 3.1.1 收集/使用/传输/共享用户数据须符合当地法 + GDPR + 三星服务条款; 3.1.2 **应用内须展示隐私政策 + 注册时提供 URL**; 3.1.3 隐私政策必含: 收集数据项与类型/使用目的/共享第三方清单/保留期与删除方式/修订通知方式/用户数据权利; 3.1.6 **不得要求超出功能所需的最小权限与个人信息**; 3.1.7 未获同意不得基于用户数据展示广告或推送。
- 3.2 知识产权: 3.2.1 不得复制 Galaxy Store 已发布应用的任何方面; **3.2.2 不得支持从应用内直接下载其他应用 (如通过 APK)**; **3.2.3 不得展示/使用任何 Samsung 标识 (品牌名/Logo/商标/服务标记)**; 3.2.4 不得暗示与三星有关系或误导用户; 3.2.5 FOSS 须合规开源许可; 3.2.6 受保护素材 (商标/名人肖像/球队/粉丝作品/受版权建筑等) 须先获权并留存证据向三星出示。
- 3.3 Kids 分类: 须符合 COPPA/GDPR; 为 13 岁以下设计; **不得含应用外链接**。
- 3.4 韩国: 须声明必需与可选权限并说明用途; 随机虚拟物品 (loot box) 须披露概率。

## 四、小组件 (AppWidget) 相关条款结论

- App Distribution Guide 全文**无 widget/小组件专属条款** — 小组件作为 APK 内置功能随应用整体审核。
- 与小组件行为相关的通用条款: 1.2.5 (widget 文本不截断不变形的可读性要求同源)、1.1.7 (桌面图标快捷方式禁令 — 不影响标准 AppWidget pin)、1.4.2 (不得更改系统默认设置)、2.7.4 (未经同意的系统通知/推送禁令)。
- 无深色模式审核条款 (深色模式是 One UI 设计规范要求, 非商店审核项, 见 samsung/darkmode-behavior.md)。
- 无 Xiaomi 式的 widget 独立审核/曝光刷新/独立进程要求 — 三星小组件零商店审核项, 走 Android 原生。

## 五、与 Sleepy 的落点 (仅事实归纳)

1. 若未来上架 Galaxy Store: 需商业卖家身份 (D-U-N-S 或 DBA) + 全英文文件 + 隐私政策 URL (应用内展示 + 注册提供)。
2. 硬条款自查项: 不得应用内自更新 (1.1.8)、不得推广其他商店 (1.3.7)、不得用 Samsung 标识 (3.2.3)、文本不截断 (1.2.5)、屏幕填满 (1.2.6)。
3. AAB 上传需 Galaxy Store 管理签名 key — 与 GitHub APK 侧载渠道的签名管理是两条线。
4. Staged rollout 可用 (Android 专属)。
