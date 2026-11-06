package com.lingion.sleepy.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * Application 级内存日志环（Task 8, SPEC §/v1/log）：
 * logcat 之外的"尾部 N 条"可读面 — console worker 写入与 UI/后台线程写入
 * 全走 [record], 环形上限 200（与 AuditLog 同容量口径, 超尾自动淘汰最旧）。
 *
 * 设计取舍：不劫持 android.util.Log（保持 logcat 原样, 零侵入）,
 * 业务点经 [AppLog.i]/[AppLog.w]/[AppLog.e] 双写 = logcat + 内存环;
 * console 侧 LogHandler 读快照。锁 = 单监视器, record 频次低(里程碑级), 无争用。
 */
object AppLog {

    const val CAPACITY = 200

    data class Entry(val level: String, val tag: String, val message: String, val epochMs: Long)

    private val ring = ArrayDeque<Entry>()

    @Synchronized
    fun record(level: String, tag: String, message: String, epochMs: Long = System.currentTimeMillis()) {
        if (ring.size >= CAPACITY) ring.removeFirst()
        ring.addLast(Entry(level, tag, message, epochMs))
    }

    fun i(tag: String, message: String) {
        android.util.Log.i(tag, message)
        record("I", tag, message)
    }

    fun w(tag: String, message: String) {
        android.util.Log.w(tag, message)
        record("W", tag, message)
    }

    fun e(tag: String, message: String, t: Throwable? = null) {
        if (t != null) android.util.Log.e(tag, message, t) else android.util.Log.e(tag, message)
        record("E", tag, if (t != null) "$message :: ${t::class.simpleName}: ${t.message}" else message)
    }

    /** 尾部快照（时间正序）。limit 取后 N 条, null 全量。 */
    @Synchronized
    fun snapshot(limit: Int? = null): List<Entry> =
        (if (limit != null && limit in 1 until ring.size) ring.toList().takeLast(limit) else ring.toList())

    @Synchronized
    fun clear() = ring.clear()

    fun json(entries: List<Entry> = snapshot()): String {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("level", e.level)
                    .put("tag", e.tag)
                    .put("message", e.message)
                    .put("epochMs", e.epochMs)
            )
        }
        return arr.toString()
    }
}
