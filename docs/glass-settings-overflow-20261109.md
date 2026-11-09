# 设置分段镜片的裁切修复

## 布局不变量

卡片位置、内边距、标题和选项的顺序不变。背景继续由 glassSurfaceBackground 按原圆角绘制。
液态镜片可以越过背景边界。经典模式保留原来的外层裁剪。点击与选择语义不变。

```text
320dp 页面
  16dp | 288dp 卡片背景 | 16dp
           16dp | 256dp 分段轨道 | 16dp
                       按住横拖的镜片可以越过背景边界
```

SettingsFlatCard 的整棵内容树原来被 clip 裁剪。分段镜片在横拖时先升起，再按现有弹簧拉伸。
256dp 双段轨道的段宽为 124dp，运动时镜片可能超过卡片右侧 16dp 的背景内边距。
修复仅取消液态玻璃模式下这层祖先裁剪。背景已经按形状绘制，不需要裁剪子控件来获得圆角。
不修改 SegmentedSwitcher 的光学、物理参数或尺寸。

## 验证记录

SettingsFlatCardOverflowTest 使用真实卡片、真实分段器和实际按住横拖手势。
测试必须先观察到镜片越过卡片至少 2dp，再比较未裁剪边界与可见边界，避免未触发溢出时误通过。
旧实现运行一项测试，实际失败于裁剪断言：镜片完整右边界为 306.6455dp，可见右边界为 304dp，卡片右边界也为 304dp。
这次运行复现了约 2.65dp 的裁切，不是只比较源码或复算弹簧公式。
修复后运行以下定向命令，六项测试通过，零失败、零错误、零跳过。

```sh
./gradlew :app:testDebugUnitTest --tests com.lingion.sleepy.ui.component.SettingsFlatCardOverflowTest --tests com.lingion.sleepy.ui.component.SettingsLayoutTest --tests com.lingion.sleepy.ui.component.SegmentedSwitcherDragTest.classicModeDoesNotTakeOverDraggingOrFloatTheThumb --offline --max-workers=2 --console=plain
```

新增一项测试验证真实卡片的横拖镜片完整可见。已有四项设置布局测试和一项经典模式手势测试继续通过。
没有运行全量测试、模拟器或设备测试。未验证设备像素与真机表现。

## 用户要求与实现核对

| 用户要求 | 实现行为 |
| --- | --- |
| 液态组件浮起时应保持完整，保留整体页面布局 | 仅液态模式移除整卡裁剪；背景形状与经典模式裁剪保持原实现 |
| 保留现有果冻和折射效果 | SegmentedSwitcher、GlassControlMotion 与 LiquidGlassPhysics 相对基线逐字节不变 |
| 组件之间的大小和间距保持统一 | 卡片与轨道的尺寸和16dp内边距不变，只解除对浮起镜片的裁切 |
