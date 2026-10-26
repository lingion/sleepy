# 文档导航

使用说明见项目 [README](../README.md) 和 [FAQ](../FAQ.md)。本目录按用途组织开发、适配和历史资料。

| 入口 | 内容 |
|---|---|
| [学校目录与状态](schools-list.md) | 学校入口、协议和适配状态 |
| [学校目录来源与准入](catalog/README.md) | 目录生成、来源证据、协议映射和准入规则 |
| [适配采集工具](adapt-kit/README.md) | 教务页面与网络数据采集说明 |
| [新增学校与城市数据](adding-school-city.md) | 学校和城市数据的维护要求 |
| [CI 自动化](ci-automation.md) | 构建、测试和依赖更新工作流 |
| [架构与设计](architecture/) | 迁移方案与[课程配色规格](architecture/spec-course-color-harmony-2026-10-18.md)；以各文件标注的日期和状态为准 |
| [厂商适配资料](vendors/README.md) | 小组件、实时卡片、负一屏、桌面形态与厂商规范 |
| [历史核验资料](history/README.md) | 按日期归档的学校适配、协议核验与证据记录 |
| [README 截图](screenshots/) | 双语 README 使用的截图；[验收记录](readme-refresh/VISUAL-ACCEPTANCE.json)供资源校验脚本使用 |

## 版本说明

`release-notes-v<版本号>.md` 保留在本目录根部，发布工作流和版本更新脚本按此路径读取。当前版本说明见 [v1.0.62](release-notes-v1.0.62.md)。

## 维护约定

- 正在维护的指南和规范放在对应主题目录；厂商资料统一放在 `vendors/`。
- 带日期的适配核验资料放在 `history/`，保留协议矩阵、来源、归属和证据，避免把历史结论当成当前支持状态。
- 临时搜索结果、会话交接和工作流草稿仅在本地保留，不纳入公开文档。
- 移动文档时同步修改文档链接、代码注释及工具引用；截图、目录数据和发布脚本使用的固定路径保持兼容。
