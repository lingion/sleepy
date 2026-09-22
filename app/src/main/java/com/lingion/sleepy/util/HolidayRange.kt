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

/**
 * issue#44 调休映射: 放假日 → 该天的课调到哪个补班日去上。
 *
 * sourceDate = 被放假挪走课的那天(放假日, 通常有课)
 * targetDate = 补班日(国家安排的"上班"日, 该天上 sourceDate 星期几的课)
 * segmentId  = 所属节假日段(展示归属, 不参与判定)
 *
 * API 只给"哪天补班", 不给"补的谁的课"(各校自定), 因此映射由用户手选,
 * 默认空 = 不映射(该天按自然星期取课)。
 */
data class HolidayTransferEntry(
    val sourceDate: LocalDate,
    val targetDate: LocalDate,
    val segmentId: String
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

    // ===== issue#44 调休映射(第二轮: 放假日 → 补班日) =====

    /** 映射纯函数集(无 Context); 互斥写入由 AppPrefs 组合本对象完成 */
    object HolidayTransferOps {

        /** 某放假日是否已有映射; 有 → 该 entry, 无 → null */
        fun transferFor(date: LocalDate, transfers: List<HolidayTransferEntry>): HolidayTransferEntry? =
            transfers.lastOrNull { it.sourceDate == date }

        /**
         * 某天应"按星期几取课": 命中映射 → targetDate 的自然星期; 未命中 → 当天自然星期。
         * 补班日(如 1/4 周日)上的是被调走那天的课, 网格该列排 sourceDate 星期几的课。
         */
        fun effectiveDayOfWeek(date: LocalDate, transfers: List<HolidayTransferEntry>): Int {
            val hit = transferFor(date, transfers) ?: return date.dayOfWeek.value
            return hit.targetDate.dayOfWeek.value
        }

        /**
         * 互斥写(核心不变量): 一个 targetDate 一天只能上一次课 —
         * 写入 [newEntry] 前先移除同 targetDate 的既有条目(后选覆盖前选),
         * 同 sourceDate 的旧条目也一并替换。返回可直接落盘的新列表。
         */
        fun withTargetExclusivity(
            existing: List<HolidayTransferEntry>,
            newEntry: HolidayTransferEntry
        ): List<HolidayTransferEntry> =
            (existing.filterNot {
                it.targetDate == newEntry.targetDate || it.sourceDate == newEntry.sourceDate
            } + newEntry).sortedBy { it.sourceDate }

        /**
         * 失效判定: sourceDate 不在当年放假日集合(段被数据源改掉/学校另行通知)。
         * 失效 entry 数据保留, UI 灰显提示, 用户手清。
         */
        fun isOrphanFor(entry: HolidayTransferEntry, yearHolidayDates: Set<LocalDate>): Boolean =
            entry.sourceDate !in yearHolidayDates

        /** 映射列表 → JSON 数组 */
        fun encodeTransfers(transfers: List<HolidayTransferEntry>): String {
            val arr = JSONArray()
            for (t in transfers) {
                arr.put(
                    JSONObject()
                        .put("sourceDate", dateFormat.format(t.sourceDate))
                        .put("targetDate", dateFormat.format(t.targetDate))
                        .put("segmentId", t.segmentId)
                )
            }
            return arr.toString()
        }

        /** JSON → 映射列表(坏行跳过; 同 sourceDate 只留最后一条; 解析失败返回空) */
        fun decodeTransfers(json: String): List<HolidayTransferEntry> {
            val arr = try { JSONArray(json) } catch (_: Exception) { return emptyList() }
            val bySource = linkedMapOf<LocalDate, HolidayTransferEntry>()
            for (i in 0 until arr.length()) {
                val obj = try { arr.getJSONObject(i) } catch (_: Exception) { continue }
                val src = try { LocalDate.parse(obj.optString("sourceDate", ""), dateFormat) } catch (_: Exception) { continue }
                val dst = try { LocalDate.parse(obj.optString("targetDate", ""), dateFormat) } catch (_: Exception) { continue }
                val seg = obj.optString("segmentId", "")
                bySource[src] = HolidayTransferEntry(src, dst, seg)
            }
            return bySource.values.sortedBy { it.sourceDate }
        }
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
