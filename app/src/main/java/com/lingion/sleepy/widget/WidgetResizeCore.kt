package com.lingion.sleepy.widget

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 今日系小组件 resize/导航推送的世代号核心 (Android-free, 纯 JVM)。
 *
 * 背景 (issue #24 顶栏导航 resize 稳定性): resize 拖拽期间系统连发 OPTIONS_CHANGED,
 * 每次都触发一次"后台渲染 → awm.updateAppWidget"。两个触发源的渲染任务可能乱序完成:
 * 旧尺寸的任务后完成 → 把新尺寸的推送覆盖回旧内容, 且不再有后续更新来纠正 (永久 stale)。
 *
 * 机制: 每个 push 触发点 (onUpdate / onAppWidgetOptionsChanged / nav 点击) 在开渲染前
 * bump 世代号并持有快照; 所有 commit 点 (updateAppWidget / notifyAppWidgetViewDataChanged)
 * 前校验"世代号没变" — 变了 = 渲染期间落了更新的触发 → 本结果作废, 由新触发的任务提交。
 * 最后一个触发永远不会被更老的完成者反超 → 收敛正确。
 *
 * 只影响性能与稳定性: 不改渲染、布局、点击区, 拖拽最终态照常提交。
 */
object WidgetResizeCore {

    /** 每个 widgetId 独立世代号 — 互不干扰, 进程内存活 (widget 推送进程即 app 主进程)。 */
    private val gens = ConcurrentHashMap<Int, AtomicLong>()

    /** bump 并返回快照值 — 每个 push 触发点开渲染前调用。 */
    fun bump(id: Int): Long = counter(id).incrementAndGet()

    /** 当前世代号 (无记录 → 0)。 */
    fun current(id: Int): Long = counter(id).get()

    /** 渲染/渲染后校验: 世代号已变 = 本结果 stale, 禁止 commit (新触发会提交最新状态)。 */
    fun isStale(id: Int, expectedGen: Long): Boolean = current(id) != expectedGen

    /** 实例删除时清理, 防跨实例 id 复用后误判 stale。 */
    fun remove(id: Int) {
        gens.remove(id)
    }

    private fun counter(id: Int) = gens.computeIfAbsent(id) { AtomicLong(0) }
}
