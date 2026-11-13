# Android 玻璃控件与 iOS 26.5 原生对照

2026-11-12 使用 iPhone SE 3 模拟器、iOS 26.5（23F77）、375 × 667 pt、2×，观察系统 SwiftUI / UIKit 控件。Android 按 1 pt = 1 dp 对照控件局部尺寸，不拉伸整张屏幕截图。参考程序见 `tools/native-glass-reference`；Android 设备截图与真实触摸回放入口为 `NativeGlassParityCaptureTest`。

本次实机是 OPPO PKX110，Android 16 / API 36。安装包为 `com.lingion.sleepy.debug`，与正式版数据隔离。下面区分原生测量、实现和仍存在的差异；通过测试不等于每帧像素相同。

2026-11-13 已按用户要求清理历史构建、下载和可再生解码缓存。下文历史 APK 哈希是当轮观测，不代表每份旧 APK 仍保留；当前仅保留现用 debug/test 各一份，稳定入口链接到同一构建输出。未决问题的原始视频、trace、测量及必要关键帧保留，重复导出通过相对链接去重。

Material Design 与 Liquid Glass 严格分流。Material Design 的单头、双头滑杆恢复默认 M3 的 handle、track、tick 和尺寸，不安装玻璃绘制；保留历史细拖输入修复与原值标记。Material Design 色相条保留原来的满幅彩虹、圆形指示器、双描原值标记及既有输入行为。Switch 使用 M3，分段选项使用原点击路径，Dock 也不会因残留的玻璃 Dock 偏好绕过当前外观。

仅 Liquid Glass 滑杆采用用户指定的 Device Hub 轨道和下方点状刻度：单头及色相滑块 37 × 24 dp，双头按 0.75 等比缩小为 27.75 × 18 dp；两者和原值标记共用横向圆角椭圆轮廓、白色与轻阴影。轨道 8 dp，刻度点 2 dp、位于轨道中心下方 14 dp，交互高度 48 dp。玻璃升降本身两轴共同缩放，单头另叠加独立横纵拖动形变；双头暂保留等比起落。色相条保留彩虹含义，密集业务档位的刻度按最少 12 dp 间距抽样展示，不改变吸附档数。此前把玻璃几何套到 Material Design 的实现已撤回。

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

### 67–69：连续滑杆输入、独立形变和端部对照

滑杆现按实际输入接收时间驱动，硬件采样时间另存。连续发布数值前先更新请求值，同值父组件回写不再中断本次输入；父组件拒绝数值或外部更新任一范围端点时取消当前指针。取消保留最后已发布数值，不调用完成回调。以实测宽度减静止滑块宽度作为形变行程，RTL使用负行程；335dp单头夹具行程为298dp。原断言不变的旧实现红版分别重现拒绝值后多发回调、两个接收时间段被合并的问题。

单头Slider使用独立的加速度形变参数，来自65慢/快正向训练，完整反向手势保留验证；Segment与Dock参数未改。参数和240Hz有界积分均为经验近似，不是UIKit内部常数。单头的横纵形变叠加在已有起落尺寸上；双头暂保留等比起落，不能将单头测量结果代作双头验收。原生逐帧宽高验证以实测原生lift为条件，只隔离形变，不能证明中心、lift节奏或光学一致。

Segment端部视觉目标改为物理12dp限幅的既有.55橡皮筋公式，业务选择、触感与松手目标仍限定在有效选项。该候选接近原生慢拖末段的约±9.08pt held位移，但不是完整动态曲线。四项Dock仅将局部镜片的spring目标限定为端部±4dp，保留spring输出瞬态；其他项数保留原规则。其父底座位移和横纵缩放仍未修正：原生在手指仍处选项范围内就会移动，正反向同一手指位置对应不同父位移；现有Android父底座主要在越界后才拉动。这一差异不能靠缩小外层高度或硬裁最终镜片输出解决。

67同轮定向JVM实际运行63个suite、362项，0失败、错误或跳过；debug和androidTest打包成功，命令总时98.387s。测试覆盖连续输入、接收/采样时钟、父值拒绝、范围更新、取消、RTL模型、独立形变及两个端部映射。旧四项Dock目标在同一组测试中真实失败5项，修复后通过。一次测试源码编译失败未执行测试，残留XML单独标记，没有计作该次运行结果。此前62完整4578项属于较早源码，不能作为67最新完整全量结果。

68补采使用原生Slider从.75正向、.25反向起步，慢/快共四次独立运行，全部在实际UP前达到1/0，UP后没有业务数值回调。iOS26.5（23F77）、375×667pt、2×；两项原生测试通过，build8.255s、test109.155s。最终手指超过实际业务饱和阈值分别10.75/11.75pt。最后150ms完整held观测中，中心减自身m41后的端部位移在两种速度均为+4.495645/−4.767461pt；采样到的整条Slider及祖先矩形不动。快速约+8.912/−9.006pt的最大外移出现在UP后约67ms，属于释放过程，不能写成held极值。现有记录缺少layer.position、anchorPoint及bounds.origin；该分解也不能命名为已恢复的原生内部target。

冻结65单头参数在68新手势上没有调参、位移缩放或拟合延时。慢正/反全窗口宽高RMSE为.126/.082与.115/.075pt，快正/反为.432/.281与.456/.298pt（同样以原生lift为条件）；快拖最后150ms仍有约.970/.628及1.062/.697pt残差。68时Android视觉目标仍与业务值一起夹住，尚缺上述Slider端部外移。只有两种不相等的excess不足以识别通用端部曲线；下一轮需分开测0/2/5/10pt等实际excess及至少1.2s的稳定按住，不能照搬Segment的12dp参数。

69在API36实机原样回放65四控件与68四个饱和Slider轨迹，8项执行通过；40个导出文件及唯一原始视频均核对设备/host SHA。主包SHA256为`611e6653d39aba081d2594c341a7d0c22fdaedb8a491d5ca6bc64087dcb11f0d`，测试包为`50c6fa1b864d980e7e7c872743b613746822da8ad00d3fd7c8d05d510d39d97c`，实际安装hash一致。固定anchor平移、1pt=1dp，保留源时间与路径；实际current重采样和派发仍按系统记录，执行通过不等于逐帧对齐。

同一无拟合延时的真实帧比较，四手势顺序仍为慢正/慢反/快正/快反：Dock中心RMSE从66的7.61/7.36/7.71/7.45降为4.46/4.28/5.12/5.14dp；原Slider为.93/3.66/17.57/8.17dp，快拖宽高误差减小。Segment为2.57/1.36/6.54/9.96dp，快拖中心较66更差，必须保留。两系统实际越界输入和绘制时刻不同，Android重采样current可先于原始outside样本越界；不能仅按原始样本断言端部helper与该误差无关。Toggle本轮未修改，中心误差仍约3.61/3.88/2.18/2.04dp。所有误差均混合实际派发、刷新相位、读取时段与模型残差，不是纯参数误差或可见光学轮廓。

原始证据分别保存在会话artifact的`slider-input-time-67`、`native-slider-saturation-68`和`android-endpoint-fixes-69`，包括独立审计、红绿日志、真实输入、原始视频和逐帧比较；不向日常CI加入采证矩阵。69当轮的Dock父底座、快速Tab、Slider端部外移和双头独立参考仍待完成，后续Slider工作见下文；当前不宣称动态1:1验收。

69的Segment另有离线观察相位诊断，保存在`android-endpoint-fixes-69/independent-center-model-review`。独立按原方程、实际current delivery及receipt前后括号重放，未修改生产参数或原始比较。固定使用Android前一次实际vsync时，四手势中心RMSE约.05–.39dp；原生公开displayTimestamp下快拖RMSE为1.178/1.228pt，最大3.586/4.607pt。原生getter中点比该timestamp晚约19ms，在getter中点强制模型的快拖RMSE为5.388/5.276pt。两种时钟只是分开报告的诊断，不是已测得的物理显示相位；不能以它们抹去上文真实最近getter配对的大误差，也不能把约20pt峰值唯一归因于弹簧。42/42离线不变量通过，不构成新的设备或光学验收。

### 70：单头Slider持续顶住后的公开位置

`testSwiftUISliderOverdriveLongHold`在同一iOS26.5原生Slider上完成10次独立launch：.75正向/.25反向，名义0/2/5/10pt慢拖及10pt快拖重复，目的地按住1.35s，再保留至少1.2s尾段。构建5.773s、测试152.550s，1项原生测试通过。实际输入以`source=delivery`为准，排除coalesced，不用请求速度代替实际速度；末段位置不变至少1.350s。稳定值只取完整getter括号落在UP前300ms的帧，每次17帧；公开position不能命名为私有动画target。

记录器新增六个公开presentation字段：`positionX`/`positionY`为直接父layer坐标，`anchorPointX`/`anchorPointY`为bounds归一化锚点，`boundsX`/`boundsY`为本地bounds原点；session以`layerGeometryFieldsVersion=1`声明坐标。70中所采stage、UISlider和glass visual element的全部公开几何保持不变，镜片锚点为(.5,.5)，最后300ms的m41为0、bounds为57.35×37.2pt。中心与公开position/锚点/变换的分解逐帧核对通过；未重建缺失wrapper或额外CALayer祖先。

以下excess从实际终点坐标及既有10pt起拖/335pt业务映射计算，位移相对各自最终REST，不是名义请求：

| 方向 | 实际饱和excess（pt） | 稳定held位置偏移（pt） |
| --- | --- | --- |
| 正向 | .25 / 2.25 / 5.25 / 10.25 | +.154295 / +1.273258 / +2.641662 / +4.353381 |
| 反向 | 1.75 / 4.75 / 9.75 | −1.011321 / −2.435056 / −4.206609 |

正反向10pt请求的快慢重复具有相同的实际excess，稳定held位置完全一致。名义零反向实际excess为−.25pt，held fraction为.000746268663，尚未饱和，+.222388pt只是剩余业务行程。该次UP后有0、.000114750030、.000746268663三个数值回调，最后又恢复非零值；最终载体中心同时为38.5pt。两种观察均保留，不能据此推断内部取整或吸附。独立交付/载体审计232/232检查通过。

在看过数据后选定的rubber family `shift=13.62×(1−1/(1+.62425×positiveExcess/13.62))`，以正向.25和10.25pt两点推导，其余70及68的合格饱和held样本也符合，最大绝对残差2.043e−13pt。这是335pt宽、298pt静止镜片行程下的回顾性核对，其他样本不是盲验，也没有证明其他宽度的原生规则。持续按住后，快正/反向释放仍在UP后约84.607/48.781ms出现+8/−7pt中心峰值；末300ms已经静止不能解释这些峰值，现有held公式不构成释放速度模型。

