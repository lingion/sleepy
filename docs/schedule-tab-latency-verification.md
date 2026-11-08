# 切回课表的短暂停顿

日期：2026-11-08。用户确认仅从其他主标签切回课表存在延迟，切到今日、课表管理、我的没有同样问题。

## 修改

点击回调直接设置当前标签，没有人为等待。课表重新进入组合树时，原来的 `beyondViewportPageCount = 1` 会在首帧同时构建当前周和左右两周的完整课表。

- `ScheduleWeekPager` 在普通切页时仅构建可见周，由 Pager 自身处理滑动期间的可见页和方向预取。拖课期间恢复左右邻页的预构建，原手势来源页固定机制继续保留。
- 设备主线程采样还发现表头文字测量重复执行。现在主标签容器持有最多 128 项的 `TextMeasurer` 缓存，供网格列宽、对齐轴和节次单元格复用，离开再切回课表时继续复用。字体解析器、密度／字体缩放或布局方向变化时重建缓存。
- 不修改课程数据库、周次选择逻辑或转场规格。页面仍正常离开组合树，原保存状态及短期状态的生命周期保留。

## 真机测量

设备：PLC110，Android 16 / API 36。使用已安装的 `com.lingion.sleepy.debug`、同一份现有课表和同一网格视图；底栏为普通贴底导航。安装各版本后先完成三组标签往返预热，再分别从今日、课表管理、我的切回课表，各测三次，共九次。

每次在来源页稳定后执行 `dumpsys gfxinfo com.lingion.sleepy.debug reset`，点击课表，等待 0.9 秒后读取 `framestats`。统计该次转场内 `Flags = 0` 的最长帧，耗时为 `(FrameCompleted - IntendedVsync) / 1e6`。

| 指标 | 修改前 | 最终版本 |
| --- | ---: | ---: |
| 九次转场的最长帧中位数 | 211.80 ms | 105.50 ms |
| 九次最长帧的最小／最大值 | 151.46 / 250.48 ms | 83.35 / 113.45 ms |

中间版本仅取消首帧邻页预构建，中位数为 115.17 ms。方法采样用于寻找剩余热点，采样期间的结果不计入上述对照。

上述数据是 Debug 包的单设备转场帧耗时，不是精确的触摸到首帧延迟，也不是所有设备上的零掉帧保证。各版本顺序测量，存在 JIT、温度及系统调度波动，不将缓存单项的差值当作严格因果性能结论。

最终版本已安装到连接设备。自动切页测试停止后，用户手动体验反馈：**“已经基本感觉不到”**。

原始资料（本机 build 目录）：

- `build/schedule-tab-perf/baseline/`：修改前九次 framestats 与 summary.json。
- `build/schedule-tab-perf/selected-week-only/`：仅分页调整的对照。
- `build/schedule-tab-perf/shared-text-cache/`：最终版本对照。
- `build/schedule-tab-perf/sample.trace`：主线程方法采样。
- `build/measure_schedule_tab.py`：测量脚本，从设备 UI 层级定位主标签后执行往返；仅用于该次已连接设备。

## 自动化验证

最终相关测试 **78 项通过，0 失败、0 错误、0 跳过**，Debug 构建成功。

- `ScheduleWeekPagerTest`：运行真实分页容器和两种课表视图；首次进入及返回仅组合当前周，双向滑动正常，拖课状态下左右邻页存在。
- `TimetableTextMeasurerTest`：超过默认缓存容量的实际文字测量跨标签销毁复用；字体缩放与布局方向变化后不复用旧几何。
- `FloatDragRegressionTest` 改为使用生产分页容器，原始拖课和重新抓取浮动课程跨周测试均通过。
- 网格浅／深色像素基线、表头布局、边缘翻页、课表状态恢复及标签转场中间帧回归均通过。

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests '*ScheduleWeekPagerTest' `
  --tests '*TimetableTextMeasurerTest' `
  --tests '*FloatDragRegressionTest' `
  --tests '*EdgePagingContractTest' `
  --tests '*ScheduleReturnRestoreRaceContractTest' `
  --tests '*ScheduleViewModeSessionContractTest' `
  --tests '*TabContentTransitionPixelTest' `
  --tests '*MainTabsTransitionContractTest' `
  --tests '*PeriodHeader*Test' `
  --tests '*CardsGridViewGoldenTest' --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
```

日志：`build/schedule-tab-verification.log`、`build/schedule-tab-build.log`。本阶段只运行相关测试，提交前的全量单测和 lint 结果见[整体验证](float-origin-drop-verification.md#提交前整体验证)。未单独测量普通翻周／上下滚动的帧时间。真机数据来自网格视图，两种视图的行为覆盖来自 Compose 测试。
