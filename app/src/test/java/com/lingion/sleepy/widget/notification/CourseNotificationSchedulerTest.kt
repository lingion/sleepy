package com.lingion.sleepy.widget.notification

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 7 天课前闹钟预排纯 JVM 单测 — 走注入端口 (BeforeClassAlarmPort/Env/DataSource),
 * 不依赖 Android 运行时。核心不变量:
 *  ① 窗口内未来的课 → 一节课一颗精确闹钟 (courseId 稳定 requestCode);
 *  ② 重复调用不产生重复槽位, 仍需要的槽位不经历"先撤后排"的空窗;
 *  ③ 学期范围外/已过触发点/开关关闭 → 零闹钟;
 *  ④ 上次排过、这次不再需要的 code(换表/删课/关开关)按持久化账本撤销。
 */
class CourseNotificationSchedulerTest {

    private val fixedToday: LocalDate = LocalDate.of(2026, 10, 7) // 星期三
    private val zone get() = ZoneId.systemDefault()

    private class FakeAlarmPort : BeforeClassAlarmPort {
        val armed = LinkedHashMap<Int, Long>()   // requestCode → epochMs (重复 set 覆盖, 语义同 PendingIntent)
        val cancels = mutableListOf<Int>()
        var setCount = 0
        override fun setExact(requestCode: Int, epochMs: Long, extras: Map<String, Any?>) {
            setCount++
            armed[requestCode] = epochMs
        }
        override fun cancel(requestCode: Int) {
            cancels += requestCode
            armed.remove(requestCode)
        }
    }

    private class FakeEnv(
        var enabled: Boolean = true,
        private val minutes: Int = 10,
        private val now: Long = System.currentTimeMillis(),
        private val today: LocalDate = LocalDate.of(2026, 10, 7),
    ) : BeforeClassEnv {
        var ledger: Set<Int> = emptySet()
        override fun isBeforeClassEnabled() = enabled
        override fun beforeClassMinutes() = minutes
        override fun nowEpochMs() = now
        override fun todayDate() = today
        override fun loadArmedCodes() = ledger
        override fun saveArmedCodes(codes: Set<Int>) { ledger = codes }
    }

    private class FakeDataSource(
        var table: TimeTableEntity?,
        var byDay: Map<Int, List<CourseEntity>>,
        private val dayMapping: (LocalDate) -> Int = { it.dayOfWeek.value },
        private val holidayDates: Set<LocalDate> = emptySet(),
    ) : BeforeClassDataSource {
        override suspend fun resolveCurrentTable() = table
        override suspend fun coursesForDay(tableId: Long, dayOfWeek: Int) = byDay[dayOfWeek].orEmpty()
        override suspend fun allCourseIds() = byDay.values.flatten().map { it.id }
        override fun effectiveDayOfWeek(tableId: Long?, date: LocalDate) = dayMapping(date)
        override fun isPublicHoliday(tableId: Long, date: LocalDate) = date in holidayDates
    }

    private fun table(
        startDate: LocalDate = fixedToday.minusDays(7),
        maxWeek: Int = 20,
        id: Long = 1L,
    ) = TimeTableEntity(id = id, name = "t", startDate = startDate.toString(), maxWeek = maxWeek)

    private val nineAm get() = fixedToday.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun course(
        id: Long,
        day: Int,
        startNode: Int,
        ownTime: Boolean = false,
        startTime: String = "",
    ) = CourseEntity(
        id = id, groupId = "g$id", tableId = 1L,
        courseName = "课$id", teacher = "师", room = "101",
        day = day, startNode = startNode, step = 1,
        startWeek = 1, endWeek = 20, type = 0,
        color = "#FF6750A4",
        ownTime = ownTime, startTime = startTime, endTime = "",
    )

    private fun scheduler(
        port: FakeAlarmPort,
        env: BeforeClassEnv,
        src: FakeDataSource,
    ) = CourseNotificationScheduler(port, env, src)

