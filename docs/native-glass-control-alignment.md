# Android 玻璃控件与 iOS 26.5 原生对照

2026-11-12 使用 iPhone SE 3 模拟器、iOS 26.5（23F77）、375 × 667 pt、2×，观察系统 SwiftUI / UIKit 控件。Android 按 1 pt = 1 dp 对照控件局部尺寸，不拉伸整张屏幕截图。参考程序见 `tools/native-glass-reference`；Android 设备截图与真实触摸回放入口为 `NativeGlassParityCaptureTest`。

本次实机是 OPPO PKX110，Android 16 / API 36。安装包为 `com.lingion.sleepy.debug`，与正式版数据隔离。下面区分原生测量、实现和仍存在的差异；通过测试不等于每帧像素相同。

2026-11-13 已按用户要求清理历史构建、下载和可再生解码缓存。下文历史 APK 哈希是当轮观测，不代表每份旧 APK 仍保留；当前仅保留现用 debug/test 各一份，稳定入口链接到同一构建输出。未决问题的原始视频、trace、测量及必要关键帧保留，重复导出通过相对链接去重。

Material Design 与 Liquid Glass 严格分流。Material Design 的单头、双头滑杆恢复默认 M3 的 handle、track、tick 和尺寸，不安装玻璃绘制；保留历史细拖输入修复与原值标记。Material Design 色相条保留原来的满幅彩虹、圆形指示器、双描原值标记及既有输入行为。Switch 使用 M3，分段选项使用原点击路径，Dock 也不会因残留的玻璃 Dock 偏好绕过当前外观。

仅 Liquid Glass 滑杆采用用户指定的 Device Hub 轨道和下方点状刻度：单头及色相滑块 37 × 24 dp，双头按 0.75 等比缩小为 27.75 × 18 dp；两者和原值标记共用横向圆角椭圆轮廓、白色与轻阴影。轨道 8 dp，刻度点 2 dp、位于轨道中心下方 14 dp，交互高度 48 dp。玻璃升降两轴共同缩放，宽高比不变。色相条保留彩虹含义，密集业务档位的刻度按最少 12 dp 间距抽样展示，不改变吸附档数。此前把玻璃几何套到 Material Design 的实现已撤回。

Slider 的原生轨迹另证明：视觉镜片中心行程为 `width − 37`，拖值增量却按完整控件宽度计算，首次起拖消耗实测 10 pt。335 pt 的正向与反向共 123 个触点/value 样本可由该映射复现，误差低于 3e−8；该阈值不能认定为 UIKit 内部 touchSlop。新的椭圆玻璃 Slider 保留此已验证的相对拖值映射，视觉 inset 改为当前滑块半宽。原生快反向抬手后的额外 value 漂移机制未证明，未模拟。

## 直接点击与长按

以 A 已选中、B 未选中为初态。SwiftUI segmented Picker 与 UIKit UISegmentedControl 在本次记录中一致，底部 UITabBar 采用另一套提交规则。

| 输入 | 分段选择控件 | 底部 Dock |
| --- | --- | --- |
| 短点 B | 按下时 A 保持静止；在 B 松手确认后，回调并升起玻璃滑片、移向 B、收回 | 按下就预览移向 B；松手才提交页面选择 |
| 按住 B 700 ms | A 保持 107 × 28 pt；没有选中回调；松手后执行完整选择脉冲 | 按下后预览、升镜；松手提交 |
| 按住 A 700 ms | 在 A 升到 131 × 44 pt；松手收回；不切换选项 | 在 A 升镜；松手收回 |
| B 按住后向上移出 100 pt，再松手 | 取消，不切换、不触发 B 的升镜脉冲 | 本次原生记录仍提交 B；不能套用分段取消规则 |
| 同一重绘前连续 B → A | 两个请求依次生效；父状态拒绝请求后，输入状态与镜片回到受控值 | 同左 |

短点未选中 B 的局部镜片 bounds 仍会到 44 pt，移动中的形变使其屏幕包围盒可能只有约 39 pt 高。它不同于“短暂按住已经选中的 A，再松手”：后者不等待完整升起。实现分别处理 hold release 和 selection activation，不能用一个固定的部分升起动画替代。

分段控件每个座位占完整轨道的 1/N，每片左右各内缩 2 dp。此前将整条轨道先扣掉 8 dp 再分段，会使首尾滑片中心偏移，并使静止片宽度错误。玻璃分段默认交互占高为 48 dp，普通字号可见轨道为 32 dp；首页单独使用紧凑占高，Material Design 布局保留原尺寸和点击逻辑。

32 dp 是普通字号下的最小轨道高度。较大的系统字号或多行 badge 按实际文本测量扩展轨道和镜片，并保留调用者更大的交互高度，避免用固定原生尺寸裁掉文字。Dock 绘制、裁切、折射与语义边界统一映射 RTL 物理位置；系统取消触摸回到受控选项，不提交页面切换，普通纵向移出后松手仍按实测提交。

首页 Liquid Glass 顶栏按钮座位与玻璃底盘为 36 dp，视图分段占高 32 dp、上下 padding 各 4 dp。Material Design 顶栏按钮仍为 48 dp，首页分段上下 padding 各 8 dp，分段本身保留原 42 dp 高度。顶栏回退按当前外观的真实座位宽度估算：先缩小间距，再统一缩小两侧操作，之后才合并周次控件；极窄窗口按实测左右最大宽度统一缩放，周次始终保持整条顶栏的中心。

## 局部几何与起落

| 控件 | 原生静止 | 稳定按住 | 起落拟合 |
| --- | --- | --- | --- |
| Switch | 轨道 63 × 28；滑片 37 × 24；行程 22 | 58 × 38⅓，短暂约 60 × 40 | rise：k=841、ζ=.56；短点过首次峰值后收回 |
| Segment | 首格 107 × 28；末格因取整为 108 × 28 | 在各自基准上增加 24 × 16 | rise：k=635、ζ=1；已选项按住与未选项提交分开 |
| Slider 原生参考 | 37 × 24 | 57.35 × 37.2；峰值 59.869 × 38.834 | rise：k=713、ζ=.547；fall：k=216、ζ=.779 |
| 普通按钮 holder | 64 × 33.5 | 80 × 41.875 | rise 约 k=570、ζ=.625 |
| prominent holder | 142 × 33.5 | 158 × 37.275 | rise 约 k=427、ζ=.626 |

以上弹簧质量归一为 1，均是公开 presentation 记录的近似拟合，不是 Apple 内部参数。拟合时排除缺帧大于 50 ms 的窗口，不把事件投递与首帧延迟写成固定等待。按钮短点在尚未完全升起时即可开始回落，保留连续速度。几何允许过冲与轻微低于静止值的回弹，透明度保持在 0..1。

按钮的 holder 不是可见玻璃边界。原生圆形示例的可见白轮廓约 29 → 40 pt，而其 holder 为 41 × 29 → 57 × 40.317 pt。该参考的内容大小与产品 36 dp 图标底盘不同，不能直接把 holder 横向增加 16 pt 当作圆直径。当前未据此更改产品图标底盘尺寸。

底部栏的旧参考为底座 274 × 62 / 片 94 × 54；新工具曾记录 274 × 49 / 94 × 41。2026-11-13 复核确认两者来自相同 SE 3 / iOS 26.5：新工具在每次 layout 强制外框 70 pt，而系统自然外框为 83 pt，恰好压矮 13 pt，还造成图标与文字重叠约 12 pt。因此新 49/41 样本仅是受约束 fixture，不能当作默认原生外观。原生工具已改用公开 `sizeThatFits` 并完成四项定向测试：55 个完整实际手势、1109 个采样，尾段至少 2.66 秒。三项自然底座 274 × 62、四项 309 × 62，镜片均为 94 × 54；间距分别为 86/69 pt。资料保存在本会话 artifact 工作区 `outputs/glass-native-parity-20261112/dock-natural-fitting-20261113`，包含配置、源码快照、视频、xcresult、trace 与实际触点。引用压缩样本的旧拟合仅为历史观察。比较仍必须去除父变换并匹配局部几何；独立 UITabBar fixture 也不是生产 TabView 的整页行为证据。

