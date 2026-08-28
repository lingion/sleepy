# 节假日范围化覆盖(编辑/添加/删除以"节日"为单位) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用户对节假日的微调从"逐日"升级为"逐段"——列表按连续日期聚合显示为节日段,编辑/添加/删除/恢复默认全部以范围为单位。

**Architecture:** 新数据层 `HolidayRangeOverride`(id/name/startDate/endDate/type/sourceKey)存 SharedPreferences JSON;`HolidayRangeOps` 纯函数对象负责「网络逐日条目 → 网络段聚合」「网络段 + 覆盖段 → 合并段列表」「合并段 → 灰显用 holidays/workdays 集合」。UI 层 `HolidaySettingsScreen` 列表改为段行,弹窗加开始/结束两个 `DatePickerField`。灰显判定 `decideGrey` 本体不动,消费合并后的集合。

**Tech Stack:** Kotlin + Jetpack Compose (Material3) + org.json + JUnit4。复用现有 `DatePickerField`(ui/component/DateTimePickers.kt:47)。

## Global Constraints

- 包名 `com.lingion.sleepy`;UI 全走 `SleepyTheme.colors/shapes/Buttons`,禁硬编码颜色
- 全 app 禁 BorderStroke/OutlinedButton;选中态 = 色块(复用 `HolidayStyleChip`)
- 新 UI 字符串 6 个 locale 全同步:values/, values-zh-rCN/, values-zh-rTW/, values-en/, values-ja/, values-es/
- 纯逻辑(聚合/合并/编解码)全部写成无 Context/无网络的纯函数,JUnit4 单测覆盖;测试放 `app/src/test/java/com/lingion/sleepy/util/`
- 测试命令:`./gradlew :app:testDebugUnitTest --tests "com.lingion.sleepy.util.HolidayRangeTest"`
- 构建:`./gradlew :app:assembleDebug`;装机:`adb -s d3efcd6a install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
- 旧逐日覆盖数据不迁移(功能未发布);`KEY_HOLIDAY_OVERRIDES` 键复用,值格式整体替换
- 旧符号清理:`HolidayManager.OVERRIDE_REMOVED`/`mergeEntries`/`decodeOverrides`/`encodeOverrides`/`decideGreyWithOverrides`、`AppPrefs.getHolidayOverrides/setHolidayOverrides`、`HolidayOverrideTest.kt` — 被 Task 1/2 替换后必须删干净,不留死代码
- commit 小步:每个 Task 一个 commit,消息用现有风格(`feat(holiday): ...`)

---

### Task 1: 数据模型 + 段聚合纯函数

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/util/HolidayRange.kt`
- Test: `app/src/test/java/com/lingion/sleepy/util/HolidayRangeTest.kt`

**Interfaces:**
- Produces:
  - `data class HolidayRange(val id: String, val name: String, val startDate: LocalDate, val endDate: LocalDate, val type: String, val sourceKey: String?)` — type 复用 `HolidayManager.TYPE_PUBLIC_HOLIDAY` / `TYPE_TRANSFER_WORKDAY` / 新增 `HolidayRangeOps.REMOVED = "removed"`;`sourceKey` = `"holiday:<date>"` 或 `"workday:<date>"`(被替换/删除的网络段首日),null = 纯新增
  - `object HolidayRangeOps`:
    - `fun aggregateSegments(entries: List<HolidayEntry>): List<HolidayRange>` — 网络逐日条目按「name+type+日期连续」聚合;返回按 startDate 排序,id 由 `HolidayRangeOps.newId()` 生成
    - `fun newId(): String` — 8 字符随机 hex
    - `fun mergeSegments(network: List<HolidayEntry>, overrides: List<HolidayRange>): MergeResult` — 返回 `data class MergeResult(val active: List<HolidayRange>, val removed: List<HolidayRange>)`;规则:先聚合网络段;overrides 里 `sourceKey` 非空的段把对应网络段整段抹除;然后按 overrides 列表顺序应用(removed 型记入 removed,其余写入/替换同 id 段);active 按 startDate 排序
    - `fun toSets(active: List<HolidayRange>): Pair<Set<LocalDate>, Set<LocalDate>>` — (holidays, workdays),逐段展开日期区间
    - `fun encodeOverrides(overrides: List<HolidayRange>): String` / `fun decodeOverrides(json: String): List<HolidayRange>` — JSON 数组格式 `[{"id":"a1b2c3d4","name":"春节","start":"2025-01-28","end":"2025-02-04","type":"public_holiday","sourceKey":"holiday:2025-01-28"}]`;坏行跳过,解析失败返回空列表

