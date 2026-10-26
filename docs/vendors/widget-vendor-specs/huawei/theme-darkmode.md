[evidence=A] 抓取 2026-09-13 · 源URL: https://consumer.huawei.com/cn/support/content/zh-cn15848009/ ; https://consumer.huawei.com/cn/support/content/zh-cn16053312/ ; https://consumer.huawei.com/cn/support/content/zh-cn16017909/ ; https://consumer.huawei.com/cn/support/content/zh-cn15872076/ ; https://consumer.huawei.com/cn/support/content/zh-cn16028416/

# 华为主题系统与深浅色（Dark mode）

## 系统深色模式开关

华为消费者支持页给出的深色模式入口：

- EMUI 11.0 / HarmonyOS 2.0-4.3 设备：设置 > 显示和亮度 > 深色模式。开启后屏幕背景转为深色，页面表述为降低屏幕过亮刺激、护眼省电。
- HarmonyOS 5/6/7 设备：设置 > 显示和亮度，在“显示模式”中点击“深色”卡片启用、“浅色”卡片关闭。控制中心也可放置深色模式快捷开关。

深色模式的启用时间可管理：进入 设置 > 显示和亮度 > 深色模式，选择“全天开启”或“定时开启”。定时开启时系统在重新亮屏后才切换深色模式。

自动打开深色模式的排查项（华为官方页）：检查“定时开启”开关；检查智慧生活/小艺任务中是否添加了深色模式场景卡片；确认是个别应用还是所有应用处于深色模式，个别应用深色属于应用自身设置。

## 第三方应用与深色模式的关系（关键行为）

华为官方支持页（HarmonyOS 5.0-7.0 适用）明确：

- 升级后仅保留系统深色模式整体开关，没有单个应用的控制开关。
- 应用是否随系统变化由应用自身策略决定，用户可前往具体应用中查看相关设置。
- 部分应用的深色模式仍在适配中，暂时不支持深色模式。

即：华为系统层不提供“强制第三方 app 跟随深色”的开关（区别于 Android 15 原生的强制深色功能）；跟随行为取决于 app 自身实现。对 Sleepy 的含义：深浅色适配要靠 app 自己的 DayNight/`uiMode` 处理，不能假设华为桌面或系统会代为处理；标准 AppWidget 的深浅色跟随也应由 app 的资源限定符与 RemoteViews 主题处理承担。

## 华为主题体系

华为官方主题页与混搭 DIY 支持页给出的体系：

- 主题 App 可对主题、壁纸、熄屏显示、字体样式进行个性化定制。
- 混搭主题：主题会员可直接混搭；非会员需购买主题资源后混搭。支持混搭的内容包括锁屏、桌面、图标、熄屏显示和其他换肤；Pura X 设备额外支持混搭外屏。
- 一镜到底主题/个性化主题不支持混搭锁屏壁纸或桌面壁纸（官方解释：需同步协调熄屏显示、锁屏界面与桌面背景三要素的动态过渡效果）。
- 恢复默认主题：设置 > 桌面和个性化 > 更多主题 > 推荐主题 > 预置主题 > 应用；默认字体为鸿蒙黑体。
- 图标可独立更换：第三方主题资源页混搭弹窗中可选“应用系统默认图标”或“应用该主题图标”；也可在主题 App 推荐页“玩转桌面图标”中选择图标资源。
- 图标大小：双指捏合桌面 > 图标 > “图标大小”左右滑动调整。

主题体系与小组件的交集：

- “桌面万象小组件”作为主题资源随主题包上架，也可单独上架；应用时在桌面添加小组件容器后点击“填充”选择具体组件。
- 一键切换主题时 App 图标、万能卡片、壁纸和锁屏一起变装（华为官方对 HarmonyOS 2 万能卡片的说明，经 IT之家转载）。

## 对 Android AppWidget 的推论边界

- 系统深浅色切换会触发 Android 的 `uiMode` 配置变化；第三方 app 与其 widget 的深浅色跟随由 app 自身资源与代码决定，华为不提供应用级强制开关（上述官方行为）。
- 华为主题（图标/壁纸/混搭）影响的是系统主题层，官方页面没有给出“主题对第三方 AppWidget RemoteViews 配色的强制覆盖”条款；不要把第三方主题会改写 widget 颜色写成官方行为。

## 来源

- 深色模式入口（EMUI 11/HarmonyOS 2-4.3）：https://consumer.huawei.com/cn/support/content/zh-cn15848009/
- 深色模式入口与定时开启（HarmonyOS 5/6/7）：https://consumer.huawei.com/cn/support/content/zh-cn16053312/
- 无单应用深色开关 + 应用自身策略决定跟随：https://consumer.huawei.com/cn/support/content/zh-cn16017909/
- 自动打开深色模式排查：https://consumer.huawei.com/cn/support/content/zh-cn15872076/
- 主题混搭 DIY/图标自定义/恢复默认：https://consumer.huawei.com/cn/support/content/zh-cn16028416/