## 速度、停住和反向

仅按速度绝对值拉长镜片不符合原生：约 1000 pt/s 的匀速中段多数接近 1×，最明显的形变出现在手指已经停住、仍按住时。快速正向停止后 x 约 +15%、y 约 −18% 至 −21%；反向停止后 x 约 −17%、y 约 +17%。约 350 ms 后有反向回弹。

`NativeGlassDragDeformation` 使用位置 follower、加速度 follower、形变弹簧三段连续状态，接受有方向的物理 dp。x/y 分别参与绘制，y 仍由有符号比例近似，并非两套独立物理状态。输入方向包含 RTL 的实际移动方向，不能只用递增的逻辑索引。按住开始新的形变，松手保留物理尾部；无交互的外部选项更新不激发形变。关闭系统动画时所有状态直接落定，不保留空转帧循环。

`app/src/test/resources/native-glass/drag-matrix.csv` 保留原生两次重复、双向、100/900 pt/s 的 16 次真实触摸与帧记录，共 2983 行。回归按原始时序回放，含停止后按住与松手阶段，不用模型生成的轨迹当参考。

前轮快速拖动形变的水平比例 RMSE：Segment 约 0.55–0.63%，Dock 约 0.70–1.03%。这是宽度比例的误差，不是镜片中心位置误差。部分首峰仍提前约 17 ms，中段也有约 1–3% 残差。直接 B 点击首峰的方向和量级接近，但 Dock 正向横向回弹可能比原生大约 41%（原生 −.060、模型 −.085）。这些误差尚未消除，不能声称所有速度下逐帧一致。

本轮 Dock 中心位置跟随单独拟合为质量 1、k≈325.36、ζ≈.78158；仅 Dock 跟随采用它，其他控件和 Dock 松手目的地弹簧保留各自规则。训练为三项 Dock 的八条轨迹，独立留出验证为四项 Dock 的八条轨迹；fixture 为 [`dock-position-matrix.csv`](../app/src/test/resources/native-glass/dock-position-matrix.csv)，共 3390 行。分析证据为本机 `native-analysis/dock-position-after/comparison.json`。

| 位置 RMSE 的统计窗口 | 三项 Dock | 四项 Dock |
| --- | --- | --- |
| 仅实际移动期间，慢速 | .26–.43 dp | .25–.41 dp |
| 仅实际移动期间，快速 | 3.32–3.48 dp | 2.91–3.13 dp |
| 全时段快速，包含 DOWN 后按住与 UP 后收稳 | 约 1.7–2.0 dp | 约 1.7–2.0 dp |

移动期间最大位置误差仍约 7.59 dp。`NativeDockPositionReferenceTest` 的 2.3 dp 门槛只针对含静止阶段的全时段 RMSE；静止段会降低均值，不能把这个门槛或全时段数值当成移动期间精度。直接点选的中心位移仍待对齐，上述跟随拟合也没有消除前述形变和光学残差。自然尺寸真机旧模型中直接按住的中心最大残差约 26.9 dp，不能用镜片形变拟合替代这个问题。

## 透明度与光学

Switch 的实心滑片在升镜时连续淡出，回落时连续恢复。轨道选中色按实际滑片位置渐变，升镜、横移和填色使用同一帧状态。原生视频支持该渐变趋势，但不能从图层 opacity=1 推出内部材质的不透明度。

原生普通按钮按住时前景对比度明显降低，prominent 降低较少。Android 仅对按钮前景拟合到 .5 / .8，并跟随 lift 连续变化；玻璃底面和大面积 surface 内容不跟随淡化。这是可见对比度拟合，不是对 Apple opacity 常量或完整 vibrancy 曲线的还原。

玻璃底栏的 clear / haze 以前实际指向同一采样内容，宣称的 haze 没有独立模糊。现在用两个独立 GPU 分支和独立 retained input layer：清晰与高斯模糊内容打包到不重叠的槽，再由光学着色器读取。生产路径和窗口原点均有真实 API 36 GPU 回归。测试图生产高频指标约 20.011 → 5.672，平均亮度 131.099 → 130.428；无白边、错区或背景变白。无 haze 时释放额外层并保留原路径，无 CPU Bitmap 模糊。

选中背景、文字和徽标继续复用当前 Material 配色：分段和浮动底栏共用 secondaryContainer、onSecondaryContainer、onSurfaceVariant、primary；Switch 保留调用方 Material 颜色。透明材质仍与背景合成。

子控件通过 `GlassMaterialScope` 显式继承父材质：输入是父级 prefix 加父级明确导出的 backing，而不是重新采整棵含子控件的视图树。普通设置卡只录制自己的颜色与形状；光学弹窗通过 Backdrop 的 `exportedBackdrop` 只导出材质，不导出前景和子控件。该 backing 不再同时注册到全局 prefix，避免半透明填色叠两次或引用正在录制的自身。采样按父到子坐标变换绘制，并在父实际尺寸上恢复父级圆角/path clip；新窗口先清除旧材质继承，再用父窗口的完成场景建立自己的链。

`GlassMaterialSamplingRenderTest` 本轮 API 36 GPU 六项已通过：半透明普通 backing 只叠加一次且更新可见、两层嵌套光学 backing 保持传递、父材质圆角 clip、子控件自身圆角 clip、排除父前景与子树、Material Design 普通 backing 且不创建材质 scope。它们验证这些采样关系，不能证明所有动态首帧和光学参数都与 iOS 一致。

语言菜单的原生组成、内容 portal / SDF 轮廓区别、打开与关闭时间轴见 [iOS 原生语言菜单参考](glass-language-menu-reference.md)。本轮修正了五行菜单的完整 backing：光学 carrier 使用卡片与菜单材质轮廓的完整并集，按真实菜单测量高度覆盖五行；内容使用自己的缩放和透明度。自然时间的真实录像又发现独立问题：保留的 placement layer clip 仍停在源卡片的一行高度，静态 captureToImage 截图未能揭露。菜单 carrier 改为不保留该 placement layer、由当前双轮廓直接裁切；`language-natural-4` 真机自然录像五行背景完整，点击、长按、滑选、外部取消的回调检查通过。不能用内容 portal 的矩形替代材质轮廓。原生参考保留观察误差和未覆盖的早期取消，不据此宣称完整形变或 vibrancy 已复刻。

原生菜单、alert、sheet 已保留视频参考。Sheet 主体材质本轮已按四背景 × 明暗主题完成独立实际 GPU 对照；8 个 ROI 平均 RGB RMSE 从 21–135/255 降至 0.23–1.38/255。同坐标彩条完整横向 profile 的 RMSE：浅色 32.01→3.58、暗色 112.23→2.40；before/after 背景 ROI 每像素一致。参数是经验拟合，不是 Apple 常量，主体接近也不能代替浮动边距、四角轮廓和边缘高光的验收。Menu 与 Alert 后续已分为四个独立的中性材质角色，实际多背景结果见文末；不再仅区别模糊半径。系统字体、不同系统缩放与圆按钮实际可见边界仍未取得完整同条件对照。API 33 以下没有同等 RuntimeShader 折射能力，降级效果不作为 1:1 证据。

浅色玻璃 alert/dialog/日期弹窗及默认 sheet 的外围遮罩按 RGB 240 → 192 拟合为黑色 20%。Dialog 退出或切换外观时恢复原窗口 dim；sheet 的调用者显式颜色优先。菜单无模态遮罩。该调整只改变周围背景，不代表 alert 主体的 tint、blur 或 vibrancy 已对齐；深色与 Material Design 默认遮罩保留。

