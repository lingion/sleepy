# SWJTU(西南交通大学)跨仓验证 — scope.md

> 日期: 2026-09-15 · SOP: jw-cross-verify-sop v1.8 · 来源: 用户采集包(sleepy-adapt-0915-091154.zip)+ 用户明示"OK SOP 不能跳,依然要去 GitHub 再搜仓库,多方交叉验证"

## 用户原话

1. "sleepy sop 学校适配准备好 你先看完 我给你发压缩包" → 已读 SOP v1.8 全文
2. (压缩包: sleepy-adapt-0915-091154.zip) → "Please analyze the attached file(s)."
3. 首轮分析结论交付后 → "OK SOP 不能跳,依然要去 GitHub 再搜仓库,多方交叉验证"(否决了"包已实锤可跳检索"选项,Step 2 必跑全量)

## 学校与系统

- 学校: 西南交通大学(SWJTU),本部(犀浦/九里)
- 平台: 西南交通大学教学服务平台 `yhxt.swjtu.edu.cn`,厂商 **YETHAN/以专**
- 登录: CAS `cas.swjtu.edu.cn/authserver/login?service=https://yhxt.swjtu.edu.cn/cas-login.html`
- 凭据: JWT 落 localStorage `ytoken`,请求头 `ytoken: <JWT>`
- 课表端点(采集包实锤): GET `/yethan/common/course-schedule/student-course-schedule`(零业务参数,后端按 ytoken 识人)
- 学期/节次(免鉴权): GET `/yethan/public/sys/config/web` → termId/termName/termStartDate/termLessonStr(13 节次 HH:MM 串)
- 响应信封: `{"code":"00000","data":...,"message":...}`; 失效形态 code "401"/"A0230"
- 课表字段: 每课 `classTime1..40` + `classPlace1..40` 成对文本槽;形态 `"1-17周 星期五 3-4节"` / `"7、10-12、14-15周 星期三 5节"`;classPlace `X30547(犀浦)(Qingen Meng)`(校区括号+教师括号可选)

## 适配类型

**初次适配**(新增 type=yethan + JwYethanParser),涉及现行 parser: 否(无现成 yethan parser;现有 seu/cqu/eams5 三种 JSON 形态均对不上)。

schools.json 现状: 337 校;唯一 SWJTU 相关条目 = 西南交通大学希望学院(urp,119.6.110.75:9007),与本部无关。

## 已知同域姊妹平台(不进本 scope)

- `jwc.swjtu.edu.cn`(vatuu 老教务,session cookie)
- `jiaowu.swjtu.edu.cn` / `dean.swjtu.edu.cn`(老教务网,login.jsp + 验证码)
- `ocw.swjtu.edu.cn`(第二课堂,VATUU/YETHAN 系)
- 采集包内 zhzs AI 门户详情页/选课记录页(与教务课表无关)

## Step 2 检索矩阵执行记录

- 接口: api.github.com search/repositories(Bearer gh auth token)
- 查询串 6 条: "西南交通大学 教务" / "西南交通大学 课表" / "Southwest Jiaotong University" / "SWJTU" / "SWJTU course OR jwgl OR timetable OR schedule" / "yethan"
- 命中汇总(去重后)教务/登录/课表/选课直接相关 **11 仓**,全部纳入派单:
  1. lpzams/swjtu-course-crawler(Python, 课表爬虫)
  2. AmaneSuzuha000/SWJTU_Login(Python, **双系统**: vatuu JWC + YHXT yethan ytoken)
  3. 949144093/SWJTU-JiaoWuAutoLogin(Python, 教务验证码登录)
  4. HackSwjtu/Postime(MIT, 老教务网评课插件, 2016-17)
  5. kakasearch/course_download(Python, 教务资源下载)
  6. zx1411057234/VatuuSpider(Python, vatuu 爬虫)
  7. 1837634311/SWJTU-Course-Management-Script(GPL-3.0, 选课/退课, README 称"换新系统")
  8. Arex-lbb/auto-course-grabber(TS, Electron 抢课, JWT+SM2)
  9. 1-nuo/swjtu-course-grabber(JS, Node 抢课, ytoken cookie + JWT)
  10. kashaku/no-vatuu-evaluation(MV3, VATUU 评教)
  11. Joe-create-star/swjtu-dektx-reminder(MIT, ocw.swjtu.edu.cn 第二课堂)
