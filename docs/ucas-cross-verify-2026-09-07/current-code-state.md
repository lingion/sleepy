# Current Code State — UCAS

- `TYPE_UCAS` is declared in `JwProtocol` and registered in `JwParserRegistry`.
- `xkgo.ucas.ac.cn/.../course/personSchedule` selects `ucas` before generic detection.
- `JwUcasParser` supports two paths:
  - **JSON 路径 (权威, exact-week)**: 解析 `selectedCourse + per-courseId.json` 拼合文档里的
    `courseTimeList[]` 条目. `courseWeek` 整数按位取低位 (bit i=1 → 第 i+1 周上课);
    `courseTime` 高位取字典前缀查 DAY_BITS 得星期, 低 12 bit 反转后是节次位图 (取最小最大
    节次作为 startNode/endNode). 来源契约: ldiex/UCAS_Course_Schedule_Convertor
    (POSITIVE 证据, 4 仓派单之一, Step 4 协议 matrix).
  - **HTML 路径 (fallback)**: 服务端渲染的 `/course/personSchedule` 网格, 节次+星期表头 +
    `/course/coursetime/` 课程详情链接. 无 JSON 时按当前学期 1-16 周占位导入
    (PROVISIONAL 常量), type=0.
- Fixture: `jw/fixtures/ucas/course-time-list.sample.json` (脱敏, ldiex 契约标注在文件头);
  HTML 路径用 `person-schedule.sample.html` (历史 #18 capture). 无原始用户数据入库.
- 周次类型: `JwCourse.type` 0=每周 1=单周 2=双周. UCAS parser 用 `weeksToType` 根据
  位图解码后的周次列表自动归类. Parser 常量 `TYPE_DEFAULT/TYPE_ODD/TYPE_EVEN` 与 JwCourse 契约一致.
- License attribution: 4 UCAS repos 致谢齐全 (`AboutLicenseAttributionTest.kt`,
  `LicenseScreen.kt` `school-ucas` card, 6 语 strings.xml) — Step 5.5 完成.
- `sleepy-collector v1.2` 现在跨 origin 详情链接走 controlled same-profile tab navigation,
  DOM 落 `4-detail-nav/`; 不再用 page-context fetch 或合成 POST.
- 状态: exact-week (JSON 路径) 已通过 ldiex 跨仓验证落地, JwUcasParserTest 7 个测试全绿,
  全量 testDebugUnitTest 1138 tests / 0 failures / 0 errors. #18 完整适配达成,
  不再依赖用户回传新 capture. **下一步**: 用户明确批准后 commit (单 commit, 邮箱
  `lingion@hrbeu.edu.cn`, 无 Claude 尾注), 然后才是 push / tag / release (需另行明示批准).
