# 全应用玻璃实现与验证记录

## PR #299 修订状态（2026-11-04 验证完成）

本次修订基线为 `4822a58796deb81568ef4eb41738236a84c26220`，工作区为 `pr299-liquid-glass-fixes`。下方初版的 3601 项及 GPU 结果是历史证据，不代表本次改动已通过同样验证。

- 半屏弹窗：Material 在外部 modifier 之后应用拖动位移，原玻璃裁切层停在原点，短内容会被裁掉。现在在录制/玻璃层两侧补偿相反 offset，保持 Material 测量、手柄、关闭及 inset 处理。
- 宽大操作(2026-11-04 用户再裁定, 修正 119e1150 的一刀切): 编辑/应用/保存/新增这类操作键一律玻璃化; 保持扁平的只有明显跳转二级页或展开面板的宽列表入口(教务条目行、导入方式行、导出行、迁移导出/导入、假期规则跳转、绑定选项行)。索引条字母不加玻璃。禁再把操作键当"宽大入口"扁平化, 也禁把导航入口玻璃化。迁移操作同时尊重 `migrationBusy` 禁用状态。
- 语言：当前值锚定独立 Popup，整块玻璃承载普通选项行；采用展开弹簧 `400/.754`、延迟 70ms 的圆角弹簧 `324/.75`，退出后再提交选择。
- Toggle：52×32 实心轨道恒定; 滑块静止 24 近圆, 行程中段随 `sin(π·on)` 加宽, 按住/拖动再 +16 泡成胶囊(高不随 lift 长, 旧 40×38 近方形已废), 形状 RoundedCornerShape(50) 静止即圆、加宽即胶囊; 按压镜片组合采样父场景与本地轨道; 支持横拖、取消、受控回弹、禁用、键盘和 RTL。iOS 26 定标: 加宽的是滑块镜片而非轨道。
- 分段 Tab：高亮位置为连续 Float，从高亮块起拖，释放最近吸附并提交一次选择。拖动中高亮块浮起: `dragging` 驱动 lift(tween 240), 滑块 scale 1.155 + 0→8dp 托影, 松手回落; 轨道不 clip, 圆角由带形状 background 自绘。FlClash 原分段只有点击，此处拖动与浮起是 Sleepy 的明确扩展。
- 本次参考为 FlClash `0.9.2+2026102401`，调用链和指纹见 `flclash-pr299-revision-reference.json`；初版 manifest 保留原始来源。

### 本次验证记录

首轮定向命令覆盖 `GlassBottomSheetTest`、`GlassNestedPopupTest`、`GlassLanguageMenuTest`、`GlassSwitchTest`、`SegmentedSwitcherDragTest`。结果为 30 项中 29 项通过：弹窗 4/4、嵌套 1/1、Toggle 13/13、Tab 5/5、语言 6/7。语言失败发生在 Popup 加入语义树前冻结时钟；已增加打开后可见性等待，保留退出等待及重复提交断言，等待复跑结果。

全量日志 `.superpowers/sdd/2026-11-03-pr299-controls/full-verification.log` 记录 3630 项、2 failures、3 skipped；语言行为测试 7/7 通过。失败的两项是 `ExpandChevronMotionContractTest` 强制旧语言内联折叠卡/180°箭头的源码契约。用户已明确批准将语言断言迁移到新浮框契约；其他折叠站点断言保留，迁移后待复跑。该轮没有完整 BUILD SUCCESSFUL，不能据 packageDebug 输出宣称构建及 Lint 全部通过。

用户追加返回按钮拖拽遮挡问题：对照实际 Material3 1.5.0-beta02 源码，`TopAppBarLayout` 被 `clipToBounds()` 包围；Scaffold 已将顶部栏放在正文之上。最终 `GlassTopAppBar` 由 Material 提供槽位尺寸/位置，实际导航与操作按钮只组合一次，放在标题裁切层之外；27 个页面入口已接入。保留 Material 的颜色、inset、RTL 与原按钮语义，未提升普通玻璃控件的全局层级。

