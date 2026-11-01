package com.lingion.sleepy.console

/**
 * 一次已解析的控制面请求，交给 handler 的最小事实集。
 * 后续任务（data-ops / ui-ops / clock / trigger / mock）全部只经
 * [CommandRouter.register] 消费本类型，禁止绕过 router 直接接 socket。
 */
data class ConsoleRequest(
    val body: String = "",
    val query: Map<String, String> = emptyMap(),
    val params: Map<String, String> = emptyMap(),
)

/**
 * 进程内命令分发器 — bridge 的路由核心，也是后续 7 个任务的唯一挂载点。
 *
 * 扩展点裁决：debug 源集组件不能从 main 编译期引用，因此 main 只提供注册通道；
 * 默认端点由 `ConsoleBootstrap.registerDefaultHandlers()` 注册，debug 侧
 * DebugConsoleProvider 经 `ConsoleBootstrap.addHandler(...)` 在 start 之前补注册。
 *
 * 分发即出口统一：命中 → handler 返回值（约定为 [Envelope.ok] 串）；
 * 未知 path/方法 → 404；handler 崩溃 → 500（异常只暴露类名，不泄露细节）。
 * token 校验本身在服务器统一入口做（SPEC §5.2），本类只暴露
 * [requiresTokenFor] 查询该路由要不要 token。
 */
object CommandRouter {

    private class Route(val requiresToken: Boolean, val handler: suspend (ConsoleRequest) -> String)

    /**
     * path → (METHOD → route)。同一路径可挂多个方法（GET 读 / POST 写）。
     *
     * 两层都用 ConcurrentHashMap：写路径（register/remove/reset）另有 @Synchronized
     * 互斥，这里的并发保证是给 [dispatch] 的**无锁读**——NanoHTTPD worker 线程随时可能
     * 在读表，debug 源集 provider 又会在运行期补注册端点。LinkedHashMap 读与写并发
     * 是数据竞争（可见性不保证、极端下结构性撕裂）。CHM 的写对后续读立即可见
     * （volatile 语义），满足"运行期注册后 dispatch 立即可见"。
     */
    private val routes = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, Route>>()

    /**
     * 注册端点。同一 path+method 重复注册直接失败——端点撞名属装配 bug，
     * 静默覆盖会把两条任务的实现悄悄吞成一条。
     */
    @Synchronized
    fun register(
        path: String,
        method: String,
        requiresToken: Boolean,
        handler: suspend (ConsoleRequest) -> String,
    ) {
        val m = method.uppercase()
        val byMethod = routes.computeIfAbsent(path) { java.util.concurrent.ConcurrentHashMap() }
        check(!byMethod.containsKey(m)) { "duplicate route: $m $path" }
        byMethod[m] = Route(requiresToken, handler)
    }

    @Synchronized
    fun requiresTokenFor(path: String, method: String): Boolean =
        routes[path]?.get(method.uppercase())?.requiresToken ?: false

    /** 路由是否已存在（注册通道用它做幂等：重复装配不炸）。 */
    @Synchronized
    fun hasRoute(path: String, method: String): Boolean =
        routes[path]?.containsKey(method.uppercase()) == true

    /** 移除一条路由；[ConsoleBootstrap.addHandler] 用它实现“后注册者覆盖”。 */
    @Synchronized
    fun remove(path: String, method: String) {
        val byMethod = routes[path] ?: return
        byMethod.remove(method.uppercase())
        if (byMethod.isEmpty()) routes.remove(path)
    }

    /** 已注册能力清单（/v1/hello 用），按字典序稳定输出。 */
    @Synchronized
    fun registeredPaths(): List<String> = routes.keys.sorted()

    /** 分发一条已鉴权的请求；本方法绝不抛异常（崩溃收敛为 500 envelope）。 */
    suspend fun dispatch(path: String, method: String, req: ConsoleRequest): String {
        val handler = routes[path]?.get(method.uppercase())?.handler
            ?: return Envelope.err(404, "unknown command: $method $path")
        return try {
            handler(req)
        } catch (t: Throwable) {
            Envelope.err(500, "handler failure: ${t::class.simpleName}")
        }
    }

    /** 仅测试复位（进程内单例，单测之间必须互相隔离）。 */
    @Synchronized
    fun reset() {
        routes.clear()
    }
}