    @Test
    fun `窗口内未来的课 排一颗精确闹钟 epoch=开课减提前分钟`() = runBlocking {
        // 周三今天, 周五(day=5)第1节 08:00 → 2026-10-09 07:50
        val src = FakeDataSource(table(), mapOf(5 to listOf(course(7, day = 5, startNode = 1))))
        val port = FakeAlarmPort()
        val now = fixedToday.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        scheduler(port, FakeEnv(now = now), src).scheduleNext7DaysExactAlarms()

        val expected = fixedToday.plusDays(2).atTime(7, 50).atZone(zone).toInstant().toEpochMilli()
        assertEquals(mapOf(100 + 7 to expected), port.armed)
    }

    @Test
    fun `重复调用幂等 仍需要的槽位原地覆盖不撤销`() = runBlocking {
        val src = FakeDataSource(table(), mapOf(5 to listOf(course(7, day = 5, startNode = 1))))
        val port = FakeAlarmPort()
        val env = FakeEnv(now = nineAm)
        val s = scheduler(port, env, src)
        s.scheduleNext7DaysExactAlarms()
        val firstArmed = port.armed.toMap()
        port.cancels.clear(); port.setCount = 0

        s.scheduleNext7DaysExactAlarms()
        assertTrue("仍需要的 code 不能先撤再排, 中间窗口会丢闹钟", port.cancels.isEmpty())
        assertEquals(firstArmed, port.armed)
        assertEquals(1, port.setCount)
        assertEquals(setOf(100 + 7), env.ledger)
    }

    @Test
    fun `换课表 旧表闹钟按账本撤销`() = runBlocking {
        val src = FakeDataSource(table(), mapOf(5 to listOf(course(7, day = 5, startNode = 1))))
        val port = FakeAlarmPort()
        val env = FakeEnv(now = nineAm)
        val s = scheduler(port, env, src)
        s.scheduleNext7DaysExactAlarms()

        src.table = table(id = 2L)
        src.byDay = mapOf(4 to listOf(course(8, day = 4, startNode = 1)))
        s.scheduleNext7DaysExactAlarms()

        assertEquals(setOf(100 + 8), port.armed.keys)
        assertTrue(100 + 7 in port.cancels)
        assertEquals(setOf(100 + 8), env.ledger)
    }

    @Test
    fun `删课 已不在库里的课闹钟按账本撤销`() = runBlocking {
        val src = FakeDataSource(table(), mapOf(
            4 to listOf(course(5, day = 4, startNode = 1)),
            5 to listOf(course(7, day = 5, startNode = 1)),
        ))
        val port = FakeAlarmPort()
        val env = FakeEnv(now = nineAm)
        val s = scheduler(port, env, src)
        s.scheduleNext7DaysExactAlarms()
        assertEquals(setOf(100 + 5, 100 + 7), port.armed.keys)

        src.byDay = mapOf(5 to listOf(course(7, day = 5, startNode = 1)))
        s.scheduleNext7DaysExactAlarms()
        assertEquals(setOf(100 + 7), port.armed.keys)
        assertEquals(listOf(100 + 5), port.cancels)
    }

    @Test
    fun `关闭课前提醒 账本内闹钟全部撤销`() = runBlocking {
        val src = FakeDataSource(table(), mapOf(5 to listOf(course(7, day = 5, startNode = 1))))
        val port = FakeAlarmPort()
        val env = FakeEnv(now = nineAm)
        val s = scheduler(port, env, src)
        s.scheduleNext7DaysExactAlarms()

        env.enabled = false
        s.scheduleNext7DaysExactAlarms()
        assertTrue(port.armed.isEmpty())
        assertTrue(env.ledger.isEmpty())
    }

    @Test
    fun `当前课表消失 账本内闹钟全部撤销`() = runBlocking {
        val src = FakeDataSource(table(), mapOf(5 to listOf(course(7, day = 5, startNode = 1))))
        val port = FakeAlarmPort()
        val env = FakeEnv(now = nineAm)
        val s = scheduler(port, env, src)
        s.scheduleNext7DaysExactAlarms()

        src.table = null
        s.scheduleNext7DaysExactAlarms()
        assertTrue(port.armed.isEmpty())
        assertTrue(env.ledger.isEmpty())
    }

