# VERSIONING (公开)

本项目版本号遵循 [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html)。

格式:`MAJOR.MINOR.PATCH[-prerelease]`,对应 git tag 前缀 `v`(如 `v1.2.3`、`v1.2.3-rc.1`)。

## 何时递增

- **MAJOR**(不兼容的 API/数据变更):用户数据/课程表导入格式破坏性变化、迁移数据库结构需要用户主动操作、移除已发布功能。
- **MINOR**(向后兼容的功能新增):新增教务协议/学校、新增用户可见功能、新增小组件变体、新增主题。
- **PATCH**(向后兼容的缺陷修复):bug 修复、性能提升、UI 微调、文档。

预发布标签(`-rc.N`、`-beta.N`)用于灰度发布,优先推给测试用户。

## 版本号真源

仓库内**不再手改 `versionCode` / `versionName`**。它们由 `app/build.gradle.kts` 在构建时派生:

1. 优先取最近 `v*.*.*` git tag(`git describe --tags --match 'v*.*.*' --abbrev=0`)。
2. 无 tag 祖先时(刚打 tag 之前的日常 main),回退到 `docs/release-notes-v*.*.*.md` 文件名最大版本。
3. 上述都无时(`fresh clone`,不应分发)fallback `0.0.0 / 1`。

派生公式:
- `versionName` = tag 去 `v` 前缀(如 `v1.2.3-rc.1` → `1.2.3-rc.1`)
- `versionCode` = `MAJOR × 10000 + MINOR × 100 + PATCH`(单调递增、与 tag 一一对应、可重现)

## 支持策略

- **当前 MAJOR 的最新 MINOR**:全功能支持,bug 修复 + 安全补丁。
- **前一个 MAJOR**(若有):仅修高严重性问题,不接新功能。
- **更早版本**:不维护,鼓励升级。

个人/双人项目无 LTS 承诺,单 MAJOR 长期滚动(`1.x` 持续加 MINOR)。需要严格兼容保证时升 MAJOR(目前未触发)。

## 内部 SOP 与本文件的关系

仓库的内部协作细则(commit 写法拆分哲学、release 发版门禁)由治理 skill `repo-governance-sop` 维护,本地 `.gitignore` 声明的内部路径(见根目录 `.gitignore` 中 `docs/sop/`、`BRANCHING.md` 等条目)永不入库。贡献者只需遵守本文件 + `CONTRIBUTING.md` + GitHub 仓库的 PR 门禁。

## 出处

SemVer 与本文档结构参考:

- [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html)
- [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/)
- [GitHub flow + branch protection](https://docs.github.com/en/get-started/using-github/github-flow)
- [Trunk Based Development](https://trunkbaseddevelopment.com/)
