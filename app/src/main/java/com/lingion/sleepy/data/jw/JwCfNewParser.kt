package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject

/**
 * 新青果 NTSS (FullCalendar 形态) 课表 JSON 解析器 (type=cf_new)。
 *
 * 适配学校：江西中医药大学 (jiaowu.jxutcm.edu.cn, 2026-09 采集包实锤)。
 * 与其它 [JwParser] 子类不同：source 不是 HTML，而是抓取侧 (CF_NEW_FETCH_JS) 组装的
 * 组合 JSON 信封 —
 *
 * ```
 * {"sleepyCfNtss":true,"xnxqdm":"202601","startDate":"2026-09-07",
 *  "weeks":[{"week":1,"rows":[{…}]},…],
 *  "periods":[{"node":1,"start":"08:30","end":"09:10"},…]}
 * ```
 *
 * rows 为 POST /new/student/xsgrkb/getCalendarWeekDatas 原始行（JS 只透传不解码，
 * 跨语言 invariant）。字段语义（2026-09-16 真实响应实锚）：
 *   kcmc=课名  teaxms=教师  jxcdmc=教学场地  xq=星期(1-7)  ps/pe=起止节（**零填充且可为空串**，
 *   空串时以行内 qssj/jssj 对 periods 节次时间反推节点）
 *   zc=**单周次**（"2"，与所在请求周一致 — 全学期周次归属由逐周请求的 week 决定；
 *   兼容旧假设的全学期串形态：zc 展开多于 1 个周次时以其为准）
 *   kcbh=课号  jxbmc=教学班  bapjxcd='1'=排课不用场地（jxcdmc 空）
 *
 * 抓取侧优先单请求全量（zc 空时服务端回整学期，bucket week=0）：同一门课在其 occurring
 * 的每周各有一行，本 parser 按唯一键 (kcbh|kcmc|teaxms|jxcdmc|xq|ps|pe|jxbmc) 聚合
 * **合并周次集合**，周次归属优先取行自身 zc（两种形态下都正确），缺失才回退所在请求周；
 * 复用 [JwChengFangParser.weekIntList2WeekBeanList] 折叠为 (start,end,type) 三元组。
 */
class JwCfNewParser(source: String) : JwParser(source) {

    /** 节次时间 (periods 行, JS 透传 businessHours) */
    private data class Period(val node: Int, val start: String, val end: String)

    /** 跨周聚合桶：同 key 行共享课务元数据, 周次集合逐周累加 */
    private data class Acc(
        val name: String,
        val room: String,
        val teacher: String,
        val day: Int,
        val startNode: Int,
        val endNode: Int,
        val weeks: MutableList<Int>,
    )