## 验证与复现

前轮定向 JVM 回归记录为 322 项，失败 / 错误 / 跳过均为 0；包括真实拖动数据拟合、B 点选/按住、A 按住、快速 B→A、受控值拒绝、RTL、动画缩放、取消、多指和页面手势隔离、设置卡片溢出、Slider 和按钮起落。前轮 `:app:assembleDebug` 与 `:app:assembleDebugAndroidTest` 成功。该记录不覆盖本轮的 MD/LG 分流、父材质链、语言菜单修正与新增截图矩阵；2026-11-13 本轮集中定向 JVM 回归 355 项，失败 / 错误 / 跳过均为 0；`:app:assembleDebug` 与 `:app:assembleDebugAndroidTest` 同轮成功。覆盖 MD/LG 隔离、父材质链、完整语言菜单、单双头滑杆、Dock direct 模型及其输入状态、Sheet carrier 内缩与四角。之后若模型再改，需单独补录新结果。

前轮 API 36 的 `LiquidGlassHazeRenderTest` 三项通过，包含独立 atlas、生产路径和窗口原点。前轮 `captureReferenceMotion` 完成 Switch / Segment / Dock / Slider 的网格背景逐阶段截图与几何 CSV；合成 Compose 时钟和实际截图耗时分别记录。`replayNativeGesture` 使用真实 Android Choreographer 与公开输入接口重放 iOS 实际触点时间，不用测试时钟冒充真实速度；输入派发阻塞和时间偏差单独导出。

```sh
./gradlew :app:testDebugUnitTest --tests '*NativeGlass*Test' \
  --tests '*NativeSegmented*Test' --tests '*SegmentedSwitcher*Test' \
  --tests '*Glass*Test' --tests '*Slider*Test' --tests '*Settings*Test' \
  --tests '*FloatingGlassControlTest' --tests '*ActiveFrameLoopTest'
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb shell am instrument -w \
  -e class com.lingion.sleepy.ui.component.liquidglass.LiquidGlassHazeRenderTest \
  com.lingion.sleepy.debug.test/com.lingion.sleepy.SleepyRenderTestRunner
```

截图：`NativeGlassParityCaptureTest#captureReferenceMotion`，显式开启 `glassCapture=true`，参数 `glassBackgrounds=grid`、`glassControls=toggle,segmented,dock,slider`。真实回放：`#replayNativeGesture`，指定 `glassReplay` 绝对 JSON 路径、`glassControl`、`glassGestureIndex`、`glassInitialValue`；B 直接点选加 `glassStartXFraction=.5`。输出在 debug app external files 的 `native-glass-parity/<glassRunId>`。设备与录屏串行使用；这些手工参考不加入日常 CI。


多背景静态 GPU 矩阵：

```sh
adb shell am instrument -w \
  -e class com.lingion.sleepy.ui.component.NativeGlassParityCaptureTest#captureStaticOpticalBackgroundMatrix \
  -e glassStaticCapture true -e glassStaticRunId optical-backgrounds-20261112 \
  com.lingion.sleepy.debug.test/com.lingion.sleepy.SleepyRenderTestRunner
```

四种输入为细网格、高对比彩色条纹、明暗大色块、固定种子的合成风景。合成风景包含云、山体、湖面反射和近景纹理，明确不是照片；四种输入在明暗主题间保持不变。完整计划为 4 背景 × 2 主题 × 3 载体（首页、分享半屏、编辑半屏）× 6 状态（静止及五个控件分别按住）= 144 个场景，覆盖 Dock、工具栏按钮、半屏载体及其中的 Switch / 分段 / Slider / 按钮。该版 144 场景已在 API 36 实际执行通过，1 项仪器测试耗时约 1516 秒，导出 304 PNG 和 6144 项光学观察。该包早于后续顶栏、双轮廓和 Sheet 材质修正，不能据此声称后续改动已覆盖。后续使用背景、主题、载体和状态过滤，只重跑受影响条件。

每个场景分别保存实际 Activity/Dialog window 的 PixelCopy 与最终屏幕合成图，并保留八组无控件背景基线。`matrix.json` / `optical-metrics.csv` 记录中心、边缘的颜色、亮度、饱和度和梯度。Dialog surface 没有伪造同窗口的纯背景基线，窗口差值为不可比较；屏幕合成图按屏幕坐标比较，包含真实 scrim。矩形区域也可能含图标、阴影和触摸留白，这些统计不是透明度、彩虹色散或等价性的直接测量。不能仅凭一个背景或一组均值宣布验收。输出为 debug app external files 的 `glass-optical-backgrounds/<glassStaticRunId>`。

两种外观的滑杆截图：

```sh
adb shell am instrument -w \
  -e class com.lingion.sleepy.ui.component.UnifiedSliderAppearanceCaptureTest \
  -e sliderCapture true -e sliderRunId unified-slider-20261112 \
  com.lingion.sleepy.debug.test/com.lingion.sleepy.SleepyRenderTestRunner
```

前轮 `shared-oval-slider-20261112` 在 OPPO PKX110 导出八张图片、几何失败 0，当时把两种外观都作为椭圆控件；该结果不能作为本轮 Material Design 恢复默认 M3 后的验证。前轮玻璃尺寸记录为单头约 36.97 × 23.87 dp、双头约 27.90 × 18.15 dp，仅表示那次设备像素取整。

当前入口升级为 schema 3：4 背景 × Material Design / Liquid Glass × 明暗主题 × 初值 / 改值，导出 32 张控件图及 16 张同窗口背景基线，记录语义几何、原值标记、中心/边缘光学观察和 `optical-metrics.csv`。Material Design 断言默认 M3 进度控件且不出现玻璃 thumb/track/tick；椭圆尺寸、8 dp 轨道和下方点只对 Liquid Glass 断言。升级后的 32 + 16 截图矩阵已在 API 36 执行通过，1 项测试耗时约 99.6 秒，覆盖全部四背景、明暗主题与两种外观。输出仍位于 `unified-slider-appearance/<sliderRunId>`；显式开启才运行，不加入日常 CI，也不能用于宣称完整运动过程逐帧相同。

当前 goal 的验收边界按用户 2026-11-13 的说明执行：Toggle、Tab、Dock、单头及双头滑杆、按钮、菜单和 Alert 的点击、按住、拖动、快慢变化、反向、取消和连续切换全部由本会话自行验证，不称为“复杂拖动”，也不转交用户。仅课表课程长按拖拽这类需要 Xcode 配合的操作保留此前另约定的验收范围。自然控件回放入口 `NativeControlMotionCaptureTest` 不推进测试时钟，记录实际输入派发、帧读取区间、HWUI commit 和回调；它们不能被当成显示器 presentation 时间或独立视频像素测量。

用户同日补充的圆角要求也是当前 goal 的必验收项：逐一检查 Toggle、Tab、Dock、单双头滑杆、顶栏及分享按钮、语言菜单、Alert、分享及课程编辑半幅面板的外层背板、内部选中色块、滑块和内容裁切。静止、按住、拉伸、反向和回落期间，分别对照 iOS 26.5 实际可见四角曲线、直边到弧线的过渡、内外轮廓间距，以及高光和折射是否服从同一边界。holder 尺寸、包围盒、名义 radius 相同都不能代替轮廓验收；不把所有组件改成同一种半高胶囊，也不改动 Material Design 原有 shape。缺少原生可见 mask 或同条件边缘对照的组件须保留为未完成，不宣称圆角已 1:1。

