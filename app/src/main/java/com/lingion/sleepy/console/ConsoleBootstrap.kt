package com.lingion.sleepy.console

import android.app.Application
import android.util.Log
import com.lingion.sleepy.BuildConfig
import com.lingion.sleepy.console.handlers.ClockHandler
import com.lingion.sleepy.console.handlers.EnableFlow
import com.lingion.sleepy.console.handlers.JwMockHandler
import com.lingion.sleepy.console.handlers.LogHandler
import com.lingion.sleepy.console.handlers.SessionHandler
import com.lingion.sleepy.console.handlers.UnlockTapCounter
import com.lingion.sleepy.console.handlers.DataOpsRuntime
import com.lingion.sleepy.console.ui.UiOpsRuntime
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * console 装配入口 — 唯一知道“开关态 → 服务器生命周期”映射的地方。
 *
 * debug 包：由 debug 源集 DebugConsoleProvider 调 [onCreate]，无条件启动全能力。
 * release 包（Task 7 完整流程）：默认物理不监听端口（SPEC §5.1 红线 1）；
 * AboutScreen 连点版本号 7 次 → [armForUnlock] 只绑"等待 enable"服务
 * （仅 /v1/hello + /v1/enable 可达 — AuthGate 在 DebugGate.isActive=false 时
 * 拒一切其他路径）；口令对 → [finishReleaseUnlock] 补注册全部 handler 并把
 * 能力面按解锁态降级（catalog/trigger/jw-mock 各自 403 红线）。一键失活 =
 * [stop]（停服 + 清 token + DebugGate.invalidate，红线 3 全程零持久化）。
 *
 * 扩展点（控制器裁决）：[registerDefaultHandlers] 定义 main 侧默认端点，
 * [addHandler] 暴露注册通道给 debug 源集（DebugConsoleProvider 在 start 之前注册
 * debug-only 端点），main 编译期零引用 debug。
 */
object ConsoleBootstrap {

    private const val TAG = "SleepyConsole"

    private val started = AtomicBoolean(false)
    private var server: DebugConsoleServer? = null

    @Volatile
    private var appRef: Application? = null

    /** debug 源集 StartupProvider 入口；release 连点 armed 后才经 [armForUnlock] 绑端口。 */
    fun onCreate(app: Application) {
        if (BuildConfig.DEBUG) start(app) else rememberApp(app)
    }

    /** release: SleepyApp.onCreate 直调, 只存 Application 引用(不碰端口/不激活任何门位)。 */
    fun rememberApp(app: Application) {
        if (appRef == null) appRef = app
    }

    /** 幂等启动（debug 全能力路径）：注册全部端点 → 装配运行时 → 起服务器 → 激活。 */
    fun start(app: Application) {
        if (!started.compareAndSet(false, true)) return
        try {
            appRef = app
            bindAwaitingServer(app)
            registerAllCapabilities(app)
            DebugGate.activate(debugBuild = BuildConfig.DEBUG, releaseUnlocked = DebugGate.isReleaseUnlocked)
        } catch (t: Throwable) {
            // SPEC §9.4：绑定失败必须在启动日志显式报错，且不留下半活状态
            started.set(false)
            server = null
            Log.e(TAG, "debug console failed to bind :${DebugConsoleServer.PORT}", t)
        }
    }

    /**
     * release 连点 7 次后调用：只绑端口 + 注册免 token 的 hello/enable —
     * 其他路径此刻物理 401（AuthGate 真值表），"等待解锁"不扩大攻击面。
     * 幂等；重复 arm 不重绑。
     */
    fun armForUnlock(app: Application) {
        if (appRef == null) appRef = app
        if (started.get()) return
        if (!started.compareAndSet(false, true)) return
        try {
            bindAwaitingServer(app)
        } catch (t: Throwable) {
            started.set(false)
            server = null
            Log.e(TAG, "release unlock-await bind failed :${DebugConsoleServer.PORT}", t)
        }
    }

