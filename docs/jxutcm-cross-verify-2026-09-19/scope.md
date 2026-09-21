# 江西中医药大学跨仓验证 — 立项与范围

> 日期: 2026-09-19 · 分支: `adapt/jxutcm-cf-new` · 基线: `origin/main` `cf46c6eb` · PR: `lingion/sleepy#50`

## 用户原话

> 第 50 号可以拉取合并，然后我们小改小改，然后按照 SOP 走一趟，也是拉到一个新分支。

本轮目标：在隔离分支接收 PR #50 的 `cf_new` 新青果 NTSS 适配，补齐协议联动、fixture、回归测试和跨仓验证归档；不 push、不关闭 PR/issue、不发送外部评论。

## 学校信息

- **学校全称**: 江西中医药大学 (Jiangxi University of Chinese Medicine)
- **英文缩写**: JXUTCM
- **slug**: `jxutcm`
- **学校入口**: `https://jiaowu.jxutcm.edu.cn/`
- **适配类型**: 初次适配 / 新协议接入
- **协议类型**: `cf_new`，新青果 NTSS / FullCalendar 形态
- **涉及现行 parser**: 新增 `JwCfNewParser`；不改旧 `JwChengFangParser`
- **适配入口**: `JwWebViewLoginScreen.kt` 中 `CF_NEW_FETCH_JS` 桥接；`JwImportViewModel` URL/HTML 识别；`JwParserRegistry` 工厂路由

## Git 历史

- `dfbd5279`：PR #50 原始 head，新增 cf_new parser、WebView fetch、学校条目和相关接线。
- `3aad673c`：本分支从最新 `origin/main` 合并 PR #50。
- 本轮修正均位于隔离 worktree `/Users/lingion_k/sleepy-worktrees/pr50-cfnew`，未 push。

## 证据边界

PR #50 的代码注释声称有 2026-09 采集包实锚，但该采集包及原始响应不在当前 checkout、PR 文件或测试资源中。本轮不把这条注释当作可复核的现场证据：

- 公开检索命中 4 个候选，均不是教务课表协议实现；详见 `candidates.json` / `findings.json`。
- 当前 fixture 是依据 PR 代码字段契约制作的脱敏合成样本，明确标注在 `app/src/test/resources/jw_fixtures/cf-new/README.md`。
- 真实登录态响应、网络请求序列、页面 DOM 和账号无关脱敏包仍是待补证据。

## 完成门槛与状态

1. 学校双份 `schools.json` 条目：✅ PR 已提供并通过现有一致性测试。
2. parser registry / `ALL_TYPES` / displayName / category：✅ 本轮补齐。
3. cf_new fixture 与 parser 单测：✅ 合成 fixture + 7 tests；真实采集 fixture：⏳
4. 跨仓检索矩阵与一一对应 findings/attribution：✅ 4 候选全量记录。
5. 应用内新增致谢：不适用；4 候选均无协议直接证据，不把无关仓库写成代码来源。
6. 全量 unit test 与 lint：⏳ 当前已跑目标测试；全量测试/lint 待最后验证。
7. 外部动作：未 push、未评论、未关闭任何 PR/issue。
