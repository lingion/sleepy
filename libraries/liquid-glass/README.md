# Liquid Glass 资产库

本目录是 Android/Compose Liquid Glass 运行模块和资产索引。当前 `:liquid-glass-runtime` 有 26 份生产 Kotlin 源码及原 AGSL：运动、帧调度、几何/轮廓缓存、渲染原语、背景自适应前景、外部源亮度采样、显式源内容通知和调用方配置的迟滞状态策略。Sleepy 已消费同一模块；Host/主题上下文、完整控件与独立示例仍待提取，公开 API 尚未收口。

以 iOS 26.5 原生控件为参照，逐控件校准后再作为独立仓库开源，Sleepy 作为首个使用方。当前近原生验收与来源/分发许可核对均未完成，不宣称“99.99%”或具备独立开源发布条件。源码范围见 [asset-catalog.json](asset-catalog.json)，提取步骤见 [extraction-plan.md](extraction-plan.md)，残差见 [对照记录](../../docs/native-glass-control-alignment.md)。

## 当前接入入口

在本仓库的消费 module 中添加：

```kotlin
dependencies {
    implementation(project(":liquid-glass-runtime"))
}
```

根工程已在 [settings.gradle.kts](../../settings.gradle.kts) 登记模块；`:kyant-backdrop`、插件及版本目录仍由本仓库提供。运行模块目前沿用 `com.lingion.sleepy.ui.component.liquidglass` 包名，独立仓库与 sample 的构建入口尚待建立。复用本机已有 Android SDK，在仓库根目录运行：

```sh
./gradlew :liquid-glass-runtime:assemble
./gradlew :liquid-glass-runtime:testAndroidHostTest --tests 'com.lingion.sleepy.ui.component.liquidglass.BackdropForegroundAtlasTest'
```

当前 [build.gradle.kts](runtime/build.gradle.kts) 声明 minSdk 26、compileSdk 37 和 JVM 17；这些构建设置不代表完整设备支持、性能或降级矩阵已经验收。

## 背景自适应前景

[backdropAwareForeground](runtime/src/androidMain/kotlin/com/lingion/sleepy/ui/component/liquidglass/BackdropAwareForeground.kt) 只按内容的 alpha mask 重绘中性前景，所有响应参数由调用方给出。`BackdropForegroundResponse` 必须显式提供 `lowBackground`、`highBackground`、`lowerLuma`、`upperLuma` 与完全不透明的 `transparentBackground`；区间满足 `0 ≤ lowerLuma < upperLuma ≤ 1`。此 GPU 前景原语不提供 iOS 分类器、默认门限、选中主题色或时间平滑策略；独立的调用方配置状态策略见下文。

下面的 slot 接收调用方已确定的尺寸和响应；整个 slot 是显式采样区域，也可改为其中一个有效子矩形：

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.Backdrop
import com.lingion.sleepy.ui.component.liquidglass.BackdropForegroundResponse
import com.lingion.sleepy.ui.component.liquidglass.backdropAwareForeground

