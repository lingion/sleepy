# Current Code State — 安徽大学 (AHU) EAMS5 适配现状

> 调研日: 2026-09-06
> 调研方法: 读 `app/src/main/` + `app/src/test/` 下相关 .kt/.js, 不读本地反推

## 1. 协议入口(已注册)

`app/src/main/assets/schools.json:4-8`:
```json
{
  "name": "安徽大学",
  "url": "https://jw.ahu.edu.cn/student/for-std/course-table",
  ...
  "slug": "ahu"
}
```

→ `JwProtocol.TYPE_EAMS5`, URL 推断前缀 = `/student`(短前缀 supwisdom 形态)。

## 2. 前缀推断 (无需改)

`app/src/main/java/com/lingion/sleepy/data/jw/Eams5PathPrefix.kt:23-30`:
```kotlin
fun fromSchoolUrl(url: String): String {
    val u = url.lowercase()
    if (u.contains("/eams5-student")) return DEFAULT
    if (u.contains("/for-std/") || u.contains("/student/")) return STUDENT
    return DEFAULT
}
```

→ `eams5PathPrefixFor("https://jw.ahu.edu.cn/student/for-std/course-table")` = `/student`。
已有测试 `Eams5PathPrefixTest:ahu url yields student prefix`。

## 3. 学号提取 (无需改)

`app/src/main/java/com/lingion/sleepy/data/jw/Eams5PathPrefix.kt:58`:
```kotlin
val EAMS5_STUDENT_ID_REGEX: Regex = Regex("""studentId\s*[=:]\s*['"]?([A-Za-z0-9]+)['"]?""")
```

跨语言 invariant 测试 `Eams5StudentIdExtractionTest:140-146` 锁住 JS regex literal
与 Kotlin regex pattern 逐字符相等。

## 4. 登录态检测 (无需改)

`isEams5LoginRedirect(finalUrl: String)` 已能识别 `/student/login?refer=...`
(`Eams5StudentIdExtractionTest:124` 已覆盖)。

## 5. ★ fetch JS(待评估 + 修复)

`app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt:814-883`

### 当前形态(3 段)

```
[1] fetch PREFIX + '/for-std/course-table'
    → 检测 /login
    → 从 HTML 提取 studentId

[2] fetch PREFIX + '/ws/schedule-table/datum' (POST)
    body: {"lessonIds":[], "studentId":<id>, "weekIndex":""}
    → 期望返 scheduleList JSON

[3] __sleepyBridge.onWiseduResult({ok, data})
```

### v1 简化点(可能根因)

- **lessonIds=[]**: 简化版,假设上游对空数组回退返全量。合工大实测接受, 安大可能
  拒绝(500)。
- **weekIndex=''**: 不指定周次,期望返整学期。**安大 EAMS5 是否接受空 weekIndex 未知**。
- **没有预调 `get-data?bizTypeId=23`**: Chiu-xaH/HFUT-Schedule 的实现中, 课表接口
  在 POST datum 之前要先拉 lessonIds, 安大可能要求同样的预调用。

## 6. Parser 形态(JwEams5Parser.kt:24-43)

```
data 形态: {"result":{"lessonList":[…],"scheduleList":[…],"scheduleGroupList":[…]}}
字段映射:
  scheduleList[].lessonId (Int)  → lessonList[].id 查 courseName
  scheduleList[].room.nameZh     → room
  scheduleList[].personName      → teacher
  scheduleList[].weekday (1-7)   → day
  scheduleList[].weekIndex       → startWeek = endWeek
  scheduleList[].startTime (HHmm) → 推断 startNode (标准 985 表)
  scheduleList[].endTime   (HHmm) → 推断 endNode
  scheduleList[].periods         → 兜底 endNode = startNode + periods - 1
```

confidence 评分:scheduleList+lessonList → 95; 单纯 schedule-table/datum → 80。

**Parser 形态与 schedule-table/datum POST 响应契约强绑定**。如果安大响应形态不同,
parser 需要扩展。

## 7. 现有 fixture

`app/src/test/resources/jw/fixtures/eams5/`:
- `schedule-table-datum.sample.json`
- `course-table-info.sample.html`

→ **没有安大专属 fixture**; 现有 fixture 是合工大形态。
