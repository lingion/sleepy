# Changelog

本项目所有重要变更记录于此。

格式基于 [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/),版本号遵循 [SemVer 2.0.0](https://semver.org/spec/v2.0.0.html)(详见 [VERSIONING.md](VERSIONING.md))。

- **每个已发布版本的完整说明**(英文在前、中文在后)见 [GitHub Releases](https://github.com/lingion/sleepy/releases),它们与仓库内的 `docs/release-notes-v*.*.*.md` 一一对应。
- 本文件只跟踪"未发布(Unreleased)"段与版本分类速览;发版时 Unreleased 内容整理进新的 release notes,此处分段归档。

## [Unreleased]

### Added

- 仓库协作规范升级:main 分支 ruleset 保护(PR-only、squash-only 线性历史、必需 CI 检查),`ci.yml` PR 门禁(commitlint / collector 单测 / Android 单测 / lint / assembleRelease),`release.yml` tag 触发发布,版本号由 git tag 与 `docs/release-notes-v*.md` 派生([VERSIONING.md](VERSIONING.md))。

## [1.0.57] - 2026-09-28

### Added

- 混合课时课表:逐节次设置不同课时,导入导出全链路往返。
- 假期调休映射:假期日期到实际上课日期的映射,主课表/Today/周网格/通知/小组件全生效。
- 最近有课日:当天无课时可显示最近的有课日,主课表与五族小组件共享开关。
- 前夜明日预告:每日提醒卡片可预览明日首课与总课数。
- 厂商实时通知引导:支持的机型家族给出能力感知的引导与厂商设置回退。
- Android 15 小组件预览:13 个小组件变体注册无数据选择器预览。
- 诊断导出:导入失败走单一 AlertDialog,一键导出完整诊断包。
- 新增教务协议与学校:目录由 340 增至 345 所。

### Changed

- 官方 Navigation 3:自研 overlay 栈替换为类型化路由 + Material 过渡。
- Material 3 迁移:主题与组件渲染切到官方 M3 API,旧主题桥删除。
- 通用设置重排:课表显示/小组件/屏幕导航/语言/实验室分组定序。

(1.0.35–1.0.56 的完整变更见对应 [GitHub Release](https://github.com/lingion/sleepy/releases) 与 `docs/release-notes-v*.md`。)

[Unreleased]: https://github.com/lingion/sleepy/compare/v1.0.57...HEAD
[1.0.57]: https://github.com/lingion/sleepy/compare/v1.0.56...v1.0.57
