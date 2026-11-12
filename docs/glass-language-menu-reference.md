# iOS 原生语言菜单参考

2026-11-12 在 iPhone 17 Pro 模拟器、iOS 26.5（23F77）实测。窗口为
402 × 874 pt、3 倍像素，系统报告最高 60 Hz。参考工具位于
[`tools/native-glass-reference`](../tools/native-glass-reference/README.md)。
这是一次特定设备和系统版本的观察，不能把模拟器回调率解释为真机显示刷新率。

## 原生组成与采样方法

参考 Sleepy iOS `GeneralSettingsScreen.swift` 的 `LanguageSection`：
`Menu` 内放 `.inline` 样式的五项 `Picker`，标签为完整设置卡片，使用
`.menuStyle(.borderlessButton)` 和 `.buttonStyle(.plain)`。五项依次为简体中文、
繁體中文、English、日本語、Español。不同语言异步提交到主队列；选当前语言
直接返回。参考程序只更新状态，不重建产品语言环境；标签颜色使用系统语义颜色。

参考程序通过公开 `UIApplication.sendEvent` 观察实际输入，通过
`CADisplayLink` 读取 `CALayer.presentation()`、坐标变换和公开动画属性。
只把内部视图类名用作观察标签，没有使用私有 KVC、方法或选择器，没有修改系统动画。
XCUITest 请求波形和实际投递的触摸分开保存，数据不插值。

Schema 2 的 `captureStartT...captureEndT` 包围一次顺序遍历，单位为相对
`originUptimeSeconds` 的秒；同一帧内各视图的读取时刻不同。通用 `t` 是稍后的
入队时刻，`displayTimestamp` 可能早于回调，二者都不能代替读取起点。
本次历史记录为 schema 1，以下时间使用 `t − captureCostMs/1000` 估算读取起点，
无法消除遍历结束到入队之间的开销。因此保留原始帧及成本，避免宣称毫秒级精度。

## 几何与视觉层

| 对象 | 稳定或起始几何（pt） | 应如何解释 |
| --- | --- | --- |
| 设置卡片锚点 | x20、y208、362 × 51⅓ | 标签的完整可点击区域 |
| 菜单稳定状态 | x76、y208、250 × 230 | 五行各 42，上下各 10 |
| 内容 portal | 固定 bounds 250 × 230；起始可见矩形约 25⅔ × 23.61 | 实际菜单内容缩放、淡入，最大约 1.02546 倍 |
| SDF 轮廓 | 起始约 25⅔ × 25⅔；形状 bounds 由 250 × 250 到 250 × 230 | 玻璃外形与内容矩形独立变化 |
| 外部 `_UIContextMenuView` | 可在动画开始就处于最终 250 × 230 | 不能拿静态容器代替运动内容 |
| 内部 `UICollectionView` | AX 可报告全屏高 874 | 是滚动容器，不是画出来的菜单高度 |

`_UIMagicMorphTransformView` 负责祖先透明度和匹配变化，`TransformView`
承担纵向位移。portal 局部 alpha 为 1 不代表最终内容全不透明。导出的
`effectiveOpacity` 只乘 UIView 祖先的 presentation opacity，不包括额外
CALayer 祖先、遮罩或材质着色器覆盖。SDF 的 alpha 和白色可见面积也不是同一个量。

本次内容缩放从约 0.1026667 起步，伴随内容透明度上升，弹性超过 1 后回落。
原始 opacity 有超过 1 和小于 0 的数值，导出保留原值；显示解释才钳制至 0...1。
收起时缩回小轮廓，内容先淡出；不是等待整个层级删除才算视觉收起。

## 行为与时序

原始 `testLanguageMenuReference` 和 `testLanguageMenuSameChoiceAndEarlyCancel`
各通过一次；参考程序随后 `build-for-testing` 通过。工具审阅新增的更严格断言、
schema 2 和测试重命名未再运行设备验证，不能把原结果算作这些修改的验证。

