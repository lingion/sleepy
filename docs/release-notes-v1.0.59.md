# Sleepy v1.0.59

> Timetable headers stay readable at any card height, Huizhou University can be imported, and update checks accept the new APK file names.

Baseline: the post-squash notes-alignment commit `c7a39ddf` through `origin/main` HEAD `e03adccd` — 9 commits, 19 source and config files, +1157/−173. The `v1.0.58` annotated tag dereferences to `a857154d`, which sits on a parallel history line that rejoins the post-squash `main` only at merge-base `6b491d74`, so a tag-based diff returns 1,349 commits and includes already-released work; `c7a39ddf` is the most recent commit that does not introduce v1.0.59 content. Every statement below is sourced from that range; where evidence is missing, that is stated instead of guessed.

## What's New

### Header text no longer overlaps or gets cut off

The timetable header shows three lines: weekday, start time, end time. The font-size rule used an estimated ratio (`HEIGHT_TO_LABEL_RATIO = 2.6`), so the computed size could be about 13 % taller than the card. With a large system font, the three lines pressed against each other and characters were clipped.

The size formula is now the exact relation between the rendered line box and the font size: `labelSize = (cardHeightSp + 2.5) / 3.75`, with `timeSize = labelSize − 1`. Three line boxes therefore fit the card exactly instead of approximately. The week-view summary card height switched from a fixed `132 × scale` to a content-driven `heightIn(min = 132 dp)`, so large system fonts no longer truncate course names. The card width logic also moved into `BoxWithConstraints` so the column font uses the real row height instead of a `52 × scale` estimate.

When a card is still too short, the label shrinks proportionally below the 11 sp minimum instead of overlapping — the new behaviour prioritises "no overlap" over "never below 11 sp". The card may therefore render very small text in extreme cases; this is intentional and tested.

**Evidence:** PR #96 (merged 2026-10-04T02:30:32Z) and PR #97 (merged 2026-10-04T05:02:27Z). Both PR descriptions record the 13 % figure and the `2.6 → 3.75` formula change. The source `PeriodHeaderLayoutModel.kt` constants `HEIGHT_TO_LABEL_RATIO = 3.75f`, `MIN_LABEL_SP = 11f`, `MAX_LABEL_SP = 16f` confirm the new rule; `PeriodHeaderLayoutModelTest.kt` (lines 124-140, 161) covers the proportional-shrink behaviour. The two PRs name the new test `width_shrink_always_preserves_no_overlap`, but the source test function is `width_shrink_also_preserves_no_overlap` (the PR descriptions have a typo); this file uses the source name.

### Header columns line up on one axis

In the two-line header style, the dash in `08:00–09:35` and the label above it were positioned independently per card, so a column could show several slightly offset dashes. The column now resolves one shared axis from the actual glyph widths, and the card draws the whole header block as a single centred unit. A `sharedLegacyAxis` parameter was wired through `SingleTimeHeadCell → PeriodHeaderCellContent`; before that, every card fell back to an inline axis rather than the column's union axis.

**Evidence:** Commit `e03adccd` is the squash of PR #102 (merged 2026-10-05T11:34:18Z). The commit subject reads `(#96 #97)` because it absorbed those PRs; the squash merge itself is PR #102. The PR description records an emulator check: a legacy five-row header had all dashes land on `x = 177 ± 0 px` (single axis per column), and 2,440 unit tests, 0 failures.

### Header-style preview matches the real grid

On the header-style settings screen, the preview cards for the two-line layout used a fixed width regardless of their text. They are now measured the same way as the three-line layout, including the same minimum width, so the preview shows the width the grid will actually use.

