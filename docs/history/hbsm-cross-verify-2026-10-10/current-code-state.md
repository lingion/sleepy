# HBSM 现状代码阅读 — Step 5

## 入口与数据流 (改动前)

- `schools.json` (assets): 348 条, 无 HBSM 条目 — 唯一缺口
- `JwImportViewModel` → WebView 加载 `https://jwxt.hbc.edu.cn/` → 登录后路由到课表页 (裸 `/kbcx/xskbcx_cxXsgrkb` 无 `/jwglxt/` 前缀)
- `JwNewZfParser.generateCourseList()`: parseEmbeddedJson → findKbListArray 递归下钻 kbList → parseCourseJsonArray 逐条映射
- fetch 协议 pick() 对无 `/jwglxt/` 路径返回 null → 落 outerHTML 捕获路径, 与 HZU (issue #90) 同通路

## 字段映射核对 (采集包 18 条实测)

| 报文字段 | parser 落点 | 形态 | 结果 |
|---------|------------|------|------|
| kcmc | name (firstStr 首选) | 9 门课名 | 18/18 命中 |
| xm | teacher | 真实教师名 (fixture 脱敏为某某某) | 18/18 |
| cdmc | room (firstStr 首选) | "XJB-104" 等 | 18/18 |
| xqj | day (firstInt 首选) | 字符串数字 "1"-"6" | 18/18, coerceIn 1..7 全合法 |
| jcs | parseSectionRanges | "3-4节"/"9-12节" 带「节」后缀 | 18/18 (剥「节」逻辑 HZU 2026-10-04 已落) |
| zcd | parseWeekStr | "9-16周"/"11-17周(单)"/"5周" | 18/18, 单周形态 type=1 正确 |

## 错误路径

- sjkList (军训/入学教育 4 条实践环节): 无 xqj/jc 字段 → parseCourseJsonArray 只处理 kbList 数组, sjkList 天然不进课表 — 无需防御
- kbList 为空/字段缺失: 既有 kblist_missing_fields / kblist_empty_semester fixture 已覆盖
- 登录态失效: requireZfNewGridFingerprint (zftal-ui-v5 指纹) 既有逻辑, 采集包 1-dom/top_page.html 实锤指纹命中

## 结论

`JwNewZfParser` 零改动即可全量解析 HBSM 报文。改动面 = schools.json 条目 + school_cities.json 城市 + fixture/测试 + 致谢, 全部为登记性落点。
