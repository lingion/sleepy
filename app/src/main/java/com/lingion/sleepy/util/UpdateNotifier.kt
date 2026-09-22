package com.lingion.sleepy.util

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/**
 * 冷启动拉一次 GitHub Releases latest, 进程内缓存结果给 AboutScreen 和主导航提醒用。
 * - dismiss 按版本持久化; 新版本自然重新显示
 * - 同一进程内只查一次, 避免 AboutScreen 重进或多次启动 scope 拉 N 次
 * - 用户关闭更新检查时不发起请求, AboutScreen 也不显示高亮
 */
object UpdateNotifier {
    private val mutex = Mutex()
    private var inFlight: Job? = null

    private val _updateAvailable = MutableStateFlow<UpdateInfo?>(null)
    /** 远端有更新时非空; null = 没查到 / 没新版本 / 用户关闭了检查 */
    val updateAvailable: StateFlow<UpdateInfo?> = _updateAvailable.asStateFlow()

    private val _dismissedVersion = MutableStateFlow("")
    private val noticeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** 所有更新提醒 UI 共用的可见状态; dismiss 按版本记忆 */
    val noticeVisible: StateFlow<Boolean> = combine(_updateAvailable, _dismissedVersion) { update, dismissed ->
        UpdateNoticeCore.isVisible(update, dismissed)
    }.stateIn(noticeScope, SharingStarted.Eagerly, false)

    fun loadDismissedVersion(context: Context) {
        _dismissedVersion.value = AppPrefs.getUpdateNoticeDismissedVersion(context)
    }

    fun dismiss(version: String, context: Context) {
        if (version.isBlank()) return
        AppPrefs.setUpdateNoticeDismissedVersion(context, version)
        _dismissedVersion.value = version
    }

    /** Debug-only visual fixture; production callers must use [maybeCheckOnStart]. */
    fun showMockUpdate() {
        _updateAvailable.value = UpdateInfo(
            version = "1.0.58",
            changelog = "## Mock update\\n\\n- 新版本提醒预览\\n- 课程表显示优化",
            downloadUrl = "https://example.invalid/mock.apk",
            isUpdateAvailable = true
        )
    }

    /** MainActivity.onCreate 调用. lifecycleScope 取消时自动中断本次请求. */
    fun maybeCheckOnStart(context: Context, scope: CoroutineScope) {
        if (!AppPrefs.isUpdateCheckEnabled(context)) return
        scope.launch(SupervisorJob() + Dispatchers.IO) {
            // 同一进程只查一次, 后到的请求直接忽略
            val shouldStart = mutex.withLock {
                val busy = inFlight?.isActive == true || _updateAvailable.value != null
                !busy
            }
            if (!shouldStart) return@launch
            inFlight = coroutineContext[Job]
            runCatching {
                val info = UpdateManager.fetchUpdateInfo(context)
                if (info.isUpdateAvailable) _updateAvailable.value = info
            }
            // 失败静默: 用户没要求弹错; cache 保持 null
        }
    }

    /** 用户在「关于」底部 Toggle 关闭后清空缓存, 立即消失高亮 */
    fun clearCache() {
        _updateAvailable.value = null
        inFlight?.cancel()
        inFlight = null
    }
}
