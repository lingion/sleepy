# WakeUP 6.6.2 教务目录吸收 — 协议 matrix（SOP Step 4）

日期: 2026-10-28 · 分支: feat/wakeup-catalog-absorb
输入: 解密官方 AdapterInfo dump 3570 条（wakeup 2268 + ziyan 1302）。
引擎: scripts/wakeup_662_batch_promote.py（三分法 decide + 安全闸 + 域冲突闸）。

## 1. 官方 sourceType → Sleepy mappedType 对照（晋升 64 条全量）

| 官方 sourceType | 条数 | 映射到 | 依据 |
|---|---|---|---|
| xju_post | 14 | xju_post | official-routable（同源 majority 亦同值） |
| south_soft | 12 | south_soft | official-routable |
| jz | 10 | qz | established-majority（既有 wakeup-* admissions 中 jz→qz 多数） |
| zf | 9 | zf_new | established-majority（zf→zf_new 多数，52.36 先例） |
| suda_post | 5 | suda_post | official-routable |
| cumtb | 2 | cumtb | official-routable |
| qz | 2 | qz | official-routable |
| qz_fspt | 1 | qz | established-majority（qz_fspt→qz 家族归并先例） |
| shuwei_new | 1 | shuwei_json | established-majority |
| sues | 1 | eams5 | established-majority（sues 行实际为 eams5 协议,前战役实测） |
| kingo_new | 1 | kingo_new | official-routable |
| nwpu_post | 1 | nwpu_post | official-routable |
| thu | 1 | thu | official-routable |
| ucas | 1 | ucas | official-routable |
| umooc | 1 | umooc | official-routable |
| urp_new | 1 | urp_new | official-routable |
| ecupl | 1 | ecupl | official-routable |

9 个全新协议家族无一被丢弃；jz→qz、zf→zf_new、qz_fspt→qz、shuwei_new→shuwei_json、sues→eams5 五组改道全部来自既有 wakeup-* 准入记录的多数派，非临时发明。

## 2. decide() 三分法与 sg_asset 精确命中闸

1. established-majority: 官方 sourceType 在既有 wakeup-* admissions 的 sourceType→mappedType 表里多数占比超过一半、目标 routable、且通过 declarable 守卫。
2. official-routable: 官方 sourceType 本身 ∈ ROUTABLE 且 declarable。
3. shiguang-manifest-exact-hit: 仅当 NFKC(canonical 行名) 精确等于拾光 manifest sgName 才允许 sg_asset。

declarable 守卫（本战役第二次修正落点）: sg_asset 无论由哪条 basis 产生，都必须 manifest 精确命中，否则 register-only。根因: JwSgAssetBridge 按目录行名逐字查脚本，「同济大学 - 研究生」命中 majority→sg_asset 但 manifest 只有「同济大学」——晋升即运行时「该校暂无拾光适配脚本」死条目。实测拦下 1 条（tju 研究生行，见 /tmp/662-taxonomy.json sg-asset-no-manifest-exact-hit）。

## 3. 3570 行全量终态对账（每一行有归属）

| 终态 | 行数 | 含义 |
|---|---|---|
| no-url | 479 | 官方行无 URL（纯分类占位），仅登记 sources |
| same-url-already-admitted | 2554 | 同 canonical 名 + 同归一化 URL 已准入，零写入（幂等主干） |
| operational-existing-untouched | 364 | 目录行已 operational 且 URL 不同，不覆盖在用品 |
| promoted | 64 | 新 admission 46 + 就地更新 18（既有 52.36 pending 行换 6.6.2 官方 URL） |
| redacted-credential-or-required | 57 | 脱敏后必需参数仍含 REDACTED，安全闸拦截，仅登记 |
| nonroutable-no-parser | 37 | 官方型不可路由且无 manifest 命中（ziyan 30 + 研究生独占入口 7 类），仅登记 |
| duplicate-662-name | 13 | dump 内部同名重复行，取先者 |
| sg-asset-no-manifest-exact-hit | 1 | majority 指到 sg_asset 但行名无精确脚本，register-only |
| other-register-only | 1 | 探针标题含 REDACTED，register-only |
| 合计 | 3570 | |

## 4. 探针证据形态（corroborating，never a precondition）

64 条晋升行的 HTTP 终态: 200 x46 · 302 x5 · 0(不可达) x11 · 403 x1 · 412 x1。
按用户裁决，探针失败不是拒绝理由: 200 记 availability=public-page，其余记 declared-entry；status 一律 unverified，证据 source=wakeup-6.6.2-official-asset、status=unknown、confidence=low、authenticatedImportTested=false。
journal: scripts/data/wakeup-662-probe-journal.jsonl（键 sourceRow，append-only，重跑零新探针）。

## 5. 生成器域冲突闸拦截清单（21 条，安全闸，用户明示保留）

晋升 64 → 生成后 43 条落地 operational（retained-wakeup-662-declared-owner=43 与 manifest.json 对账吻合），21 条停 pending。根因: 这 21 行的 grad 入口与同校已 operational 的本科行 registrable 域相同，catalog_ownership 域冲突闸（防同名异主 URL 劫持在用品）按既有纪律 withhold，非引擎缺陷。名单:

上海大学（2024新版） - 研究生(jz) · 东南大学 - 研究生(jz) · 中国人民大学 - 研究生(jz) · 中国农业大学 - 研究生(south_soft) · 中国矿业大学 - 研究生(xju_post) · 中国矿业大学（北京） - 研究生(xju_post) · 中国科学院大学 - 研究生(ucas) · 中国药科大学 - 研究生(south_soft) · 北京林业大学 - 研究生(south_soft) · 北京理工大学 - 研究生(jz) · 华南农业大学 - 研究生(xju_post) · 南京大学 - 研究生(jz) · 南方医科大学 - 研究生(xju_post) · 四川大学 - 研究生(jz) · 延边大学 - 研究生(south_soft) · 新疆大学 - 研究生(xju_post) · 武汉轻工大学 - 研究生(xju_post) · 清华大学（反馈群：807321351） - 研究生(thu) · 石河子大学 - 研究生(south_soft) · 重庆移通学院(zf) · 青岛理工大学 - 研究生(xju_post)

全部 21 行已在 sources 登记、admission 已写入、证据已落，用户可在导入界面按目录 pending→unverified 通路手工选用官方 URL。

## 6. 一致性不变量（本战役第三次修正落点）

生成器从 admission.probe.finalUrl 写 row.url；apply_ownership 要求 evidence.url == row.url，否则抛 Stale ownership evidence（实证: 牡丹江师范学院 moe:4123010233）。引擎据此把单一 resolved URL = canonical_url(probe.finalUrl or declared) 同写 probe.url / probe.finalUrl / evidence.url / evidence.finalUrl 四字段——与 shiguang_batch_promote.py 先例逐字节同构（该先例数据实测 2021/2189 条 admission.url==evidence.url 零偏差）。
