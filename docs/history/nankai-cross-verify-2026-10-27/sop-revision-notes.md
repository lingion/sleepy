# SOP 复用即修订笔记 — v1.15 → v1.16（南开战役, 2026-10-27）

本轮按 v1.15 全流程跑通, 复盘产生以下修订并入 v1.16:

1. **同族二次适配路径定型（新增小节）**: 协议族已在库、仅入口形态不同的学校（南开 = classic_eams 家族第二例）, 修复面 = 注册开关翻转 + 入口自动动线 + 锚点/指纹扩充 + pinned 测试, parser 零改动。区别于初次适配（NBCC 建通路）。检索与致谢规模不因此缩减（全量铁律照常 38/38）。
2. **fixture 学期形态铁律（Step 7 陷阱入册）**: 入口页 fixture 的 semesterCalendar 初始化必须用真实页面不引号 key 形态 `{onChange:"",value:"4384"}`——正则 `CLASSIC_EAMS_SEMESTER_CAL_RE` 按真形态写, 引号 JSON 形态会静默提取 null。动作页 fixture 若无测试提取学期可用任意形态, 但混用会误导后续复用者。
3. **parser 行数语义锚点（Step 7 陷阱入册）**: classic_eams 课表行数 = Σ(块周位数), 块内 indexPairs 折为单 slot; 写行数断言前先数位图, 禁 weeks×indexPairs 朴素乘积。
4. **同名误命中记档不剔除（Step 2 细则）**: 检索命中里的异校/异国同名仓（john8654john/nankai-schedule = 日本南海电铁）判 NEGATIVE 后仍进 candidates/findings/致谢——全量铁律的「全量」含证伪条目, 它们是可检索的反证。
5. **本项目 fork 误入检索的剔除留痕（Step 2 细则）**: cand_meta 混入 starlake1009/sleepy（本项目自身 fork）, 以最终清单为准剔除, 且剔除理由写进 candidates.json note, 不留静默差异。
6. **pinned 测试的裁决闸形态（Step 7 细则）**: 活体证据未闭环（等报告人回传）时, pinned 断言开闸字段（enableFetch/type/url）, 显式不锁 status, 注释写明「URL 裁决闸待活体回传」——防止测试把未裁事实钉死。
7. **背景 agent 挂起控制器接管再实证（memory agent-death-controller-takeover 对齐）**: 致谢 batch agent 死于 auth 错误, 控制器直接完成 38 条记录, 未重派、未缩面。
