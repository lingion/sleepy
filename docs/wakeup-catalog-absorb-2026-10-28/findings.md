# WakeUP 6.6.2 教务目录吸收 — 发现与根因链（SOP Step 3/4/5 汇写）

日期: 2026-10-28 · 分支: feat/wakeup-catalog-absorb

## 1. 解密链定案（复盘自 v6.1.70 战役, 本轮直接复用）

AdapterInfo 密文读取路径: readText → trim → 字符串整体反转 → Base64 decode → AES-128-CBC（key == IV == 固定常量）→ Base64 encode → GZIP → JSON。密文常量属逆向证据记录，仅存分析文档，永不入 app 管线（用户定令）。
明文 dump 3570 条 = wakeup 2268 + ziyan 1302；根键 _meta/data/release/versionStatus/versionTimeLine；引擎兼容 records/data 双键写法。

## 2. 引擎三轮修正根因链（全部实证, 非理论风险）

### 修正一: 同 URL 比较不对称 → 候选数 86 膨胀到 1490
现象: dry-run 探针量爆炸, 任务超时被停。
根因: 比较「6.6.2 行与既有 admission 是否同 URL」时只对 admission 侧套 canonical_url（丢 query/fragment），6.6.2 侧用裸 sanitize 值，两侧永远不等 → 全量误判「未准入」。
修复: norm_cmp 双侧对称比较（scheme/host/port/path/query、去 fragment、默认端口归一），promote 引擎 wakeup_662_batch_promote.py:88。
教训: 幂等判据必须双侧同构，单侧归一 = 判据失效。

### 修正二: sg_asset 死条目 → declarable 守卫
现象: 同济大学 - 研究生被 established-majority 改道 sg_asset，manifest 实际只有「同济大学」本科脚本。
根因: JwSgAssetBridge 按目录行名逐字（NFKC）查脚本，本科名命中 ≠ 研究生行命中；晋升即「该校暂无拾光适配脚本」运行死闸。
修复: decide() 内 declarable() 守卫——sg_asset 无论 majority 还是 official-routable 还是 manifest-hit 哪条 basis 产生，都必须 exact manifest 命中，否则 register-only（wakeup_662_batch_promote.py:170）。回滚重跑，promoted 65→64。

### 修正三: Stale ownership evidence 异常 → resolved URL 单值约定
现象: 生成器对牡丹江师范学院（moe:4123010233）抛「Stale ownership evidence」。
根因: admission.probe.finalUrl（重定向终点）与 evidence.url（声明 URL）分叉；生成器从 probe.finalUrl 写 row.url，而 apply_ownership 断言 evidence.url == row.url。
修复: 引擎把单一 resolved URL = canonical_url(probe.finalUrl) 同写 probe.url / probe.finalUrl / evidence.url / evidence.finalUrl 四字段，与 shiguang_batch_promote.py 约定同构（数据实测该先例 2021/2189 条 url==evidence.url 零偏差, 先验证再抄, 不凭印象）。回滚重跑走全缓存 journal, 零新探针。

## 3. 数据写入终态（全部实数, 来自落盘文件复算）

- scripts/data/wakeup_school_sources.json: records 9327 条（6.6.2 增 3548）+ excludedTemplates 66（6.6.2 增 22）+ metadata.wakeup662Asset（decryptedSha256 / versionTimeLine / license 口径: 未找到再分发许可, 仅事实性元数据, 不含应用代码）。
- scripts/data/school_import_admissions.json: 总 2721 条 = 662 新建 46 + 就地更新 18（既有 52.36 pending 行换官方 URL, 打 sourceType662 字段留痕）。就地更新而非追加第二 admission 的理由: 生成器按文件序应用 admission, 同 catalog 行双 admission 会让结果依赖 append 顺序——单 resolved URL 纪律。
- scripts/data/school_url_evidence.json: 662 源条目 64（public-page 46 / declared-entry 18）, status=unknown, confidence=low, authenticatedImportTested=false, defects 声明第三方目录所有权。
- scripts/data/wakeup-662-probe-journal.jsonl: 探针 journal, 键 sourceRow, append-only, 重跑幂等。
- catalog_ownership.py: 新谓词 wakeup_662_declared_owner（208 行）——无活性要求（全量吸收裁决）+ 安全三条件（valid_url / 无 REDACTED / 公网主机）; outcome() 新分支 retained-wakeup-662-declared-owner（486/504 行）; candidate_keys 已含。测试 51 全绿（含三新用例: 无活性全 retained / 安全闸五反例 withheld / grad 身份保 grad_unverified）。

## 4. 生成结果与对账

- app/src/main/assets/schools.json: catalogCount 4140; statusCounts supported 349 / grad_supported 2 / unverified 1241 / pending 2247 / grad_pending 275 / grad_unverified 26。
- 本次新晋 operational 43 行（grad_unverified 26 + unverified 17）; 21 行被域冲突安全闸拦停在 pending（名单见 protocol-matrix.md 第 5 节, 属用户明示保留闸的正确行为）。
- docs/catalog/manifest.json 动作计数与 64 条晋升完全对账: retained-wakeup-662-declared-owner 43 + withheld-domain-collision（本批占 21）+ 其余为既有行动作。
- CI python 闸全绿: url_safety / ownership / school_locations / enrich_school_cities / build_school_location_evidence / generate_schools_list; generate_school_catalog.py --check 幂等通过。

## 5. ziyan 1302 行去向说明

ziyan 型默认非路由（app 内无通用教务通用解析器）, 其中 majority 表把部分 ziyan 改道到具体型（先战役实证）。本轮 ziyan 行绝大多数落在 already-admitted / operational-untouched / no-url 桶; 37 行 register-only-no-parser 全清单见 protocol-matrix.md 第 3 节口径, 仅登记不晋升（无 parser 即无导入通路, 晋升是假可用）。

## 6. 边界交叉验证（源 E LumaSchedule, Apache-2.0）

检索矩阵触达 Junyxor/LumaSchedule（https://github.com/Junyxor/LumaSchedule，Apache-2.0，未归档，2026-10-27 仍活跃），触达理由与 2026-10-07 新 URP 超级解析器战役同仓（docs/history/new-urp-super-parser-cross-verify-2026-10-07/candidates.json:16443 已登记 html_url）。其 scripts/convert-wakeup-school-info.mjs 与 docs/wakeup-compat.md（本地调研副本存 /tmp/wakeup_catalog/luma/）属 WakeUp 兼容层证据: 只做 profile 与登录元数据迁移, 不复制 WakeUp 服务端解析器。结论: 与 Sleepy 管线边界一致——吸收的是事实性目录元数据（URL/名称/类型声明）, 不搬运任何一方应用代码。按「凡触达必致谢」纪律, 致谢已落 LicenseScreen.kt + 6 语 strings.xml + AboutLicenseAttributionTest（Step 5.5, 先于数据写入完成）。