- [ ] **Step 1: Write the failing test**

```kotlin
package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 范围化覆盖纯逻辑: 聚合/合并/集合展开/序列化 (不触 Context/网络) */
class HolidayRangeTest {

    private fun d(m: Int, day: Int) = LocalDate.of(2025, m, day)
    private fun entry(m: Int, day: Int, name: String, type: String) =
        HolidayEntry(d(m, day), name, type)

    // ===== 网络段聚合 =====

    @Test
    fun aggregate_merges_consecutive_same_name_same_type() {
        val segments = HolidayRangeOps.aggregateSegments(
            listOf(
                entry(2, 10, "春节", HolidayManager.TYPE_PUBLIC_HOLIDAY),
                entry(2, 11, "春节", HolidayManager.TYPE_PUBLIC_HOLIDAY),
                entry(2, 12, "春节", HolidayManager.TYPE_PUBLIC_HOLIDAY),
            )
        )
        assertEquals(1, segments.size)
        assertEquals(d(2, 10), segments[0].startDate)
        assertEquals(d(2, 12), segments[0].endDate)
    }

    @Test
    fun aggregate_splits_on_gap_or_name_or_type_change() {
        val segments = HolidayRangeOps.aggregateSegments(
            listOf(
                entry(5, 1, "劳动节", HolidayManager.TYPE_PUBLIC_HOLIDAY),
                entry(5, 2, "劳动节", HolidayManager.TYPE_PUBLIC_HOLIDAY),
                entry(5, 4, "劳动节", HolidayManager.TYPE_PUBLIC_HOLIDAY), // 5/3 断档
                entry(5, 5, "劳动节(青年节)", HolidayManager.TYPE_PUBLIC_HOLIDAY), // 名称变
                entry(4, 27, "班", HolidayManager.TYPE_TRANSFER_WORKDAY), // 类型变+乱序
            )
        )
        assertEquals(4, segments.size)
    }

    @Test
    fun aggregate_empty_and_singleton() {
        assertTrue(HolidayRangeOps.aggregateSegments(emptyList()).isEmpty())
        val one = HolidayRangeOps.aggregateSegments(listOf(entry(1, 1, "元旦", HolidayManager.TYPE_PUBLIC_HOLIDAY)))
        assertEquals(1, one.size)
        assertEquals(d(1, 1), one[0].startDate)
    }

    // ===== 合并 =====

    @Test
    fun merge_adds_new_range() {
        val ov = HolidayRange(newId = "id1", name = "校庆", startDate = d(3, 8), endDate = d(3, 9),
            type = HolidayManager.TYPE_PUBLIC_HOLIDAY, sourceKey = null)
        val result = HolidayRangeOps.mergeSegments(
            listOf(entry(1, 1, "元旦", HolidayManager.TYPE_PUBLIC_HOLIDAY)), listOf(ov))
        assertEquals(2, result.active.size)
        assertTrue(result.removed.isEmpty())
    }

    @Test
    fun merge_replaces_network_segment_via_sourceKey() {
        // 网络: 春节 2/10-2/14; 用户改成 2/10-2/12
        val ov = HolidayRange("id1", "春节", d(2, 10), d(2, 12),
            HolidayManager.TYPE_PUBLIC_HOLIDAY, sourceKey = "holiday:${d(2, 10)}")
        val result = HolidayRangeOps.mergeSegments(
            (10..14).map { entry(2, it, "春节", HolidayManager.TYPE_PUBLIC_HOLIDAY) }, listOf(ov))
        assertEquals(1, result.active.size)
        assertEquals(d(2, 12), result.active[0].endDate)
    }

    @Test
    fun merge_removed_network_segment_goes_to_removed_list() {
        val ov = HolidayRange("id1", "元旦", d(1, 1), d(1, 1),
            HolidayRangeOps.REMOVED, sourceKey = "holiday:${d(1, 1)}")
        val result = HolidayRangeOps.mergeSegments(
            listOf(entry(1, 1, "元旦", HolidayManager.TYPE_PUBLIC_HOLIDAY)), listOf(ov))
        assertTrue(result.active.isEmpty())
        assertEquals(1, result.removed.size)
    }

    @Test
    fun merge_workday_sourceKey_only_kills_workday_segment() {
        val ov = HolidayRange("id1", "班", d(1, 26), d(1, 26),
            HolidayRangeOps.REMOVED, sourceKey = "workday:${d(1, 26)}")
        val result = HolidayRangeOps.mergeSegments(
            listOf(entry(1, 26, "班", HolidayManager.TYPE_TRANSFER_WORKDAY)), listOf(ov))
        assertTrue(result.active.isEmpty())
    }

    @Test
    fun merge_same_sourceKey_twice_second_wins() {
        val a = HolidayRange("id1", "春节", d(2, 10), d(2, 12),
            HolidayManager.TYPE_PUBLIC_HOLIDAY, sourceKey = "holiday:${d(2, 10)}")
        val b = HolidayRange("id2", "寒假", d(2, 10), d(2, 14),
            HolidayManager.TYPE_PUBLIC_HOLIDAY, sourceKey = "holiday:${d(2, 10)}")
        val result = HolidayRangeOps.mergeSegments(
            (10..14).map { entry(2, it, "春节", HolidayManager.TYPE_PUBLIC_HOLIDAY) }, listOf(a, b))
        // 后应用的覆盖前者: active 里只剩 id2
        assertEquals(1, result.active.size)
        assertEquals("id2", result.active[0].id)
    }

    // ===== 集合展开 =====

    @Test
    fun toSets_expands_ranges_and_splits_types() {
        val active = listOf(
            HolidayRange("id1", "春节", d(2, 10), d(2, 11), HolidayManager.TYPE_PUBLIC_HOLIDAY, null),
            HolidayRange("id2", "班", d(1, 26), d(1, 26), HolidayManager.TYPE_TRANSFER_WORKDAY, null),
        )
        val (holidays, workdays) = HolidayRangeOps.toSets(active)
        assertEquals(setOf(d(2, 10), d(2, 11)), holidays)
        assertEquals(setOf(d(1, 26)), workdays)
    }

    // ===== 序列化 =====

    @Test
    fun overrides_roundtrip_through_json() {
        val overrides = listOf(
            HolidayRange("id1", "校庆", d(3, 8), d(3, 9), HolidayManager.TYPE_PUBLIC_HOLIDAY, null),
            HolidayRange("id2", "调休", d(9, 28), d(9, 28), HolidayManager.TYPE_TRANSFER_WORKDAY, "workday:${d(9, 28)}"),
            HolidayRange("id3", "元旦", d(1, 1), d(1, 1), HolidayRangeOps.REMOVED, "holiday:${d(1, 1)}"),
        )
        val decoded = HolidayRangeOps.decodeOverrides(HolidayRangeOps.encodeOverrides(overrides))
        assertEquals(overrides, decoded)
    }

    @Test
    fun decodeOverrides_survives_garbage_and_bad_rows() {
        assertTrue(HolidayRangeOps.decodeOverrides("{not json").isEmpty())
        assertTrue(HolidayRangeOps.decodeOverrides("[]").isEmpty())
        // start > end 的段跳过
        assertTrue(HolidayRangeOps.decodeOverrides(
            """[{"id":"x","name":"n","start":"2025-03-09","end":"2025-03-08","type":"public_holiday"}]"""
        ).isEmpty())
        // 类型不认的跳过
        assertTrue(HolidayRangeOps.decodeOverrides(
            """[{"id":"x","name":"n","start":"2025-03-08","end":"2025-03-08","type":"weird"}]"""
        ).isEmpty())
    }

    @Test
    fun newId_is_8_hex_chars_and_unique() {
        val ids = (1..100).map { HolidayRangeOps.newId() }.toSet()
        assertEquals(100, ids.size)
        ids.forEach { assertTrue(it.length == 8); assertTrue(it.all { c -> c.isDigit() || c in 'a'..'f' }) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingion.sleepy.util.HolidayRangeTest"`
