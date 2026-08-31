# Widget 小尺寸变体(Small Variants)实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 5 个小组件各增加一个「· 小」变体(provider 声明),固定格数启动器(华为桌面等)上用户选小档即得专门排版的紧凑内容,解决固定尺寸下截断和比例别扭。

**Architecture:** 每个 widget 一个薄壳 Receiver 子类(继承现有 Receiver,只覆写 `variant` 标记) + 一个独立 `*_small_widget_info.xml`(targetCell 2×2 或 3×2)。渲染走现有 `WidgetBitmapRenderers`,新增 `WidgetVariant` 枚举参数;SMALL 分支画紧凑排版;SMALL 变体被用户拖大超过阈值时内部升档回全量排版。预览图由脚本调用渲染器生成 PNG 进 git。

**Tech Stack:** Kotlin, Android AppWidgetProvider (RemoteViews 同步 Canvas bitmap 管线,v1.0.36 已验证), JUnit4 单测, 真机 Mate 30 5G (TAS-AN00, 华为桌面)。

## Global Constraints

- 全程遵守 [[no-runtime-verification-static-only]]:本机只跑 build/单测/静态检查;真机验证只能用 adb(用户已授权 adb 真机流程);禁跑模拟器炸机器。
- UI 纯色块禁描边:[[ui-blocks-no-border-rule]] — 小档排版同样禁 BorderStroke/outline 画法。
- 禁彩色 emoji,功能标记只用 ✓✗⚠★ 文本符号。
- 仓库命令一律 `cd /Users/lingion_k/Desktop/sleepy` 前缀。
- minSdk 26;不新增第三方依赖;渲染器改动必须保持现有大档输出**逐字节不变**(老用户零影响是本方案的验收项)。
- `todayContentHeightDp` 等高度估算函数与渲染常量互为镜像(注释已写明"改那边必须同步这边"),小档新增常量同样要镜像。
- 测试命令统一:`./gradlew testDebugUnitTest --tests "..."`;构建命令:`./gradlew assembleDebug`。
- 不可逆操作(真机删 widget/清数据)前先问用户。

---

### Task 1: WidgetVariant 枚举 + 渲染器入口签名扩展(Today)

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/widget/WidgetVariant.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/widget/WidgetBitmapRenderers.kt:150` (renderToday 签名与入口)
- Test: `app/src/test/java/com/lingion/sleepy/widget/WidgetVariantRenderTest.kt`

**Interfaces:**
- Consumes: 现有 `renderToday(context, data, wDp, hDp)` 及 `WidgetData`
- Produces: `enum class WidgetVariant { REGULAR, SMALL }`;`renderToday(context, data, wDp, hDp, variant: WidgetVariant = WidgetVariant.REGULAR)`(默认参数 → 全部现有调用点零改动);`fun todayCompactTexts(context, data): List<String>`(小档纯文本提取,渲染与测试共用,单测断言它)

- [ ] **Step 1: 写失败测试**

```kotlin
package com.lingion.sleepy.widget

import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class WidgetVariantRenderTest {

    private val data = WidgetData(
        date = LocalDate.of(2026, 9, 1),
        courses = listOf(
            testCourse(name = "高等数学", startNode = 1),
            testCourse(name = "大学英语", startNode = 3),
            testCourse(name = "数据结构", startNode = 5)
        ),
        timeJson = TimeTableUtils.DEFAULT_TIME_JSON,
        hasTable = true,
        isDark = false,
        themeKey = "default"
    )

    @Test
    fun `compact texts keep only first course`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val texts = WidgetBitmapRenderers.todayCompactTexts(ctx, data)
        assertEquals(1, texts.size)
        assertTrue(texts[0].contains("高等数学"))
    }

    @Test
    fun `small render size matches request and regular path untouched`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bmp = WidgetBitmapRenderers.renderToday(ctx, data, 100f, 100f, WidgetVariant.SMALL)
        assertEquals((100f * ctx.resources.displayMetrics.density).toInt(), bmp.width)
        assertEquals((100f * ctx.resources.displayMetrics.density).toInt(), bmp.height)
        bmp.recycle()
    }
}

/** 测试用最小 CourseEntity — 若已有测试 fixture 工厂则复用之,删除本函数 */
private fun testCourse(name: String, startNode: Int): com.lingion.sleepy.data.entity.CourseEntity =
    com.lingion.sleepy.data.entity.CourseEntity(
        tableId = 1, name = name, teacher = "", room = "", dayOfWeek = 2,
        startNode = startNode, endNode = startNode + 1, colorKey = "blue", weeks = null
    )