    @Test
    fun `调休映射让同一门课在窗口内出现两次 只排最早一次`() = runBlocking {
        // 周六(10-10)补周五的课 → 课 7 在 10-09 与 10-10 各出现一次
        val src = FakeDataSource(
            table(),
            mapOf(5 to listOf(course(7, day = 5, startNode = 1))),
            dayMapping = { if (it == fixedToday.plusDays(3)) 5 else it.dayOfWeek.value },
        )
        val port = FakeAlarmPort()
        scheduler(port, FakeEnv(now = nineAm), src).scheduleNext7DaysExactAlarms()
        val expected = fixedToday.plusDays(2).atTime(7, 50).atZone(zone).toInstant().toEpochMilli()
        assertEquals(mapOf(100 + 7 to expected), port.armed)
    }

    @Test
    fun `学期范围外 零闹钟`() = runBlocking {
        // 学期 30 天后才开始 → 窗口 7 天全在学期外
        val src = FakeDataSource(table(startDate = fixedToday.plusDays(30)), mapOf(5 to listOf(course(7, day = 5, startNode = 1))))
        val port = FakeAlarmPort()
        scheduler(port, FakeEnv(now = fixedToday.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()), src)
            .scheduleNext7DaysExactAlarms()
        assertTrue(port.armed.isEmpty())
    }

    @Test
    fun `已过触发点跳过 未来的同名周课不受影响`() = runBlocking {
        // 周三今天(day=3) 08:00 的课, 现在 09:00 → 今日已过点, 跳过
        // 若配置成未来日仍要排: day=5 周五同 id 结构体不同课程
        val src = FakeDataSource(table(), mapOf(
            3 to listOf(course(3, day = 3, startNode = 1)),
            5 to listOf(course(5, day = 5, startNode = 1)),
        ))
        val port = FakeAlarmPort()
        scheduler(port, FakeEnv(now = fixedToday.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()), src)
            .scheduleNext7DaysExactAlarms()
        assertEquals(listOf(100 + 5), port.armed.keys.toList())
    }

    @Test
    fun `开关关闭 零闹钟`() = runBlocking {
        val src = FakeDataSource(table(), mapOf(5 to listOf(course(7, day = 5, startNode = 1))))
        val port = FakeAlarmPort()
        scheduler(port, FakeEnv(enabled = false), src).scheduleNext7DaysExactAlarms()
        assertTrue(port.armed.isEmpty())
    }

    @Test
    fun `窗口内法定节假日跳过当天但保留后续课程`() = runBlocking {
        val holiday = fixedToday.plusDays(2)
        val src = FakeDataSource(
            table(),
            mapOf(
                5 to listOf(course(7, day = 5, startNode = 1)),
                6 to listOf(course(8, day = 6, startNode = 1)),
            ),
            holidayDates = setOf(holiday)
        )
        val port = FakeAlarmPort()
        scheduler(port, FakeEnv(now = nineAm), src).scheduleNext7DaysExactAlarms()
        val expected = fixedToday.plusDays(3).atTime(7, 50).atZone(zone).toInstant().toEpochMilli()
        assertEquals(mapOf(100 + 8 to expected), port.armed)
    }

    @Test
    fun `ownTime 课用自定义开始时间`() = runBlocking {
        val src = FakeDataSource(table(), mapOf(5 to listOf(course(9, day = 5, startNode = 1, ownTime = true, startTime = "15:30"))))
        val port = FakeAlarmPort()
        scheduler(port, FakeEnv(now = fixedToday.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()), src)
            .scheduleNext7DaysExactAlarms()
        val expected = fixedToday.plusDays(2).atTime(15, 20).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, port.armed[100 + 9])
    }

    @Test
    fun `破损时间 非法时 跳过不崩`() = runBlocking {
        val src = FakeDataSource(table(), mapOf(5 to listOf(course(9, day = 5, startNode = 1, ownTime = true, startTime = "25:99"))))
        val port = FakeAlarmPort()
        scheduler(port, FakeEnv(now = fixedToday.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()), src)
            .scheduleNext7DaysExactAlarms()
        assertTrue(port.armed.isEmpty())
    }
}
