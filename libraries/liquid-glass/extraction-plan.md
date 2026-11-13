# Liquid Glass 提取实施计划

目标是先把资产库变成 Sleepy 实际消费的单一 Android 运行模块，继续用同一实现校准，逐控件接近原生且满足现有发布条件后，再迁入独立公开仓库。2026-11-14 已完成 M1：三个纯运动文件进入 runtime，AAR 和 Sleepy 编译通过，三类原测试迁移前后均为 19 项通过；命令和哈希见 [M1 记录](validation/migrations/m1.json)。M2a 随后迁入两份帧调度/输入时钟文件，四类原测试迁移前后各 33/33 通过、AAR 唯一归属，详见 [M2a 记录](validation/migrations/m2a.json)。M2b 首批四文件及其独立 host 测试已完成构建、原 124 项回归、AAR 归属、依赖解析和安装身份核对，详见 [M2b 首批运动记录](validation/migrations/m2b-motion.json)。随后按压/端部三源及原17项回归、唯一归属和安装身份也已核对，见 [M2b 按压/端部记录](validation/migrations/m2b-endpoints.json)。几何、M3–M4、独立示例及发布 gate 仍待执行。来源事实沿用 [catalog](asset-catalog.json)，不在本计划重复取证。

现已建立 `:liquid-glass-runtime`，目录 `libraries/liquid-glass/runtime`；后续示例放 `libraries/liquid-glass/sample`。M1 使用现有 Kotlin Multiplatform + Android library 插件的 Android target，未同时引入平台迁移。首轮保留现 Kotlin 包名，减小调用变动；适配器落位时统一迁到 `com.lingion.liquidglass`，Sleepy 旧入口作为转调适配器保留。每个源文件只属于一个 module；不复制实现，不把 app 源目录同时登记给两个编译单元。

文中 `G/` 指 `app/src/main/java/com/lingion/sleepy/ui/component/liquidglass/`，`T/` 指对应的 `app/src/test/java/com/lingion/sleepy/ui/component/liquidglass/`。阶段表列的是同包符号依赖与 imports 的静态闭包；最终以各阶段实际编译和原基线验证为准。

## 真实依赖顺序