本轮圆角清单已区分实际实现：Toggle、Segment 和 Slider 主要为半高圆弧；Dock 底座为 Kyant Capsule、镜片为半高 RoundRect；普通按钮保留 caller/M3 shape；Menu32（此前24）、Alert34、五语言26为独立超椭圆拟合；Sheet为上36/下20的独立超椭圆。不同 shape 的 CPU clip、shader edge/normal、rim 与 shadow 尚需实际像素核对，不由半径参数推定相同。Sheet geometry2 的四角局部 profile 排除首个栅格行后，顶角最大 inset 差1.092、底角1.429 logical units；当前 rim4 独立 neutral corner 审计为顶1.092、底1.618，首行敏感数据均保留；其余多数组件仍缺同条件四角完整 profile。五语言 English 原生直接按住图已明确显示胶囊，本轮将原矩形 hover 和默认 row ripple 合为同一胶囊高亮，保留独立公开输入 GPU 验证。

既有静止截图的独立白体轮廓审计拒绝了细网格上白对白的失效分割；改在明暗大色块背景提取minRGB246/248/250的完整连通白体，八个Slider/Toggle/Range候选均未裁边。原生Slider/Toggle为37×24pt，Android为36.974789×23.865545dp。保留完整首行时，颜色级边界最大差6.727dp；深度≥1后最大差.6702dp。它们是阈值颜色岛，包含栅格、底轨和高光影响，不是真实alpha mask，不能用来反推另一种曲线原语。双头仍按用户规定的同比缩小验收，没有匹配的原生双头参考；自然Dock的selected lens和held完整可见轮廓仍未建立。

2026-11-13 真机自然交互三轮全部通过：Dock3/Dock4（58.025s）、Toggle/分段（52.497s）、Slider/Range（49.686s）。每轮覆盖 direct/hold/drag/cancel/rapid，完整输入和回调存于 `native-control-motion`。这些序列验证输入行为与连续运动，光学/几何误差仍另行报告，不以测试通过代替视觉等价。五语言自然 GPU 回归另已通过（3.248s），同一 GPU 回归切回旧裁切路径真实失败（3.123s、第二行 delta=0）；恢复修复版后再次通过（3.196s）。


2026-11-13 自然尺寸 Dock 的点击形变另用 11 组、1000 个实际原生帧拟合，训练 5 组、留出 6 组，保留输入时间与最后静止 presentation 的观察起点。新的 `NativeDockDeformation` 使用有界位移、真实 lift 下降速度和连续的物理状态；没有固定等待。四项留出组水平 scale RMSE 为 .00569–.00783、最大残差 .02811，垂直 RMSE .00944–.01160、最大 .03187；三项留出组水平 RMSE 约 .00326、垂直约 .0072–.0073。长按训练样本的局部垂直残差仍约 .0445，不能视为逐帧等价。完整 1.0–1.5 秒尾段、首峰与反弹分别回归。直接/拖动模式交接只衰减两种垂直读出间的残差，同时保留 X/Y 及速度，纯拖动方程保持原样。

自然尺寸 Android 真机再次确认三项/四项可见底座约 274/309 dp、镜片 94 × 54 dp；Tab 相对底座中心与原生最大差约 .2 dp。透明 wrapper 的 86 dp 与原生 83 pt 不作为可见底座高度误差。新的定向 JVM 55 项（含模式切换、完整尾段、单双头交互及光学系数隔离）失败/错误/跳过均 0，debug 与 test APK 构建成功。随后普通控件的实际输入仍逐轮记录，不以这些 JVM 结果代替 GPU 动态验证。

双头范围两端重合时，旧距离比较总选终点，导致向低值方向点击轨道无法展开。现在按点选方向确定端点；LTR/RTL 两个回归在旧实现真实失败，修复后通过。仅 Liquid Glass 输入分支改变，Material Design 不受影响。

Sheet 圆角现通过 `CornerBasedShape` 提供真实 36/20 dp 半径，保留原 superellipse 轮廓；独立 Sheet rim 移除菜单的 CPU 黑色发丝线。四组实际 GPU 对照中主体与背景 ROI 逐像素一致，不透明核心 mask 一致，抗锯齿边缘会随黑线移除而变化。浅色左侧 .5 dp 的暗槽由约 179.4 恢复为 231.7；四边完整 profile 误差降低，但侧边亮峰仍弱。后续 Sheet-only 方向高光候选继续单独验证，不覆盖 menu、alert 或 Material Design。

2026-11-13 较早一轮集中构建记录为 358 项 JVM，失败、错误、跳过均为 0，debug/test APK 同轮构建成功（51s）。该轮包括 Dock 新按压首帧、滑杆取消与重合端点、Sheet 独立方向高光和菜单 GPU fixture；没有把后续尚未实施的弹窗布局列入覆盖范围。

单双头滑杆的新实际输入回放通过（55.981s）：34 次手势、325 次数值回调，单头 13 次正常松手各完成一次，两个控件各有一次取消。旧单头实现确实在 `ACTION_CANCEL` 后误发完成回调；修复后取消保留已发布的数值、不发 finish，后续点击正常。范围滑杆没有 finish API，且其后续重合端点用例主动重置数值，不能把该用例写成无重置的取消恢复证明。快速点选六次均实际改变同端数值，记录实际 DOWN 间隔和保持时间。

自然尺寸 Dock 较早一轮实际回放通过（66.679s），32 场景的回调计数正确。新按压首个观察帧统一零步长，避免暖帧把按下前的时间用于新目标；不能据此宣称解决全部调度相位。四项反向/正向按住/反向按住的中心最大残差从约 22.54/27.70/23.66 pt 降至 11.73/16.57/12.57 pt，仍未对齐。三项反向有一次回调的 vsync 时间早于实际 DOWN、执行晚于 DOWN，下一帧仍包含跨输入时间，最大残差约 7.84 pt。宽高局部最大残差仍约 2.09–5.71 dp。实际四项点击保持约 67.5ms，冷三项约 79.9ms，快速切换则约 45.6–59.7ms，不能泛称全部输入保持 67ms。原始视频、实际触点和逐帧时间保留，不拟合隐藏时间平移。

Sheet 方向高光当前使用独立经验下限，非 Sheet 的方向系数有隔离回归。四背景 × 明暗 8 个 rest 分两批实际通过（47.487s、57.333s），彩条背景下五控件 × 明暗 10 个 held 实际通过（110.295s）。正文 ROI、背景和不透明核心相对前轮逐像素不变。浅色中性背景四边 profile RMSE 为 2.00/6.10/3.99/4.89；深色边缘仍约 8–10 的残差。明暗色块和风景在两平台边缘的底图相位不总一致，已同时保存无面板输入 profile；不能把背景位置差全算作材质误差。详见会话 artifact 的 `sheet-comparison-rim-4/eight-rest`。

菜单与 Alert 较早一轮 GPU 矩阵为四背景 × 明暗 × 两载体的 16 个 rest（76.078s）；旧透明预检与失败日志保留。之后按原生 AX frame 实现菜单 42dp 行和 Alert 等宽动作排布。Popup PixelCopy 改为使用实际窗口 sourceRect，排除 surfaceInsets 造成的图像挤压；每场另等到实际 Window 与最终屏幕合成收敛。不能从修正截图工具推断产品关闭逻辑被修复。

完整字体/表面矩阵 `presentations-content-font-6` 实际通过（197.858s），覆盖四背景 × 明暗 × Menu/Alert 的 56 个 rest/held、8 个背景基线及 16 组公开动作记录。字体密度在实际 Popup 内容、Alert 的标题/正文/两个动作 slot 内单独提供和读取，共 128 条观测均为 fontScale=1；设备原设置仍为 .9。独立逐像素检查中，40 张 Window/屏幕的所有不透明像素完全一致，另 16 张菜单图各只有 1 像素相差 RGB1。菜单为 250.084×146.218dp、行约 42.017dp；Alert pane 为 320×172.773dp、标题约 21.176dp、双行正文约 38.319dp、动作约 140.168×48.067dp。先前 320×166.723dp 的 Alert 来自实际 slot 仍为 .9 的预检，不能用补高产品内容解决。17/16sp 与字重是视觉拟合，尚非 UIFont 内部值。该矩阵早于新的整块 Alert 按压缩放。

