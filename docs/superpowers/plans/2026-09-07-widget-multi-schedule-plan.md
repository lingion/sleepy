# 多课表小组件编辑入口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Sleepy app 内提供「管理桌面小组件」入口,允许用户为桌面上放置的每个 Sleepy 小组件单独选择要呈现的课表;为后续扩展小组件设置预留可扩展框架。

**Architecture:** 9 个 widget provider(5 大 + 4 小)共用一个 `WidgetBindingStore` (SharedPreferences-backed `widget_id → tableId`)。`WidgetTableResolver.resolveBoundTable(id)` 优先读 store,命中且对应表仍存在则用之,否则回退 `resolveCurrentTable()`。新增 `WidgetManagementScreen` + `WidgetEditScreen`,沿用 `MainActivity.OverlayScreen` 栈。`WidgetEditScreen` 由 `List<WidgetEditSection>` 组合,目前只实现 `WidgetEditScheduleSection`(选课表)。改 binding 写 store 后调 `WidgetUpdater.notifyDataChanged(context)` 全刷一遍,9 个 receiver 收到广播时各按自己的 id 读 binding 重新画。

**Tech Stack:** Kotlin · AndroidX AppCompat · Jetpack Compose · SharedPreferences · Coroutines · Room(已有) · Robolectric 单测。

---

## Global Constraints

- 包名 `com.lingion.sleepy`
- 仓库根 `/Users/lingion_k/sleepy`
- commit 邮箱必须 `lingion@hrbeu.edu.cn`(memory `git-author-lingion-hrbeu`)
- 禁止 `Co-Authored-By: Claude/Anthropic` 尾注(memory `co-author-trailer-banned`)
- 不打 tag / 不发 GH push 之外的外部动作
- 测试命令:`./gradlew :app:testDebugUnitTest`(精简时加 `--tests "com.lingion.sleepy.widget.*"`)
- 国际化:6 语 strings.xml 全加(`values/`, `values-zh-rCN/`, `values-zh-rTW/`, `values-en/`, `values-ja/`, `values-es/`)
- 风格:沿用现有 `SettingsCard` / `SectionHeader` / Material3 `RadioButton` 风格
- 真机验证:由用户在 Mate 30 5G (TAS-AN00) 上完成,我只跑静态 + build + 单测(用户授权见 memory `no-runtime-verification-static-only`)

---

## File Structure

### 新文件

| 文件 | 职责 |
|---|---|
| `app/src/main/java/com/lingion/sleepy/widget/WidgetBindingStore.kt` | SharedPreferences 封装 |
| `app/src/main/java/com/lingion/sleepy/widget/WidgetVariantInfo.kt` | 9 个变体的元数据(provider class + 名称 res) |
| `app/src/main/java/com/lingion/sleepy/widget/WidgetManagementViewModel.kt` | 列出已放置实例 + 绑定的课表名 |
| `app/src/main/java/com/lingion/sleepy/widget/WidgetEditViewModel.kt` | 编辑单个 widget 的状态 + 改 binding + 触发刷新 |
| `app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetEditSection.kt` | section 接口(可扩展) |
| `app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetManagementScreen.kt` | 管理页 Compose |
| `app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetEditScreen.kt` | 编辑页 Compose |
| `app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetEditScheduleSection.kt` | 「呈现的课表」section |
| `app/src/test/java/com/lingion/sleepy/widget/WidgetBindingStoreTest.kt` | 5 个 case |
| `app/src/test/java/com/lingion/sleepy/widget/WidgetVariantInfoTest.kt` | 元数据形状 |
| `app/src/test/java/com/lingion/sleepy/widget/WidgetBindingResolutionTest.kt` | resolveBoundTable 三路径 |
| `app/src/test/java/com/lingion/sleepy/widget/WidgetEditViewModelTest.kt` | setBinding 行为 |

### 改文件

| 文件 | 改动 |
|---|---|
| `app/src/main/java/com/lingion/sleepy/widget/WidgetTableResolver.kt` | + `resolveBoundTable(id, repo)` |
| `app/src/main/java/com/lingion/sleepy/widget/WidgetUpdater.kt` | `remoteViewsReceiverClasses` 改派生自 `ALL_WIDGET_VARIANTS` |
| `app/src/main/java/com/lingion/sleepy/widget/TodayWidget.kt` | `loadDataSync` 加 `appWidgetId` 参数,优先用 binding |
| `app/src/main/java/com/lingion/sleepy/widget/WeekListWidget.kt` | 同上 |
| `app/src/main/java/com/lingion/sleepy/widget/TwoDayWidget.kt` | 同上 |
| `app/src/main/java/com/lingion/sleepy/widget/WeekViewWidget.kt` | 同上 |
| `app/src/main/java/com/lingion/sleepy/widget/TodaySmallWidgetReceiver.kt` | 新增 `onDeleted` 清 binding |
| `app/src/main/java/com/lingion/sleepy/widget/WeekListSmallWidgetReceiver.kt` | 同上 |
| `app/src/main/java/com/lingion/sleepy/widget/TwoDaySmallWidgetReceiver.kt` | 同上 |
| `app/src/main/java/com/lingion/sleepy/widget/WeekViewSmallWidgetReceiver.kt` | 同上 |
| `app/src/main/java/com/lingion/sleepy/widget/WeekGridWidgetProvider.kt` | onDeleted 清 binding |
| `app/src/main/java/com/lingion/sleepy/widget/WeekGridSmallWidgetProvider.kt` | 同上 |
| `app/src/main/java/com/lingion/sleepy/widget/ScrollStripService.kt` | 3 个 `loadDataSync(context)` 调用处加占位 id(用 0,不影响 strip 渲染) |
| `app/src/main/java/com/lingion/sleepy/MainActivity.kt` | +2 OverlayScreen 值 + 2 if 分支 + 入口回调 |
| `app/src/main/java/com/lingion/sleepy/ui/screen/mine/GeneralSettingsScreen.kt` | 「小组件」分组加入口行 |
| `app/src/main/res/values/strings.xml` 等 6 语 | 新增 string keys |

