# CI 自动化 / 免费算力手册 ("CI 白嫖厨房")

本仓库是 **public**,GitHub Actions 对 public 仓库的 Linux/Windows runner **完全免费、不设分钟数上限**(macOS runner 也是 free tier,只是并发低、要排队)。这意味着凡是"本地跑不动、跑不得、跑得慢"的重活,都可以甩给 CI 的云端机器:4 核 16G、海外直连带宽、干净的 Linux/macOS 环境。

一句话:**你只负责 `git push` 和点按钮,算力账单 GitHub 买单。**

> 适用边界:Actions 的设计用途是"构建 / 测试 / 部署"。不要用它挖矿、当网盘/CDN、挂代理、长时间纯下载刷流量——那会触发 fair-use 封禁。下面每一项都是正经 CI 用途。

---

## 0. 为什么需要它(本地痛点对照)

| 本地跑不了的事 | 原因 | CI 怎么解决 |
|---|---|---|
| Android clean build / R8 / Robolectric | 3.2G 内存 + swap 撑不住,JVM 一炸全卡 | Linux runner 16G 内存随便造 |
| 模拟器 instrumentation 测试 | 本地没 KVM 或内存不够起 emulator | Linux runner 有硬件加速 KVM |
| iOS 打包 / 模拟器测试 | 本机是 Linux,根本没有 Xcode/macOS | macOS runner 原生 xcodebuild |
| 海外依赖 / 数据采集 | 本地代理下载 GitHub 掉到 ~3KB/s | runner 海外直连,快几十倍 |
| 依赖升级 / 定时体检 | 手工做容易忘、占用本地机器 | dependabot + schedule 自动开 PR |

---

## 1. 获取 debug APK

### 1.1 提交分支后自动构建

向 Sleepy 的 `main` 发起非草稿 PR，或更新已有 PR，Debug APK Build 就会构建 PR 分支的 head SHA。它不构建 GitHub 临时生成的合并预览提交。草稿 PR 转为可评审后开始构建；同一个 PR 的新提交会取消未完成的旧构建，不影响其他 PR。

不准备提 PR 时，也可以在自己的 fork 使用：先同步包含此工作流的代码，在 fork 的 Actions 页面启用工作流，再推送分支。fork 的分支 push 自动构建；上游仓库只为 `main` push 和 PR 构建，普通分支 push 不重复打包。tag push 不触发 debug 构建。

GitHub 可能要求维护者批准外部贡献者的首次工作流运行；这一步不能绕过。fork 推送与上游 PR 的运行属于各自仓库，互不取消。

自动构建一次生成四种包，每种架构分别下载：`arm64-v8a`、`armeabi-v7a`、`x86_64`、`universal`。不知道选哪个时可以用 universal。

### 1.2 手动选择源码与架构

打开 Actions 的 Debug APK Build，点击 Run workflow。上方分支选择决定使用哪一版工作流；下方 `ref` 留空时，也构建该分支在本次事件中的固定提交。填写 `ref` 才会覆盖源码目标，支持本仓库中的分支、标签或完整 SHA。普通群友没有上游写权限时，可以在自己的 fork 手动运行，或通过 PR 触发上游自动构建。

```bash
# 从选中的分支运行，只生成 V7A APK；将仓库名换成自己的 fork
# 该分支需要包含新版工作流与辅助工具。
gh workflow run "Debug APK Build" --repo YOUR_LOGIN/sleepy \
  --ref feature/example -f abi=armeabi-v7a

# 使用 main 的工作流构建任意已有分支，只生成 V7A APK
# 目标分支可以早于这次 CI 改动，辅助工具来自工作流版本。
gh workflow run "Debug APK Build" --repo lingion/sleepy \
  --ref main -f ref=feature/example -f abi=armeabi-v7a

# 使用 main 的工作流构建历史标签的 universal 包
gh workflow run "Debug APK Build" --repo lingion/sleepy \
  --ref main -f ref=v1.0.63 -f abi=universal

# 最新主线包，ref 留空，允许使用项目固定 debug 签名
gh workflow run "Debug APK Build" --repo lingion/sleepy --ref main -f abi=all
```

`abi` 可选 `all`、`arm64-v8a`、`armeabi-v7a`（V7A）、`x86_64`、`universal`。单选会改变实际 APK 生成配置，不只筛选上传文件。显式指定 ref 的构建和不同 ABI 请求不会互相取消；同一个目标与架构的后续手动请求会替代之前未完成的请求。

### 1.3 下载、源码身份与签名

进入 PR 检查对应的 Debug APK Build 运行，或直接在 Actions 中找到该次运行，展开构建摘要。每个架构有独立 artifact 下载入口；也可以从运行页面的 Artifacts 区域下载。需登录 GitHub，ZIP 内含 APK、`build-info.json` 和 `sha256sums.txt`，产物保留 7 天。

APK 和 artifact 名包含 PR 或分支上下文、源码短 SHA、架构、运行编号和尝试编号，例如 `sleepy-debug-pr286-a1b2c3d4e5f6-armeabi-v7a-r20-a1`。完整 SHA、实际包名、版本、签名证书指纹和构建地址记录在元数据中。转发 APK 时不要去掉文件名里的源码身份。

