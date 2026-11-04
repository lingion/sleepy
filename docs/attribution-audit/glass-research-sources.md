# 液态玻璃调研来源归档

整理日期为 2026 年 11 月 3 日。原始本地调研为 2026 年 10 月 21 日的《安卓端液态玻璃开源实现仓库》、`liquid-glass-dock-android-research.md` 与 `ios26-liquid-glass-android-spec.md`。这里保留引用来源及边界；历史星数、评分和性能判断不作为当前事实重新发布。

完整仓库名称见 [致谢清单](../../ACKNOWLEDGEMENTS.md) 的液态玻璃调研部分。原调查也涉及 React Native、Flutter、GNOME、网页及注入模块路线；这些候选保留调研致谢，不表示 Sleepy 分发了其实现。Haze 曾用于早期原型，提交 ab3ef704 记录了移除，当前构建没有这项依赖。

## 官方设计与 API 资料

- [Apple WWDC25 Meet Liquid Glass](https://developer.apple.com/videos/play/wwdc2025/219/)
- [Apple Human Interface Guidelines: Materials](https://developer.apple.com/design/human-interface-guidelines/materials)
- [Apple Human Interface Guidelines: Color](https://developer.apple.com/design/human-interface-guidelines/color)
- [SwiftUI Glass](https://developer.apple.com/documentation/swiftui/glass)
- [Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass)
- [AOSP 窗口模糊处理](https://source.android.google.cn/docs/core/display/window-blurs?hl=zh-cn)
- [Kyant Backdrop 文档](https://kyant.gitbook.io/backdrop)

## 已进入当前源码的来源

FLClash 的几何、样式、着色器和底栏交互移植在提交 c2490785 及其后续修订中有明确记录。Flutter 超椭圆路径移植注明了引擎修订 adc901062556672b4138e18a4dc62a4be8f4b3c2。Kyant 模块的原始基线和逐文件 blob 标识见 third-party/kyant-backdrop/UPSTREAM.json。

当前 Kyant 模块还保留了 SleepDown 的定制类及修改说明。因此同时署名 Kyant 与 SleepDown；不能仅凭 Kyant 原始许可推断定制部分的许可。SleepDown 的导入修订没有记录，此处保留这个缺口，不编造提交号。NexioSchedule 的行为参考范围沿用该模块已有说明。

SleepDown 当前许可与第三方声明于 2026 年 11 月 3 日通过 GitHub API 读取。保留的上游声明描述的是 SleepDown 的内容，不能据此宣称 Sleepy 使用了其中的 Shizuku、Miuix 或 WebDAV 依赖。

## 本次来源复核

GitHub 仓库元数据与本仓库引用记录交叉核对后，保留了 192 项补充来源。历史记录中的 JetIsland 链接已修正为 cengiztoru/JetIsland_Dynamic-Island-Jetpack-Compose，VLC 项目链接已修正为 videolan/vlc-android，Mifos Mobile 链接已修正为 openMF/mifos-mobile；原始拼写保存在清单的 historical_name 字段。Signal-Chat-Backup、miosegit/MiPushSDK 与 ECSA-Thu 的历史地址本次未确认，不将搜索到的同名或相似项目替换为原来源。
