# Android 玻璃控件与 iOS 26.5 原生对照

2026-11-12 使用 iPhone SE 3 模拟器、iOS 26.5（23F77）、375 × 667 pt、2×，观察系统 SwiftUI / UIKit 控件。Android 按 1 pt = 1 dp 对照控件局部尺寸，不拉伸整张屏幕截图。参考程序见 `tools/native-glass-reference`；Android 设备截图与真实触摸回放入口为 `NativeGlassParityCaptureTest`。

本次实机是 OPPO PKX110，Android 16 / API 36。安装包为 `com.lingion.sleepy.debug`，与正式版数据隔离。下面区分原生测量、实现和仍存在的差异；通过测试不等于每帧像素相同。

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

原生菜单、alert、sheet 已保留视频参考。Sheet 主体材质本轮已按四背景 × 明暗主题完成独立实际 GPU 对照；8 个 ROI 平均 RGB RMSE 从 21–135/255 降至 0.23–1.38/255。同坐标彩条完整横向 profile 的 RMSE：浅色 32.01→3.58、暗色 112.23→2.40；before/after 背景 ROI 每像素一致。参数是经验拟合，不是 Apple 常量，主体接近也不能代替浮动边距、四角轮廓和边缘高光的验收。Menu 和 alert 尚未完成同等数值对齐。例如参考 alert 平坦区域约 235–236，menu 约 244–247，弹出后外背景约为原来的 .8；当前 Android alert 与 menu 主要仅模糊半径不同。深色外观、系统字体、不同系统缩放与圆按钮实际可见边界也未取得完整同条件对照。API 33 以下没有同等 RuntimeShader 折射能力，降级效果不作为 1:1 证据。

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

所有控件的点击、按住、拖动、快慢变化、反向、取消和连续切换由本会话自行验证，不转交用户。仅课表课程长按拖拽等需要 Xcode 测试的复杂业务流程保留此前另约定的验收范围。自然控件回放入口 `NativeControlMotionCaptureTest` 不推进测试时钟，记录实际输入派发、帧读取区间、HWUI commit 和回调；它们不能被当成显示器 presentation 时间或独立视频像素测量。

2026-11-13 真机自然交互三轮全部通过：Dock3/Dock4（58.025s）、Toggle/分段（52.497s）、Slider/Range（49.686s）。每轮覆盖 direct/hold/drag/cancel/rapid，完整输入和回调存于 `native-control-motion`。这些序列验证输入行为与连续运动，光学/几何误差仍另行报告，不以测试通过代替视觉等价。五语言自然 GPU 回归另已通过（3.248s），同一 GPU 回归切回旧裁切路径真实失败（3.123s、第二行 delta=0）；恢复修复版后再次通过（3.196s）。


2026-11-13 自然尺寸 Dock 的点击形变另用 11 组、1000 个实际原生帧拟合，训练 5 组、留出 6 组，保留输入时间与最后静止 presentation 的观察起点。新的 `NativeDockDeformation` 使用有界位移、真实 lift 下降速度和连续的物理状态；没有固定等待。四项留出组水平 scale RMSE 为 .00569–.00783、最大残差 .02811，垂直 RMSE .00944–.01160、最大 .03187；三项留出组水平 RMSE 约 .00326、垂直约 .0072–.0073。长按训练样本的局部垂直残差仍约 .0445，不能视为逐帧等价。完整 1.0–1.5 秒尾段、首峰与反弹分别回归。直接/拖动模式交接只衰减两种垂直读出间的残差，同时保留 X/Y 及速度，纯拖动方程保持原样。

自然尺寸 Android 真机再次确认三项/四项可见底座约 274/309 dp、镜片 94 × 54 dp；Tab 相对底座中心与原生最大差约 .2 dp。透明 wrapper 的 86 dp 与原生 83 pt 不作为可见底座高度误差。新的定向 JVM 55 项（含模式切换、完整尾段、单双头交互及光学系数隔离）失败/错误/跳过均 0，debug 与 test APK 构建成功。随后普通控件的实际输入仍逐轮记录，不以这些 JVM 结果代替 GPU 动态验证。

