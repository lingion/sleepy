package com.lingion.sleepy.widget.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM 契约测试 — 锁定 FluidCloudService ACTION_STOP 路径的源码结构.
 * 项目无 Robolectric (app/build.gradle.kts:223-224 注释明确禁引入),
 * 读源码字符串等价于读编译产物 (先例: BackRestoreSaveableContractTest).
 *
 * 锁的不变量:
 *   1. ACTION_STOP 常量值 = "com.lingion.sleepy.action.FLUID_STOP"
 *   2. ACTION_STOP 与 ACTION_TEST 字面量不同 (防合并错误)
 *   3. onStartCommand 顶部存在 ACTION_STOP 分支, 在首个 return START_NOT_STICKY 前
 *   4. ACTION_STOP 分支体走齐 4 步停止语义:
 *      removeCallbacks(updater) + stopForeground + NotificationManagerCompat.from.cancel +
 *      stopSelf()
 */
class FluidCloudServiceActionStopContractTest {
    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/")
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    private val source: String by lazy {
        loadSource("widget/notification/FluidCloudService.kt")
    }

    @Test
    fun ACTION_STOP_constant_is_declared_with_exact_value() {
        assertTrue(
            "ACTION_STOP 常量必须存在且值为 com.lingion.sleepy.action.FLUID_STOP",
            source.contains(
                """const val ACTION_STOP = "com.lingion.sleepy.action.FLUID_STOP""""
            )
        )
    }

    @Test
    fun ACTION_STOP_compiles_and_exposes_distinct_value() {
        // 编译断言: ACTION_STOP 可被外部引用, 编译期锁定
        assertNotEquals(FluidCloudService.ACTION_STOP, FluidCloudService.ACTION_TEST)
        assertTrue(FluidCloudService.ACTION_STOP.isNotEmpty())
        assertEquals(
            "com.lingion.sleepy.action.FLUID_STOP",
            FluidCloudService.ACTION_STOP
        )
    }

    @Test
    fun onStartCommand_branches_on_ACTION_STOP_before_first_return() {
        val startCmdIdx = source.indexOf("override fun onStartCommand")
        assertTrue("onStartCommand 必须存在", startCmdIdx >= 0)
        val afterStart = source.substring(startCmdIdx)
        val firstReturn = afterStart.indexOf("return START_NOT_STICKY")
        assertTrue("必须至少有一个 return START_NOT_STICKY", firstReturn >= 0)
        val branch = afterStart.substring(0, firstReturn)
        assertTrue(
            "ACTION_STOP 分支必须在首个 return START_NOT_STICKY 前, 防死代码覆盖",
            branch.contains("ACTION_STOP")
        )
    }

    @Test
    fun ACTION_STOP_branch_cancels_notification_and_stops_service() {
        val startCmdIdx = source.indexOf("override fun onStartCommand")
        val afterStart = source.substring(startCmdIdx)
        val firstReturn = afterStart.indexOf("return START_NOT_STICKY")
        val branch = afterStart.substring(0, firstReturn)
        assertTrue(
            "ACTION_STOP 分支必须 handler.removeCallbacks(updater) 取消 15s 循环",
            branch.contains("removeCallbacks(updater)")
        )
        assertTrue(
            "ACTION_STOP 分支必须 stopForeground 解除前台服务",
            branch.contains("stopForeground")
        )
        assertTrue(
            "ACTION_STOP 分支必须 NotificationManagerCompat.from 拿通知管理器",
            branch.contains("NotificationManagerCompat.from")
        )
        assertTrue(
            "ACTION_STOP 分支必须 cancel(NOTIFY_BEFORE_CLASS_BASE) 取消流体云通知",
            branch.contains(
                "cancel(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE)"
            )
        )
        assertTrue(
            "ACTION_STOP 分支必须 stopSelf() Service 自停",
            branch.contains("stopSelf()")
        )
    }
}