@Composable
fun AdaptiveNeutralSlot(
    width: Dp,
    height: Dp,
    backdrop: Backdrop,
    response: BackdropForegroundResponse,
    neutralContent: @Composable () -> Unit,
    accentContent: @Composable () -> Unit,
) {
    val pane = with(LocalDensity.current) {
        Rect(0f, 0f, width.toPx(), height.toPx())
    }
    Box(Modifier.size(width, height)) {
        Box(Modifier.matchParentSize().backdropAwareForeground(
            backdrop = backdrop,
            response = response,
            sampleBoundsPx = { pane },
        )) { neutralContent() }
        accentContent()
    }
}
```

调用方提供该控件应采样的外部 `Backdrop`，包含正确的父层上下文，并排除本控件前景及自身自适应 backing，避免反馈采样。`sampleBoundsPx` 使用该 modifier 的局部物理像素坐标，必须是尺寸内的有限正面积矩形；中心及相对 `(20%,30%)`、`(80%,30%)`、`(20%,70%)`、`(80%,70%)` 五点共同参与响应。输入 RGB 不保留，只有 alpha 用作中性 mask；主题色、选中内容、badge 和其他 accent 应像示例一样放在 mask 外。

普通 `LayerBackdrop` 处理相对平移。若消费者相对 source 有缩放或旋转，调用方需先用现有 `LayerBackdrop.withTransformCompensation()` 包装，再传入；该路径未在本批硬件测试中验证。API 低于 33、消费者坐标不可用或采样矩形无效时绘制原内容；source 为空时按显式 `transparentBackground` 测量亮度。此原语为小面积前景保留三层 GPU recording，总记录面积约为内容面积的五倍，实际内存和性能尚未测量。

[93 验证记录](validation/observations/backdrop-aware-foreground-gpu-93.json)保留构建、5 项 host 检查、API36 两项 GPU 测试、8 张原图及 37 个独立像素检查的哈希链。验证范围是合成与生命周期；该 GPU 前景原语未接入生产 Dock，也未提供原生分类器、默认响应参数或完整原生外观验收。真实 glyph 边缘、透明端点/部分透明 source、中间亮度、source 替换、scale/rotation、低 API 硬件及性能仍不在本批结论内。

## 外部源采样与迟滞状态

[sampleBackdropLuma](runtime/src/androidMain/kotlin/com/lingion/sleepy/ui/component/liquidglass/BackdropLumaSampler.kt) 从调用方已经录制的外部 `Backdrop` 取得五个一像素区域，录入一个 `5×1` layer 后异步读取。调用方显式提供不透明的 `transparentBackground`、局部物理像素 `sampleBoundsPx` 和接收 `BackdropLumaSample` 的 `onSample`。矩形须在 modifier 尺寸内且有限、正面积；点位为中心和 `(20%,30%)`、`(80%,30%)`、`(20%,70%)`、`(80%,70%)`。source 须先完成 recording，并排除消费者及自身自适应 backing；普通内容仍绘制一次。

`meanLuma` 直接按捕获的 sRGB 通道值和 `.2126/.7152/.0722` 权重计算并归一化，不是线性光亮度或原生分类器。直接传入 `LayerBackdrop` 时，样本可带其录制 revision；包装后的 `Backdrop`（包括 `withContentSignal`）不再通过该直接类型检查，`sourceRevision` 为 null。其他 `Backdrop` 须在 `drawBackdrop` 中暴露 snapshot invalidation，或由调用方显式通知内容变化；静默更新 retained 子层不会自动成为父层 revision。source 内容在读取期间更新时，已冻结样本仍按历史观察交付，然后请求较新内容；source 身份、几何或配置变化会使不兼容结果失效。中间版本可能跳过，回调不保证对应当前帧。调用方应保留快照/完成时间、revision、generation 和计数的范围，不能把它们当作 compositor 或 settling 时钟。

[BackdropContentSignal](runtime/src/androidMain/kotlin/com/lingion/sleepy/ui/component/liquidglass/BackdropContentSignal.kt) 为这种外部源提供显式通知。调用方为同一场景的源叶持有一个 signal，并用 `remember(source, signal) { source.withContentSignal(signal) }` 保留包装对象；sampler 绘制该 wrapper 时观察通知。每次调用 `withContentSignal` 都创建新对象，不能在重组中反复替换 source 身份。signal 只请求重新采样，不记录像素，也不是源完成录制的 revision 或 presentation 时间。

调用方必须覆盖每个独立 retained 的、排除光学消费者的源叶。在该叶自身的 `graphicsLayer`/clip 边界内、绘制 modifier 之前放置 `notifyBackdropDraw(signal)`，由它在 `drawContent()` 返回后通知；含其他 retained 子层的父节点无法观察这些子层后续单独重绘。此 modifier 不创建源/消费者隔离，也不递归检测任意子树。`graphicsLayer` 属性变化和 native 绘制更新须由源的拥有方在 UI 线程显式调用 `signal.notifyContentChanged()`。通知必须在对应源更新已可供绘制后发生；不能只在状态赋值时提前通知，否则 sampler 可能读取旧内容而收不到后续变化。禁止从光学消费者通知，避免反馈。

recording 与 `toImageBitmap()` 留在 UI 线程，worker 只对返回的独立 bitmap 做 CPU 读回，硬件 bitmap 先复制为 `ARGB_8888`；source layer、`DrawScope` 和 `LayoutCoordinates` 不传给 worker。modifier 内部保留 consumer `graphicsLayer`，将观察失效与同层的 source 生产者隔离，避免共享层重绘使 source 再录制、revision 回灌而持续触发采样。同一 sampler 至多一个请求在途，未变化的 draw 不轮询。`5×1` 目标不保证总 GPU 工作或主线程延迟很小，实际支持和性能尚未验收。API<28、坐标不可用或无效矩形不产生样本；调用方保留自己的 fallback。相对 scale/rotation 仍需现有 `LayerBackdrop.withTransformCompensation()`，本说明不增加其硬件验收结论。

[BackdropAppearanceHysteresis](runtime/src/androidMain/kotlin/com/lingion/sleepy/ui/component/liquidglass/BackdropAppearanceHysteresis.kt) 是独立的状态策略，不负责采样或配色。构造时必须给出 `initialDark`、`enterLightLuma`、`enterDarkLuma`，满足 `0 ≤ enterDarkLuma < enterLightLuma ≤ 1`；dark 状态在 `luma ≥ enterLightLuma` 转 light，light 状态在 `luma ≤ enterDarkLuma` 转 dark，其余保持。非有限或越界输入保持上次状态。调用方持有同一实例，在 source 或外观上下文替换时重建，并将 `accept()` 返回值写入自己的 UI state；类自身不提供 Compose 状态、默认门限、动画、debounce 或原生时间模型。

Sleepy 的 [Dock 消费者](../../app/src/main/java/com/lingion/sleepy/ui/component/liquidglass/LiquidGlassNavigationBar.kt) 保留采样/迟滞的应用侧工程候选接线，`LocalDockBackdropAppearanceEnabled` 默认 false；光学和外观验证 fixture 显式 opt in。启用后的受支持采样路径联动材质分支和普通前景，selected/accent 继续由应用 token 提供。五点策略及 `223.5/255`、`153.5/255` 门限属于应用侧工程候选，未作为库默认值；原有主题 fallback 与具体控件仍在 Sleepy。源码接线不代表生产默认启用，也不说明 body transfer、glyph RGBA/coverage 或原生时序已经匹配。

94 的无显式通知 retained 诊断已记录局限：A→B→C→D→C→B→A 的 14 个 REST 观察中，源 draw serial 从3增至9，`source_host_version` 始终为2，亮度样本数始终为2、亮度保持 `26/255`。这说明该路径的父层版本未覆盖 retained 叶更新；原捕获/坐标检查通过不等于采样持续更新通过。该旧观察不能作为新 signal 的通过结果。原 [93 通用 GPU 前景验证](validation/observations/backdrop-aware-foreground-gpu-93.json)只覆盖旧 `BackdropAwareForeground`；Batch94 results and package identities are archived in the [94 validation record](validation/observations/source-aware-sampling-94.json): builds,18 host checks and two API36 real-Activity tests pass, covering direct-source sampling, explicitly notified retained leaves and unchanged-source idle. Fourteen retained Dock observations and sixteen multi-background captures pass their declared state/geometry/readiness checks. Production adaptation remains default-disabled; arbitrary subtree updates, complete lifecycle/fallback, performance and native optics/timing remain unaccepted.

## 已完成的迁移

[可执行提取路线](extraction-plan.md)按实际源码闭包安排纯运动/几何、材质采样、控件和 Sleepy 适配器。2026-11-14 已完成 M1：`NativeGlassDragDeformation.kt`、`NativeDockDeformation.kt`、`NativeSliderEndpoint.kt` 只有 runtime 中的一份实现；module AAR 与 Sleepy 编译通过，三类原测试在迁移前后均为 19 项通过，测试与两份 CSV 哈希不变。实际命令、首次 SDK 环境缺失及重跑结果见 [M1 迁移记录](validation/migrations/m1.json)。M2a 随后移动 `ActiveFrameLoop.kt` 和 `DockInputClock.kt`，只开放五处必要可见性；四类原测试迁移前后各 33/33 通过，计算和时钟行为不变，AAR 是唯一实现归属，debug/androidTest 同轮构建通过。依赖解析保持 Compose runtime 1.12.1 和 coroutines-core 1.11.0，详见 [M2a 记录](validation/migrations/m2a.json)。M2b、M2 几何和 M3a 渲染原语记录见下；M3 Host/主题上下文、M4 控件及独立示例仍待执行。

## 提取边界

M2b 首批仅移动 `LiquidGlassPhysics.kt`、`GlassControlMotion.kt`、`GlassDockMotion.kt`、`NativeDockParentMotion.kt`。Physics/parent-motion 正文逐字节一致，两个 driver 仅开放 11 处跨 module 必需的声明和只读 getter；`SpringState` 的四个 setter 与 parent driver 仍为 internal。原 `NativeDockParentMotionTest` 和两份独占 CSV 原样进入标准 `runtime/src/androidHostTest`，其余 18 类测试留 app。runtime 复用现有 Compose compiler 2.4.21、UI 1.12.1 与 JUnit 4.13.2，接入正常 host test；不加 friend-path。原 19 类 124 项迁移前后均通过，after 分为 app 109 项及 host 15 项；21 份测试/资源哈希及全部测试身份不变。AAR 与 app 生产输出无重复实现，实际依赖解析保持原版本，API36 debug/test 安装身份匹配且首页启动可见。该安装只验证身份与启动，不新增光学或自然动态验收。完整命令、哈希、证据修正及边界见 [M2b 首批运动记录](validation/migrations/m2b-motion.json)。

目标 Android/Compose 运行库包含玻璃材质、背板采样与分层、轮廓、输入时钟、各控件自己的动态参数，以及 Toggle、分段 Tab、Dock、单双头滑杆、按钮、菜单和面板。主题色、文案、图标、选项数据、选择回调和触觉策略由调用方注入；课表、课程、数据库、设置持久化及 Material Design 路由继续归 Sleepy。原生采集和离线拟合工具不成为 Android 运行时依赖。

M2b 后续将 `GlassPressMotion.kt`、`NativeDockEndpoint.kt` 和 `NativeSegmentedEndpoint.kt` 移入同一 runtime，各仅移除一次顶层 internal。两份 Endpoint 原测试逐字节移至标准 host test，依赖 app alpha helper 的 Press 原测试留 app；迁移前后均为17项通过，全部测试身份与字节保持。runtime AAR、debug/androidTest 同轮29.31s构建通过，66个AAR类与app实际2289个生产类无交集，未改构建配置、调用者、参数或内部setter。安装身份与MainActivity启动已核对；首张截图为现有空课表卡片与Dock，不据此增加原生验收。详见 [M2b 按压/端部记录](validation/migrations/m2b-endpoints.json)。

M2 几何已移动六份几何源码，并把原 Shader 中的 `LiquidGlassCachedOutlineProfile` 提取到 runtime 的 `LiquidGlassOutlineProfile.kt`；只移除 28 处跨 module 必需的 internal，保留公式、FMA/精度分支和 lazy/path/descriptor 身份判断，仅增加 shapes `1.2.1` implementation 依赖。五类原 app 单测迁移前后各26/26通过，测试身份与哈希不变；AAR、debug/androidTest 和 after 单测同轮43.9488s通过。该批runtime共19源，102个AAR类与实际2253个app生产类无交集，六份原app源已不存在。compile/runtime 独立依赖图均确认 shapes `1.2.1`、UI graphics `1.12.1` 且无未解析项，API36 main/test 安装哈希匹配并已 resumed MainActivity。原三类8项设备测试使用注册的 `SleepyRenderTestRunner` 在1.286s通过；首次误用未注册默认 runner 的命令在0项测试启动时失败，原错误单独保留，未改源码、测试或预期。root逐张核对三份截图：android-module-launch.png显示现有空课表卡片和Dock，android-after-geometry-tests.png为测试后即刻resume的splash，后续android-launched-home.png可见第11周课程网格和Dock，三者分开保留且不推断瞬态数据差异；仅验证本批真实安装/启动可见，不新增数据内容、UI/光学或自然动态验收。详见 [M2 几何记录](validation/migrations/m2-geometry.json)。

M3a 已把 `LiquidGlassStyle.kt`、`LiquidGlassShader.kt`、`LiquidGlassEffect.kt` 和原 AGSL 移入 runtime；只移除六处跨 module 必需的 internal，其余源码和 AGSL 字节不变，包名、调用者、实参及原测试未改。七类原 app 单测 before 34/34 用时11.8416s，after AAR/debug/androidTest 与同34项用时50.9080s通过，测试身份与源码哈希不变。该阶段 runtime 共22源，113个AAR类与实际2242个app生产类无交集；AAR/APK各有唯一、字节相同的 shader，原 app 三源、资产与旧 generated shader 已不存在。

标准 `runtime/src/androidMain/assets/shaders` 配合 `androidResources { enable = true }` 已由实际包验证。现有 backdrop/Material3 以 api 暴露公开类型，Foundation `1.12.1` 和 HCT `1.10.0` 为 implementation；这不是无 Material3 依赖的 core。

API36 使用注册的 `SleepyRenderTestRunner` 运行原 HazeRender 三项，5.3395s通过；该次安装主包/测试包与 host 哈希匹配，第11周网格首页和 Dock 可见，仅作安装/启动验证。独立 resolutionResult 的 compile67/runtime121组件无未解析项，backdrop、Material3、Foundation、HCT、shapes/UI 均解析到现有版本，真实编译输入含 backdrop jar；最初两个只读诊断错误保留。署名证据路径元数据修正后仅重打包主包，2.4462s通过；最终主包已重装、host-device哈希一致且MainActivity resumed。该身份与原 GPU 运行包分开记录，未重复原34项单测或三项GPU测试，本页不固定主包哈希。完整证据见 [M3a 渲染原语记录](validation/migrations/m3a-rendering.json)。

完整运行库仍未完成解耦。同一个 `liquidglass` 包目前仍混有 `LocalUiStyle` 等主题判断、导航图标驱动、设置布局、调试语义和 Material 分支；滑杆还引用包外的弹窗动画及 Material 辅助函数。`SegmentedSwitcher.kt` 同时承载玻璃控件实现和 Sleepy 适配，不能整文件归为已分离的适配器。语言菜单已泛化为 app 侧共享 `SingleChoiceDropdown` / `GlassChoiceMenu`，语言选项及入口留在 Sleepy；Host、主题与调试依赖仍未迁出。当前二十六份 runtime 实现还包含通用源亮度采样、显式内容通知和迟滞策略；完整 Host 采样闭包、主题注入、控件及独立示例仍待完成，catalog 保留相应阻碍。本批未新增完整光学、自然动态、近原生或发布验收。

原生采集工具、可分发的数值基线、回放和示例属于独立验证目录。原始长视频、临时 APK、DerivedData 和设备导出缓存不进入库或日常 CI。CI 保留正常贡献所需的编译、针对性回归和可复现示例；多背景 GPU 采证显式运行。

## 整理与发布顺序

1. 用同一份运行代码继续修复，集中记录每个控件、每类输入和每种背景的误差。
2. 逐项移除 Sleepy 业务依赖，提取 Android library 模块；用 Sleepy 适配器注入配色、文案、图标、回调及策略。运行模块不反向依赖 Sleepy；提取前后复用相同基线，避免整理代码时改变行为。
3. 建立可独立运行的示例和验证入口，保留参考版本、设备、实际输入、帧时间及全部适用范围；独立声明 SDK、Compose、backdrop 和 shader 资产依赖，验证降级路径、取消和无障碍行为。
4. 逐控件达到预先固定的接近原生验收要求，并完成逐文件来源和分发许可核对后，再创建独立公开仓库，迁入来源记录、许可证声明、使用示例和贡献流程。当前两个条件均未完成，不因目录和文档齐备提前发布。

每次校准只保留一个真实实现。模块按依赖闭包逐步迁移，Sleepy 继续消费同一份代码，逐阶段核对行为和基线。

## 验收依据

[控件回归记录 95](validation/observations/control-regressions-95.json)保留 Toggle 点按回落、真实设置/课程单双头滑杆和菜单收尾修复的测试、包身份与残差。该批未完成逐控件原生验收；Toggle 的中心位移、后段宽度和异步父行路径仍有差距。

[单选统一记录 96](validation/observations/single-choice-unification-96.json)记录五处单选入口、Material Expressive 菜单及 Glass 共享交接。最终定向 23 项通过，跨轮按类汇总为 52 项；pre-Expressive Glass 夹具与最终包的 Material 语言、Glass 调休生产检查分列身份和范围。运行库、既有运动参数及其他控件未改，完整 1:1、全应用 Material 现代化和发布验收仍未完成。

不能用一个平均分把某个控件的明显误差覆盖掉，也不能把当前状态标成“99.99%”。发布前需逐控件、逐角色先固定测量方法和可接受误差，再保留实测结果及未通过项；目前没有完整通过的发布验收矩阵。每类控件分别保留点击、按住已选/未选项、慢快拖动、加速减速、反向、端部拉伸、持续顶住、松手回弹、取消及连续首尾切换的适用证据。形状与四角、位置、宽高形变、透明度、磨砂、折射、色散和分层分别记录。

视觉验证覆盖细网格、高反差彩条、明暗色块和纹理风景，以及明暗主题、普通背板、磨砂背板和弹窗。对照须匹配控件和父容器上下文、背景坐标及实际投递输入；输入、模型和实际绘制结果分开记录。公开 presentation 载体矩形不能代替可见轮廓或最终屏幕，内部几何或单个 probe 通过也不能覆盖整个控件。未测项目、帧相位不确定性和保留残差必须随参考一起发布；当前光学、快速动态和完整四角等残差仍见对照记录。

原生 Dock 的[实际前景属性记录](validation/observations/dock-foreground-properties-91.json)区分外层 bar 与每个图标/标签自身的 traits、RGBA 和字体。它补充参考证据；Android 背景自适应前景及完整光学仍待校准。

[灰阶历史记录](validation/observations/dock-tonal-history-93.json)另保留两次独立原生序列：相同179/204输入在升程和回程有不同前景及body响应，下降路径在179仍为light、128已返回dark。这约束状态策略，不识别原生阈值或状态粒度。Dock 的源亮度采样/迟滞候选默认关闭，仅在验证 fixture 中显式启用；通用 GPU alpha-mask 前景原语仍未接入该控件，完整光学尚待校准。

## 双头参考与验证入口

现有 [原生参考工具](../../tools/native-glass-reference/README.md#paired-native-range-optical-matrix)已增加四种背景、明暗主题的独立双 UISlider 按住参考；[Android 光学夹具](../../app/src/androidTest/java/com/lingion/sleepy/ui/component/NativeRangeOpticalWorldRenderTest.kt)直接使用生产 Range/input，在相同逻辑世界导出两端和轨道的实际坐标。原生是 host 组合两个真实 UISlider，不能称为 Apple 原生双头 API；Android 稳态使用合成时钟，不能据此验收自然动态。原生按下前与按住中心分别记录，不隐藏取整、主题色及轨道上下文差异。夹具和其 Sleepy 主题依赖目前仍属于应用侧验证，尚未迁入独立示例，也不改变发布 gate。 77 批次已用同 primary 及实际产品主题比较独立 Range 材质入口，减少多余彩纹与深色白化；它暂复用 Single F，保留独立角色入口、四处诊断区域退步及暗上沿/端帽残差。该有限修正不代表完整原生双头验收，数据与限制见对照记录。

## 来源

保留 Sleepy 现有 [GPL v3 LICENSE](../../LICENSE) 和[完整致谢清单](../../ACKNOWLEDGEMENTS.md)。FLClash 的 Kotlin/AGSL 移植保留 GPL-3.0 声明；Flutter 超椭圆移植保留 BSD-3-Clause 声明；Kyant 原始基线保留 Apache-2.0 声明。AGSL 运行资产已迁入 runtime，原源码头保留；适用 notice 的独立库分发安排仍需按实际提取范围完成，不能把 shader 打包通过当作许可门禁通过。

[现有 backdrop 模块](../../third-party/kyant-backdrop/README.md)经 SleepDown 定制源码导入，SleepDown 自有修改另有非商业、源码可见声明。2026-11-14 已逐字节确认：Sleepy 最初导入 `c2490785` 的 42 个 Android/common 运行文件和模块 README，与 [SleepDown 固定快照 `d02898140327796946f2b140cb3ca87390595810`](https://github.com/xiaomanjun233/SleepDown-Schedule/tree/d02898140327796946f2b140cb3ca87390595810/third-party/kyant-backdrop) 完全一致；保留的 SleepDown 许可与第三方声明也与该快照一致。该修订记录为 `verifiedEquivalentSourceRevision`，证明可复现的内容基线，不代替仍未知的历史取得/导入修订，也不代表取得定制代码的再分发授权。当前运行代码相对 Kyant 2.0.0 为 24 个仅多一个末尾换行的原文件、10 个修改原文件及 8 个新增文件；导入后的 Sleepy 修改另记于 [UPSTREAM.json](../../third-party/kyant-backdrop/UPSTREAM.json)。

定制代码的分发许可兼容性仍未确认；Kyant 的原始许可不能覆盖这个缺口。catalog 链接现存来源、许可证及待核对事项，当前不改授权、不宣称全部原创，也不将这些记录当作独立开源分发许可已解决的证明。原生截图、数值基线和示例素材的可分发范围同样需在迁出前核对。
