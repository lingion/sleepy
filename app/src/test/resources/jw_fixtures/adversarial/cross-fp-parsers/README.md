# cross-fp-parsers — 解析器互吃对抗样本组 (T8 裁决机制设计依据)

每个 .html/.json 样本配同名 .case.json (字段 input / expected)。
所有 current_behaviour_measured 均为实测: 用 sleepy 当前 8 个 parser
(JwWisedu/JwNewUrp/JwNewZf/JwOldZf0/JwOldZf1/JwQz/JwQzCrazy/JwUrp) 在
JDK17 + jsoup 1.18.1 + org.json 20231013 环境下逐个执行, 非
推测。执行 harness 见 /tmp/jw_verify/。

## 样本清单与实测结论

| 样本 | 真协议 | 8-parser 实测 | 分类 |
|---|---|---|---|
| cf_kbxx_into_newzf.html | cf | 全 0 课 (marker 被认领, 字段不匹配) | silent_zero_courses_marker_hijack |
| cf_kbxx_into_newzf2.html | cf | 全 0 课 (jcdm2='3,4' toIntOrNull 失败丢行) | silent_zero_courses_marker_hijack |
| zfnew_kblist_into_qzparser.html | zf_new | 全 0 课 (markers 不含 kbList!) | silent_zero_courses_kblist_marker_missing |
| zfnew_kblist_alias_trap.html | zf_new | 全 0 课 (jc='1-2' toIntOrNull 失败 + jasmc 优先级) | silent_zero_courses_alias_priority_and_jc_range_string |
| wisedu_json_into_newzf.json | wisedu | Wisedu=2 课, 其余 0 (JSON 嵌套深度互斥) | 无假阳性 |
| qz_base_into_qzcrazy.html | qz | Qz=1/NewZf=1 (节次公式差异), Crazy=0 | 变体边界 + NewZf 抢注 |
| qz_html_into_newzf.html | qz_base | NewZf=7/Qz=7 同构, 数量裁决随机 | 同构 day 偏移 + 节次公式差 |
| qz_withnode_into_qzparser.html | qz_with_node | Qz=2/NewZf=2 (节次全钉 1-2, 真值 3-4/5-6 丢) | silent_wrong_nodes_false_attribution |
| isomorph_zfnew_kbgrid_qzcell.html | zf_new | NewZf=2/Qz=2 同构 (唯一判据=页面级指纹) | 裁决靠顺序非身份 |
| zf_old_table1_into_newzf.html | zf | NewZf=0, OldZf=2 课但 endNode=startNode-1 | silent_wrong_endnodes_result1_never_assigned |
| zf_old_blacktab_into_parsers.html | zf | 全 0 课 (id='Table1' 只认大写) | silent_zero_courses_selector_gap |
| zfnew_grid_into_oldzf.html | zf_new | 全 0 课 (kbgrid_table_0/timetable_con 无支持) | silent_zero_courses_timetable_con_variant_missing |
| zfnew_table1_into_oldzf.html | zf_new | 全 0 课 ([id*=kb] 不命中 'table1') | silent_zero_courses_upstream_table1_variant_missing |
| bnuz_table1_id_collision.html | bnuz | 全 0 课 (BNUZ 与上游 NewZF 同用 id='table1') | silent_zero_courses_id_collision_between_bnuz_and_zfnew |
| urp_displaytag_into_zf_qz.html | urp | 全 0 课 — 发现 JwUrpParser L28 自过滤 bug | silent_zero_courses_parser_self_bug_table_filter |

## 对 T8 裁决机制的设计结论 (全部由上表实测推出)

1. **marker 级互斥不足**: CF 的 'kbxx' marker 会命中 JwNewZfParser L42, 但字段
   体系不匹配产出 0 课。协议认领必须做"marker 命中 + 字段白名单校验"两级,
   否则一个协议的页面会被另一个协议的 parser 静默吞掉。
2. **'课程数最多' 裁决在 4/15 样本上失效**: 同构样本 (qz_html_into_newzf,
   isomorph_zfnew_kbgrid_qzcell, qz_base_into_qzcrazy, qz_withnode) 两个
   parser 课程数相等, 先到先得由 JwImportViewModel.kt L121-130 候选序决定,
   协议归属随机。
3. **字段级错误数量裁决看不见**: qz_withnode 节次全错 (真 3-4/5-6 → 解析 1-2),
   课程数完全一致 — 裁决机制必须引入结构锚点评分 (title 属性齐全度、
   festival 行头存在性、容器 ID 与字段名联合评分), 数量只作同协议次级规则。
4. **ID 冲突**: id='table1' 同时是 BNUZ (BNUZParser.kt L23) 与上游 NewZFParser
   (L12) 的容器 ID; id='Table1' 是老正方。sleepy JwOldZfParser L24 只认大写,
   blacktab 变体也吃不到 — 容器 ID 不能做协议充分指纹。
5. **页面级指纹是唯一可靠判据**: isomorph 样本证明只有 zftal-ui-v5/jwglxt/
   gnmkdm 这类页面级特征能区分同构的 QZ/ZF_NEW 网格; T6 的
   detectProtocolFromHtml 必须在 T8 裁决前运行并覆盖显式 type。