    /**
     * release 解锁成功后的能力面补注册（enable handler 内调）— 与 debug
     * [start] 同一集合，唯一差异 = DebugGate.debugBuild=false，红线 2/降级面
     * 由各自 handler 内部按 debugBuild 判定（catalog 写 403 / trigger 未注册
     * 物理 404 / jw-mock 403）。
     */
    fun finishReleaseUnlock() {
        appRef?.let { registerAllCapabilities(it) }
    }

    private fun bindAwaitingServer(app: Application) {
        TokenStore.reset(passphrase = BuildConfig.CONSOLE_ENABLE_PASSPHRASE)
        // debug 包装配即签发(零口令路径): SPEC §3"debug 跳过此步"= 跳口令与连点
        // arm, 不跳 token 本身 — AuthGate 真值表对两种构建一视同仁(§5.2 无例外)。
        // release arm 路径物理不进入本分支, 口令解锁仍走 EnableFlow→finishReleaseUnlock。
        if (BuildConfig.DEBUG) TokenStore.grantNewToken()
        registerDefaultHandlers()
        val s = DebugConsoleServer()
        s.start()
        server = s
        Log.i(TAG, "console listening on 127.0.0.1:${DebugConsoleServer.PORT} (debug=${BuildConfig.DEBUG})")
    }

    private fun registerAllCapabilities(app: Application) {
        ClockHandler.registerAll()
        SessionHandler.registerAll(app)
        LogHandler.registerAll()
        JwMockHandler.registerAll(app)
        DataOpsRuntime.install(app)
        UiOpsRuntime.install(app)
        Log.i(TAG, "debug console listening on 127.0.0.1:${DebugConsoleServer.PORT}")
    }

    /** 失活（红条一键 / 测试复位）：停服 + 清 token + 门位归零，红线 3。 */
    fun stop() {
        if (!started.compareAndSet(true, false)) return
        runCatching { server?.stop() }
        server = null
        DebugGate.invalidate()
        TokenStore.revoke()
        UnlockTapCounter.reset()
        CommandRouter.reset()
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
                    ",\"active\":${DebugGate.isActive}" +
                    // debug 装配已自动签发 → 调用方直接可取(token 红线: 只回内存态现值)
                    ",\"token\":${TokenStore.issue()?.let { "\"$it\"" } ?: "null"}}"
            )
        }
        addHandler("/v1/enable", "POST", requiresToken = false) { req ->
            // Task 7 完整流程：EnableFlow 五分支状态机（JVM 锁于 ReleaseGateUnitTest）。
            // debug 构建短路 ALREADY_ACTIVE — 口令不参与任何 debug 侧分支(红线 4)。
            val passphrase = try {
                JSONObject(req.body).getString("passphrase")
            } catch (_: Exception) {
                return@addHandler Envelope.err(400, "body requires {\"passphrase\":...}")
            }
            when (EnableFlow.attempt(passphrase)) {
                EnableFlow.Result.ALREADY_ACTIVE -> Envelope.ok("{\"result\":\"ALREADY_ACTIVE\"}")
                EnableFlow.Result.OK -> {
                    finishReleaseUnlock()
                    UnlockTapCounter.reset()
                    val token = TokenStore.issue()
                    val expires = TokenStore.expiresAtMs()
                    Envelope.ok(
                        "{\"result\":\"OK\"" +
                            (if (token != null) ",\"token\":\"$token\"" else "") +
                            (if (expires != null) ",\"expiresAt\":$expires" else "") + "}"
                    )
                }
                EnableFlow.Result.WRONG_PASSPHRASE -> Envelope.ok("{\"result\":\"WRONG_PASSPHRASE\"}")
                EnableFlow.Result.BACKOFF -> Envelope.ok("{\"result\":\"BACKOFF\"}")
                EnableFlow.Result.NOT_ARMED -> Envelope.ok("{\"result\":\"NOT_ARMED\"}")
            }
        }
    }
}