| 阶段 / 源文件 | 必须同时具备的闭包、当前阻碍 | 阶段边界 |
| --- | --- | --- |
| M1 纯运动：`NativeGlassDragDeformation.kt`、`NativeDockDeformation.kt`、`NativeSliderEndpoint.kt` | 前者和 Slider endpoint 只用 Kotlin 数学；Dock deformation 只额外引用前者的 `Spec`。无 Android/Compose/app imports。 | 最小首批可构建 runtime；不包含控件、backdrop 或 shader。 |
| M2a 调度（已完成）：`ActiveFrameLoop.kt`、`DockInputClock.kt` | 两份完整调度闭包，只依赖 Compose runtime 1.12.1（api）和 coroutines-core 1.11.0（implementation）；不需要 Compose compiler、UI 或 shapes。 | 同包移动、五处必要可见性调整；保留唯一 `LocalDockInputTimeNanos`、真实帧/receipt 时间和队列顺序。四类原测试前后各33项通过。 |
| M2b 首批运动（已完成）：`LiquidGlassPhysics.kt`、`GlassControlMotion.kt`、`GlassDockMotion.kt`、`NativeDockParentMotion.kt` | Physics 的 `SpringState` 用 Compose state，`FlClashDockPhysics` 用 Compose `Rect`；两个 driver 直接写 internal velocity/isRunning，四文件已同迁 runtime。 | 保留四个 internal setter 与 internal ParentMotion；仅开放 11 处 app 调用/测试必需的声明和只读 getter。原 ParentMotion 测试及两份独占 CSV 已原样迁入标准 runtime host test，原 124 项前后均通过，AAR 唯一归属已验证。 |
| M2b 按压/端部（已完成）：`GlassPressMotion.kt`、`NativeDockEndpoint.kt`、`NativeSegmentedEndpoint.kt` | 只使用 runtime 已有公开 Physics/UI API；三源各移除一次顶层 internal，调用者/构建配置不变。 | 两 Endpoint 测试原字节进入标准 host test，依赖 app alpha helper 的 Press 测试留 app。原17项前后通过，AAR唯一归属和安装身份已核对；不改 press/endpoint 策略或原基线。 |
| M2 几何：`LiquidGlassSuperellipse.kt`、`LiquidGlassGeometry.kt`、`LiquidGlassExactContour.kt`、`LiquidGlassDualContourShape.kt`、`FlClashGlassShape.kt`、`DockPlatterShape.kt` | ExactContour → Superellipse；FlClashGlassShape → 两者；DockPlatterShape → ExactContour、Kyant `Capsule` **和当前定义在 Shader 文件中的 `LiquidGlassCachedOutlineProfile`**。Superellipse 内含 Android Build/FMA 分支与 `android.graphics.Path` 回放，不能整文件移到纯 commonMain。 | 先把缓存类从 `LiquidGlassShader.kt` 原样移动到新 `LiquidGlassOutlineProfile.kt`；再把几何闭包放 `androidMain`。保留 FMA 分支、Float/Double 精度、路径/描述符身份和缓存所有权。 |
| M3 光学：`LiquidGlassStyle.kt`、`LiquidGlassShader.kt`、`LiquidGlassEffect.kt` | Style → Material3 `ColorScheme`、HCT、backdrop Shadow；Shader → 几何、style、轮廓缓存；Effect → 几何/shape、shader/style、定制 `BackdropEffectScope.inputRenderEffect`。 | 需要现有 `:kyant-backdrop`，不能直接换成原版 Maven artifact；连同 AGSL 资产迁移。 |
| M3 采样：`GlassHost.kt`、`GlassLayerRecorder.kt`、`GlassFilterBands.kt`、`GlassBackdropSource.kt`、`GlassSurfaceSource.kt`、`GlassAndroidViewSource.kt`、`GlassMaterialBackdrop.kt`、`GlassSurface.kt` | Host ↔ FilterBands/MaterialBackdrop；Source/SurfaceSource → Host、Recorder、MaterialScope；MaterialBackdrop → Host 的 overlay sampling host **和 ControlModifier 文件中的 `LocalGlassOverlay`**。此外 MaterialBackdrop:93 读 `LocalUiStyle`。这些不是仅凭“没有 app import”就能独立迁走的文件。 | 新建 `GlassMaterialContext.kt`，原样承接 `LocalGlassOverlay`、`LocalGlassGrouped`、`LocalGlassPlacementLayer` 及 `GlassMaterialRole`；MaterialBackdrop 的主题判断改为调用方传入的 `glassEnabled`。采样状态闭包同批进入 runtime，不拉入控件分支。 |
| M4 控件与适配器 | 见下表。`GlassControlModifier` 依赖 M2/M3；`GlassButtons` 还引用 OverlayContent 的 alert action；`GlassOverlays` ↔ OverlayContent/MaterialContext；不能逐文件机械搬。 | 同一控件实现迁一次；Sleepy 保留业务内容、Material 路由和策略。 |

M2a 已增加匹配实际解析的 runtime `1.12.1` 和 coroutines `1.11.0`；M2b 首批现已接入根构建的 Compose compiler `2.4.21`、UI `1.12.1` 及标准 host JUnit `4.13.2`。两份独立 compile/runtime 依赖图确认 runtime/UI `1.12.1` 与 coroutines `1.11.0`，compiler 图确认 `2.4.21`，没有升级原版本；几何阶段再加 shapes `1.2.1`。M3 再增加现有 `:kyant-backdrop`、Material3 `1.5.0-rc01`、`studio.lunabee.compose:material-color-utilities:1.10.0`，需要动画/布局的阶段再声明 foundation/animation。当前 app 使用 Compose BOM `2026.09.00`，backdrop 显式用 UI/Foundation `1.12.1`；后续阶段仍须核对两方实际解析结果。