- 检索触达但不属于教务协议证据的(仍记档,见 candidates.json non-protocol 节): swjtuhub/swjtu-wiki(40★ wiki)、swjtuhub/SWJTU-Courses(333★ 课程资料)、NaroZeol/GetNoticeFromSWJTU(通知聚合)、swjtuThesis 系列 LaTeX 模板、AutoCourseGrabber(西北农大同名词,域名不符)、各课程作业/科研仓。
- 唯一否决条款(archived+>10年): 无命中归档仓。
- yethan 查询确认: GitHub 上无 YETHAN 厂商开源学生仓库(yethangul 系韩文字体仓为误命中)。

## Step 5 现状代码阅读

- 协议注册: `JwProtocol.kt` — 31 个 TYPE 常量,ALL_TYPES 31 项,schools.json declared types 29(yethan 未收录,需 31→32 ALL_TYPES +schools.json 29→30)
- Fetch 注入点: `JwWebViewLoginScreen.kt` type 分派链(wisedu/neu/cqu/whut/chaoxing/boya_pp/qz_app/bjtu/eams5/…),每协议一段 `*_FETCH_JS` + `evaluateFetchWithTimeout` → `__sleepyBridge.onWiseduResult({ok,data,periods,startDate})`
- 结果回调解析: `JwCquParser`(嵌套 JSON)/`JwEams5Parser`(三段 JSON)/`JwSeuParser`(扁平 JSON)与本包形态对不上 → 新写 JwYethanParser
- 计数闸 4 处: ALL_TYPES 数量测试 / qz 系数量 / 校总数 337 / schools.json declared 集合
- fixtures 现状: 17 目录(bjtu/boya_pp/chaoxing/eams5/jou/neu/qz_app/qz_br/qz_ieas/qz_old/qz_with_node/scu/seu/ucas/ustc/zju/adversarial),无 swjtu/yethan

---

## 实测验证闸门(2026-09-15 用户定版,覆盖此前一切验证口径)

用户原话:
- 「交叉验证最终还要做实测验证。一切都以我那个压缩包为主,之前的老教务系统仅作为参考。」
- 「只有最新的压缩包能保证导入完成,并且用户能在最新的教务系统里按这个结构抓取成功,才算真正验证成功。」

### 三层验证定义

| 层 | 内容 | 状态 |
|---|------|------|
| 1. 采集包离线导入干跑 | 用包内真实 course-schedule JSON(10 门课 85 条 classTime)+ config(13 节次/开学日 2026-09-07)驱动计划中的解析算法,要求 100% 吃掉、零丢课 | 2026-09-15 通过:85/85 全解析;顿号枚举「6、10-11周」= 同一天节后缀共享周列表(首版按段独立解析错,已定正解);329 条课次展开,节点 3..12 ≤13,周 1..17,零非法碰撞;地点四形态(含北区田径场(犀浦) 与 Online(教师))全覆盖 |
| 2. 真实系统按此结构抓取成功 | 用户在最新教务系统 yhxt.swjtu.edu.cn 实际登录,App 按本结构抓取课表成功 | 待做(需用户参与) |
| 3. 导入完成 | 上述抓取结果在 App 内完整落成课表 | 待做(依赖层 2) |

### 层 1 干跑修正记录(错误→正解)

- 错:把「7、10-12、14-15周 星期三 5节」按 `、` 拆成独立段、每段自带星期节 → 11/85 段解析失败。
- 正解:`、` 枚举只作用于周列表,整串共用同一个「星期X 节」后缀。修正后 85/85。
- 地点:四形态 `room(campus)` / `room(campus)(teacher)` / `Online` / `Online(teacher)` / 非编码 `北区田径场(犀浦)`(等宽括号宽容);教师括号剥离后 room 保留。