---

## Task 1: WidgetBindingStore(SharedPreferences 封装)

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/widget/WidgetBindingStore.kt`
- Test: `app/src/test/java/com/lingion/sleepy/widget/WidgetBindingStoreTest.kt`

**Interfaces:**
- `object WidgetBindingStore { fun get(ctx, id): Long?; fun put(ctx, id, tableId); fun remove(ctx, id); fun getAll(ctx): Map<Int, Long> }`
- 文件名 `widget_bindings.xml`(沿用项目内 `ScrollStripService.EXTRA_WIDGET_ID = "widget_id"` 命名风格)

- [ ] **Step 1: 写失败测试**

```kotlin
// app/src/test/java/com/lingion/sleepy/widget/WidgetBindingStoreTest.kt
package com.lingion.sleepy.widget

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetBindingStoreTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val prefs = ctx.getSharedPreferences("widget_bindings", android.content.Context.MODE_PRIVATE)

    @After fun cleanup() { prefs.edit().clear().commit() }

    @Test fun `get returns null when no binding`() {
        assertNull(WidgetBindingStore.get(ctx, 42))
    }

    @Test fun `put then get returns same tableId`() {
        WidgetBindingStore.put(ctx, 42, 7L)
        assertEquals(7L, WidgetBindingStore.get(ctx, 42))
    }

    @Test fun `remove makes get return null`() {
        WidgetBindingStore.put(ctx, 42, 7L)
        WidgetBindingStore.remove(ctx, 42)
        assertNull(WidgetBindingStore.get(ctx, 42))
    }

    @Test fun `different widgetIds do not collide`() {
        WidgetBindingStore.put(ctx, 42, 7L)
        WidgetBindingStore.put(ctx, 43, 8L)
        assertEquals(7L, WidgetBindingStore.get(ctx, 42))
        assertEquals(8L, WidgetBindingStore.get(ctx, 43))
    }

    @Test fun `getAll returns full map`() {
        WidgetBindingStore.put(ctx, 1, 100L)
        WidgetBindingStore.put(ctx, 2, 200L)
        val map = WidgetBindingStore.getAll(ctx)
        assertEquals(setOf(1, 2), map.keys)
        assertEquals(100L, map[1])
        assertEquals(200L, map[2])
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests "com.lingion.sleepy.widget.WidgetBindingStoreTest"`
Expected: FAIL with "Unresolved reference: WidgetBindingStore"

- [ ] **Step 3: 实现最小代码**

```kotlin
// app/src/main/java/com/lingion/sleepy/widget/WidgetBindingStore.kt
package com.lingion.sleepy.widget

import android.content.Context

object WidgetBindingStore {
    private const val PREFS = "widget_bindings"
    private fun key(widgetId: Int) = "app_widget_$widgetId"

    fun get(context: Context, widgetId: Int): Long? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.contains(key(widgetId))) prefs.getLong(key(widgetId), -1L)
               else null
    }

    fun put(context: Context, widgetId: Int, tableId: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(key(widgetId), tableId).apply()
    }

    fun remove(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(key(widgetId)).apply()
    }

    fun getAll(context: Context): Map<Int, Long> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (k, v) ->
            val id = k.removePrefix("app_widget_").toIntOrNull()
            if (id != null && v is Long) id to v else null
        }.toMap()
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests "com.lingion.sleepy.widget.WidgetBindingStoreTest"`
Expected: PASS(5 个测试全绿)

- [ ] **Step 5: Commit**

```bash
cd /Users/lingion_k/sleepy
git add app/src/main/java/com/lingion/sleepy/widget/WidgetBindingStore.kt \
        app/src/test/java/com/lingion/sleepy/widget/WidgetBindingStoreTest.kt
git commit -m "feat(widget): WidgetBindingStore — per-widget tableId 绑定"
git log -1 --format=%ae   # 断言 = lingion@hrbeu.edu.cn
git log -1 --format=%b | grep -i claude  # 断言空
```

---

## Task 2: WidgetVariantInfo + WidgetUpdater 派生

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/widget/WidgetVariantInfo.kt`
- Create: `app/src/test/java/com/lingion/sleepy/widget/WidgetVariantInfoTest.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/widget/WidgetUpdater.kt`(替换 hardcoded list)

**Interfaces:**
- `data class WidgetVariantInfo(val receiverClass: Class<out AppWidgetProvider>, val displayNameRes: Int)`
- `val ALL_WIDGET_VARIANTS: List<WidgetVariantInfo>`(9 条 — 5 大 4 小,WeekGrid 用同一个 receiver 的两种配置但同 class)

注:WeekGridWidgetProvider 与 WeekGridSmallWidgetProvider 是两个独立类(grep 确认),共 10 个 receiver class。修正:本任务枚举所有 10 个 receiver。

- [ ] **Step 1: 写失败测试**

