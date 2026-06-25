# Release Policy

**强制规范**，违反一次停一天。

## 版本号

遵循 [semver 2.0.0](https://semver.org/) + Android 适配：

| 字段 | 规则 | 示例 |
|---|---|---|
| `versionName` | `MAJOR.MINOR.PATCH`，纯数字 `.` 分隔，**不带任何后缀** | `1.0.14` ✓ / `1.0.16-rebuild-v19` ✗ |
| `versionCode` | 整数，**严格单调递增**，每次 release 必须 > 上次 | `14` ✓ / `9` (跳号) ✗ |

**不允许的后缀**：`-rebuild`、`-fixed`、`-alpha`、`-beta`、`-debug`、`-v19i`、`-jw-config`、`-jw-heu-time`、其他任何 `-.+`。

> 历史 tag `v1.0.16-rebuild-*`（14 个）已全部删除归零。

## 何时打 release

| 情况 | 是否打 release | 说明 |
|---|---|---|
| 新功能（用户可感知） | **必须** | 例：HEU 教务导入 |
| Bug 修复（影响使用） | **必须** | 例：导入流程补全配置页 |
| 仅 widget 内部迭代 | **禁止** | v17→v19i 那种调字号调布局，合并到下次功能 release |
| 重构（无外部行为变化） | **禁止** | 内部 commit 不打 tag |
| 文档/注释改动 | **禁止** | — |

## APK asset 命名

**唯一格式**：`sleepy-v{versionName}-{abi}-{flavor}.apk`

| 部分 | 取值 |
|---|---|
| `{versionName}` | 与代码 `versionName` 一致 |
| `{abi}` | `arm64` / `x86_64` / `universal` |
| `{flavor}` | `release` / `debug` |

**示例**：
- ✓ `sleepy-v1.0.14-arm64-release.apk`
- ✗ `app-arm64-v8a-debug.apk`（旧风格）
- ✗ `Sleepy-v1.0.10-arm64.apk`（大小写混乱）

## 构建命令

```bash
# arm64-only release APK
./gradlew assembleRelease
# arm64-only release APK + 上传到 GH release
./scripts/release.sh <versionName> <versionCode> <commit-sha>
```

## Tag 规则

| 项 | 值 |
|---|---|
| 格式 | `v{versionName}`，与代码 `versionName` 完全一致 |
| 示例 | `v1.0.14` ✓ / `v1.0.16-rebuild-jw-heu-time` ✗ |
| 指向 | 必须指向打了该 versionName + versionCode 的 commit |

## GH Release body 模板

```markdown
## v{versionName}

### 新增
- {feature}

### 修复
- {bug}

### 构建
- APK: sleepy-v{versionName}-arm64-release.apk
- versionCode: {versionCode}
- commit: {short-sha}
```

## 自检清单（每次 release 前过一遍）

- [ ] `build.gradle.kts` 的 `versionName` 与要打的 tag 一致
- [ ] `build.gradle.kts` 的 `versionCode` > 上次 release 的 `versionCode`
- [ ] APK asset 命名符合 `sleepy-v{versionName}-{abi}-{flavor}.apk`
- [ ] Release body 列出新增/修复/构建信息
- [ ] 本地无未 commit 改动（除 `RELEASE_POLICY.md` 自身）