| 观察 | 本次结果 |
| --- | --- |
| 点击打开、选择 English / Español | 选择成功，回调先于视觉关闭完成 |
| 点击当前简体中文 | 原生 setter 被调用、菜单收起，没有异步状态提交 |
| 点击外部取消 | 不调用语言 setter、不改变选择 |
| 长按后松开 | 菜单保持打开 |
| 按住锚点、滑到日本語、松开 | 实际投递 80 个触摸样本，选择成功 |
| 首次 / 后续打开的首个内容观察距触摸抬起 | 约 464 / 107 ms，包含首次系统工作与观察开销 |
| 首次 / 后续内容 alpha 首次达到 0.99 | 距首个内容观察约 200 / 200 ms |
| 后续关闭内容 alpha 首次低于 0.01 | 距首个关闭内容观察约 310 ms |
| English / Español / 日本語 setter 到下一队列提交 | 约 42.2 / 14.7 / 12.6 ms |
| 相同三次 setter 到内容 alpha 首次低于 0.01 | 约 346 / 340 / 338 ms |

首次打开的额外等待不能固化为动画延迟。上述透明度阈值是离散观察，第一帧可能
已经处于动画中途。不可根据不可见 portal 继续保留约 1.5 秒来设置关闭动画时长。

纵向轨迹随触发变化，不能复用一条全局 y 补间：首次点击打开的内容 y 最大约
244.02，后续点击约 262.57，外部取消前的一次打开约 234.90，长按打开约
237.81，按住后滑动打开约 252.06；它们最后都回到 y208 附近。
这些是各次实际观察到的极值，不是保证能捕获到的连续轨迹峰值。

原“立即取消”探针的实际外部按下发生在锚点抬起约 1.50 秒之后。公开 XCUITest
可能等待动画静止，所以该次没有验证打开早期取消。测试现名
`testLanguageMenuSameChoiceAndCancellationProbe`，保留输入时钟后再判断是否覆盖。

公开动画读取只得到 `CAMatchPropertyAnimation`、`CAMatchMoveAnimation`
等匹配动画，未取得可直接使用的 `CASpringAnimation` 参数，非有限时长保存为 null。
拟合观察点的弹簧只能称作经验拟合，不能称作从系统提取的弹簧常数。

## 证据与限制

原始 run ID：

- `-_NativeReferenceTests_testLanguageMenuReference_-A6AF136F-6CB1-4563-A0D9-741DF2FF69C5`
- `-_NativeReferenceTests_testLanguageMenuSameChoiceAndEarlyCancel_-AC406F1B-8098-465B-87E0-02F370F20035`

本机完整证据位于 `/tmp/sleepy-language-native-20261112`，含 xcresult、原始
session/JSONL、截图、AX、请求手势和视频；临时目录不作为仓库永久金样。
运动测试金样由产品测试单独维护，此文不复制一份不同来源的数据。

全量观察 2443 帧，回调均值约 54.70 Hz，间隔中位数和 P95 均约 16.67 ms，
最大约 475.22 ms；采样成本中位数 2.33 ms、P95 5.14 ms、最大 104.16 ms。
聚焦观察 1145 帧、均值约 56.45 Hz；成本中位数 2.81 ms、P95 5.46 ms。
两次 UIView 遍历均未截断；旧记录没有子 layer 遍历截断标记，不能据此声称
所有动画子层都被捕获。聚焦模式只减少序列化内容，仍然遍历视图树。

`inspect-video.swift` 用 BGRA 解码后读取实际 PTS。主视频 111.66 秒、1248 帧，
平均约 11.18 帧/视频秒，存在长时间不更新；这是可变帧率的模拟器录像。
导出只选最近的实际帧，不造中间帧；不同请求可能指向同一帧。视频 creation date
精度不足以和 trace 精确对时，接触表只作视觉佐证。接触表裁剪假定 402 pt
参考布局，同时保留完整 PNG；材质折射、SDF 着色覆盖仍需视觉观察。
