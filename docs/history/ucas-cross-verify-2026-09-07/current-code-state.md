# Current Code State — UCAS

- `TYPE_UCAS` is declared in `JwProtocol` and registered in `JwParserRegistry`.
- `xkgo.ucas.ac.cn/.../course/personSchedule` selects `ucas` before generic detection.
- `JwUcasParser` supports two paths:
  - **JSON 路径 (权威, exact-week)**: 解析 `selectedCourse + per-courseId.json` 拼合文档里的
    `courseTimeList[]` 条目. `courseWeek` 整数按位取低位 (bit i=1 → 第 i+1 周上课);
    `courseTime` 高位取字典前缀查 DAY_BITS 得星期, 低 12 bit 反转后是节次位图 (取最小最大
    节次作为 startNode/endNode). 来源契约: ldiex/UCAS_Course_Schedule_Convertor
    (POSITIVE 证据, 4 仓派单之一, Step 4 协议 matrix).
  - **HTML 路径 (fallback)**: 服务端渲染的 `/course/personSchedule` 网格 + 跨源详情站
    (xkcts.ucas.ac.cn:8443, **免登录**, v1.2 采集包实测) 原生 HTTP enrich。JwImportActivity
    在 `TYPE_UCAS` 时先走 `UcasDetailFetch.enrich()`: 从网格提取详情 URL → 逐课直抓 → marker
    分段拼组合源; parser 解析详情三行组 (上课时间/地点/周次), 只 enrich 网格已有的课
    (网格权威, 详情不造课), 带洞周次 `splitWeekRuns` 拆可表示段; 抓取失败/详情缺失回退
    1-16 占位 (PROVISIONAL 常量), type=0.
- Fixture: `jw/fixtures/ucas/course-time-list.sample.json` (脱敏, ldiex 契约标注在文件头);
  HTML 路径用 `person-schedule.sample.html` (历史 #18 capture) + `coursetime-multi.sample.html`
  (真实 `<p>课程名称：X</p>` 形态, 带洞/单周枚举) + `coursetime-continuous.sample.html`
  (宽容 th/td 分离行形态). 无原始用户数据入库.
- 周次类型: `JwCourse.type` 0=每周 1=单周 2=双周. UCAS parser 用 `splitWeekRuns` 把任意周次
  集合拆 (startWeek,endWeek,type) 段 — 步长1→每周, 步长2→单/双周, 缺口/步长切换断开;
  JSON 位图路径同款, 替换旧 `weeksToType` first..last 有损合并. 常量
  `TYPE_DEFAULT/TYPE_ODD/TYPE_EVEN` 与 JwCourse 契约一致.
- License attribution: 4 UCAS repos 致谢齐全 (`AboutLicenseAttributionTest.kt`,
  `LicenseScreen.kt` `school-ucas` card, 6 语 strings.xml) — Step 5.5 完成.
- `sleepy-collector v1.2` 现在跨 origin 详情链接走 controlled same-profile tab navigation,
  DOM 落 `4-detail-nav/`; 不再用 page-context fetch 或合成 POST.
- 状态: exact-week 详情路径已落地并测试锁死 (UCAS 16 tests 全绿, 全量 1248 / 0 failures /
  0 errors; lint 增量 0 — 52 errors 为 main 存量, 干净基线同值). #18 代码侧完成;
  issue 回复/关闭/push 待用户明示批准.
