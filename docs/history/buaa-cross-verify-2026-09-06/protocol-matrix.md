# Protocol Matrix — BUAA (北航) vs 上游形态

> 调研日: 2026-09-06
> 单线程调研, 11 仓全读 (派单 SOP 模式但不开 subagent, 由主对话逐个 clone + 读)

## 协议族判定: **强智 iEAS 网络版 (`ieas2.1`) — 不是 EAMS5, 也不是新版正方**

BUAA 教务系统是 iEAS 网络版 (强智 eams 旧版的 URL 路径形态), 走 ASP.NET MVC 框架。证据 11 仓高度一致:

| 仓 | URL 锚点 |
|---|---|
| APassbyDreg/BUAA_JW_Utils | `http://jwxt.buaa.edu.cn:7001/ieas2.1` |
| SE2020-TopUnderstanding/BUAA-Campus-Tools-Backend | `https://jwxt-7001.e2.buaa.edu.cn/ieas2.1/welcome` |
| SE2020-TopUnderstanding/BUAA-Campus-Tools-Backend | `https://jwxt-7001.e2.buaa.edu.cn/ieas2.1/kbcx/queryGrkb` (课表) |
| fondoger/buaa-teacher-evaluation | `http://jwxt.buaa.edu.cn:8080/ieas2.1/` |
| Cauchy1412/BUAAGetCourse | `http://jwxt.buaa.edu.cn:8080/ieas2.1/xslbxk/saveXsxk` |
| KKRainbow/JWOneShotEval | `http://10.200.21.61:7001/ieas2.1` (校内 IP 等价) |

## 7 维度对比表

| 维度 | BUAA | 上游强智 iEAS | 上游 supwisdom EAMS5 | 上游新版正方 |
|------|------|---------------|-----------------------|--------------|
| **1. URL 前缀** | `/ieas2.1` | `/ieas2.1` | `/eams5-student` 或 `/student` | `/jwglxt/` 或 `/kbcx/` |
| **2. 协议族** | 强智 iEAS 网络版 (ASP.NET MVC) | 同 | supwisdom 新版 | 新版正方 |
| **3. 登录入口** | `https://sso.buaa.edu.cn/login?service=...` (CAS 统一认证) | CAS 统一认证 | `/cas/login` (同上) | `/login_slogin.html` |
| **4. 课表 endpoint** | `GET /ieas2.1/kbcx/queryGrkb` (HTML, **不是 JSON**) | 同 | `GET .../print-data` 或 `GET .../get-data` (JSON) | `POST .../kbcx/xskbcx_cxXsgrkb.html` (JSON) |
| **5. 响应格式** | HTML (BeautifulSoup 解析) | HTML | JSON | JSON |
| **6. studentId 提取** | HTML 文本中 `(学号)` 截取 | 同 | script 段 `studentId='...'` | regex A-F |
| **7. 登录态失效表现** | 重定向到 sso.buaa.edu.cn/login | 同 | 同 (5 仓共识) | 重定向到 login_slogin.html |

## 关键结论

1. **协议族 ≠ EAMS5, ≠ 新版正方** — 是**强智 iEAS 网络版** (TYPE_QZ 系列变体)
2. **响应格式 = HTML 不是 JSON** — 不能复用 EAMS5 fetcher 路径 (`studentTableVms`), 也不能复用 zf_new JSON fetcher
3. **现行 Sleepy 检测逻辑会把 `/kbcx/` 误判为 zf_new** — 需修复 URL 分类优先级
4. **没有现成 BUAA-specific parser** — 必须新增, 或者**复用 QZ 系列 HTML parser** (但 BUAA 是 iEAS, 不是 jsxsd)

## Sleepy 现有 parser 与 BUAA 适配性评估

| Parser | 适配性 | 原因 |
|--------|--------|------|
| `JwQzParser` (qz) | ❌ | 走 jsxsd 路径 + node 表 |
| `JwQzCrazyParser` (qz_crazy) | ❌ | 走 jsxsd + 疯狂排课 |
| `JwQzWithNodeParser` (qz_with_node) | ❌ | 走 jsxsd 路径 + 周次(节次) |
| `JwQzBrParser` (qz_br) | ❌ | 走 jxb/ 路径 |
| `JwOldQzParser` (qz_old) | ❌ | 走 jsxsd 旧版 |
| `JwEams5Parser` (eams5) | ❌ | 走 eams5-student |
| `JwZfNew*Parser` (zf_new) | ❌ | 走 JSON + jwglxt |
| **需要新增** | **JwIeasParser** | 走 ieas2.1 + HTML table |

## 适配复杂度评估

| 项 | 复杂度 | 备注 |
|---|--------|------|
| URL 检测 (JwImportViewModel) | **低** | 在 `/kbcx/` 锚点前加 `ieas2.1` 高优先级规则, 避免误判为 zf_new |
| Parser (新增 JwIeasParser) | **中** | BeautifulSoup 解析 `/ieas2.1/kbcx/queryGrkb` HTML 表格 → JwCourse |
| fetcher (HTML fetcher, 类似 HFUT 老正方) | **中** | 单 GET, Cookie 已带 |
| fixture | **中** | 需要真实 HTML 样本 (5 仓无 fixture, 需用户采集包) |
| 测试 | 低 | 6 form studentId + HTML table 行提取 |

## 外部佐证 (POSITIVE 4 仓)

- **APassbyDreg/BUAA_JW_Utils** — Python, ieas2.1 完整 URL + 选课抓取
- **SE2020-TopUnderstanding/BUAA-Campus-Tools-Backend** — Python, queryGrkb 课表 endpoint 实锤
- **fondoger/buaa-teacher-evaluation** — Python, ieas2.1 autoscore URL
- **Cauchy1412/BUAAGetCourse** — Python, xslbxk/saveXsxk URL
- **KKRainbow/JWOneShotEval** — PHP, ieas2.1 校内 IP

## INDIRECT 7 仓 (致谢保留, 非协议证据)

- **yinwoods/Fuck-BUAA-JiaoWu-Anyway** (gsmis 研究生)
- **xunux/WeixinScoreGetter** (gsmis 研究生)
- **SE2020-TopUnderstanding/BUAA-Campus-Tools-Frontend** (Flutter UI)
- **LabiKyo/buaa-jwgl-evaluator** (油猴前端)
- **Yngu196/Schedule** (WakeUp fork, 仅占位)
- **wyx-1236/BUAA-Java-ACP** (课程作业)
- (1 个 others not category)