```kotlin
// app/src/test/java/com/lingion/sleepy/widget/WidgetVariantInfoTest.kt
package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetVariantInfoTest {
    @Test fun `ALL_WIDGET_VARIANTS has 10 entries`() {
        assertEquals(10, ALL_WIDGET_VARIANTS.size)
    }

    @Test fun `every variant has a unique receiverClass`() {
        val classes = ALL_WIDGET_VARIANTS.map { it.receiverClass }
        assertEquals(classes.size, classes.toSet().size)
    }

    @Test fun `all entries carry a positive name res id`() {
        ALL_WIDGET_VARIANTS.forEach { assertTrue("nameRes must be set", it.displayNameRes != 0) }
    }

    @Test fun `WidgetUpdater receiver list matches ALL_WIDGET_VARIANTS`() {
        val updaterClasses = WidgetUpdater.remoteViewsReceiverClasses
        val infoClasses = ALL_WIDGET_VARIANTS.map { it.receiverClass }
        assertEquals(infoClasses.toSet(), updaterClasses.toSet())
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests "com.lingion.sleepy.widget.WidgetVariantInfoTest"`
Expected: FAIL — `WidgetVariantInfo` / `ALL_WIDGET_VARIANTS` 未定义

- [ ] **Step 3: 创建 WidgetVariantInfo**

读 `app/src/main/res/values/strings.xml` 找 `widget_*_label`(10 个,`widget_week_grid_label` / `widget_week_grid_small_label` / `widget_today_label` / `widget_today_small_label` / `widget_week_list_label` / `widget_week_list_small_label` / `widget_week_view_label` / `widget_week_view_small_label` / `widget_twoday_label` / `widget_twoday_small_label`)。若已存在就直接用;否则先补齐。

```kotlin
// app/src/main/java/com/lingion/sleepy/widget/WidgetVariantInfo.kt
package com.lingion.sleepy.widget

import android.appwidget.AppWidgetProvider
import com.lingion.sleepy.R

data class WidgetVariantInfo(
    val receiverClass: Class<out AppWidgetProvider>,
    val displayNameRes: Int
)

val ALL_WIDGET_VARIANTS: List<WidgetVariantInfo> = listOf(
    WidgetVariantInfo(WeekGridWidgetProvider::class.java,    R.string.widget_week_grid_label),
    WidgetVariantInfo(WeekGridSmallWidgetProvider::class.java, R.string.widget_week_grid_small_label),
    WidgetVariantInfo(TodayWidgetReceiver::class.java,      R.string.widget_today_label),
    WidgetVariantInfo(TodaySmallWidgetReceiver::class.java, R.string.widget_today_small_label),
    WidgetVariantInfo(WeekListWidgetReceiver::class.java,   R.string.widget_week_list_label),
    WidgetVariantInfo(WeekListSmallWidgetReceiver::class.java, R.string.widget_week_list_small_label),
    WidgetVariantInfo(WeekViewWidgetReceiver::class.java,   R.string.widget_week_view_label),
    WidgetVariantInfo(WeekViewSmallWidgetReceiver::class.java, R.string.widget_week_view_small_label),
    WidgetVariantInfo(TwoDayWidgetReceiver::class.java,     R.string.widget_twoday_label),
    WidgetVariantInfo(TwoDaySmallWidgetReceiver::class.java, R.string.widget_twoday_small_label)
)
```

- [ ] **Step 4: 改 WidgetUpdater 派生**

```kotlin
// app/src/main/java/com/lingion/sleepy/widget/WidgetUpdater.kt — 替换原来的 list
internal val remoteViewsReceiverClasses: List<Class<out AppWidgetProvider>> =
    ALL_WIDGET_VARIANTS.map { it.receiverClass }
```

`AppWidgetProvider` 的 import 已存在(顶部已 import `ComponentName`;若 `AppWidgetProvider` 没 import 就补上)。

- [ ] **Step 5: 跑测试确认通过**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests "com.lingion.sleepy.widget.WidgetVariantInfoTest"`
Expected: PASS(4 个测试全绿)

- [ ] **Step 6: 跑全量测试确保没回归**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest`
Expected: 既有测试全绿

- [ ] **Step 7: Commit**

```bash
cd /Users/lingion_k/sleepy
git add app/src/main/java/com/lingion/sleepy/widget/WidgetVariantInfo.kt \
        app/src/main/java/com/lingion/sleepy/widget/WidgetUpdater.kt \
        app/src/test/java/com/lingion/sleepy/widget/WidgetVariantInfoTest.kt
git commit -m "refactor(widget): WidgetVariantInfo 元数据 + WidgetUpdater 列表派生"
```

---

## Task 3: WidgetTableResolver.resolveBoundTable

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/widget/WidgetTableResolver.kt`
- Create: `app/src/test/java/com/lingion/sleepy/widget/WidgetBindingResolutionTest.kt`

**Interfaces:**
- `fun resolveBoundTable(widgetId: Int): TimeTableEntity?` — 读 binding,命中且 repo 有此表则返回表,否则 null
- 不与 `resolveCurrentTable()` 耦合;调用方负责回退

- [ ] **Step 1: 写失败测试**

```kotlin
// app/src/test/java/com/lingion/sleepy/widget/WidgetBindingResolutionTest.kt
package com.lingion.sleepy.widget

