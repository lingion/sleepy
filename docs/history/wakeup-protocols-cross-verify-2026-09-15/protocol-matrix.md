# WakeUp 协议族跨仓验证 — protocol-matrix (Step 4)

日期: 2026-09-16 · 分支: feat/wakeup-protocol-coverage · SOP: jw-cross-verify-sop v1.1+

## 覆盖总览

38 候选全量派单, 38 全部回收 verdict (5 个补派 agent 二批完成):
- POSITIVE: 25 · INDIRECT: 9 · NEGATIVE: 1 · NULL_EVIDENCE: 1 · 非 verdict 佐证 (suda-1 partial / suda-2 inconclusive / suda-3 supports): 3

## 8 族 × 7 维度矩阵

### 1. kingo_new (青果 Kingosoft EAMS TaskActivity JS 网格)
| 维度 | WakeUp 6.1.80 (jadx) | 开源仓证据 | Sleepy 实现 | 判定 |
|---|---|---|---|---|
| 端点 | courseTableForStd!courseTable.action (POST semester.id) | CourseHelper Swift 三校 + shiguang HPU 一致 | 由 fetch 层承担 | 一致 |
| 网格数据 | HTML 内嵌 JS: activity=new TaskActivity(name,teacher,room,weeksBinary) | 同左 (CourseHelper taskActivityPattern 7 参严格) | parseKingoTaskActivity 同构 | 一致 |
| index 公式 | `index =(.*?)\*unitCount\+(.*?);` (o0000OO0.java:111) | 同左 (shiguang HPU:132 / NEUQ:177) | 同左 | 一致 |
| day 坐标 | **day = parseInt(g1) + 1** (o0000OO0.java:117; o000O0Oo.java:2085 同款) | CourseHelper dayOfWeek=match[1]; shiguang HUNNU day=floor(idx/unitCount)+1; UESTC day=D+1 | ~~day = g1~~ → **已修 day = g1 + 1** (c624363c) | 已对齐 |
| node 坐标 | 13→10, <9→+1, else +2 (o0000OO0.java:121-123) | shiguang: section=idx%unitCount+1 (无 remap; 学校作息差异) | 保留 WakeUp remap (kingo_new 原生形态) | 一致 |
| 周次 | 二进制 '1'@pos → week pos+1 (HUNNU 1-index; NEUQ 0-index 差异已记录) | 同左 | '1'@pos→pos+1 + 连续段压缩 | 一致 |
| 单双周 | 位图奇偶端点隐式 | UESTC "单1-17"/"双2-16" 文字另路 | 段端点奇偶判定 | 等价 |

### 2. jz (金智 JZHandCourseInfoItem)
| 维度 | WakeUp jadx | 开源仓证据 | Sleepy 实现 | 判定 |
|---|---|---|---|---|
| JSON schema | JZHandCourseInfoItem: kcmc/xqj/djj/qmz/dsz | o00oO0o.java:413 唯一 POSITIVE 源 | parseJzJson 同字段 | 一致 |
| **dsz 语义** | dsz==1→1(单); dsz!=2→2(双); else(==2)→0(每周) | SDDFVC 注释 dsz 枚举 0全/1单/2双 (异端点旁证) | `1->1; 2->0; else->2` 逐分支等价 | 一致 (反转语义锁死) |
| HTML 兜底 | CourseFormTable + <hr> 分块 + <br> 字段 | SCUEC scuec.js:31 同构 | parseJzHtml 同构 | 一致 |

### 3. suda_post (苏大正方 DataGrid)
| 维度 | WakeUp jadx (o0OO00O dex) | 开源仓证据 | Sleepy 实现 | 判定 |
|---|---|---|---|---|
| 表选择器 | DataGrid1 → null 兜底 MainWork_DataGrid1 (L0055/L005d) | wakeup-shu dex dump 实证同串 | 同序双选择器 | 一致 |
| 节次列 | align=center td (L0083-9c) | 同左 | 同左 | 一致 |
| <br> 切分 | Regex "<br>{2,}" 容忍连续 br (L0111) | 同左 | 单 "<br>" split + Jsoup text + 空行过滤 (效果等价) | 等价 |
| rowspan | attr("rowspan") (L00e9) | shiguang UPC/CUG class-C rowspan→endSection | ~~无~~ → **已修 rowspan 折叠** (c624363c) | 已对齐 |
| 教师 | "主讲教师:" (半角 startsWith) + "辅讲教师" (contains, 括号剥出) (L01a6/L0183) | 同左 | 三通道 (半角/全角/辅讲跳过) | 一致 |
| 周次 | "第[0-9]*周" + "从第(\d+)至(\d+)" + 单/双三态 (L021f) | suda-3 教师正则 + 单双 parity 佐证 | "第a-b周[单/双]" 正则族 | 一致 |

### 4. cumtb (HFUInfo JSON)
| 维度 | WakeUp jadx (oo000o.java) | Sleepy 实现 | 判定 |
|---|---|---|---|
| schema | result.lessonList{id→courseName} + scheduleList | 同左 | 一致 |
| time→node | <1230→(t-800)/100+1; <1800→(t-1400)/100+5; else (t-1900)/100+9 | timeToNode 逐分支相同 | 一致 |
| 时长→节 | 50..99→1; <=200→2; 210..340→3; else→4 | durationToNodes 相同 | 一致 |
| 未知课名 | **lessonId 缺失 → "未知" 仍导入** (oo000o.java:45) | ~~跳过该行~~ → **已修 "未知" 占位** (c624363c) | 已对齐 |
| 周次 | weekIndex 单周 start=end=weekIndex | 同左 | 一致 |

