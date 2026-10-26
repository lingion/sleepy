# BJTU 跨仓验证 — findings (Step 3 派单 verdict 汇总)

> 日期: 2026-09-09 · 派单 24 / 收到 24 · greasyfork 1 条网络不可达(fetch-failure)
> verdict 口径: POSITIVE=给出可执行教务协议(端点/表单/解析) · INDIRECT=只给局部/外围证据 · NULL_EVIDENCE=无协议内容 · NEGATIVE=证据指向非主校区或非在线协议 · FETCH_FAILED=源不可达(仍致谢)

## Tally

| verdict | 数量 | 候选 id |
|---------|------|---------|
| POSITIVE | 12 | 1,2,3,4,5,6,7,10,13,14,15,16 |
| INDIRECT | 7 | 8,11,12,18,19,21,22 |
| NULL_EVIDENCE | 2 | 17,20 |
| NEGATIVE | 2 | 9,23 |
| FETCH_FAILED | 1 | 24 |

## 逐仓 verdict

### POSITIVE (12)

**id1 wan300/bjtu_mis_Android** (Kotlin/Compose, push 2026-09-08, MIT, 9星) — 本轮最完整协议源。
- 登录: GET `mis.bjtu.edu.cn/home/` 落到 `cas.bjtu.edu.cn/auth/login` (Django CAS); 解析 `form#login` 隐藏域 `next`/`csrfmiddlewaretoken`/`captcha_0` + `img.captcha`; POST 字段 `next, csrfmiddlewaretoken, loginname(学号/工号), password, captcha_0(验证码id), captcha_1(算式答案)`; Headers `Referer=登录页url, Origin=cas.bjtu.edu.cn`。仍在 /auth/login = 失败, 错误从 `.tishi` 提取。
- AA 桥: GET `mis.bjtu.edu.cn/module/module/10/` 用 regex `https?://[^\s"'<]+/client/login/[^\s"'<]+` 提取 AA 直登 URL, GET 后落到 `aa.bjtu.edu.cn/notice/item/`。
- 课表: GET `aa.bjtu.edu.cn/course_selection/courseselect/stuschedule/` 无 term/week 参数(服务端按会话定); HTML `table.table`, 表头 th 星期一至星期日; 行首格 `第N节 <span class="text-muted">[HH:MM-HH:MM]</span>`; 日格内 div 块: `M310005B [01]`(regex `([A-Z]\d+[A-Z]?)\s*\[([^]]+)]`) + span 课名 + `<div style="max-width...">第01-16周 <i>教师</i></div>` + `<span class="text-muted">校区, 楼, 室</span>`。
- 周次解析(canonical, 无 bug): regex `(\d{1,2})(?:\s*[-~—–－至到]\s*(\d{1,2}))?` 展开区间后, 文本含 `单/odd` 奇数过滤、`双/even` 偶数过滤。样例形态: `第01-16周` / `第1,3,5-7周（单周）` / `第02-17周（双周）` / 全枚举 `第1,2,...,17周`。
- 其他端点: 成绩 `/score/scores/stu/view/?zxjxjhh={term}&ctype=ln|current`; 考试 `/examine/examplanstudent/stulist/?zxjxjhh=`; 学籍 `/school_census/schoolcensus/stuview/`; 空教室 `/classroom/timeholdresult/room_view/?zxjxjhh=&zc=&jxlh=&jash=`; 选课 `/course_selection/courseselecttask/selects/` + `selects_action/?action=load|submit`(hashkey+answer 验证码)。
- 学期选择器: `<select name=zxjxjhh>`; VE 校历 xqCode=2025202602 (YYYYYYYY+TT, currentFlag 1/2)。
- 登录态失效: 重定向到 `/client/login/` 或 body 含 "用户登录"+"教学"。

