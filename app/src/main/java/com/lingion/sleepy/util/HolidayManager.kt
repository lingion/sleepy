package com.lingion.sleepy.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 节假日管理器 - 从网络获取法定节假日，App 内判断是否显示灰显。
 *
 * API: https://unpkg.com/holiday-calendar/data/CN/{year}.json
 * 格式: {"year":2025,"region":"CN","dates":[{"date":"2025-01-01","name":"元旦","type":"public_holiday"}]}
 *
 * 数据源：https://gitcode.com/zy-mayong/publicHoliday (MIT, 商用 OK)
 */

/** 带名称的节假日/补班日条目 */
data class HolidayEntry(val date: LocalDate, val name: String, val type: String)

object HolidayManager {
    /** API 条目类型: 法定节假日 / 补班日(周末但要上课) */
    const val TYPE_PUBLIC_HOLIDAY = "public_holiday"
    const val TYPE_TRANSFER_WORKDAY = "transfer_workday"

    /** 用户覆盖哨兵类型: 该日期被用户删除(强制不灰显) */
    const val OVERRIDE_REMOVED = "override_removed"

    private const val BASE_URL = "https://unpkg.com/holiday-calendar/data/CN/"
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val holidayCache = mutableMapOf<Int, Set<LocalDate>>()
    private val workdayCache = mutableMapOf<Int, Set<LocalDate>>()
    private val entriesCache = mutableMapOf<Int, List<HolidayEntry>>()
    /** 各年数据是否拉取成功过(空结果+成功=false => 网络失败) */
    private val yearFetchFailed = mutableMapOf<Int, Boolean>()

    /** 判断某日期是否为周末 */
    fun isWeekend(date: LocalDate): Boolean {
        return date.dayOfWeek.value == 6 || date.dayOfWeek.value == 7
    }

    /** 判断某日期是否应该灰显（根据用户设置，含用户覆盖） */
    suspend fun shouldGrey(ctx: Context, date: LocalDate): Boolean {
        val overrides = AppPrefs.getHolidayOverrides(ctx)
        val holidays = getHolidays(ctx, date.year)
        val workdays = if (AppPrefs.isHolidayGreyWeekend(ctx) && AppPrefs.isHolidayIgnoreWorkday(ctx)) {
            getWorkdays(ctx, date.year)
        } else emptySet()
        return decideGreyWithOverrides(
            date = date,
            overrides = overrides,
            holidays = holidays,
            workdays = workdays,
            greyHoliday = AppPrefs.isHolidayGreyHoliday(ctx),
            greyWeekend = AppPrefs.isHolidayGreyWeekend(ctx),
            ignoreWorkday = AppPrefs.isHolidayIgnoreWorkday(ctx)
        )
    }

    /** 获取某年的节假日日期（仅 public_holiday） */
    private suspend fun getHolidays(ctx: Context, year: Int): Set<LocalDate> {
        holidayCache[year]?.let { return it }
        val fetched = fetchHolidays(year)
        holidayCache[year] = fetched
        return fetched
    }

    /** 获取某年的补班日日期（仅 transfer_workday） */
    private suspend fun getWorkdays(ctx: Context, year: Int): Set<LocalDate> {
        workdayCache[year]?.let { return it }
        val fetched = fetchWorkdays(year)
        workdayCache[year] = fetched
        return fetched
    }

    /**
     * 获取某年全部条目(节假日+补班日, 带名称, 含用户覆盖), 供设置二级页展示。
     * 空列表 + [yearFetchFailed] 为 true 表示网络失败而非"该年无数据"。
     */
    suspend fun getYearEntries(ctx: Context, year: Int): List<HolidayEntry> {
        val cached = entriesCache[year]
        if (cached != null) return applyOverrides(cached, AppPrefs.getHolidayOverrides(ctx))
        val entries = fetchEntries(year)
        entriesCache[year] = entries
        return applyOverrides(entries, AppPrefs.getHolidayOverrides(ctx))
    }