```bash
gh run list --repo lingion/sleepy --workflow "Debug APK Build" --limit 5
gh run download RUN_ID --repo lingion/sleepy --pattern '*armeabi-v7a*' --dir ./debug-download
# 进入下载目录后，对照 sha256sums.txt 校验 APK 与元数据
shasum -a 256 -c sha256sums.txt
```

只有上游 `main` push，或从上游 main 手动运行且 `ref` 留空时，才使用 `DEBUG_KEYSTORE_BASE64` 的固定 debug 签名，密钥缺失会直接失败。外部 PR、fork、非主线工作流以及所有显式 ref 手动构建均使用临时签名，不能保证跨次覆盖安装。不要为了安装测试包而随意卸载有数据的旧包。

当前源码的 debug 包使用 `com.lingion.sleepy.debug`，与正式版并存；历史分支可能尚未区分包名，以摘要和元数据中核验出的实际包名为准。历史代码是否还能编译也取决于其 Gradle、AGP 和依赖兼容性，工作流不会改写历史源码来修复构建。

APK 构建成功只代表出了包，PR 的测试与 lint 结果需另看。现有 CI 中的 `apks-debug-signing` 是使用 debug 签名的 release 构建，用于检查 release/R8 链路，不能与本工作流的真正 debug 包混淆。

### 1.4 模拟器测试

当前仓库没有名为 Android Emulator Test 的手动工作流。模拟器或设备测试请按项目实际测试工具执行，不使用此前文档中的失效命令。

### 1.5 iOS 无签名 IPA 构建(见 sleepy-ios 仓库)
```bash
# 在 sleepy-ios 目录 (默认分支是 master)
gh workflow run "iOS Build" -f ref=master -f config=Release
```
- macOS runner 跑 `xcodegen` + `xcodebuild`,产出无签名 IPA(供 AltStore 重签)。
- 本机没有 Xcode,这条路径**只能靠 CI**。

---

## 2. 全自动、你不用管的工作流

### 2.1 Dependabot 依赖升级 (`.github/dependabot.yml`)
每周自动检查 Gradle / GitHub Actions 版本,每月检查 collector 的 Go 依赖;发现新版就**自动开 PR**,走既有 CI 全绿后你再合。你不用手动盯 `libs.versions.toml`。

### 2.2 Nightly main 体检 (`.github/workflows/nightly.yml`)
现有 CI 只在 **PR** 上跑;dependabot 合入或 runner 镜像变更造成的漂移,PR 门禁看不见。Nightly 每天(北京时间凌晨 2:30)对 main 跑一遍 单元测试 + 目录校验 + release 打包,确保 main 永远随时可发布。

---

## 3. 命令速查

```bash
# 列出所有工作流
gh workflow list

# 触发一个(带参数)
gh workflow run "Debug APK Build" -f abi=universal

# 看某次 run 的状态 / 日志
gh run watch <run-id>
gh run view <run-id> --log-failed

# 下载 run 的全部产物
gh run download <run-id>

# 一次性看最近 run 和产物名
gh run list --workflow "Debug APK Build" --limit 5
```

> 提示:`workflow_dispatch` 只有当工作流文件存在于**默认分支(main)**时才能触发。给其他项目加同类能力时,先合进 main 再点,别在临时分支上试。

---

## 4. 免费额度与硬限制(避免踩坑)

- public 仓库:Linux/Windows/macOS runner 全免费,分钟数无限(受 fair-use 约束)。
- 单个 job 最长 **6 小时**,超时被截断。本仓库最重的模拟器 job 设了 60 分钟。
- artifact 保留 1–90 天(本仓库统一 7 天);cache 总额 10 GB/仓库,LRU 淘汰。
- 并发:free plan 最多 20 个并发 job、5 个并发 macOS。所以 `concurrency` 块里都配了 `cancel-in-progress`,同类任务只保留最新一次。
- **secret 不进日志**:本仓库的 `RELEASE_KEYSTORE_BASE64` / `DEBUG_KEYSTORE_BASE64` 只从仓库 secrets 注入,fork PR 读不到,不会泄露。

---

## 5. 给贡献者:怎么加一条新的"白嫖"能力

1. 在 `.github/workflows/` 新建 `*.yml`,优先用 `workflow_dispatch + inputs`(手动、可参数化),别污染 PR 门禁。
2. 密钥只在明确受信任的路径通过 `secrets.*` 注入，禁止向外部源码构建传入已有签名密钥。需要固定签名的主线构建缺密钥必须失败，不能静默回退。
3. 加 `concurrency` + `timeout-minutes` + `permissions: contents: read`(最小权限)。
4. **把它合进 main**,否则 dispatch 点不动。
5. 回到本页 §1/§2 补一行说明,让下一个人知道有这道菜。

需要大算力又装不下的工具(ffmpeg、OCR、大型代码扫描),照这个模式加一个 dispatch workflow 即可,不必要求每个贡献者本地都装。
