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

## 1. 手动触发的工作流 ("点菜")

这些用 `workflow_dispatch` 触发,**不占用合入门禁**,想打就打。触发后产物存 7 天,可在 run 页面下载,或让手机/浏览器直接拉(别走本地慢代理)。

### 1.1 打 debug APK(任意架构 / 任意分支)
```bash
gh workflow run "Debug APK Build" -f abi=arm64-v8a -f ref=main
# abi 可选: all | arm64-v8a | armeabi-v7a | x86_64 | universal
```
- 用**项目自己的 debug keystore**(`DEBUG_KEYSTORE_BASE64` secret)签名,CI 产物与本地 `./gradlew assembleDebug` **签名一致,可互相覆盖安装**,数据不丢。
- 下载产物:`gh run download <run-id> -n sleepy-debug-apk-<abi>-<n>`

### 1.2 跑模拟器测试(Room 迁移 + Compose 像素)
```bash
gh workflow run "Android Emulator Test" -f ref=main
# 只想跑某几个类: -f tests=com.lingion.sleepy.data.AppDatabaseMigrationTest
```
- 白嫖 runner 的 KVM 硬件加速,这是本地最难复现的一环。
- 结果以 XML 报告 artifact 形式存 7 天。

### 1.3 iOS 无签名 IPA 构建(见 sleepy-ios 仓库)
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
2. 敏感值(密钥、token)一律走 `secrets.*`,缺失时**优雅回退**并打印提示,不要直接 fail。
3. 加 `concurrency` + `timeout-minutes` + `permissions: contents: read`(最小权限)。
4. **把它合进 main**,否则 dispatch 点不动。
5. 回到本页 §1/§2 补一行说明,让下一个人知道有这道菜。

需要大算力又装不下的工具(ffmpeg、OCR、大型代码扫描),照这个模式加一个 dispatch workflow 即可,不必要求每个贡献者本地都装。