```

注:CourseEntity 构造参数以实体真实定义为准 — 写测试前先 Read `data/entity/CourseEntity.kt`,按真实字段名/顺序修正构造调用;仓库里已有 `ExcelFramesetHtmlTest.kt` 等构造过实体,先 grep 复用现成 fixture 写法。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /Users/lingion_k/Desktop/sleepy && ./gradlew testDebugUnitTest --tests "com.lingion.sleepy.widget.WidgetVariantRenderTest"`
Expected: FAIL — `todayCompactTexts` 未定义 / renderToday 无 5 参重载

- [ ] **Step 3: 最小实现**

`WidgetVariant.kt`:

```kotlin
package com.lingion.sleepy.widget

/** 小组件排版档位 — REGULAR=现有全量排版(默认, 逐字节不变); SMALL=紧凑档(「· 小」变体专用) */
enum class WidgetVariant { REGULAR, SMALL }
```

`WidgetBitmapRenderers.kt` — renderToday 签名加默认参数,入口分派:

```kotlin
fun renderToday(
    context: Context, data: WidgetData, wDp: Float, hDp: Float,
    variant: WidgetVariant = WidgetVariant.REGULAR
): Bitmap {
    if (variant == WidgetVariant.SMALL && wDp < 150f) {
        return renderTodayCompact(context, data, wDp, hDp)
    }
    // SMALL 但容器被拖大 ≥150dp → 内部升档回全量排版(设计第三节决策)
    return renderTodayRegular(context, data, wDp, hDp)  // 原 renderToday 函数体整体改名迁入
}
```

原函数体原地改名为 `private fun renderTodayRegular(...)`(内容一行不动,保证大档逐字节不变)。新增 compact 分支与文本提取:

```kotlin
/** 小档纯文本行(渲染与单测共用单一事实来源)。空课表/学期外也各有对应一行。 */
fun todayCompactTexts(context: Context, data: WidgetData): List<String> {
    val ctx = SleepyApp.get()
    if (!data.hasTable) return listOf(ctx.getString(R.string.widget_create_schedule))
    if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) {
        val statusRes = if (data.semesterStatus == DateUtils.SemesterStatus.BEFORE_START)
            R.string.semester_not_started else R.string.semester_ended
        return listOf(ctx.getString(statusRes))
    }
    if (data.courses.isEmpty()) return listOf(ctx.getString(R.string.today_no_course))
    return data.courses.take(1).map { it.name }
}

private fun renderTodayCompact(context: Context, data: WidgetData, wDp: Float, hDp: Float): Bitmap {
    val density = context.resources.displayMetrics.density
    val w = (wDp * density).toInt(); val h = (hDp * density).toInt()
    val s = scheme(context, data.themeKey, data.isDark)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(c)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = s.bg
    canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 20f * density, 20f * density, p)
    val pad = 10f * density
    val lines = todayCompactTexts(context, data)
    // 日期行(顶部小字) + 首课程名(居中大字)
    p.color = s.onSurfaceVariant
    p.textSize = 11f * density
    canvas.drawText("${data.date.monthValue}/${data.date.dayOfMonth}", pad, pad + 11f * density, p)
    p.color = s.onSurface
    p.textSize = 15f * density
    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    var y = h / 2f
    for (line in lines.take(2)) {
        canvas.drawText(ellipsize(p, line, w - pad * 2), pad, y, p)
        y += 20f * density
    }
    return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
}

/** 按可用宽度截断文本(字符级贪心, 与 [[sleepy-vert-text-overflow-fix]] 同思路) */
private fun ellipsize(p: Paint, text: String, maxW: Float): String {
    if (p.measureText(text) <= maxW) return text
    var t = text
    while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
    return "$t…"
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd /Users/lingion_k/Desktop/sleepy && ./gradlew testDebugUnitTest --tests "com.lingion.sleepy.widget.WidgetVariantRenderTest"`
Expected: PASS

- [ ] **Step 5: 全量测试回归(证明大档无回归)**

Run: `cd /Users/lingion_k/Desktop/sleepy && ./gradlew testDebugUnitTest`
Expected: 全部 PASS(若出现 pre-existing 失败,对照 [[qdp-secret-persistence-fix]] 的"pre-existing 挂测试别当回归"原则先在 main 上复跑确认)

- [ ] **Step 6: Commit**