**id2 Anyes666/BJTU-MIS-HarmonyOS** (TS/HarmonyOS, MIT) — 课表/考试/历年成绩/校历; courseCode regex `/[A-Z]\d{6}[A-Z]?/`; 学期 label regex `20\d{2}\s*[-~至]\s*20\d{2}[^\n]{0,16}学期`; 学籍路径 `/school_census/schoolcensus/stuview/`; 单元格另有 `div.ellipsis[title="第01-16周 星期二 第1节 校区, 楼, 室"]` 形态。

**id3 HFDLYS/BJTUselfService** (Kotlin, MIT, 100星, 旗舰) — CAS 算式验证码: `input#id_captcha_0`=id, `#id_captcha_1`=答案, 图为 `数字+数字=` 算式(regex `\d+[+\-*]\d+=`), 提交算得结果非算式本身; 课表双端点 `/course_selection/courseselecttask/schedule/`(选课任务课表) + `/course_selection/courseselect/stuschedule/`(本学期); 教师 map `/course_selection/courseselectabsent/absent_list/`; 会话过期文案 `会话已过期/登录已失效/重新登录`; 2FA 词条 `二次认证/多因素认证`。

**id4 fish2lab/bjtu-cli** (Swift CLI) — MIS/AA/VE 三栈; VE(123.121.147.7:88): 周课表 `/ve/back/course.shtml?method=getTimeList` 返回 JSON `{weekCode}`; 学期 `/ve/back/rp/common/teachCalendar.shtml?method=queryCurrentXq` 返回 `{result:[{xqCode,...}]}`; 登录 `/s.shtml` md5 密码 + sessionId。警示 bug: 其 parseCourseWeeks 先剥 `第/周` 再按 `,`/`-` 展开, 丢掉 (单)/(双) 奇偶后缀 — Sleepy 实现必须保留奇偶过滤(以 id1 canonical 为准)。

**id5 fish2lab/BJTUselfService-macOS** (Swift) — id3 的 macOS 完整移植(含抢课), 协议与 id3 同源, 主佐证。

**id6 s1y4x1/BJTU-course-assistant** (JS) — 节次时间直接从表格首列解析: 节次 regex `第\s*\d+\s*节`, 时间 `\[([^\]]+)\]` — 与 id1 页面内嵌时间互证。

**id7 ZiuChen/userscript** (JS, MIT, 41星) — MIS 课表到 iCal userscript; 周次文法三形态(连续/离散/单周)与 id1 一致。

**id10 hyskr/BJTU-course-autoget-program** (Electron/JS, 38星) — AA 选课自动化 + 验证码自动识别; 选课 listing/submit 流与 id1 互证。

**id13 jlytwhx/bjtubox_python** (Python, 2020) — 交大魔盒后端(脱敏): 老教务 `dean.bjtu.edu.cn` `course_selection/courseselecttask/remains/` + `selects_action/` + `classroom/timeholdresult/room_stat/`; term 4 段码 `'2019-2020-1-2'`(zxjxjhh); 13 节/day schema; 移动端课表 skzc bitmap 周次表单。

**id14 Orien233/Campus-Mate** (Kotlin, push 2026-09-01) — BJTU 课表导入(WebView 会话复用), 与 id1 同一 AA 栈。

**id15 ymzhang-cs/BJTU-STU-MCP** (Python MCP) — MIS/AA 端点 MCP 封装(schedule/scores/exams), 与 id1/id3 互证。

**id16 xschur/CourseRobber** (Python, 2019) — `dean.bjtu.edu.cn` 抢课: `remains/` + `selects_action/`, 与 id13 互证老教务栈。

### INDIRECT (7)

