# WakeUP 6.6.2 教务目录吸收 — 现状代码阅读（SOP Step 5）

日期: 2026-10-28 · 分支: feat/wakeup-catalog-absorb

## 1. 管线数据流（入口 → 落点，全部实读）

```
APK 资产(用户自有 6.6.2, 离线)
  └─ 解密 dump docs/wakeup-catalog-absorb-2026-10-28/wakeup-6.6.2-school-info-decrypted.json
       └─ scripts/wakeup_662_batch_promote.py           ← 本战役新建引擎
            ├─ 写 scripts/data/wakeup_school_sources.json   (records + excludedTemplates + metadata.wakeup662Asset)
            ├─ 写 scripts/data/school_import_admissions.json (新 admission + 就地更新)
            ├─ 写 scripts/data/school_url_evidence.json      (source=wakeup-6.6.2-official-asset)
            └─ 追加 scripts/data/wakeup-662-probe-journal.jsonl (探针 journal, 键 sourceRow)
                 └─ scripts/generate_school_catalog.py:70 main()
                      ├─ 消费 admissions → 行 url/status
                      ├─ scripts/catalog_ownership.py:477 apply_ownership()
                      │    ├─ :486 outcome() → :504 retained-wakeup-662-declared-owner 新分支
                      │    └─ :208 wakeup_662_declared_owner() 新谓词（无活性要求 + 安全三条件）
                      └─ 输出 app/src/main/assets/schools.json + test resources + docs/catalog/* + schools-list.md
```

## 2. 运行时消费面（app 侧零改动，本轮确认）

- app/src/main/java/com/lingion/sleepy/data/jw/JwProtocol.kt — type 字符串到解析器的路由表；本轮全部 mappedType ∈ 既有 routable 集（scripts/routable_types.py:18 routable_types() 为单一真源），运行时不需新解析器。
- app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwSgAssetBridge.kt — sg_asset 通路按目录行名逐字查拾光脚本；这就是引擎 declarable 守卫的代码级依据（行名不精确命中 = 运行时死条目）。
- app/src/main/java/com/lingion/sleepy/data/jw/JwSgAssetParser.kt — 拾光资产解析器,只读确认,无改动。
- app/src/main/assets/third_party/shiguang/manifest.json — sgName 精确命中集合的输入（引擎 sg_names 只读引用）。
- SchoolSelectScreen / JwWebViewLoginScreen — 目录行消费方,status=unverified 行 enableFetch=False、用户自选导入的既有 UI 语义不变。

## 3. 错误路径与守卫点（既有,本轮复用）

- scripts/batch_url_admissions.py:192 probe_url(url, timeout=10) — 探针实现,引擎直接复用;失败不拒绝,只影响 availability 标注。
- scripts/catalog_url_safety.py:14 sanitize_url — 会话参数 REDACTED 化(jsessionid/(S(...))/_key/ticket/state);required 参数仍 REDACTED → 引擎拒晋升。
- scripts/integrate_batch_evidence.py:53 canonical_url — 去 query/fragment 的落盘规范化;resolved URL 单值约定的组成件。
- catalog_ownership 域冲突闸 — grad 入口与同校 operational 本科行 registrable 域相同时 withhold(21 行,protocol-matrix.md 第 5 节),防同名异主 URL 劫持在用品;用户明示保留的唯一筛选类安全闸。

## 4. fixture 与测试面

- scripts/test_catalog_ownership.py — 51 tests 全绿;本战役新增三用例: 662 声明无活性全 retained / 安全闸五反例 withheld / grad 身份保 grad_unverified(红绿闭环:先写失败用例再落谓词)。
- app/src/test/resources/jw/schools.json — 与 assets 同步再生成(生成器落点之一)。
- app/src/test/java/com/lingion/sleepy/AboutLicenseAttributionTest.kt — FOUNDATIONAL_ATTRIBUTIONS 增 LumaSchedule 条目,ALL_RELEASED_LOCALES 六语(values, values-zh-rCN, values-zh-rTW, values-en, values-ja, values-es)断言致谢 token 齐备。

## 5. 既有惯例对照(先验证再抄,不凭印象)

- shiguang_batch_promote.py — 就地更新既有 pending admission 而非追加第二行;resolved URL 同写 probe/evidence 四字段。引擎逐条对照其数据(2021/2189 条 url==evidence.url)后采纳同构约定。
- 52.36 ziyan 战役 — sourceType662 字段留痕方式仿其 sourceType 换代先例。
- 6.1.06 先例 — 模板名行入 excludedTemplates(本轮 22 行),record 形态(sourceImportType 等字段)逐字节同构。
