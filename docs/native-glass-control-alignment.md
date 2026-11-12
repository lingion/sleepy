# Android 玻璃控件与 iOS 26.5 原生对照

2026-11-12 使用 iPhone SE 3 模拟器、iOS 26.5（23F77）、375 × 667 pt、2×，观察系统 SwiftUI / UIKit 控件。Android 按 1 pt = 1 dp 对照控件局部尺寸，不拉伸整张屏幕截图。参考程序见 `tools/native-glass-reference`；Android 设备截图与真实触摸回放入口为 `NativeGlassParityCaptureTest`。

本次实机是 OPPO PKX110，Android 16 / API 36。安装包为 `com.lingion.sleepy.debug`，与正式版数据隔离。下面区分原生测量、实现和仍存在的差异；通过测试不等于每帧像素相同。

后续用户明确将软件内滑杆外观改为所附 Device Hub 样式：圆形白色滑块、圆角轨道、下方点状刻度；单头正常大小，双头更小以便精细选择。这项明确要求优先于前面的 iOS 椭圆 Slider 外形；下表的原生 Slider 数据仅保留为测量依据。

统一后的单头直径 32 dp、双头 24 dp，白色圆形和轻阴影；轨道 8 dp，刻度点 2 dp、位于轨道中心下方 14 dp。默认交互高度 48 dp。Material Design 与 LiquidGlass 使用相同外观度量，各自保留输入引擎；玻璃按住时等比升起，不再绘制椭圆形变。色相条保留彩虹含义，原值标记使用对应大小的圆形。刻度按业务档位绘制，过密时以最少 12 dp 间距抽样展示，不改变吸附档数。

Slider 的原生轨迹另证明：视觉镜片中心行程为 `width − 37`，拖值增量却按完整控件宽度计算，首次起拖消耗实测 10 pt。335 pt 的正向与反向共 123 个触点/value 样本可由该映射复现，误差低于 3e−8；该阈值不能认定为 UIKit 内部 touchSlop。新的圆形玻璃 Slider 保留此已验证的相对拖值映射，视觉 inset 改为当前圆滑块半径。原生快反向抬手后的额外 value 漂移机制未证明，未模拟。

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

分段控件每个座位占完整轨道的 1/N，每片左右各内缩 2 dp。此前将整条轨道先扣掉 8 dp 再分段，会使首尾滑片中心偏移，并使静止片宽度错误。玻璃交互高度为 48 dp，真实可见轨道为 32 dp；经典 Material 布局保留原尺寸和点击逻辑。

32 dp 是普通字号下的最小轨道高度。较大的系统字号或多行 badge 按实际文本测量扩展轨道和镜片，并保留调用者更大的交互高度，避免用固定原生尺寸裁掉文字。Dock 绘制、裁切、折射与语义边界统一映射 RTL 物理位置；系统取消触摸回到受控选项，不提交页面切换，普通纵向移出后松手仍按实测提交。

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

底部栏存在两份不同布局的原生记录：底座 274 × 62 / 片 94 × 54，以及本次底座 274 × 49 / 片 94 × 41；均增加局部镜片 16 × 16。比较时必须去除父底座缩放，并使用同一布局。Android 暂保留产品已有 62 dp 底座，不把不同布局的屏幕尺寸混作同一基准。

## 速度、停住和反向

仅按速度绝对值拉长镜片不符合原生：约 1000 pt/s 的匀速中段多数接近 1×，最明显的形变出现在手指已经停住、仍按住时。快速正向停止后 x 约 +15%、y 约 −18% 至 −21%；反向停止后 x 约 −17%、y 约 +17%。约 350 ms 后有反向回弹。

`NativeGlassDragDeformation` 使用位置 follower、加速度 follower、形变弹簧三段连续状态，接受有方向的物理 dp。x/y 分别参与绘制，y 仍由有符号比例近似，并非两套独立物理状态。输入方向包含 RTL 的实际移动方向，不能只用递增的逻辑索引。按住开始新的形变，松手保留物理尾部；无交互的外部选项更新不激发形变。关闭系统动画时所有状态直接落定，不保留空转帧循环。

`app/src/test/resources/native-glass/drag-matrix.csv` 保留原生两次重复、双向、100/900 pt/s 的 16 次真实触摸与帧记录，共 2983 行。回归按原始时序回放，含停止后按住与松手阶段，不用模型生成的轨迹当参考。

当前快速拖动水平比例 RMSE：Segment 约 0.55–0.63%，Dock 约 0.70–1.03%。部分首峰仍提前约 17 ms，中段也有约 1–3% 残差。直接 B 点击首峰的方向和量级接近，但 Dock 正向横向回弹可能比原生大约 41%（原生 −.060、模型 −.085）。这些误差尚未消除，不能声称所有速度下逐帧一致。

## 透明度与光学

