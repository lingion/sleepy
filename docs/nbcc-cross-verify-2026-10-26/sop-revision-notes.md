# SOP v1.14 → v1.15 修订候选(NBCC 战役复盘素材,Step 9 定稿)

战役: docs/nbcc-cross-verify-2026-10-26/ · 日期: 2026-10-26 · 状态: 草稿(Step 9 时并入 ~/sleepy/docs/sop/jw-cross-verify-sop.md 顶注)

## 1. 「触达」定义必须成文(本轮最大歧义,裁决记录见 .superpowers/sdd/scope/progress.md)
- 现象: 268 候选全派单且批单要求每仓过 tree/README;SOP §0 致谢铁律字面 = "哪怕只看了一眼 README" → 268 仓全上关于页。
- 实证边界: HFUT 先例(3b64f085)致谢 11 仓,被谢者均与调研问题相关(含仅维护活跃度旁证),关键词噪声仓未谢。
- v1.15 拟文: 触达 = 对证据网有贡献的内容阅读(verdict ∈ POSITIVE/INDIRECT/NEGATIVE,或深读仓);纯排除性阅读记 findings 不记致谢。判据写死,消灭二次判断。

## 2. 既有致谢 token 的实物复核闸(新翻车点)
- 现象: school-hfut 卡与测试 token 写 "HFUT-Schedule (Chiu-xaH, MIT)",而 LICENSE 实物自 2023 initial commit 即 Apache-2.0(API+body 双证)。与 schools.json shuwei 猜配同类的"静态清单漂移"。
- v1.15 拟则: Step 5.5 对「将在本轮文本中再次出现」的既有仓条目,license 一律按 brief 第 4 条取法重新实物核证;发现错标 = 独立修正 commit(与新增致谢分面,回滚粒度)。

## 3. 盐值双形态先例(知识沉淀,拟入 Step 4 维度表注)
- 同一校 wengine 部署可并存两把密钥: 门户表单密码加密盐(NBCC: wrdvpnisawesome!,源=登录页 JS 明文)与代理 URL hex 编解码盐(NBCC: wrdvpnisthebest!,源=可复现解密)。用途不同,不得互换,不得跨校写死(NEU 异盐先例)。

## 4. UA 分流门户的入口双分支(拟入 Step 6 检查项)
- 深信服/wengine 门户按 UA 分流时,适配设计必须同时给「旧门户直链」与「新门户 /https/<hex>/ 桥」两分支,公开证据定不了最终值就以"学生自愿回传实测"为裁决闸,禁止单分支上线。

## 5. 并行致谢与并行代码的冲突面(流程经验)
- Step 3 深读信号仓会在 brief 定稿后新增触达 → 第二批致谢 commit 合法化(本轮 Ruling: 11 仓先提,merge 后 POSITIVE/INDIRECT 且 relevant_files 非空者第二批补提)。致谢前置原则不因分批而失效: 第二批仍先于 Step 6 代码改动。
