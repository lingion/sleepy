# 荣耀开源实现复用核验

> 核验目标：公开 GitHub/Gitee 项目中可复用的荣耀灵动胶囊、YOYO 建议通知/动态通知卡片实现。
> 核验日期：2026-09-22。本文只记录公开仓库证据；不把官方文档、闭源 SDK 或媒体文章当作开源实现。

## 结论速览

| 类别 | 结论 | 可复用范围 |
|---|---|---|
| 可直接搬 | 无 | 没有发现公开仓库同时提供荣耀灵动胶囊或 YOYO 建议通知的可直接移植实现 |
| 可参考 | `HONORDevelopers/suggestionskit-demo` | Apache-2.0；可参考 Suggestions Kit 的客户端初始化、能力探测、事件/计划/订单数据反馈和结果码处理；不能据此生成胶囊 UI 或 YOYO 卡片 |
| 不可用/准入封闭 | 荣耀全局触达/灵动胶囊/YOYO 生产接入 | 需要开发者平台注册、服务开通/审核、应用与包名绑定；公开文档未给出可替代的第三方胶囊渲染 API |

## 逐仓核验

### 1. 可参考：HONORDevelopers/suggestionskit-demo

- **URL**：<https://github.com/HONORDevelopers/suggestionskit-demo>
- **默认分支**：`master`
- **仓库状态**：`archived=false`；API 返回 `pushed_at=2024-09-05T01:49:06Z`。
- **最新 commit**：`d74e5b5c4320d635e4c97b5acaf34d75f1b81a33`，`init commit`，作者 `clouder`，2024-09-05 01:49:04 UTC。
- **License**：Apache License 2.0，SPDX `Apache-2.0`；仓库 `LICENSE` 为 Apache License Version 2.0（January 2004）。
- **官方仓库定位**：README 将其定位为 Suggestions Kit 数据反馈示例，覆盖服务记录、事件、计划、订单及活动状态感知；要求开发者注册应用、开通 API、放置 `mcs-services.json`，并在 MagicOS 7.0+ 设备调试。

#### 关键文件和行号

| 文件 | 关键行 | 证据/用途 |
|---|---:|---|
| `app/src/main/java/com/hihonor/suggestionkittool/activity/FeedbackEventActivity.java` | 约 72 | `Suggestion.getInstance(this.getApplicationContext())` 初始化客户端 |
| 同上 | 约 132 | 获取 `suggestion.getFeedbackClient()` |
| 同上 | 约 138 | 创建 `FeatureCheckReq`，设置 `FEEDBACK_EVENT` 和包名，调用 `suggestion.hasFeature(...)` |
| 同上 | 约 155 | 创建 `EventFeedbackReq`，填写 intent type、包名、创建/开始/结束时间和 event status |
| 同上 | 约 163 | 调用 `feedbackClient.feedbackEvent(eventFeedbackReq, FeedbackCallback)`，在 `onResult(int resultCode)` 处理结果 |
| `app/src/main/java/com/hihonor/suggestionkittool/activity/BaseFeedBackActivity.java` | 20 | 引入 `com.hihonor.android.magicx.intelligence.suggestion.common.config.ResultCode` |
| 同上 | 120-155 | 将 Suggestions Kit 结果码映射到成功、参数错误、权限校验失败、服务错误、未授权等提示 |
| `app/src/main/AndroidManifest.xml` | manifest/package 行 | 示例包名 `com.hihonor.demo.suggestionkittool`；声明 `android.permission.ACTIVITY_RECOGNITION` |
| 同上 | permission/meta-data 段 | 仅见活动识别权限相关 metadata；没有胶囊、YOYO、动态通知专用 provider 或公开渲染组件 |
| `mcs-services.json` | 仓库根目录 | 荣耀服务配置文件，README 要求从开发者站下载并放入工程 |
| `README.md` / `README_ZH.md` | 全文 57 行文件中的接入说明 | 注册应用、开通 API、配置签名指纹、导入 `mcs-services.json`、MagicOS 调试环境 |

#### 可直接借用的部分

仅限以下通用接入骨架，且必须重新核对当前 SDK 版本和应用准入：

1. `Suggestion.getInstance(context)` 的初始化方式。
2. `FeatureCheckReq` 能力探测，再决定是否提交反馈。
3. `EventFeedbackReq` 的事件字段组织方式和 `feedbackEvent` 回调处理。
4. `ResultCode` 分支与日志/用户提示的错误处理思路。
5. `mcs-services.json`、包名、签名指纹和开发者平台配置的工程依赖关系。

