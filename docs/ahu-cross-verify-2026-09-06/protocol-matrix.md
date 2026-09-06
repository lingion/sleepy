# Protocol Matrix — AHU vs HFUT vs 上游共同形态

> 调研日: 2026-09-06
> 横向对比表: 7 维度。结论在底部。

## 维度表

| 维度 | **HFUT (合工大)** [Chiu-xaH/HFUT-Schedule] | **AHU (安大)** [MoeclubM + qiqqqqq517 + search/code] | 上游 supwisdom 标准 | sleepy v1.0.50 现状 |
|------|-------------------------------------------|------------------------------------------------------|---------------------|---------------------|
| **1. URL 前缀** | `/eams5-student` (长前缀) | `/student` (短前缀, supwisdom 自定义域部署) | 二者皆有, 看校 | `/student` (按 URL 推断, 现有代码正确) |
| **2. 协议族** | supwisdom 标准 EAMS5 | 金智 EAMS 新版 (自建新教务) | supwisdom | 当成 EAMS5 (type=eams5) |
| **3. 课表入口 HTML** | `GET /for-std/course-table` → 302 → `/info/<studentId>` | `GET /student/for-std/course-table` → 200, HTML 含 `allSemesters` | 同 | 同 (HFUT 段对 AHU 可复用, 检测点一致) |
| **4. 学号提取** | HTML `<script>` 段 `studentId='…'` (regex A-F) | **MoeclubM**: grade/sheet 302 redirect; **qiqqqqq517**: 留空 dataId 服务端按 session 绑定 | 上游 HTML script (HFUT 形态) | HFUT regex(A-F), 对 AHU 不致命 (HTML 段可能也有 studentId) |
| **5. 课表 API 形态** | **POST** `/ws/schedule-table/datum` body=`{lessonIds,studentId,weekIndex}` | **GET** `/for-std/course-table/get-data?bizTypeId=2&semesterId=<id>&dataId=` | POST datum | **POST datum** (✗ 安大 500) |
| **6. 响应 JSON 形态** | `{result:{lessonList[],scheduleList[],scheduleGroupList[]}}` | `{data:{lessons[]}}` (qiqqqqq517 实锤; MoeclubM 用 print-data 变体) | 同 HFUT | 当成 HFUT 形态解析 |
| **7. 学期/课表预取** | `/for-std/lessons?studentId=…` 拿 lessonIds[]; 然后 POST datum | 不需 lessons 预取; get-data 直接返 `data.lessons[]` | 同 HFUT | **没有 lessons 预取** (lessonIds=[] 简化, 对 AHU 不致命, 但 datum 本身 500) |

## 关键差异 (3 处需修复)

### A. 请求方法 + URL — 必修
- HFUT: `POST /ws/schedule-table/datum` body JSON
- AHU: `GET /for-std/course-table/get-data?bizTypeId=2&semesterId=<id>&dataId=`

### B. 响应 JSON 路径 — 必修
- HFUT: `result.lessonList[]` + `result.scheduleList[]`
- AHU: `data.lessons[]`

### C. 学期 ID 来源 — 必修
- HFUT: HTML `var semesterId = 234`
- AHU: HTML `allSemesters` JSON 数组(每个学期 `{id, name, ...}`); 取最新或当前学期

## 修复策略 (决策: 共用 EAMS5 type + 内部判别)

**方案**: 不新增 `JwProtocol.TYPE_AHU_EAMS_NEW`, 而是在 EAMS5 type 内部按 `school.url` 主机名分发到不同的 fetch JS, 同时让现有 `JwEams5Parser` 在 `confidence()` 中优先匹配 AHU 形态(`data.lessons[]`), HFUT 形态(`result.scheduleList`)作为 fallback。

**理由**:
1. 协议族根同源 (supwisdom 新版部署差异, 不是独立协议)
2. 避免 schools.json type 字段增项引发 179 校回归风险
3. fetcher 端分发单一, parser 端兼容两形态, 符合"协议族内形态适配"原则
4. 致谢/合规层不变 (eams5 协议族, 跨仓致谢)

**代价**:
- fetch JS 内多一段 hostname 分支 (可接受)
- parser 内多一处 `data.lessons[]` 解析路径 (可接受, 增量)
- 测试需新增 AHU 形态 fixture (必须)

## 外部佐证 (cross-citation 完整致谢链)

- **MoeclubM/AHU-AIO** (Dart, ★0, 无 license 但代码完整实现 AHU 新教务 REST)
  → https://github.com/MoeclubM/AHU-AIO
  → 关键文件: `lib/jw/api/jw_api.dart`, `lib/jw/pages/jw_schedule_page.dart`
  → 协议族 = AHU 自建'新教务'; studentId=302; endpoints=getSemesters + getCourseTablePrintData

- **qiqqqqq517/shangkeschschedule** (Apache-2.0, ★10)
  → `shared/assets/offline_repo/schools/resources/AHU/ahu.js`
  → 协议族 = 金智 EAMS 新版; WebVPN+CAS; dataId 空; lessons 由 get-data 直接返

- **abydym/Ahu_Plus** (GPL-3.0, ★2, Kotlin 同栈证据价值最高 — 验证中)
- **MuxYang/AhuCourseSelectCLI** (Python, 选课系统, 旁证 token/URL pattern — 验证中)
- **Tonyseth/AHU_JW_GPA_Calculator** (油猴脚本, GPA 计算含课表抓取 — 验证中)
- **Zeraora-807/Anhui-Univ-DSH-Tool** (TS, DeepSeek Harness — 验证中)
- **curdbin/AHU-API** (Python — 验证中)
- **UponNoise/AHU_SBI_DMT** (Python — 验证中)