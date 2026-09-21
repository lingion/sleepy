package com.lingion.sleepy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 前一晚预告和当天提醒只有目标日期、触发时间不同；无课时两者都必须发送摘要。
 *
 * 这是纯 JVM 契约测试，避免通知接收器的 Android 依赖掩盖调度语义回归。
 */
class TomorrowReminderWiringContractTest {

    private fun schedulerSource(): String =
        File("src/main/java/com/lingion/sleepy/widget/notification/CourseNotificationScheduler.kt").readText()

    @Test
    fun `previous evening receiver targets tomorrow through shared summary path`() {
        val source = schedulerSource()
        val receiver = source.substringAfter("class TomorrowNotifyReceiver")
            .substringBefore("private suspend fun sendScheduleSummary")

        assertTrue("前一晚提醒必须从次日开始计算", receiver.contains("LocalDate.now().plusDays(1)"))
        assertTrue("前一晚提醒必须调用与当天相同的摘要生成函数", receiver.contains("sendScheduleSummary("))
        assertTrue("前一晚提醒必须标记为明日预告文案", receiver.contains("isTomorrowPreview = true"))
    }

    @Test
    fun `both reminder types publish a no course summary`() {
        val source = schedulerSource()

        assertTrue("当天提醒必须保留无课摘要文案", source.contains("R.string.notif_daily_title_no_course"))
        assertTrue("前一晚提醒必须有对应的无课摘要文案", source.contains("R.string.notif_tomorrow_title_no_course"))
        assertTrue(
            "前一晚无课摘要必须用明日专用正文,不能复用「享受一天」",
            source.contains("R.string.notif_tomorrow_text_no_course")
        )
        assertFalse(
            "前一晚提醒不能因次日无课而静默返回",
            source.contains("if (isTomorrowPreview && courses.isEmpty()) return")
        )
    }

    @Test
    fun `tomorrow reminder keeps an independent alarm and notification id`() {
        val source = schedulerSource()

        assertTrue("前一晚提醒需要独立 PendingIntent 请求码", source.contains("RC_TOMORROW_DAILY = 3"))
        assertTrue("前一晚提醒需要独立通知 ID，不能覆盖当天提醒", source.contains("NOTIFY_TOMORROW_DAILY = 1002"))
        assertTrue("关闭提醒时必须取消前一晚闹钟", source.contains("TomorrowNotifyReceiver::class.java"))
    }

    @Test
    fun `daily master gates both sub-reminders while old same-day behavior remains enabled`() {
        val source = schedulerSource()

        assertTrue("每日提醒总开关必须包住当天提醒调度", source.contains("if (AppPrefs.isDailyReminderEnabled(prefs))"))
        assertTrue("当天提醒需要独立子开关", source.contains("AppPrefs.isTodayReminderEnabled(prefs)"))
        assertTrue("前一晚提醒需要受每日提醒总开关控制", source.contains("!AppPrefs.isDailyReminderEnabled(context)"))
    }
}