这些代码**不能直接搬成荣耀灵动胶囊或 YOYO 通知**：仓库树中没有 `capsule`、`YOYO`、`notification` 命名的实现文件，也没有胶囊布局、状态栏展示控制、卡片模板或 YOYO 建议卡片 payload。

### 2. 不可用/准入封闭：生产级荣耀灵动胶囊与 YOYO 建议

现有本地资料已核对的官方入口：

- [全局触达设计指南 101538](https://developer.honor.com/cn/doc/guides/101538)
- [全局触达接入流程 101453](https://developer.honor.com/cn/doc/guides/101453)
- [全局触达开发指南 101454](https://developer.honor.com/cn/doc/guides/101454)
- [荣耀 Suggestions Kit 示例仓库](https://github.com/HONORDevelopers/suggestionskit-demo)

本地 `capsule-access-gate.md`、`capsule-api.md` 已记录的准入事实：应用注册、包名绑定、场景化建议服务/全局触达逐项申请、审核，以及 MagicOS 版本和云调试限制。公开资料没有提供一个无需平台开通、可由第三方应用直接调用的 `startCapsule/updateCapsule/stopCapsule` 式公开 API。系统是否展示由 Suggestions Kit/系统择机决定，不等于应用拥有胶囊绘制控制权。

因此：

- 不能把 `suggestionskit-demo` 宣称为“灵动胶囊实现”。
- 不能从普通 Android `Notification` 或开源 Dynamic Island 仿制项目推导出荣耀 YOYO 接入协议。
- 未获得荣耀开发者平台服务开通、SDK/AAR 和真机权限前，生产胶囊/YOYO 适配应标记为“不可用/准入封闭”。
- 纯系统通知可以作为降级路径，但它不是 YOYO 建议通知实现，也不证明会获得荣耀胶囊展示。

## 已查范围与负证据

### GitHub

1. 关键词检索：`HONORDevelopers`、`Suggestions Kit`、`suggestionskit-demo`、`灵动胶囊`、`YOYO 建议`、`Honor Magic Capsule`、`capsule`、`smart notification`。
2. 检查 `HONORDevelopers` 公共仓库目录；公开列表中唯一命中 Suggestions Kit 的仓库是 `suggestionskit-demo`。
3. 对 `suggestionskit-demo` 的完整递归树核对：存在事件/计划/订单/运动状态等 Java activity，但没有 `YOYO`、`capsule`、`notification` 命名路径。
4. 对其 manifest、README、最新 commit 和 license 逐项核对，结果见上表。

### Gitee

以“荣耀 灵动胶囊”“荣耀 Suggestions Kit”“YOYO 建议”“荣耀 动态通知/卡片”等组合检索，未得到可核验的公开实现仓库。搜索结果不是相关源码，或没有足够的仓库、license、commit、文件路径证据，因此不列为候选实现。

### 负证据边界

“未找到”不等于证明互联网不存在实现。这里的可审计结论是：在上述公开 GitHub/Gitee 检索范围内，没有同时满足“公开仓库 + 可核验 license/commit + 真实荣耀胶囊或 YOYO 代码路径”的候选；唯一强相关开源仓库只覆盖 Suggestions Kit 数据反馈。

## 给 Sleepy 的准入建议

1. 当前不应复制第三方 Dynamic Island 仿制代码并标称荣耀适配。
2. 若要继续做官方路径，先用平台注册包名申请 Suggestions Kit/全局触达服务，再取得对应 SDK/AAR 和公开协议，之后以 `suggestionskit-demo` 的反馈骨架为参考做最小真机验证。
3. 在准入完成前，保留普通 Android 常驻通知作为独立降级方案；文案中不要称其为荣耀 YOYO 或灵动胶囊。

## 来源

- [HONORDevelopers/suggestionskit-demo](https://github.com/HONORDevelopers/suggestionskit-demo)
- [该仓库最新 commit](https://github.com/HONORDevelopers/suggestionskit-demo/commit/d74e5b5c4320d635e4c97b5acaf34d75f1b81a33)
- [该仓库 Apache-2.0 LICENSE](https://github.com/HONORDevelopers/suggestionskit-demo/blob/master/LICENSE)
- [荣耀全局触达接入流程 101453](https://developer.honor.com/cn/doc/guides/101453)
- [荣耀全局触达开发指南 101454](https://developer.honor.com/cn/doc/guides/101454)
- [荣耀全局触达设计指南 101538](https://developer.honor.com/cn/doc/guides/101538)