像素验证的中间失败也保留：最初取样在栏底+2px，而诊断显示夹具纯红前景延伸到栏底+1px，导致两种修正方案均被该取样点判失败。用户明确批准将取样点校正到栏底外第一行，精确颜色及其他断言不变。随后用原 Material 转发重新运行，三项越界断言仍失败（`topbar-corrected-red.log`）；恢复最终布局方案后，导航/RTL/右侧操作、前景更新、取消回弹和单次回调四项全部通过。第一版图层重放方案未用校正后的取样点复测，不据旧失败推断它的裁切行为。

### 最终全量结果

- 定向验证 35 项全部通过（`topbar-final-focused.log`），含顶部栏、原有玻璃层遮挡、语言交互/迁移契约及 Tab 两项选择/反向拖动/空选项边界。
- 普通全量命令曾被本机 OpenJDK `21+35-2513` 的 ARM64 C1 编译器中止，`assembler_aarch64.hpp:264: Field too big for insn`，编译任务指向 `Recomposer$runRecomposeAndApplyChanges$2::invokeSuspend$lambda$2`。这次为 JVM 崩溃，不是断言失败；证据见 `final-verification.log` 与 `app/hs_err_pid79421.log`。
- 仅为验证进程设置 `JAVA_TOOL_OPTIONS='-XX:CompileCommand=exclude,androidx/compose/runtime/Recomposer$runRecomposeAndApplyChanges$2.invokeSuspend$lambda$2'` 后，原全量任务 `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` 返回 **BUILD SUCCESSFUL**，耗时 17m32s。未改项目 JVM 配置，也未跳过测试任务。
- XML 汇总 **3638 项：3635 通过、0 failures、0 errors、3 skipped**。三个跳过项为已有的两项 WakeUp 活体/握手验证和一项本地时光资产覆盖检查，新增回归测试均执行。
- Debug APK 构建成功；Lint **0 Fatal / 0 Error / 556 Warning / 38 Hint**，不能称为零警告。其中 `GlassSwitch.kt` 有状态驱动非 lambda offset 的性能建议。
- 全量日志：`.superpowers/sdd/2026-11-03-pr299-controls/final-verification-jit-workaround.log`。
- ARM64 APK：`app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`；通用 APK：`app/build/outputs/apk/debug/app-universal-debug.apk`。

本次本地验证没有可用设备：模拟器安装失败（`Error on ZipFile unknown archive`）。Robolectric Native 像素与几何测试不能替代 GPU 折射或真实 `OccurrenceEditor`、输入法、逐页入口的验收。本节为提交前验证记录；后续远端状态见 PR #299。

## 初版历史状态

本地分支 `feat/app-liquid-glass` 已保存共享材质、全局风格、公共按钮、选择器、弹层与页面接线，并同步已合入 main 的并行修订。没有推送、创建 PR、合并或发布。

这份记录证明代码与下列自动化验证的状态，不构成全设备或逐页一比一视觉验收。覆盖表中 183 条包含边界项、动态控件族和公共控件引用，不等于 183 个已经逐一目验的独立按钮。

## 对照与实现

- 固定本地 FLClash 0.9.1+2026101702 资产，来源指纹见 `flclash-reference-manifest.json`。
- 返回与工具栏单图标使用 regular，主动作和选择态使用 tinted，组合动作共用玻璃壳；不把所有材质替换成 Dock pill。
- 分段和内容操作的全量玻璃化属于用户明确选择的 Sleepy 延伸，参考中对应控件部分仍为普通表面，不能标成原样一比一。
- “Dock 主题”六语言文案改为“界面风格”；颜色、深浅模式、底栏形态保持独立。新键优先、旧选择兼容且无双向回写。
- 背景采用显式普通来源和严格前序层级，玻璃前景保留原树；没有使用每帧整页图片回读或提升所有玻璃前景。
- 弹窗读取已完成的直接父场景，嵌套菜单读取父弹窗而非继续读取最底页面；同一来源和模糊半径共享过滤资源，首次需求会驱动宿主绘制。
- 日期弹窗光学轮廓和 Material 载体使用同一个超椭圆形状；普通课程大卡保留原按压反馈，只有显式玻璃操作使用对应弹性反馈。

