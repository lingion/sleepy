package com.lingion.sleepy.console

/**
 * 调试门进程态 — 全 console 的唯一开关真源。
 *
 * 语义（SPEC §5 红线 3）：解锁只在进程存活期有效，不写任何持久化；
 * 重启后本 object 回到全 false，release 路径端口物理不绑。
 *
 * 时钟走可注入 [forTestNow]，单测用假时钟锁 60s 解锁等待窗口。
 */
object DebugGate {

    /** UI 连点版本号 7 次后，“等待解锁”态的内存窗口（60s 过期，SPEC §3 /v1/enable）。 */
    const val UNLOCK_ARM_WINDOW_MS = 60_000L

    @Volatile
    var isActive: Boolean = false
        private set

    @Volatile
    var debugBuild: Boolean = false
        private set

    @Volatile
    var isReleaseUnlocked: Boolean = false
        private set

    /** 进入“等待解锁”态的时刻；null = 从未 arm 或已失效。 */
    @Volatile
    var unlockArmedAt: Long? = null
        private set

    private var now: () -> Long = System::currentTimeMillis

    /** arm 后是否仍在 60s 窗口内（判据 unlockArmedAt==null 或超窗 → false）。 */
    val isUnlockArmed: Boolean
        get() = unlockArmedAt?.let { now() - it <= UNLOCK_ARM_WINDOW_MS } == true

    fun activate(debugBuild: Boolean, releaseUnlocked: Boolean) {
        this.debugBuild = debugBuild
        this.isReleaseUnlocked = releaseUnlocked
        isActive = true
    }

    fun deactivate() {
        isActive = false
    }

    /** UI 侧连点版本号 7 次时调用（Task 7 接线；Task 1 只提供原语）。 */
    fun armUnlock() {
        unlockArmedAt = now()
    }

    /** 解锁成功或过期后清除等待态。 */
    fun clearArm() {
        unlockArmedAt = null
    }

    /** 一键立刻失活（SPEC §5.3；Task 7 UI 接线）。 */
    fun invalidate() {
        isActive = false
        isReleaseUnlocked = false
        unlockArmedAt = null
    }

    /** 仅测试/装配复位：注入假时钟并清空全部状态。 */
    fun reset(forTestNow: () -> Long = System::currentTimeMillis) {
        now = forTestNow
        isActive = false
        debugBuild = false
        isReleaseUnlocked = false
        unlockArmedAt = null
    }
}