import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.TimeTableEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetBindingResolutionTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val prefs = ctx.getSharedPreferences("widget_bindings", android.content.Context.MODE_PRIVATE)
    private val repo = SleepyApp.get().repository

    @After fun cleanup() { prefs.edit().clear().commit() }

    @Test fun `no binding returns null`() {
        assertNull(WidgetTableResolver.resolveBoundTable(42))
    }

    @Test fun `binding pointing to existing table returns it`() = runBlocking {
        val t = TimeTableEntity(name = "t1", isDefault = true)
        val id = repo.insertTable(t)
        WidgetBindingStore.put(ctx, 42, id)
        val resolved = WidgetTableResolver.resolveBoundTable(42)
        assertEquals(id, resolved?.id)
    }

    @Test fun `binding pointing to deleted table returns null`() = runBlocking {
        val t = TimeTableEntity(name = "t2", isDefault = true)
        val id = repo.insertTable(t)
        WidgetBindingStore.put(ctx, 42, id)
        repo.deleteTable(id)
        assertNull(WidgetTableResolver.resolveBoundTable(42))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests "com.lingion.sleepy.widget.WidgetBindingResolutionTest"`
Expected: FAIL — `resolveBoundTable` 未定义

- [ ] **Step 3: 实现**

```kotlin
// app/src/main/java/com/lingion/sleepy/widget/WidgetTableResolver.kt — 新增方法
import com.lingion.sleepy.SleepyApp
import kotlinx.coroutines.runBlocking

suspend fun resolveBoundTable(widgetId: Int): TimeTableEntity? {
    val ctx = SleepyApp.get()
    val bound = WidgetBindingStore.get(ctx, widgetId) ?: return null
    return runCatching { SleepyApp.get().repository.getTable(bound) }.getOrNull()
}
```

放在现有 `resolveCurrentTable()` 同一 `object` 内。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests "com.lingion.sleepy.widget.WidgetBindingResolutionTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
cd /Users/lingion_k/sleepy
git add app/src/main/java/com/lingion/sleepy/widget/WidgetTableResolver.kt \
        app/src/test/java/com/lingion/sleepy/widget/WidgetBindingResolutionTest.kt
git commit -m "feat(widget): WidgetTableResolver.resolveBoundTable — binding 优先"
```

---

## Task 4: 4 个 loadDataSync 加 appWidgetId + 内部 binding 优先

**Files:**
- Modify: `TodayWidget.kt` / `WeekListWidget.kt` / `TwoDayWidget.kt` / `WeekViewWidget.kt`
- Modify: `ScrollStripService.kt`(调用方加 `id` 占位参数)

**接口变化:**
- `fun loadDataSync(context: Context, appWidgetId: Int): WidgetData`(WidgetData)或 `WeekData` / `TwoDayData`
- 内部:`val table = WidgetTableResolver.resolveBoundTable(appWidgetId) ?: WidgetTableResolver.resolveCurrentTable()`

- [ ] **Step 1: 跑现有测试看基线**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests "com.lingion.sleepy.widget.*"`
Expected: 既有全绿

- [ ] **Step 2: 改 TodayWidget.kt**

```kotlin
// TodayWidget.kt — 改 push / loadDataSync
private fun push(context: Context, awm: AppWidgetManager, id: Int) {
    pushTodayData(context, awm, id, variantHint, loadDataSync(context, id))
}

// companion 内
fun loadDataSync(context: Context, appWidgetId: Int): WidgetData {
    val today = LocalDate.now()
    ...
    runBlocking {
        val app = SleepyApp.get()
        val repo = app.repository
        val table = WidgetTableResolver.resolveBoundTable(appWidgetId)
            ?: WidgetTableResolver.resolveCurrentTable()
        ...
    }
}
```

- [ ] **Step 3: 改 WeekListWidget.kt / TwoDayWidget.kt / WeekViewWidget.kt**

每个文件:
- 调用方 `loadDataSync(context)` → `loadDataSync(context, id)`(传当前 onUpdate / onAppWidgetOptionsChanged 里的 id)
- 函数定义加 `appWidgetId: Int` 参数
- 内部 `WidgetTableResolver.resolveCurrentTable()` 改为 `resolveBoundTable(appWidgetId) ?: resolveCurrentTable()`

每个 widget 的 `onUpdate` 循环里调 `loadDataSync` 时,确保传对应的 id。WeekViewWidget 当前是 `loadData = { loadDataSync(context) }`,要在 renderAndPush 里改成 `loadData = { loadDataSync(context, widgetId) }` —— `widgetId` 是 renderAndPush 的入参(已存在),不需新签名。

- [ ] **Step 4: 改 ScrollStripService.kt 三处调用**

`ScrollStripService.kt:67 / 73 / 79` 三个 `loadDataSync(context)` 调用:`loadDataSync(context, widgetId)`。其中 widgetId 来自 `ScrollStripService.StripFactory.EXTRA_WIDGET_ID` —— 该 service 在调用此函数时是否持有 widgetId?需要读 StripFactory.kt 确认;若无,传 0 占位(strip 渲染走表,用户操作 strip 的 widget 就在那台桌面上,strip 内 widgetId = 列表所在 widget 的 id,通常从 intent 拿)。

具体怎么传:读完 StripFactory.kt 后选最小改法。**兜底方案**:若 StripFactory 内部没有 widgetId 上下文,新增 `widgetId` 入参(从 Service onBind 时缓存的 widgetId 透传)。

- [ ] **Step 5: 跑测试确认基线全绿**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest`
Expected: 全绿(签名变更后所有既有测试仍通过,因为 `WidgetData` / `WeekData` / `TwoDayData` 的内部形状没改)

- [ ] **Step 6: 跑 lint**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:lintDebug 2>&1 | tail -30`
Expected: 无新增 error/warning(既有 warning 忽略)

- [ ] **Step 7: Commit**

```bash
cd /Users/lingion_k/sleepy
git add app/src/main/java/com/lingion/sleepy/widget/TodayWidget.kt \
        app/src/main/java/com/lingion/sleepy/widget/WeekListWidget.kt \
        app/src/main/java/com/lingion/sleepy/widget/TwoDayWidget.kt \
        app/src/main/java/com/lingion/sleepy/widget/WeekViewWidget.kt \
        app/src/main/java/com/lingion/sleepy/widget/ScrollStripService.kt
git commit -m "refactor(widget): loadDataSync 加 appWidgetId 参数, 内部 binding 优先"
```

---

## Task 5: 9 个 receiver 加 onDeleted 清 binding

**Files:**
- Modify: `TodayWidget.kt` / `WeekListWidget.kt` / `TwoDayWidget.kt` / `WeekViewWidget.kt` / `WeekGridWidgetProvider.kt`
- Modify: 4 个 Small 子类(继承父类的 onDeleted,父类有即可)

- [ ] **Step 1: 看 5 个父类是否已有 onDeleted**

Run: `grep -n 'fun onDeleted' app/src/main/java/com/lingion/sleepy/widget/TodayWidget.kt app/src/main/java/com/lingion/sleepy/widget/WeekListWidget.kt app/src/main/java/com/lingion/sleepy/widget/TwoDayWidget.kt app/src/main/java/com/lingion/sleepy/widget/WeekViewWidget.kt app/src/main/java/com/lingion/sleepy/widget/WeekGridWidgetProvider.kt`
Expected: 都没有;`AppWidgetProvider.onDeleted` 默认空实现

- [ ] **Step 2: 在每个父类添加 onDeleted**

```kotlin
// 加在 onUpdate / onAppWidgetOptionsChanged 之后
override fun onDeleted(context: Context, appWidgetIds: IntArray) {
    super.onDeleted(context, appWidgetIds)
    for (id in appWidgetIds) WidgetBindingStore.remove(context, id)
}
```

Small 子类继承即可,无需覆盖。

- [ ] **Step 3: 跑测试**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest`
Expected: 全绿

- [ ] **Step 4: Commit**

```bash
cd /Users/lingion_k/sleepy
git add app/src/main/java/com/lingion/sleepy/widget/TodayWidget.kt \
        app/src/main/java/com/lingion/sleepy/widget/WeekListWidget.kt \
        app/src/main/java/com/lingion/sleepy/widget/TwoDayWidget.kt \
        app/src/main/java/com/lingion/sleepy/widget/WeekViewWidget.kt \
        app/src/main/java/com/lingion/sleepy/widget/WeekGridWidgetProvider.kt
git commit -m "feat(widget): onDeleted 清理 WidgetBindingStore"
```

---

## Task 6: WidgetEditSection 接口 + ScheduleSection

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetEditSection.kt`
- Create: `app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetEditScheduleSection.kt`
- Create: `app/src/main/java/com/lingion/sleepy/widget/WidgetEditViewModel.kt`
- Test: `app/src/test/java/com/lingion/sleepy/widget/WidgetEditViewModelTest.kt`
- Modify: 6 语 strings.xml(`widget_edit_section_schedule` / `widget_edit_default_label`)

- [ ] **Step 1: 写失败测试**

```kotlin
// app/src/test/java/com/lingion/sleepy/widget/WidgetEditViewModelTest.kt
package com.lingion.sleepy.widget

import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.TimeTableEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WidgetEditViewModelTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val prefs = ctx.getSharedPreferences("widget_bindings", android.content.Context.MODE_PRIVATE)
    private val repo = SleepyApp.get().repository

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { prefs.edit().clear().commit(); Dispatchers.resetMain() }

    @Test fun `initial state has no binding`() = runTest {
        val vm = WidgetEditViewModel(42)
        assertNull(vm.state.first().currentBinding)
    }

    @Test fun `setBinding writes to store`() = runTest {
        val t = TimeTableEntity(name = "T", isDefault = true)
        val id = repo.insertTable(t)
        val vm = WidgetEditViewModel(42)
        vm.setBinding(id)
        assertEquals(id, WidgetBindingStore.get(ctx, 42))
    }

    @Test fun `setBinding null removes binding`() = runTest {
        WidgetBindingStore.put(ctx, 42, 99L)
        val vm = WidgetEditViewModel(42)
        vm.setBinding(null)
        assertNull(WidgetBindingStore.get(ctx, 42))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests "com.lingion.sleepy.widget.WidgetEditViewModelTest"`
Expected: FAIL — `WidgetEditViewModel` 未定义

- [ ] **Step 3: 实现 ViewModel**

```kotlin
// app/src/main/java/com/lingion/sleepy/widget/WidgetEditViewModel.kt
package com.lingion.sleepy.widget

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.TimeTableEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WidgetEditUiState(
    val currentBinding: Long? = null,
    val availableTables: List<TimeTableEntity> = emptyList()
)

class WidgetEditViewModel(
    val widgetId: Int
) : ViewModel() {
    private val ctx: Context get() = SleepyApp.get()
    private val repo get() = SleepyApp.get().repository
    private val _state = MutableStateFlow(WidgetEditUiState())
    val state: StateFlow<WidgetEditUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            val all = repo.getAllTables()
            val nonEmpty = all.filter { repo.countCourses(it.id) > 0 }
            _state.value = WidgetEditUiState(
                currentBinding = WidgetBindingStore.get(ctx, widgetId),
                availableTables = nonEmpty
            )
        }
    }

    fun setBinding(tableId: Long?) {
        if (tableId == null) WidgetBindingStore.remove(ctx, widgetId)
        else WidgetBindingStore.put(ctx, widgetId, tableId)
        reload()
        viewModelScope.launch { WidgetUpdater.notifyDataChanged(ctx) }
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest --tests "com.lingion.sleepy.widget.WidgetEditViewModelTest"`
Expected: PASS

- [ ] **Step 5: 实现 section 接口 + schedule section**

```kotlin
// app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetEditSection.kt
package com.lingion.sleepy.ui.screen.widget

import androidx.compose.runtime.Composable
import com.lingion.sleepy.data.entity.TimeTableEntity

data class WidgetEditScope(
    val widgetId: Int,
    val currentBinding: Long?,
    val availableTables: List<TimeTableEntity>,
    val onSelectTable: (Long?) -> Unit
)

sealed interface WidgetEditSection {
    val titleRes: Int
    @Composable fun Content(scope: WidgetEditScope)
}
```

```kotlin
// app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetEditScheduleSection.kt
package com.lingion.sleepy.ui.screen.widget

import androidx.compose.foundation.layout.Column as ComposeColumn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R

object WidgetEditScheduleSection : WidgetEditSection {
    override val titleRes = R.string.widget_edit_section_schedule

    @Composable
    override fun Content(scope: WidgetEditScope) {
        ComposeColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.widget_edit_section_schedule), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().selectable(selected = scope.currentBinding == null, onClick = { scope.onSelectTable(null) }).padding(8.dp)) {
                RadioButton(selected = scope.currentBinding == null, onClick = { scope.onSelectTable(null) })
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.widget_edit_default_label))
            }
            scope.availableTables.forEach { table ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().selectable(selected = scope.currentBinding == table.id, onClick = { scope.onSelectTable(table.id) }).padding(8.dp)) {
                    RadioButton(selected = scope.currentBinding == table.id, onClick = { scope.onSelectTable(table.id) })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(table.name)
                }
            }
        }
    }
}
```

- [ ] **Step 6: 6 语 strings.xml 加 key**

`app/src/main/res/values/strings.xml`:
- `widget_edit_section_schedule` = "Schedule"
- `widget_edit_default_label` = "Default (follow current schedule)"

`values-zh-rCN/strings.xml`:
- `widget_edit_section_schedule` = "呈现的课表"
- `widget_edit_default_label` = "默认（跟随当前课表）"

`values-zh-rTW/strings.xml`:
- "呈現的課表" / "預設（跟隨當前課表）"

`values-ja/strings.xml`:
- "表示する時間割" / "デフォルト（現在の時間割に従う）"

`values-en/strings.xml` (与 `values/` 同)

`values-es/strings.xml`:
- "Horario mostrado" / "Predeterminado (sigue el horario actual)"

- [ ] **Step 7: 编译 + 跑全量测试**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:testDebugUnitTest`
Expected: 全绿