    /** 某年数据是否因网络原因拉取失败 */
    fun isYearFetchFailed(year: Int): Boolean = yearFetchFailed[year] == true

    /** 强制重新拉取某年条目(二级页"重试"用), 同时刷新灰显判定缓存 */
    suspend fun refreshYearEntries(ctx: Context, year: Int): List<HolidayEntry> {
        entriesCache.remove(year)
        holidayCache.remove(year)
        workdayCache.remove(year)
        yearFetchFailed.remove(year)
        return getYearEntries(ctx, year)
    }

    // ===== 用户覆盖层 =====
    // 存储: AppPrefs "holiday_overrides" JSON — { "yyyy-MM-dd": {"name":..., "type":...} }
    // type 为 OVERRIDE_REMOVED 表示该日被用户删除(视作"什么都不标")。

    /** 网络条目 + 用户覆盖 → 展示列表。编辑/新增替换或追加, 删除的剔除; 结果按日期排序。 */
    fun mergeEntries(
        networkEntries: List<HolidayEntry>,
        overrides: Map<LocalDate, HolidayEntry>
    ): List<HolidayEntry> {
        val byDate = networkEntries.associateByTo(mutableMapOf()) { it.date }
        for ((date, ov) in overrides) {
            if (ov.type == OVERRIDE_REMOVED) byDate.remove(date)
            else byDate[date] = ov.copy(date = date)
        }
        return byDate.values.sortedBy { it.date }
    }

    private fun applyOverrides(
        networkEntries: List<HolidayEntry>,
        overrides: Map<LocalDate, HolidayEntry>
    ): List<HolidayEntry> = if (overrides.isEmpty()) networkEntries else mergeEntries(networkEntries, overrides)

    /** 覆盖 JSON → Map(坏行跳过/类型不认的跳过/解析失败返回空) */
    fun decodeOverrides(json: String): Map<LocalDate, HolidayEntry> {
        val result = mutableMapOf<LocalDate, HolidayEntry>()
        val root = try { JSONObject(json) } catch (_: Exception) { return emptyMap() }
        for (key in root.keys()) {
            val date = parseDate(key) ?: continue
            val obj = root.optJSONObject(key) ?: continue
            val type = obj.optString("type", "")
            if (type != TYPE_PUBLIC_HOLIDAY && type != TYPE_TRANSFER_WORKDAY && type != OVERRIDE_REMOVED) continue
            result[date] = HolidayEntry(date = date, name = obj.optString("name", ""), type = type)
        }
        return result
    }

    /** 覆盖 Map → JSON */
    fun encodeOverrides(overrides: Map<LocalDate, HolidayEntry>): String {
        val root = JSONObject()
        for ((date, entry) in overrides) {
            root.put(
                dateFormat.format(date),
                JSONObject().put("name", entry.name).put("type", entry.type)
            )
        }
        return root.toString()
    }

    /**
     * 灰显判定(含用户覆盖)。覆盖优先级最高:
     * public_holiday → 灰, transfer_workday / removed → 不灰, 无覆盖走基础逻辑。
     */
    fun decideGreyWithOverrides(
        date: LocalDate,
        overrides: Map<LocalDate, HolidayEntry>,
        holidays: Set<LocalDate>,
        workdays: Set<LocalDate>,
        greyHoliday: Boolean,
        greyWeekend: Boolean,
        ignoreWorkday: Boolean
    ): Boolean {
        when (overrides[date]?.type) {
            TYPE_PUBLIC_HOLIDAY -> return true
            TYPE_TRANSFER_WORKDAY, OVERRIDE_REMOVED -> return false
        }
        return decideGrey(date, holidays, workdays, greyHoliday, greyWeekend, ignoreWorkday)
    }

    private suspend fun fetchHolidays(year: Int): Set<LocalDate> =
        fetchYear(year) { type, dateStr, result ->
            if (type == TYPE_PUBLIC_HOLIDAY) parseDate(dateStr)?.let { result.add(it) }
        }