```bash
cd /Users/lingion_k/Desktop/sleepy && git add -A && git commit -m "feat(widget): WidgetVariant 枚举 + renderToday 紧凑档分支"
```

---

### Task 2: Today 小变体 Receiver + xml + manifest

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/widget/TodaySmallWidgetReceiver.kt`
- Create: `app/src/main/res/xml/today_small_widget_info.xml`
- Modify: `app/src/main/AndroidManifest.xml:100-115`(TodayWidgetReceiver 声明后追加)
- Modify: `app/src/main/res/values/strings.xml` + `values-en/strings.xml`(label)
- Test: `app/src/test/java/com/lingion/sleepy/widget/WidgetVariantRenderTest.kt`(追加路由测试)

**Interfaces:**
- Consumes: `TodayWidgetReceiver`(基类)、`WidgetVariant.SMALL`(Task 1)、`RemoteViewsWidgetHelper`
- Produces: `open val variantHint: WidgetVariant` 基类属性(默认 REGULAR,子类覆写 SMALL);小变体 provider 类 `TodaySmallWidgetReceiver`

- [ ] **Step 1: 写失败测试(路由断言:小壳类标记 SMALL)**

在 `WidgetVariantRenderTest.kt` 追加:

```kotlin
@Test
fun `small receiver declares SMALL variant`() {
    assertEquals(WidgetVariant.SMALL, TodaySmallWidgetReceiver().variantHint)
    assertEquals(WidgetVariant.REGULAR, com.lingion.sleepy.widget.TodayWidgetReceiver().variantHint)
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /Users/lingion_k/Desktop/sleepy && ./gradlew testDebugUnitTest --tests "com.lingion.sleepy.widget.WidgetVariantRenderTest"`
Expected: FAIL — `TodaySmallWidgetReceiver` / `variantHint` 未定义

- [ ] **Step 3: 基类加 variantHint 并让 push() 消费它**

`TodayWidgetReceiver` 修改(最小 diff):

```kotlin
open class TodayWidgetReceiver : AppWidgetProvider() {          // class → open class
    open val variantHint: WidgetVariant = WidgetVariant.REGULAR

    private fun push(context: Context, awm: AppWidgetManager, id: Int) {
        val data = loadDataSync(context)
        val opts = awm.getAppWidgetOptions(id)
        val (wDp, hDp) = RemoteViewsWidgetHelper.computeSizeDp(opts)
        val contentH = WidgetBitmapRenderers.todayContentHeightDp(data)
        // SMALL 变体: compact 分支内部还有 150dp 升档闸, 这里直接传 variant
        val variant = variantHint
        if (contentH <= hDp) {
            RemoteViewsWidgetHelper.renderAndPush(
                context, awm, id, TAG,
                loadData = { data },
                renderBitmap = { d, w, h ->
                    WidgetBitmapRenderers.renderToday(context, d, w, h, variant)
                }
            )
        } else {
            // SMALL 档内容只有 1-2 行, 永远装得下; 兜底仍走原 scrollable
            val shell = WidgetBitmapRenderers.renderToday(context, data, wDp.toFloat(), hDp.toFloat(), variant)
            RemoteViewsWidgetHelper.pushScrollable(
                context, awm, id, TAG,
                layoutRes = com.lingion.sleepy.R.layout.widget_scroll_today,
                shellBitmap = shell,
                scopeExtra = ScrollStripService.StripFactory.SCOPE_TODAY
            )
        }
    }
    // onUpdate / onAppWidgetOptionsChanged / companion 不动
}
```

`TodaySmallWidgetReceiver.kt`:

```kotlin
package com.lingion.sleepy.widget

/** 「今日课程 · 小」变体壳 — 只标记 SMALL, 渲染/数据全部继承基类 */
class TodaySmallWidgetReceiver : TodayWidgetReceiver() {
    override val variantHint: WidgetVariant = WidgetVariant.SMALL
}
```

- [ ] **Step 4: 新 xml(照抄现有 today_widget_info.xml 改尺寸)**

`today_small_widget_info.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp"
    android:minHeight="110dp"
    android:minResizeWidth="110dp"
    android:minResizeHeight="110dp"
    android:targetCellWidth="2"
    android:targetCellHeight="2"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_bitmap_container"
    android:previewLayout="@layout/widget_bitmap_container"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:description="@string/widget_today_small_label" />
```

strings.xml 追加(中/英):

```xml
<string name="widget_today_small_label">今日课程 · 小</string>
```
```xml
<string name="widget_today_small_label">Today\'s Classes · Small</string>
```

- [ ] **Step 5: manifest 注册**

在 TodayWidgetReceiver 的 `</receiver>` 后追加:

```xml
        <receiver
            android:name=".widget.TodaySmallWidgetReceiver"
            android:exported="true"
            android:label="@string/widget_today_small_label">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/today_small_widget_info" />
        </receiver>
```

(其余 4 行 intent-filter/meta-data 与现有 receiver 条目保持同构;照抄 manifest 里现有条目的 action 行。)

- [ ] **Step 6: 跑测试 + assembleDebug 静态验证**

Run: `cd /Users/lingion_k/Desktop/sleepy && ./gradlew testDebugUnitTest assembleDebug`
Expected: PASS / BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
cd /Users/lingion_k/Desktop/sleepy && git add -A && git commit -m "feat(widget): 今日课程·小 变体 receiver+provider 声明"
```

---

### Task 3: TwoDay 小变体

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/widget/WidgetBitmapRenderers.kt:579`(renderTwoDay 签名+SMALL 分支)
- Modify: `app/src/main/java/com/lingion/sleepy/widget/TwoDayWidget.kt`(open class + variantHint, 同 Task 2 模式)
- Create: `app/src/main/java/com/lingion/sleepy/widget/TwoDaySmallWidgetReceiver.kt`
- Create: `app/src/main/res/xml/twoday_small_widget_info.xml`
- Modify: `app/src/main/AndroidManifest.xml`、`values/strings.xml`、`values-en/strings.xml`
- Test: `app/src/test/java/com/lingion/sleepy/widget/WidgetVariantRenderTest.kt`

**Interfaces:**
- Consumes: `WidgetVariant`(Task 1)、`TwoDayWidgetReceiver.loadDataSync`
- Produces: `renderTwoDay(context, data, wDp, hDp, variant = REGULAR)`;`twoDayCompactTexts(context, data): List<String>`(只取今天第一条课名,空课表→"今日无课"一行);`TwoDaySmallWidgetReceiver`

- [ ] **Step 1: 写失败测试**(模式同 Task 1:`twoDayCompactTexts` 返回 1 条且含首课名;`TwoDaySmallWidgetReceiver().variantHint == SMALL`)

```kotlin
@Test
fun `twoDay compact texts keep only today first course`() {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    val td = TwoDayData(
        today = WidgetData(date = LocalDate.of(2026, 9, 1), courses = listOf(testCourse(name = "高等数学", startNode = 1)), timeJson = TimeTableUtils.DEFAULT_TIME_JSON, hasTable = true, isDark = false, themeKey = "default"),
        tomorrow = WidgetData(date = LocalDate.of(2026, 9, 2), courses = emptyList(), timeJson = TimeTableUtils.DEFAULT_TIME_JSON, hasTable = true, isDark = false, themeKey = "default")
    )
    val texts = WidgetBitmapRenderers.twoDayCompactTexts(ctx, td)
    assertEquals(1, texts.size)
    assertTrue(texts[0].contains("高等数学"))
}

@Test
fun `twoDay small receiver declares SMALL variant`() {
    assertEquals(WidgetVariant.SMALL, TwoDaySmallWidgetReceiver().variantHint)
}
```

(TwoDayData 构造字段以 `TwoDayWidget.kt` / `WidgetContent.kt` 真实定义为准,先 Read 再写 — plan 里字段名是推导,执行者必须核对。)

- [ ] **Step 2: 跑测试确认失败**(同 Task 1 模式)

- [ ] **Step 3: 实现** — `renderTwoDay` 与 Task 1 完全同构:原函数体改名 `renderTwoDayRegular` 一行不动;入口 `if (variant == SMALL && wDp < 150f) return renderTwoDayCompact(...)`;compact 画法 = 日期小字 + 今日首课名大字(复用 `ellipsize`)。`TwoDayWidgetReceiver` 改 `open class` + `open val variantHint`,`push()` 里 `renderBitmap` 闭包传 variant。壳类/xml/strings/manifest 全部照 Task 2 模式,label 用 `widget_twoday_small_label`("最近两天 · 小" / "Next Two Days · Small"),xml targetCell 2×2。

- [ ] **Step 4: 跑测试确认通过 + 全量回归**

Run: `cd /Users/lingion_k/Desktop/sleepy && ./gradlew testDebugUnitTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
cd /Users/lingion_k/Desktop/sleepy && git add -A && git commit -m "feat(widget): 最近两天·小 变体"
```

---

### Task 4: WeekList 小变体

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/widget/WidgetBitmapRenderers.kt:303`(renderWeekList)
- Modify: `app/src/main/java/com/lingion/sleepy/widget/WeekListWidget.kt`(open + variantHint)
- Create: `app/src/main/java/com/lingion/sleepy/widget/WeekListSmallWidgetReceiver.kt`
- Create: `app/src/main/res/xml/week_list_small_widget_info.xml`
- Modify: manifest、strings(中英)
- Test: `WidgetVariantRenderTest.kt`

**Interfaces:**
- Consumes: `WidgetVariant`、`WeekListWidgetReceiver.loadDataSync`(WeekData)
- Produces: `renderWeekList(context, data, wDp, hDp, variant = REGULAR)`;`weekListCompactTexts(context, data): List<String>`(今天+明天各一条"周X 课名",无课天跳过);`WeekListSmallWidgetReceiver`

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun `weekList compact texts show today and tomorrow`() {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    val wd = WeekData(
        days = listOf(
            testDay(dow = 3, courses = listOf(testCourse(name = "高等数学", startNode = 1))),   // 周三
            testDay(dow = 4, courses = listOf(testCourse(name = "大学英语", startNode = 1)))    // 周四
        ),
        timeJson = TimeTableUtils.DEFAULT_TIME_JSON, hasTable = true, isDark = false, themeKey = "default"
    )
    val texts = WidgetBitmapRenderers.weekListCompactTexts(ctx, LocalDate.of(2026, 9, 2), wd)
    assertEquals(2, texts.size)
    assertTrue(texts[0].contains("高等数学"))
    assertTrue(texts[1].contains("大学英语"))
}

@Test
fun `weekList small receiver declares SMALL variant`() {
    assertEquals(WidgetVariant.SMALL, WeekListSmallWidgetReceiver().variantHint)
}
```

(WeekData/DayData 真实结构先 Read `WidgetContent.kt` 或渲染器数据类定义再核;测试辅助 `testDay` 按真实字段写。`weekListCompactTexts` 签名带 `today: LocalDate` 是因为"今天+明天"相对周几计算需要锚点。)

- [ ] **Step 2: 跑测试确认失败**

- [ ] **Step 3: 实现** — 同构模式:原 `renderWeekList` 函数体改名 `renderWeekListRegular` 不动;SMALL 且 wDp<150 走 `renderWeekListCompact`:标题小字"周X"+每行"周X 课名"(取今天+明天,各天取首课,空天跳过,最多 2 行,`ellipsize` 兜底)。`WeekListWidgetReceiver` open + variantHint;壳类/xml(targetCell 3×2,minWidth 150dp/minHeight 110dp)/strings(`widget_week_list_small_label`"本周课表(列表) · 小"/"This Week (List) · Small")/manifest 同前。

- [ ] **Step 4: 跑测试 + 全量回归** — `./gradlew testDebugUnitTest` 全绿

- [ ] **Step 5: Commit**

```bash
cd /Users/lingion_k/Desktop/sleepy && git add -A && git commit -m "feat(widget): 本周列表·小 变体"
```

---

### Task 5: WeekView 小变体

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/widget/WidgetBitmapRenderers.kt:435`(renderWeekView)
- Modify: `app/src/main/java/com/lingion/sleepy/widget/WeekViewWidget.kt`(open + variantHint)
- Create: `app/src/main/java/com/lingion/sleepy/widget/WeekViewSmallWidgetReceiver.kt`
- Create: `app/src/main/res/xml/week_view_small_widget_info.xml`
- Modify: manifest、strings(中英)
- Test: `WidgetVariantRenderTest.kt`

**Interfaces:**
- Consumes: `WidgetVariant`、`WeekViewWidgetReceiver.loadDataSync`
- Produces: `renderWeekView(context, data, wDp, hDp, variant = REGULAR)`;`weekViewCompactColumns(data, todayDow): List<Int>`(小档显示的星期列集合);`WeekViewSmallWidgetReceiver`

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun `weekView compact columns center on today`() {
    val wd = weekDataAllDays()   // 7 天都有课的 fixture
    // 周三(3)为中心 → 只保留 3±1 列 = 周二三四
    val cols = WidgetBitmapRenderers.weekViewCompactColumns(wd, todayDow = 3, maxColumns = 3)
    assertEquals(listOf(2, 3, 4), cols)
}

@Test
fun `weekView compact columns skip empty days`() {
    val wd = weekDataOnlyThursday()   // 只有周四有课
    val cols = WidgetBitmapRenderers.weekViewCompactColumns(wd, todayDow = 3, maxColumns = 3)
    assertEquals(listOf(4), cols)     // 只剩有课的周四
}

@Test
fun `weekView small receiver declares SMALL variant`() {
    assertEquals(WidgetVariant.SMALL, WeekViewSmallWidgetReceiver().variantHint)
}
```

- [ ] **Step 2: 跑测试确认失败**

- [ ] **Step 3: 实现**

```kotlin
/** 小档列选择: 优先有课的列, 今天必保, 按"与今天距离"取最近 maxColumns 列 */
fun weekViewCompactColumns(data: WeekData, todayDow: Int, maxColumns: Int = 3): List<Int> {
    val nonEmpty = data.days.filter { it.courses.isNotEmpty() }.map { it.dayOfWeek }
    val pool = if (nonEmpty.isEmpty()) data.days.map { it.dayOfWeek } else nonEmpty
    return pool.sortedBy { kotlin.math.abs(it - todayDow) }.take(maxColumns).sorted()
}
```

`renderWeekView` 同构:原函数体改名 Regular 不动;SMALL 且 wDp<150 走 Compact — 复用 Regular 的列绘制循环,仅 `shownDays` 换成 `weekViewCompactColumns(...)` 结果过滤,字号不变(列少了每列自然变宽)。`WeekViewWidgetReceiver` open + variantHint;壳类/xml(3×2)/strings(`widget_week_view_small_label`"本周课表(周视图) · 小"/"This Week (View) · Small")/manifest 同前。

- [ ] **Step 4: 跑测试 + 全量回归** — 全绿

- [ ] **Step 5: Commit**

```bash
cd /Users/lingion_k/Desktop/sleepy && git add -A && git commit -m "feat(widget): 周视图·小 变体"
```

---

### Task 6: WeekGrid 小变体

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/widget/WeekGridWidgetProvider.kt`(renderBitmap 分派 + open/variantHint — 该类是 provider 本体,直接加 `open class` + `open val variantHint`)
- Modify: `app/src/main/java/com/lingion/sleepy/widget/WeekGridWidgetProvider.kt`(渲染入口加 variant 参数)
- Create: `app/src/main/java/com/lingion/sleepy/widget/WeekGridSmallWidgetProvider.kt`
- Create: `app/src/main/res/xml/week_grid_small_widget_info.xml`
- Modify: manifest、strings(中英)
- Test: `WidgetVariantRenderTest.kt`

**Interfaces:**
- Consumes: `WidgetVariant`、WeekGrid 现有 `renderBitmap`(注意:WeekGrid 不走 WidgetBitmapRenderers,渲染函数在 WeekGridWidgetProvider 内 — 本任务把 variant 参数加在该函数上)
- Produces: `WeekGridSmallWidgetProvider`;WeekGrid 渲染 SMALL 分支 = 单日列(只显示今天列,字号放大)

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun `weekGrid small provider declares SMALL variant`() {
    assertEquals(WidgetVariant.SMALL, WeekGridSmallWidgetProvider().variantHint)
}

@Test
fun `weekGrid compact single-day selection follows today`() {
    // 单测覆盖"列选择"纯函数(若 renderBitmap 是私有巨函数, 先抽 weekGridCompactColumns 与 WeekView 同构)
    val cols = WidgetBitmapRenderers.weekGridCompactDays(allDays = (1..7).toList(), todayDow = 3, maxDays = 1)
    assertEquals(listOf(3), cols)
}
```

- [ ] **Step 2: 跑测试确认失败**

- [ ] **Step 3: 实现** — `weekGridCompactDays(allDays, todayDow, maxDays=1)`:有课优先、今天必保、按距离取 1 天。WeekGrid 渲染入口加 `variant` 参数;SMALL 时 `shownDays` 收缩为 `weekGridCompactDays(...)`(单列,网格自动放大)。`WeekGridWidgetProvider` 声明 `open class` + `open val variantHint`(provider 也可以被继承,构造默认无参);壳类 `WeekGridSmallWidgetProvider : WeekGridWidgetProvider()`;xml 2×2;strings `widget_week_grid_small_label`("本周课表(网格) · 小"/"This Week (Grid) · Small");manifest 条目同构。

- [ ] **Step 4: 跑测试 + 全量回归** — 全绿

- [ ] **Step 5: Commit**

```bash
cd /Users/lingion_k/Desktop/sleepy && git add -A && git commit -m "feat(widget): 本周网格·小 变体"
```

---

### Task 7: 预览图生成脚本 + xml 接线

**Files:**
- Create: `tools/gen_widget_previews.py`(或 .kts — 选 Python,直接命令行调 gradle 产物跑太绕,改为**用单测生成**:见 Step 1)
- Create: `app/src/test/java/com/lingion/sleepy/widget/WidgetPreviewGenTest.kt`(生成器:robolectric 环境调渲染器写 PNG 到 `app/src/main/res/drawable-nodpi/`)
- Modify: 5 个新 xml(+`android:previewImage="@drawable/widget_preview_*_small"`)与 5 个旧 xml(+previewImage 大档)
- Test: 生成器自身即测试(跑一次 + 文件存在断言)

**Interfaces:**
- Consumes: 全部渲染函数(SMALL+REGULAR)、固定 fixture(与 WidgetVariantRenderTest 同源数据,复制一份常量)
- Produces: `app/src/main/res/drawable-nodpi/widget_preview_{today,twoday,weeklist,weekview,weekgrid}{,_small}.png` ×10

- [ ] **Step 1: 写生成器测试/脚本**

```kotlin
@RunWith(RobolectricTestRunner::class)
class WidgetPreviewGenTest {
    @Test
    fun `generate all 10 preview pngs`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val outDir = java.io.File("src/main/res/drawable-nodpi")
        outDir.mkdirs()
        val data = PreviewFixtures.today()      // 与 WidgetVariantRenderTest 同源 fixture, 提到共享文件
        val week = PreviewFixtures.week()
        val twoDay = PreviewFixtures.twoDay()
        val density = ctx.resources.displayMetrics.density

        fun dump(name: String, bmp: android.graphics.Bitmap) {
            java.io.File(outDir, "$name.png").outputStream().use {
                android.graphics.Bitmap.CompressFormat.PNG.let { f ->
                    bmp.compress(f, 100, it)
                }
            }
            bmp.recycle()
        }
        // 大档用各自 target 格数×70dp 估算尺寸; 小档统一 100dp 方图
        dump("widget_preview_today", WidgetBitmapRenderers.renderToday(ctx, data, 280f, 210f))
        dump("widget_preview_today_small", WidgetBitmapRenderers.renderToday(ctx, data, 100f, 100f, WidgetVariant.SMALL))
        dump("widget_preview_twoday", WidgetBitmapRenderers.renderTwoDay(ctx, twoDay, 350f, 210f))
        dump("widget_preview_twoday_small", WidgetBitmapRenderers.renderTwoDay(ctx, twoDay, 100f, 100f, WidgetVariant.SMALL))
        dump("widget_preview_weeklist", WidgetBitmapRenderers.renderWeekList(ctx, week, 350f, 280f))
        dump("widget_preview_weeklist_small", WidgetBitmapRenderers.renderWeekList(ctx, week, 150f, 100f, WidgetVariant.SMALL))
        dump("widget_preview_weekview", WidgetBitmapRenderers.renderWeekView(ctx, week, 350f, 280f))
        dump("widget_preview_weekview_small", WidgetBitmapRenderers.renderWeekView(ctx, week, 150f, 100f, WidgetVariant.SMALL))
        dump("widget_preview_weekgrid", WidgetBitmapRenderers.renderWeekGridPreview(ctx, week, 280f, 350f))
        dump("widget_preview_weekgrid_small", WidgetBitmapRenderers.renderWeekGridPreview(ctx, week, 100f, 100f, WidgetVariant.SMALL))
        // 断言: 10 个文件都生成且 >1KB(空白图会远小于此)
        for (n in listOf("today","today_small","twoday","twoday_small","weeklist","weeklist_small","weekview","weekview_small","weekgrid","weekgrid_small")) {
            val f = java.io.File(outDir, "widget_preview_$n.png")
            org.junit.Assert.assertTrue("$n exists", f.exists())
            org.junit.Assert.assertTrue("$n >1KB", f.length() > 1024)
        }
    }
}
```

`PreviewFixtures.kt`(共享 fixture,Task 1 的 testCourse 迁入):

```kotlin
package com.lingion.sleepy.widget

