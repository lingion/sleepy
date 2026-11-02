# 全应用玻璃控件对照

## 来源与边界

参考本地 FLClash `0.9.1+2026101702`，GPL v3。来源为完整软件资产中的 `lib/ui/`，构建工作流使用 Flutter 3.47.6。资产目录没有应用 Git 提交，故不填写未经核实的应用 SHA；同目录截图与 APK 的精确构建对应关系尚未证明。

本任务按用户选择将所有独立小操作、分段选择和小选择块玻璃化。FLClash 中仍为普通表面的控件，在此明确称作延伸设计。颜色主题、深浅模式与导航形态保持独立；不自动玻璃化课程大卡、设置大卡或输入本体。

逐项进展见 `flclash-control-coverage.csv`。未接入、未验证项目必须保留真实状态，不能仅凭控件函数存在就记为通过。动态数据生成的各选项经同族覆盖，不按屏幕上的初始数量裁剪范围。

## 控件对应

| 家族 | FLClash 来源 | 材质与交互 | Sleepy 行为约束 |
| --- | --- | --- | --- |
| 单图标 | button.dart:175–282；scaffold.dart:359–436 | regular；选中 tinted；ElasticPress 放大与跟手；无按压水波 | 原返回/添加/刷新/分享回调、enabled、语义不变 |
| 组合胶囊 | button.dart:59–127 | 一个 regular 壳与一个整体反馈；子按钮透明 | 撤回左、重做右；各自禁用；保留缩放始终独立 |
| 主次文字操作 | button.dart:131–169,203–232 | 普通 Tonal 延伸为 regular/tinted | 危险操作可辨，每次输入只有一次业务回调 |
| 分段选择 | segmented_control.dart:15–105 | 原为 raised 轨道和 primary 滑块；延伸为玻璃 | 选项与选中值、焦点、徽标和回调保持 |
| 周次/星期/位置小块 | 按钮材质和选择状态 | 无同构课程控件，按 regular/tinted 延伸 | 动态周数与时段全部覆盖，不套 Dock 拖拽语义 |
| 菜单 | popup.dart:346–402,766–915,2180–2288 | menu、Rim 与行高亮 | 共用表面，不每行叠 Dock；禁用和危险项保持 |
| 弹窗操作 | dialog.dart:320–428,568–823 | alert、Rim；动作玻璃化为延伸 | 保留 Sleepy 操作次序与横竖布局策略 |
| 日期/取色确认 | 同按钮、弹窗族 | 操作表面玻璃化 | 日期/色值/范围输入本体不改变 |
| 平板、贴底导航 | home.dart:113–132 | 原为普通导航，此处是适配 | tab 数量、位置、内容状态和导航行为不变 |

## 参数基准

材质工厂来源 `liquid_glass.dart:79–224`，Rim 来源 `:434–452`。默认 blur=2.5、refraction=60、bevel=20、saturation=1.5、brightness=1、rim=0.6、rimWidth=1.3，其余依各工厂覆写。

regular、pill、menu 的深色 tint 使用 `surfaceContainerHighest`，不得以其他 surface 推算代替；亮度与色彩来自当前主题。lens 的 refraction=-4.3、bevel=5.5、saturation=1、brightness 浅1/深1.33、rim 浅0.6/深0.3、rimWidth=1.1、endShade=0.045、dispersion=6.5、rimBlur=1.5、hairline=0.42。lensContent refraction=14、bevel=11.2、brightness 浅1/深1.33、dispersion=3.6、rimBlur=2.5。

`LiquidGlassStyleReferenceTest` 用不同的普通和最高 surface 验证真实暗色来源，防止相近颜色掩盖错误。参数变更影响 Dock 的共享材质，不代表旧设备或新设备的已知视觉问题全部解决。

## 采样约束

FLClash 在 theme_manager.dart 的统一 Host/Scope 下分层捕获，在 capture 模块按前序层、模糊 band 与 atlas 共享资源。普通透明度或离屏层会改变捕获根，因此弹层与缩放不能只复制材质参数。

Sleepy 当前 Dock 的源内容在玻璃消费者之前，行内按钮不能直接复用这个布置。全局宿主必须先证明不捕获自身/消费者层，禁止上一帧包含玻璃的整页回灌；真实布局、点击与语义仅一份。跨弹窗根使用经过验证的屏幕仿射坐标，独立 Activity 只共享设置、不共享 GraphicsLayer。

## 能力与验证

参考的 standard/ultra 是质量选择，不是 Android 版本分支。Flutter shader 支持检测与 Android 原生 RuntimeShader 的 API 门槛不同。当前低版本原生回退不能称作完整折射。新增后端或隐藏 API 需另作明确决策，不以性能为由减少效果。

每族验证静止、按下、松开、选中、禁用、深浅切换、滚动、弹层及返回首帧。固定线条、文字和彩色背景确认折射、色散、高光与真实采样。记录自动化、参考截图、设备验证各自能证明的范围，不互相替代。

## 用户原话与实现行为

用户要求先对照 FLClash 资产逐项核对再应用。对应表保存参考来源及原样/延伸标记；覆盖表逐项跟踪，不把代表控件完成当作全量完成。

用户已选择全量小控件玻璃化。参考普通分段与内容操作不会据此排除；大内容卡、系统界面和桌面 RemoteViews 本体仍保持范围边界。
