# NewUrp Super parser — current-code-state (Step 5)

日期: 2026-10-07 · 分支: feat/adapt-new-urp-super-parser · 基线: main (= adapt/hfut-issue46-r2-semesterid-loss @ HEAD)

## 实现文件

- `app/src/main/java/com/lingion/sleepy/data/jw/JwNewUrpSuperParser.kt`
  - 继承 `JwParser`
  - `generateCourseList()`: 抠 JSON 数组 → 遍历 items → 解析 skzc 位图 → `weekBitsToRanges` 段合并 → 输出 `JwCourse`
  - `extractJsonArray()`: 双策略 — 纯 JSON 数组文本 (深度平衡扫描) / HTML 包裹 (`"skzc"` 锚点往前找 `[`)
  - `confidence()`: skzc+id+kcm = 100, skzc+id = 70, 仅 skzc = 30
  - `matchedFeatures()`: skzc / id / kcm / jxlm / cxjc
  - `parseWeekBits()` / `weekBitsToRanges()`: 复用 `JwNewUrpParser` 同款逻辑

## 注册/集成

- `app/src/main/java/com/lingion/sleepy/data/jw/JwProtocol.kt`
  - L70-84: `TYPE_NEW_URP_SUPER = "new_urp_super"` 常量 + KDoc
  - L289: `WAKEUP_COMPAT_TYPES` 列表新增
  - L312-313: `displayName` "新 URP 教务 (WakeUp 兼容)"
  - L345: `category()` 走 `other` 分支
- `app/src/main/java/com/lingion/sleepy/data/jw/JwParserRegistry.kt`
  - L16: `TYPE_NEW_URP_SUPER to 16` (priority, 介于 XJU_POST=15 与 WISEDU=20)
  - L62: `JwProtocol.TYPE_NEW_URP_SUPER to ::JwNewUrpSuperParser` (FACTORIES 注册)
- `app/src/main/java/com/lingion/sleepy/ui/screen/mine/LicenseScreen.kt`
  - L552-558: `school-new-urp-super` PerSchoolEntry (置于 HZCU 之后)

## 测试

- `app/src/test/java/com/lingion/sleepy/data/jw/JwNewUrpSuperParserTest.kt` (4121 chars, 4 用例)
  - flat-array 解析 (5 课)
  - confidence 评分边界 (三件套 / 双件 / 单件)
  - matchedFeatures 列表
  - 空 / 非法输入回退
- 配套 fixture: `app/src/test/resources/jw/new-urp-super/sample-flat-array.json` (5 课, 修正 28-char 位图)

## 编译/测试状态

- `./gradlew :app:compileDebugKotlin` ✓
- `./gradlew :app:testDebugUnitTest --tests '*JwNewUrpSuperParser*'` BUILD SUCCESSFUL ✓

## strings.xml 翻译

(待 6 语言 strings.xml 同步更新 attribution segment)

## AboutLicenseAttributionTest

(待更新 Attribution() 列表 — INDIRECT 项目)

## 与 TYPE_NEW_URP 的关系

- TYPE_NEW_URP (旧 PR): 解析 `o0` 嵌套 (dateList → selectCourseList → timeAndPlaceList)
- TYPE_NEW_URP_SUPER (本 PR): 解析 `o0O0o` flat array
- confidence 排序自动二选一: 若数据含 `dateList`/`selectCourseList` 锚点, 走 TYPE_NEW_URP; 若含 `id.skzc` 锚点, 走 TYPE_NEW_URP_SUPER. 二者不冲突.

## 已知 lint 基线

(待 lint 复测, 暂未引入新增 lint 项)