双头范围两端重合时，旧距离比较总选终点，导致向低值方向点击轨道无法展开。现在按点选方向确定端点；LTR/RTL 两个回归在旧实现真实失败，修复后通过。仅 Liquid Glass 输入分支改变，Material Design 不受影响。

Sheet 圆角现通过 `CornerBasedShape` 提供真实 36/20 dp 半径，保留原 superellipse 轮廓；独立 Sheet rim 移除菜单的 CPU 黑色发丝线。四组实际 GPU 对照中主体与背景 ROI 逐像素一致，不透明核心 mask 一致，抗锯齿边缘会随黑线移除而变化。浅色左侧 .5 dp 的暗槽由约 179.4 恢复为 231.7；四边完整 profile 误差降低，但侧边亮峰仍弱。后续 Sheet-only 方向高光候选继续单独验证，不覆盖 menu、alert 或 Material Design。

2026-11-13 后续集中构建的当前记录为 358 项 JVM，失败、错误、跳过均为 0，debug/test APK 同轮构建成功（51s）。该轮包括 Dock 新按压首帧、滑杆取消与重合端点、Sheet 独立方向高光和菜单 GPU fixture；没有把后续尚未实施的弹窗布局列入覆盖范围。

单双头滑杆的新实际输入回放通过（55.981s）：34 次手势、325 次数值回调，单头 13 次正常松手各完成一次，两个控件各有一次取消。旧单头实现确实在 `ACTION_CANCEL` 后误发完成回调；修复后取消保留已发布的数值、不发 finish，后续点击正常。范围滑杆没有 finish API，且其后续重合端点用例主动重置数值，不能把该用例写成无重置的取消恢复证明。快速点选六次均实际改变同端数值，记录实际 DOWN 间隔和保持时间。

自然尺寸 Dock 最新实际回放通过（66.679s），32 场景的回调计数正确。新按压首个观察帧统一零步长，避免暖帧把按下前的时间用于新目标；不能据此宣称解决全部调度相位。四项反向/正向按住/反向按住的中心最大残差从约 22.54/27.70/23.66 pt 降至 11.73/16.57/12.57 pt，仍未对齐。三项反向有一次回调的 vsync 时间早于实际 DOWN、执行晚于 DOWN，下一帧仍包含跨输入时间，最大残差约 7.84 pt。宽高局部最大残差仍约 2.09–5.71 dp。实际四项点击保持约 67.5ms，冷三项约 79.9ms，快速切换则约 45.6–59.7ms，不能泛称全部输入保持 67ms。原始视频、实际触点和逐帧时间保留，不拟合隐藏时间平移。

Sheet 方向高光当前使用独立经验下限，非 Sheet 的方向系数有隔离回归。四背景 × 明暗 8 个 rest 分两批实际通过（47.487s、57.333s），彩条背景下五控件 × 明暗 10 个 held 实际通过（110.295s）。正文 ROI、背景和不透明核心相对前轮逐像素不变。浅色中性背景四边 profile RMSE 为 2.00/6.10/3.99/4.89；深色边缘仍约 8–10 的残差。明暗色块和风景在两平台边缘的底图相位不总一致，已同时保存无面板输入 profile；不能把背景位置差全算作材质误差。详见会话 artifact 的 `sheet-comparison-rim-4/eight-rest`。

菜单与 Alert 的当前实际 GPU 矩阵为四背景 × 明暗 × 两载体的 16 个 rest（76.078s）。每场均检查真实窗口材质覆盖、文字像素及公开动作关闭/单次回调，避免只有语义节点却截图全透明。旧透明预检原图和失败日志保留；补齐 Popup 首帧与静态时钟时序后预检通过（9.479s），据此不能声称修了产品关闭逻辑。它们的尺寸、内容排版和材质仍与原生有差异：菜单约 250×160dp 对 250×146pt，Alert 约 320×201dp 对 320×172pt。原生 Alert 另有 8 项真实静态系统测试通过，只作为 rest 和动作参考，不证明 held 或打开/关闭动画已匹配。