2026-11-13 输入时钟后续修正：Dock、Toggle、Segment 按真实输入 receipt 时间排队；旧帧标记早于 receipt 时不消费新命令。Press 在第一个合资格帧建立起点，其他命令按 receipt 拆分积分，同帧保序；不添加固定等待，也不平移输入起点。Toggle/Segment 新自然回放 `controls-timed-3` 实际通过（59.652s）：28 场景、439 个实际投递事件（含5次 CANCEL），30 次选择回调均位于自己的 UP 派发内。两种初值的移动取消均不回调，下一次点击各提交一次。350 个原始样本位于 Android 批次历史，不能把重采样后的当前坐标当作输入丢失。取消后几何收稳时间仍单独报告，不能据回调正确宣称运动与原生相同。

Dock 同时钟新回放 `dock-timed-5` 实际通过（66.726s），32 场景、34 回调。未平移实际 DOWN 的六组原生对照中，中心最大残差分别为三项正/反向12.028/3.797pt，四项正/反向4.758/13.351pt，四项正/反向按住19.941/12.700pt；宽高仍约1.3–4.7dp残差。Android约120Hz、原生60Hz的相位只能解释部分误差，过冲和峰值时间也有区别。现有共有 TravelSpring 保持原样，离线候选未写入生产；不能通过延迟或把观察标记当presentation时间消除误差。

另一次 `dock-timed-60hz-6` 实际通过（24.446s），只对测试 Activity 请求60Hz，实际显示60.000004Hz，2416次观察、3549条trace和10次回调完整，原Window偏好已恢复。六组中心最大残差仍为10.671/3.055、6.260/4.710、12.030/6.986pt。独立审计在原生1.5–1.8%过冲范围扫描121055组常系数二阶弹簧，没有一组同时改善六组RMSE；早期上升与晚峰的无相位时间比例也不符。该记录不支持安装这些候选，不能把刷新率或时间平移作为修复。

原生 Toggle 新补测分别保留 SwiftUI/UIKit 的14次真实手势；竖向移出100pt后UP依然切换，不能当作CANCEL。公开双击的实际UP→下一DOWN间隔约3.4–6.2ms，不能把顺序点击约1.6–1.9秒的间隔写成快速连点。公开XCUITest尚不能提供显式CANCEL或同指多段反向参考，Android对应行为测试仍由本会话负责。

原生 Alert 自然motion两项通过（48.496s），2155条实际帧记录、4张按住截图均确认处于真实DOWN/UP之间。whole-pane稳定比例为1.0125，峰值约1.01351；零延迟经验弹簧ζ=.565/k256在完整DOWN样本的最大全宽误差约.45838pt，非Apple内部参数。跨背景最终接受16张held：LandscapeLight采用另一次明确的.7秒触发长按探针成功取得2张；两次原短点触发CANCEL的失败仍保留，原因未证明。暗色按住有空间和动作相关的材质变化，不用一个统一白色overlay冒充对齐；release、颜色波形和开关场动画仍待对照。

较早一轮集中定向JVM与APK构建实际成功（1m22s）：423项，失败/错误/跳过均0，原XML、APK及SHA256存于会话artifact的`verified-build-423`。当时新增Alert 7项覆盖真实Material交互源、整块材质/内容缩放、UP即时回调、多Press token独立取消、禁用/换源/销毁、MD隔离和窄Window透明留白。初次集中423项中的1项失败为测试先推进时钟、后执行SubcomposeLayout布局，已保留红证据；先同步布局后，原断言和原1600ms时钟推进全部通过，产品逻辑未因此更改。JVM通过不代替真实GPU、自然时间或原生视觉验收。

2026-11-13 后续集中构建 `verified-build-425` 实际成功（2m1s）：425项JVM，失败/错误/跳过均0，debug和androidTest APK均已安装。Alert现有9项产品JVM回归继续覆盖原断言；单独的“平台默认320dp cap”夹具无法在Robolectric可靠建立，三次红证据保留，私有framework style/XML已移除，不将错误夹具算作产品通过。该回归迁至真实API36 `GlassAlertWindowRenderTest` 后实际通过（2.897s）：默认Dialog请求340dp而真实Window为320dp；产品Window为MATCH_PARENT，实际pane为319.99997dp、按住323.99997dp、取消回到319.99997dp，PixelCopy opaque core四边有留白。此前的默认窗口会将pane连同halo压成约311.60dp，修复将玻璃pane和透明绘制空间分开测量；Material Design继续走原Material Alert。

五语言行高亮的实际GPU测试 `language-row-corners-8` 通过（8.825s）：打开后直接按English、锚点按住后滑入English，两路径四角与未按住图差值均0、两侧中部差值16，整行差分逐像素相同；CANCEL不选择，之后外部dismissal为null。原生pane本身存在微小变化，独立分析只能提取无文字角区的强暗化候选，不能冒充alpha mask。密度换算后阈值8的平均横向轮廓差约.31–.32dp，顶部扫描线最大约4.17dp；没有拟合平移或拉伸，不能据此宣称四角已全部1:1。

Menu/Alert中性材质 `presentations-native-material-8` 实际通过（198.517s），56rest/held、8背景基线和16公开动作记录完整。独立GPU审计16/16复现四组冻结候选，整体RMS最大1.030/255；每patch及固定4dp横纵profile另行保留。前后整张显示截图的差异仅在系统栏；应用场景、pane和扩展64dp输入邻域实际逐像素一致。该结果验证生产GPU确实执行候选，不等同于完整原生视觉验收。

同世界画布 `presentations-native-canvas-9` 实际通过（85.293s）：固定375×667dp背景画布，导出每次实际localToScreen四角、原始float rect和Window placement。16个Menu/Alert rest覆盖四背景×明暗，Window opaque像素与最终屏幕误差均≤RGB1。Alert宽319.99997dp、top257.47898dp，目标位置残差[-.187492,+.062561]物理px；高度172.7731dp比原生172pt高.7731dp。字体和动作主题色仍分别核对，不能通过裁切或重定位隐藏尺寸差异。

在相同背景坐标的无文字body probe中，四背景顺序为细网格/彩条/明暗色块/风景，Menu浅色RGB RMS为1.807/3.445/3.964/4.503，深色为2.700/2.888/4.252/7.404；Alert浅色为.855/4.963/2.522/3.259，深色为2.064/5.523/2.829/4.228。四背景逐项保留，彩条Alert局部尾部及深色风景Menu仍有明显误差。风景可用的低梯度probe像素较少，这些body数据不覆盖完整pane、glyph、四角、rim或运动。冻结旧公式在新背景输入上的结果仅是反事实，不能写成该画布中旧包的实际GPU截图。


2026-11-13 圆弧裁切与 shader 高光边界的实际分支差异已修复：Toggle、Segment、Slider、通用圆按钮和 Dock 镜片的 CPU 使用圆弧，旧 shader 却在近边缘切到 Flutter 连续超椭圆 `cornerInset`。现在由实际 `Outline.Rounded` 识别四角均匀圆弧，按实际尺寸限制半径，并传入独立 `uCircularCorners`；Dock 两条直接 shader 路径显式使用圆弧。CPU clip、rim 与 shadow 继续使用实际 shape。Generic、双轮廓、goo 和 Dock 底座 Capsule 仍保留各自轮廓；Material Design 未改变。该识别器不宣称支持偏移、非对称或椭圆 RoundRect 的精确距离场。

圆角增量构建 `circular-corner-green-12` 的41项定向JVM全部通过；最终恢复构建的 `CircularGlassCornerTest` 与 `LiquidGlassShaderContractTest` 共17项全部通过，debug APK构建成功（24s）。这两轮是独立增量验证，不能与前述425项相加后写成一次完整429项运行。

