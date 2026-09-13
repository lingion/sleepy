[evidence=B] 抓取 2026-09-13 · 源URL: https://consumer.huawei.com/cn/support/content/zh-cn15978796/ ; https://consumer.huawei.com/cn/support/content/zh-cn15900044/ ; https://www.ithome.com/0/568/554.htm

# 华为 EMUI/HarmonyOS 2-4 桌面与小组件行为

## 可直接核验的桌面入口

华为消费者支持页给出的窗口小工具入口是：桌面双指捏合，点击“服务卡片”，进入详情后下滑到“窗口小工具”，再进入窗口小工具列表。长按目标小工具并点击“添加至桌面”；添加后可长按拖动调整位置，或点击“移除”删除。

华为另一消费者支持页对“桌面万象小组件”给出的入口是：双手捏合桌面，点击“服务卡片”，下滑找到“窗口小工具”，找到主题图标并添加；随后点击“填充”选择具体万象小组件。长按万象小组件可调整位置及尺寸。

这些入口说明“服务卡片/窗口小工具”是华为桌面用户界面中的不同入口；不能据此把 HarmonyOS NEXT ArkTS Form 卡片与 Android `AppWidget` 视为同一协议。

## 万象小组件（主题引擎）与 Android AppWidget 的边界

华为消费者支持页将桌面万象小组件描述为桌面上的可自由布局小组件，并列出八类功能：时钟、天气、日历、音乐播放器、趣味、工具、倒计时、步数。该页标明支持版本为 EMUI 9.1 及以上，以及 HarmonyOS 4.x 及以下；主题 App 版本要求为 12.0.12.300 及以上。

万象小组件是主题资源/主题引擎能力，属于华为主题体系；它不是普通 Android APK 通过 `AppWidgetProvider` 自动获得的厂商专属 API。Sleepy 的 Android `AppWidget` 适配仍应以 Android AppWidget/AOSP 通道为准。

## HarmonyOS 2 万能卡片的用户交互

华为官方说明经 IT之家转载的 HarmonyOS 2 万能卡片资料描述：支持的 App 图标下可出现小横条，用户向上滑动图标可呼出卡片；卡片可藏可显、可大可小，并可由第三方主题设计师提供换肤资源。该资料讨论的是 HarmonyOS 2 的系统卡片体验，不是 Android `RemoteViews` 的 API 契约。

## 网格尺寸与实现边界

现有可抓取的华为消费者支持页说明了添加、移动和尺寸调整操作，但没有给出华为桌面对 Android AppWidget 的固定“4x2/4x4”网格像素、单元格 dp、`minWidth`/`minHeight` 映射或跨 EMUI/HarmonyOS 版本的统一网格表。不要把其他厂商或 AOSP 的网格数值直接写成华为行为。

因此对 Sleepy 的事实边界是：

- 使用标准 `AppWidgetProvider`、`RemoteViews` 和 `AppWidgetManager` 通道。
- 通过 `AppWidgetOptions` 的实际尺寸进行布局，而不是假定华为桌面固定尺寸。
- 将“华为桌面支持长按添加、拖动移动、移除和部分小组件尺寸调整”作为用户界面行为记录。
- 固定 4x2/4x4 的华为专属网格映射仍需真实设备矩阵验证，当前没有足够官方来源。

## 启动器限制（社区证据，不作官方承诺）

一篇针对 EMUI 9.0 Mate 20 Pro 的社区实测记录称，系统设置中不能直接选择第三方启动器；作者通过 ADB 停用 `com.huawei.android.launcher` 后使用 Nova Launcher，并指出手势导航不能使用、需要改用屏幕内三键导航。该记录是单设备、单版本经验，不应外推到全部 EMUI/HarmonyOS 版本，也不等同于华为官方兼容性声明。

来源：

- A 级用户操作来源：https://consumer.huawei.com/cn/support/content/zh-cn15978796/
- A 级万象小组件来源：https://consumer.huawei.com/cn/support/content/zh-cn15900044/
- B/C 级 HarmonyOS 2 卡片说明：https://www.ithome.com/0/568/554.htm
- C 级第三方启动器实测：https://blog.csdn.net/jgw2008/article/details/103540868
