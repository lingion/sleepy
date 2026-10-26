# boya_pp 多校 SaaS 调研 scope

- 学校全称: 燕山大学(首校) + 博雅研究生平台(超星 chaoxingbook 旗下)全部部署校
- 英文缩写: YSU (首校); 产品代号 boya_pp
- 适配类型: 已适配(YSU, PR #30 复活合入)之后的 同产品多校 SaaS 调研
- 是否涉及现行 parser: 是 — JwBoyaPpParser / TYPE_BOYA_PP (priority 17), 调研结论决定其他校是否零代码收录(仅加 schools.json 条目)
- 用户原话: "这个多 Saas 的话，你再研究一下"
- 触发: jw-cross-verify-sop (SaaS 产品域调研适用)
- 已知线索: PR 代码前端含 YANSHANDAXUE / DALIANJIAOTONG fid 常量 (fid=41571 = 燕大)