真实API36 `LiquidGlassCornerRenderTest` 使用硬件Canvas运行完整生产AGSL，仅将最终输出替换为不透明RGB(edge/4)，绑定实际纹理child。使用真实CPU Outline和独立圆弧距离式，逐点检查四个肩部55–85°、向内.30–1.20dp，阈值固定.05dp。`circular-corner-direct-15`通过（2.101s）；同一测试APK仅将生产shader条件临时改为旧近边缘行为，`circular-corner-direct-red-17`真实失败（2.113s）；恢复后 `circular-corner-restored-18` 再次通过（2.095s），前后绿色APK的SHA256完全相同。三种尺寸的旧→新最大误差分别为Toggle静止.198901→.007853dp、Toggle按住.313949→.007495dp、Dock按住.484568→.008237dp。原始PNG、完整窗口、裁切坐标、逐点JSON、源码及APK哈希保留在会话artifact的`circular-corner-red-green-review`和各run目录。

这个诊断只证明shader圆弧边缘与实际CPU轮廓计算一致，不验证生产RenderEffect调度、backdrop光学、可见alpha或iOS原生四角。此前RenderEffect诊断后端的零尺寸失败及两个小图全黑均保留，没有将它们计作小控件真实几何失败或通过，也没有放宽阈值。原生可见白体的颜色阈值审计与此内部几何诊断分别记录。

修复版生产控件的四背景×明暗×静止/五项按住共48个首页Window状态，分两次获取：`circular-controls-backgrounds-14`保留30个实际Window，原整轮在深色彩条背景预检时受系统通知横幅遮挡而失败；`circular-controls-backgrounds-dark-16`补齐18个状态并通过（109.117s）。原失败保留，不能将该轮写成全矩阵通过。首页沿用legacy截图入口，未逐场保证display收敛；必须分别审计Window与最终屏幕合成，不以两者等同。该生产矩阵检查实际控件绘制，不是同世界原生完整角区对照，也不是动态轮廓验收。

圆角后续验收仍逐角色保留：Toggle/Segment/单双头滑块的完整四角运动轮廓与内部间距；Dock连续底座、选中镜片和按住镜片分别对照；顶栏及分享按钮用匹配实际内容的原生参考；Menu、Alert、五语言外轮廓及过渡时源卡片/光学anchor裁切；分享与课程编辑Sheet四角、深色边缘、侧边亮峰和展开收起。现有body probe明确排除perimeter，不能作为Menu/Alert四角通过证据。普通控件的点击、按住、拖动与取消测试继续由本会话完成。


## 2026-11-13：实际底座、菜单四角和中心几何

全组件圆角属于本轮验收：按各角色分别检查静止、升起、首峰、按住、横纵拉伸、回落及稳定后的四角、切线、边缘高光、折射、裁切和阴影。不能把 holder 或单一标称半径当作可见轮廓，也不能用相同宽高比的截图替代运动中的曲率。普通 Toggle、Tab、Dock、滑杆与菜单的点击、按住和滑动测试继续由本会话执行；早先“复杂拖动交用户”的约定只适用于需 Xcode 配合的复杂课程长按拖拽，不适用于这些控件。

Dock 镜片此前用原始场景加平色胶囊、假白边和重画选中色作为输入，遗漏了真正的底座材质。现在只保留实际底座 sibling，把其磨砂、连续圆角、边框和渐隐选中色与场景组合；图标和镜片独立，不参与回采。源和消费者共同位于 swell/stretch 父变换内，采样使用镜片自身坐标。API36 同一测试 APK 的红绿验证：实际底座从红换绿时，旧镜片内部红通道下降/绿通道上升各仅 .224654，修复后各 96.685484；painted bounds 不变。红版失败 2.590s，绿版通过 2.085s，最终源码复验通过 2.497s。该测试只锁定漏采背板，不是原生光学拟合。

修复后首页四背景×明暗×静止及五种按住共48个实际 Window 状态（21原轮30＋25补18），8个纯背景与40个非按住Dock状态新旧整图 RGB 完全一致；8个按住Dock差异限定在镜片区域。576个ROI未出现整块全黑或整块纯背景。原21受屏幕叠层阻挡失败保留，25补轮通过116.893s；legacy首页仍没有逐态display fence。独立审计有34态screen interior与Window完全相同，14态未收敛，其中1态差异在下方，不能把所有screen差异归为顶部通知。旧自然Dock视频为20pt低对比网格，Android矩阵为8dp高对比网格且world大小不同；可见彩色短纹仍待同输入对照，不能据此拟合光学系数。

菜单的默认连续圆角从24改为32，Alert34、语言26和Sheet36/20保持独立。用实际CPU path拟合候选，再用同一375×667源world、实际Window和固定原生颜色阈值后验：四角平均轮廓误差由3.303/3.417/3.527/3.952降至.301/.220/.165/.369dp，最大误差分别.792/.670/.571/.830dp。浅色两角训练后的深色两角独立验证也改善；原生颜色级边界不是alpha，32也不是Apple内部半径。四背景×明暗共32状态、8基线由26浅色16、27暗网格4、28其余暗色12合成；26/27原始display-readiness失败日志保留，28通过49.31s。两个高对比主场景各314415个opaque Window像素与display RGB误差为0。阈值家族和剩余右下角/材质残差保留，不称精确1:1。

另有独立几何错误：Generic单轮廓在精确中心点产生零径向向量和零梯度，导致距离/法线除零。复用既有双轮廓的中心方向与梯度下界后，同一测试APK在完整生产AGSL、仅替换最终输出为有限性诊断的硬件Canvas上由红变绿：251×147 Menu和321×173 Alert中心原本失败，四邻点及同实际path的双轮廓对照本已通过；修复后15点全过（红2.202s、绿1.954s）。这不验证材质、RenderEffect或原生可见alpha。原始APKs、shader、逐点RGBA、几何和SHA均保留在本会话artifact。

本轮增量定向JVM27项通过（shader contract、圆弧、CPU superellipse精度、嵌套popup与scrim），assembleDebug成功。不要把此前425项与后续增量相加为一次全量执行。新的原生iOS26.5自然布局Dock专用8场测试通过（110.674s），四背景×明暗只按住初始已选Home；8张host截图均通过完整PNG及实际投递DOWN/END时间区间校验；END按原始phase区分UP与CANCEL，不据此声称松手提交行为通过。它们补足同输入采证，尚需与同位置同world的Android专用fixture比较，不等于原生光学验收。

## 2026-11-13：精确轮廓与保留节点的生产回归修复

Menu/Alert/Sheet/语言菜单已知 CPU cubic 指令现直接传给 GPU；未知 Generic path 继续沿用原 profile。新模式保留实际 Float 控制点、平移中心、closing line 和曲线切线，求解失败显式回退。深处的深度仍是径向法向投影，不宣称欧氏距离场。硬件几何42覆盖12个fixture、5613个采样点；实际语言菜单终态含tiny anchor、density=2.975和五行探针。单/双轮廓终态诊断buffer一致；深度编码在8dp饱和，不能从该图推断更深处数值相等。这些仅证明几何传输，不证明完整材质或原生四角。

完整生产语言弹窗曾出现下四行泛白至约253–255。CPU、GPU编码与编译字节码确认根因：DrawBackdropNode构造参数与可变成员同名，初始化闭包永久捕获初始shapeProvider，效果在尺寸更新后仍使用展开前的形状；clip、采样scale和options的另外三处闭包也存在同类捕获。将构造参数改名后，四处均读取当前节点成员。没有降阈值或移除高光来遮盖错误。主效果入口另行拦截attach时Size.Unspecified/NaN，待实际尺寸到达再创建效果。

自然contrast生产夹具51/52用同一测试APK、同一6/5 RGB阈值完成红绿：红版后四行距第一行平均RGB差为12.714/7.517/12.717/15.821；绿版五行共12455个采样像素全部225/225/227，距离0，实际通过3.171s。contrast背景是显式覆盖测试输入；旧neutral的独立delta<6阈值失效原样保留，不与泛白根因混同。编码诊断53读取到已展开的48.235/115.294/230.588dp，8bit量化误差另记，不把编码测试称为视觉验收。

