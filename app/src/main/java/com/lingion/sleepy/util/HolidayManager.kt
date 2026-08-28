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
object HolidayManager {
    private const val BASE_URL = "https://unpkg.com/holiday-calendar/data/CN/"
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val holidayCache = mutableMapOf<Int, Set<LocalDate>>()

    /** 判断某日期是否为周末 */
    fun isWeekend(date: LocalDate): Boolean {
        return date.dayOfWeek.value == 6 || date.dayOfWeek.value == 7
    }

    /** 判断某日期是否应该灰显（根据用户设置） */
    suspend fun shouldGrey(ctx: Context, date: LocalDate): Boolean {
        // 周末
        if (AppPrefs.isHolidayGreyWeekend(ctx) && isWeekend(date)) {
            return true
        }

        // 法定节假日（已过滤掉补班日，只缓存 public_holiday）
        val holidays = getHolidays(ctx, date.year)
        return date in holidays
    }

    /** 获取某年的全部节假日日期（已排除 transfer_workday） */
    private suspend fun getHolidays(ctx: Context, year: Int): Set<LocalDate> {
        holidayCache[year]?.let { return it }
        val fetched = fetchHolidays(year)
        holidayCache[year] = fetched
        return fetched
    }

    private suspend fun fetchHolidays(year: Int): Set<LocalDate> = withContext(Dispatchers.IO) {
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
            parseHolidayJson(json)
        } catch (e: Exception) {
            e.printStackTrace()
            emptySet()
        } finally {
            conn.disconnect()
        }
    }

    /** 解析返回的 JSON。仅保留 type=public_holiday 的日期。 */
    private fun parseHolidayJson(json: String): Set<LocalDate> {
        val result = mutableSetOf<LocalDate>()
        try {
            val root = JSONObject(json)
            val dates = root.optJSONArray("dates") ?: return result
            for (i in 0 until dates.length()) {
                val entry = dates.getJSONObject(i)
                val type = entry.optString("type", "")
                if (type != "public_holiday") continue
                val dateStr = entry.optString("date", "")
                if (dateStr.isBlank()) continue
                try {
                    result.add(LocalDate.parse(dateStr, dateFormat))
                } catch (_: Exception) {
                    // 单条日期解析失败不中断整批
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    /** 预加载当前年和明年的节假日 */
    suspend fun preload(ctx: Context) {
        val year = LocalDate.now().year
        getHolidays(ctx, year)
        getHolidays(ctx, year + 1)
    }
}