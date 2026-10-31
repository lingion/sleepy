package com.lingion.sleepy.console.ui

import android.app.Activity
import android.app.Application
import com.lingion.sleepy.console.ActivityTracker
import com.lingion.sleepy.console.handlers.UiOpsHandlers

/**
 * ui-ops 的可注入接缝 — [UiOpsHandlers]（纯 JVM）只认这个接口，真机走
 * [AndroidUiRuntime]（桥 [TagRegistry]/[Gestures]/[ScreenCapture]/[ActivityTracker]），
 * 单测走 FakeRuntime。handler 不碰 Android 类型，四端点语义全在 JVM 锁死。
 *
 * 方法面与 handlers 一一对照；手势类返回 false = 派发超时/异常/无活动，
 * 调用方 handler 报 500，不无声吞掉（SPEC §3 红线）。
 */
interface UiRuntime {
    /** 当前语义节点快照（[TagRegistry.snapshot] 或测试桩）。 */
    fun snapshotNodes(): List<UiNode>
    fun activityName(): String?
    fun focusTag(): String?
    /** 可见文本哈希（指纹的"内容变了没"维度）。 */
    fun visibleTextHash(): String
    fun tap(node: UiNode?, x: Int, y: Int, longPressMs: Long?, doubleTap: Boolean): Boolean
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean
    fun scroll(direction: String, amount: Int, node: UiNode?): Boolean
    fun inputText(text: String, node: UiNode?): Boolean
    fun systemKey(key: String, keyCode: Int): Boolean
    fun screenshotPng(): ByteArray?
}

/**
 * 真机运行时 — 手势与截图都要求"当前有 resumed Activity"（[ActivityTracker]）；
 * 无活动 = null 目标 → 手势 false、截图 null，handler 层转 500。
 */
class AndroidUiRuntime : UiRuntime {

    private val activity: Activity? get() = ActivityTracker.currentActivity()

    override fun snapshotNodes(): List<UiNode> = TagRegistry.snapshot()

    override fun activityName(): String? = activity?.javaClass?.simpleName

    override fun focusTag(): String? = TagRegistry.focusTag()

    override fun visibleTextHash(): String =
        textHashOf(snapshotNodes().mapNotNull { it.text })

    override fun tap(node: UiNode?, x: Int, y: Int, longPressMs: Long?, doubleTap: Boolean): Boolean {
        val act = activity ?: return false
        return Gestures(act).tap(node, x, y, longPressMs, doubleTap)
    }

    override fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean {
        val act = activity ?: return false
        return Gestures(act).swipe(x1, y1, x2, y2, durationMs)
    }

    override fun scroll(direction: String, amount: Int, node: UiNode?): Boolean {
        val act = activity ?: return false
        return Gestures(act).scroll(direction, amount, node)
    }

    override fun inputText(text: String, node: UiNode?): Boolean {
        val act = activity ?: return false
        return Gestures(act).inputText(text, node)
    }

    override fun systemKey(key: String, keyCode: Int): Boolean {
        val act = activity ?: return false
        return Gestures(act).systemKey(key, keyCode)
    }

    override fun screenshotPng(): ByteArray? = activity?.let { ScreenCapture.capture(it) }
}

/**
 * 装配点（仿 [com.lingion.sleepy.console.handlers.DataOpsRuntime.install]）：
 * ConsoleBootstrap.start() 调 [install] 即把四端点接上真机运行时。
 * release 物理不含调用方即不激活（SPEC 红线 1）。
 */
object UiOpsRuntime {

    private var injected: UiRuntime? = null

    /** 测试/后门注入位（JVM 侧不需要真 Activity）。 */
    fun use(runtime: UiRuntime?) {
        injected = runtime
    }

    fun runtime(): UiRuntime = injected ?: AndroidUiRuntime()

    fun install(app: Application) {
        ActivityTracker.register(app)
        UiOpsHandlers.registerAll(::runtime)
    }
}
