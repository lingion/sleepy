package com.lingion.sleepy.widget.notification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 提醒重排枢纽 (参照 shiguang SyncManager 防抖模式)。
 *
 * 问题: 编辑课表连续保存 / 提醒设置连点开关时, 旧链路每次都直接
 * scheduleAll() (cancelAll + 重排 7 天闹钟), 高频全量重排既浪费又在
 * cancel/重排间隙留下"无闹钟"窗口。
 *
 * 契约: request() 幂等触发; 静默 [debounceMs] 后才执行一次 action() —
 * 窗口内任意次 request 合并为一次。实现为「取消旧延迟任务 + 重排」,
 * 无 SharedFlow 订阅竞态 (发射早于收集器会丢事件)。
 *
 * action 由 [init] 注入 (生产 = notificationScheduler::scheduleAll);
 * Receiver/Boot 等冷启动路径继续直调 scheduleAll(), 不经过本枢纽。
 */
object ReminderRescheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null
    private var action: (suspend () -> Unit)? = null
    private var debounceMs: Long = 300L

    /** 注册重排动作与防抖窗口; 重复调用覆盖 (幂等键为调用方语义, 仅 app 启动调用一次)。
     *  替换 action 时取消 in-flight pending Job, 防止旧 action 在新 action 已替换后仍执行一次
     *  (极端时序: 旧 action 已过 delay 进入 runCatching → init 替换 → 旧 action 仍跑)。
     *  生产路径只调一次 (SleepyApp.onCreate), 此 guard 主要是测试场景的兜底。
     */
    @Synchronized
    fun init(debounceMs: Long = 300L, action: suspend () -> Unit) {
        this.debounceMs = debounceMs
        this.action = action
        pending?.cancel()
        pending = null
    }

    /** 请求一次重排; 静默窗口内的后续请求并入同一批。init 前调用静默丢弃。 */
    @Synchronized
    fun request() {
        val a = action ?: return
        pending?.cancel()
        val wait = debounceMs
        pending = scope.launch {
            delay(wait)
            runCatching { a() }
        }
    }

    /** 测试钩子 — 取消在途任务并复位。 */
    @Synchronized
    internal fun resetForTest() {
        pending?.cancel()
        pending = null
        action = null
    }
}
