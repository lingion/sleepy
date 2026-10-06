package com.lingion.sleepy.data.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 本地日历兜底源码级契约 — 无 Google 服务/未登录设备的导入链命脉。
 * Robolectric 避免 (同 VendorLiveCardRendererExtrasTest 风格), 锁住
 * "CALLER_IS_SYNCADAPTER 插入 + writableCalendars 兜底接入" 这条结构不被误删。
 */
class LocalCalendarFallbackContractTest {

    private val source: String
        get() {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                val f = File(dir, "app/src/main/java/com/lingion/sleepy/data/calendar/SystemCalendarManager.kt")
                if (f.isFile) return f.readText()
                dir = dir.parentFile
            }
            error("SystemCalendarManager source not found")
        }

    @Test
    fun `ensureLocalCalendar uses sync-adapter uri for insert`() {
        val s = source
        assertTrue(s.contains("fun ensureLocalCalendar"))
        assertTrue("必须用 CALLER_IS_SYNCADAPTER 参数 (否则普通插表被 CalendarProvider 拒绝)",
            s.contains("CalendarContract.CALLER_IS_SYNCADAPTER"))
        assertTrue(s.contains("ACCOUNT_TYPE_LOCAL"))
        assertTrue("账户名稳定为包名, 幂等查询靠它",
            s.contains("\${context.packageName}.account"))
    }

    @Test
    fun `writableCalendars falls back to local calendar when empty`() {
        val s = source
        val idx = s.indexOf("fun writableCalendars")
        val body = s.substring(idx, s.indexOf("fun buildPreview", idx))
        assertTrue("列表为空时必须兜底 ensureLocalCalendar",
            body.contains("ensureLocalCalendar(context)"))
        assertTrue("有权限是前置条件, 权限拒绝时不动日历 provider",
            body.contains("hasCalendarPermissions"))
    }

    @Test
    fun `ensureLocalCalendar 插入时 SYNC_EVENTS=0 — 阻止系统 sync 错误通知污染通知栏`() {
        val s = source
        // 锁 SYNC_EVENTS 必须是 0: Sleepy 不是真 sync adapter, 设 1 会让 SyncManager
        // 周期性尝试 sync → 失败 → 在通知栏弹"同步错误", 污染 Sleepy 自身通知流。
        assertTrue("ensureLocalCalendar 必须用 SYNC_EVENTS=0 (不是 sync adapter, 不应触发 sync)",
            s.contains("put(CalendarContract.Calendars.SYNC_EVENTS, 0)"))
        // 函数体内不允许出现 SYNC_EVENTS, 1 (防止某次重构改回 1)
        val idx = s.indexOf("fun ensureLocalCalendar")
        assertTrue("ensureLocalCalendar 必须存在", idx > 0)
        val endIdx = s.indexOf("\n    }\n", idx).let { if (it < 0) s.length else it }
        val body = s.substring(idx, endIdx)
        assertFalse(
            "ensureLocalCalendar 函数体内不应出现 SYNC_EVENTS, 1 (会触发系统 sync 错误通知)",
            body.contains("SYNC_EVENTS, 1")
        )
    }
}