证据在会话artifact的`native-slider-overdrive-70/independent-overdrive-review`及`independent-endpoint-family-review`，包括实际输入、完整载体帧、末300ms、公开位置分解和原始trace哈希。载体bounds与matrix不等于可见alpha轮廓、折射支持域或最终像素。

### 71：端点视觉位置与仿射载体，主机红绿及设备对照

当前工作集将单头thumb拖动的未夹住相对值与已发布业务值分开：只有thumb拖动的视觉目标可以增加上述held外移，业务数值仍夹住；轨道点选和双头沿用原规则。同值父组件回写保留视觉外移，继续外拉不重复发布同一业务值；松手/取消或父值拒绝撤回外移。既有单头加速度形变仍由业务位置驱动，避免用新增端部外移重新激发冻结的形变模型。335dp以外保持相同物理dp响应及13.62dp上限，是响应式实现政策，尚无其他宽度原生证据。这里未模拟未解释的8/7pt释放峰值。

单头绘制工作集保留等比lift的外层触摸/语义盒，将阴影、玻璃和白体放在居中仿射子层，分别应用X/Y形变，零lift释放尾段也继续变形；轨道采样使用完整矩阵补偿。动态可见几何应读取`glass-slider-thumb-carrier`，不能再用刻意保持等比的外层`glass-slider-end`代替。双头和Material Design不采用该单头变换。

原生公开bounds加matrix支持这样的载体分解，但“等比基础胶囊再仿射变换”只是可见轮廓假设，未证明原生内部clip或光学边缘。新增测试分别检查载体轴向/外层稳定、白体肩部像素是否按椭圆变换，以及轨道world纹理补偿；通过这些内部回归也不能据理论曲线声称原生可见四角已对齐。

71主机验证实际记录：旧端点行为在10项测试中真实触发3个断言失败；保留相同最终包围盒而改回圆弧重绘的counterfactual，在白体肩部像素测试真实触发1次断言失败，失败是实际白色像素而非节点缺失或编译错误。首轮集中运行434项，433通过、1失败、0错误/跳过；唯一失败来自测试使用`getUnclippedBoundsInRoot`将变换后位置与未变换layout size拼成矩形，误把57.25dp等比宽当作期望65.9525dp仿射宽。随后只改测试几何读取为实际`boundsInRoot`并按实际density转dp，所有尺寸/中心/owner断言及1dp容差保持原样；补跑3项Thumb测试全部通过、0错误/跳过。同一26s命令成功生成debug和androidTest APK。没有再运行一轮434项全绿，不把两轮拼成新的完整运行。

主机红绿日志、XML及源码恢复哈希保存在会话artifact的`slider-endpoint-affine-71/endpoint-red`、`affine-red-receipt.json`、`green-first`、`affine-green`及`affine-green-build.log`。肩部像素和轨道纹理是在Robolectric native graphics下的内部实现回归，不能代替真实GPU光学或设备逐帧对照。

71在API36手机回放native70全部10份输入，10项仪器测试通过，50份trace文件及唯一原始视频的设备/host哈希一致。实际安装主包SHA256为`6e20a77fcf8c926bda77f960d5b238bad5688c829bf294574e1b04ba33bd6d66`，测试包仍为`50c6fa1b864d980e7e7c872743b613746822da8ad00d3fd7c8d05d510d39d97c`。源独立审计192/192、Android完整性/观测审计152/152通过：10份源共568个去重实际样本（538 main delivery、30 unique coalesced），保持各自原采样间隔、固定anchor平移及1pt=1dp，每次注入均在Android current/history找回。实际current还有额外观察，两系统receipt时刻与current输入不同，不能称为相同实际波形。

以各自最后300ms完整held getter括号相对最终REST比较，9个已饱和样本的中心位移差最大.170985dp/pt；快慢两组10pt请求的Android稳定外移约±4.20168dp。这里减掉各自REST只是独立的端部位移指标，没有改写整条运动。无拟合时间平移的整窗口真实帧比较仍有1.642–5.498dp中心X RMSE，见`slider-endpoint-affine-71/geometry-review/report.json`；读取区间、实际派发和刷新相位的限制保留。这些载体矩形不是独立显示像素、可见四角或材质等价性。

名义零反向须另列：原生held仍未饱和，Android最后pressed CURRENT的实测x为107.350586px，超过按源码机械重建的业务零阈值108px约.649414px（.218290dp）；同一末事件的HISTORY与后续UP均为108.83789px、位于阈值内。独立Float重建的raw fraction分别为−.0006513595581与+.0008404105902，全部pressed current业务回调重建最大误差约1.77e−8；这不是直接记录到的内部raw target。其后约1.356s没有新的pressed MOVE纠正，源码UP分支在重新计算/发布坐标前释放现有目标，Android没有UP后业务回调，最终保留0。原生末delivery为x20pt、held fraction仍.000746268663，UP后另有三次回调。CURRENT与HISTORY差异的具体平台机制尚未证明；不能把这次真实forcing差异写成相同输入下的回归或验收通过。该项10/10源码/输入核对及全部实际输入保存在`independent-input-audit/zero-reverse-causality.json`。

71另对70释放阶段逐帧拆分：快正/反的+8/−7pt峰值来自公开presentation `positionX`，峰值括号内m41为0、变换为单位矩阵，锚点居中，已采父层几何不动，不能由bounds收缩解释。相同excess的快慢手势具有相同稳定held位置，末段约1.36s没有touch delivery，但释放后的公开位置轨迹不同。140/140离线检查及24个输入哈希通过；模型层位置、额外layer祖先和私有交互状态未采集，不能识别唯一速度规律。证据见`slider-endpoint-affine-71/independent-release-mechanism-review`。

本轮仅补足单头held外移及仿射绘制的部分证据；原生长按后8/7pt释放峰值、完整可见轮廓/四角、材质与折射、其他宽度和双头独立动态参考仍未完成。输入/几何独立审计均在`slider-endpoint-affine-71/independent-input-audit`，保留完整观测和不一致项，不称动态1:1。

供后续抽离的[本地Liquid Glass库清单](../libraries/liquid-glass/README.md)与[资产目录](../libraries/liquid-glass/asset-catalog.json)已登记当前资源。现阶段仅为清单，尚非可独立构建的库，也未发布；保留原有来源说明和待核实的来源问题，未更改许可证。后续抽离须另处理依赖边界与来源核对，本次主机测试不构成独立库验证。

### 72：四项Dock父层接收时钟与单头Slider同世界光学采证

四项Dock父层平移现使用独立的`NativeDockParentMotion`，由实际DOWN后的物理手指位移驱动，不再用逻辑选项、夹住后的镜片位置或仅越界的pull代替。候选来自65的333×62pt SwiftUI四项父层：`u=.010287932530511721×(fingerX−downX)`，`x''+2ζωx'+ω²x=ω²u`，`ω=9.488283178142856/s`、`ζ=.6099720458279732`、单位质量刚度`90.0275176686287`。仅慢正向gesture0用于拟合，慢反向及两个快拖为冻结后的留出计算；这些轨迹曾用于机制观察，不是前瞻盲验。该线性候选没有新增通用端部限幅。

原生实测及候选误差保持如下，没有用Kotlin数值容差代替原生验收：

| 原生手势 | 用途/观测数 | 全active RMSE（pt） | 最大绝对残差（pt） | UP前150ms RMSE（pt） |
| --- | --- | ---: | ---: | ---: |
| 慢正向 | train / 209 | .044699370 | .156530395 | .150339528 |
| 慢反向 | holdout / 214 | .044642326 | .156119182 | .148561294 |
| 快正向 | holdout / 57 | .291415316 | .586557971 | .212318315 |
| 快反向 | holdout / 61 | .294340323 | .598668836 | .249824567 |

证据只覆盖该父层尺寸、从静止开始的四次单向手势、相同293pt最终DOWN位移幅度及约250ms末端保持。它没有识别任意宽度的原生规律、快速反转、打断或释放机制，也没有修正共同lift、父层横纵缩放、完整可见轮廓或光学。其他Android四项宽度明确外推相同物理dp响应；UP/CANCEL/外部选择使用既有Android `PressSpring`回零，打断DOWN保留当前位置/速度并重设手指anchor，这些是实现政策。

`GlassDockMotion`在命令入队时转交可选物理坐标，父层有独立receipt队列。冷帧从首个合资格的实际receipt开始，MOVE按receipt拆分积分，保留首帧从实际输入到帧尾的elapsed；硬件sample时间没有代替receipt，也没有拟合延时。既有七弹簧的Press仍在首个合资格帧尾建立起点。动画倍率只除一次，倍率0立即结算当前held目标或返回0；epoch阻止取消/新DOWN后的旧MOVE和release，收稳后不保留父层空转。

生产四项在移动层外的pointer owner上以`LayoutCoordinates.localToWindow(position).x/density`取得DOWN和每个MOVE，包含≤4px、仍走`trackGlow`的输入；RTL保持物理屏幕+X。输出将期望window向量通过同一owner的`windowToLocal`逆映射后写入父层`translationX/Y`，避免caller scale再次放大响应。这是静态可逆caller仿射的坐标处理政策；已回归的caller条件为水平scale .8/1.2，旋转、剪切及动态祖先变化没有新增原生实测验收。density改变会重建pointer handler，防止旧物理dp anchor跨密度存活。三项继续原pull平移，父层scale公式也保持原样。

新增`dock-parent`标记位于包住底座、外部内容、镜片及内部内容的整块移动carrier。当前参考布局为333×86dp，含62dp底座上下各12dp阴影留白；它不是可见plume或alpha轮廓。实际中心平移可独立读取这个carrier，`DockLensBoundsInRootPx`仍描述绘制镜片，并显式订阅新父层平移以更新held/return期间的`localToRoot`。仓库的`dock-parent-input-65.csv`和`dock-parent-reference-65.csv`保留362个实际main delivery和541个原生观测，附source/frame/raw行号、getter括号、实际手指及冻结候选残差。helper的`rest=1e−9`与回归`1e−4dp`分别是数值停止和Float/纳秒舍入容差，不是原生等价容差。

72主机红绿严格分开记录：最早的SDK路径基础设施失败未执行测试，单独保留；配置环境后由API兼容no-op helper运行15项，出现12个真实断言失败、0错误/跳过，编译和资源发现成功。实现后首次定向133项为132通过、1断言失败、0错误/跳过，15项helper全部通过，命令在单测处停止、尚未产出该轮APK。单独诊断仍是同一项断言失败，记录Explore两份实际文字bounds、正确touch root点、carrier和镜片均完全静止；原因是手工时钟在snapshotFlow唤醒前一次推进2500ms，七弹簧直到末帧才建立Press起点。只将夹具改为先`waitForIdle`、显式首帧并同步，再推进原2500ms，保留所有原断言和诊断，没有为该失败改runtime。