Switch 的实心滑片在升镜时连续淡出，回落时连续恢复。轨道选中色按实际滑片位置渐变，升镜、横移和填色使用同一帧状态。原生视频支持该渐变趋势，但不能从图层 opacity=1 推出内部材质的不透明度。

原生普通按钮按住时前景对比度明显降低，prominent 降低较少。Android 仅对按钮前景拟合到 .5 / .8，并跟随 lift 连续变化；玻璃底面和大面积 surface 内容不跟随淡化。这是可见对比度拟合，不是对 Apple opacity 常量或完整 vibrancy 曲线的还原。

玻璃底栏的 clear / haze 以前实际指向同一采样内容，宣称的 haze 没有独立模糊。现在用两个独立 GPU 分支和独立 retained input layer：清晰与高斯模糊内容打包到不重叠的槽，再由光学着色器读取。生产路径和窗口原点均有真实 API 36 GPU 回归。测试图生产高频指标约 20.011 → 5.672，平均亮度 131.099 → 130.428；无白边、错区或背景变白。无 haze 时释放额外层并保留原路径，无 CPU Bitmap 模糊。

选中背景、文字和徽标继续复用当前 Material 配色：分段和浮动底栏共用 secondaryContainer、onSecondaryContainer、onSurfaceVariant、primary；Switch 保留调用方 Material 颜色。透明材质仍与背景合成。

原生菜单、alert、sheet 已保留视频参考，但尚未完成光学数值对齐。例如参考 alert 平坦区域约 235–236，menu 约 244–247，弹出后外背景约为原来的 .8；当前 Android alert 与 menu 主要仅模糊半径不同。深色外观、系统字体、不同系统缩放与圆按钮实际可见边界也未取得完整同条件对照。API 33 以下没有同等 RuntimeShader 折射能力，降级效果不作为 1:1 证据。

浅色玻璃 alert/dialog/日期弹窗及默认 sheet 的外围遮罩按 RGB 240 → 192 拟合为黑色 20%。Dialog 退出或切换外观时恢复原窗口 dim；sheet 的调用者显式颜色优先。菜单无模态遮罩。该调整只改变周围背景，不代表 alert 主体的 tint、blur 或 vibrancy 已对齐；深色与 Material Design 默认遮罩保留。

## 验证与复现

本次最终定向 JVM 回归 322 项，失败 / 错误 / 跳过均为 0；包括真实拖动数据拟合、B 点选/按住、A 按住、快速 B→A、受控值拒绝、RTL、动画缩放、取消、多指和页面手势隔离、设置卡片溢出、Slider 和按钮起落。`:app:assembleDebug` 与 `:app:assembleDebugAndroidTest` 成功。

API 36 的 `LiquidGlassHazeRenderTest` 三项通过，包含独立 atlas、生产路径和窗口原点。`captureReferenceMotion` 在真实 GPU 上完成 Switch / Segment / Dock / Slider 的网格背景逐阶段截图与几何 CSV；合成 Compose 时钟和实际截图耗时分别记录。`replayNativeGesture` 使用真实 Android Choreographer 与公开输入接口重放 iOS 实际触点时间，不用测试时钟冒充真实速度；输入派发阻塞和时间偏差单独导出。

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


统一滑杆截图：

```sh
adb shell am instrument -w \
  -e class com.lingion.sleepy.ui.component.UnifiedSliderAppearanceCaptureTest \
  -e sliderCapture true -e sliderRunId unified-slider-20261112 \
  com.lingion.sleepy.debug.test/com.lingion.sleepy.SleepyRenderTestRunner
```

2026-11-12 在 OPPO PKX110 上实际执行通过（1 项），导出八张图片，几何失败 0；已逐图检查圆形、大小差异、轨道、刻度点和原值标记。该入口默认跳过，显式运行时在真实 API 36 GPU 上导出 Material Design / LiquidGlass × 浅色 / 深色 × 初值 / 改值共八张图，并记录圆滑块、轨道、点状刻度和原值标记的实际语义尺寸。HWUI 提交帧后用 PixelCopy 取窗口；输出位于 debug app external files 的 `unified-slider-appearance/<sliderRunId>`。这些手工截图不加入日常 CI。


新的 Device Hub 圆形玻璃 Slider 另以 `captureReferenceMotion`、`glassCapture=true`、`glassBackgrounds=grid`、`glassControls=slider` 实际截图通过（1 项），共 36 个阶段帧。静止约 31.93 × 31.93 dp，稳定按住约 49.75 × 49.75 dp（设备像素取整），升降各阶段始终等比圆形；白色层在光学场景之上按 `1−lift` 淡出 / 恢复，不会在启用 shader 的第一帧被不透明采样突然覆盖。该截图使用测试时钟逐阶段推进，不能据此声称真实速度或 iOS 每帧光学完全一致。