**Evidence:** Commit `e03adccd` (PR #102 squash, merged 2026-10-05), which touches `PeriodHeaderSettingsScreen.kt` and introduces a `legacyLineWidthDp` code path parallel to `threeLineWidthDp`.

### Huizhou University timetable import

Huizhou University (`惠州学院`, `jwxt.hzu.edu.cn`) is now in the school list under the `zf_new` system. Two blockers were removed:

- Section numbers arrive as `8-9节`. The trailing `节` is now stripped before parsing; previously the value failed to convert and the whole row was dropped, producing an empty result.
- The page's currently selected or default semester is now read directly. Previously that value was never supplied to the request, so the import had no reliable semester.

**Evidence:** Commit `f332be9b` (2026-10-05), with the captured fixture `kblist_hzu_huizhou_20261004.json` and parser tests. The commit records a successful real import against the live site. No Issue or PR number was recorded for this change, and none is claimed here.

## Fixes

### Update checks accept the new APK file names

Release assets were renamed to `Sleepy-v<version>-<abi>.apk`, but the client only recognised `app-<abi>-release.apk`. The match failed, the download URL came back empty, and opening the download crashed with `MalformedURLException("no protocol: ")`.

The release-info parser now accepts all three names in this priority order: `app-<abi>-release.apk` first, then `app-<abi>.apk`, then `Sleepy-v<version>-<abi>.apk`. The mirror fallback probes each candidate with an HTTP HEAD and keeps the first one that returns 200. The candidate order is identical for the GitHub API path and the mirror fallback.

**Evidence:** PR #94 (merged 2026-10-03T11:04:14Z), with four new tests covering both naming schemes. PR #94 also notes that the four v1.0.58 assets were temporarily renamed back on the server while this fix shipped.

### The update banner opens the real Releases page

Tapping the "new version" banner opened the mirror host, whose proxied GitHub pages do not render reliably — the manual fallback download path was effectively dead. The banner now opens `github.com/lingion/sleepy/releases/tag/<version>` via the system's browser handler. In-app APK downloads keep their existing mirror-first, GitHub-fallback order.

**Evidence:** PR #95 (merged 2026-10-03T15:43:42Z).

### CI no longer hides failed tests

The unit-test step piped Gradle into `tail`, so the job read `tail`'s exit code instead of Gradle's. A failing test suite was reported as success, which disabled a required status check. The step now runs with `set -o pipefail` and keeps the full log via `tee`.

**Evidence:** Issue #98 (no PR; closed 2026-10-04) records run `37138168029`: `PeriodHeaderAdaptiveFontTest > preview_skips_ink_guard_keeping_baseline FAILED`, then `2426 tests completed, 1 failed`, then `BUILD FAILED in 2m 36s`, while the job was reported `success`. Fixed in commit `e83162bf`. The `android-unit-test` branch-protection required check was effectively a no-op from 2026-09-10 governance #60 onward until this fix.

## Known Limitations

- **Mirror fallback still builds the new file name from the installed version.** `candidatesFor()` uses `BuildConfig.VERSION_NAME` and runs *before* the remote tag is parsed, so a client on 1.0.58 probing a mirror-hosted 1.0.59 asset looks for `Sleepy-v1.0.58-<abi>.apk`. The GitHub API path is unaffected because it reads asset names from the response. Use the banner or GitHub Releases when the mirror path returns no download.
- **Huizhou University has not been checked on physical hardware.** Commit `f332be9b` records a successful import against the live site; no device-specific qualification is recorded.
- **Header layout was verified by tests and an emulator, not by a device font-scale sweep.** PR #96's test plan leaves two items unchecked: "本地 assembleDebug 打包 + 手机系统字体调大回归周视图/网格视图 (另一台机器进行中)" and "fontScale=1.0 默认档视觉回归 (周视图卡片应与 1.0.58 一致)".
- **Vendor live cards still depend on the manufacturer.** Xiaomi Super Island and similar surfaces require vendor services, notification permission, and device support; an AOSP emulator cannot verify their rendering.

## Verification

- Range audited: `c7a39ddf..origin/main` (HEAD `e03adccd`); 9 commits, 19 source and config files, +1157/−173.
- Unit tests: `:app:testDebugUnitTest` — 2,440 tests, 0 failures, 0 errors, 0 skipped.
- Lint: `:app:lintDebug` — 0 errors, 488 warnings (pre-existing baseline; no new errors introduced by this range).
- Build: `:app:assembleRelease` succeeded. `versionName` 1.0.59, `versionCode` 10059, confirmed in `output-metadata.json` for all four variants.
- APK SHA-256 and sizes (published CI build, JDK 17.0.18 / Gradle 9.3.1):
  - `app-arm64-v8a-release.apk`: `cdb3e1226b26dea35e14a6af710377bd29a6cf82a31eb2e90dccda27ce8a490b` (3,790,421 bytes)
  - `app-armeabi-v7a-release.apk`: `e3303345ab8e0700bc2ccecd9d954888349cfff57ef99625ec4ce937b9ca2dbc` (3,787,729 bytes)
  - `app-x86_64-release.apk`: `0f61a90a90514271b3b33936f464ad1ee65054499350d1a9d35e0dc20a37130b` (3,789,527 bytes)
  - `app-universal-release.apk`: `2bf979d64f11e62e07bb0edceaf30030d4abf23b120650f6b9950bbfb17249a3` (3,888,357 bytes)
  - A local rebuild produced byte-identical sizes but different digests (Android APK signing embeds build timestamps and is not byte-reproducible); the digests above are the published assets and are what you should verify your download against.
- Baseline §12: 17 checks reviewed. Code-level items verified by the suite above; the device-only items (physical navigation gestures, real-device font scale, vendor live cards, widget interaction, alarm re-scheduling after reboot) were not executed — no physical device was attached to this build.

---

# Sleepy v1.0.59

> 表头在任何卡片高度下都能看清；支持惠州学院导入；更新检查兼容新的安装包文件名。

基线：squash 后校齐 v1.0.58 笔记的提交 `c7a39ddf` 到 `origin/main` HEAD `e03adccd`，共 9 个提交、19 个源码与配置文件、+1157/−173。`v1.0.58` annotated tag 解引用到 `a857154d`，它处于另一条历史线，只在 merge-base `6b491d74` 与 post-squash 的 `main` 重新汇合，所以直接用 tag 算 diff 会得到 1,349 个提交、把已发的 v1.0.58 内容也算进来；`c7a39ddf` 是最后一个不引入 v1.0.59 改动的提交，从这里往后才是真正的 v1.0.59 增量。以下每一条都来自该范围的实际记录；找不到依据的地方直接写明，不做推测。

## 新增功能

### 表头文字不再重叠或被截断

课表表头有三行：星期、开始时间、结束时间。字号规则原本用的是估算比 `HEIGHT_TO_LABEL_RATIO = 2.6`，计算出的字号可能比卡片高约 13 %。系统字体调大后，三行互相挤压、文字被裁掉。

字号公式现在改成行盒与字号的精确关系：`labelSize = (cardHeightSp + 2.5) / 3.75`，`timeSize = labelSize − 1`，三行行盒刚好等于卡高。周视图周视图摘要卡的高度也从写死的 `132 × scale` 改成 `heightIn(min = 132 dp)` 由内容撑开，系统字体调大不再截课名。列宽计算也下移到 `BoxWithConstraints`，用真实行高替代之前的 `52 × scale` 估算。

卡片确实装不下时，字号会按比例缩到 11 sp 下限以下，优先保证「不重叠」而不是「不低于 11 sp」。极端情况下卡片可能显示非常小的字，这是有意的设计，并已有测试覆盖。

**依据：** PR #96（2026-10-04T02:30:32Z 合并）与 PR #97（2026-10-04T05:02:27Z 合并）。两个 PR 描述都明确写出 13 % 偏差与 `2.6 → 3.75` 的公式变更。源码 `PeriodHeaderLayoutModel.kt` 常量 `HEIGHT_TO_LABEL_RATIO = 3.75f`、`MIN_LABEL_SP = 11f`、`MAX_LABEL_SP = 16f` 与改动吻合；`PeriodHeaderLayoutModelTest.kt` 第 124-140 行与第 161 行覆盖了按比例缩字号的逻辑。两个 PR 描述里把新测试写作 `width_shrink_always_preserves_no_overlap`，但源码函数实际是 `width_shrink_also_preserves_no_overlap`（PR 描述有拼写笔误）；本文以源码名为准。

### 表头各列对齐到同一条基准线

两行式表头里，`08:00–09:35` 中间的横线和上方标签原本各卡片独立计算，同一列可能出现好几条错开的短线。现在整列按实际字形宽度解出一条共用基准线，卡片把整个表头块作为一个整体居中。`sharedLegacyAxis` 参数穿过 `SingleTimeHeadCell → PeriodHeaderCellContent` 一路传到底；之前所有卡片都走 inline 轴而不是列 union 轴。

**依据：** 提交 `e03adccd` 是 PR #102 的 squash 提交（2026-10-05T11:34:18Z 合并）。提交标题里 `(#96 #97)` 表示吸收了这两个 PR；本次 squash 合并本身就是 PR #102。PR 描述记录了模拟器实测：legacy 5 行表头的 dash 全部落在 `x = 177 ± 0 px`（每列共用一条轴），以及 2,440 个单测、0 失败。

### 表头样式预览与真实网格一致

表头样式设置页里，两行式样式的预览卡片用的是写死宽度，与文字无关。现在改成和三行式同一套实测规则、同一个宽度下限，预览出来的宽度就是网格实际会用的宽度。

**依据：** 提交 `e03adccd`（PR #102 squash，2026-10-05 合并），同时改动了 `PeriodHeaderSettingsScreen.kt` 并新增 `legacyLineWidthDp` 代码路径与 `threeLineWidthDp` 并列。

### 支持导入惠州学院课表

学校列表新增惠州学院（`jwxt.hzu.edu.cn`），走 `zf_new` 教务系统。解决了两个卡点：

- 节次返回的是 `8-9节`。现在解析前先去掉结尾的「节」字。之前这个值转换失败，整行被丢弃，结果就是「解析结果为空」。
- 直接读取页面当前选中或默认的学期。之前这个值根本没有传给请求，导入没有可靠的学期。

**依据：** 提交 `f332be9b`（2026-10-05），附带采集数据 `kblist_hzu_huizhou_20261004.json` 和解析测试。提交记录了对真实站点的导入成功。该变更没有登记 Issue 或 PR 编号，这里也不编造。

## 修复

### 更新检查兼容新的安装包文件名

发布资源改名为 `Sleepy-v<版本>-<abi>.apk`，但客户端只认 `app-<abi>-release.apk`。匹配失败后下载地址为空，点击下载抛出 `MalformedURLException("no protocol: ")`。

现在发布信息解析接受三种名称，优先级为 `app-<abi>-release.apk` → `app-<abi>.apk` → `Sleepy-v<版本>-<abi>.apk`。镜像回退用 HTTP HEAD 逐个探测，返回 200 的第一个采纳。GitHub API 通路和镜像回退共用同一份候选列表与同一顺序。

**依据：** PR #94（2026-10-03T11:04:14Z 合并），新增 4 个测试覆盖两种命名。PR #94 同时说明在修复发布前，v1.0.58 四个资产已在服务端临时改回旧名。

### 更新横幅打开真正的 Releases 页面

点击「发现新版本」横幅原本跳镜像站，而镜像代理的 GitHub 页面渲染不全，手动兜底下载等于走不通。现在横幅用系统浏览器打开 `github.com/lingion/sleepy/releases/tag/<版本>`。应用内下载仍保持「优先镜像、失败回退 GitHub」。

**依据：** PR #95（2026-10-03T15:43:42Z 合并）。

### CI 不再掩盖失败的测试

单测步骤把 Gradle 输出接给 `tail`，任务读到的是 `tail` 的退出码而不是 Gradle 的。测试失败也会报成功，等于让必需检查失去拦截作用。现在启用 `set -o pipefail`，并用 `tee` 保留完整日志。

**依据：** Issue #98（不是 PR；2026-10-04 关闭）记录了 run `37138168029`：`PeriodHeaderAdaptiveFontTest > preview_skips_ink_guard_keeping_baseline FAILED` → `2426 tests completed, 1 failed` → `BUILD FAILED in 2m 36s`，但作业结论是 `success`。修复在提交 `e83162bf`。`android-unit-test` 这条分支保护必需检查自 2026-09-10 治理 #60 起事实上是空跑，直到本次修复才真正生效。

## 已知限制

- **镜像回退仍用当前安装版本拼新文件名。** `candidatesFor()` 取的是 `BuildConfig.VERSION_NAME`，并且调用在远程 tag 解析之前，因此 1.0.58 客户端探测镜像上的 1.0.59 资源时，找的是 `Sleepy-v1.0.58-<abi>.apk`。GitHub API 通路不受影响，因为它直接读响应里的资源名。镜像拿不到下载地址时，请走横幅或 GitHub Releases。
- **惠州学院未在实体设备上验证。** 提交 `f332be9b` 记录的是对真实站点导入成功，没有记录机型验证。
- **表头改动只经过测试与模拟器验证，没有做实体机字体缩放遍历。** PR #96 的 Test plan 中两项未勾选：「本地 assembleDebug 打包 + 手机系统字体调大回归周视图/网格视图 (另一台机器进行中)」与「fontScale=1.0 默认档视觉回归 (周视图卡片应与 1.0.58 一致)」。
- **厂商实时卡片仍依赖厂商。** 小米超级岛等需要厂商服务、通知权限和机型支持，AOSP 模拟器无法验证其渲染。

## 验证

- 已审计基线：`c7a39ddf..origin/main`（HEAD `e03adccd`），9 个提交、19 个源码与配置文件、+1157/−173。
- 单元测试：`:app:testDebugUnitTest` — 2,440 个用例，0 失败，0 错误，0 跳过。
- Lint：`:app:lintDebug` — 0 error，488 warning（历史存量基线；本范围未新增 error）。
- 构建：`:app:assembleRelease` 成功。四个变体的 `output-metadata.json` 均确认 `versionName` 1.0.59、`versionCode` 10059。
- APK SHA-256 与体积（已发布 CI 构建，JDK 17.0.18 / Gradle 9.3.1）：
  - `app-arm64-v8a-release.apk`：`cdb3e1226b26dea35e14a6af710377bd29a6cf82a31eb2e90dccda27ce8a490b`（3,790,421 字节）
  - `app-armeabi-v7a-release.apk`：`e3303345ab8e0700bc2ccecd9d954888349cfff57ef99625ec4ce937b9ca2dbc`（3,787,729 字节）
  - `app-x86_64-release.apk`：`0f61a90a90514271b3b33936f464ad1ee65054499350d1a9d35e0dc20a37130b`（3,789,527 字节）
  - `app-universal-release.apk`：`2bf979d64f11e62e07bb0edceaf30030d4abf23b120650f6b9950bbfb17249a3`（3,888,357 字节）
  - 本地重新构建得到完全相同的字节数但不同的摘要（Android APK 签名内嵌构建时间戳，非字节可复现）；以上摘要即已发布资产，请以它们校验你下载的 APK。
- 基线 §12：17 条逐条过。代码层项由上述测试套件验证；需要实机的项（实体机返回手势、真机字体缩放、厂商实况卡片、小组件交互、重启后闹钟重排）本次未执行——本次构建未接实体设备。