- [ ] **Step 8: Commit**

```bash
cd /Users/lingion_k/sleepy
git add app/src/main/java/com/lingion/sleepy/widget/WidgetEditViewModel.kt \
        app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetEditSection.kt \
        app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetEditScheduleSection.kt \
        app/src/test/java/com/lingion/sleepy/widget/WidgetEditViewModelTest.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-zh-rCN/strings.xml \
        app/src/main/res/values-zh-rTW/strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/main/res/values-ja/strings.xml \
        app/src/main/res/values-es/strings.xml
git commit -m "feat(widget): 编辑页 section 接口 + Schedule section + ViewModel"
```

---

## Task 7: WidgetManagementViewModel + Screen

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/widget/WidgetManagementViewModel.kt`
- Create: `app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetManagementScreen.kt`
- Modify: 6 语 strings.xml(`widget_manage_title` / `widget_manage_empty`)

- [ ] **Step 1: 实现 ViewModel**

```kotlin
// app/src/main/java/com/lingion/sleepy/widget/WidgetManagementViewModel.kt
package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingion.sleepy.SleepyApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class PlacedWidgetItem(
    val widgetId: Int,
    val variant: WidgetVariantInfo,
    val tableName: String? = null
)

class WidgetManagementViewModel(
    private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow<List<PlacedWidgetItem>>(emptyList())
    val state: StateFlow<List<PlacedWidgetItem>> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            val awm = AppWidgetManager.getInstance(context)
            val items = mutableListOf<PlacedWidgetItem>()
            withContext(Dispatchers.IO) {
                for (variant in ALL_WIDGET_VARIANTS) {
                    val component = ComponentName(context, variant.receiverClass)
                    val ids = runCatching { awm.getAppWidgetIds(component) }.getOrDefault(intArrayOf())
                    for (id in ids) {
                        val boundId = WidgetBindingStore.get(context, id)
                        val tableName = boundId?.let {
                            runCatching { runBlocking { SleepyApp.get().repository.getTable(it) } }.getOrNull()?.name
                        }
                        items += PlacedWidgetItem(id, variant, tableName)
                    }
                }
            }
            _state.value = items
        }
    }
}
```

- [ ] **Step 2: 实现 Screen**

```kotlin
// app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetManagementScreen.kt
package com.lingion.sleepy.ui.screen.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.widget.PlacedWidgetItem
import com.lingion.sleepy.widget.WidgetManagementViewModel