随后同一命令重新执行133项定向测试，失败/错误/跳过均0，debug和androidTest APK也构建成功（约32s）。过滤范围为`*Dock*Test`、`*GlassSlider*Test`、`*NativeSlider*Test`、`*TimedGlassControl*Test`，这不是全仓完整suite。新增15项helper和7项Compose契约覆盖真实receipt首帧、固定手指继续演化、正负方向/anchor、epoch、倍率0、Android返回政策，以及四项范围内移动、阈值内移动、三项既有路径、静止未选中项按住、RTL和静态caller scale。红绿、诊断XML和APK安装hash分别在`android-dock-parent-response-72/{parent-red,parent-green-first,parent-direct-diagnostic,parent-green}`及`installed-apk-receipt.json`；源参考和数值推导在`helper-reference`，不能把这些JVM结果当作设备动态或视觉对齐。

Slider另新增八个`testSwiftUISliderOptics{FineGrid,ColorStripes,TonalBlocks,Landscape}{Light,Dark}`公开方法，四背景×明暗各独立launch，保留原生SwiftUI Slider自然父层和25%值。每次保存REST、对准实测AX-local `(93.5,16)pt`保持6s，再保存released REST；实际窗口375×667pt、Slider AX frame为`(20,414,335,31)pt`，实际DOWN均为`(113.5,430)pt`。这组不能与旧AX归一化`.25/.5`按住记录混称同一几何。原生构建5.159s、测试170.879s均exit0，8/8 held PNG经实际DOWN/UP括号及文件完整性核对，截图起始在实际DOWN后约651.945–661.943ms，图像hash逐项保留。

Android72的`NativeSliderOpticalWorldRenderTest#captureNativeSliderOpticalWorld`也已实际exit0：四背景×明暗8场景、16份REST/HELD控件图与8份背景基线，Window/display分别保存，共48 PNG及result.json的49份设备/host hash全部一致。它使用生产单头、335dp宽和完整375×667dp背景世界，导出实际四角、单位basis、holder/carrier、DOWN/CANCEL注入记录、active held状态和HWUI readiness（无独立Android平台receipt trace）；用合成Compose时钟结算光学状态，不能拿来说明自然时间节奏。首次REST残差只允许每轴≤1dp的一次固定光学采样平移，本次为`(+.22270203,−.08401489)dp`，跨背景/主题/REST/HELD及清理CANCEL复用；不缩放或移动背景世界，也不能用该平移证明生产几何相同。值始终.25且没有value/finish回调。

Dock72实际四手势回放exit0，5份trace与唯一原始MP4均核对设备/host SHA。独立比较按各自实际DOWN receipt为原点，只选最近真实window draw，没有延时拟合、插值或重拟合。严格使用两版本都完整held的同一540个原生raw行：

| 手势 | 共同held观测数 | Android69中心RMSE（dp） | Android72中心RMSE（dp） | Android72最大绝对差（dp） |
| --- | ---: | ---: | ---: | ---: |
| 慢正向 | 209 | 3.579757 | .046427 | .162079 |
| 慢反向 | 214 | 3.587388 | .047706 | .153100 |
| 快正向 | 57 | 4.666596 | .198231 | .390251 |
| 快反向 | 60 | 4.245231 | .212943 | .423614 |

全部541个原生合资格观测仍保留。快反向frame1628/raw2419最近的Android72 draw835已在UP之后，因此不计入共同held表，也没有换成更远的帧。实际输入分发数量和时刻仍不同，快正/反向同时间手指差最大约42.552/22.410dp，最近draw相位差最大25.711ms；较低的快拖总误差不能反过来证明候选模型比原生冻结残差更准。原生全部541帧tree层truncated=true，但目标parent每帧存在且presentationAvailable=true。载体几何不是光学像素；当前结果只支持此父层平移实验，不验收共同起落/缩放、其他宽度、反转、释放或打断规律。独立parent比较及最终LF参考的raw/解析审计31,150项检查通过，完整输入差、getter括号、重复配对及缺失项见`independent-dock-parent-review/device-parent-comparison`。

Slider独立比较确认8份背景、16份控件及全部hash有效。16份控件各120048个readiness样本的RGB3匹配率均1、最大误差0；同世界背景crop的RGB MAE仅.482–1.338/255。排除轨道和保守标题源区域后，HELD下部内芯RGB MAE仍为6.875–23.838/255。安卓在网格与明暗块上出现重复高饱和彩虹边和亮白下缘；风景内部更明显地扭成带状，深主题内芯也偏亮。明暗块下缘窗口差38.83/41.78；原生明暗角色排除crop差.109–.243，Android差4.192–7.336。不能用背景的小差异或readiness通过代替材质验收。

源码确认单头`GlassSliderThumb`复用`controlLens`，该工厂直接返回Dock的同一lens，包括dispersion6.5、深/浅brightness1.33/1及rim.3/.6。这是已确认的耦合和待验证原因，不是已证明的唯一原因；本轮没有修改光学runtime。下一步应分别做Slider独立分支的单变量诊断：仅取消色散、仅将深色brightness置1，分别复用这组固定世界与预声明窗口，保持Dock和其他系数不变；不能同时改多项后把变化唯一归因于某项。

REST胶囊大体相近，但本轮未测得原生实际可见alpha边界，31pt AX高度不能当圆角半径；HELD端帽的源色瓣、色边、强度与直线交接明显不同，也不能由受材质污染的窗口直接拟合半径。实际HELD中心还保留生产取整导致的−.16806dp偏移，没有第二次校准。原生staticOptics省略presentation遍历，Android使用合成时钟，两者不构成自然动画/四角等价性。独立原图、窗口方法、来源与全部未通过项见`independent-slider-optical-review/findings.md`及`comparison.json`。

本轮主包`fc8f0596dd3c4a68517857ede3a122eb80d077fb6a40a77f03a2d3884d091727`和测试包`794470570f2fcbbee11be8bdb3acbc80d7dae984bc600dad046c66f030c4027e`已安装且与设备SHA一致。已删除约90MB无用架构APK，以及新鲜hash再核对后的55个手机重复导出文件共39,746,940bytes；Dock设备视频/pid由controller核对后删除，host唯一原片和失败证据保留。手机已恢复MainActivity。当前资产清单已登记新增父层helper，仍只有一份运行实现，尚未抽离或独立发布。


提交前检查另发现新增CSV采用Python默认CRLF而被Git判作行尾空白；导出器改为显式LF并从同一原始证据重新生成，两份CSV的362/541条解析记录逐字段完全一致，仅行尾字节变化，说明与来源hash已同步。随后单独重跑15项`NativeDockParentMotionTest`全部通过，0失败/错误/跳过；该15项是格式修正后的独立补验，不拼成新的133项运行。`parent-lf-verification`和`csv-lf-normalization.json`保留回执，runtime及已安装APK未变。

### 73：单头Slider材质消融与两窗口film假设

本节先记录有界诊断，不据此选定最终生产材质。A、B、C及组合D均从完整冻结72的`GlassSliderThumb-baseline.kt`分别建立临时源码，并非逐轮累积修改：A只把单头dispersion置0；B只把深主题单头brightness置1；C只把单头rim置0；D同时把单头dispersion置0、brightness置1，保留原rim、refraction和其余字段。变更均在既有lift scaling前，Range分支、Dock和`controlLens`工厂未改。D是A+B的组合诊断，不能作为单参数因果臂，也不能用A/B分别改善推定组合效果可相加。C为看到A后增加的自适应后续诊断，扩展方案在独立检查C截图前保存，crop、mask、指标和阈值未改。

四轮分别完成构建及同一`NativeSliderOpticalWorldRenderTest#captureNativeSliderOpticalWorld`：A/B/C/D构建约20/19/20/20s，仪器测试38.441/41.313/39.056/48.220s，各1项通过、capture exit0；D保存目录名为`variant-candidate`，不表示生产接受。每轮四背景×明暗8场景，16份REST/HELD控件和8份背景各保存Window/display，共48 PNG及result.json的49份设备/host hash全部一致。冻结源码hash与manifest一致，源码差异及当时安装APK回执支持各轮声明；被后续覆盖的历史APK路径没有重新冒充旧包复核。这是四次采集/构建记录，不是完整主机回归或自然动画验收。

四轮复用72的375×667dp世界、335dp单头、25%值、普通背板、同一固定采样平移`(+.22270203,−.08401489)dp`及合成Compose时钟；没有再配准、缩放源图或另调HELD位置。原生仍是375×667pt世界、AX frame`(20,414,335,31)pt`和实际DOWN`(113.5,430)pt`的72参考。独立复核中，所审source crop相对冻结72的RGB3匹配率均1、RGB MAE均0；carrier四角变化0dp，每份控件120048个readiness样本匹配率1、最大通道差0。它们锁定本轮输入与载体，不能据此把载体当原生alpha轮廓或消除72保留的HELD位置残差。

A/B/C共28份负对照控件crop逐像素不变：A的8份REST，B的8份REST及4份浅色HELD，C的8份REST。D另有8份REST负对照逐像素不变、flag为0，单独报告。三种诊断背景为细网格、彩条和明暗块；风景始终作为仅报告的留出场景，不用于选参数或改mask。以下为固定`lower_inner_haze_transmission_proxy`的HELD原生RGB MAE，单位为保存PNG的0–255码值；该窗口不覆盖完整内芯、rim、端帽或四角。

| 场景/用途 | 冻结72 | A 色散0 | B 深色亮度1 | C rim0 | D 色散0＋亮度1 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 细网格浅色 / 诊断 | 6.875 | 5.500 | 6.875 | 6.875 | 5.500 |
| 细网格深色 / 诊断 | 18.283 | 17.614 | 6.879 | 18.283 | 5.500 |
| 彩条浅色 / 诊断 | 14.852 | 13.451 | 14.852 | 14.852 | 13.451 |
| 彩条深色 / 诊断 | 8.554 | 5.648 | 14.851 | 8.554 | 13.451 |
| 明暗块浅色 / 诊断 | 22.361 | 16.899 | 22.361 | 22.361 | 16.899 |
| 明暗块深色 / 诊断 | 23.838 | 18.452 | 22.360 | 23.838 | 16.899 |
| 风景浅色 / 仅报告留出 | 15.005 | 15.072 | 15.005 | 15.005 | 15.072 |
| 风景深色 / 仅报告留出 | 11.860 | 11.876 | 15.004 | 11.860 | 15.072 |

A支持色散对彩色带的贡献：浅色网格/明暗块下部窗口残余chroma均值由9.534/24.303降至1.015/.091，原生为.845/1.814；四个灰阶源场景中chroma≥24的面积均降为0。但深色网格/明暗块新出现的近白比例由51.89%/33.33%增至57.58%/38.64%，彩条浅/深左端帽MAE由18.546/19.502恶化为20.748/22.021。零色散不是已识别的原生系数，也不是最终材质结论。