### 5. xju_post (新大 dgData)
| 维度 | WakeUp jadx (OooOOO.java mode 1) | 开源仓证据 | Sleepy 实现 | 判定 |
|---|---|---|---|---|
| 表选择器 | th 星期日/星期一 顺序检测 | shiguang UPC ctl00_contentParent_dgData 列序 0:周日 | #ctl00_contentParent_dgData 双兜底 | 一致 |
| sundayFirst | f8381 = sunIdx < monIdx; TABLE=[0,7,1..6] | UPC 列序旁证 | 同谓词 + 翻转公式 | 一致 |
| day | sundayFirst ? TABLE[col+1] : col+1 (line 241/478) | 同左 | col+1 / 翻转 | 一致 |
| rowspan | (i6+i30)-1 连堂折叠 | UPC rowspan→endSection 同构 | node+rowspan-1 | 一致 |
| 单元格文本 | "｛名(周次)[教师:…,地点:…]｝" ";" 分条、"、" 分周 | 同左 | 同构 | 一致 |

### 6. shuwei_json (数维)
| 维度 | WakeUp jadx (o000O0Oo.java mode 16) | Sleepy 实现 | 判定 |
|---|---|---|---|
| TaskActivity 变体 | day=g1+1, node=g2+1 (2083-2086) — HTML 形态 | parseShuwei 走 JSON activities 通路; kingo HTML 形态由 parseKingoTaskActivity 覆盖 (含 day+1 修复) | 一致 |
| JSON activities | 通用 activities/courseUnits 递归 | parseActivity 字段族 (courseName/weekday/startSection/…) | 一致 |
| 单双周 | weeksStr "1-15单,2-16双" | parseWeekTokens 单/双 token | 一致 |
| 反向证据 | adapter-4 NEGATIVE: ZKJJaker 仓实为商丘 EAMS 非数维 — 记录为反向验证 | — | 已记录 |

### 7. login_chaoxing (超星)
| 维度 | WakeUp jadx (OooOOOO.java = CxInfo schema) | 开源仓证据 | Sleepy 实现 | 判定 |
|---|---|---|---|---|
| 数据形态 | CxInfo.Data.kckbData **或** 裸 kckbData[] 双兜底 | adapter-2/3 POSITIVE: queryKbForXsd + sdpkkbList 同字段族 (kcmc/tmc/croommc/djc/zc) | data.kckbData ?: kckbData 双兜底 | 一致 |
| day/node | xq=parseInt字符串; djc start=end | xingqi/djc 同族 | xq.toInt/djc | 一致 |
| zctype | parseInt ?: 0 | 同左 | 同左 | 一致 |
| 端点族 | — (WakeUp 内嵌 web 拦截) | moeshin/Sittymin 证实 /admin/pkgl/xskb/* ; queryKbForGrdb/kckbData 0 命中=同名同族异 action, WakeUp 内嵌形态自洽 | 不改 | 一致 |

### 8. south_soft (南方软件)
| 维度 | WakeUp jadx (o00000O0.java mode 10 → OooOoO0) | Sleepy 实现 | 判定 |
|---|---|---|---|
| HTML 形态 | table#kb→tbody→tr; div 按 "(<[/]*((?!br).)*?> *)+" 切; "\d+-\d+周" 锚 | parseSouthSoft 走 studentTableVms JSON 通路 ( WakeUp WebView 注入链同源数据) | JSON/HTML 双形态互补, 不冲突 |
| 坐标 | day=td 索引; node=tr 序 - "无节次"跳行数; weeks a-b (默认1-20); type 恒 0 | JSON 通路 SKSJ/xq_jc KEY 直读 | 同源异构, 等价 |
| 反向 | ref-5 NULL on south_soft (NUISTTable 实为正方 jwapp) — 南方软件专属仓开源界 0 命中, WakeUp mode10 即最权威参照 | — | 记录 |

## 汇总

- **3 处真差异全部修复** (commit c624363c): kingo day+1 (4 源一致) / suda rowspan / cumtb 未知课占位
- **5 处形态差异判定等价不修**: <br>{2,} vs 单br+过滤; 表头跳过策略; 周次形态; chaoxing 端点族; south_soft JSON/HTML 双形态
- **2 条反向证据**: adapter-4 (商丘≠数维), ref-5 (NUIST≠南方)
- 38/38 候选全致谢 (commit f8486196)

## 检索矩阵 & findings 溯源

- scope: docs/history/wakeup-protocols-cross-verify-2026-09-15/scope.md
- candidates: docs/history/wakeup-protocols-cross-verify-2026-09-15/candidates.json (38)
- findings 原始 JSON: /tmp/wakeup-findings/*.json (43+5=48 份, 归档至 findings/ 下)
- attribution: docs/history/wakeup-protocols-cross-verify-2026-09-15/attribution-candidates.json