## M1：最小首个可构建 module

本阶段只迁上表的三个文件。接口暂保留 `NativeGlassDragDeformation`、其嵌套 `Spec`、`NativeDockDeformation`、`nativeSliderVisualPosition(rawFraction, businessFraction, controlWidthDp, thumbTravelDp): Float` 的现有签名。它们是迁移期间的可见接口，公开 API 稳定性留到控件收口时审阅。

- [x] 在 `libraries/liquid-glass/validation/migrations/m1.json` 记录这三个文件、`app/src/test/resources/native-glass/{drag-matrix,dock-direct-selection}.csv` 和三个原测试的哈希及迁移前后结果；不改误差门限和预期值。该记录只保存哈希、命令及结果，不复制输入或构建输出。

  ```sh
  ./gradlew :app:testDebugUnitTest --tests 'com.lingion.sleepy.ui.component.liquidglass.NativeGlassDragDeformationTest' --tests 'com.lingion.sleepy.ui.component.liquidglass.NativeDockDirectDeformationTest' --tests 'com.lingion.sleepy.ui.component.liquidglass.NativeSliderEndpointTest'
  ```

- [x] 创建 `libraries/liquid-glass/runtime/build.gradle.kts`，只复用根构建已声明的插件和版本：

  ```kotlin
  import org.jetbrains.kotlin.gradle.dsl.JvmTarget

  plugins {
      id("org.jetbrains.kotlin.multiplatform")
      id("com.android.kotlin.multiplatform.library")
  }

  kotlin {
      android {
          namespace = "com.lingion.liquidglass"
          compileSdk = 37
          minSdk = 26
          compilerOptions { jvmTarget = JvmTarget.JVM_17 }
      }
  }
  ```

- [x] 在 `settings.gradle.kts` 增加 `include(":liquid-glass-runtime")` 与 `project(":liquid-glass-runtime").projectDir = file("libraries/liquid-glass/runtime")`；在 `app/build.gradle.kts` 增加 `implementation(project(":liquid-glass-runtime"))`。
- [x] 将三个文件**移动**到 `runtime/src/androidMain/kotlin/com/lingion/sleepy/ui/component/liquidglass/`。跨 module 的 `internal` 不能靠相同 package 访问：把这两个类、嵌套 `Spec` 和 endpoint 函数的 `internal` 去掉，方法/数值不变；其余内部状态仍保持私有。当前调用点是 `GlassControlMotion.kt`、`GlassDockMotion.kt`、`GlassSlider.kt`，加上原测试。没有添加 compiler friend-path 绕过边界。
- [x] 运行 `./gradlew :liquid-glass-runtime:tasks --all` 确认插件生成任务，再运行 `./gradlew :liquid-glass-runtime:assemble :app:compileDebugKotlin`，随后原样重跑本阶段原测试。测试和 CSV 暂留 app 的唯一原位置；本阶段验证的是 runtime 可构建且 app 已真实消费，不宣称独立测试工程已经完成。

本阶段退出条件已通过：app 原目录不再声明这三个实现；runtime 产出的 `liquid-glass-runtime.aar` 包含三个实现及嵌套类型；app 自己的 Kotlin 编译输出不再包含这些类；原测试为 Drag 6、Dock 8、Slider 5 项，迁移前后均无失败、错误或跳过，测试和 CSV 哈希不变。逐字节比对确认源码只有四处 `internal` 移除。首次测试在执行前因 SDK 路径缺失而失败，随后仅设置本机已有 SDK 的 `ANDROID_HOME`，原命令重跑通过；没有改缓存根或新建 SDK。当前 Android-only KMP module 会提示 `commonTest` 未挂到 compilation，M1 原测试继续留在 app，host test 接线留待 M2。若后续解析或编译失败，修正依赖/可见性，不能复制实现回 app 让两份并存。

