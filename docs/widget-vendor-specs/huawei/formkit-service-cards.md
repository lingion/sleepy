[evidence=B] 抓取 2026-09-13 · 源URL: https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/formkit-overview ; https://gitee.com/openharmony/docs/blob/master/zh-cn/application-dev/form/formkit-overview.md

# HarmonyOS NEXT ArkTS 服务卡片（Form Kit）

## 生态边界

Form Kit 是 HarmonyOS 服务卡片框架：卡片由卡片提供方实现，桌面等系统应用作为卡片使用方显示它，卡片管理服务负责两者之间的添加、删除、显示、刷新和点击事件通知。官方文档列出的可用设备类型包括手机、平板、PC/2in1、智慧屏、智能手表和车机；普通应用内嵌显示不在该使用场景内。

ArkTS 卡片属于 HarmonyOS 原生卡片模型，不是 Android `AppWidget`。`FormExtensionAbility`、`formProvider`、`formBindingData` 和 `postCardAction` 是 Form Kit API；Android APK 的 `AppWidgetProvider` 不会自动获得这些能力。

官方文档将 ArkTS 卡片分为静态卡片、动态卡片和互动卡片：

- 静态卡片：支持 UI 组件和布局，使用 `FormLink` 交互；渲染后使用最后一帧数据作为静态图片，实例运行资源会释放。
- 动态卡片：在 UI 和布局之外支持通用事件与自定义动效；用 `postCardAction` 处理 `router`、`message`、`call`。
- 互动卡片：从 API version 20 起在动态卡片基础上提供破框动效能力。

## 工程和生命周期

卡片可与应用共包，也可使用独立卡片包。独立卡片包从 API version 20 起支持，应用包的 `module.json5` 用 `formWidgetModule` 关联卡片模块，卡片模块用 `formExtensionModule` 关联应用模块。

`module.json5` 中的 `FormExtensionAbility` 使用固定 metadata 名 `ohos.extension.form`，其资源指向 `form_config.json`。`form_config.json` 的卡片配置包括 `name`、`src`、`uiSyntax`、`isDefault`、`supportDimensions`、`defaultDimension` 等字段；一个应用最多配置 16 个卡片。

典型生命周期回调包括：

- `onAddForm`: 添加卡片时调用，返回初始 `FormBindingData`。
- `onUpdateForm`: 周期或请求刷新时调用。
- `onFormEvent`: 卡片 `message` 事件到达时调用。
- `onRemoveForm`: 卡片删除时调用，清理实例数据。
- `onChangeFormVisibility`: 系统应用卡片的可见性变化回调。
- `onConfigurationUpdate`: 系统配置变化回调。

`FormExtensionAbility` 进程不能常驻后台。文档说明：生命周期调度完成后，若 10 秒内没有新的生命周期回调，进程会被清理；超过 10 秒的业务应拉起主应用处理，再通过 `updateForm` 更新卡片。

## 刷新机制

### 提供方主动刷新

提供方可调用 `formProvider.updateForm(formId, formData)`。常见流程是卡片按钮调用 `postCardAction` 的 `message` 事件，`onFormEvent` 收到消息后构造 `FormBindingData` 并调用 `updateForm`。

### 定时、定点和下次刷新

`form_config.json` 中：

- `updateEnabled: true` 开启周期刷新能力。
- `updateDuration` 单位为 30 分钟；正整数 `N` 表示 `30 * N` 分钟。
- `scheduledUpdateTime` 使用 24 小时制，精确到分钟，例如 `10:30`。
- 同时配置 `updateDuration` 与 `scheduledUpdateTime` 时，`updateDuration` 优先。
- `formProvider.setFormNextRefreshTime` 可设置某个卡片的下一次刷新，最短刷新时间为 5 分钟。

卡片管理服务会综合定时配置、卡片可见状态和刷新次数等因素决定是否通知 `onUpdateForm`，因此配置刷新时间不等于每次都必然产生回调。

### 代理刷新

