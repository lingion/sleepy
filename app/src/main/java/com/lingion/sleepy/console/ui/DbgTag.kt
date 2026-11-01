package com.lingion.sleepy.console.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * ui-ops 语义定位的注册表 — tag → 节点快照（含实时窗口 bounds 与 View 弱引用）。
 *
 * 设计（SPEC §4 "Compose 语义定位靠补全的 Modifier.testTag + 遍历"）：
 *  - [dbgTag] 在 onPlaced 里把 (tag, bounds, View) 写进本表，节点离开组合时注销；
 *  - View 引用为弱引用（泄漏防线：注册表绝不能钉死已销毁的 View/Activity）；
 *  - bounds 用窗口坐标（boundsInWindow），手势派发与 hitTest 直接用；
 *  - 读侧无锁（ConcurrentHashMap）：HTTP worker 线程随时查，主线程放置时写。
 */
object TagRegistry {

    class Entry(
        val text: String?,
        val role: String?,
        val className: String?,
        val clickable: Boolean,
        val viewRef: WeakReference<View>,
        @Volatile var left: Int = 0,
        @Volatile var top: Int = 0,
        @Volatile var right: Int = 0,
        @Volatile var bottom: Int = 0,
        @Volatile var placement: Long = 0L,
    )

    private val entries = ConcurrentHashMap<String, MutableList<Entry>>()

    fun put(tag: String, entry: Entry) {
        entries.compute(tag) { _, list ->
            (list ?: mutableListOf()).apply {
                // 同 (view,text) 视为同节点重放置：替换旧位，防一 tag 内陈旧副本堆积
                val old = indexOfFirst { it.viewRef.get() === entry.viewRef.get() && it.text == entry.text }
                if (old >= 0) removeAt(old)
                add(entry)
            }
        }
    }

    /** dbgTag 注销：只删"自己那一条"（同 tag 兄弟节点共存，多个 dbgTag 同串合法）。 */
    fun removeEntry(tag: String, view: View, text: String?) {
        entries.compute(tag) { _, list ->
            list ?: return@compute null
            list.removeAll { it.viewRef.get() === view && it.text == text }
            if (list.isEmpty()) null else list
        }
    }

    /** 快照（tree/find 的节点源）。tag 字典序内保持放置序，整体 order 稳定。 */
    fun snapshot(): List<UiNode> {
        val out = ArrayList<UiNode>()
        for (tag in entries.keys.sorted()) {
            val list = entries[tag] ?: continue
            for (e in list.sortedBy { it.placement }) {
                val v = e.viewRef.get() ?: continue
                val reachable = v.isShown && v.windowToken != null
                out += UiNode(
                    tag = tag,
                    text = e.text,
                    className = e.className ?: v.javaClass.simpleName,
                    role = e.role,
                    clickable = e.clickable,
                    reachable = reachable,
                    bounds = Bounds(e.left, e.top, e.right, e.bottom),
                )
            }
        }
        return out.mapIndexed { i, n -> n.copy(order = i) }
    }

    /** 按命中节点找回它的可点击 View（performClick 语义优先通路）。 */
    fun clickableOf(node: UiNode): View? {
        val tag = node.tag ?: return null
        val list = entries[tag] ?: return null
        val match = list.firstOrNull {
            it.text == node.text && it.left == node.bounds.left && it.top == node.bounds.top
        } ?: list.firstOrNull()
        val v = match?.viewRef?.get() ?: return null
        return if (v.isClickable || v.hasOnClickListeners()) v else null
    }

    /** 焦点语义：当前组合里最后一个"可点击且被 hover/pressed"的近似不可得，取主线程 View 焦点。 */
    fun focusTag(): String? {
        for (tag in entries.keys) {
            val list = entries[tag] ?: continue
            for (e in list) {
                val v = e.viewRef.get() ?: continue
                if (v.isFocused) return tag
            }
        }
        return null
    }

    fun clear() = entries.clear()
}

/**
 * dbgTag — 交互控件的调试面语义身份 = testTag（Compose 测试/语义树可查）
 * + onPlaced（窗口 bounds 实时上报 [TagRegistry]）一体。
 *
 * 用法：`Modifier.dbgTag("settings_theme")`；带文本身份的多实例节点传 text
 * （同 tag 不同 text = 同端点的不同课程格等，find 可用 text 区分）。
 * 命名规范（Task 8 skill 文档同步）：屏前缀_控件语义。
 */
fun Modifier.dbgTag(
    tag: String,
    text: String? = null,
    role: String? = null,
    className: String? = null,
    clickable: Boolean = true,
): Modifier = composed {
    val view = LocalView.current
    DisposableEffect(tag, view, text) {
        onDispose { TagRegistry.removeEntry(tag, view, text) }
    }
    this
        .testTag(tag)
        .onPlaced { coords ->
            val b = coords.boundsInWindow()
            TagRegistry.put(
                tag,
                TagRegistry.Entry(
                    text = text,
                    role = role,
                    className = className,
                    clickable = clickable,
                    viewRef = WeakReference(view),
                    left = b.left.toInt(),
                    top = b.top.toInt(),
                    right = b.right.toInt(),
                    bottom = b.bottom.toInt(),
                    placement = System.nanoTime(),
                ),
            )
        }
}
