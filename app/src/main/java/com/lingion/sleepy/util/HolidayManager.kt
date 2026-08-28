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

    private const val BASE_URL = "https://unpkg.com/holiday-calendar/data/CN/"
    /** 与 AppPrefs 同一个 SharedPreferences 文件 */
    private const val PREFS_NAME = "sleepy_prefs"
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val entriesCache = mutableMapOf<Int, List<HolidayEntry>>()
    /** 各年数据是否拉取成功过(空结果+成功=false => 网络失败) */
    private val yearFetchFailed = mutableMapOf<Int, Boolean>()

    /**
     * 磁盘缓存 — 拉成功一次即落盘, 进程重启后仍有效。
     * 刷新走 [refreshYearEntries] 强制绕过内存+磁盘。
     */
    private const val CACHE_KEY_PREFIX = "holiday_entries_"
    private const val CACHE_LOADED_SUFFIX = "_loaded"
    private fun cacheKey(year: Int) = CACHE_KEY_PREFIX + year
    private fun loadedKey(year: Int) = cacheKey(year) + CACHE_LOADED_SUFFIX

    private fun diskCache(ctx: Context, year: Int): List<HolidayEntry>? {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // loaded 标记与数据同写: 空年份(该年确实无数据)也视为已缓存, 否则每次启动都会重复请求
        if (!prefs.getBoolean(loadedKey(year), false)) return null
        val json = prefs.getString(cacheKey(year), null) ?: return null
        return try {
            val dates = org.json.JSONArray(json)
            val wrapped = org.json.JSONObject().put("dates", dates)
            parseEntries(wrapped.toString())
        } catch (_: Exception) {
            null
        }
    }
    private fun writeDiskCache(ctx: Context, year: Int, entries: List<HolidayEntry>) {
        val arr = org.json.JSONArray()
        entries.forEach { e ->
            arr.put(org.json.JSONObject().put("date", e.date.toString()).put("name", e.name).put("type", e.type))
        }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(cacheKey(year), arr.toString())
            .putBoolean(loadedKey(year), true)
            .apply()
    }

    /** 判断某日期是否为周末 */
    fun isWeekend(date: LocalDate): Boolean {
        return date.dayOfWeek.value == 6 || date.dayOfWeek.value == 7
    }

    /** 判断某日期是否应该灰显（根据用户设置，含用户范围化覆盖） */
    suspend fun shouldGrey(ctx: Context, date: LocalDate): Boolean {
        val ranges = AppPrefs.getHolidayRanges(ctx)
        val networkEntries = getYearEntries(ctx, date.year)
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

    /**
     * 获取某年全部原始网络条目(节假日+补班日, 带名称, 不含用户覆盖)。
     * 取数顺序: 内存缓存 → 磁盘缓存(拉成功一次即永久) → 网络。
     * 空列表 + [yearFetchFailed] 为 true 表示网络失败而非"该年无数据"。
     * 灰显判定与设置页共用的唯一取数路径; 覆盖合并由调用方用 [HolidayRangeOps.mergeSegments] 完成。
     */
    suspend fun getYearEntries(ctx: Context, year: Int): List<HolidayEntry> {
        entriesCache[year]?.let { return it }
        diskCache(ctx, year)?.let {
            entriesCache[year] = it
            return it
        }
        val entries = fetchEntries(year)
        if (entries.isNotEmpty() || !isYearFetchFailed(year)) {
            // 成功(含空年份, loaded 标记保证空数据也算已缓存)才落盘; 网络失败不缓存失败态
            entriesCache[year] = entries
            writeDiskCache(ctx, year, entries)
        }
        return entries
    }

    /** 某年数据是否因网络原因拉取失败 */
    fun isYearFetchFailed(year: Int): Boolean = yearFetchFailed[year] == true

    /** 强制重新拉取某年条目(设置页"刷新"按钮用): 绕过内存+磁盘缓存 */
    suspend fun refreshYearEntries(ctx: Context, year: Int): List<HolidayEntry> {
        entriesCache.remove(year)
        yearFetchFailed.remove(year)
        return getYearEntries(ctx, year)
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

    /** 预加载当前年和明年的节假日数据 */
    suspend fun preload(ctx: Context) {
        val year = LocalDate.now().year
        getYearEntries(ctx, year); getYearEntries(ctx, year + 1)
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