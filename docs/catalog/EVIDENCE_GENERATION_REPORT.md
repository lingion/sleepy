# Wave 2/3 证据生成报告（PR #179）

数据基准：2026-10-17。本报告汇总四个阶段的证据管线结果；所有数字都可以追溯到仓库内的证据文件与脚本，可复现、可审计。

复现顺序：

1. `python3 scripts/adjudicate_protocol_mapping.py --write` — 逐行协议裁定（回放 `scripts/data/batch-probes.jsonl` + `scripts/data/entry-evidence-captures.jsonl`）
2. `python3 scripts/capture_entry_evidence.py --resume` — 补抓公开入口页
3. `python3 scripts/integrate_batch_evidence.py` — 把捕获整合进 `scripts/data/school_url_evidence.json`
4. `python3 scripts/generate_school_catalog.py && python3 scripts/generate_schools_list.py` — 重建目录与人读清单

## 阶段 1 — 逐行协议裁定

输入是 WakeUp 来源的 2,499 条批量 admission（`scripts/data/school_import_admissions.json` 第 171 行起，前 170 行为冻结基线）。每一条只有两种合法证据类别：

- **URL 强特征**：路径/参数锚定已知协议（如 `/jwglxt/xtgl/login_slogin.html` → 金智、`/eams/homeExt.action` → 强智 classic_eams、`zxkd` → kingo_new）；
- **页面指纹**：脱机捕获的入口页 HTML 中的产品名、表单 action、资源路径指纹。

CAS / authserver / 通用门户壳一律**不构成**协议证据；不做批量猜测；无充分证据即 fail-closed 留在 `ziyan` 等待人工适配。每条裁定连同 `type|source|label` 证据写入 `scripts/data/adjudication-log.jsonl`，全 2,499 行的裁定结果表见 **[PROTOCOL_MAPPING.md](PROTOCOL_MAPPING.md)**。

当前状态（本轮 13 行由新捕获指纹改判后）：

| 项目 | 数量 |
|---|---|
| 批量 admission 总数 | 2,499 |
| 已映射到具体协议 | 2,499 |
| 映射到应用内**已注册解析器**（可路由） | 1,581 |
| 映射到无解析器协议（fail-closed，永不可运营） | 918 |

改判示例（均有日志证据）：兰州/吕梁职业技术学院 ziyan→kingo_new；河南广播电视大学/河南开放大学/郑州信息科技/长江大学文理/马鞍山 ziyan 或 shuwei_m→classic_eams（eams URL 锚点）；江西飞行/牡丹江医科/长春工业人文→cf；苏州工学院/贵州民用航空→zf_new；锦州医科大→qz。

## 阶段 2 — 入口页证据捕获与整合

`capture_entry_evidence.py` 只抓取 probe http==200 的公开入口页（无登录、无第三方），追加写入 `scripts/data/entry-evidence-captures.jsonl`（3,031 行，其中 1,585 条可用捕获；HEAD 中 1,808 行保持字节级不变，只追加不修改）。`integrate_batch_evidence.py` 将其折叠进 `scripts/data/school_url_evidence.json`：

| 项目 | 数量 |
|---|---|
| 证据条目总数 | 2,088（批量 1,569 / 基线 350 / 导入 169） |
| 批量 admission 中有证据行（active，去重后） | 1,569 |
| — verified（来源页证明身份） | 389 |
| — unknown（身份不足证明，fail-closed） | 1,180 |
| 全库所有权裁定 | verified 737 / unknown 1,350 / contradicted 1 |
| evidence == active 不变式 | 0 missing / 0 stale / 0 empty-url / 0 url-mismatch |

两类被主动排除、未强行给证据的行：918 行无解析器协议（unroutable-type）、488 行 probe 非 200、12 行无 finalUrl。

### 修复的 NEL 数据完整性缺陷