/** 预览图/变体测试共用固定样例数据 — 稳定输出, 不依赖真机日期 */
object PreviewFixtures { /* today()/week()/twoDay() 返回 Task1-4 测试里的同构数据, 日期固定 2026-09-01 周二 */ }
```

注:`renderWeekGridPreview` 若 WeekGrid 渲染函数是 private,本任务在 `WeekGridWidgetProvider` 里加一个 `fun renderWeekGridPreview(context, data, wDp, hDp, variant = REGULAR)` 公开薄封装(内部复用现有渲染管线+固定 theme)。

- [ ] **Step 2: 跑生成器,确认 10 个 PNG 落盘**

Run: `cd /Users/lingion_k/Desktop/sleepy && ./gradlew testDebugUnitTest --tests "com.lingion.sleepy.widget.WidgetPreviewGenTest"`
Expected: PASS;`ls app/src/main/res/drawable-nodpi/widget_preview_*.png` 有 10 个

- [ ] **Step 3: xml 接线 + 提交生成产物**

10 个 xml 各加一行 `android:previewImage="@drawable/widget_preview_xxx"`(小档加到小 xml,大档补到现有 xml — 现有 xml 目前没有 previewImage,选择列表预览是空白壳,顺手补齐)。

- [ ] **Step 4: assembleDebug 验证资源解析**

Run: `cd /Users/lingion_k/Desktop/sleepy && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL(资源引用无悬空)