保留节点回归55/56固定128×96dp、同一个consumer和effects receiver执行4→40→4dp。红版仅phase1的scope.shape仍是初始对象，绿版全部观测为当前形状。两次共54个GPU探针本来都通过，三张对应PNG完全相同；不能把这个引用回归测试称为GPU像素红绿改善。自然生产51/52才提供另外的真实材质红绿证据。最新定向JVM57为四类25项零失败/错误/跳过，debug和androidTest构建同轮成功33s；不是六类或47项。实际圆弧及haze补测59显式启用后4项通过6.462s，58的错误haze类名和未启用圆角的skip原日志保留。

完整未修改shader的mode-only压力采集54通过45.078s，共1032个可归属FrameMetrics；每模式172帧来自两个各86帧的真实source重绘块。driver隔次才发生source重绘，原因未证明。Menu/Sheet/Language的GPU中位数差为+.033/+.838/+.723ms，但Sheet同模式块漂移3.284ms，不能把pool差精确归因于算法。该夹具sampledBlurPx=0走64tap frost，不能作为实页ready共享预模糊成本；共享CPU/profile/descriptor和旧shader asset增量也未测。deadline miss和约33ms提交间隔仍存在，采集PASS不是性能通过。

自然尺寸同背景Dock40的16个Window/display fence、REST定位和DOWN→HELD→CANCEL输入独立通过；磨砂底座、边缘和四角响应仍有明显光学差异。旧原生参考为独立UITabBar，生产却是四项SwiftUI TabView/UITabBarController，父容器上下文需单独补齐后才据此调光学参数。主题选中色保留MD同一颜色，不把这一用户要求的差异当成shader错误。普通控件全部点击、按住、滑动和快速切换继续由本会话测试，完整四角动态仍未验收。

2026-11-13 新增四项 SwiftUI 父容器参考 `swiftui-dock-parent-61`：原生定向测试2/2通过，host截图2/2通过实际投递DOWN/UP时间包围及PNG完整性检查。依据保存在会话artifact工作区 `artifacts/glass-optics-20261112/swiftui-dock-parent-61/{reference,attachments,verification}` 与 `native-test-summary.json`；REST PNG和AX来自完整导出的6个附件，HELD来自 `captures`。包围的是截图子进程的起止时间，不是每个像素的扫描输出时间，也不构成原生材质一致通过。

该参考为iOS26.5、375×667pt、2×，四个原生Tab沿用生产角色，父链为外层SwiftUI host→隐藏UINavigationController→Tab页面host。公开context确认背景canvas和真实HostingScrollView均为window内(0,0,375,667)pt，祖先背景透明；ScrollView内容375×1871.5pt、offset(0,-20)pt、adjusted top inset20pt，实际未滚动，内容底部padding92pt。child bottom safe-area策略沿用生产实现，将初始83pt通过additional bottom inset−49pt保留34pt。它补齐了本次父链与铺满背景条件，内容仍为合成文字行，并非完整业务页面。

两场REST的原生UITabBar holder均为(0,584,375,83)pt；可见底座约(21,584,333,62)pt，首个选中片AX为(25,588,88.25,54)pt，可见栅格约88×54pt。像素强边缘测量排除外阴影，REST边界约有0.5pt选边误差。两场HELD首片可见色散rim约x9.5–118.5、y578.5–651.5pt，即约109×73pt，rim/栅格选边误差约0.5–1pt；这不是真实alpha或公开layout包围盒。HELD底座中央上/下边约582.5/647.5pt、右边约361pt，左边被抬起镜片遮挡，不能将按对称推算的约347pt宽写成完整实测。holder、底座和镜片分别记录，不能互换。

FineGrid的window始终为dark；t=.390517s时UITabBar及其container/wrapper的effective style实际转为light，override仍为unspecified，外层controller/window仍dark。对应REST/HELD图片确为亮底和黑色未选前景。TonalBlocks的记录仍为dark，材质随明暗块背景变化、未选前景为白色。因此“dark样本却有亮bar”是本次原生局部适应的实测结果，不能按文件名强制改成固定暗色；两场静态/按住观察也不能证明完整自适应算法或光学系数。

取得61参考时，Android `NativeDockOpticalWorldRenderTest`仍为3项、316dp caller、274dp底座和94dp镜片的独立UITabBar旧参考，当时不能直接与61整图比较。当时推算的最小fixture调整为4项、375dp caller、offset(0,572)dp并保留生产21dp侧距和12dp阴影空间，使REST底座成为(21,584,333,62)dp；但修复前生产 `MIN_LENS_WIDTH=94` 会给出首片(25,588,94,54)dp、中心72dp，较新原生88.25pt宽多5.75、中心69.125pt偏右2.875。这是实际布局差异，不能只换目标数字消除。本段保留修复前的历史观察，后续四项fixture与生产布局改动见文末进展。原生holder(0,584,375,83)与Android carrier(21,572,333,86)也应分别记录。两端底层图案规格/原点可对照，但原生背后还有可折射的白色合成文字（含row8），旧Android仅有图案；SF Symbols、文字与Material图标、固定teal/white/secondaryLabel与产品主题角色也不同。现有证据支持几何和排除前景后的局部材质核对，不支持完整像素一致或动态阶段等价；产品选中色继续复用Material Design同一主题色。

61 后续实际修复将 Kyant 底座的原始 12 条 cubic 和 4 条 Line 直接传入精确轮廓，CPU clip 未改。`DockPlatterShape` 采用普通 Compose `Shape`，避免实现外模块 sealed 接口；actual-path 测试迁至 Android instrumentation，因 AndroidX PathIterator 的 JNI 库无法在本机 Robolectric 加载。设备上三项 Dock 加一项路径读取测试全部通过。首次 GPU 验证实际发现 AGSL 不支持位移运算，已改为逐段 `mod`/`floor` 解码 17 位精确整数掩码；保留 144-float 协议、v1 分派及原 solver。修复后 3 个 Dock fixture 的 1043 点与 1 个既有 v1 Menu fixture 的 317 点全部通过原深度/法线/coverage 阈值。完整 APK 与 androidTest 打包成功，设备安装文件与 host SHA256 一致。这些是实际轮廓传输验证，不是原生 Dock 材质验收。

语言菜单首开现在保留源卡片，直到 Popup 自己的替代卡片内容绘制并提交；光学采样与原 spring 时钟进一步等待源窗口隐藏卡片后的独立提交。61 自然时间采集通过六种交互，回调为 `en/en/ja`，首开八阶段及 Popup/DecorView 不同窗口身份断言通过。独立原片的首开连续 25 个编码样本、基线和稳定态共 27 张已逐张复核，未再出现 57 中只剩背景网格的空档。原始 MP4/trace、接触表及六张代表帧保留，其余解码图已删。该轮 motion-origin 回调距实际 UP 仍为 254ms，且 HWUI commit 不等于 compositor present；不能据此宣称原生节奏或每帧 alpha 一致。34 项纯逻辑 JVM 检查通过，但原有六个菜单 JVM 测试仍停在绘制/提交交接；无效的测试专用 shadow 与遍历改动已撤回，未放松原断言或修改生产 fence，也未将该轮写成全量 JVM 通过。

## 2026-11-13：四项 Dock 校准、缓存与菜单 JVM 后续进展

Android 专用 Dock fixture 现已增加与61父容器对应的四项 SwiftUI 参考，保留三项独立 UITabBar 分支。生产四项布局只依据375pt原生caller的实测参考校准；在匹配61的375×667dp fixture世界坐标中，底座333×62dp，首片(25,588,88.25,54)dp、中心x69.125dp，相邻中心间距78.916667dp。375dp之外仍按可用空间计算，宽容器的镜片可增长，不宣称其他宽度已获原生测量。三项274dp底座/94dp镜片及其他项数沿用原策略，Material Design 路由和主题选中色未改。

