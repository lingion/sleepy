package com.lingion.sleepy.util

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * 全 app 业务时间源 — java.time.Clock 实现, 真实 epoch 加纯内存偏移。
 *
 * 存在理由（Agent Debug Console SPEC §虚拟时钟）：调试台 /v1/clock 把"今天/现在/
 * 第几周"整体平移, 课表状态判定、闹钟计算、widget 刷新、日期格式化全部跟着走,
 * 且只改内存、不触碰系统时钟、不落盘 — 进程重启即回真实时间。
 *
 * 使用纪律（契约测试 ClockContractScanTest 物理把关）：main 源码里
 * LocalDate.now() / LocalTime.now() / LocalDateTime.now() 无参裸调用 = 违例,
 * 一律写 now(AppClock)。System.currentTimeMillis() 的业务判定处收敛 millis()。
 *
 * 线程模型：offsetMs volatile 单写多读; 短暂读到旧偏移仅影响一帧渲染,
 * 无数据完整性风险（调试台语义）。
 */
object AppClock : Clock() {

    /** 业务统一时区 — 与 DateUtils/ScheduleParser 既有 Asia/Shanghai 口径一致。 */
    private val businessZone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Volatile
    var offsetMs: Long = 0L
        private set

    override fun withZone(zone: ZoneId): Clock = ZoneShiftingClock(this, zone)

    override fun getZone(): ZoneId = businessZone

    override fun instant(): Instant = Instant.ofEpochMilli(millis())

    /** 虚拟化的 epoch 毫秒（= System.currentTimeMillis() + offset）。 */
    override fun millis(): Long = System.currentTimeMillis() + offsetMs

    /** 相对真实时间平移 deltaMs（可负, 可累加）。 */
    fun offsetBy(deltaMs: Long) {
        offsetMs += deltaMs
    }

    /** 虚拟"现在"钉到 epochMs（offset 重算 = epochMs - realNow）。 */
    fun setAbsolute(epochMs: Long) {
        offsetMs = epochMs - System.currentTimeMillis()
    }

    /** 回真实时间。 */
    fun reset() {
        offsetMs = 0L
    }

    /** withZone 派生视图：偏移共享源钟, 只换时区。 */
    private class ZoneShiftingClock(
        private val source: AppClock,
        private val zone: ZoneId
    ) : Clock() {
        override fun withZone(zone: ZoneId): Clock = ZoneShiftingClock(source, zone)
        override fun getZone(): ZoneId = zone
        override fun instant(): Instant = source.instant()
    }
}
