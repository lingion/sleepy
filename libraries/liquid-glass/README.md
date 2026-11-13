# Liquid Glass 资产库

本目录是 Liquid Glass 资产索引和独立库的整理入口。M1、M2a 和 M2b 首批已将九份运动/调度实现移动到可构建的 `:liquid-glass-runtime`，Sleepy 通过项目依赖实际消费；构建、原基线与 AAR 唯一归属均已验证。完整控件、其余几何、材质采样和 shader 仍在 app，独立示例尚未建立。校准及来源/分发许可核对尚未完成，当前不具备独立开源发布条件。

用户要求：先以 iOS 26.5 原生控件打磨到接近一致，再作为独立仓库开源。Sleepy 将作为首个使用方。现阶段优先校准控件，源码索引见 [asset-catalog.json](asset-catalog.json)，实际进度和残差见 [对照记录](../../docs/native-glass-control-alignment.md)。

[可执行提取路线](extraction-plan.md)按实际源码闭包安排纯运动/几何、材质采样、控件和 Sleepy 适配器。2026-11-14 已完成 M1：`NativeGlassDragDeformation.kt`、`NativeDockDeformation.kt`、`NativeSliderEndpoint.kt` 只有 runtime 中的一份实现；module AAR 与 Sleepy 编译通过，三类原测试在迁移前后均为 19 项通过，测试与两份 CSV 哈希不变。实际命令、首次 SDK 环境缺失及重跑结果见 [M1 迁移记录](validation/migrations/m1.json)。M2a 随后移动 `ActiveFrameLoop.kt` 和 `DockInputClock.kt`，只开放五处必要可见性；四类原测试迁移前后各 33/33 通过，计算和时钟行为不变，AAR 是唯一实现归属，debug/androidTest 同轮构建通过。依赖解析保持 Compose runtime 1.12.1 和 coroutines-core 1.11.0，详见 [M2a 记录](validation/migrations/m2a.json)。M2b 首批记录见下；其余 press/endpoint、几何、M3–M4 和独立示例仍待执行。

## 提取边界

M2b 首批仅移动 `LiquidGlassPhysics.kt`、`GlassControlMotion.kt`、`GlassDockMotion.kt`、`NativeDockParentMotion.kt`。Physics/parent-motion 正文逐字节一致，两个 driver 仅开放 11 处跨 module 必需的声明和只读 getter；`SpringState` 的四个 setter 与 parent driver 仍为 internal。原 `NativeDockParentMotionTest` 和两份独占 CSV 原样进入标准 `runtime/src/androidHostTest`，其余 18 类测试留 app。runtime 复用现有 Compose compiler 2.4.21、UI 1.12.1 与 JUnit 4.13.2，接入正常 host test；不加 friend-path。原 19 类 124 项迁移前后均通过，after 分为 app 109 项及 host 15 项；21 份测试/资源哈希及全部测试身份不变。AAR 与 app 生产输出无重复实现，实际依赖解析保持原版本，API36 debug/test 安装身份匹配且首页启动可见。该安装只验证身份与启动，不新增光学或自然动态验收。完整命令、哈希、证据修正及边界见 [M2b 首批运动记录](validation/migrations/m2b-motion.json)。

目标 Android/Compose 运行库包含玻璃材质、背板采样与分层、轮廓、输入时钟、各控件自己的动态参数，以及 Toggle、分段 Tab、Dock、单双头滑杆、按钮、菜单和面板。主题色、文案、图标、选项数据、选择回调和触觉策略由调用方注入；课表、课程、数据库、设置持久化及 Material Design 路由继续归 Sleepy。原生采集和离线拟合工具不成为 Android 运行时依赖。

完整运行库仍未完成解耦。同一个 `liquidglass` 包目前仍混有 `LocalUiStyle` 等主题判断、导航图标驱动、设置布局、调试语义和 Material 分支；滑杆还引用包外的弹窗动画及 Material 辅助函数。`SegmentedSwitcher.kt` 同时承载玻璃控件实现和 Sleepy 适配，不能整文件归为已分离的适配器。语言菜单也需先拆出通用菜单行为，再把语言选项、设置入口和调试标签留在 Sleepy。M1/M2a 与本批四文件共九份实现，仍只覆盖运动和调度；catalog 继续保留其余提取阻碍。

原生采集工具、可分发的数值基线、回放和示例属于独立验证目录。原始长视频、临时 APK、DerivedData 和设备导出缓存不进入库或日常 CI。CI 保留正常贡献所需的编译、针对性回归和可复现示例；多背景 GPU 采证显式运行。

## 整理与发布顺序