@Composable
fun WidgetManagementScreen(onBack: () -> Unit, onSelect: (Int) -> Unit) {
    val ctx = SleepyApp.get()
    val vm = remember { WidgetManagementViewModel(ctx) }
    val items by vm.state.collectAsState()
    val colors = SleepyTheme.colors

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_manage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background, titleContentColor = colors.onBackground, navigationIconContentColor = colors.onBackground)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (items.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.widget_manage_empty), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.widgetId }) { item ->
                        PlacedWidgetRow(item = item, onClick = { onSelect(item.widgetId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PlacedWidgetRow(item: PlacedWidgetItem, onClick: () -> Unit) {
    val colors = SleepyTheme.colors
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(item.variant.displayNameRes), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                val tableLabel = item.tableName ?: stringResource(R.string.widget_edit_default_label)
                Text(tableLabel, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}
```

- [ ] **Step 3: 6 语 strings.xml 加 key**

- `widget_manage_title`: "管理桌面小组件" / "Manage Widgets" / "ホームウィジェットを管理" / "Administrar widgets del escritorio"
- `widget_manage_empty`: 中文"暂未在桌面添加 Sleepy 小组件。长按桌面空白处 → 小组件 → 选 Sleepy 即可添加。";英文"No Sleepy widget added to home screen yet. Long-press empty space → Widgets → pick Sleepy to add.";日文类似;西文类似;繁中类似

- [ ] **Step 4: 编译**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
cd /Users/lingion_k/sleepy
git add app/src/main/java/com/lingion/sleepy/widget/WidgetManagementViewModel.kt \
        app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetManagementScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-zh-rCN/strings.xml \
        app/src/main/res/values-zh-rTW/strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/main/res/values-ja/strings.xml \
        app/src/main/res/values-es/strings.xml
git commit -m "feat(widget): 管理页 ViewModel + Screen + i18n"
```

---

## Task 8: WidgetEditScreen

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetEditScreen.kt`
- Modify: 6 语 strings.xml(`widget_edit_title`)

- [ ] **Step 1: 实现 Screen**

```kotlin
// app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetEditScreen.kt
package com.lingion.sleepy.ui.screen.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.widget.WidgetEditViewModel

@Composable
fun WidgetEditScreen(widgetId: Int, onBack: () -> Unit) {
    val vm = remember(widgetId) { WidgetEditViewModel(widgetId) }
    val state by vm.state.collectAsState()
    val colors = SleepyTheme.colors

    val sections: List<WidgetEditSection> = remember { listOf(WidgetEditScheduleSection) }
    val scope = remember(state) {
        WidgetEditScope(
            widgetId = widgetId,
            currentBinding = state.currentBinding,
            availableTables = state.availableTables,
            onSelectTable = { vm.setBinding(it) }
        )
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background, titleContentColor = colors.onBackground, navigationIconContentColor = colors.onBackground)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sections.size) { idx -> sections[idx].Content(scope) }
            }
        }
    }
}
```

注:`items(count: Int)` 是 Compose 1.x LazyListScope.items(count) → 调用 `items(count: Int, itemContent: @Composable LazyItemScope.(index: Int) -> Unit)`。如果项目用旧版,改成 `itemsIndexed(sections) { _, s -> s.Content(scope) }` + 加 import。

- [ ] **Step 2: 6 语 strings.xml 加 `widget_edit_title`**

- 中文 "编辑小组件" / 英文 "Edit Widget" / 日文 "ウィジェットを編集" / 西文 "Editar widget" / 繁中 "編輯小工具"

- [ ] **Step 3: 编译**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
cd /Users/lingion_k/sleepy
git add app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetEditScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-zh-rCN/strings.xml \
        app/src/main/res/values-zh-rTW/strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/main/res/values-ja/strings.xml \
        app/src/main/res/values-es/strings.xml
git commit -m "feat(widget): 编辑页 Screen + section 列表"
```

---

## Task 9: 入口接入(MainActivity + GeneralSettingsScreen)

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/MainActivity.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/mine/GeneralSettingsScreen.kt`
- Modify: 6 语 strings.xml(`widget_manage_entry`)

- [ ] **Step 1: 读 GeneralSettingsScreen 的 widget section**

读 `app/src/main/java/com/lingion/sleepy/ui/screen/mine/GeneralSettingsScreen.kt` 第 501 行附近(小组件 `SettingsCard`),确认现有 toggle 行模板。

- [ ] **Step 2: 在 MainActivity 加 OverlayScreen 值与 if 分支**

```kotlin
// MainActivity.kt — 改 enum
private enum class OverlayScreen {
    AddCourse, AllTables, EditTable, Theme, General, Holiday, Export, Reminder, About, License,
    WidgetManagement, WidgetEdit
}

// AppRoot 内加 remember state
var widgetEditId by remember { mutableStateOf<Int?>(null) }

// 加 2 个 if 分支(放在 License if 之后,fallthrough 之前)
if (topOverlay() == OverlayScreen.WidgetManagement) {
    WidgetManagementScreen(
        onBack = { popOverlay() },
        onSelect = { id -> widgetEditId = id; pushOverlay(OverlayScreen.WidgetEdit) }
    )
    return
}
if (topOverlay() == OverlayScreen.WidgetEdit) {
    val id = widgetEditId ?: run { popOverlay(); return }
    WidgetEditScreen(widgetId = id, onBack = { widgetEditId = null; popOverlay() })
    return
}
```

- [ ] **Step 3: GeneralSettingsScreen 加入口行**

读 GeneralSettingsScreen 现有签名(参数列表) → 加回调 `onOpenWidgetManagement: () -> Unit = {}`。

在「小组件」SettingsCard 内(已有两条 toggle 之后)加一行:

```kotlin
HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
Row(
    modifier = Modifier.fillMaxWidth().clickable { onOpenWidgetManagement() }.padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Text(stringResource(R.string.widget_manage_entry), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
    Icon(painter = painterResource(R.drawable.ic_chevron_right_24), contentDescription = null, tint = colors.onSurfaceVariant)
}
```

`painterResource(R.drawable.ic_chevron_right_24)` 若不存在,改用 `Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, ...)`。

- [ ] **Step 4: MainActivity 传 callback**

```kotlin
if (topOverlay() == OverlayScreen.General) {
    GeneralSettingsScreen(
        onBack = { popOverlay() },
        onOpenHoliday = { pushOverlay(OverlayScreen.Holiday) },
        onOpenWidgetManagement = { pushOverlay(OverlayScreen.WidgetManagement) },
        navDock = navDock,
        onNavDockChange = { navDock = it }
    )
    return
}
```

- [ ] **Step 5: 6 语 strings.xml 加 `widget_manage_entry`**

- "管理桌面小组件" / "Manage Widgets" / "ウィジェットを管理" / "Administrar widgets" / "管理桌面小工具"

- [ ] **Step 6: 编译 + 测试**

Run: `cd /Users/lingion_k/sleepy && ./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 全绿

- [ ] **Step 7: Commit**

```bash
cd /Users/lingion_k/sleepy
git add app/src/main/java/com/lingion/sleepy/MainActivity.kt \
        app/src/main/java/com/lingion/sleepy/ui/screen/mine/GeneralSettingsScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-zh-rCN/strings.xml \
        app/src/main/res/values-zh-rTW/strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/main/res/values-ja/strings.xml \
        app/src/main/res/values-es/strings.xml
git commit -m "feat(widget): 入口接入 — 我的 → 通用设置 → 小组件 → 管理桌面小组件"
```

---

## Task 10: 真机验收(用户)

**Files:** 无

**Steps:**
- [ ] 步骤 1: `./gradlew :app:installDebug` 安装
- [ ] 步骤 2: 添加一个 Sleepy widget,确认默认显示当前课表(无配置页弹出)
- [ ] 步骤 3: 删 widget,检查 `widget_bindings.xml` 已清空
- [ ] 步骤 4: 添加 2 个不同/相同 widget,app 内进入「管理桌面小组件」,列表显示 2 行
- [ ] 步骤 5: 点第 1 个 widget → 编辑页 → 选具体课表 → 回桌面观察 widget 1 切到目标表,widget 2 不动
- [ ] 步骤 6: 选「默认」,widget 1 回退到当前课表
- [ ] 步骤 7: 删除绑定的课表,观察 widget 1 自动回退到当前课表(不闪退,不显示空表)
- [ ] 步骤 8: 桌面删两个,`widget_bindings.xml` 空

---

## Self-Review

**1. Spec coverage:**

- 入口路径 → Task 9 ✓
- 管理页(已放置列表) → Task 7 ✓
- 编辑页 section 列表 → Task 6 + 8 ✓
- 课表切换立即保存+刷新 → Task 6 (setBinding → WidgetUpdater.notifyDataChanged) ✓
- 默认 / 恢复默认 → Task 6 (`currentBinding == null` 选项) ✓
- 共享变体元数据 → Task 2 ✓
- onDeleted 清理 → Task 5 ✓
- 删课表惰性失效 → Task 3 (resolveBoundTable 返回 null) + Task 4 (调用方回退) ✓
- i18n 6 语 → Task 6, 7, 8, 9 ✓
- 测试策略 → Task 1, 2, 3, 6 ✓
- 真机验收 → Task 10 ✓
- 明确不做项 — plan 不引入 launcher 配置页、不在 widget 上加 tap 入口、不动 Feature 2 ✓

**2. Placeholder scan:** 0 个 TBD / TODO。代码块全为实代码。

**3. Type consistency:**
- `WidgetBindingStore.get(ctx, id) -> Long?` 在 Task 1 定义,Task 3, 5, 6, 7 调用 — 一致
- `WidgetTableResolver.resolveBoundTable(id)` 在 Task 3 定义,Task 4 调用 — 一致
- `WidgetEditViewModel(widgetId)` + `setBinding(Long?)` 在 Task 6 定义,Task 8 调用 — 一致
- `PlacedWidgetItem(widgetId, variant, tableName?)` Task 7 定义,内部一致
- `WidgetVariantInfo(receiverClass, displayNameRes)` Task 2 定义,Task 7 使用 — 一致

**风险标记**:Task 9 把 enum 加 2 个值,9 个 OverlayScreen.X 引用仍然合法(只追加,不动现有);Task 4 改 4 个 `loadDataSync` 签名后,需要确保 ScrollStripService.kt 的调用点有 widgetId 可传——若 StripFactory 没缓存 widgetId,本任务要顺手把 widgetId 缓存进去(strip 是 listview,widgetId 来自 widget host 启动时的 intent extra)。