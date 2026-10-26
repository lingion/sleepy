# HBSM 跨仓验证 — 范围锁定

- **学校**: 湖北商贸学院 (Hubei Business University, HBSM)
- **教务域名**: `jwxt.hbc.edu.cn`
- **适配类型**: 初次适配 (新增学校, type=zf_new)
- **涉及 parser**: `JwNewZfParser` (zf_new)
- **数据来源**: 用户采集包 `湖北商贸学院-课表数据-脱敏.zip` (2026-10-10 采集, 学期 2026-2027-1)
- **判定结论 (预分析)**: 协议 = zf_new (zftal-ui-v5 指纹 + /kbcx/ 裸路径 + kbList JSON), 现有 `JwNewZfParser` 字段全兼容, **无需改 parser**; 唯一缺口 = `schools.json` 缺该校 entry + fixture/致谢等 SOP 流程产物
- **用户原话**: "完整适配流程" (2026-10-10)
- **对齐参考**: 惠州学院 HZU (issue #90) 同为裸 /kbcx/ zf_new, precedent commit 见 LicenseScreen.kt:424/554

## 范围边界

- 只新增学校条目 + fixture + 检测测试 + SOP 产物文档
- 不动 `JwNewZfParser` 解析逻辑 (预分析已证 18/18 条解析 0 失败)
- sjkList 实践环节 (军训/入学教育) 无 xqj/jc, 不进课表 — zf_new 标准行为, 不处理
- 不涉及 fetch 协议 (无 /jwglxt/ 路径, pick() 返回 null → outerHTML 捕获路径, 与 HZU 同)
