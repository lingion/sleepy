# HBSM 协议 Matrix — Step 4

对比维度 (7): 判定指纹 / 字段映射 / 节次形态 / 周次形态 / 星期字段 / 登录态检测 / 抓取路径

| 维度 | shangkeschedule (INDIRECT 同判定) | OpenWakeUp (NEGATIVE) | sleepy 现状 (JwNewZfParser) |
|------|------|------|------|
| 协议判定 | zhengfang_new (独立同判定) | type=zf 伞型 (未区分新旧) | zf_new |
| 判定指纹 | (通用 zf_new 路径) | 旧版 Table1/kbgrid_table 网格 | zftal-ui-v5 指纹 + 裸 /kbcx/ 路径 + kbList JSON |
| 字段映射 | kcmc/xm/cdmc/xqj/jcs/zcd | (旧版: name/room/teacher 网格) | kcmc/xm/cdmc/xqj/jcs/zcd — 一致 |
| 节次形态 | jcs `{n}-{m}节` | — | jcs `3-4节`/`9-12节` 单段; NODE_PATTERN `\(\d{1,2}[-]*\d*节` 全命中 |
| 周次形态 | parseWeeks `9-16周` / `11-17周(单)` | ZfTimeParser `{1-18周}` 旧版文本 | zcd `9-16周` / `11-17周(单)` / `5周`; parseWeekStr 同语义 |
| 星期字段 | xqj 1-7 | 网格列推断 | xqj ∈ {2,3,4,5} (18 条全 1-7 合法) |
| 登录态检测 | (未深挖) | requireZfGridFingerprint 星期一/二/节 | requireZfNewGridFingerprint (zftal-ui-v5 指纹) |
| 抓取路径 | kbList JSON | WebView 页面 HTML | pick() null → outerHTML 捕获, 与 HZU #90 同 |

## 求同存异结论

- **同**: shangkeschedule 与 sleepy 对 HBSM 协议判定一致 (zf_new), 字段映射 6 字段逐一相同, 节次/周次形态处理语义相同 — **跨仓互证成立**
- **异**: OpenWakeUp 用旧版伞型归类该校 (其 ZfNewParser 未接线), 属归类粒度差异, 不构成 sleepy 判定反证; 该仓无协议细节可供借鉴
- **sleepy 无需改 parser**: HBSM 数据形态落在现有 zf_new 容差内 (18/18 条解析 0 失败, verify_parse.py 实测)
