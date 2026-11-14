# Liquid Glass 提取实施计划

目标是让 Sleepy 和独立库共享单一 Android 运行实现，并继续逐控件校准。按更新后的用户要求，已先公开 [Kerry1020/sleepy-liquid-glass](https://github.com/Kerry1020/sleepy-liquid-glass) 的实验性 runtime 与 sample；完整控件、原生验收和稳定 API 继续按阶段推进。2026-11-14 已完成 M1：三个纯运动文件进入 runtime，AAR 和 Sleepy 编译通过，三类原测试迁移前后均为 19 项通过；命令和哈希见 [M1 记录](validation/migrations/m1.json)。M2a 随后迁入两份帧调度/输入时钟文件，四类原测试迁移前后各 33/33 通过、AAR 唯一归属，详见 [M2a 记录](validation/migrations/m2a.json)。M2b 首批四文件及其独立 host 测试已完成构建、原 124 项回归、AAR 归属、依赖解析和安装身份核对，详见 [M2b 首批运动记录](validation/migrations/m2b-motion.json)。随后按压/端部三源及原17项回归、唯一归属和安装身份也已核对，见 [M2b 按压/端部记录](validation/migrations/m2b-endpoints.json)。M2 几何六源及新轮廓缓存文件已完成构建、原26项单测和AAR唯一归属，该阶段runtime共19源；设备8项、安装身份及独立依赖解析也已核对，见 [M2 几何记录](validation/migrations/m2-geometry.json)。M3a 渲染原语随后迁入三源和原 AGSL，该阶段runtime共22源，原34项单测、构建、归属、资产及依赖解析已验证，原HazeRender三项与启动范围另记于 [M3a 记录](validation/migrations/m3a-rendering.json)。M3 Host/主题上下文、M4 完整控件和支持/性能验收仍待执行。来源事实沿用 [catalog](asset-catalog.json)，不在本计划重复取证。

实验性独立发行采用官方 Kyant 2.0.0，不包含本应用的 SleepDown 定制 fork，也不切换 Sleepy 当前后端。该范围须单独核对来源/声明、独立构建和 sample；完整近原生门槛不再阻挡明确标注限制的实验性公开。公开 `main` 已通过 Git 与 API 核对为 `ac9254c6e36312c6ef35c99ed28a881818bbd097`；独立 38 项 host 测试通过，sample 四背景 × 明暗主题已在真机查看；远程全新 clone 的 `:runtime:assemble :runtime:testAndroidHostTest :sample:assembleDebug` 在 34.99 秒通过，未创建 `local.properties`，复用既有 SDK 环境且未修改用户配置。该实验性发布不完成下面的完整控件示例和原生验收。

93 批次新增通用 `BackdropAwareForeground.kt`，当时 runtime 共23源；它通过显式响应参数重绘中性 alpha mask，不迁移 Host、不提供原生分类器或默认门限，也尚未接入生产 Dock。构建、5项 host 和API36两项GPU检查通过；37个独立像素检查最大通道误差1（原容差3）。本批AAR119类与app实际2242个生产类无交集，原AGSL仍唯一且字节不变；完整记录和未覆盖范围见 [93 通用前景验证](validation/observations/backdrop-aware-foreground-gpu-93.json)。这项新增原语不完成 M3b/M4 或独立示例 gate。

94 新增 `BackdropLumaSampler.kt`、`BackdropAppearanceHysteresis.kt` 和 `BackdropContentSignal.kt` 后，当前 runtime 共26份生产 Kotlin 源码及原 AGSL。Sampler 按外部 source 的 recording/坐标记录五点历史亮度样本，hysteresis 只持有调用方显式配置的双阈状态，signal 为 retained 源叶等更新提供调用方显式通知；source/外观上下文的重置、更新覆盖和主题/颜色策略由调用方负责。Sleepy Dock 的应用侧候选接线由 `LocalDockBackdropAppearanceEnabled=false` 默认关闭，验证 fixture 显式启用；五点与223.5/255、153.5/255仍是未校准工程选择，不成为库默认参数。此处更新源码与接口清单并保留无通知故障，不把93的host/GPU结果扩展到新原语，也不完成M3b/M4、支持/性能或发布验收。

无显式通知的94 retained-diagnostic已实测：A→B→C→D→C→B→A的14个REST观察中，source draw serial从3增至9，但`source_host_version`保持2、`luma_sample_count`保持2、亮度保持26/255。父层版本未覆盖retained叶后续更新；原捕获/坐标检查通过不等于采样持续更新通过。新signal测试、retained/光学结果和安装身份待补入`validation/observations/source-aware-sampling-94.json`，不能用这份旧观察或93结果提前声明新API通过。

现已建立 `:liquid-glass-runtime`，目录 `libraries/liquid-glass/runtime`；初版 runtime/material sample 已在独立仓库提供，完整控件示例后续在该仓库扩展。M1 使用现有 Kotlin Multiplatform + Android library 插件的 Android target，未同时引入平台迁移。首轮保留现 Kotlin 包名，减小调用变动；适配器落位时统一迁到 `com.lingion.liquidglass`，Sleepy 旧入口作为转调适配器保留。每个源文件只属于一个 module；不复制实现，不把 app 源目录同时登记给两个编译单元。

文中 `G/` 指 `app/src/main/java/com/lingion/sleepy/ui/component/liquidglass/`，`R/` 指 `libraries/liquid-glass/runtime/src/androidMain/kotlin/com/lingion/sleepy/ui/component/liquidglass/`，`T/` 指对应的 `app/src/test/java/com/lingion/sleepy/ui/component/liquidglass/`。阶段表列的是同包符号依赖与 imports 的静态闭包；最终以各阶段实际编译和原基线验证为准。

## 真实依赖顺序

| 阶段 / 源文件 | 必须同时具备的闭包、当前阻碍 | 阶段边界 |
| --- | --- | --- |
| M1 纯运动：`NativeGlassDragDeformation.kt`、`NativeDockDeformation.kt`、`NativeSliderEndpoint.kt` | 前者和 Slider endpoint 只用 Kotlin 数学；Dock deformation 只额外引用前者的 `Spec`。无 Android/Compose/app imports。 | 最小首批可构建 runtime；不包含控件、backdrop 或 shader。 |
| M2a 调度（已完成）：`ActiveFrameLoop.kt`、`DockInputClock.kt` | 两份完整调度闭包，只依赖 Compose runtime 1.12.1（api）和 coroutines-core 1.11.0（implementation）；不需要 Compose compiler、UI 或 shapes。 | 同包移动、五处必要可见性调整；保留唯一 `LocalDockInputTimeNanos`、真实帧/receipt 时间和队列顺序。四类原测试前后各33项通过。 |
| M2b 首批运动（已完成）：`LiquidGlassPhysics.kt`、`GlassControlMotion.kt`、`GlassDockMotion.kt`、`NativeDockParentMotion.kt` | Physics 的 `SpringState` 用 Compose state，`FlClashDockPhysics` 用 Compose `Rect`；两个 driver 直接写 internal velocity/isRunning，四文件已同迁 runtime。 | 保留四个 internal setter 与 internal ParentMotion；仅开放 11 处 app 调用/测试必需的声明和只读 getter。原 ParentMotion 测试及两份独占 CSV 已原样迁入标准 runtime host test，原 124 项前后均通过，AAR 唯一归属已验证。 |
| M2b 按压/端部（已完成）：`GlassPressMotion.kt`、`NativeDockEndpoint.kt`、`NativeSegmentedEndpoint.kt` | 只使用 runtime 已有公开 Physics/UI API；三源各移除一次顶层 internal，调用者/构建配置不变。 | 两 Endpoint 测试原字节进入标准 host test，依赖 app alpha helper 的 Press 测试留 app。原17项前后通过，AAR唯一归属和安装身份已核对；不改 press/endpoint 策略或原基线。 |
| M2 几何（构建/原单测/AAR归属已完成）：`R/LiquidGlassSuperellipse.kt`、`LiquidGlassGeometry.kt`、`LiquidGlassExactContour.kt`、`LiquidGlassDualContourShape.kt`、`FlClashGlassShape.kt`、`DockPlatterShape.kt`、`LiquidGlassOutlineProfile.kt` | ExactContour → Superellipse；FlClashGlassShape → 两者；DockPlatterShape → ExactContour、Kyant `Capsule` 和已从 Shader 提取到 runtime `LiquidGlassOutlineProfile.kt` 的 `LiquidGlassCachedOutlineProfile`。Superellipse 内含 Android Build/FMA 分支与 `android.graphics.Path` 回放，保留在 `androidMain`。 | 六源移动及缓存类提取只开放28处必要可见性，保留 FMA 分支、Float/Double 精度、路径/描述符身份和缓存所有权。原五类26项单测前后通过，该阶段runtime共19源；设备8项、安装及依赖解析已核对；首次错误runner的零项命令失败另行保留。 |
| M3a 渲染原语（已迁移）：`R/LiquidGlassStyle.kt`、`LiquidGlassShader.kt`、`LiquidGlassEffect.kt` | Style → Material3 `ColorScheme`、HCT、backdrop Shadow；Shader → 已迁几何、style、轮廓缓存；Effect → 几何/shape、shader/style、定制 `BackdropEffectScope.inputRenderEffect`。 | 三源只移除六处必要 internal，原 AGSL 同迁标准 androidMain assets；该阶段runtime共22源，原34项单测、AAR唯一归属与单资产已验证。保留现有定制 `:kyant-backdrop` 和 Material3 公开类型；不是无 Material3 core，也未迁 Host。 |
| 通用背景自适应前景（93 已新增）：`R/BackdropAwareForeground.kt` | 依赖公开 `Backdrop`、Compose GraphicsLayer/坐标及 API33 RuntimeShader；调用方显式给出响应端点、亮度区间、透明 source fallback 与局部采样矩形。普通 LayerBackdrop 仅处理相对平移；scale/rotation 须显式用 `withTransformCompensation()` 包装。 | 93当时runtime23源；5项host/2项GPU及AAR119类唯一归属通过，仅验证合成、平移/尺寸变化和重挂载。无生产Dock接入、原生分类器或默认门限；不视作M3b迁移，性能、变换硬件及完整光学未验收。 |
| 通用外部源亮度/通知/状态原语（94已新增）：`R/BackdropLumaSampler.kt`、`BackdropContentSignal.kt`、`BackdropAppearanceHysteresis.kt` | Sampler → 外部 `Backdrop`、Compose GraphicsLayer/坐标、UI线程快照和worker对独立bitmap的CPU读回；硬件bitmap先复制为ARGB_8888，不将source layer/DrawScope/坐标传给worker。保留consumer graphicsLayer隔离观察失效，避免同层source重录与revision回灌。直接 `LayerBackdrop` 可带recording revision，包装源（含 `withContentSignal`）的 `sourceRevision` 为null。Signal提供显式snapshot通知，不是完成录制的revision；Hysteresis仅依赖Kotlin标量状态和调用方门限。 | 当前runtime26源。冻结样本可作为有时间标签的历史观察交付，随后追赶较新source，不保证当前帧；身份/几何/配置变化淘汰不兼容结果，未变化的draw不轮询。API<28不采样；scale/rotation仍由调用方补偿，性能未验收。Dock候选默认关闭，五点/双阈/普通前景策略留在应用侧，新源码存在不代表M3b/M4迁移或原生验收完成。 |
| M3b 采样/主题上下文（待执行）：`GlassHost.kt`、`GlassLayerRecorder.kt`、`GlassFilterBands.kt`、`GlassBackdropSource.kt`、`GlassSurfaceSource.kt`、`GlassAndroidViewSource.kt`、`GlassMaterialBackdrop.kt`、`GlassSurface.kt` | Host ↔ FilterBands/MaterialBackdrop；Source/SurfaceSource → Host、Recorder、MaterialScope；MaterialBackdrop → Host 的 overlay sampling host **和 ControlModifier 文件中的 `LocalGlassOverlay`**。此外 MaterialBackdrop:93 读 `LocalUiStyle`，Host 的背景默认值读 MaterialTheme。这些不是仅凭“没有 app import”就能独立迁走的文件。 | 新建 `GlassMaterialContext.kt`，原样承接 `LocalGlassOverlay`、`LocalGlassGrouped`、`LocalGlassPlacementLayer` 及 `GlassMaterialRole`；MaterialBackdrop 的主题判断改为调用方传入的 `glassEnabled`，Host 接收明确背景色。采样状态闭包同批进入 runtime，不拉入控件分支。 |
| M4 控件与适配器 | 见下表。`GlassControlModifier` 依赖 M2/M3；`GlassButtons` 还引用 OverlayContent 的 alert action；`GlassOverlays` ↔ OverlayContent/MaterialContext；不能逐文件机械搬。 | 同一控件实现迁一次；Sleepy 保留业务内容、Material 路由和策略。 |

M2a 已增加匹配实际解析的 runtime `1.12.1` 和 coroutines `1.11.0`；M2b 首批已接入根构建的 Compose compiler `2.4.21`、UI `1.12.1` 及标准 host JUnit `4.13.2`。M2 几何新增 shapes `1.2.1` implementation，原阶段依赖图均保留。M3a 为公开签名增加现有 `:kyant-backdrop`、Material3 `1.5.0-rc01` 的 api，增加 Foundation `1.12.1`、`studio.lunabee.compose:material-color-utilities:1.10.0` 的 implementation，没有升级版本。实际 resolutionResult 的 compile67/runtime121组件无未解析项，上述依赖及 shapes/UI 均解析到现有版本，真实 `compileAndroidMain.libraries` 含 backdrop 编译 jar。初始两个只读诊断错误另行保留，未据此改依赖或重跑构建。当前 app 使用 Compose BOM `2026.09.00`，后续阶段仍须独立核对解析结果。

`BackdropContentSignal` 的调用方契约随采样源一起迁移：同一场景的源叶共享一个signal，`remember(source, signal) { source.withContentSignal(signal) }`保留wrapper，不能在每次重组时新建source身份。每个独立retained且排除光学消费者的源叶，须在自身graphicsLayer/clip边界内、绘制modifier之前放置`notifyBackdropDraw(signal)`；它只在本次`drawContent()`返回后通知，父节点无法代替独立retained子层的后续通知，也不会创建隔离或递归检测子树。layer属性变化和native绘制更新由源拥有方另行调用`notifyContentChanged()`，须在UI线程且对应更新已可供绘制后通知，不能在尚待绘制的状态赋值时提前通知。消费者禁止回灌通知；signal不代表像素录制完成或presentation时间。

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
- [x] M2 几何已补齐六源及轮廓缓存闭包；`LiquidGlassOutlineProfile.kt` 承接原缓存类，只开放28处跨module必要声明，lazy/path/descriptor身份判断、FMA/精度分支和路径公式不变。`SpringState` 的 internal setter、调用者、原单测/设备测试及AGSL资产均未改。
- [x] M2 几何构建、原单测与归属：before五类26项14.0007s通过；after AAR/debug/androidTest构建和相同26项43.9488s通过，零失败/错误/跳过，测试身份和哈希不变。该批runtime共19源，102个AAR类与实际2253个app生产类无交集；六份原app源不存在，AAR拥有其实现。完整命令和哈希见 [M2 几何记录](validation/migrations/m2-geometry.json)。
- [x] M2 几何本批独立 compile/runtime 依赖解析通过；API36 debug/test host-device哈希匹配，MainActivity resumed。原三类8项设备测试以注册的 `SleepyRenderTestRunner` 在1.286s通过；首次误用未注册默认runner的命令在0项测试启动时失败，原log/json单独保留，未改源码/测试/预期。原图形单测继续留app；独立Robolectric host接线另行验证。
- [x] M2 几何最终启动态：root目视核对 `android-launched-home.png`，可见第11周有课程的网格首页和Dock，hierarchy与MainActivity resumed另有记录；先前即刻splash截图单独保留。仅验证本批真实安装/启动可见，不新增数据内容、UI/光学或自然动态验收。
- [ ] 迁移前后原样复用 `T/LiquidGlassPhysicsTest.kt`、`DockInputClockTest.kt`、`GlassControlTimedInputTest.kt`、`GlassDockMotionTest.kt`、`NativeDockParentMotionTest.kt`、`NativeDockEndpointTest.kt`、`NativeSegmentedEndpointTest.kt`、`LiquidGlassSuperellipsePrecisionTest.kt`、`LiquidGlassExactContourTest.kt`，每批只运行对应改变的闭包。先保留 app 集成测试；独立于 app 的测试才逐步移动到 `runtime/src/androidHostTest`，不整包迁入 app theme 或 `SoftwareWindowComposeRule` 等 helper。首批 ParentMotion 只需 JUnit `4.13.2`。后续图形测试沿用现有 `withHostTestBuilder` 接线，按实际闭包补齐 app 当前使用的 Robolectric `4.17`、AndroidX Test Core `1.7.0`、匹配的 Compose UI test 依赖及 Android 资源支持，保留 NATIVE 图形模式/SDK 配置。只有调用者一起迁完才收回临时 public 可见性。
- [x] M3a 将 Style/Shader/Effect 三源移入 runtime，只移除 `shiftTone`、`LiquidGlassShader`、其嵌套 `Goo`、`liquidGlass`、`circularGlassCornerRadii`、`createLiquidGlassHazeAtlasEffect` 六处必要 internal；其余源码字节、包名、调用者、实参及原测试保持。原七类34项 before11.8416s通过，after AAR/debug/androidTest与同34项50.9080s通过，测试身份与源码哈希不变。
- [x] 原 AGSL 已移至 `runtime/src/androidMain/assets/shaders/liquid_glass.agsl`，采用 Android KMP 标准 source 布局并显式启用资源。app 的 `prepareRuntimeAssets` 未改；原 app 资产与旧 generated shader 已不存在。`LiquidGlassShader.shaderSource()` 和原测试仍打开同一 `shaders/liquid_glass.agsl` 路径；notice 独立库分发安排仍待完成，不扩大授权结论。

  ```kotlin
  kotlin {
      android {
          androidResources { enable = true }
      }
  }
  ```

  标准布局及开关依据 [Android KMP 插件资产支持](https://developer.android.com/kotlin/multiplatform/plugin)，已由89实际构建确认：AAR 和 APK 的 `assets/shaders/liquid_glass.agsl` 各仅一份，字节与原源文件相同。无需 `runtime/assets` 或额外 variant 目录登记；这仍不代替尚未建立的独立示例。

- [x] M3a AAR 的113类与 app 实际2242个生产类无交集，runtime共22源且拥有三份新实现，原app三源不存在。独立 compile/runtime 解析与实际 backdrop 编译输入已核对；错误诊断原样保留。API36 使用注册的 `SleepyRenderTestRunner` 运行原 `LiquidGlassHazeRenderTest` 三项5.3395s通过，该次主包/测试包 host-device哈希一致，第11周网格首页和Dock可见，仅作原测试及安装/启动范围证据。
- [x] M3a 署名证据路径元数据修正后仅重打包主包，2.4462s通过；最终主包重装后host-device哈希一致且MainActivity resumed。最终身份与原GPU运行包分开记于 [迁移记录](validation/migrations/m3a-rendering.json)，未重复原34项单测或三项GPU测试，本计划不固定主包哈希。
- [x] 93 新增通用 GPU 前景原语，当时runtime23源；按显式 source/alpha mask/response 分离验证同 source 更新、消费者平移/resize/detach/reattach。5项host及2项GPU通过，8张图共37个独立probe在原容差3内（最大误差1）；119个AAR类与2242个app生产类无交集，AGSL及原Style/Shader/Effect不变。仅文档KDoc在构建后补充坐标、采样源与小面积使用约定，构建时源哈希与当前哈希分开记录。完整 [93记录](validation/observations/backdrop-aware-foreground-gpu-93.json)保留初始负尺寸检查失败log及修复后XML；无原生分类器/默认门限/生产Dock接入，不代替M3b或近原生验收。
- [x] Batch94 sampler/content-signal/hysteresis and default-disabled Dock candidate are archived in the [94 record](validation/observations/source-aware-sampling-94.json): runtime/app builds,18 host checks,two API36 real-Activity tests,14 notified retained observations and16 multi-background captures pass their declared integration/readiness scope. Runtime26-source ownership is verified. Original retained-source and system-overlay failures are preserved. Full lifecycle/replacement,fallback,performance,production descendant notifications and native calibration remain pending; historical samples/nullable revisions are not presentation fences or a low-latency guarantee.
- [ ] M3b 落实 MaterialContext、显式背景色和 `glassEnabled` 注入，再迁 Host 采样闭包。`glassEnabled` 表示调用方是否选择玻璃样式，不能覆盖控件已有的 `enabled` 交互参数。保留 clear/haze 两个输入分支、effect padding、深度前缀、父材质 backing、popup 坐标、View dirty invalidation 与 layer 释放。
- [ ] M3b 继续复用 `T/GlassHostCaptureTest.kt` 及其 API31/35 图形基线，按采样变动闭包运行原 `GlassMaterialSamplingRenderTest`、`GlassMaterialSamplingMatrixCaptureTest` 与适用 haze 场景。host test 或本批三项 HazeRender 不替代完整屏幕光学、自然动态或性能结论。

## M4：控件到 Sleepy 适配器的收口

| 当前真实文件/依赖 | runtime 保留 | Sleepy 适配器保留/注入 |
| --- | --- | --- |
| `GlassControlModifier.kt` → theme locals、M2/M3；`GlassButtons.kt` ↔ `GlassOverlayContent.kt` 的 alert action；`GlassSelectionControls.kt` | `glassControl` 光学/按压、按钮/选择绘制、通用 alert action 状态；按闭包迁，不把 action 状态留成 runtime → app 依赖。 | `LocalUiStyle` 路由、`LocalDarkTheme` 解释；传入 `glassEnabled`、`dark`、颜色/内容。保留 Material 分支入口。 |
| `GlassSwitch.kt`、`GlassSlider.kt`、`GlassSliderThumb.kt`、`SliderVisuals.kt` | 玻璃 Toggle、单双 Slider 交互/映射/绘制，thumb metrics、M2 时钟；保留当前可取消手势和可访问语义。 | `materialSliderGhostX` 来自 `IntRangeSlider.kt`，`sleepyPopupEnter/Exit` 来自 `SleepyMotion.kt`，属于现有适配依赖：Material 分支留 app；值提示层从调用方接收 enter/exit transition。`GlassGestureOwnership.kt` 中 Material SliderState 函数留适配器，通用 `glassGestureOwnership` 单独进入 runtime。 |
| `LiquidGlassNavigationBar.kt` → `PillNavItemSpec`、`NavIconDriver*`、`NavPartIcon`、`selectionColors` | Dock 布局、光学双绘制通道、M2 运动/端部响应、选择语义；复用通用source采样、显式通知和状态原语。 | 候选当前默认关闭；启用策略、完整源叶/layer属性/native更新通知、五点区域、门限/初态/重置、主题fallback及内外前景角色由调用方注入，不得提前通知或把当前Dock工程门限写成通用默认值。从 `PillNavigationBar.kt`、`NavIconMotion.kt`、`SelectionColors.kt` 组装 item 内容、内外绘制 slot、颜色、选中回调；不把导航路由/图标驱动整体迁入库。 |
| 包外 `app/src/main/java/com/lingion/sleepy/ui/component/SegmentedSwitcher.kt` | 玻璃分段控件的指针/时钟、轮廓、渲染与选择实现；不能把整文件标成适配器。 | `dbgTagPrefix`、选项文案、`SleepyTheme`、`LocalUiStyle` 和 Material 路由留 app；用同一库控件转调。 |
| `GlassLanguageMenu.kt` → `SettingsLayout`、`sleepyExpandSpecs`、`SleepyTheme`、`dbgTag`；另用 `LanguageMenuMotion.kt`、`LanguageMenuGestures.kt` | 通用菜单 anchor、长按/滑动选择、morph、popup/source handoff、绘制；后两文件和其语义/时间模型同迁。 | 语言 code/label 数据、设置行布局、展开策略、持久化回调与调试标签。用 item slot/选择回调承接，不在库中创建语言菜单专用业务入口。 |
| `GlassOverlays.kt`、`GlassOverlayContent.kt`、`GlassTopAppBar.kt` → theme locals，M3 popup/material 状态 | 通用 menu/dialog/sheet/app-bar 的玻璃 backing、共享采样和控件布局。 | 样式切换、Material 对应路由、主题输入与应用页面内容；不要把 Material fallback 路由误当成 RuntimeShader 不支持时的光学降级。 |
| `LiquidGlassHaptics.kt` | 可选的 Android 触觉实现可随库提供，保持平台 API 路径。 | 所有选择入口注入触觉回调/策略；库不替 app 决定权限、强制反馈或设置策略。 |

- [ ] 每个控件完成上述注入后，才将实现迁入 runtime；Sleepy 旧入口在原命名空间转调，新库命名空间统一为 `com.lingion.liquidglass`。同一阶段同步正常调用、测试和 example imports，避免旧包又生成一份实现。以 `rg` 检查 runtime 不再 import `com.lingion.sleepy`，且 Gradle 不依赖 `:app`；同包隐藏调用也须通过编译暴露。
- [ ] 控件回归使用原 `GlassSwitchQuickInputTest`、`GlassSelectionCoalescedInputTest`、`GlassSliderTimestampInteractionTest`、`GlassSliderEndpointInteractionTest`、`GlassDockInputContractTest`、`GlassDockParentTranslationContractTest`、`DockAccessibilityTest`、`GlassNestedPopupTest`、`GlassLanguageMenuTest`、`LanguageMenuMotionTest` 的对应场景。保留实际 receipt/sample/frame 时间、取消、disabled、RTL 和 reduced motion 语义，不改测量门限来通过迁移。

## 独立示例与实验性公开

- [ ] 在已公开的独立 sample 中继续接入完整控件，只依赖库 runtime 及自身 Activity/Compose 引导，不依赖 Sleepy 的 `:app`、theme、资源或测试 helper。由 sample 自己提供颜色、item 内容、回调和触觉策略。待 Sleepy 切换独立库时，核对应用与 sample 消费同一份 runtime 和 shader。
- [ ] 示例展示 Toggle、分段、Dock、单双 Slider、按钮、menu/dialog/sheet 在网格、彩条、明暗色块和纹理背景下的相同输入回放。编译 sample/AAR 通过后，独立运行验证首次加载 shader、降级、取消、焦点/无障碍与动态选项变化。不能仅用“最小模块 assemble 通过”替代此 gate。
- [ ] 对每次迁移保留同一控件、输入脚本、背景、fixture 哈希、测量代码、误差门限及 before/after 结果；可分发数值基线最终移动到唯一的 `libraries/liquid-glass/validation/baselines/native-glass`，相关测试通过资源接线消费，避免复制成多套。设备原视频和离线采集工具仍不属于 Android runtime。
- [x] 独立实验性 runtime 与 sample 已公开至 `Kerry1020/sleepy-liquid-glass`，`main` 为 `ac9254c6e36312c6ef35c99ed28a881818bbd097`，Git 与 API 身份一致。采用官方 Kyant 2.0.0 并排除 SleepDown 定制源码；独立 38 项 host 测试通过，sample 四背景 × 明暗主题已在真机查看。远程全新 clone 的 runtime AAR、host test 和 sample debug 构建也已通过，不把本项等同于完整控件或近原生验收。
- [ ] 继续完成完整控件、逐控件近原生、实际支持范围/性能和稳定 API 验收。catalog 的 `publicationReady`、`nativeCalibrationAccepted` 和 `sourceAndLicenseReviewComplete` 保留为完整资产库验收状态，与实验性公开状态分别记录。Sleepy 后续切换到独立库时才验证后端和调用适配，不用一次实验性发布宣称应用已完成切换。