Expected: FAIL — `HolidayRange`/`HolidayRangeOps` 未定义,编译错误

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.lingion.sleepy.util

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 用户可编辑的节假日段(连续日期范围)。type 复用 HolidayManager 常量 + HolidayRangeOps.REMOVED */
data class HolidayRange(
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val type: String,
    /** 被本段替换/删除的网络段首日标识 "holiday:<date>"/"workday:<date>"; null=纯新增 */
    val sourceKey: String?
)

/** 网络段 + 用户段合并纯函数集(无 Context/网络) */
object HolidayRangeOps {
    /** 用户删除段的哨兵类型: 该段整体抹掉 */
    const val REMOVED = "removed"

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val random = SecureRandom()

    fun newId(): String {
        val bytes = ByteArray(4)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 网络逐日条目 → 连续段。按「name+type 相同 + 日期连续」聚合;
     * 输入乱序没关系(先排序); 返回按 startDate 排序。
     */
    fun aggregateSegments(entries: List<HolidayEntry>): List<HolidayRange> {
        val sorted = entries.sortedBy { it.date }
        val result = mutableListOf<HolidayRange>()
        for (e in sorted) {
            val last = result.lastOrNull()
            if (last != null && last.name == e.name && last.type == e.type && last.endDate.plusDays(1) == e.date) {
                result[result.lastIndex] = last.copy(endDate = e.date)
            } else {
                result.add(HolidayRange(newId(), e.name, e.date, e.date, e.type, null))
            }
        }
        return result
    }

    /** 合并结果: active=生效段, removed=被用户删除的网络段(展示在"已删除"区块) */
    data class MergeResult(val active: List<HolidayRange>, val removed: List<HolidayRange>)

    private fun sourceKeyOf(type: String, date: LocalDate) =
        "${if (type == HolidayManager.TYPE_TRANSFER_WORKDAY) "workday" else "holiday"}:$date"

    /**
     * 网络条目 + 用户覆盖段 → 合并。按 overrides 顺序应用:
     * sourceKey 命中网络段(sourceKey==null 的段)→ 整段抹除; 同 sourceKey 的先前用户段被后者替换。
     * removed 型只在其 sourceKey 确实对应网络段时进入 removed 列表。
     */
    fun mergeSegments(network: List<HolidayEntry>, overrides: List<HolidayRange>): MergeResult {
        val active = aggregateSegments(network).toMutableList()
        val removed = mutableListOf<HolidayRange>()
        val networkKeys = active.map { sourceKeyOf(it.type, it.startDate) }.toSet()

        for (ov in overrides) {
            val sk = ov.sourceKey
            if (sk != null) {
                active.removeAll {
                    (it.sourceKey == null && sourceKeyOf(it.type, it.startDate) == sk) ||
                        (it.sourceKey == sk && it.id != ov.id)
                }
            }
            if (ov.type == REMOVED) {
                if (sk != null && sk in networkKeys) removed.add(ov)
            } else {
                active.add(ov)
            }
        }
        return MergeResult(active = active.sortedBy { it.startDate }, removed = removed)
    }

    /** 生效段 → (holidays, workdays) 集合, 供灰显判定 */
    fun toSets(active: List<HolidayRange>): Pair<Set<LocalDate>, Set<LocalDate>> {
        val holidays = mutableSetOf<LocalDate>()
        val workdays = mutableSetOf<LocalDate>()
        for (seg in active) {
            var d = seg.startDate
            while (!d.isAfter(seg.endDate)) {
                if (seg.type == HolidayManager.TYPE_TRANSFER_WORKDAY) workdays.add(d) else holidays.add(d)
                d = d.plusDays(1)
            }
        }
        return holidays to workdays
    }

    /** 用户段列表 → JSON 数组 */
    fun encodeOverrides(overrides: List<HolidayRange>): String {
        val arr = JSONArray()
        for (ov in overrides) {
            arr.put(
                JSONObject()
                    .put("id", ov.id)
                    .put("name", ov.name)
                    .put("start", dateFormat.format(ov.startDate))
                    .put("end", dateFormat.format(ov.endDate))
                    .put("type", ov.type)
                    .put("sourceKey", ov.sourceKey ?: JSONObject.NULL)
            )
        }
        return arr.toString()
    }

    /** JSON → 用户段列表(坏行跳过, start>end 跳过, 类型不认跳过, 解析失败返回空) */
    fun decodeOverrides(json: String): List<HolidayRange> {
        val result = mutableListOf<HolidayRange>()
        val arr = try { JSONArray(json) } catch (_: Exception) { return emptyList() }
        for (i in 0 until arr.length()) {
            val obj = try { arr.getJSONObject(i) } catch (_: Exception) { continue }
            val id = obj.optString("id", "")
            val name = obj.optString("name", "")
            val start = try { LocalDate.parse(obj.optString("start", ""), dateFormat) } catch (_: Exception) { continue }
            val end = try { LocalDate.parse(obj.optString("end", ""), dateFormat) } catch (_: Exception) { continue }
            val type = obj.optString("type", "")
            if (id.isBlank()) continue
            if (type != HolidayManager.TYPE_PUBLIC_HOLIDAY &&
                type != HolidayManager.TYPE_TRANSFER_WORKDAY && type != REMOVED) continue
            if (end.isBefore(start)) continue
            val sk = if (obj.isNull("sourceKey")) null else obj.optString("sourceKey", "").ifBlank { null }
            result.add(HolidayRange(id, name, start, end, type, sk))
        }
        return result
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.lingion.sleepy.util.HolidayRangeTest"`
Expected: PASS (11 tests)

- [ ] **Step 5: Commit**

```bash
cd /Users/lingion_k/Desktop/sleepy && git add app/src/main/java/com/lingion/sleepy/util/HolidayRange.kt app/src/test/java/com/lingion/sleepy/util/HolidayRangeTest.kt && git commit -m "feat(holiday): 范围化覆盖数据层 — 段聚合/合并/集合展开/JSON 编解码"
```

---

### Task 2: 灰显判定接入范围数据 + 删旧逐日层

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/util/HolidayManager.kt`(删 106-172 行旧覆盖层)
- Modify: `app/src/main/java/com/lingion/sleepy/util/AppPrefs.kt:293-300`(换 accessors)
- Modify: `app/src/test/java/com/lingion/sleepy/util/HolidayOverrideTest.kt`(整文件删除)
- Modify: `app/src/test/java/com/lingion/sleepy/util/HolidayManagerTest.kt`(删引用 `decideGreyWithOverrides` 的测试,如有)

**Interfaces:**
- Consumes: Task 1 的 `HolidayRangeOps.toSets/decodeOverrides/encodeOverrides`
- Produces:
  - `AppPrefs.getHolidayRanges(ctx: Context): List<HolidayRange>`
  - `AppPrefs.setHolidayRanges(ctx: Context, ranges: List<HolidayRange>)`
  - `HolidayManager.shouldGrey(ctx, date)` 签名不变(消费方 ScheduleScreen.kt:186 零改动),内部改为:
    ```
    ranges = AppPrefs.getHolidayRanges(ctx)
    network = entriesCache/holidayCache 走原逻辑取该年条目
    (holidays', workdays') = HolidayRangeOps.toSets(HolidayRangeOps.mergeSegments(networkEntries, ranges).active)
    return decideGrey(date, holidays', workdays', greyHoliday, greyWeekend, ignoreWorkday)
    ```
  - 删除:`OVERRIDE_REMOVED`、`mergeEntries`、`applyOverrides`、`decodeOverrides`、`encodeOverrides`、`decideGreyWithOverrides`

- [ ] **Step 1: 改 HolidayManager.shouldGrey + 删旧层**

`HolidayManager.kt`:
- 删 `OVERRIDE_REMOVED` 常量、`mergeEntries`、`applyOverrides`、`decodeOverrides`、`encodeOverrides`、`decideGreyWithOverrides`(106-172 行)
- `shouldGrey` 重写(内部需要该年 entries——`getYearEntries(year)` 已有缓存逻辑,直接用):

```kotlin
    /** 判断某日期是否应该灰显（根据用户设置，含用户范围化覆盖） */
    suspend fun shouldGrey(ctx: Context, date: LocalDate): Boolean {
        val ranges = AppPrefs.getHolidayRanges(ctx)
        val networkEntries = getYearEntries(date.year)
        val merged = HolidayRangeOps.mergeSegments(networkEntries, ranges)
        val (holidays, workdays) = HolidayRangeOps.toSets(merged.active)
        val workdaysForWeekend = if (AppPrefs.isHolidayGreyWeekend(ctx) && AppPrefs.isHolidayIgnoreWorkday(ctx)) {
            workdays
        } else emptySet()
        return decideGrey(
            date = date,
            holidays = holidays,
            workdays = workdaysForWeekend,
            greyHoliday = AppPrefs.isHolidayGreyHoliday(ctx),
            greyWeekend = AppPrefs.isHolidayGreyWeekend(ctx),
            ignoreWorkday = AppPrefs.isHolidayIgnoreWorkday(ctx)
        )
    }
```

注意:原 `getHolidays/getWorkdays` 只在 `shouldGrey` 用,重写后不再需要——但 `fetchHolidays/fetchWorkdays/fetchYear` 与 `holidayCache/workdayCache` 也随之无消费方,一并删除;`getYearEntries` 变成 shouldGrey 与设置页共用的唯一取数路径。`preload` 改为:

```kotlin
    /** 预加载当前年和明年的节假日数据 */
    suspend fun preload(ctx: Context) {
        val year = LocalDate.now().year
        getYearEntries(year); getYearEntries(year + 1)
    }
```

- [ ] **Step 2: 换 AppPrefs accessors**

`AppPrefs.kt` 293-300 行替换:

```kotlin
    fun getHolidayRanges(ctx: Context): List<com.lingion.sleepy.util.HolidayRange> =
        com.lingion.sleepy.util.HolidayRangeOps.decodeOverrides(sp(ctx).getString(KEY_HOLIDAY_OVERRIDES, "[]") ?: "[]")

    fun setHolidayRanges(ctx: Context, ranges: List<com.lingion.sleepy.util.HolidayRange>) {
        sp(ctx).edit().putString(KEY_HOLIDAY_OVERRIDES, com.lingion.sleepy.util.HolidayRangeOps.encodeOverrides(ranges)).apply()
    }
```

`KEY_HOLIDAY_OVERRIDES` 注释同步改为 `// JSON — 用户范围化覆盖(编辑/新增/删除节日段)`。

- [ ] **Step 3: 删 HolidayOverrideTest.kt、清理 HolidayManagerTest**

```bash
rm app/src/test/java/com/lingion/sleepy/util/HolidayOverrideTest.kt
grep -n "decideGreyWithOverrides\|mergeEntries\|OVERRIDE_REMOVED\|decodeOverrides\|encodeOverrides\|getHolidayOverrides" app/src/test/java/com/lingion/sleepy/util/HolidayManagerTest.kt
```

HolidayManagerTest 里引用已删符号的测试整段删除(纯函数语义已由 HolidayRangeTest 覆盖);`parseEntries`/`decideGrey` 相关测试保留不动。

- [ ] **Step 4: 全量编译 + 单测确认无引用残留**

```bash
grep -rn "OVERRIDE_REMOVED\|decideGreyWithOverrides\|HolidayManager.mergeEntries\|getHolidayOverrides\|setHolidayOverrides" app/src/main app/src/test --include="*.kt"
```
Expected: 无输出(空 grep)。然后:
Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL,全部测试通过(含 Task 1 的 11 个 + HolidayManagerTest 保留项)

- [ ] **Step 5: Commit**

```bash
cd /Users/lingion_k/Desktop/sleepy && git add -A app/src && git commit -m "refactor(holiday): 灰显判定接入范围合并, 删除逐日覆盖层"
```

---

### Task 3: 设置页 UI 范围化 — 列表段行 + 编辑/添加弹窗(起止日期) + 已删除区块

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/mine/HolidaySettingsScreen.kt`(全文重构展示层)
- Modify: `app/src/main/res/values{,-zh-rCN,-zh-rTW,-en,-ja,-es}/strings.xml`(9 个旧串改文案 + 5 个新串)

**Interfaces:**
- Consumes: Task 1 `HolidayRange`/`HolidayRangeOps.{aggregateSegments,mergeSegments,encodeOverrides,decodeOverrides,newId}`、Task 2 `AppPrefs.getHolidayRanges/setHolidayRanges`、现有 `DatePickerField(value,onValueChange,label,isError)`、`HolidayStyleChip(label,selected,onClick)`、`DateUtils.shortDateSlash`
- Produces: 无下游消费方(叶子 UI)

**字符串改动**(9 改 + 5 新,六 locale 同步):

| key | 新文案(zh-CN 基准) |
|---|---|
| holiday_edit_title | 编辑节日(旧"编辑条目") |
| holiday_add_title | 添加节日(旧"添加条目") |
| holiday_name_label | 节日名称(旧"名称(可选)") |
| holiday_name_label_date | 开始日期(旧"日期") |
| holiday_name_label_end **新增** | 结束日期 |
| holiday_date_invalid **新增** | 日期无效或结束早于开始 |
| holiday_removed_section **新增** | 已删除 |
| holiday_restore **新增** | 恢复默认 |
| holiday_delete_range **新增** | 删除 |
| holiday_add_entry | 添加节日(旧"添加") |
| holiday_remove_override | 删除(删除弹窗里的动作,旧"恢复默认"语义废弃;恢复默认改用 holiday_restore) |
| holiday_custom_badge | 自定义(不变) |
| holiday_type_holiday / holiday_type_workday | 不变 |

其余 locale 文案按语义翻译(en: Edit holiday / Add holiday / Holiday name / Start date / End date / Invalid dates or end before start / Deleted / Restore default / Delete / Add holiday;ja・es・zh-TW 同理)。

**UI 结构:**

1. 状态:`overrides: List<HolidayRange>`、`editing: HolidayRange?`(编辑既有段)、`showAdd: Boolean`、`target: EditingTarget?`。弹窗统一为一个 `HolidayRangeEditDialog(target: HolidayRange?, isNew: Boolean, onDismiss, onSave(HolidayRange), onDelete(id), onRestore(HolidayRange))`;编辑网络段时先把该段复制进 dialog target(sourceKey 填 `holiday:<start>`/`workday:<start>`)。
2. 列表(替换现 272-301 行):`mergeSegments(loaded.holidays + loaded.workdays, overrides)` →
   - `active.filter { type == TYPE_PUBLIC_HOLIDAY }` → "节假日" 卡;`active.filter { type == TYPE_TRANSFER_WORKDAY }` → "补班日" 卡(带补班 badge)
   - 行内容:名称(weight 1f)+ 自定义 badge(若 `id` 在 overrides 的 id 集合)+ 起止日期 `M/d – M/d`(单日只显示 `M/d`)
   - 行点击 → `editing = segment`
   - `removed` 非空时追加"已删除"卡:行 = 名称 + 日期 + "恢复默认" TextButton(点击 = 从 overrides 移除该段)
3. `HolidayRangeEditDialog`(替换现 `HolidayEntryEditDialog`):
   - 名称 `OutlinedTextField`
   - 开始日期 `DatePickerField` + 结束日期 `DatePickerField`(两个并排或纵向,纵向与现有弹窗布局一致)
   - 校验:`start != null && end != null && !end.isBefore(start)`,否则 confirmButton disabled + `holiday_date_invalid` 错误文案
   - 类型 chips 复用 `HolidayStyleChip`(假日/补班)
   - 动作区:`isNew=false` 时显示两个 TextButton——**删除**(onDelete:把该段从 overrides 移除;若段是网络段派生且还没保存过覆盖,直接写一条 `type=REMOVED, sourceKey=该段网络键` 的覆盖)和 **恢复默认**(onRestore:从 overrides 移除该 id 的覆盖)。confirmButton=保存,onSave 组装 `HolidayRange(id = target?.id ?: newId(), name, start, end, type, sourceKey = target?.sourceKey ?: 编辑网络段时的 "holiday|workday:<首日>")`
4. 保存逻辑(screen 层):
   ```kotlin
   fun saveRange(range: HolidayRange) {
       val next = overrides.filter { it.id != range.id }.toMutableList()
       next.add(range)
       AppPrefs.setHolidayRanges(context, next)
       reload()
   }
   fun deleteRange(range: HolidayRange) {
       // 若是网络段(无 id 于 overrides), 写 REMOVED 覆盖; 否则直接移除
       val known = overrides.any { it.id == range.id }
       val next = overrides.filter { it.id != range.id }.toMutableList()
       if (!known) {
           val key = "${if (range.type == HolidayManager.TYPE_TRANSFER_WORKDAY) "workday" else "holiday"}:${range.startDate}"
           next.add(HolidayRange(HolidayRangeOps.newId(), range.name, range.startDate, range.endDate, HolidayRangeOps.REMOVED, key))
       }
       AppPrefs.setHolidayRanges(context, next)
       reload()
   }
   fun restoreRange(range: HolidayRange) {
       AppPrefs.setHolidayRanges(context, overrides.filter { it.id != range.id })
       reload()
   }
   ```
   编辑网络段入弹窗:`editing = segment.copy(id = segment.id)`(聚合段 id 本来就是 newId 生成的,不在 overrides 里即视为网络段;sourceKey 缺失时保存时补 `holiday|workday:<startDate>`)。
5. 删除旧符号:`EditingEntry`、`HolidayEntryListCard`(整体替换为段行版 `HolidayRangeListCard`)、`HolidayEntryEditDialog`;imports 清理(`HolidayEntry` 若不再直接用则移除)。

- [ ] **Step 1: 六 locale strings.xml 改 9 串 + 加 5 串**

- [ ] **Step 2: 重构 HolidaySettingsScreen.kt 按上述结构**

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
cd /Users/lingion_k/Desktop/sleepy && git add -A app/src && git commit -m "feat(holiday): 设置页范围化 — 段列表/起止日期弹窗/已删除区块/恢复默认"
```

---

### Task 4: 真机 E2E 验证 + 装机

**Files:** 无代码改动;验证产出为截图

**Interfaces:**
- Consumes: Task 3 的 APK;真机 `d3efcd6a`(PKX110, arm64);导航路径:我的 → 节假日设置

- [ ] **Step 1: 构建并装机**

```bash
cd /Users/lingion_k/Desktop/sleepy && ./gradlew :app:assembleDebug && adb -s d3efcd6a install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

- [ ] **Step 2: uiautomator 定位 + 逐流程验证**

流程清单(每步截图存 `audit_shots/holiday_range/`):
1. 打开节假日设置 → 列表按段显示(春节一行 1/28–2/4 这种),不是逐日 8 行
2. 点节假日段行 → 弹窗显示名称+开始+结束+类型;改结束日期 → 保存 → 列表行日期更新 + 出"自定义"badge
3. 点"删除" → 该段从列表消失,出现在"已删除"区块;课程表该日期不再灰显
4. "已删除"区块点"恢复默认" → 段回到原列表,灰显恢复
5. 点"添加节日" → 填名称+起止+类型 → 保存 → 列表新增段 + 灰显生效
6. 单元测试全绿 + 手动回归:课程表页周末灰显正常

- [ ] **Step 3: 汇报验证结果(附截图路径), 不 commit 截图外的代码**