    override fun generateCourseList(): List<JwCourse> {
        val root = try { JSONObject(source) } catch (e: Exception) { return emptyList() }
        val weeksArr = root.optJSONArray("weeks") ?: return emptyList()
        val periods = parsePeriods(root.optJSONArray("periods"))

        // 两遍扫描。第一遍: 收集全部行 + 从行自身派生 节次时间→节点 回退映射 —
        // ps/pe 空串的行 (如 操作系统) 依赖 periods (businessHours) 反推, 但真机上
        // 该变量可能抠不到; 此时用同响应里带 ps+qssj / pe+jssj 的行锚定时间→节点。
        val flatRows = arrayListOf<Pair<JSONObject, Int>>() // (row, bucketWeekNo)
        val startByTime = HashMap<String, Int>()
        val endByTime = HashMap<String, Int>()
        for (w in 0 until weeksArr.length()) {
            val week = weeksArr.optJSONObject(w) ?: continue
            val weekNo = week.optInt("week", 0) // week=0 → 单请求全量 bucket, 周次全部来自行 zc
            val rows = week.optJSONArray("rows") ?: continue
            for (i in 0 until rows.length()) {
                val o = rows.optJSONObject(i) ?: continue
                flatRows += o to weekNo
                val ps = o.optString("ps", "").trim().toIntOrNull()
                val qssj = normTime(o.optString("qssj", ""))
                if (ps != null && qssj.isNotBlank()) startByTime.putIfAbsent(qssj, ps)
                val pe = o.optString("pe", "").trim().toIntOrNull()
                val jssj = normTime(o.optString("jssj", ""))
                if (pe != null && jssj.isNotBlank()) endByTime.putIfAbsent(jssj, pe)
            }
        }

        val acc = LinkedHashMap<String, Acc>()
        for ((o, weekNo) in flatRows) {
            val name = o.optString("kcmc", "").trim()
            if (name.isBlank()) continue
            val teacher = o.optString("teaxms", "").trim()
            val room = o.optString("jxcdmc", "").trim()
                .ifBlank { if (o.optString("bapjxcd", "") == "1") "不用场地" else "" }
            val day = o.optString("xq", "").trim().toIntOrNull() ?: continue
            // ps/pe 零填充且可为空串 — 空串时反推节点: businessHours periods →
            // 同响应行自锚定映射 (仍失败则丢行)
            val qssjN = normTime(o.optString("qssj", ""))
            val jssjN = normTime(o.optString("jssj", ""))
            val startNode = o.optString("ps", "").trim().toIntOrNull()
                ?: periods.firstOrNull { it.start == qssjN }?.node
                ?: startByTime[qssjN]
                ?: continue
            val endNode = (o.optString("pe", "").trim().toIntOrNull()
                ?: periods.firstOrNull { it.end == jssjN }?.node
                ?: endByTime[jssjN]
                ?: startNode).coerceAtLeast(startNode)
            // 周次归属: 优先行自身 zc — 全量形态 (bucket week=0) 行 zc 即所属周;
            // 逐周兜底形态行 zc == 请求周, 同样正确; zc 缺失才回退所在请求周
            val zcList = parseWeekList(o.optString("zc", ""))
            val addWeeks = when {
                zcList.size > 1 -> zcList
                zcList.size == 1 -> listOf(zcList[0])
                weekNo >= 1 -> listOf(weekNo)
                else -> emptyList()
            }
            if (addWeeks.isEmpty()) continue
            val key = listOf(
                o.optString("kcbh", ""), name, teacher, room,
                day.toString(), startNode.toString(), endNode.toString(),
                o.optString("jxbmc", ""),
            ).joinToString("|")
            val a = acc.getOrPut(key) {
                Acc(name, room, teacher, day, startNode, endNode, mutableListOf())
            }
            a.weeks.addAll(addWeeks)
        }

        val result = arrayListOf<JwCourse>()
        for (a in acc.values) {
            for (wb in JwChengFangParser.weekIntList2WeekBeanList(a.weeks.distinct().sorted())) {
                result += JwCourse(
                    name = a.name,
                    room = a.room,
                    teacher = a.teacher,
                    day = a.day.coerceIn(1, 7),
                    startNode = a.startNode.coerceAtLeast(1),
                    endNode = a.endNode.coerceAtLeast(a.startNode),
                    startWeek = wb.first.coerceAtLeast(1),
                    endWeek = wb.second.coerceAtLeast(wb.first),
                    type = wb.third,
                )
            }
        }
        return result
    }

    private fun parsePeriods(arr: JSONArray?): List<Period> {
        if (arr == null) return emptyList()
        val list = arrayListOf<Period>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val node = o.optInt("node", 0)
            if (node < 1) continue
            list += Period(node, normTime(o.optString("start", "")), normTime(o.optString("end", "")))
        }
        return list
    }

    /** "10:15:00"/"10:15" → "10:15" (小时位补零, 兼容 periods 与行内时间格式差异) */
    private fun normTime(t: String): String {
        val parts = t.trim().split(':')
        if (parts.size < 2) return t.trim()
        val h = parts[0].trim().toIntOrNull() ?: return t.trim()
        val m = parts[1].trim()
        return "%02d:%s".format(h, if (m.length == 1) "0$m" else m.substring(0, 2))
    }

    companion object {
        /**
         * 周次串 → 周次列表。逗号分隔的数字为主形态（"1,2,3"），
         * 兼容区间段（"1-16"）与混合（"1-8,10,12-16"）；非法 token 跳过。
         */
        internal fun parseWeekList(zcs: String): List<Int> {
            val rangeRe = Regex("""^(\d+)\s*[-~—]\s*(\d+)$""")
            return zcs.split(',').flatMap { tok ->
                val t = tok.trim()
                val r = rangeRe.find(t)
                if (r != null) {
                    val a = r.groupValues[1].toIntOrNull() ?: return@flatMap emptyList<Int>()
                    val b = r.groupValues[2].toIntOrNull() ?: return@flatMap emptyList<Int>()
                    if (a <= b) (a..b).toList() else emptyList()
                } else {
                    listOfNotNull(t.toIntOrNull())
                }
            }.filter { it >= 1 }
        }
    }

    /** T8: sleepyCfNtss 标记 + kcmc/zc 字段 = 100; 仅标记 = 70 */
    override fun confidence(): Int {
        if (!source.contains("sleepyCfNtss")) return 0
        val hasFields = source.contains("kcmc") && source.contains("\"zc\"")
        return if (hasFields) 100 else 70
    }

    override fun matchedFeatures(): List<String> {
        if (!source.contains("sleepyCfNtss")) return emptyList()
        return buildList {
            add("sleepyCfNtss")
            if (source.contains("weeks")) add("weeks")
            if (source.contains("kcmc")) add("字段=kcmc")
            if (source.contains("teaxms")) add("字段=teaxms")
            if (source.contains("jxcdmc")) add("字段=jxcdmc")
            if (source.contains("\"ps\"") && source.contains("\"pe\"")) add("字段=ps/pe")
        }
    }
}
