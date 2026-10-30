package com.lingion.sleepy.console

import android.app.Application
import android.util.Log
import com.lingion.sleepy.BuildConfig
import com.lingion.sleepy.console.handlers.ClockHandler
import com.lingion.sleepy.console.handlers.DataOpsRuntime
import com.lingion.sleepy.console.ui.UiOpsRuntime
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * console 装配入口 — 唯一知道“开关态 → 服务器生命周期”映射的地方。
 *
 * debug 包：由 debug 源集 DebugConsoleProvider 调 [onCreate]，无条件启动。
 * release 包：默认物理不启动（SPEC §5.1）；隐藏开关解锁流程属 Task 7，
 * 届时解锁成功才调 [start]。Task 1 的 /v1/enable 只做 TokenStore 挂壳。
 *
 * 扩展点（控制器裁决）：[registerDefaultHandlers] 定义 main 侧默认端点，
 * [addHandler] 暴露注册通道给 debug 源集（DebugConsoleProvider 在 start 之前注册
 * debug-only 端点），main 编译期零引用 debug。
 */
object ConsoleBootstrap {

    private const val TAG = "SleepyConsole"

    private val started = AtomicBoolean(false)
    private var server: DebugConsoleServer? = null

    /** debug 源集 StartupProvider 入口；release 解锁后才允许调 [start]。 */
    fun onCreate(app: Application) {
        if (BuildConfig.DEBUG) start(app)
    }

    /** 幂等启动：注册默认端点 → 装配 data-ops → 起服务器 → DebugGate 激活（debugBuild 取编译期常量）。 */
    fun start(app: Application) {
        if (!started.compareAndSet(false, true)) return
        try {
            TokenStore.reset(passphrase = BuildConfig.CONSOLE_ENABLE_PASSPHRASE)
            registerDefaultHandlers()
            ClockHandler.registerAll()
            DataOpsRuntime.install(app)
            UiOpsRuntime.install(app)
            val s = DebugConsoleServer()
            s.start()
            server = s
            DebugGate.activate(debugBuild = BuildConfig.DEBUG, releaseUnlocked = DebugGate.isReleaseUnlocked)
            Log.i(TAG, "debug console listening on 127.0.0.1:${DebugConsoleServer.PORT}")
        } catch (t: Throwable) {
            // SPEC §9.4：绑定失败必须在启动日志显式报错，且不留下半活状态
            started.set(false)
            server = null
            Log.e(TAG, "debug console failed to bind :${DebugConsoleServer.PORT}", t)
        }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        runCatching { server?.stop() }
        server = null
        DebugGate.invalidate()
        TokenStore.revoke()
    }

    /**
     * debug 侧注册 debug-only 端点的通道（必须在 [start] 前由 provider 调用）。
     * 后注册者覆盖同 path+method 的既有实现——debug-only 增强有权替换默认端点。
     */
    fun addHandler(
        path: String,
        method: String,
        requiresToken: Boolean,
        handler: suspend (ConsoleRequest) -> String,
    ) {
        CommandRouter.remove(path, method)
        CommandRouter.register(path, method, requiresToken, handler)
    }

    /**
     * 注册 main 侧默认端点：/v1/hello、/v1/enable（均免 token，见 SPEC §3）。
     * 走覆盖式注册（[addHandler]），装配重启/重复调用不炸——默认端点是本对象
     * 自己的实现，不存在“静默吞掉别人端点”的风险。
     */
    fun registerDefaultHandlers() {
        addHandler("/v1/hello", "POST", requiresToken = false) {
            val paths = CommandRouter.registeredPaths()
                .joinToString(",") { "\"$it\"" }
            Envelope.ok(
                "{\"paths\":[$paths]" +
                    ",\"debugBuild\":${DebugGate.debugBuild}" +
                    ",\"unlocked\":${DebugGate.isReleaseUnlocked}" +
                    ",\"active\":${DebugGate.isActive}}"
            )
        }
        addHandler("/v1/enable", "POST", requiresToken = false) { req ->
            // Task 1 挂壳：完整解锁流程（UI 连点 7 次 arm + 红条）是 Task 7 的活；
            // 这里只锁 TokenStore 纯逻辑通路。result 四态 = UnlockResult 枚举名。
            val passphrase = try {
                JSONObject(req.body).getString("passphrase")
            } catch (_: Exception) {
                return@addHandler Envelope.err(400, "body requires {\"passphrase\":...}")
            }
            when (TokenStore.attemptUnlock(passphrase)) {
                UnlockResult.OK -> {
                    val token = TokenStore.issue()
                    Envelope.ok(if (token != null) "{\"result\":\"OK\",\"token\":\"$token\"}" else "{\"result\":\"OK\"}")
                }
                UnlockResult.WRONG_PASSPHRASE -> Envelope.ok("{\"result\":\"WRONG_PASSPHRASE\"}")
                UnlockResult.BACKOFF -> Envelope.ok("{\"result\":\"BACKOFF\"}")
                UnlockResult.NOT_ARMED -> Envelope.ok("{\"result\":\"NOT_ARMED\"}")
            }
        }
    }
}
