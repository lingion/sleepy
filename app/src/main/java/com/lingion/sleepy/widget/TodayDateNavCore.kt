package com.lingion.sleepy.widget

/**
 * 每日课程小组件日期导航的纯 JVM 状态核心。
 *
 * 状态语义: 每个 appWidgetId 存 (selectedEpochDay, anchorEpochDay)。
 * - anchor == 今天的 epochDay → 用户处于导航态, 显示 selected
 * - anchor 已跨天(stale) → 显示今天 (R5: 到新的一天回到今天)
 * 偏移钳制在 ±[MAX_ABS_OFFSET_DAYS] 天(约 ±13 周) — 防止用户连点箭头把 epochDay
 * 推到荒谬位置; 也保证 `currentWeek(startDate, target)` 落到合理 week 范围。
 *
 * 与 [[WidgetBindingCore]] 同模式: key 形状 + Map 操作在本文件; SharedPreferences
 * 读写细节在 [[TodayDateNavStore]]。所有非平凡逻辑可纯 JVM 单测 (无 Robolectric)。
 */
internal object TodayDateNavCore {

    /** SharedPreferences 文件名 — 与 [TodayDateNavStore] 同步。 */
    const val PREFS_NAME: String = "widget_today_nav"

    /** 选中期 epochDay 的 key 前缀。 */
    const val KEY_SEL_PREFIX: String = "today_nav_sel_"

    /** 锚点期 epochDay 的 key 前缀 (即最近一次用户点击箭头时「今天」的 epochDay)。 */
    const val KEY_ANCHOR_PREFIX: String = "today_nav_anchor_"

    /** 选中相对今天的最大偏移天数 (前/后对称) — 约一学期浏览范围。 */
    const val MAX_ABS_OFFSET_DAYS: Long = 92L

    data class Entry(val selectedEpochDay: Long, val anchorEpochDay: Long)

    fun keySel(widgetId: Int): String = "$KEY_SEL_PREFIX$widgetId"
    fun keyAnchor(widgetId: Int): String = "$KEY_ANCHOR_PREFIX$widgetId"

    /**
     * 把 raw prefs (key -> Any?, 即 SharedPreferences.all 的原貌) 解析为
     * `widgetId -> Entry`。忽略非 Long 值、非自家前缀、非数字 id。
     * 只有 sel + anchor 两个 key 都存在时才算合法 entry; 缺一个视为无效 (读侧容错)。
     */
    fun parseAll(raw: Map<String, Any?>): Map<Int, Entry> {
        val tmp = HashMap<Int, Long>(8)
        val anchorTmp = HashMap<Int, Long>(8)
        for ((k, v) in raw) {
            if (v !is Long) continue
            when {
                k.startsWith(KEY_SEL_PREFIX) -> {
                    val id = k.removePrefix(KEY_SEL_PREFIX).toIntOrNull() ?: continue
                    tmp[id] = v
                }
                k.startsWith(KEY_ANCHOR_PREFIX) -> {
                    val id = k.removePrefix(KEY_ANCHOR_PREFIX).toIntOrNull() ?: continue
                    anchorTmp[id] = v
                }
            }
        }
        val out = HashMap<Int, Entry>(minOf(tmp.size, anchorTmp.size))
        for ((id, sel) in tmp) {
            val anchor = anchorTmp[id] ?: continue
            out[id] = Entry(sel, anchor)
        }
        return out
    }

    /**
     * 决定实际渲染的日期 epochDay。
     * 无 entry → today; entry 锚点失效(锚 != today)→ today (R5 跨天回今天);
     * 锚点有效 → 返回 selected。
     */
    fun resolveTargetEpochDay(entry: Entry?, todayEpochDay: Long): Long =
        if (entry != null && entry.anchorEpochDay == todayEpochDay) entry.selectedEpochDay else todayEpochDay

    /**
     * 从当前 entry + today 出发, 沿 deltaDays 推进 (正向未来 / 负向过去)。
     * 锚点失效的 entry 视为「无状态」, 以 today 为基准。
     * 偏移钳制在 ±[MAX_ABS_OFFSET_DAYS] 内; 锚点一律刷新为 today。
     */
    fun shift(entry: Entry?, todayEpochDay: Long, deltaDays: Long): Entry {
        val base = resolveTargetEpochDay(entry, todayEpochDay)
        val target = (base - todayEpochDay + deltaDays).coerceIn(-MAX_ABS_OFFSET_DAYS, MAX_ABS_OFFSET_DAYS)
        return Entry(selectedEpochDay = todayEpochDay + target, anchorEpochDay = todayEpochDay)
    }

    /** 写 raw prefs (Store facade 使用, 测试也可直接断言 key 形状)。 */
    fun write(raw: MutableMap<String, Long>, widgetId: Int, entry: Entry) {
        raw[keySel(widgetId)] = entry.selectedEpochDay
        raw[keyAnchor(widgetId)] = entry.anchorEpochDay
    }

    /** 读 raw prefs; sel 或 anchor 缺失返回 null (Entry 完整性自我约束)。 */
    fun read(raw: Map<String, Long>, widgetId: Int): Entry? {
        val sel = raw[keySel(widgetId)] ?: return null
        val anchor = raw[keyAnchor(widgetId)] ?: return null
        return Entry(sel, anchor)
    }

    /** 删 raw prefs 中该 widget 的两条 key (R4 隔离: 只动自己的 entry)。 */
    fun delete(raw: MutableMap<String, Long>, widgetId: Int) {
        raw.remove(keySel(widgetId))
        raw.remove(keyAnchor(widgetId))
    }
}