捕获页正文含 `\x85`(NEL)/`\u2028`/`\u2029` 时，`str.splitlines()` 会把 JSONL 单行误当作多行边界，`clean_journal` 随之把合法行当“损坏”静默删除。已在全部读写点修复：统一 `split('\n')`，写入时转义这三个字符。修复后两条此前被跳过的 probe-200 端点（文华学院、长沙电力）补抓成功，并按证据正确 fail-closed 为 unknown（泛化产品标题不具校名身份）。证据日志完整性以 HEAD 前 1,808 行逐字节比对验证。

## 阶段 3 — 目录重建与验证

`generate_school_catalog.py`（`--check` 幂等通过）+ `generate_schools_list.py` 重建，断言冻结基线不被改写、无 pending-with-url、无新增 supported：

| 指标 | 上一轮（2026-10-16） | 本轮（2026-10-17） |
|---|---|---|
| 目录条目 | 4,140 | 4,140 |
| 已实现配置（supported + grad_supported） | 350 | 350（未变） |
| 待验证·可尝试（unverified） | 418 | **525** |
| 待适配（pending + grad_pending） | 3,372 | **3,265** |
| 带教务入口 URL 的条目 | 873 | **875** |

关于 1,569 条 active 证据行与 875 条带 URL 条目的差额（所有权闸门，`catalog_ownership.apply_ownership`，fail-closed 设计，与历史 2,185→873 行为一致）：

| 所有权动作 | 数量 | 结果 |
|---|---|---|
| ownership-confirmed（verified 且来源页证明身份） | 737 | 保留/新启用 URL |
| retained-existing-config-ownership-unresolved | 137 | 历史配置保留 URL |
| corrected-official-entry | 1 | 官方链路纠错后启用 |
| withheld-new-unproven-owner | 1,213 | 新条目身份未证明 → 置 pending、清空 URL |

唯一 contradicted 行（潍坊护理职业学院错校入口）经官方公告 + HTML 刷新 + 连续 HTTP 跳转链证明后替换为实名入口（corrected-official-entry），仍为 unverified、enableFetch=false。

即：1,213 条“URL 可达、协议可路由，但公开证据不足以证明该入口属于这所学校”的行被刻意拦下——收录不等于支持，宁可少放不可放错。`docs/catalog/manifest.json` 的 `ownershipAudit` 保存每条决定，`docs/catalog/import-admissions.json` 收录全部 525 条可尝试行（各带 `limitation` 声明：仅有公开入口与协议族证据，未做登录态导测）。

测试（串行执行，避免 KSP 并发冲突）：

- Python：ownership 32 项、URL 脱敏 7 项、pytest 61 项 — 全绿
- Gradle：`:app:testDebugUnitTest`（含 SchoolsJsonConsistencyTest、Schools179CrossValidationTest、JwImportViewModel 系列）、`:app:lintDebug`、`:app:assembleDebug` — 全部 BUILD SUCCESSFUL

## 阶段 4 — 说明与用户可见口径

- **[学校目录](../schools-list.md)** 计数与资产一致：4,140 条目；已实现配置 350、待验证·可尝试 525、待适配 3,265。
- 应用内学校选择页只展示带 URL、可实际尝试的 875 个条目（`JwImportViewModel` 过滤无 URL 行），不再有 4,140 全量列表造成的“灰项不可用”困扰；该 UX 修复记录已并入[目录来源说明](README.md)。
- 与旧指令中 2,185 预期 active 数的差异：该数字产生于逐行裁定之前；裁定把 918 行判为无解析器协议、并剔除了 CAS 壳等伪证据后，1,739（去重 1,569）才是证据正确的 active 规模。


## 遗留（不在本轮自动化范围）

- 918 行无解析器协议：需要先在应用内注册对应解析器或人工适配（`ziyan` 等待办见 PROTOCOL_MAPPING.md）。
- 1,213 行 withheld：需要官方校页→教务入口的证据链（meta-chain / 官方公布地址）才能解锁。
- 289+ 城市映射、单校登录态实机验证：按 `docs/adapt-kit` 与 issue 模板推进。