B支持深色亮度对泛白/裁切的贡献：深色网格下部近白比例87.12%降为0、新出现近白51.89%降为0；明暗块对应两项33.33%均降为0。彩色瓣和明暗块下部光学段仍在，彩条深色下部窗口MAE反而8.554→14.851。网格深色残余chroma由7.299增至9.523，说明原来的亮度裁切会遮住部分色差，亮度修正不能替代色散诊断。

C支持rim对部分细外缘的贡献，却不支持用删rim修复持续下缘：全部预声明下部内窗、右上内窗、腐蚀内芯及外部shadow ring的HELD像素与72完全相同，明暗块下部白/彩虹段仍在。六个诊断场景的整圈rim MAE有5个恶化，左端帽6个全部恶化。D实际测得若干局部改善，同时保留相反结果：彩条深色下部窗口8.554→13.451、深色网格下直线rim16.768→19.519；风景深色下部11.860→15.072、整圈rim13.641→16.179、左端帽14.600→17.660。不能用一个均值覆盖这些退化，也不能从组合D反推唯一原因。完整逐区域误差和改善/恶化像素比例见本会话artifact的`android-slider-material-73/independent-optical-review/{findings.md,D_dispersion_zero_brightness_one-review.md}`及各轮comparison JSON；固定72胶囊mask只是采样模型，不是原生alpha、曲率或物理色散测量。

另按提取前冻结的两窗口方案，只读取原生72的`tonal_blocks-light` HELD原PNG及其同世界`background-input.png`，不读取Android、深主题、风景或其他背景来拟合。375×667pt世界以2px/pt保存为750×1334px，原点为(0,0)；以下半开world矩形直接对应各12×6px，没有配准、重采样、过滤或修边。四份source/native窗口各72像素均为单一RGB、通道range和标准差为0、alpha255。

| 固定窗口（world pt） | 源RGB | 原生HELD RGB | 原生−源 |
| --- | --- | --- | --- |
| 白区 `(102,439)–(108,442)` | (245,245,240) | (239,239,234) | (−6,−6,−6) |
| 黑区 `(121,439)–(127,442)` | (16,16,24) | (39,39,46) | (+23,+23,+22) |

保存PNG的归一化RGB码值仿射式`y=a*x+b`给出共享斜率`a=.8724412358616319`、`b=(25.1464187201,25.1464187201,24.8377568663)/255`，没有另拟brightness或各通道斜率。仅在brightness=1和`y=(1−alpha)*x+alpha*tint`假设下，对应有效`alpha=.12755876413836809`、`tint=(197.135954475,197.135954475,194.716192447)/255`，无需clamp；六个均值通道残差RMSE约.155212、最大约.223653码值。这是精确选定源图/两个窗口上的in-sample拟合，不是留出验证或Apple内部物理opacity；量化PNG也不赋予这些小数同等测量精度。窗口位置不同，空间光学项可模拟亮度响应，折射/模糊还可能读取窗外，局部恒色不证明有限采样支持或整片均匀film。

`source-review/veil-fit-report.md`建议另行GPU试验：在brightness=1后使用现有`style.tint`，于既有lift scaling前局部供给单头；`uVeil`位于gain前且影响独立hairline采样，不是该建议的运算。默认sRGB Compose `Color`只储存8-bit RGBA，拟合值最近存储为`Color(0x21C5C5C3)`，实际RGB=(197,197,195)/255、alpha=33/255≈.1294117647。该量化表示没有第二次拟合，必须区分精确拟合系数和实际传入值；没有因此更换色空间或shader。方案hash、原PNG/session hash、窗口统计、构造器字节码与复现脚本均在该source-review目录。此拟合本身不作生产选择，也不接受完整材质、端帽、四角、自然时序或其他控件。

E在组合D上只新增`style.tint=Color(0x21C5C5C3)`，保留D的零色散、brightness1及原rim/refraction/blur/hairline等字段，仍在原lift scaling前供给单头。独立E方法在读成功E图前冻结，明暗块浅色明确为拟合场景/in-sample，其余五个诊断场景不参与拟合，风景仍只报告。首次E在`synthetic_landscape-dark` REST的readiness失败：手机系统“已使用40分钟”浮层只出现在composited display，原非系统栏比较域内46251个RGB3差异像素全部在`[401,159,814,278)`，y≥400无差异。14份已完成控件图及失败调试图没有当成完整E；49份导出hash复核后清理手机重复文件，失败截图、分区分析与日志保留在`variant-film-readiness-failure`。没有调整capture阈值、掩膜、等待时限、源码或fixture。

同一E APK完整重采通过（`variant-film`），16份控件和8份背景的48PNG＋result.json共49文件全部hash一致，原readiness各120048样本匹配率1、最大通道差0；8份REST相对72及D均逐像素不变，source crop相同、carrier几何变化0。E构建约21s；首轮失败和后续成功分别保留，不拼为一次无失败运行。固定下部窗口的结果如下，仍为0–255RGB MAE而非alpha或整体材质评分：

| 场景 / 用途 | 冻结72 | E |
| --- | ---: | ---: |
| fine_grid-light / 未参与拟合的诊断 | 6.875 | 2.927 |
| fine_grid-dark / 未参与拟合的诊断 | 18.283 | 2.927 |
| color_stripes-light / 未参与拟合的诊断 | 14.852 | 9.069 |
| color_stripes-dark / 未参与拟合的诊断 | 8.554 | 9.069 |
| tonal_blocks-light / 拟合场景 / in-sample | 22.361 | 1.917 |
| tonal_blocks-dark / 未参与拟合的诊断 | 23.838 | 1.917 |
| synthetic_landscape-light / 仅报告留出 | 15.005 | 3.611 |
| synthetic_landscape-dark / 仅报告留出 | 11.860 | 3.611 |

E相对D在全部五个未参与拟合的诊断场景中，固定腐蚀内芯和整圈rim MAE均下降；这些局部结果支持单头材质的有界改善。腐蚀内芯网格浅/深由25.835/25.605降为21.767/21.501，彩条14.984/14.793降为13.604/13.553，明暗块深32.646降为19.809。拟合场景明暗块浅32.850→19.818单独列为in-sample。局部回退没有抹去：相对D，彩条右上内窗18.046→18.938（两主题），右上rim彩条16.217→16.791/13.943→14.426、明暗块15.234→16.077/11.510→11.628，明暗块下直线rim35.268→35.695/35.242→35.709。相对完整72，彩条浅/深左端帽18.546/19.502→20.735/20.306，网格深下直线rim16.768→19.569也更差；下部窗口彩条深8.554→9.069保留在表中。

风景只作报告：E腐蚀内芯浅/深为4.780/4.872，整圈rim15.217/14.042；深色rim仍比72的13.641差，深色左端帽14.600→14.786也没有改善。这些场景没有用于改参数。E仍缺失原生部分细色边，网格过度变形、端帽映射和持续底部光学段仍明显，不接受完整边缘、四角、自然动态或“99.99%”结论。详表和全crop见`E_neutral_film-comparison.json`、`E_neutral_film-vs-D-comparison.json`及相应contact，不能用两个拟合窗口代替完整验证。

正式实现把E同一材质放入独立`LiquidGlassStyle.sliderLens(dark)`，单头调用该工厂，Range继续原`controlLens`；Dock、Switch、Material Design、shader、尺寸、动态和采样图未改。该次整理不是再次调参。2026-11-14定向JVM过滤`*GlassSlider*Test`、`*LiquidGlassStyleReferenceTest`和`*MaterialSliderAppearanceIsolationTest`，9类48项全部通过，0失败/错误/跳过；debug与androidTest同轮构建成功约38s。它是本次正式材质变更后的48项，不与72的133项或LF后的15项拼接，也不是完整仓库suite。构建日志与9份XML hash见`production-regressions-build.log`和`production-focused-tests.json`。

正式包`variant-production`完整8场景再次通过，16份控件＋8份背景各保存Window/display，共49文件hash核对成功；设备导出校验后清理，包回到debug首页。主APK设备/host SHA256为`9248bcc2f04c6ea30807c1f701684e968e21d7abbc24cceb624ccde3c225fa32`，测试APK仍为72的`794470570f2fcbbee11be8bdb3acbc80d7dae984bc600dad046c66f030c4027e`。`production-source-review`另检查19字段、构造默认值、未改scale及两主题各lift输入，104项源码/来源检查0失败，760次字段比较相同；这些是源码等价性检查，不能替代GPU。

E→正式包在预声明严格48PNG解码像素比较中仅24/48逐像素相同：恰好全部24张Activity Window图。24张composited screen全图都有差异，因此严格全屏条件仍未通过，不能写“48/48保真”。独立定位沿用采集前既有的每场景实际系统栏排除域`[0,135,1216,2592)`，全部24张screen在该域内非零差异像素均为0，16个控件光学ROI也均为0；差异仅位于原先已排除的系统栏区域。网格浅色HELD全屏14715个差异像素、比REST多3493个，额外差异仍全部在该域外。没有重新配准、修改mask或把RGB3放宽成新验收；原screen差异和严格未通过结论均保留在`production-preservation`与`production-screen-delta-localization`报告。光学改善只限于本次单头普通背板稳定状态，不能外推到Range、Dock、嵌套背板、其他值、起落或自然运动；后续仍须逐项完成目标矩阵。


### 74：单头Slider折射幅度的半幅诊断（未采纳）

F从73正式E出发，只把独立`sliderLens`的`refraction=-4.3`改为`-2.15`，原bevel5.5、film、rim、frost、hairline、lift scaling、shader、采样图及Range/Dock/MD不变。保留负号是必要控制：精确0会同时把`lens`布尔分支及rim方向函数切换，不能作为单纯幅度消融。半幅是预先固定的诊断端点，不是原生参数估计。root方案在采集前保存，独立光学方法在读取F像素前冻结；沿用72固定世界、25%值、原registration/mask及合成时钟，不作二次拟合。

首次F在第五场景`fine_grid-dark`的纯背景readiness失败（24.505s）：display中可见系统“已使用55分钟”浮层，Activity Window没有该浮层。原纯背景比较域`[47,190,1169,2450)`内34682个RGB3差异像素，bbox为`[402,190,813,278)`；另在既有control非系统栏域中46126个差异全部位于`[402,159,813,278)`，y≥400无差异。8份已完成控件及失败调试资料共29文件保留，设备/host hash重核对后仅删除设备重复导出。没有把半批结果当完整矩阵，也没有放宽readiness或改mask。

