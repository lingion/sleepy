# HFUT issue #46 跨仓验证 — scope

- **日期**: 2026-09-16
- **触发**: 用户原话「开新分支去看最新的 issue：合肥工业大学又抓新包了。」
- **issue**: #46 `[Adapt]: 合工大` (cysxun, 2026-09-16T06:36:29Z, OPEN)
  - 教务 URL: `one.hfut.edu.cn`（用户填的是门户,非教务;真实教务 jxglstu.hfut.edu.cn）
  - 附件: `sleepy-adapt-0916-143239.zip` ×2（正文+补充各一份,**md5 相同 aafec04f93255eade99480d71a46ef73,同一包传两次**,21MB,282 文件）
  - 归档: `issues/0046-adapt-hfut-one/`
- **类型**: 适配修 bug（已有 eams5 parser + HFUT 四段链 JS,非初次适配、非协议升级）
- **涉及现行 parser**: `JwEams5Parser` + `EAMS5_FETCH_JS`（issue #25 fb39af5a 落的四段链）

## 现状（动手前已读）

- 学校条目: `合肥工业大学 → https://jxglstu.hfut.edu.cn/eams5-student/for-std/course-table`, type=eams5（340 校之一）
- 采集链: `EAMS5_FETCH_JS` 四段（course-table→studentId / info→bizTypeId / get-data→lessonIds / POST ws/schedule-table/datum）
- 解析: `JwEams5Parser` 三形态（HFUT result.lessonList+scheduleList / AHU data.lessons / AHU studentTableVms.activities）
- 节次推断: `inferNodes` 硬编码 985 标准 5 段×2 节（08:00/10:10/14:00/16:10/19:00）
- 门户拦截: JS 首行对 `one.hfut.edu.cn` 显式拦截,提示"门户没有课表数据"（issue #25 的 8eef1ae7）

## 本包新证据（gate）

1. **D1（修）**: 合工大宣城校区 12 节次布局。datum 122 行里 `(1550,1730)` 10 行 + `(1920,2100)` 8 行共 **18 行（15%）节次映射错**：
   - 1550-1730（第 7-8 节）→ 现 inferNodes 落 null 分段 → 兜底 `node 1..periods`（错到上午第 1 节）
   - 1920-2100（第 10-11 节）→ 现 inferNodes 映射 section4 → node 9-10（差 1）
   - 根因: 服务端 `ws/schedule-table/timetable-layout` 有权威 `courseUnitList`（12 节次,含起止时间）,JS 链没抓,parser 用硬编码启发式猜
2. **D2（文案微调,策略不变）**: 门户 `one.hfut.edu.cn` 上线了课表预览卡 `GET /api/operation/course-timetable/search/1/<date>`（Bearer token,只回本周+下周共 12 行）。全学期数据仍只有 jxglstu datum 有（18 周 122 行）→ **门户仍不可作为导入源**,拦截策略不变,文案从"门户没有课表数据"改为准确表述。
3. **正面验证（不改）**: 现网四段链在本包完全走通（studentId=178619, bizTypeId=23, get-data→13 lessonIds, datum 122 行全 publish, lessonId2Flag 服务端已过滤无需客户端过滤）——#25 修复合用此账号实锤有效。

## 非目标

- 不从门户 API 导入（周范围预览,非全学期）
- 不动 AHU/CUMTB 形态（fallback 启发式保持原样,无回归）
- 不动登录链路（cas.hfut.edu.cn 本包仅采集,无新问题）
