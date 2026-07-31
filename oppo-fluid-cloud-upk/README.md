# Sleepy OPPO Fluid Cloud UPK

这是 Pantanal DevStudio 的有宿主流体云工程骨架。

## 已接入

- `src/config.json`：API 2.0、`immediate`、`notification/statusbar` 入口
- `src/assets/card-config.json`：宿主包名、Provider、sm/md/lg 三种流体云尺寸
- `src/pages/index.oml`：课程名、上课时间、地点、教师、状态字段
- Sleepy APK：官方 `SeedlingSupportSDK-lite 3.0.7`
- Sleepy APK：`SeedlingCardWidgetProvider` 生命周期桥接

## 必须由 OPPO 分配后替换

- `identifier.id`
- `intent.action`
- `intent.domain`
- 事件名称与 eventCode
- 可能的白名单/grade/requestShowPanel 能力

当前这些值保留为 `REPLACE_WITH_OPPO_*`，因此该 UPK 目前不能提交发布，也不能作为真实流体云 PASS 证据。

## 构建

需要 Pantanal DevStudio 验证 OML 模板语法并生成 UPK。当前命令行环境没有 DevStudio，因此这里只提交源工程骨架，不伪造 `.upk`。
