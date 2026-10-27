# Step 5 现状代码阅读 + Step 6/7 落地面 — 南开

## 立项时现状（bucket-S.md 精要）

- 注册: schools.json:1829 南开条目 `type=classic_eams`, 根 URL, 默认 supported, enableFetch 未开。
- 通路: NBCC 战役（PR#232, 已合 main）已建好 `TYPE_CLASSIC_EAMS ∧ enableFetch → CLASSIC_EAMS_FETCH_JS` 调度、ClassicEamsWebVpn.kt 共享常量、JwClassicEamsParser（纯内联 JS 解析, 不导航）。
- 缺口: 南开用户直接导入根 URL → 148 字节壳 → findAnchors 空 → WRONG_PAGE「请先在门户进入」。同族 NBCC 入口在 VPN 域内, 无需自动导航; 南开入口在子路径, 家族里第一次出现「同源 GET 入口页再提取 scope」需求。

## 本轮落地面（Step 6/7, 全部在本 worktree 未提交改动内）

1. `ClassicEamsWebVpn.kt`: 新增 `CLASSIC_EAMS_NANKAI_HOST_RE`（host 判据）+ fetch JS 南开段 `tryEntryPage()`: hostname 命中 eamis.nankai.edu.cn 且 scope 未找到 → entryTried 一次闸 → XRW GET `courseTableForStd.action`（`new URL(...,pageUrl)` 同源解析）→ loginRe 命中回「登录态已失效…」→ `document.implementation.createHTMLDocument('')` 离屏解析 → `findScope(d, entryUrl)` 复用同一 scope 提取器 → 成功即走原 run() 提取/POST 链, 失败保「请先在门户进入」双提示。
2. `JwWebViewFrameCapture.kt`: ANCHORS 增 `manualArrangeCourseTable`（课表动作页锚点, 原仅 coursetableforstd/form）; LOGIN_FINGERPRINTS 增南开 IAM 对（`password_account_input` + `iam.nankai.edu.cn`）。
3. `JwWebViewLoginScreen.kt`: dispatcher `if (scope) run(); else tryEntryPage(...)`; 原提取链不变。
4. `schools.json`（主+测试双写）+ catalog manifest/baseline: 南开 `enableFetch: true`。
5. fixture 四件: `nankai-entry-page.html`（学期真实形态 `{onChange:"",value:"4384"}` 不引号 key）、`nankai-course-table-action.html`（三块 23 行, 含线性 index=42 与 join-teachers 反查）、`nankai-iam-login-page.html`（双指纹登录页）+ 148B 根壳反例（测试内联断言）。
6. 测试面: NankaiClassicEamsWebViewContractTest 5 条（跨语言 invariant / 行为面 / 形态B 零凭据复检 / 双源提取候选体 / isLoginUrl）; FrameCapture +2; Parser +1（南开样本 confidence 95 五锚 + 23 行行数锁）; ProtocolDetection +2（课表直链判 classic_eams, 根 URL 判 null）; Schools179 pinned 南开开闸钉死（**故意不断言 status**, 等 zzask77 活体回传再裁）。

## Step 7 抓到的两个自造错误（教训入档）

1. **行数语义**: indexPairs 经 mergeConsecutiveNodes 折成单 slot, 块展开按周位数, 期望 23 非 35（blocks×weeks 朴素乘积错）。
2. **学期正则**: `CLASSIC_EAMS_SEMESTER_CAL_RE` 要求不引号 key `value:"4384"`（真实页面形态）, 初版 fixture 写成引号 JSON `{"value":"4384"}` → extractSemesterId null。NBCC 动作页 fixture 用引号形态无害（无测试从其提取学期）, 入口页 fixture 才必须真形态。两枚测试首跑即抓到, 证明新测试有效。

## 未触碰面（边界声明）

解析器 JwClassicEamsParser 零改动（南开页面无新方言, 线性 index 形态已有测试覆盖）; detectProtocolFromUrlImpl 零改动（根 URL 判 null 系正确行为, 目录 type 权威）; 凭据链路零接触（形态B）。
