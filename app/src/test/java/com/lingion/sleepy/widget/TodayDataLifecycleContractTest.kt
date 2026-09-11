package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/**
 * Today widget 数据/生命周期契约 (segment 3: data/lifecycle 修复锁)。
 *
 * 仓库无 Robolectric: Android 侧 (Context/prefs/WorkManager) 用源码级守卫
 * (先例 [[TodayDateNavWiringTest]] 源码守卫段), 纯函数用直接 JVM 断言。
 *
 * 锁四个修复:
 * - D2 [TodayDateNavStore] 读改写串行化 (双 tap lost-update 竞态)
 * - D3 [WidgetTableResolver] 单遍课表解析 (禁重复物化课程列表)
 * - D4 [WidgetUpdater] 午夜/跨天触发链 (15-min periodic 之外的对齐兜底)
 * - D5 [TodayWidget] loadDataSync 单次 now 贯穿 (跨午夜渲染口径一致)
 */
class TodayDataLifecycleContractTest {

    private fun mainSource(rel: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/com/lingion/sleepy/$rel")
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$rel not found")
    }

    private fun widgetSource(name: String): File = mainSource("widget/$name")

    // ---- D2: nav store 读改写串行化 ----

    @Test
    fun `nav store shift is synchronized read-modify-write`() {
        val src = widgetSource("TodayDateNavStore.kt").readText()
        val shiftBody = src.substringAfter("fun shift(").substringBefore("fun remove(")
        assertTrue(
            "shift 的 read→compute→clear+rewrite 全程必须在锁内 (双 tap 派发两广播并发" +
                "执行, 无锁丢一次推进)",
            shiftBody.contains("synchronized(")
        )
    }

    @Test
    fun `nav store remove shares the same write lock`() {
        // remove 与 shift 同一 clear()+rewrite-all 写全量路径, 并发交错同样丢数据
        val src = widgetSource("TodayDateNavStore.kt").readText()
        val removeBody = src.substringAfter("fun remove(").substringBefore("private fun read(")
        assertTrue(
            "remove 的写全量路径必须与 shift 同锁",
            removeBody.contains("synchronized(")
        )
    }

    // ---- D3: 课表解析单遍 ----

    @Test
    fun `resolveCurrentTable materializes each course list at most once`() {
        val src = widgetSource("WidgetTableResolver.kt").readText()
        val body = src.substringAfter("suspend fun resolveCurrentTable(")
            .substringBefore("resolveBoundTable")
        val materializations = Regex("repo\\.getCourses").findAll(body).count()
        assertTrue(
            "resolveCurrentTable 每次解析最多 1 个课程列表读取点 (当前 $materializations 处," +
                "默认表检查 + maxByOrNull + takeIf 重复物化 2-3x/表)",
            materializations in 1..1
        )
        assertTrue(
            "解析仍须容忍单表课程读取失败 (runCatching 语义保留)",
            body.contains("runCatching")
        )
        // 策略不变: 默认表优先, 次选课程数最多 (注释契约, 源码级锚)
        assertTrue(body.contains("isDefault"))
    }

    // ---- D4: 午夜/跨天触发链 ----

    @Test
    fun `nextMidnightDelayMillis computes wall-clock distance to next local midnight`() {
        // 23:59:30 → 30s
        assertEquals(
            30_000L,
            WidgetUpdater.nextMidnightDelayMillis(LocalDateTime.of(2026, 9, 10, 23, 59, 30))
        )
        // 午夜整点 → 距"下一个"午夜整 24h (恒 >0, 不会 0ms 连环触发)
        assertEquals(
            86_400_000L,
            WidgetUpdater.nextMidnightDelayMillis(LocalDateTime.of(2026, 9, 10, 0, 0, 0))
        )
        // 13:45 → 10h15m
        assertEquals(
            36_900_000L,
            WidgetUpdater.nextMidnightDelayMillis(LocalDateTime.of(2026, 3, 8, 13, 45, 0))
        )
    }

    @Test
    fun `nextMidnightDelayMillis is always positive`() {
        // 全天逐时采样, 恒 (0, 24h]
        for (hour in 0..23) {
            val delay = WidgetUpdater.nextMidnightDelayMillis(
                LocalDateTime.of(2026, 9, 10, hour, 0, 0)
            )
            assertTrue("hour=$hour delay=$delay 必须 >0", delay > 0)
            assertTrue("hour=$hour delay=$delay 必须 ≤24h", delay <= 86_400_000L)
        }
    }

    @Test
    fun `WidgetUpdater arms a midnight one-shot refresh chain`() {
        val src = widgetSource("WidgetUpdater.kt").readText()
        assertTrue(
            "须有午夜单次刷新任务 (跨 00:00 后 15-min periodic 长期显示昨天)",
            src.contains("OneTimeWorkRequestBuilder")
        )
        assertTrue(
            "午夜任务必须唯一名 REPLACE 重排 (幂等, 每次刷新重新对齐下一晚)",
            src.contains("ExistingWorkPolicy.REPLACE")
        )
        assertTrue(
            "notifyDataChanged 完成广播后必须重新对齐下一晚 (自续链: 午夜 worker 本身也走" +
                "notifyDataChanged → 触发下一晚排程)",
            Regex("suspend fun notifyDataChanged\\([\\s\\S]*armMidnightRefresh")
                .containsMatchIn(src)
        )
        val worker = widgetSource("WidgetUpdateWorker.kt").readText()
        assertTrue(
            "午夜 worker 复用 notifyDataChanged (doWork → 链自续, 不需第二个 worker 类)",
            worker.contains("WidgetUpdater.notifyDataChanged")
        )
    }

    // ---- D5: loadDataSync 单次 now ----

    @Test
    fun `loadDataSync computes now once and threads it through`() {
        val src = widgetSource("TodayWidget.kt").readText()
        val syncBody = src.substringAfter("fun loadDataSync(")
            .substringBefore("fun loadDataForDate(")
        assertTrue(
            "loadDataSync 必须先取一次 now (val now = LocalDate.now())",
            syncBody.contains("val now = LocalDate.now()")
        )
        val nowCalls = Regex("LocalDate\\.now\\(\\)").findAll(syncBody).count()
        assertTrue(
            "loadDataSync 里 LocalDate.now() 只允许出现 1 次 (跨午夜渲染 nav target 与" +
                "isToday 口径必须一致, 当前 $nowCalls 次)",
            nowCalls == 1
        )
        assertTrue(
            "单次 now 必须同时贯穿 nav target 解析与 loadDataForDate",
            syncBody.contains("target(context, appWidgetId, now)") &&
                syncBody.contains(", now)")
        )
    }

    @Test
    fun `loadDataForDate no longer calls now internally`() {
        val src = widgetSource("TodayWidget.kt").readText()
        val body = src.substringAfter("fun loadDataForDate(")
        assertFalse(
            "loadDataForDate 内部禁再取第二次 now (由调用方注入, 缺省参数仅供旧调用方兼容)",
            body.contains("val today = LocalDate.now()")
        )
    }
}
