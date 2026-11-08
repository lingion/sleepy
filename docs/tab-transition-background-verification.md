# 主标签背景透叠修复验证

日期：2026-11-08。

## 实现

- `MainTabs` 和横屏 `RightHalfContent` 使用各自的生产转场容器，内部通过 `TabPage` 提供 `fillMaxSize()`、`MaterialTheme.colorScheme.background` 背景。背景随页面一起缩放。
- 两处均显式设置 `targetContentZIndex = 1f`；主标签保留 `SizeTransform(clip = false)` 和四个原有 `SaveableStateProvider`。
- 中间帧测试发现：仅补背景和固定层级，普通双向切换通过，但快速反向切换仍失败。切回未退出的页面会保留部分退出透明度。
- 因此标签的退出透明度改由 `TabPage` 使用原 `defaultEffectsSpec` 驱动；成为目标页时立即使用 alpha 1。`sleepyContentExit(includeFade = false)` 继续提供原退出缩放，其默认调用仍保留缩放加淡出。未修改 `sleepyContentEnter()`、子页面转场调用、网格模糊、裁剪或构建优化配置。

透明度反转行为可对照 [AndroidX EnterExitTransition 的实现](https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/src/commonMain/kotlin/androidx/compose/animation/EnterExitTransition.kt)。本次修正以仓库实际依赖运行的失败／通过像素测试为依据。

## 已完成自动化验证

`TabContentTransitionPixelTest`：Robolectric NATIVE graphics、API 35，共 5 个测试通过。

- 主标签“课表／今日”和横屏右侧“今日／管理”，分别覆盖浅色、深色主题。
- 使用生产 `MainTabContent` / `RightHalfTabContent`，注入稀疏标记页。手动推进动画，每次取样确认新旧两页均仍在组合树内。
- 验证双向切换及连续快速反向切换：目标页标记保持原色，目标页空白区域的整块像素等于主题背景色，不能出现旧页标记。
- 主标签滚动状态探针：页面完成退出并被移除后，两页各自的滚动位置仍可恢复；快速切回也不丢失。
- 首版快速反向切换的 4 个像素用例均失败，补充透明度处理后通过。不是只检查动画结束后的截图或源码字符串。

相关回归和构建命令（PowerShell）：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests '*TabContentTransitionPixelTest' `
  --tests '*MainTabsTransitionContractTest' `
  --tests '*BackRestoreSaveableContractTest' `
  --tests '*TabletMasterDetailContractTest' `
  --tests '*NavHostMigrationContractTest' `
  --tests '*ScheduleViewModeSessionContractTest' `
  --tests '*ScheduleReturnRestoreRaceContractTest' `
  --tests '*SleepyMotionTest' `
  --tests '*StateSwapMotionContractTest' `
  --tests '*ScheduleMotionContractTest' `
  :app:assembleDebug --console=plain
```

结果：**80 tests，0 failures，0 errors，0 skipped；BUILD SUCCESSFUL**。

- 日志：`build/tab-transition-verification.log`
- 测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`
- 通用 Debug 包：`app/build/outputs/apk/debug/app-universal-debug.apk`
- `git diff --check` 通过。本阶段只运行相关测试，提交前的全量单测和 lint 结果见[整体验证](float-origin-drop-verification.md#提交前整体验证)。

## 尚未完成的真机验收

背景修复阶段 `adb devices` 无设备。后续已连接 Android 16 设备完成[切回课表延迟的对照测量](schedule-tab-latency-verification.md)，但下面的完整视觉验收清单尚未逐项完成。上述像素测试使用生产转场容器加测试内容，并不替代完整业务页面的真机验收。

- [ ] Android 16，关闭玻璃底栏：浅／深色主题下“课表／今日”往返及快速反向切换。
- [ ] 课表两种视图分别检查：新页内部不得透叠，缩放边缘允许看到正常退出中的旧页。
- [ ] 两页实际滚动位置和课表视图模式在往返后恢复。
- [ ] 横屏右侧标签切换的背景覆盖和层级。
- [ ] 动画结束后独立检查左右翻周、上下滚动。

普通滚动卡顿未在本次修复或验证。若真机仍卡顿，应单独采集帧时间（例如 Perfetto / FrameTimeline）定位；本次背景修复不作为性能修复结论。
