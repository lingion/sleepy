package com.lingion.sleepy.util

import android.content.Context
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseColorEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 持久色相只读缓存（spec §2.1/§2.3 渲染层）— course_colors 全表 gid→hue 的进程级快照。
 *
 * 渲染入口拿 `hueFor(groupId)` 作 persistedHue 注入四入口:
 *   - CLASSIC 档或查无 → null → pickCourseColor* 走 v1.0.61 stableHue 老路径（零变化）;
 *   - 派生档命中 → 表值（认亲/重算写入的色相）。
 *
 * 刷新源（全部 IO, 渲染叶子同步读快照零开销）:
 *   ① 应用启动 [start] 首载 + AppPrefs.changeBus 的 mode/seed/theme 键;
 *   ② 写侧显式 [invalidate]（导入补色/重算, 事务提交后调用）;
 *   ③ widget 装载函数在 runBlocking 内 [refresh]（小组件进程与 app 同进程,
 *      但刷新可能滞后于渲染, 装载点自刷保证同帧一致）。
 *
 * 失败语义: 表读失败 → 空快照 → 全线回落 stableHue。配色是增值数据,
 * 任何异常不得影响课程渲染主流程（同 CourseColorWriteGlue 红线）。
 */
object CourseHueStore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _hueMap = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Compose 叶子用 collectAsState 订阅; 快照变化即重组（如模式切换后回非 CLASSIC）。 */
    val hueMapFlow = _hueMap.asStateFlow()

    private val _invalidateSignal = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val invalidateFlow = _invalidateSignal.asSharedFlow()

    @Volatile
    private var started = false

    /** 同步取持久 hue（CLASSIC/查无 → null）。 */
    fun hueFor(groupId: String): Float? = _hueMap.value[groupId]

    /** 幂等启动 — SleepyApp.onCreate 调一次。 */
    fun start(ctx: Context) {
        if (started) return
        synchronized(this) { if (started) return; started = true }
        val appCtx = ctx.applicationContext
        scope.launch { runCatching { refresh(appCtx) } }
        AppPrefs.changeBus
            .filter {
                it == AppPrefs.KEY_COURSE_COLOR_MODE || it == AppPrefs.KEY_COURSE_COLOR_SEED_HUE ||
                    it == AppPrefs.KEY_THEME || it == AppPrefs.KEY_THEME_MODE
            }
            .onEach { runCatching { refresh(appCtx) } }
            .launchIn(scope)
        invalidateFlow
            .onEach { runCatching { refresh(appCtx) } }
            .launchIn(scope)
    }

    /** 写侧（补色/重算）事务提交后调用 — 触发后台重读。 */
    fun invalidate() {
        _invalidateSignal.tryEmit(Unit)
    }

    /**
     * 直读 course_colors 并更新快照。CLASSIC 档 → 空快照（红线: 渲染零变化,
     * 模式切换钩子(step-5)在切出 CLASSIC 时经 invalidate 重灌）。
     */
    suspend fun refresh(ctx: Context) {
        val mode = AppPrefs.getCourseColorMode(ctx)
        val map = if (mode == AppPrefs.COURSE_COLOR_MODE_CLASSIC) {
            emptyMap()
        } else {
            runCatching {
                AppDatabase.get(ctx).courseColorDao()
                    .getHueMap(CourseColorEntity.GLOBAL_TABLE_KEY)
            }.getOrElse { emptyMap() }
        }
        _hueMap.value = map
    }
}
