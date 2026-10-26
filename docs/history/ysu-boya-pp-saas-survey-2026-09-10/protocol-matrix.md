# boya_pp 多校 SaaS 协议矩阵

## 结论

YSU 采集包与公开前端证据确认 boya_pp 是部署在 `/pp/` 下的 Vue 2 SPA，产品页面自称“博雅研究生招生管理系统 V6.0”，供应商线索为超星 chaoxingbook。前端代码包含 `YANSHANDAXUE` (`fid=41571`) 与 `DALIANJIAOTONG` 等学校常量，因此产品模型是多校 SaaS；同产品部署校理论上可复用 `TYPE_BOYA_PP`，但每校仍需实测 CAS service、域名、fid/token 入口和字段差异。

## 证据矩阵

| 维度 | YSU boya_pp 实证 | 多校复用含义 |
|---|---|---|
| 登录入口 | YSU CAS: `cer.ysu.edu.cn/authserver/login?service=.../api/casLogin/ysu` | 每校 CAS host/service 必须单独确认，不能按域名猜 |
| 前端入口 | `/pp/`, Vue 2 SPA, 路由 `#/u/class-schedule/student/mine` | 产品路径结构可复用，部署根路径可能不同 |
| 产品身份 | 博雅研究生招生管理系统 V6.0；超星 chaoxingbook 线索 | 同 vendor 不等于接口完全相同 |
| 学校标识 | `YANSHANDAXUE` / `fid=41571`; `DALIANJIAOTONG` 常量 | fid 是租户/学校配置，收录新校前必须取得实际 fid 或免登 token |
| studentId | `/api/login/currentUser` 的 `userNo` 等字段 | parser 不依赖学校名，按课表 data 字段解析 |
| 学期 | `GET /api/microForm/term`，`termBeginTime`, `weekEnd` | 逐校确认日期字段和值域 |
| 节次 | `GET /api/schedule/class/setting/current?yearTerm=...` | lessonConfig 结构目前可复用，数量由学校配置决定 |
| 课表 | `GET /api/schedule/table/byStudent?...&whichWeek=N&yearTerm=...` | 必须逐周抓；不能假设无 whichWeek 返回完整课表 |
| 登录失效 | 401 或业务 `code != 200` | WebView 层保留失效分支；新校做实测 |
| 认证 | cookie/request header `token`, `Protocol-Type: https` | token 注入来源可能随 CAS/工作台不同，不能硬编码 |

## 复用决策

- 当前证据支持新增学校优先复用 `JwBoyaPpParser`，不新增 parser。
- 仅凭前端 `fid` 常量不能直接新增 schools.json 条目；至少需要该校入口、登录态和一份真实课表响应的采集证据。
- GitHub 检索到的四个仓库均不是 boya_pp 协议实现，不能作为同产品协议的实现参考；按 SOP 仍全部记录并致谢/说明。
