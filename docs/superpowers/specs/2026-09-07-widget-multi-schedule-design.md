# 多课表小组件编辑入口设计

**日期:** 2026-09-07  
**关联:** GitHub issue #24 Feature 1

## 目标

保留添加小组件时的现有体验：新小组件默认显示当前课表。新增一个 app 内的编辑入口，让用户在桌面上放置多个小组件后，分别选择每个小组件要呈现的课表。这个入口作为以后增加更多小组件设置的容器。

## 用户流程

1. 用户按现有方式把任意 Sleepy 小组件添加到桌面。
2. 小组件直接显示当前课表，不弹配置页。
3. 用户打开 Sleepy，进入「我的 → 通用设置 → 小组件 → 管理桌面小组件」。
4. 管理页列出当前已放置的小组件实例；用户点选其中一个。
5. 编辑页目前提供一个设置：「呈现的课表」。用户选择课表后立即保存并刷新该小组件。
6. 之后可在同一个编辑页增加其他小组件设置，不改变管理页入口和每个实例的编辑模型。

## 页面与导航

项目使用 `MainActivity` 的 `OverlayScreen` 和 overlay 栈，不引入新的导航框架。

### 管理页

`WidgetManagementScreen` 只列出已放置的实例，而不是应用支持的全部 widget 类型。数据通过现有的十个 provider class 查询 `AppWidgetManager.getAppWidgetIds(ComponentName(...))` 得到。每行展示：

- widget 变体的本地化名称；
- 当前绑定的课表名称；没有单独绑定时显示「默认」；
- 点击后进入该 `appWidgetId` 的编辑页。

没有已放置的小组件时展示说明，提示用户先从桌面添加。

### 编辑页

`WidgetEditScreen` 接收 `appWidgetId` 和变体元数据，页面骨架按可扩展的设置 section 组织。目前只有「呈现的课表」section：

- 提供「默认」选项，表示跟随当前课表；
- 提供现有课表选项；
- 选择后立即写入绑定并刷新对应实例；
- 选择「默认」会移除该实例的显式绑定，恢复现有默认课表逻辑。

列表只显示至少有一门课的课表，与 `WidgetTableResolver.resolveCurrentTable` 的可用课表判断一致；如果没有任何课表，编辑页直接显示「请先创建课表」提示，跳过选择控件，不生成无效绑定。

## 数据与渲染

新增 `WidgetBindingStore`，以应用私有 SharedPreferences 保存 `appWidgetId -> tableId`。缺少 key 表示该 widget 没有显式绑定，必须回到现有 `WidgetTableResolver.resolveCurrentTable()`。

渲染路径改为：

```text
loadDataSync(context, appWidgetId)
  -> WidgetBindingStore.get(appWidgetId)
  -> 已绑定且课表仍存在: repository.getTable(tableId)
  -> 否则: WidgetTableResolver.resolveCurrentTable()
  -> 现有 WidgetData / bitmap / RemoteViews 渲染
```

显式绑定指向已被删除的课表时，按无绑定处理并回退当前课表；不在删除课表的业务路径里维护 widget 偏好。widget 从桌面删除时，各 receiver 的 `onDeleted` 清理对应 key。

修改绑定后只刷新对应实例，复用现有 receiver 的完整渲染路径，包括静态 bitmap 与可滚动 RemoteViews 的尺寸分支。课表内容、主题或全局显示设置变化仍按现有逻辑刷新所有实例。

## 变体元数据

新增统一的 `WidgetVariantInfo` 列表，包含 provider class、本地化名称资源、预览图资源和尺寸信息。`WidgetUpdater` 的 provider 遍历从该列表派生，管理页也使用同一列表，避免新增变体时漏改其中一处。

## 错误与生命周期

- 没有 widget 实例：管理页显示空态，不创建虚假条目。
- widget 已从桌面删除：`onDeleted` 删除绑定；管理页下一次进入不会列出它。
- 绑定课表已删除：渲染和编辑页都回退到当前课表；不让失效 tableId 阻塞 widget。
- widget 刷新失败：沿用现有 receiver 的异常捕获和日志，不影响课表编辑操作。
- AppWidgetManager 查询单个 provider 失败：跳过该 provider，其他变体照常显示。

## 测试策略

- `WidgetBindingStore` 单测覆盖写入、读取、删除、默认缺省、不同 widget ID 隔离。
- resolver/数据加载测试覆盖显式绑定、无绑定、绑定课表已删除三条路径。
- 管理页模型测试覆盖十个 provider 的实例收集、空态和绑定名称。
- 编辑页测试覆盖选择具体课表、恢复默认和刷新调用。
- 现有 widget 渲染与更新 wiring 测试继续运行，确保默认路径不变。
- 真机验收：添加两个不同变体或同一变体的两个实例；在管理页分别绑定不同课表；确认两者各自显示正确内容；再把其中一个恢复为默认，确认另一实例不受影响。

## 明确不做

- 添加小组件时不弹选择页。
- 不在桌面小组件上增加单独的配置按钮或点击入口。
- 不修改每日小组件的日期滑动；那是 issue #24 的 Feature 2，另行设计。
- 不新增主题覆盖、周次范围、显示日期等设置；只为后续扩展保留 section 结构。
- 不改变现有「当前课表」解析策略。