## M2 / M3：闭包迁移与基线

- [x] M2a 按阶段表移动调度闭包；两份源码仅移除五处必要 internal，四类原测试与哈希不变，前后各33项通过，runtime/app/androidTest构建及AAR唯一归属通过。
- [x] M2b 首批四份运动源码移入 runtime；Physics/ParentMotion 逐字节不变，两个 driver 仅去掉 11 处必要 internal，四个 SpringState setter 仍为 internal。原 ParentMotion 15 项测试及其两份 CSV 原样移入 `runtime/src/androidHostTest`，通过 `withHostTestBuilder {}.configure {}` 与 JUnit 4.13.2 接线。
- [x] M2b 首批构建与原基线：冻结的 19 类 124 项 before 测试通过；after 18 类 app 109 项及同名 host 15 项通过，0失败/错误/跳过，测试及资源哈希不变。runtime AAR、app debug 和 androidTest 同轮构建通过。
- [x] M2b 首批其余证据：AAR 的 63 个运行类与 app 实际生产输出 2292 类无交集，源码只有 runtime 一份；实际依赖解析保持原版本。API36 debug/test 设备与 host 哈希相同，MainActivity resumed，截图可见有课程的课表网格与 Dock；仅验身份和启动。完整命令、初始证据路径/选择器修正及结果见 [M2b 首批运动记录](validation/migrations/m2b-motion.json)。
- [x] M2b 按压/端部三源迁入 runtime，仅三处必要可见性变化；两 Endpoint 原测试进入既有标准 host test，Press 原测试留 app。原三类17项前后通过、测试身份与字节不变，AAR/debug/androidTest构建29.31s通过；66个AAR类与实际2289个app类无交集。现有CI已覆盖两module，无新任务或依赖。记录见 [M2b 按压/端部](validation/migrations/m2b-endpoints.json)。
- [ ] M2 几何按阶段表补齐完整闭包；新增的 `LiquidGlassOutlineProfile.kt` 只承接原缓存类，不改 lazy/identity 判断。先检查 app 与测试跨边界真正使用的声明再开放必要可见性；`SpringState` 的 internal setter 留在 runtime，app 原调用以已有方法为边界。此阶段不改弹簧参数、Native 拟合范围、端部策略或路径公式。
- [ ] 迁移前后原样复用 `T/LiquidGlassPhysicsTest.kt`、`DockInputClockTest.kt`、`GlassControlTimedInputTest.kt`、`GlassDockMotionTest.kt`、`NativeDockParentMotionTest.kt`、`NativeDockEndpointTest.kt`、`NativeSegmentedEndpointTest.kt`、`LiquidGlassSuperellipsePrecisionTest.kt`、`LiquidGlassExactContourTest.kt`，每批只运行对应改变的闭包。先保留 app 集成测试；独立于 app 的测试才逐步移动到 `runtime/src/androidHostTest`，不整包迁入 app theme 或 `SoftwareWindowComposeRule` 等 helper。首批 ParentMotion 只需 JUnit `4.13.2`。后续图形测试沿用现有 `withHostTestBuilder` 接线，按实际闭包补齐 app 当前使用的 Robolectric `4.17`、AndroidX Test Core `1.7.0`、匹配的 Compose UI test 依赖及 Android 资源支持，保留 NATIVE 图形模式/SDK 配置。只有调用者一起迁完才收回临时 public 可见性。
- [ ] M3 先落实 MaterialContext 和 `glassEnabled` 注入，再迁光学/采样闭包。`glassEnabled` 表示调用方是否选择玻璃样式，不能覆盖控件已有的 `enabled` 交互参数。保留 clear/haze 两个输入分支、effect padding、深度前缀、父材质 backing、popup 坐标、View dirty invalidation 与 layer 释放。
- [ ] 将 `app/src/main/assets/shaders/liquid_glass.agsl` 移至 `runtime/assets/shaders/liquid_glass.agsl`，在 runtime 登记下方资产目录。app 的 `prepareRuntimeAssets` 只扫描自身源码资产；原文件移除后，应只通过 library 合并获得 shader。`LiquidGlassShader.shaderSource()` 和测试继续打开同一 `shaders/liquid_glass.agsl` 路径。相应 notice 以 catalog 既有清单迁移，更新路径引用，不扩大授权结论。

  ```kotlin
  androidComponents {
      onVariants { variant ->
          variant.sources.assets?.addStaticSourceDirectory("assets")
      }
  }
  ```

  该入口依据 [Android KMP 插件官方资产 API](https://developer.android.com/kotlin/multiplatform/plugin)。构建后检查 AAR 的 `assets/shaders/liquid_glass.agsl` 及最终 APK 的同一路径各来自一个源文件，不能靠 app 遗留资产让示例假通过。

- [ ] M3 迁移前后复用 `T/LiquidGlassShaderContractTest.kt`、`LiquidGlassHazeAtlasTest.kt`、`LiquidGlassStyleReferenceTest.kt`、`GlassHostCaptureTest.kt`；后者已有 API 31/35 的原生图形基线。GPU 光学仍以 `app/src/androidTest/java/com/lingion/sleepy/ui/component/liquidglass/{LiquidGlassHazeRenderTest,GlassMaterialSamplingRenderTest,GlassMaterialSamplingMatrixCaptureTest}.kt` 的适用场景和现有对照记录验收；host test 不替代屏幕光学或性能结论。

## M4：控件到 Sleepy 适配器的收口

| 当前真实文件/依赖 | runtime 保留 | Sleepy 适配器保留/注入 |
| --- | --- | --- |
| `GlassControlModifier.kt` → theme locals、M2/M3；`GlassButtons.kt` ↔ `GlassOverlayContent.kt` 的 alert action；`GlassSelectionControls.kt` | `glassControl` 光学/按压、按钮/选择绘制、通用 alert action 状态；按闭包迁，不把 action 状态留成 runtime → app 依赖。 | `LocalUiStyle` 路由、`LocalDarkTheme` 解释；传入 `glassEnabled`、`dark`、颜色/内容。保留 Material 分支入口。 |
| `GlassSwitch.kt`、`GlassSlider.kt`、`GlassSliderThumb.kt`、`SliderVisuals.kt` | 玻璃 Toggle、单双 Slider 交互/映射/绘制，thumb metrics、M2 时钟；保留当前可取消手势和可访问语义。 | `materialSliderGhostX` 来自 `IntRangeSlider.kt`，`sleepyPopupEnter/Exit` 来自 `SleepyMotion.kt`，属于现有适配依赖：Material 分支留 app；值提示层从调用方接收 enter/exit transition。`GlassGestureOwnership.kt` 中 Material SliderState 函数留适配器，通用 `glassGestureOwnership` 单独进入 runtime。 |
| `LiquidGlassNavigationBar.kt` → `PillNavItemSpec`、`NavIconDriver*`、`NavPartIcon`、`selectionColors` | Dock 布局、光学双绘制通道、M2 运动/端部响应、选择语义。 | 从 `PillNavigationBar.kt`、`NavIconMotion.kt`、`SelectionColors.kt` 组装 item 内容、内外绘制 slot、颜色、选中回调；不把导航路由/图标驱动整体迁入库。 |
| 包外 `app/src/main/java/com/lingion/sleepy/ui/component/SegmentedSwitcher.kt` | 玻璃分段控件的指针/时钟、轮廓、渲染与选择实现；不能把整文件标成适配器。 | `dbgTagPrefix`、选项文案、`SleepyTheme`、`LocalUiStyle` 和 Material 路由留 app；用同一库控件转调。 |
| `GlassLanguageMenu.kt` → `SettingsLayout`、`sleepyExpandSpecs`、`SleepyTheme`、`dbgTag`；另用 `LanguageMenuMotion.kt`、`LanguageMenuGestures.kt` | 通用菜单 anchor、长按/滑动选择、morph、popup/source handoff、绘制；后两文件和其语义/时间模型同迁。 | 语言 code/label 数据、设置行布局、展开策略、持久化回调与调试标签。用 item slot/选择回调承接，不在库中创建语言菜单专用业务入口。 |
| `GlassOverlays.kt`、`GlassOverlayContent.kt`、`GlassTopAppBar.kt` → theme locals，M3 popup/material 状态 | 通用 menu/dialog/sheet/app-bar 的玻璃 backing、共享采样和控件布局。 | 样式切换、Material 对应路由、主题输入与应用页面内容；不要把 Material fallback 路由误当成 RuntimeShader 不支持时的光学降级。 |
| `LiquidGlassHaptics.kt` | 可选的 Android 触觉实现可随库提供，保持平台 API 路径。 | 所有选择入口注入触觉回调/策略；库不替 app 决定权限、强制反馈或设置策略。 |

- [ ] 每个控件完成上述注入后，才将实现迁入 runtime；Sleepy 旧入口在原命名空间转调，新库命名空间统一为 `com.lingion.liquidglass`。同一阶段同步正常调用、测试和 example imports，避免旧包又生成一份实现。以 `rg` 检查 runtime 不再 import `com.lingion.sleepy`，且 Gradle 不依赖 `:app`；同包隐藏调用也须通过编译暴露。
- [ ] 控件回归使用原 `GlassSwitchQuickInputTest`、`GlassSelectionCoalescedInputTest`、`GlassSliderTimestampInteractionTest`、`GlassSliderEndpointInteractionTest`、`GlassDockInputContractTest`、`GlassDockParentTranslationContractTest`、`DockAccessibilityTest`、`GlassNestedPopupTest`、`GlassLanguageMenuTest`、`LanguageMenuMotionTest` 的对应场景。保留实际 receipt/sample/frame 时间、取消、disabled、RTL 和 reduced motion 语义，不改测量门限来通过迁移。

## 独立示例与公开仓库 gate

- [ ] 新建 `:liquid-glass-sample`（`libraries/liquid-glass/sample`），使用根构建已有 Android application/Compose 插件；只依赖 `:liquid-glass-runtime` 及自身 Activity/Compose 引导，不依赖 `:app` 或 app 的 theme、资源、测试 helper。由 sample 自己提供颜色、item 内容、回调和触觉策略。Sleepy 与 sample 必须消费同一份 runtime 和 shader。
- [ ] 示例展示 Toggle、分段、Dock、单双 Slider、按钮、menu/dialog/sheet 在网格、彩条、明暗色块和纹理背景下的相同输入回放。编译 sample/AAR 通过后，独立运行验证首次加载 shader、降级、取消、焦点/无障碍与动态选项变化。不能仅用“最小模块 assemble 通过”替代此 gate。
- [ ] 对每次迁移保留同一控件、输入脚本、背景、fixture 哈希、测量代码、误差门限及 before/after 结果；可分发数值基线最终移动到唯一的 `libraries/liquid-glass/validation/baselines/native-glass`，相关测试通过资源接线消费，避免复制成多套。设备原视频和离线采集工具仍不属于 Android runtime。
- [ ] 逐控件近原生验收、实际支持范围/性能、独立示例和现有来源/分发许可 gate 全部完成后，才创建独立公开仓库。迁出时带入 runtime、sample、必要基线与声明；Sleepy 切换为消费库，不维护平行实现。当前 catalog 的 `publicationReady`、`nativeCalibrationAccepted` 和 `sourceAndLicenseReviewComplete` 继续为 false。