一次全量 JVM 尝试暴露了 eager exact 解析的问题：普通 CPU `createOutline` 也加载 AndroidX PathIterator，触发本机 Robolectric 无法加载 JNI 的错误。`DockPlatterShape.Entry.exact` 已改为 lazy，仅光学描述符请求才解析实际指令，CPU clip、background 和 shadow 不再主动解析。原失败证据保留，不能用此前的设备路径测试代替本轮全量 JVM 结果。底座的dp Path与径向fallback profile另按尺寸、density和布局方向保留在同一Entry内；只有Path及single exact descriptor身份都对应时才复用profile，双轮廓等模式保留原行为。`touchGlow`仍逐帧生成实时uniform，缓存不冻结按压光晕；当前没有据此记录新的实测性能结论。

此前六个菜单 JVM 停在绘制/提交交接的状态已有后续修复。共享 `SoftwareWindowComposeRule` 只对已附着、有实际尺寸且需要绘制的窗口根节点执行软件Canvas绘制，让绘制后post的工作在后续空闲轮询中完成；不手动调用应用handoff，不代替生产回调，也不额外推进Compose时钟。`GlassLanguageMenuTest`与`GlassSelectionColorRegressionTest`继续使用原断言，本会话主验证流程已实际运行这16项JVM并全部通过。旧shadow、遍历尝试及其失败记录保留；这16项定向通过不等于全量JVM或设备GPU验收。

原生六场补采中，五场取得实际UP；`ColorStripesDark`两次尝试均以CANCEL结束，仍缺该场正常松手证据，不能算作六场UP提交通过。原生局部effective appearance变化及其材质差异尚未在Android实现，不能由文件名强制归为固定明暗，也不能用现有几何或输入测试替代外观验收。

截至本次记录，新的62自然时间录制、完整JVM复跑和本轮设备测试仍在进行中，尚无最终通过结论；结果在完成并核对原始输出后另行补记。前述历史失败、未收敛截图和CANCEL证据继续保留。


62 集中验证后续：`:app:testDebugUnitTest` 完整运行615个suite、4578项，0 failure/error、3 skipped，1m59s结束；两组共享窗口夹具16项亦先单独通过。两次全量失败（eager PathIterator/JNI 22项失败、另一组菜单未绘制导致内存耗尽）仍保留。debug与androidTest打包通过；API36实际路径及缓存8项通过，Dock v2三fixture和Menu v1一fixture的GPU深度、法线、coverage原阈值复验通过。缓存本身尚无新的独立性能归因。

62自然菜单六交互通过22.871s，selection仍为`en/en/ja`。首开源/Popup提交顺序由不同window identity记录；独立视频审查42个连续首开编码样本、基线与稳定/选择态，源/替代卡片保持到展开，未见只剩网格的空档。首次有效场景marker为sample55，不能误用前期同为黑色的marker0；保存原片、时序、接触表和9张关键图，其余47张重复解码图已删。该轮UP→motion-origin回调259ms，帧时钟origin237.219ms；相较61的254ms没有证明首开速度改善。HWUI commit不是compositor present，视频也不证明每毫秒一致。

四项Dock设备矩阵的首轮因fixture将独立dp取整误算成一次比例而失败：density2.975时`round(375*d)-2*round(21*d)=992px`，并非`333*d=990.675px`。现改为断言生产准确取整合同，native-world边缘仍保留一物理像素原容差。第二轮几何已过，display readiness为.994675、未达原.995；完整Window/display差图仅在系统状态栏52–106px和底部手势条2607–2618px，Dock1700–2020px逐像素RGB差为0。新fixture只显式排除实际systemBars/displayCutout inset（本机0/135/0/48），保留完整PNG及99.5%/RGB3原阈值，并断言整个Dock holder及阴影空间仍在比较域内。前两次失败与原图保留；新一轮矩阵结论待核对后补记。


62修正比较域后的四项矩阵通过37.062s：四背景×明暗，共8baseline、16REST/HELD；49个原始导出文件全部与设备SHA一致。系统栏排除域由实际Insets形成，Dock holder与阴影空间完整保留，阈值未放松。深主题局部材质仍服从应用主题，与FineGrid原生局部变亮的残差仍存在；此PASS只确认世界坐标、选中状态和Window/display收敛。当前主APK与测试APK实际安装hash核对一致；重复架构APK和自然视频多余解码图已清理，唯一原始证据保留。


## 逐控件加速度、端点拉伸与回弹验收

用户2026-11-13补充：Dock、Tab/分段、Toggle、单头和双头滑杆各有自己的动态响应，均需以iOS26.5实际轨迹与关键帧对齐。每类分别检查不同实际速度/加速度，触底后继续向外拖、停在端点持续顶住、向内反向、松手回弹、取消和连续快速输入。逐帧记录实际pointer delivery、位移、速度/加速度、轮廓宽高/四角/切线、拉伸/弹跳峰值与时间、透明度和折射。不能以requested XCUITest速度当成实际加速度，不能把其他控件或统一spring的通过结果代替本控件的动态证据。

当前16态Dock矩阵只覆盖REST与初始选项按住后的稳定HELD，使用1600ms测试时钟稳定；不覆盖拖到端点、非匀速轨迹或释放弹跳，不是动态验收。接下来应先核对已有每控件的实际native输入/观测能力，再用同一实际输入路径配对Android，保留不同系统输入分发与视频采样间隔的限制。


2026-11-13 原生端部动态批次65已使用iOS 26.5四个独立SwiftUI控件完成一次采集：Dock采用真实四项TabView父容器，另含Picker、Toggle、Slider；每控件慢/快、正/反向，共16次实际UP，无CANCEL。构建4.799s、测试命令135.643s，合计141.703s。Dock全树1868帧均标记truncated，但所选platter/lens/四按钮每帧都存在；其余三项无truncated。5,198行所选lens的公开presentation矩形只是几何载体，不是可见alpha轮廓。快Toggle仅5个移动间隔，不能解析精细加速度曲线。

Slider慢拖最终值为0.9768656492/0.0440298505，未达业务端点；0.9804266095/0.0402547009极值分别出现在UP delivery后86.192/92.815ms，不能称为顶住极值。快拖首次1/0也在UP后105.419/93.136ms；本轮Slider held阶段均未证明业务端点饱和，不能把越过AX外框当成触底平台证据。

Android配对批次66原样回放这四份触点，固定初始anchor平移、1pt=1dp，保留每个sampleT与手势间隔，未动态归中、缩放或夹住路径。四项各一次通过，仅说明回放执行完整。独立核对1,409个不同注入身份全部在current/history找回，1,414条源行中5条受stationary→MOVE和毫秒量化影响共用身份。实际receipt相对计划采样的中位数Dock/Picker/Slider/Toggle分别9.749/14.651/13.054/11.607ms，最大48/44/33/50ms；额外重采样与批次不同，因此不宣称实际收到的v/a一致。

66初版离线比较脚本将Dock未标tag的首Tab误选为lens（None==None）；该脚本已修正并由独立离线回归核对，原始生产语义和视频中的lens正常移动，不能据错误报表修改生产物理模型。修正后按各系统实际DOWN receipt为原点、不拟合delay、仅配对25ms内真实观测，Dock四手势中心X RMSE约7.61/7.36/7.71/7.45dp，Picker约4.59/4.48/5.30/8.11dp，Slider约1.80/4.13/19.23/9.74dp，Toggle约3.61/3.86/2.17/2.34dp。误差含实际派发、刷新相位、getter括号和模型差异，不是单纯物理参数误差，也不是光学等价验收。原始视频各保留一份，未导出全量视频帧。