    private suspend fun fetchWorkdays(year: Int): Set<LocalDate> =
        fetchYear(year) { type, dateStr, result ->
            if (type == TYPE_TRANSFER_WORKDAY) parseDate(dateStr)?.let { result.add(it) }
        }

    /** 拉取并解析某年全部条目(带名称), 供二级页展示 */
    private suspend fun fetchEntries(year: Int): List<HolidayEntry> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL$year.json"
        val conn = try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = CONNECT_TIMEOUT_MS
            c.readTimeout = READ_TIMEOUT_MS
            c.setRequestProperty("User-Agent", "Sleepy/holiday")
            c
        } catch (_: Exception) {
            yearFetchFailed[year] = true
            return@withContext emptyList()
        }
        try {
            if (conn.responseCode !in 200..299) {
                yearFetchFailed[year] = true
                return@withContext emptyList()
            }
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val entries = parseEntries(json)
            if (entries.isEmpty() && !json.contains("\"dates\"")) {
                // 返回体异常(非预期结构)视为失败
                yearFetchFailed[year] = true
            }
            entries
        } catch (_: Exception) {
            yearFetchFailed[year] = true
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    /** 解析 API JSON 为条目列表(纯函数, 单测覆盖) */
    fun parseEntries(json: String): List<HolidayEntry> {
        return try {
            val root = JSONObject(json)
            val dates = root.optJSONArray("dates") ?: return emptyList()
            val entries = mutableListOf<HolidayEntry>()
            for (i in 0 until dates.length()) {
                val entry = dates.getJSONObject(i)
                val date = parseDate(entry.optString("date", "")) ?: continue
                entries.add(
                    HolidayEntry(
                        date = date,
                        name = entry.optString("name", ""),
                        type = entry.optString("type", "")
                    )
                )
            }
            entries.sortedBy { it.date }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseDate(dateStr: String): LocalDate? = try {
        LocalDate.parse(dateStr, dateFormat)
    } catch (_: Exception) {
        null
    }

    private suspend fun fetchYear(
        year: Int,
        collect: (type: String, dateStr: String, result: MutableSet<LocalDate>) -> Unit
    ): Set<LocalDate> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL$year.json"
        val conn = try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = CONNECT_TIMEOUT_MS
            c.readTimeout = READ_TIMEOUT_MS
            c.setRequestProperty("User-Agent", "Sleepy/holiday")
            c
        } catch (e: Exception) {
            return@withContext emptySet()
        }
        try {
            if (conn.responseCode !in 200..299) {
                return@withContext emptySet()
            }
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val result = mutableSetOf<LocalDate>()
            try {
                val root = JSONObject(json)
                val dates = root.optJSONArray("dates") ?: return@withContext result
                for (i in 0 until dates.length()) {
                    val entry = dates.getJSONObject(i)
                    collect(entry.optString("type", ""), entry.optString("date", ""), result)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptySet()
        } finally {
            conn.disconnect()
        }
    }

    /** 预加载当前年和明年的节假日+补班日 */
    suspend fun preload(ctx: Context) {
        val year = LocalDate.now().year
        getHolidays(ctx, year); getWorkdays(ctx, year)
        getHolidays(ctx, year + 1); getWorkdays(ctx, year + 1)
    }

    /**
     * 灰显判定纯函数(无 Context/网络, 单测覆盖)。
     * [greyHoliday]/[greyWeekend]/[ignoreWorkday] 对应用户三个开关,
     * [workdays] 为该年补班日集合(仅周末判定分支用到)。
     */
    fun decideGrey(
        date: LocalDate,
        holidays: Set<LocalDate>,
        workdays: Set<LocalDate>,
        greyHoliday: Boolean,
        greyWeekend: Boolean,
        ignoreWorkday: Boolean
    ): Boolean {
        // 法定节假日（独立开关）
        if (greyHoliday && date in holidays) return true

        // 周末; 补班日(transfer_workday)是"周末但要上课"的日子, 开关开时豁免
        if (greyWeekend && isWeekend(date)) {
            if (ignoreWorkday && date in workdays) return false
            return true
        }
        return false
    }
}