- [ ] **Step 5: Commit**

```bash
cd /Users/lingion_k/Desktop/sleepy && git add -A && git commit -m "feat(widget): 10 张选择列表预览图 + xml previewImage 接线"
```

---

### Task 8: 真机验证(Mate 30 5G, 华为桌面)

**Files:** 无代码改动;产出截图与结论。

**Interfaces:**
- Consumes: Task 7 的 assembleDebug 产物(adb install 到真机,不卸载不清数据)
- Produces: 真机截图(10 变体列表 + 2 个小档上桌效果)+ 结论

- [ ] **Step 1: 安装到真机**

```bash
cd /Users/lingion_k/Desktop/sleepy && adb -s XPL0220804046994 install -r app/build/outputs/apk/debug/app-debug.apk
```

(`-r` 覆盖安装保数据;debug 包包名 `com.lingion.sleepy.debug` 与真机 release 包名不同 → **确认装的是 release 包或与设备现存包同 applicationId 的包**,若冲突先问用户,禁擅自卸载 — [[sleepy-package-id-migration]]。)

- [ ] **Step 2: adb 打开 widget 选择页并核对 10 项**

```bash
adb -s XPL0220804046994 shell uiautomator dump /sdcard/w.xml && adb -s XPL0220804046994 pull /sdcard/w.xml /tmp/w.xml
```

