# Sleepy 侧现有 THU 接入点 — v1.11 SOP Step 5

**Read Time**: 2026-10-06
**Reader**: lingion <lingion@hrbeu.edu.cn>

## 1. JwProtocol 常量层 — 缺 `TYPE_THU`

`app/src/main/java/com/lingion/sleepy/data/jw/JwProtocol.kt`:
- 现有 46 个 `TYPE_*` 常量 (WakeUp compat 8 个, 原生 38 个)
- **无 `TYPE_THU`** — 需新增
- `ALL_TYPES` 排序列表 (45 entry), `WAKEUP_COMPAT_TYPES` 8 个子集 — THU 应入 WAKEUP_COMPAT
- `displayName(type: String)` 当前 map 缺 THU → null (未识别的协议) — 需补
- `category(type: String)` (推断存在) — 需补 (其他 wakeup compat 都归 "wakeup_compat" 类)

## 2. JwParser 抽象层 — 已 T8 升级

`app/src/main/java/com/lingion/sleepy/data/jw/JwParser.kt`:
- `abstract fun generateCourseList(): List<JwCourse>` — 主解析入口
- `open fun confidence(): Int = 0` (T8 新加, 0-100) — 协议指纹强度
- `open fun matchedFeatures(): List<String> = emptyList()` (T8 新加) — 诊断标识
- 已有 `JwParity.adjustedRange()` 帮手 — THU 周次解析可复用 (单/双周端点修正)

## 3. JwParserRegistry 路由层 — 缺 THU factory

`app/src/main/java/com/lingion/sleepy/data/jw/JwParserRegistry.kt`:
- `TYPE_PRIORITY` linkedMapOf 现有 45 个 type → priority int — THU 应入 (建议 priority = 16, 排在 XJU_POST 后, 原生前)
- `FACTORIES` linkedMapOf 现有 45 个 type → factory lambda — THU 应入 `JwProtocol.TYPE_THU to ::JwThuparser`
- `selectBest()` 委托 TYPE_PRIORITY 裁决 — THU 加入后自动参与 fallback 路径

## 4. JwWakeUpCompatParsers — 现有 8 配的样板

`app/src/main/java/com/lingion/sleepy/data/jw/JwWakeUpCompatParsers.kt` (300+ 行):
- `private object WakeUpCompat` 内部单例, 提供 `parse(source, markers, jsonHint)` 通用入口
- 现有方法: `parseJzJson` / `parseJzHtml` / `parseKingoInfo` / `parseChaoxing` / `parseCumtb` / `parseSouthSoft` / `parseKingoTaskActivity` / `parseXju`
- **THU 不是简单 JSON/table 解析**, 是 HTML embedded JS 协议 (setInitValue block + gridData array + aX_N 节点映射) — **不应放 WakeUpCompat**, 应单独 `JwThuparser : JwParser` 类 (与 `JwNeuParser` 同样的独立类模式)

## 5. JwImportViewModel — 路由白名单

`app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwImportViewModel.kt`:
- `isRoutable(type: String): Boolean` — 仅放行 `JwProtocol.ALL_TYPES ∪ WAKEUP_COMPAT_TYPES`
- THU 加入 WAKEUP_COMPAT_TYPES 自动 routable, 不需改 viewModel

## 7. 测试模式 — 已有 JwBjtuParserTest 样板

`app/src/test/java/com/lingion/sleepy/data/jw/JwBjtuParserTest.kt` (240 行):
- `res(name)` 读 fixture (classpath `jw/fixtures/<dir>/<file>`)
- A. week grammar 表驱动 (regex 不变式测试)
- B. end-to-end 解析 (5/8 课 fixture)
- C. multi-doc combined 裁决
- D. confidence / matchedFeatures 验证
- E. negatives (login/foreign/plain)
- **F. 跨语言不变式** (SOP 铁律 3): `srcOf(rel)` 读 kotlin 源 + 读 WebViewLoginScreen.kt JS 源, char-by-char 校验 regex literal 一致
- G. registry routing

THU 复用此模式 + fixture 目录 `jw/fixtures/thu/`, source path `com/lingion/sleepy/data/jw/JwThuparser.kt`, WebView 入口 `JwThuparser.WEBVIEW_LOGIN_URL` + `JwThuparser.HOST_SUFFIX` + `JwThuparser.PATH_ZHKBCX` 等 path 常量。

## 8. 致谢 4 处

`app/src/main/java/com/lingion/sleepy/ui/screen/settings/LicenseScreen.kt`:
`app/src/main/res/values/strings.xml` + 5 语 `values-*/strings.xml`:
`app/src/test/java/com/lingion/sleepy/util/AboutLicenseAttributionTest.kt`:
`app/src/main/assets/attribution-candidates.json`:
- 现有 8 配致谢条目, THU 8 仓需 append (SOP §5.5 触达即致谢)

## 9. 不复用 WakeUp 14 节外部 JSON 的决策 (Step 3/4 锁定)

- `schedule-data.netlify.app/{semester}.json` — 单源独证 (1/8 = 仅 WakeUp)
- 4/8 仓硬编码 (Starrah+piggyham+brightcolin+denny2001) 印证硬编码是共识
- Sleepy 落地: **直接硬编码 14 节表** (复用 WakeUp THUParser.java 内 14 元素 list, 但不引用 schedule-data.netlify.app)
- 代码内常量提取为 `JwThuparser.THU_TIME_SLOTS: List<Pair<String, String>>` (起始 HH:MM + 结束 HH:MM 共 14 对) — 跨仓不变式锁定 (SOP 铁律 3)