同源码、同APK身份完整重采通过（40.299s，1项instrumentation），四背景×明暗8场景的16份REST/HELD及8份背景各保留Window/display，共49文件hash一致。首次与重采构建分别约20.35/10.12s；诊断主APK SHA256为`43e0c3675ed8620a547e09254095bb7edc1c0d78f1045b4b3a8bc436b0afb48d`。两轮的源码与安装包hash相同。8个REST完整world crop相对正式73逐像素相同，16个source crop相对73及72也相同，carrier角点最大差0，16份control readiness均为120048样本、匹配率1、最大通道差0。这些只支持静态诊断隔离，不是动态验收。

主要固定窗`upper_right_inner_source_sensitive=(125,418)-(130,422)`的HELD直接RGB MAE（0–255）为：网格浅/深25.208333→24.170833；条纹浅/深18.937500→15.662500；明暗块浅/深2.020833不变。own-source-effect MAE分别25.683333→24.662500、18.237500→14.795833及2.020833不变。六个诊断场景84个region cell中，direct有52改善、24不变、8恶化；own-source有51改善、24不变、9恶化，所有17项严格增加（包括极小增加）完整保留。root从原始整屏图及原72 mask独立重算336个MAE，与独立报告差0；没有把区域计数汇总成验收分数。风景只报告，不参与判断或参数选择。

固定坐标完整图确认网格/明暗块的下缘异常内横段减弱，条纹左端蓝色内侵缩小；网格固定左右下端窗却小幅恶化，部分model cap壳改善。主窗及左右端部的原生对应特征坐标仍未识别，缺失细色边、线条剖面、端部和下缘残差仍在，因此不能从RGB误差下降声称位移或完整结构已经对齐。F不自动采纳，正式材质继续73的E。

随后源码/元数据支持分析只给出条件结论：在实际导出的171×111px单位carrier变换、默认effectOffset0/sampleScale1及声明的PIL1px、合成/栅格1px和纹理1px支持范围下，主窗的显式clamp保守余量为正式73的4.251px、F的10.648px，rim-frost不足以形成Vogel扩张。没有GPU uniform、局部矩阵或kernel实测dump，不能推广为无条件GPU排除，也不覆盖端帽/下缘及完整上游采样支持。当前只建立depth/采样敏感性，没有识别唯一原因或原生物理深度。

每次诊断结束均恢复正式源码、host与设备的原主/测试APK并核对hash；主包回到`9248bcc2f04c6ea30807c1f701684e968e21d7abbc24cceb624ccde3c225fa32`，测试包仍为`794470570f2fcbbee11be8bdb3acbc80d7dae984bc600dad046c66f030c4027e`，MainActivity已观察到resumed，临时恢复APK副本已清理。没有新增正式运行时变更，也没有重复宣称JVM全量通过。本会话artifact的`android-slider-refraction-74/independent-review`冻结162份评估文件及11份输入，`review-freeze.json` SHA256为`3e471370db7e90bd57cbcaeea06f6ffd101da9ac4418ec36eddb5c1cc5d61334`，root重核对0失败；原失败、所有退步、条件支持和未验收项均保留。局部光学与自然动态、Range及多层背板矩阵仍需继续，资产库仍为本地索引，未达到独立发布条件。


### 75：单头下缘结构复核后采用半幅折射

74的F当轮未采纳；本轮先复核已有原图中的结构，再作有限生产选择。`android-native-range-75/feature-audit`从原始完整截图精确重建72份诊断crop，未读取风景像素，未另作配准、调色或拟合。固定明暗块配对条带在原生仅于world y447.75–448.25发生强反向；E多出y443.75–444.25的内侧反向段，F消除此段，剩余反向为y447.25–447.75。网格的异常下内横段也减弱。彩条下部最强蓝/品红跳变由E的x92到F的90.5，原生为88.5；该原生跳变紧邻轮廓，不能当成已识别的源采样坐标。主红/黄边仍是E/F的125.5对原生126.0，没有证明主窗坐标改善。

据此将单头`sliderLens.refraction`从−4.3改为−2.15，保留负lens分支及原lift scaling，其余材质字段、shader、采样图、形状和动态不变；Range、Dock、Switch、Material Design均未改。它是减少显著额外下缘结构的渐进修正，不是原生物理深度、完整warp/profile、四角或动态验收。74六诊断场景中的全部17项区域MAE增加继续保留：8项direct、9项own-source，最大分别+.178819444/+.175347222（网格深色左下端帽）；不是把退步全算成噪声。下端帽、细边、点状刻度和部分映射仍有差异。下一项辨别结构的候选是固定水平单阶跃及互补极性扫描，不再依赖平均RGB误差猜深度。

本轮9类48项定向JVM通过，0失败/错误/跳过；过滤仍是`*GlassSlider*Test`、`*LiquidGlassStyleReferenceTest`和`*MaterialSliderAppearanceIsolationTest`，debug与androidTest同轮构建成功37s。正式F在API36设备完成四背景×明暗8场景的静态GPU重采，1项通过约40s，49份文件与设备hash一致；原合成时钟、readiness、坐标和判定未改。主APK设备/host SHA256为`fd89106ce31e539665f468016d34c0d9e2bfd81a1e23ecc9e517dc3882d95117`，测试包仍为`794470570f2fcbbee11be8bdb3acbc80d7dae984bc600dad046c66f030c4027e`，手机恢复debug MainActivity。

正式75与成功诊断74F的48张完整PNG中严格相同24张，即全部24张App Window；24张display在原既定`[0,135,1216,2592)`域内无差异，完整display仍有系统栏差异。因此严格48/48完整PNG条件继续未通过，也不能以相同生产重采结果推成原生等价。独立复核和root逐图重算分别在75的`production-comparison`及`root-production-png-comparison.json`，保留原判定和全部残差。48项JVM、本次1项GPU与此前各轮分开报告，不拼为新全量结果。


### 75补充：公开模型层的释放外冲与双头参考缺口

原生参考新增仅显式启用的`testSwiftUISliderReleaseLayerProbe`，对相同10pt名义excess的慢90/快900正向各重新launch一次，末端保持1.35s，保留1.2s命令后尾部。只在带`-ReferenceSliderLayerProbe`的Slider场景读取公开model geometry及CAAnimation，记录所有UIView节点以保留祖先；不修改原生控件的图层、动画或交互。正常参考采集不扩大遍历范围。

iOS26.5/23F77、375×667pt两会话均完成。构建5.854s、所选测试29.400s通过（整个xcodebuild命令65.081s），9份manifest文件hash/size通过独立核对。日志命名活动及session时间建立请求身份，实际MOVE sample跨度1166.667/100ms；两者末端实际x366.5，相对名义阈值356.25为10.25pt，实际输入不同于请求速度本身。两者稳定held模型/显示position相对最终REST均为+4.353380581pt；快手势在UP后完整getter括号83.103–85.307ms同时出现model与presentation position的+8pt峰值，慢手势不超过held位置，post-UP模型最大+4pt。两者UP后无业务值回调。

镜片及8个已记录UIView背书祖先的animation keys均为空，祖先几何固定；因此新证据支持外冲包含逐帧改变的公开model位置，而非仅由已有presentation变换或bounds收缩解释。没有据此识别私有动画机制、目标、释放速度规则，或排除未记录CALayer节点的动画。遍历均0截断，慢/快capture cost中位数2.018/2.232ms，早期最大29.385/26.010ms；顺序getter和observer成本保留。视频仅作上下文，没有同步视频轮廓验收。本次不更改Android动态规则。原始及独立复核在75的`release-probe`。

另核对双头参考：现有原生参考工程只有单值SwiftUI Slider/UISlider；当前iOS业务`IntRangeSlider`自绘22×22圆形，不能作为用户要求的原生椭圆玻璃对照。旧Android `sliders-cancel-green-3`确有15个已完成range场景，另一个命名为`sliders-collapsed-range-2`的会话实际只有单头，不能凭目录名宣称双头已测。它们均早于当前runtime，不替代当前双头验收。下一项可行性参考是同轨道的两个公开UISlider原生thumb，先证明独立tracking/材质，再测等比缩小；重叠端点的方向路由须单独设计，尚未建立原生双头等价性。完整源位置、trace计数与hash见75的`range-audit`。

### 76：同轨道双 UISlider 的原生参考可行性

新增显式启用的 `range` 场景：两个真实公开 `UISlider` 保留系统 thumb 绘制和 tracking，通过公开 tint 属性清除各自轨道，host 绘制共用轨道并约束端点顺序。这是 host 组合的实验参考，不是 Apple 原生双头 API。常规六项场景选择器不变；尺寸 1 / .75 作用于整只 UISlider，不能描述成只缩放私有 thumb。选端后继续调用该 slider 自身的 `hitTest`，保留真实子视图结果和 nil；只读的 touch superview 链记录归属，不给原生子视图写别名。当前值的公开 `thumbRect` 模型矩形还用于真实 UISlider 的公开 AX frame，不创建代理滑块。

最初构建因误用 UIView 不具备的 AX 属性失败；修正后第二次运行仅 scale1 完成，.75 的理想轨道精确收敛门槛使其在输入前被关闭。两次失败及当时源码保留在 76 的 `range-reference/build-failure`、`calibration-failure`。最终实现用两次 fresh UIKit 端点布局、一次宽度补偿，以第二次实测跨度和精确缩放建立居中轨道，保留期望与实际差值，不猜 anchor。旧 direct-target 输入结果也不作为当前原生 child 路径的证据。

最终采集确认 iOS26.5/23F77、SE3 375×667pt，两次 fresh launch，grid/light、分离 .25/.75 端点。构建4.697s，所选1项测试71.247s通过（整条 xcodebuild77.388s）。四张 host PNG 的完整截图子进程括号均在实际 DOWN→UP 内；DOWN 实际命中 `_UILiquidLensView`，完整公开祖先链各只归属目标 UISlider。scale1 lower/upper 的36/20、scale.75 的34/18个完整截图括号内帧均目标 tracking、peer idle，且缓存模型 thumb 可用。四次 stationary hold 无数值变化，各在 UP 后出现一次同值回调，不能写成“无回调”。根检查和独立 raw 复算分别保存，不以 AX 值或测试通过代替真实触摸归属。

scale1 最终原生 bounds332×34pt，实测轨道295pt；scale.75 原生 bounds430×34pt、整控件精确 .75 变换，实际轨道294.75pt，比期望295pt少.25pt，此残差保留。原生模型 thumb 均为37×24pt，公开模型矩形不等于 pressed presentation 或可见 alpha 轮廓。两会话保留1893/1896帧，遍历0截断；capture cost 中位2.290/2.295ms、最大24.394/24.454ms，顺序 getter 成本不忽略。四张按住原图人工核对可见横向椭圆玻璃，peer 保持白色静止体；未据此接受光学系数或每帧轮廓。

