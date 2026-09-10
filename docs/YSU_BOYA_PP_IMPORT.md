# 燕山大学研究生平台直连导入 (boya_pp 协议)

> 2026-09-06 落地。首个 `grad_supported` 研究生直连校, 也是 `boya_pp`
> (博雅研究生平台) 协议的首校。数据依据: 用户提供的 sleepy-adapt 采集包 +
> 会话 token 有效期内对全部接口的实采 (19 周 390 行课表行、30 学期字典、
> 12 节次配置), 并与页面渲染 DOM 逐格交叉验证。

## 系统识别

| 项 | 值 |
|---|---|
| 入口 | `https://cer.ysu.edu.cn/authserver/login?service=https%3A%2F%2Fyjsxt.ysu.edu.cn%2Fapi%2FcasLogin%2Fysu` (燕大统一身份认证 CAS, 实测无 ticket 时 `/api/casLogin/ysu` 302 至此) |
| 前端 | Vue 2 SPA, 路由 `#/u/class-schedule/student/mine` ("我的课表"); `/pp/` 根路径无默认路由 (catch-all 404), 不可作入口 |
| 产品 | 博雅研究生平台 (超星 chaoxingbook 旗下, 帮助文档自称"博雅研究生招生管理系统 V6.0") |
| 多校性 | 前端代码内含 `YANSHANDAXUE`(fid=41571) / `DALIANJIAOTONG` 等校 fid 常量 — 多校 SaaS, 同产品学校可复用本协议 |
| 认证 | cookie `token` (URL `#/enter?...&token=` 形态由工作台免登写入), 请求头 `token` + `Protocol-Type: https` |
| 响应信封 | `{code:200, data, message, result, success}`; `code!==200` 为业务失败, 401=登录态失效 |

## 接口契约 (全部 GET, baseURL `/api`)

### 1. 当前用户 — `/api/login/currentUser`

`data.termName` = 当前学期 (`"2026-2027-1"`), `data.platRole` = `"STUDENT"`,
另有 `userName/userNo/college/gradeName/schoolName` 等。

### 2. 学期字典 — `/api/microForm/term`

`data[]` 每项:

```json
{
  "termName": "2026-2027-1",
  "currentTerm": "是",
  "term": "秋季学期",
  "yearName": "2026-2027学年",
  "termBeginTime": "2026-08-31",
  "termEndTime": "2027-01-10",
  "weekBegin": "1",
  "weekEnd": "19"
}
```

`termBeginTime` 即第一周周一 (fetch JS 经桥回传, 确认页起始日预填);
`weekEnd` 为总周数, 是逐周抓取的循环上界。注意: 前端 `AE()` 显示学期选项
时 `key=termName, label=alias||termName`, 学期起止日来自同一行 —
页面的"周次下拉"正是用它算出来的。

### 3. 节次配置 — `/api/schedule/class/setting/current?yearTerm=…`

`data.lessonConfig[]` 每项 `{lessonNumber, lessonName, lessonTime:["2023-01-01 08:00:00","2023-01-01 08:45:00"], lessonNumberAlias, …}`。
燕山大学当前 12 节 (`08:00~08:45` … 第十一节 `20:05~20:50`、第十二节 `20:55~21:40`;
页面只渲染到第十一节是因为第 12 节无排课, 配置本身是 12 节)。

### 4. 个人课表 — `/api/schedule/table/byStudent`

```
GET /api/schedule/table/byStudent?page=0&size=20&whichWeek=2&yearTerm=2026-2027-1
```

- 响应 `data` 是**裸数组** (列表基类 `d.data.data.list || d.data.data || d` 的第三分支), 无分页包装。
- **必须逐周抓**: 不带 `whichWeek` 时返回的是不完整子集 (实测 114 行,
  是逐周并集 390 行的真子集), 服务端大概率为某默认周过滤。逐周 N=1..weekEnd。
- 每行是一条排课记录 (行 `id` 全局唯一), 关键字段:

| 字段 | 含义 | 燕大实测样例 |
|---|---|---|
| `courseName` | 课程名 | 新时代中国特色社会主义理论与实践 |
| `courseNo` | 课程号 | 2000009002 |
| `teachingClassName/Code` | 教学班 | 2026级西22环化班 / 20262000009002289 |
| `college` | 开课学院 | 马克思主义学院 |
| `courseTeacher[]` | 教师 `{id,no,name}` | 何茜曦 (150808) |
| `classroomName/Code` | 教室 | （里）J207多媒体 / 2107390207 |
| `week` | 星期 1..7 | 1 |
| `lessonNumber` | 节次 (单节粒度) | 5 |
| `whichWeek` | 周次 (int) | 2 |
| `classPlanId` | 排课计划 (研/本 tag 依据) | 57544 |
| `suspension` | 停课标记 | false (停课行不导入) |

- 课程卡popover文案 `第{whichWeekGroupByWeekLessonNumber}周` 与本行 `whichWeek` 一致。

## 解析规则 (JwBoyaPpParser)

1. 剔除 `suspension` / `deleted` 行; `courseName` 空白跳过。
2. 行 → 原子 `(courseName, week, lessonNumber, whichWeek, classroomName(回退 classroomCode), courseTeacher[].name 去重排序)`。
3. 按 `(课名, 星期, 教室, 教师集合)` 分组, 组内节号→周次集合。
4. 节号连续**且周次集合相等**的行合并为一条课块 (startNode..endNode)。
5. 周次集合展开为连续段: 单段=每周(type 0); 全程等差 2=单/双周(type 1/2); 其余拆多段。

研究生集中授课注意: **同一课同一时段在不同周可能占不同节次** (实测
"材料与化工现代研究方法" 星期六 第 2 周占 5-8 节、第 5-7 周占 5-6 节、
第 6 周另占 3-4 节), 规则 4 会把它们拆成独立课块 — 这是正确行为,
不要按"同名课全学期拉通"合并。

## fetch JS (BOYA_PP_FETCH_JS) 流程

```
currentUser.termName (失败不阻断)
  → microForm/term 精确匹配 termName (回退 currentTerm==='是')
    → 并发: setting/current (periods) + 逐周 byStudent N=1..weekEnd (Promise.all)
      → 桥回传 {ok, data:{term, rows}, periods, startDate: termBeginTime}
```

- token 取 cookie `token`, 空则回退 sessionStorage `ROOT:APPSTORE.token`。
- 单周请求失败静默跳过 (不致命), 但 401 显式中止 — 防止部分数据伪装成完整课表。
- `startDate` 用于 JwImportActivity 确认页起始日预填 (燕大 2026-2027-1 实为
  2026-08-31 开学, 本地"9 月首个周一"推断会差一周)。

## 采集/验证记录

- 采集包: sleepy-adapt (2026-09-06T12:57Z, v3.0 格式) — 抓包时 API 会话已过期
  (全部 401), 但 DOM 已渲染第 2 周课表, sessionStorage 含 `ROOT:APPSTORE.token`。
- token 有效期内实采: `login/currentUser`、`microForm/term` (30 学期)、
  `setting/current` (12 节)、`byStudent` 逐周 19 次 (390 行) + 无参对照 (114 行)。
- 解析结果与 DOM 第 2 周视图逐格一致 (10 个课块: 新时代×2、心理 5-8 连堂、
  马克思主义与社会科学方法论 9-12、材料 day6 5-6、高等传递 day5 7-8 + day6 9-12、
  高等催化 day3/day7 9-12)。
- 单测: `JwBoyaPpParserTest` (fixture 56 行真实数据裁剪, 18 课块断言)。