## 初版历史验证

主线同步基准：`035589c4`；PR 289 查询时仍为 OPEN，未将其未合入分支夹带进本任务。

命令：`./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain`。

结果：3601 项测试（增加多 SDK 与导航契约），0 failures，0 errors，2 skipped；debug 构建成功；lint 为 0 Fatal、0 Error。

一次较早全量运行曾出现已有 ClockHandlerTest 的回包时间相差 1 毫秒失败。未修改时钟生产逻辑；该类单独复查及后续完整验证通过。不能把这次中间失败从验证记录抹去。

收尾波次补充：
- 抽取 `recordGlassLayer` 隔离 `LayoutNodeDrawScope` 的 density delegate，防止嵌套弹窗采样递归。
- 接入 `BindGlassAndroidViewSource` 提供原生 AndroidView（如 WebView）的重绘代际失效通知。
- 补齐主导航 Tab 贴底栏的胶囊指示器玻璃化及二级页（浮动卡片、提醒、设置导出导入、添加课程）的玻璃控件接入。

Android 14 模拟器 `emulator-5560` 的三项真实 GPU 测试在最终主线同步 APK 上实际执行通过：

1. 相邻不同 tint 的玻璃不会互相覆盖 shader uniform。
2. 普通来源颜色变化会传入现有光学消费者。
3. 选中分段与背景轨道有不同的材质输出。

命令以明确设备序列号执行 `am instrument`，返回 `OK (3 tests)`，没有在连接的物理手机上运行。

相关 JVM 像素和交互验证覆盖：前景遮挡顺序、后画半透明内容不进入行内前序源、来源更新、同深度不重叠来源动态注册、首个延迟过滤请求、直接父弹窗选择、禁用动作、单次回调、横竖弹窗动作顺序，以及风格切换后编辑草稿和组合位置保留。

原来源污染红测取证曾先遇到未录制层零尺寸异常，后来补实际帧触发后才取得有效的颜色断言失败。修复后保留原绿色像素预期，改为显式声明前后背景来源，验证通过；没有删断言、放宽为混色或跳过测试。

## 仍不能宣称的结果

- 尚未逐页与 FLClash 在全部静止、按压、松开、禁用、深浅、滚动和弹层状态做完整同条件视觉对比。公共 GPU 测试不能替代这项验收。
- 行内来源必须由拥有层级和裁剪语义的容器明确登记。未登记来源只使用明确主题底色，不能说未登记的页面内容也已发生折射。
- 当前原生 API 33 以下路径仍不能当作 FLClash Flutter 的完整折射管线。既有旧设备和新设备 Dock 问题未据此宣布全部解决；没有新增 Flutter/JNI 后端或隐藏 API，也没有修改设备策略。
- 平板、特殊 ROM、复杂窗口变换和实体设备的完整表现仍需对应设备证据。没有操作用户华为手机。
- 课程大卡、设置大卡、输入本体、系统权限和文件分享界面、学校网页内部、桌面 RemoteViews 本体保留范围边界。

## 用户原话与实现行为

“先对照着 FLClash 软件资产逐条核对，再应用过来”：已有参考来源、材质参数测试、对应说明、逐项台账和实现；台账未把尚未目验的项目写成视觉通过。

“全量玻璃化”：独立小操作、分段和小选择块按参考机制接入；参考原本非玻璃的部分明确标为延伸。必要的菜单和弹窗载体接入，不擅自把内容卡和输入本体一起换材质。

“主题设置就不是 Dock 主题了”：界面风格已独立于配色和导航形态，兼容已有 Dock 材质选择，切换状态不会通过另建业务树丢失编辑内容。

## 本轮实现判断

采用显式有序来源而非透明拦截任意 Compose 后代；原因是后画透明内容污染的真实红测证明整页近似不成立。代价是每个需要内容采样的容器必须准确声明来源与层级，不能仅接入按钮函数就标记视觉完成。

保持旧版 Android 的真实能力记录，不以平台 API 门槛冒充参考也降级。本轮不自动引入新的渲染运行时；若需要扩大后端范围，应先有独立技术验证和明确授权。