实际慢拖/快拖还保留各自释放尾部：lower 在 UP 后有18次值变化，最大回摆相对 UP 前值为 .004226/.004413，最终回到 UP 前值；upper 在 UP 后有19次值变化，分别从 .864458/.867829 继续到1，净增加 .135542/.132171。两者末尾另有一次同值回调。lower 的公开 endTracking 在 UP 后335.89/335.70ms，upper 在385.98/369.24ms；UIKit 给该回调的 touch 为 nil，不能补造 endTracking 触点。所有真实 delivery 坐标、sample/delivery 时钟与 peer 不变均保留，hostClamped 为 false。这些是本次实际输入下的观察，不能据请求速度、净变化或单一尾部推成任意原生释放规律，也不能写成“UP 立即结束 tracking / 无后续值变化”。

本次只证明分离端点、两档整控件缩放的参考可用。重叠方向路由、cross-over、取消、多背景/深色材质和释放规律仍需单独验证；现有独立 collapsed 测试没有在本轮运行。Android Range 已有共用 timed input 引擎，但材质、独立轴变形和端部策略仍有各自分支，本次没有改生产参数，也不把单头73–75的结论转记为双头验收。完整原图、原始视频、会话、源快照、hash 和独立报告在本会话76的 `range-reference` 与 `range-runtime-audit`。


### 77：双头多背景参考与独立材质修正

2026-11-14 新增八个可单独选择的原生光学测试，每个背景/主题 fresh launch，两个真实 UISlider 整控件缩放 .75、端点 .25/.75，各自实际按住六秒。iOS26.5/23F77、SE3 375×667pt：构建5.464s，8项通过、0失败（XCTest215.726s，整条命令230.128s）。16/16 host PNG 完整截图括号位于真实 DOWN→UP 内，真实子视图命中只归目标 slider，目标 tracking、peer idle、遍历0截断；每张图括号内有16–50个完整采样帧。stationary hold 无值变化，但各次 UP 后有一次同值 callback。独立 raw 审计17项与50份hash核对通过。

原生 backing 是375×667pt，stage/host 为(20,166,335,392)pt，共用rail为(40.125,393.36,294.75,6)pt。首次按住前REST中心分别(113.625,396.36)/(261.375,396.36)pt；HELD为(113.8125,396.36)/(261.1875,396.36)pt，向内 .1875pt 的实际移动保留。REST载体27.75×18、HELD43.0125×27.9pt，这些公开模型/presentation矩形不是可见alpha轮廓。此次非static路径没有新background-input.png，未拿旧背景冒充；只对预先固定的未遮挡局部作有限源场景检查。组合host仍不是Apple原生双头API；.75整控件变换对材质光学长度的影响仍未识别，不把该缩放当作只缩小thumb。

Android新增显式启用的生产Range采集夹具：固定375×667dp世界、holder原点(26.25,372.36)dp/宽322.5dp，保留生产48dp触摸区、8dp轨道、真实pointer处理和两端坐标。使用合成Compose时钟稳定1600ms，只采REST及两端中心HELD，结束用CANCEL而非原生UP；不验收自然运动、释放或finish callback。原≥99.5%/RGB3 Window/display readiness不变。初次产品基线被系统“已使用3小时30分钟”浮层阻挡，原比较域57330个差异像素位于[355,159,861,278]，y≥400无差异；部分结果和失败图独立保存，没有改掩膜、阈值、源码或APK重采。

随后产品基线63.644s、仅primary匹配的诊断61.099s、Range F诊断60.755s、真实产品主题F确认62.369s分别各1项通过。每臂四背景×明暗，24控件状态/8纯背景，共64PNG+result.json，均与设备hash一致、各臂内部source unchanged。初始产品基线fixture与后续带palette导出的fixture版本不同；初始基线没有primary元数据，不将该字段记作跨臂完全相同。fixture默认沿用产品主题；native诊断只换primary，请求(0,.65,.75,1)，实际[0,.6509804,.7490196,1]，不覆盖其他颜色角色。仅换primary时内芯误差变化为−.064372至+.036756，不能解释原有额外彩纹/深色白化；Android/native-host轨道8/6的上下文差异仍保留。

正式修正仅让Range调用独立命名的rangeSliderLens，其暂时复用现有Single F：tint=0x21C5C5C3、refraction=−2.15、dispersion=0、dark brightness=1。它是组合材质候选，不是单参数因果证明、独立Range拟合或原生物理系数。shader、lift scaling、Range等比尺寸/四角、轨道采样图、输入时钟、值/取消/端部规则不变；Single、Dock、Toggle、共享controlLens及Material路由没有改动。

固定world masks（内芯腐蚀2dp、排除rail±5dp，rim/cap另算）保留两端、每个场景和全部区域，不配准、不调色、不重选mask。独立可视复核支持减少显著的彩虹内纹、弯折多条带和深色白化；原生暗上沿、细色边和端帽折射仍明显不同，F更平、更淡。部分内芯RGB MAE（0–255，lower/upper）如下，仅是局部描述：

| 场景 | 同primary旧Range | F |
| --- | ---: | ---: |
| 网格浅色 | 18.018 / 17.391 | 10.272 / 10.014 |
| 网格深色 | 28.364 / 28.809 | 10.270 / 9.992 |
| 彩条浅色 | 16.900 / 14.205 | 10.986 / 12.270 |
| 彩条深色 | 17.844 / 34.747 | 10.991 / 12.261 |
| 明暗块浅色 | 25.503 / 13.014 | 5.620 / 7.509 |
| 明暗块深色 | 27.741 / 25.338 | 5.618 / 7.510 |

同primary六诊断场景108个region cell中92下降、12相同、4上升，计数不是通过分数。四个退步均在彩条浅色：lower rim+1.535813，upper lower-inner+.538371、rim+.988062、right-cap+.558442。风景仅报告，另有浅色lower/upper rim+2.762626/+2.455923、深色lower rim+.109275。真实紫色主题确认同样减少多余结构，四个诊断退步为+1.743343/+.605274/+1.298439/+.558442；风景浅色两端rim+2.842516/+2.576217。全部144项/臂均保留，未以平均误差覆盖这些退步，也没有用风景选参。

同primary及真实产品主题两组各16HELD的carrier/source/rail几何完全保持；各八张REST完整Window与各自基线逐字节、逐像素相同，八张背景Window像素相同。REST完整display仍有系统栏差异，原既定app域[0,135,1216,2592)像素完全相同，不能声称整屏PNG均相同。独立同primary/产品确认分别有824/824项比较检查和80/80项REST检查，均0失败；检查不是原生材质验收。实际Android HELD43.0252×27.89914dp、REST27.89916×18.15127dp及中心取整残差未归一化，轨道实测294.7899×8.06723dp仍不同于原生。

现有9类48项滑杆/材质/Material隔离JVM原测试通过，0失败/错误/跳过（15s）；debug和androidTest构建12s。没有改测试预期、门限或共享lens的6.5色散断言。实际F主包SHA256为b9b9341427961192cc95536a82352bc52c9909fb95fbfd08e90b27032efce9f8，测试包a200d8db5d11e5d5bc74c0b9b24a6b97782322b7faaa50edb35654c5cb6d2325。生产选择是有限改善，未完成全控件/四角/动态/嵌套背板验收。

证据在本会话android-native-range-77：原始参考、失败、四个完整Android臂、冻结方案、逐格误差和独立source/raw/optical复核分别留存。八份原生host trace已无损gzip并验证解压hash，核对后删除模拟器原始重复导出及未用ABI包，本轮已释放2,277,963,375bytes（约2.28GB）；截图、原视频、源码与失败资料保留。runtime仍是Sleepy实际消费的同一库实现，完整控件解耦、独立示例和来源/分发许可gate继续未完成，不作99.99%或独立发布声明。


### 78：资产库继续提取帧调度与输入时钟

在77材质修正后，将ActiveFrameLoop.kt和DockInputClock.kt移动到Sleepy实际依赖的:liquid-glass-runtime，同包保留单一实现。只去掉五处跨module必须的internal，队列/私有状态、first-zero/clamp、唤醒重置、sample/receipt区分、同帧事件顺序、实际withFrameNanos和默认System::nanoTime均逐字节保留。LocalDockInputTimeNanos只定义一次，不复制时钟key。独立source复核确认两份正文除五处可见性外与冻结before及ae30faec相同。

运行库仅增加api Compose runtime1.12.1与implementation coroutines-core1.11.0，与app迁移前及runtime编译/运行实际解析一致；没有增加Compose compiler、UI、shapes或backdrop依赖。四类原测试（ActiveFrameLoop3、DockInputClock4、GlassControlTimedInput16、DockTimedInput10）迁移前后各33项通过，0失败/错误/跳过，测试文件哈希不变。before命令4s，迁移后runtime AAR、debug、androidTest与上述测试同轮34s构建成功；完整XML均在覆盖前分别留存。AAR包含八个调度相关编译类，app自己的production Kotlin输出中此类为0，源码也只有runtime一份。不是把同一文件登记给两个module。

首次准备快照时误猜ActiveFrameLoopTest位于liquidglass子包，Python读取失败；未修改源码，随后按唯一实际路径重新冻结，原Gradle基线仍通过。该准备失败记录保留，不冒充首次全流程无失败。完整source/测试/依赖/归属记录在libraries/liquid-glass/validation/migrations/m2a.json及本会话android-library-motion-78。本次不改任何动画参数、光学或交互规则，也不重复宣称新原生对齐结论。

现有库已实际承载M1三个纯运动文件及M2a两份调度文件，完整driver/几何、材质采样与shader、控件适配器和独立示例继续按extraction-plan提取。Physics下一批必须与两份driver/ParentMotion同迁以保留internal setter，原ParentMotion测试与其两份CSV届时进入标准runtime host test。独立仓库仍待逐控件校准和来源/分发gate完成。

78正式debug及匹配测试包已安装到连接的API36设备，两包设备/host SHA256分别一致，MainActivity resumed且截图确认回到课表首页。主包为961352f0b4cec273b5d16ccf1d6e1795b5902309fd30b6428bcfbf786a9184dd；这次安装包含77的Range材质和78的模块迁移，不能把77旧包hash当成本次安装。

### 80：M2b 首批 Physics 与 motion driver 迁移

本批只将 LiquidGlassPhysics.kt、GlassControlMotion.kt、GlassDockMotion.kt 和 NativeDockParentMotion.kt 移动到 :liquid-glass-runtime 的 androidMain。Physics 和 ParentMotion 正文逐字节保留；两个 driver 仅去掉 11 处跨 module 必需的 internal（九个顶层声明及两个 appliedPressEpoch 只读 getter），没有改数值、事件顺序、输入时间、拟合范围或释放/取消策略。四个 SpringState setter 与 ParentMotion class 继续为 internal。

