package com.lingion.sleepy.widget

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

/**
 * Android facade — [TodayDateNavCore] 的 SharedPreferences 读写壳。
 * 与 [[WidgetBindingStore]] 同模式 (raw prefs map 进出, 业务逻辑归 Core)。
 *
 * 调用方约定:
 * - [target] 只读 — resolve 当前该 widget 应渲染的日期; 不写 prefs
 * - [shift]  持久化前/后一天 (供 onReceive 钩子调用)
 * - [remove] 重置为「今天」(等价无状态; 走 prefs.delete 即归零)
 */
object TodayDateNavStore {

    /** 读取 prefs 解析出该 widget 应渲染的日期 (无状态 → today)。 */
    fun target(context: Context, widgetId: Int, today: LocalDate): LocalDate {
        val entry = read(context, widgetId)
        val epoch = TodayDateNavCore.resolveTargetEpochDay(entry, today.toEpochDay())
        return LocalDate.ofEpochDay(epoch)
    }

    /** 把日期推进 ±deltaDays (锚点刷新为 today); 钳制在 Core.MAX_ABS_OFFSET_DAYS 内。 */
    fun shift(context: Context, widgetId: Int, today: LocalDate, deltaDays: Long) {
        val entry = read(context, widgetId)
        val newEntry = TodayDateNavCore.shift(entry, today.toEpochDay(), deltaDays)
        val raw = loadAll(context).toMutableMap()
        TodayDateNavCore.write(raw, widgetId, newEntry)
        saveAll(context, raw)
    }

    /** 重置为「今天」 — 删除该 widget 的两条 prefs key (语义上等价「无导航态」)。 */
    fun remove(context: Context, widgetId: Int) {
        val raw = loadAll(context).toMutableMap()
        TodayDateNavCore.delete(raw, widgetId)
        saveAll(context, raw)
    }

    private fun read(context: Context, widgetId: Int): TodayDateNavCore.Entry? =
        TodayDateNavCore.read(loadAll(context), widgetId)

    private fun loadAll(context: Context): Map<String, Long> {
        val sp = context.getSharedPreferences(
            TodayDateNavCore.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        return sp.all.mapNotNull { (k, v) -> if (v is Long) k to v else null }.toMap()
    }

    private fun saveAll(context: Context, data: Map<String, Long>) {
        val sp = context.getSharedPreferences(
            TodayDateNavCore.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        sp.edit {
            clear()
            data.forEach { (k, v) -> putLong(k, v) }
        }
    }
}
