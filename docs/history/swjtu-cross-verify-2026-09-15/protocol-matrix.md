# SWJTU 多仓协议矩阵

生成日期：2026-09-15。列为 `candidates.json` 中全部 11 个候选，未因“无关”而删除。

| 对比维度 | lpzams/swjtu-course-crawler | AmaneSuzuha000/SWJTU_Login | 949144093/SWJTU-JiaoWuAutoLogin | HackSwjtu/Postime | kakasearch/course_download | zx1411057234/VatuuSpider | 1837634311/SWJTU-Course-Management-Script | Arex-lbb/auto-course-grabber | 1-nuo/swjtu-course-grabber | kashaku/no-vatuu-evaluation | Joe-create-star/swjtu-dektx-reminder |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 登录入口 URL 形态 | 旧 vatuu | CAS `/yethan/public/cas/tms`；另有 CAS login service | 旧 vatuu 表单 | 旧教务表单 | 旧 vatuu | 旧 vatuu | TMS/vatuu | YHXT CAS/JWT | YHXT ytoken | 旧 vatuu | OCW/YETHAN 第二课堂 |
| studentId 提取位置 | 旧页面/会话 | JWT payload `sub` | 旧会话 | 旧表单 | 旧会话 | 旧会话 | 旧会话 | JWT payload `sub` | JWT payload `sub` | 旧会话 | 未形成 YHXT studentId 证据 |
| studentId 形态 | 旧系统字段 | 数字学号（JWT sub） | 旧系统字段 | 旧系统字段 | 旧系统字段 | 旧系统字段 | 旧系统字段 | JWT sub | JWT sub | 旧系统字段 | 未知 |
| API 路径 | 旧 vatuu 课程路径 | `/yethan/common/*`、`/public/*`；选课 `/register/*` | `/vatuu/*` | 老教务路径 | `/vatuu/*` | `/vatuu/*` | `/vatuu/*` / TMS | YHXT course/register 路径 | YHXT register 路径 | `/vatuu/*` | `ocw.swjtu.edu.cn/yethan/YouthIndex` |
| 请求方法 | 旧系统 GET/POST | 课表 GET；配置 GET；选课按接口 | 旧系统 POST | 旧系统 POST | 旧系统 GET/POST | 旧系统 GET/POST | 旧系统 GET/POST | GET/POST | GET/POST | GET/POST | 页面请求 |
| 登录态失效表现 | 旧页面/会话 | 业务码 `401`/`A0230`；跨仓补充 `A0422` | 旧登录失败 | 旧登录页 | 旧登录页 | 旧登录页 | 旧登录/会话 | HTTP 401 或业务码 `401`/`A0230`/`A0422` | 业务码/HTTP 401 | 旧登录页 | 未验证课表 API 失效 |
| 反爬/加密 | 旧验证码 | common 课表无 `_j`；register/sport 使用 SM2 `_j`、C1C3C2 + `04` | 验证码 | 旧表单 | 旧验证码/会话 | 验证码 | 旧验证码/会话 | SM2、JWT、Referer/Origin | SM2、JWT | 旧会话 | 页面会话 |

## 求同与存异

- **当前本部课表适配的共同证据**：YHXT base 为 `https://yhxt.swjtu.edu.cn/yethan`，凭据是 `ytoken` JWT；课表属于 `/common` 无业务参数 GET 族。
- **失效码需覆盖**：采集包的 `401`、`A0230` 与 Arex-lbb 交叉发现的 `A0422` 均应视为登录态失效。
- **不能混用旧协议**：五个 VATUU/旧教务候选和一个 TMS 候选不能为 YHXT parser 提供 endpoint 依据。
- **SM2 边界**：只在 register/sport 选课接口出现；Sleepy 读取 common 课表不需要实现 SM2。
