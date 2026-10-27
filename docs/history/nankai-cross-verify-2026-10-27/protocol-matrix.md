# Step 4 协议 matrix — 南开 eamis (classic_eams) vs NBCC 同族横向对比

- 日期: 2026-10-27 · 依据: SOP Step 4 七维度 · 证据源 = findings.json（38 verdict）+ bucket-S.md 代码勘察
- 结论先行: 南开与 NBCC 同属金智 classic_eams 家族, 课表协议串逐维一致; 差异全部集中在**入口链**（登录体系 + 根 URL 形态）, 故修复 = 通路复用 + 入口自动动线新增, 解析器零改动。

## 七维度对比

| 维度 | NBCC（已上线 PR#232） | 南开（本轮） | 差异定性 |
|---|---|---|---|
| 入口 URL 形态 | WebVPN 门户 `webvpn.nbcc.edu.cn/login` → 磁贴 → `jwgl.webvpn.nbcc.cn/eams/courseTableForStd.action` | 根 `https://eamis.nankai.edu.cn/` = 148 字节空壳（零指纹, bucket-D 无凭据探测）; 课表入口 = 同源 `/eams/courseTableForStd.action` | **核心差异**: 南开无 VPN 层, 但根 URL 不可解析, 必须 GET 子路径入口页 |
| 登录体系 | 深信服 WebVPN（门户凭据 = VPN 票据 `wrdvpn1-`） | IAM 飞连统一身份认证 `iam.nankai.edu.cn`（指纹 `password_account_input`）, 登录后回跳 eamis | 完全不同栈; 我方 WebView 只负责承载, 凭据人输（形态B 零凭据） |
| studentId 提取位置 | 入口页 `bg.form.addInput(form,"ids","…")` + 隐藏域兜底 | 同左（bucket-A zawnpn std_id_pattern 与我方 extractIds 同形） | 一致 |
| studentId 形态 | 学号 | 学号（2023 级起 10 位, fixture 合成 `2000000001` 形态） | 一致 |
| API path / 请求方法 | POST `/eams/courseTableForStd!courseTable.action`, 体 `ignoreHead=1&setting.kind=std&startWeek=&project.id=1&semester.id=&ids=` | 同左逐字（bucket-A/B 多仓端点串一致）; 学期号 = `semesterCalendar({…,value:"4384"})` 真实页面为**不引号 key** 形态 | 一致; 学期号当前值 4384（jxy18100 实测） |
| 会话失效形态 | 响应不含 `new TaskActivity` → 门户回登录 | 回跳 IAM 登录页（LOGIN_FINGERMENTS 双指纹 score≥2 判 SESSION_EXPIRED）; 自动动线内 loginRe 命中 → 「登录态已失效,请在 WebView 内重新登录后再试」 | 南开多一条**主动**失效判据（entry GET 阶段即可判, 不必等 POST 空手而归） |
| 反爬/传输障碍 | 深信服打残页面 jQuery → 原生 fetch; UA 分流（移动 UA 被送进钉钉 SPA） | 未见 UA 分流证据; 38 仓 2017-2026 跨度均正常协议串; 根域本机不可达属校园网侧限制 | 南开无 UA 分支需求 |

## 同族互证（课表页形态锚点）

南开 fixture `nankai-course-table-action.html` 与 NBCC `nbcc-course-table-action.html` 共用骨架: `#manualArrangeCourseTable` 空壳 + `new TaskActivity(…)` + `index = D*unitCount + P`（含线性裸 index 形态, 南开 fixture 42 即此例） + 周次位图 + `table0.marshalTable`。parser 五锚 confidence=95 双校同分, 跨校互证。

## 「树莓教务」断言证伪记录

zzask77 原话: 「南开已使用树莓教务、不再使用金智教务」。检索矩阵含专项反证（`树莓|raspberry|urmp|正方|树维|金智` 全库 grep + GitHub code search）: 38/38 仓**零**「树莓」命中; 最新活动仓（jxy18100/nku- 2025、TsXor/NK-eamis 2025-01、NKUCS-ICU 2026-08 边界条目）协议串仍 courseTableForStd 家族。判定: 断言不成立; 其「网页空白」症状与 148 字节根壳 + enableFetch 未开的旧行为一致（根 URL 直接导入 → 无锚点 → WRONG_PAGE）。