原 NativeDockParentMotionTest 及其独占的 dock-parent-input-65.csv、dock-parent-reference-65.csv 逐字节移至标准 runtime androidHostTest；保留直接写 velocity 的测试与全部预期，通过正常同 module 测试访问，无 friend-path 或 app 源码重复登记。runtime 接入现有 Compose compiler2.4.21、api UI1.12.1 和 JUnit4.13.2，原 runtime1.12.1/coroutines1.11.0 声明不变。

迁移前冻结 26 份源码/测试/资源/build 输入，原 19 类 124 项测试通过（13.8703s）。迁移后同轮 runtime AAR、app debug、androidTest 构建及 app18类109项、同名 ParentMotion host15项均通过，0失败/错误/跳过（67.3926s）。21份测试/资源哈希不变，38份前后XML的124项测试身份完全保留；源码核对确认五份原样文件逐字节一致，另两份仅可见性变化。AAR 含本批48个运动类、累计63个实现类；app 实际 built_in_kotlinc 生产输出2292类及两个锚点均存在，与全部AAR类无交集。两份独立 compile/runtime 依赖图确认 runtime/UI1.12.1、coroutines1.11.0，compiler图确认2.4.21，与既有版本一致。

初次归属检查误用旧版 app/build/tmp/kotlin-classes/debug 路径，该不完整记录另存且未作为验收；随后检查实际 built_in_kotlinc 输出。一次命令里重复 dependencyInsight 选择器只保留末个 coroutines，原日志保留，再分别解析完整 compile/runtime 图。这两项证据修正没有改源码、预期或重跑构建/测试。完整记录见 [M2b 首批运动迁移](../libraries/liquid-glass/validation/migrations/m2b-motion.json)，原始资料位于本会话 android-library-motion-80。

正式 debug 及匹配测试包已安装到 API36 设备，设备与 host SHA256 各自相同。主包为78e5a7f2dedc64193b00503648ef63afdf5a6ab4bb42ae788bf1de98580bd59f，测试包为a200d8db5d11e5d5bc74c0b9b24a6b97782322b7faaa50edb35654c5cb6d2325；MainActivity resumed，截图确认课表网格含课程和 Dock。此处只验证安装身份与首页启动，没有新增逐帧渲染或光学结论。

本批只收口首个四文件运动闭包。其余 GlassPressMotion、Dock/Segment endpoint、几何、M3 材质/采样/shader、M4 控件适配器和独立示例仍待执行；没有新增原生光学、自然动态或性能验收。publicationReady、nativeCalibrationAccepted、sourceAndLicenseReviewComplete 保持 false，独立仓库和示例仍未创建。

### 79：原生 Dock 局部/远端亮带保留序列

2026-11-14（Asia/Shanghai）完成原生专用 A→B→C→D→C→B→A 保留序列，沿用 SwiftUI TabView/隐藏导航父层、375×667pt/2×、iOS26.5/23F77 SE3 和既有 DerivedData。修正后构建7.0545s，单次所选测试命令45.8969s通过，无超时、retry或repetition；app、runner、XCTest与Swift debug dylib四份manifest在构建/测试间一致。七次请求与七次实际source draw逐阶段对应，保留全部14份原始PNG/AX及截图前后getter括号，预算分别为实际draw结束后至少1s/3s。326/326项离线检查通过、0错误；必需result exports通过，optional build/console log不可用，其错误文件保留。

每张图的前后公开上下文除phase外相同（共28份getter记录），bar/controller/window/canvas四个identity始终不变；Schedule保持选中、scroll为(0,−20)且无tracking/dragging/decelerating。canvas为(0,0,375,667)pt，tab bar为(0,584,375,83)pt；观察到的platter、lens和selection模型矩形/identity稳定，分别为(21,584,333,62)、(25,588,88.25,54)、(25,588,88,54)pt。optional presentation矩形单独保留root-layer基准，不与公开模型或可见alpha轮廓混同。窗口/canvas保持dark(2)，controller继承dark，bar override保持unspecified(0)，未赋值原生材质或几何。

两档1s/3s及每图前后getter的实际公开bar trait均为 **A2→B2→C2→D1→C2→B2→A2**。固定无字形body探针为[196,592,208,596]及[244,592,256,596]pt，每窗192px；所有A/C重复阶段两窗均为192个RGB(37,37,37)，B/D两窗均为192个RGB(247,247,247)，原始精确分布与lossless crops保留。B的公开trait仍为2，最终body像素却亮；trait与渲染像素分别报告，不据其中一个代替另一个，也不据此识别内部material state。

四份750×1334px输入PNG解码后与明确opaque sRGB RGBA源逐字节相符。B/C全图raw histogram相同：825000个(26,26,26,255)、175500个(230,230,230,255)；相同亮带分别位于y=1100..<1334px与400..<634px。两带外649500px均等于A，B/C恰有351000px不同。该单序列中raw histogram相同而B/C最终body像素不同是有限观察；固定合成前景仍在组成场景中，未由此推断全局亮度classifier、采样半径/范围、transfer model、opacity或物理材质系数。1s/3s只是实际draw后的最小观察预算，不是compositor时间、settled state或适应延迟。

首次尝试的fixture未把B传入canvas：请求0/A、1/B而只应用0/A，1001条stage1记录canvas仍A、apply elapsed为−1，120s命令预算结束exit−15。原始源码、trace、日志及无Info.plist的不完整xcresult保存在79/failed-first-run，完整export不可用的错误也保留；该失败不作为14checkpoint比较。经明确授权，仅将immutable stageIndex/input传入既有canvas representable，保留UIView identity、draw callback、原测试/输入/预算及样式规则，再执行上述一次修正验证。最初20项静态检查没有证明SwiftUI运行时update传播。

修正后main.swift冻结SHA256为caeb66e232eca683bf494db33602e8369d87f08cc6bdf32664663783930a84ee，UITest仍为e8970394b68d9ab939b67dc66fb48b7d4c58a8876afd3c8ae07552c62b4288ca。原图、root联系图、完整修正结果包、必需exports、source/binary/runtime回执与326项报告在本会话android-dock-local-appearance-79。此批只有原生夹具和证据文档变更，没有Android材质改动、拟合、preferred-style重试或新增native acceptance/parity；80的模块迁移结论与gate不变。

### 81：Android 保留 Dock 局部背景基线

新增显式启用的 captureRetainedDockLocalAppearance，使用未改的生产 Dock、GlassHost 和 GlassBackground；旧 captureNativeDockOpticalWorld 方法正文逐字节不变。一次保留组合接收与79相同的 A→B→C→D→C→B→A 输入，selected index0、固定dark与背景key、无Dock输入；只用stageID促发新source draw，不重建Dock。位图按实际密度独立取整边界，整数source/destination同尺寸、FilterQuality.None、DENSITY_NONE；B/C实际raw histogram相同。各阶段确认首次Compose source draw后，以随后HWUI commit返回为保守实时间锚，分别至少1s/3s请求首个完整Window/display对；无合成时钟、readiness重选或材质偏好。15项独立source检查通过，生产材质和参数未改。

androidTest构建9.8286s通过；主包仍为80的78e5a7f2dedc64193b00503648ef63afdf5a6ab4bb42ae788bf1de98580bd59f，仅安装新测试包2792dc6c8d43bdf32fab4851bdb546a9a6f22aebc017eb4f1a7fec5d1dc6a455，两包设备hash各自匹配host。首轮29.2566s保留14对图后在原完整域readiness汇总断言失败，ADB exit0不当作JUnit通过。3项为stage2 C/3s和stage3 D/1s、3s；可见系统“已使用4小时30分钟”提醒。各失败差异均为57,449个RGB>3像素，bbox[354,159,861,278)，既定5px采样bad2316/120048，matched=.9807077169；实际Dock holder[0,1702,1116,1958)内bad0、max0。原RGB3/99.5%门槛与比较域不变，局部一致不豁免全域失败。

提醒消失后，仅换全新输出ID local-baseline81-confirmation，以相同source/APKs/阈值执行一次完整序列，28.7421s单项通过、无超时或retry flag。两轮各33文件均与设备SHA256逐一相同。离线独立原像素审计首轮456项通过/3失败/0读取错误，确认轮459/459项通过、14/14完整域readiness；这里的检查数不是视觉通过分数。实际source draw、各14预算、28组前后状态、保留身份与geometry均复核；两轮不拼接。

实际Android密度2.9750001px/dp，source1116×1984px、逻辑375.12604×666.89075dp；base world LTRB[20.840336,584.10077,354.2857,646.1008]dp，首lens[24.840336,588.1008,113.09033,642.1008]dp。固定两body探针[196,592,208,596]、[244,592,256,596]均为每窗432个Android像素：A/C全部RGB(39,38,42)，B/D全部(117,115,120)，两轮Window/display一致。79原生每窗192像素对应A/C(37,37,37)、B/D(247,247,247)。根查看完整B及原生联系图，亮带下Android仍灰体/浅色未选字形，原生可见亮体/黑色未选字形；此处明确保留外观差距，不将capture readiness当作材质验收。

双方密度/取整、Android Material图标与原生SF Symbols/scroll前景、Android较大采样host中的世界外#F0F0F5均不同；没有缩放、配准、拟合或逐像素跨平台评分。body分布、公开bar trait、直接source draw与capture时钟分别记录，不据此推断内部material state、classifier、采样范围、opacity或适应延迟。REST与两预算不能覆盖动态、release、四角和完整光学验收。冻结source SHA256 fa23de029583d931d528d5b85189c917603cd856cdea1e465677329708c1627f、首轮失败、确认轮、逐文件导出回执和完整脚本/分布/限制均在本会话android-dock-local-baseline-81；资产库和发布gate不变。

### 82：M2b 按压与 Dock/分段端部迁入资产库

将 GlassPressMotion.kt、NativeDockEndpoint.kt、NativeSegmentedEndpoint.kt 移入现有 runtime androidMain，每份仅移除一次顶层 internal；函数正文、弹簧参数、short-tap重定向、各控件独立端部策略、包名与三个生产调用者保持。SpringState四个setter仍internal，既有runtime源和所有构建配置不变，不加依赖或friend-path。两份Endpoint原测试逐字节迁入标准androidHostTest，GlassPressMotionTest因调用app的glassPressedContentAlpha而完整留app；不把该helper反向拉入库。

原三类17项基线在移动前3.5265s通过；移动后同轮runtime AAR、app debug/androidTest构建及同名17项测试29.3098s通过，after分为app Press3与runtime Endpoint14，0失败/错误/跳过。三份原测试字节和17项完整测试身份不变。runtime累计12份生产Kotlin源，AAR66个类与app实际built_in_kotlinc生产输出2289个类无交集；本批三类的生产声明均只有runtime一份。现有CI/nightly/release及PR模板已经运行app+runtime host，故本批不新增CI任务。

