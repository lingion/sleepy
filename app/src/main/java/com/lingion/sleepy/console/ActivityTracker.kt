package com.lingion.sleepy.console

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * 前台 Activity 追踪 — ui-ops 手势派发与截图的"当前窗口"来源。
 *
 * 经 [Application.registerActivityLifecycleCallbacks] 维护 resumed Activity 弱引用
 * （SPEC 红线 3 精神：不钉死生命周期对象）。console handler 全部只读这里。
 *
 * 注册时机：UiOpsRuntime.install(app) 在 ConsoleBootstrap.start 装配段调用，
 * 与 DataOpsRuntime 同层——main 侧装配，release 物理不含调用方即不激活。
 */
object ActivityTracker : Application.ActivityLifecycleCallbacks {

    @Volatile
    private var current: WeakReference<Activity>? = null

    /** 仅测试注入（JVM 侧不需要真 Activity）。 */
    @Volatile
    var override: Activity? = null

    fun register(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
    }

    fun currentActivity(): Activity? = override ?: current?.get()?.takeIf { !it.isFinishing }

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