`dataProxyEnabled: true` 启用卡片代理刷新。该机制只对系统应用开放：卡片提供方在 `onAddForm` 返回数据提供方定义的 `key + subscriberId`，数据管理服务感知共享数据变化后，通知卡片管理服务和渲染服务更新卡片。

启用代理刷新后，定时刷新失效，但不影响定点刷新。只有系统提供了公开可用的共享数据标识，卡片提供方才能使用该特性。

### 数据传递

卡片通过 `postCardAction` 向卡片提供方传递数据，提供方通过 `updateForm` 向卡片传递数据。卡片与提供方是独立进程，文档规定两者间共享数据使用 `LocalStorageProp`，不能使用 `getContext`；提供方推送到卡片的数据接收时会转换为字符串。

刷新约束包括：提供方只能刷新自己的卡片，使用方只能刷新添加到自己的卡片。API version 20 起，共享内存刷新数据总大小不超过 10 MB、刷新图片不超过 20 张；API version 19 及之前图片数量上限为 5 张且每张限制 2 MB。

定时刷新使用同一个系统计时器计时，因此定时刷新的第一次刷新最多有 30 分钟的偏差。文档示例：卡片 A（每半小时刷新）3:20 添加成功，卡片 B（每半小时刷新）3:40 添加成功，3:50 定时器触发时只有 A 刷新，B 要等到下一次事件（4:20）。`updateDuration` 值为 2 表示刷新间隔为 2 个 30 分钟（1 小时）。

## 卡片交互

动态卡片的 `postCardAction` 只能在卡片控件点击事件中调用，支持三类事件：

```ts
postCardAction(this, {
  action: 'router',
  abilityName: 'EntryAbility',
  params: { targetPage: 'detail' }
});
```

- `router`：跳转到指定 UIAbility。非系统应用只能跳转到自身应用内的 UIAbility。
- `message`：拉起 `FormExtensionAbility`，通过 `onFormEvent` 把消息传给应用后端，常用于按钮触发卡片数据刷新。
- `call`：将指定 UIAbility 拉到后台并调用指定方法；文档以音乐播放等后台长时任务为场景。

静态卡片不能使用动态卡片的运行逻辑，使用 `FormLink` 实现同样三类事件的页面交互。

## 深浅色、渲染与开发约束

卡片支持适配系统深浅色。`form_config.json` 的 `colorMode` 对 JS 卡片在 API version 12 起可用、API version 20 起废弃；从 API version 20 起卡片主题样式统一跟随系统颜色模式。

ArkTS 卡片运行在系统公共卡片渲染服务进程中，不同卡片提供方通过不同 ArkTS 虚拟机环境隔离。卡片提供方主进程和 `FormExtensionAbility` 进程相互隔离但共享文件沙箱。

官方约束包括：

- 仅支持 ArkUI 的受限能力集合，接口需带“卡片能力”标记。
- 支持 HAR 静态共享包，不支持 HSP 动态共享包。
- 不支持 native 语言和 native `.so`。
- 不支持卡片内左右滑动控件，以避免与卡片使用方手势冲突。
- 不支持 `setTimeout`、断点调试、Hot Reload 和极速预览。
- `FormExtensionAbility` 加载不支持的模块会得到 `undefined` 并可能导致崩溃；官方 FAQ 特别提示要避免把 `particleAbility`、`audio`、`camera`、`media`、`backgroundTaskManager` 等不支持模块带入其导入链。

## 来源说明

华为开发者页面是对应官方文档入口，但抓取时返回 JS SPA 空壳；正文事实采用 OpenHarmony 官方 `docs` 仓库的同主题文档核对。相关入口：

- https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/formkit-overview
- https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/arkts-form-overview
- https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/arkts-ui-widget-lifecycle
- https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/arkts-ui-widget-passive-refresh
- https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/arkts-ui-widget-update-by-proxy-sys
- https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/arkts-ui-widget-event-overview
- https://gitee.com/openharmony/docs/tree/master/zh-cn/application-dev/form