长按桌面(手动步骤若 adb 拉不起来,请用户配合一次)→ 添加小组件 → 确认列表出现:今日课程 / 今日课程 · 小 / 最近两天 / 最近两天 · 小 / 本周课表(列表) / (列表) · 小 / 周视图 / 周视图 · 小 / 网格 / 网格 · 小,共 10 项,预览图非空白。

- [ ] **Step 3: 添加「今日课程 · 小」到桌面,截图验证**

```bash
adb -s XPL0220804046994 exec-out screencap -p > /tmp/sl-wv-today-small.png
```

检查:2×2 尺寸下无截断、日期+首课名排版正确、圆角背景、主题跟随。

- [ ] **Step 4: 老用户回归确认**

设备上已有的大档 widget(如有)更新后:内容与更新前一致、无重排、无丢失(大档渲染路径逐字节不变的最终证据)。

- [ ] **Step 5: 结论与收尾**

截图 + 结论汇报;commit 若有收尾修正:

```bash
cd /Users/lingion_k/Desktop/sleepy && git add -A && git commit -m "fix(widget): 真机验证反馈修正"
```

---

## Self-Review 记录

- Spec 覆盖:10 变体(Task 2-6 五组)+ 升档闸(Task 1 Step 3 `wDp<150f` 条件)+ 预览图(Task 7)+ 老用户零影响(Task 1 大档不动 / Task 8 Step 4 真机核)— 全覆盖。
- 占位符:测试代码中的 CourseEntity/TwoDayData/WeekData 构造参数已标注"以真实定义为准,执行者先 Read 再写",这不是占位符而是防推导错误的执行指令;除此之外无 TBD/TODO。
- 类型一致性:`variantHint: WidgetVariant`(Task 2 定义,Task 3-6 复用同一签名);`*CompactTexts(ctx, data)` 命名族一致(weekListCompactTexts 带 today 锚点参数,已在接口块注明);`ellipsize(p, text, maxW)` Task 1 定义后各任务复用。
