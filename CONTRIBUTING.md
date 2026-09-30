# 贡献指南 (Contributing)

感谢你对 Sleepy 的关注！无论是报 bug、交 PR 还是让学校支持教务直连，这里都有你要的信息。

中文为主，英文亦可。想先摸清代码结构，看 [wiki 的架构总览](https://github.com/lingion/sleepy/wiki/Architecture) 与 [代码库地图](https://github.com/lingion/sleepy/wiki/Codebase-Map)；功能细节、适配流程、测试体系也都在 [wiki](https://github.com/lingion/sleepy/wiki) 里。

## 环境

```bash
git clone https://github.com/lingion/sleepy.git
cd sleepy
./gradlew :app:testDebugUnitTest   # 先跑一遍全量单测,确认基线全绿
```

- Android Studio 打开即可开发；命令行构建用 `./gradlew`。
- 改代码前先跑通测试基线；提交前确认自己新增的行为有测试锁定。

## 提 Bug

用 issue 模板（Bug Report / Feature Request / School Adaptation），不要开空白 issue。

- **教务直连问题**：附上用 [适配采集器](docs/adapt-kit/README.md) 抓的 `sleepy-adapt-*.zip` 包，定位会快很多。
- 报障时写清：机型 / Android 版本 / 学校 / 复现步骤。

## 提 PR

1. **从 `main` 拉个人 topic 分支**，不要直接向 `main` 推送；当前不需要共享的 `dev` 分支。命名：
   - 新功能 `feat/<scope>-<topic>`
   - 修 bug `fix/<scope>-<topic>` 或 `fix/issue-<N>-<topic>`
   - 教务适配 `adapt/<school-slug>-<topic>`

   首次开始一个变更时，可以直接执行：

   ```bash
   git fetch origin
   git switch -c feat/your-topic origin/main
   git push -u origin HEAD
   ```

   如果是修 bug 或教务适配，把 `feat/your-topic` 换成对应的 `fix/...` 或 `adapt/...`。后续只在这个个人分支上 commit 和 push，再发起目标为 `main` 的 Pull Request。
2. **一个分支一个主题**；小步 commit，格式 `<type>(<scope>): <subject>`，`<type>` ∈ {feat, fix, refactor, docs, test, chore, perf, build}。
3. **合并前测试全绿**：

   ```bash
   ./gradlew :app:testDebugUnitTest
   ./gradlew :app:lintDebug
   ```

   教务适配另跑该校 fixture 测试；改 WebView 内 JS 正则的，还必须同步通过对应的 `*WebViewContractTest`（如 `JwNeuWebViewContractTest`、`HfutPortalEams5WebViewContractTest`）。这类测试锁的是：同一条解析规则会同时出现在网页注入的 JS 和 Kotlin 两侧，两边写得不一致时页面表现和导入结果就对不上，contract 测试保证"两边写的是同一条规则"。

4. PR 描述写清动机和行为变化；关联 issue 用 `ref #N`（不用 `Fixes #N`，由维护者合并后统一关闭）。
5. **合并走 PR + squash**(不要 merge / rebase 进 main)。main 受 ruleset 保护：禁直推、禁非 fast-forward、必需 CI 全绿（commit-lint / collector-test / android-unit-test / android-assemble）。PR 里的**每条 commit message** 都要符合上面的 `<type>(<scope>): <subject>` 格式——commit-lint job 逐条校验。
6. 贡献者的 PR 分支推到 `origin` 不需要额外批准；**合入 main 由维护者 squash 合并**。
7. 版本号 / Release 由维护者打 `v*.*.*` tag 触发（`scripts/bump-version.sh`），不需要在 PR 里手改 `build.gradle.kts`——版本构建时从 tag 派生，见 [VERSIONING.md](VERSIONING.md)。

## 教务适配的特殊要求

新增/修改学校适配不是普通代码提交，额外要求：

1. **调研归档**：解析规则要能对上真实页面。你的采集包（`sleepy-adapt.zip`）就是最好的依据；提交时说明页面上哪个元素对应哪条解析规则。
2. **致谢**：凡参考了他人维护的教务/课表开源项目（无论参考了解析思路还是直接复用规则），都会在 App 内「关于 → 开源许可」页致谢。提 PR 时请列出你参考过的仓库，作者会核对补全。
3. **测试**：每所学校带 fixture 测试；解析规则改动必须先有失败的测试再修（红→绿）。

## Commit 身份

- 贡献者用本人邮箱，遵守上面的 commit 格式。
- commit 尾注不要加 AI 署名（如 `Co-Authored-By: ...`），保持历史干净。