**id8 ymzhang-cs/BJTU-iCalendar-Generator** (Python, MIT) — 课表到 iCal, 输入形态间接印证表格文本文法。
**id11 57Darling02/BjtuCoursePlatform** (JS, 18星) — 移动端后端 `123.121.147.7:8081 /course/get_stu_course_sched.action?id=00000&dateStr=YYYYMMDD`(返回 classBeginTime/classEndTime); VE sessionId/odbcPassword 登录; 无整学期课表端点。
**id12 jlytwhx/bjtuDean** (Python, 2019, MIT) — MIS+教务处模拟登录 + 空教室爬取(老栈外围)。
**id18 Yukikasu/BJTU_ezRate** (JS, MIT) — MIS 评教 userscript(+中国大学MOOC互评), 非课表。
**id19 xxxand/bjtu_teaching_assessment** (JS, MIT) — AA 评教表单页 `https://aa.bjtu.edu.cn/teaching_assessment/stu/*/update/`; DOM 契约 `label[for] 到 input[type=radio]` + textarea; 纯 DOM 无网络层。
**id21 aooxin/BJTU-CC** (Python, 2021) — 抢课含验证码自动填写, 老栈外围。
**id22 etherealviator/CourseTable** (TS/Expo, MIT) — BJTU 仅以 url-only 条目(`http://jwc.bjtu.edu.cn`)出现在通用 130 校词典 `schools-data.json:44-49`; WebView 注入为通用正方/jqGrid/HTML-table 探测, 无任何 BJTU 特有 adapter; 不能作为"BJTU=正方"的证据(host 猜测禁令的活教材)。

### NULL_EVIDENCE (2)

**id17 Futuremind-BJTU/Futuremind-BJTU** — org 简介 repo, 无代码无协议。
**id20 Coconut00/BJTU-script** (Python, 2018) — BJTU选课脚本, 无可提取现行协议(年代过老且无端点留存)。

### NEGATIVE (2)

**id9 Moliseeee/bjtu-timetable** (Python) — 纯手写 dict 到静态 .ics, 零网络代码(全仓唯一 URL=jsDelivr CDN 供输出文件); NEGATIVE 定级。incidental 价值: 其 `PERIODS` 7 节时间表 `1=08:00-09:50, 2=10:10-12:00, 3=12:10-14:00, 4=14:10-16:00, 5=16:20-18:10, 6=19:00-20:50, 7=21:00-21:50` 与共识节次表逐项一致, 成为节次硬编码表的第二独立来源(作者自称转录自教务系统课表, 海淀校区 2026-2027-1)。
**id23 mcdona1d/ZF-Assistant** (Python, 2016) — 三重实锤为北京交通大学海滨学院(独立学院, 沧州: repo 描述+菜单链接 `bjtuhbxy.cn`+天气代码 101090701 沧州), 走老正方 `default6.aspx`/`xskbcx.aspx`, `jw_url` 留空, 13 文件零条主校区端点 — 与主校区 MIS/AA 无交集; 同时佐证"主校区 BJTU 非正方协议"。

### FETCH_FAILED (1)

**id24 greasyfork 430918 北交大iCalender课表生成** — greasyfork.org 三路不可达(1 web2text 直连空; 2 gh.qdp.qzz.io 镜像 Not Found; 3 直连 curl HTTP=000, /en /zh-CN /code 路径全试)。按 SOP "宁可错谢不可放过" 仍入致谢(id24), findings 记 fetch-failure; 其"北交大课表到 iCal"描述与 id7/id8 同类, 不影响协议收敛结论。

## 收敛结论 (供 protocol-matrix)

24 仓证据全部指向同一协议族: BJTU 自研 Django 系 = CAS SSO(cas.bjtu.edu.cn, 算式验证码) + MIS 门户(mis.bjtu.edu.cn, `/module/module/{10,322,104}/` 桥) + AA 教学支撑平台(aa.bjtu.edu.cn, 主数据源) + 老教务(dean.bjtu.edu.cn, 遗留) + VE 智慧课程平台(123.121.147.7:88/8081, 周级 weekCode)。不属 Sleepy 现有任一协议族(wisedu/cqu/neu/eams5/zf_new/zf/urp/qz/ucas), 需新增 TYPE + 专属 parser。
