package com.lingion.sleepy.data.jw

import org.json.JSONArray

/** Optional source-declared period defaults for the editable import confirmation screen. */
object JwSourcePeriods {
    fun resolve(source: String, type: String?, schoolName: String,
        captured: List<Triple<Int, String, String>>): List<Triple<Int, String, String>> {
        if (captured.isNotEmpty()) return captured
        val times = when (type) {
            JwProtocol.TYPE_GDBH -> listOf("08:25-09:10", "09:20-10:05", "10:25-11:10", "11:20-12:05",
                "15:00-15:45", "15:55-16:40", "17:00-17:45", "17:55-18:40", "19:40-20:25", "20:35-21:20")
            JwProtocol.TYPE_FSTVC -> {
                if (schoolName != "福州软件职业技术学院") return emptyList()
                val classes = runCatching {
                    val rows = JSONArray(source)
                    (0 until rows.length()).mapNotNull { (rows.optJSONObject(it)?.opt("skbjmc") as? String)?.takeIf(String::isNotBlank) }
                }.getOrDefault(emptyList())
                if (classes.isEmpty()) return emptyList()
                val batches = classes.map { name -> firstBatchMajors.any(name::contains) }.distinct()
                if (batches.size != 1) return emptyList()
                val morning = if (batches.single()) listOf("08:25-09:10", "09:15-10:00", "10:10-10:55", "11:00-11:45")
                    else listOf("08:45-09:30", "09:35-10:20", "10:30-11:15", "11:20-12:05")
                morning + listOf("13:50-14:35", "14:40-15:25", "15:35-16:20", "16:25-17:10",
                    "18:30-19:15", "19:25-20:10", "20:20-21:05", "21:15-22:00")
            }
            else -> return emptyList()
        }
        return times.mapIndexed { index, time -> Triple(index + 1, time.substringBefore('-'), time.substringAfter('-')) }
    }

    private val firstBatchMajors = listOf("软件技术", "移动互联应用技术", "信息安全技术应用", "智能产品开发与应用",
        "人工智能技术应用", "建设工程管理", "建筑室内设计", "工程造价", "建筑智能化工程技术", "智能建造技术",
        "现代通信技术", "现代移动通信技术", "智能互联网络技术", "网络规划与优化技术", "大数据技术", "区块链技术应用",
        "云计算技术应用", "工业软件开发技术")
}
