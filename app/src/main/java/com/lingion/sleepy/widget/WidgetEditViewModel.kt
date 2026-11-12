package com.lingion.sleepy.widget

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Read model for a single widget instance's edit screen.
 *
 * - [currentBinding]: `null` means "follow the app-wide default table"
 *   (i.e. [WidgetTableResolver.resolveCurrentTable]); a Long means the
 *   user has explicitly picked this schedule for this widget.
 * - [availableTables]: schedules with at least one course (binding to an
 *   empty table would render the "请先创建课表" state).
 */
data class WidgetEditUiState(
    val currentBinding: Long? = null,
    val availableTables: List<TimeTableEntity> = emptyList(),
    /** issue#26: widget 场景 课程名显示 原名/别名(全局一档, 全部小组件共享) */
    val useAlias: Boolean = false,
    /** 设计 §6: 本实例 receiver simpleName (getAppWidgetInfo().provider 解析); null=未知 */
    val receiverSimpleName: String? = null,
    /** 强制滚动(实验) — 本实例一档 (2026-09-14: per-widget, 不再全局共享) */
    val scrollEnabled: Boolean = false,
    /** 最小档三天窗口 — 本实例一档 (2026-09-15: per-widget; true=今日居第一位) */
    val compactTodayFirst: Boolean = true,
    val wideGridSettings: WeekGridWideSettings = WeekGridWideSettings(),
)

/**
 * Per-widget edit ViewModel. All non-trivial logic is delegated to
 * [WidgetEditCore] (pure-JVM, unit-tested in [WidgetEditCoreTest]).
 *
 * Context is resolved lazily via [SleepyApp.get] so the VM can be
 * instantiated from a Compose `remember { WidgetEditViewModel(id) }`
 * without an explicit Context parameter at the call site — same shape as
 * other VMs in this codebase.
 */
class WidgetEditViewModel(
    val widgetId: Int
) : ViewModel() {

    private val ctx: Context get() = SleepyApp.get()
    private val repo get() = SleepyApp.get().repository

    private val _state = MutableStateFlow(WidgetEditUiState())
    val state: StateFlow<WidgetEditUiState> = _state.asStateFlow()

    init {
        reload()
    }

    /** Re-read binding + non-empty tables from store and repo. */
    fun reload() {
        viewModelScope.launch {
            val all = repo.getAllTables()
            // repo.countCourses is suspend, but WidgetEditCore.filterAvailableTables
            // takes a plain (Long) -> Int so it stays pure-JVM testable. Pre-compute
            // the counts map here so the core lambda is non-suspending.
            val counts: Map<Long, Int> = all.associate { tableId ->
                tableId.id to runCatching { repo.countCourses(tableId.id) }.getOrDefault(0)
            }
            val available = WidgetEditCore.filterAvailableTables(all) { counts[it] ?: 0 }
            // Sentinel (0L) means "follow default" — translated to null so the
            // UI shows the "Default" row as selected (same as no binding).
            val raw = WidgetBindingStore.get(ctx, widgetId)
            _state.value = WidgetEditUiState(
                currentBinding = WidgetEditCore.displayBinding(raw),
                availableTables = available,
                useAlias = AppPrefs.isWidgetUseAlias(ctx),
                receiverSimpleName = resolveReceiverSimpleName(),
                scrollEnabled = WidgetScrollStore.isScrollEnabled(ctx, widgetId),
                compactTodayFirst = WidgetCompactWindowStore.isTodayFirst(ctx, widgetId),
                wideGridSettings = WeekGridWideSettingsStore.read(ctx, widgetId),
            )
        }
    }

    /**
     * 设计 §6: 解析本实例的 receiver simpleName。
     * 首选 AppWidgetInfo.provider (绑定即有, 就是 receiver 本体);
     * configure 仅作兜底 (本 app 未声明 android:configure, 恒 null — 2026-09-15 修正,
     * 此前只读 configure 导致族判断永远走"未知"分支)。
     * 实例已删除/厂商异常 → null (编辑页仍可用, 仅按族显隐的节按未知=隐藏)。
     */
    private fun resolveReceiverSimpleName(): String? = runCatching {
        val awm = android.appwidget.AppWidgetManager.getInstance(ctx)
        val info = awm.getAppWidgetInfo(widgetId)
        (info?.provider?.className ?: info?.configure?.className)
            ?.substringAfterLast('.')
    }.getOrNull()

    /**
     * 设计 §6: 切换强制滚动(实验)。本实例一档 (per-widget, 写 WidgetScrollStore),
     * 写后 reload + 全量重推 (评审 #25: 否则「看着没生效」) — 与 setUseAlias 同管线。
     */
    fun setWideGridLabelSide(side: WeekGridWideLabelSide) {
        setWideGridSettings(WeekGridWideSettingsStore.read(ctx, widgetId).copy(labelSide = side))
    }

    private fun setWideGridSettings(settings: WeekGridWideSettings) {
        WeekGridWideSettingsStore.write(ctx, widgetId, settings)
        _state.value = _state.value.copy(wideGridSettings = settings)
        viewModelScope.launch { runCatching { WidgetUpdater.notifyDataChanged(ctx) } }
    }

    fun setScrollEnabled(v: Boolean) {
        WidgetScrollStore.setScrollEnabled(ctx, widgetId, v)
        reload()
        viewModelScope.launch {
            runCatching { WidgetUpdater.notifyDataChanged(ctx) }
        }
    }

    /**
     * 2026-09-15 用户令: 三天窗口档位 (今日居第一位/第二位)。本实例一档,
     * 写 WidgetCompactWindowStore 后 reload + 全量重推 — 与 setScrollEnabled 同管线。
     */
    fun setCompactTodayFirst(v: Boolean) {
        WidgetCompactWindowStore.setTodayFirst(ctx, widgetId, v)
        reload()
        viewModelScope.launch {
            runCatching { WidgetUpdater.notifyDataChanged(ctx) }
        }
    }

    /**
     * issue#26: 切换 widget 场景 课程名显示(原名/别名)。全局一档(所有小组件共享),
     * 写 AppPrefs 后 reload + 全量刷 widget — 与 setBinding 同管线。
     */
    fun setUseAlias(v: Boolean) {
        AppPrefs.setWidgetUseAlias(ctx, v)
        reload()
        viewModelScope.launch {
            runCatching { WidgetUpdater.notifyDataChanged(ctx) }
        }
    }

    /**
     * Persist a binding change. `null` clears the binding so the widget
     * falls back to [WidgetTableResolver.resolveCurrentTable]. After
     * writing, ask [WidgetUpdater] to refresh every widget receiver so the
     * change is reflected on the home screen immediately.
     */
    fun setBinding(tableId: Long?) {
        if (tableId == null) WidgetBindingStore.remove(ctx, widgetId)
        else WidgetBindingStore.put(ctx, widgetId, tableId)
        reload()
        viewModelScope.launch {
            runCatching { WidgetUpdater.notifyDataChanged(ctx) }
        }
    }
}