正式debug及匹配测试包安装到API36设备，两包host/device SHA256分别一致；当前主包948441072ed34b301d7ab4690f34973b4920c43cc19f3637f340970c8bcec45f。MainActivity resumed，保留的首张启动图显示现有空课表卡片和Dock；仅验安装身份与启动，不宣称课程数据填充、光学、自然运动或性能通过。原源码/测试冻结、命令、XML、精确可见性diff、唯一归属和安装回执在本会话android-library-endpoints-82，便携记录见 [M2b按压/端部迁移](../libraries/liquid-glass/validation/migrations/m2b-endpoints.json)。

本批不修改任何控件材质或已测运动行为。几何、采样/材质/shader、完整控件适配器及独立示例仍未提取；81记录的亮背景Dock差距和既有动态/四角残差继续保留。独立仓库待逐控件近原生、可运行示例与来源/分发gate完成后创建，三个发布gate继续为false。


### 85：原生 Dock 左右等面积亮块响应

沿用79修正后的保留TabView/scroll/canvas上下文，新入口只切换A→L→R→B→R→L→A。L/R亮块为[125,550,225,667)及[225,550,325,667)pt，各46800个亮像素；B为原底部全宽亮带175500个亮像素。四份原始750×1334 opaque sRGB输入解码hash与运行前预声明逐字节一致，保留固定前景；默认79序列不变。iOS26.5/23F77 SE3一次构建10.3624s、一次所选测试54.5830s通过，无retry/repetition/超时，沿用既有DerivedData；四份binary manifest及source hash在build/test间相同。全部14原PNG/AX、source draw/getter/capture括号和完整必需exports保留；optional build/console log不可用的原错误保留。

326/326离线artifact检查通过，四个主identity及观察到的Dock/scroll模型身份与几何保持、Schedule index0、scroll(0,-20)；前后publicState除phase外相同。bar有效trait全程为2。原两个固定body探针每窗192px，1s/3s均为中性RGB：A37/37、L182/55、R55/182、B247/247，回程R/L/A各自重复。根查看七张原生Dock上下文，L/R的亮暗分区及A/L/R浅色、B深色未选字形可见。Today/Mine预声明原生上下文裁片包含body与抗锯齿，不作为隔离后的字形颜色。 七个阶段各自的1s/3s完整原图及初末A逐像素相同；回程L/R完整原图仍分别有45,592/49,508像素差异，bbox均为[0,1055,750,1334]px，最大通道差42。两个body探针重复不代表整图重复，也不识别hysteresis或settled状态。

该结果证明这两个位置的空间响应不同，不能用应用明暗主题或整屏直方图代替；不识别内部classifier、材质状态、采样范围/blur半径、opacity、transfer函数或适应延迟。观察预算不等于compositor/settled时间。原图与完整326项报告位于本会话android-dock-spatial-response-85；[便携记录](../libraries/liquid-glass/validation/observations/dock-horizontal-response-85.json)保留输入与原图hash和精确分布。本批没有Android生产材质或foreground变更，81的亮背景差距继续保留，不能把采集验证当作原生验收。

86只读代码调查另发现：当前深色pill的主题tint(54,52,59)、alpha.48和contrast−.4，在opaque均匀源26/230、原五点behind与远离rim/glow等条件下，现有AGSL公式舍入得到39/38/42与117/115/120，吻合81实测。它说明材质公式已足以解释偏暗，不需假设源未更新；这仍是有条件静态推导，没有GPU中间uniform dump。生产compact Dock从MainTabs的LayerBackdrop取源，81夹具则用GlassHost深度前缀，两者不能混称。未选前景是固定sibling tint，不能假称当前body shader会自动改变文字。后续修正需保持Dock私有边界与多背景验证，不凭这四个灰度输入拟定阈值。


### 87：圆角、精确轮廓与缓存进入运行库

先冻结六几何源、Shader、原测试、AGSL及构建输入，原五类26项测试14.0007s全部通过。随后将Superellipse、Geometry、ExactContour、DualContourShape、FlClashGlassShape和DockPlatterShape物理移入runtime，并把Shader中的583字节CachedOutlineProfile块原样抽出到LiquidGlassOutlineProfile.kt；只删除跨module必需的28次internal，公式、FMA分支、Float/Double运算、路径精度、lazy及两处identity判断不变。Shader块外字节不变，app原测试/调用者/AGSL不动，SpringState四个setter仍internal。

runtime只新增既有shapes1.2.1 implementation。同轮AAR/debug/androidTest构建及相同五类26项测试43.9488s通过，全部测试字节与完整身份保持、0失败/错误/跳过。runtime累计19份生产Kotlin源、AAR102类，app实际built_in_kotlinc生产输出2253类，无交集；移动源在app原处不存在，AAR持有全部实现及CachedOutlineProfile。compile/runtime真实依赖图均解析shapes1.2.1和ui-graphics1.12.1，无未解析依赖；不增加CI作业或host图形测试接线。

API36安装main/test的host/device哈希分别匹配；main为aeb5060ffddce0e17a3f93fb77ecf05c6ad556c4901a70235ae8b14afb86f9a0。原设备DockPlatterContour、DockPlatterOpticalCache与ExactContourPath三类8项1.2862s通过，覆盖原路径命令、方向/密度/尺寸、cache身份及失效。首次root命令误用未注册默认AndroidJUnitRunner，在任何测试启动前失败；原log/json保留，核对Gradle和设备注册后改用既有SleepyRenderTestRunner执行，未改测试或预期。最终MainActivity resumed，根查看实际启动后的第11周课程网格与Dock；即刻resume的splash帧单独保留，本批未写数据库，不据此宣称课程数据或光学验收。

[便携迁移记录](../libraries/liquid-glass/validation/migrations/m2-geometry.json)保留命令、hash及边界；本会话android-library-geometry-87保留完整before/after、归属、依赖、设备和失败命令证据。删除实际生成而未使用的两个ABI安装包，释放60,122,842字节，保留实际arm64/test包及唯一参考/失败资料。几何迁移不校正任何现有光学或运动误差；完整材质/采样/shader、控件适配器、独立示例及发布条件仍待完成，三个发布gate保持false。

### 89：渲染原语及 AGSL 进入运行库

将 Style、Shader、Effect 三份源码及原33,561字节AGSL物理移入runtime，包名和调用者保持；三源仅删除跨module必需的六处internal，其余字节一致。标准androidMain/assets打包原shader，runtime显式启用Android资源；新增依赖均为Sleepy已用版本，backdrop和Material3 ColorScheme因公开签名而用api，foundation及HCT为implementation。Material3这里只提供调色类型，不迁Material控件或路由。runtime现有22份生产Kotlin源；Host、主题上下文和完整控件尚在app。

七类原app单测迁移前34/34在11.8416s通过；迁移后AAR/debug/androidTest构建及同34项在50.9080s通过，测试字节及完整身份保持，0失败/错误/跳过。AAR有113类，app实际生产输出2242类，无重复；AAR和APK各含唯一且原字节相同的shader。compile/runtime实际解析分别67/121个组件，无未解析项；实际compileAndroidMain输入含backdrop编译jar。标准依赖文本的literal断言及直接configuration.files变体解析两项只读诊断失败原样保留，随后以resolutionResult和编译任务libraries核对，未修改生产输入或重跑通过的构建/测试。

API36原HazeRender三项以已注册SleepyRenderTestRunner在5.3395s通过，初始主包3b4c85aa236a6e7a3241d43ed11810893b51c0b49ba6b3715bcc3e06b366f30e和测试包dca9e55ac4a868be16befe1aa55e529d5aeebbc1cd5639c61b943a56e3f4689a的host/device哈希分别匹配。之后只纠正ATTRIBUTIONS中shader、Style及前批Superellipse的三条依据路径，并用原generator重生成ACKNOWLEDGEMENTS；署名、来源及许可字段均保持。仅assembleDebug重打包2.4462s，未重复原单测/GPU测试；最终主包448a5b67bbc970b96485bd0d7f0a7c7c3bb8f010058e737f4237bf419eddf190已重装且设备哈希一致，AAR和shader不变。根查看最终截图，MainActivity resumed且可见第11周有课程网格和Dock；不新增数据、光学或运动结论。

[M3a便携记录](../libraries/liquid-glass/validation/migrations/m3a-rendering.json)保留39份证据哈希、原测试身份和两次主包身份。完整源码快照、原失败诊断、XML、日志及截图在本会话android-library-render-primitives-89。删除实际未用的三个ABI主包，释放90,324,317字节；保留实际arm64/test包和唯一资料。此批只是同一实现的库提取，81/85的Dock残差及既有动态误差均未解决；Host/控件/独立示例、来源及分发核对继续待办，三个发布gate保持false。独立公开仓库仍按用户要求留待近原生校准后创建。

### 90：Dock 底座负 contrast 的单变量诊断

按86预声明方案，仅临时把Dock底座调用的pill style覆盖为contrast=0，其余8份冻结输入、原测试、原AGSL、几何及取源保持。仍是81的GlassHost保留A→B→C→D→C→B→A，不改生产MainTabs的LayerBackdrop或前景；既有fixture内“unchanged production”旧文案不能代替本次明确的ablation范围。仅构建app debug一次9.1958s，复用未改测试包，所选单项instrumentation28.9513s通过，无retry/repetition；原14组完整Window/display均保留。33份导出逐文件host/device哈希相同，原81离线核验器逐字节复用，459项核对通过、0失败/读取错误，14/14原完整域readiness通过。这些是采集及完整性检查，不是视觉验收。

两处原定无字形探针各432px，在Window/display、1s/3s和回程都分别为：A/C保持RGB(39,38,42)，B/D变为(146,145,148)。相对81原亮区(117,115,120)增加(29,30,28)，符合先验有条件公式；说明已实现负contrast确实贡献了亮区压暗，不需用丢失source来解释这些探针。仍未dump中间uniform或识别唯一完整机制；81是提取前历史包，本次只保证相对当前HEAD的单调用变化，未额外重跑一份同build基线。原生B/D探针仍为(247,247,247)，关contrast远不足以修复；未选字形仍是独立固定tint，本次没有校准前景、彩色/纹理背景、动态或生产取源差异。

诊断后所有冻结源码逐字节恢复，正式主包448a5b67bbc970b96485bd0d7f0a7c7c3bb8f010058e737f4237bf419eddf190及原测试包重新核对host/device身份，MainActivity resumed，根查看恢复截图为第11周有课网格与Dock；诊断参数未留在app或手机。删除三个实际不用ABI包90,283,952字节及临时主包备份30,078,637字节，保留原PNG和唯一证据。[便携诊断记录](../libraries/liquid-glass/validation/observations/dock-contrast-diagnostic-90.json)保留先验、精确分布、50份证据哈希和范围；原始资料在本会话android-dock-contrast-diagnostic-90。本批不提交材质候选，也不改变原生校准或发布gate。