1. 用同一份运行代码继续修复，集中记录每个控件、每类输入和每种背景的误差。
2. 逐项移除 Sleepy 业务依赖，提取 Android library 模块；用 Sleepy 适配器注入配色、文案、图标、回调及策略。运行模块不反向依赖 Sleepy；提取前后复用相同基线，避免整理代码时改变行为。
3. 建立可独立运行的示例和验证入口，保留参考版本、设备、实际输入、帧时间及全部适用范围；独立声明 SDK、Compose、backdrop 和 shader 资产依赖，验证降级路径、取消和无障碍行为。
4. 逐控件达到预先固定的接近原生验收要求，并完成逐文件来源和分发许可核对后，再创建独立公开仓库，迁入来源记录、许可证声明、使用示例和贡献流程。当前两个条件均未完成，不因目录和文档齐备提前发布。

每次校准只保留一个真实实现。模块按依赖闭包逐步迁移，Sleepy 继续消费同一份代码，逐阶段核对行为和基线。

## 验收依据

不能用一个平均分把某个控件的明显误差覆盖掉，也不能把当前状态标成“99.99%”。发布前需逐控件、逐角色先固定测量方法和可接受误差，再保留实测结果及未通过项；目前没有完整通过的发布验收矩阵。每类控件分别保留点击、按住已选/未选项、慢快拖动、加速减速、反向、端部拉伸、持续顶住、松手回弹、取消及连续首尾切换的适用证据。形状与四角、位置、宽高形变、透明度、磨砂、折射、色散和分层分别记录。

视觉验证覆盖细网格、高反差彩条、明暗色块和纹理风景，以及明暗主题、普通背板、磨砂背板和弹窗。对照须匹配控件和父容器上下文、背景坐标及实际投递输入；输入、模型和实际绘制结果分开记录。公开 presentation 载体矩形不能代替可见轮廓或最终屏幕，内部几何或单个 probe 通过也不能覆盖整个控件。未测项目、帧相位不确定性和保留残差必须随参考一起发布；当前光学、快速动态和完整四角等残差仍见对照记录。

## 双头参考与验证入口

现有 [原生参考工具](../../tools/native-glass-reference/README.md#paired-native-range-optical-matrix)已增加四种背景、明暗主题的独立双 UISlider 按住参考；[Android 光学夹具](../../app/src/androidTest/java/com/lingion/sleepy/ui/component/NativeRangeOpticalWorldRenderTest.kt)直接使用生产 Range/input，在相同逻辑世界导出两端和轨道的实际坐标。原生是 host 组合两个真实 UISlider，不能称为 Apple 原生双头 API；Android 稳态使用合成时钟，不能据此验收自然动态。原生按下前与按住中心分别记录，不隐藏取整、主题色及轨道上下文差异。夹具和其 Sleepy 主题依赖目前仍属于应用侧验证，尚未迁入独立示例，也不改变发布 gate。 77 批次已用同 primary 及实际产品主题比较独立 Range 材质入口，减少多余彩纹与深色白化；它暂复用 Single F，保留独立角色入口、四处诊断区域退步及暗上沿/端帽残差。该有限修正不代表完整原生双头验收，数据与限制见对照记录。

## 来源

保留 Sleepy 现有 [GPL v3 LICENSE](../../LICENSE) 和[完整致谢清单](../../ACKNOWLEDGEMENTS.md)。FLClash 的 Kotlin/AGSL 移植保留 GPL-3.0 声明；Flutter 超椭圆移植保留 BSD-3-Clause 声明；Kyant 原始基线保留 Apache-2.0 声明。shader 运行资产、源码头和 notice 文件均需随实际提取范围迁移，不能只复制 Kotlin。

[现有 backdrop 模块](../../third-party/kyant-backdrop/README.md)经 SleepDown 定制源码导入，SleepDown 自有修改另有非商业、源码可见声明。2026-11-14 已逐字节确认：Sleepy 最初导入 `c2490785` 的 42 个 Android/common 运行文件和模块 README，与 [SleepDown 固定快照 `d02898140327796946f2b140cb3ca87390595810`](https://github.com/xiaomanjun233/SleepDown-Schedule/tree/d02898140327796946f2b140cb3ca87390595810/third-party/kyant-backdrop) 完全一致；保留的 SleepDown 许可与第三方声明也与该快照一致。该修订记录为 `verifiedEquivalentSourceRevision`，证明可复现的内容基线，不代替仍未知的历史取得/导入修订，也不代表取得定制代码的再分发授权。当前运行代码相对 Kyant 2.0.0 为 24 个仅多一个末尾换行的原文件、10 个修改原文件及 8 个新增文件；导入后的 Sleepy 修改另记于 [UPSTREAM.json](../../third-party/kyant-backdrop/UPSTREAM.json)。

定制代码的分发许可兼容性仍未确认；Kyant 的原始许可不能覆盖这个缺口。catalog 链接现存来源、许可证及待核对事项，当前不改授权、不宣称全部原创，也不将这些记录当作独立开源分发许可已解决的证明。原生截图、数值基线和示例素材的可分发范围同样需在迁出前核对。
