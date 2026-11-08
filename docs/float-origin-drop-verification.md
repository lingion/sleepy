# 课程拖回原位后的浮窗闪现

日期：2026-11-08。

用户要求：长按课程、拖离再回到原位置并松手，应直接结束拖拽，不应短暂出现操作浮窗。

## 原因与修复

`commitFloatDrop()` 原来先设置 `dragging = false`，随后调用异步课程处理。即使位置没有改变，处理器仍需读取课程快照再返回完成结果。在此期间 `active && !dragging` 成立，操作浮窗及背景遮罩会重新进入，随后又被完成回调关闭。

现在在修改拖拽状态及调用保存管线之前，比较落点与长按时的显示位置快照：周次、星期、开始节次、跨度均相同则同步 `dismiss()` 并返回。这样不会进入“仍浮起、但已停止拖拽”的中间状态，也不需要查询或写入课程。

比较使用显示位置快照，适用于投影后的课程。跨周同格或跨度变化仍提交原移动流程；仅长按不移动仍显示操作浮窗，取消真实移动后的弹回行为保留。本次没有修改浮层 zIndex、裁剪、模糊或动效规格。

## 验证

新增两种真实触摸路径：连续长按拖离后回原位，以及重新抓住已浮起课程再拖回原位。回调故意不立即完成，以暴露原先依赖异步完成关闭浮窗的问题。原实现两项测试失败；修复后逐帧检查松手后的 24 帧，操作浮窗和背景遮罩均不存在，最终浮卡关闭且未调用保存回调。

另增加跨周同格仍提交移动、改变跨度后回原起点仍提交新跨度的行为测试。

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests '*CourseOccurrenceFloatDragTest' `
  --tests '*FloatDragRegressionTest' `
  --tests '*FloatOccurrenceHandlerTest' `
  --tests '*CourseOccurrenceGestureTest' `
  --tests '*FloatLayerMotionContractTest' `
  --tests '*EdgePagingContractTest' `
  --tests '*ScheduleWeekPagerTest' `
  :app:assembleDebug --console=plain
```

结果：104 项通过，0 失败、0 错误、0 跳过，Debug 构建成功，`git diff --check` 通过。

日志：`build/float-origin-red.log`、`build/float-origin-verification.log`。修复包已成功更新到连接设备的 `com.lingion.sleepy.debug`；尚未取得本轮真机手动复测反馈。上述 Compose 自动化使用 Robolectric API 35。

## 提交前整体验证

三项修复一起执行 `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain`，结果 `BUILD SUCCESSFUL`。

- 全量单测：3766 项，3763 通过、3 跳过，0 失败、0 错误。跳过项为已有的两项 `WakeUpLiveCrossVerifyTest` 和 `SgAssetVendorInvariantTest.manifest_covers_full_shiguang_gap_locally`。
- lint：通过，0 错误；仍有告警，未新增豁免或修改 lint 配置。
- Debug 构建和 `git diff --check` 通过。
- 本机完整日志：`build/fix-pr-verification.log`。
