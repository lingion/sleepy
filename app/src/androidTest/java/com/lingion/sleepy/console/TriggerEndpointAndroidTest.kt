package com.lingion.sleepy.console

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.debug.DebugTriggers
import com.lingion.sleepy.debug.console.TriggerHandler
import com.lingion.sleepy.util.AppClock
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/**
 * Task 5 Step 1：注入 addSoonestCourse(delayMin=2) 后, 课表里出现一节
 * "虚拟现在+2分钟"上课的课 —— DB 态断言（真机通知到达归 SPEC Success
 * Criteria 2 人工验收）。端点面走 TriggerHandler.handle 直调（不起 HTTP,
 * 与 receiver 共享同一 DebugTriggers 实现 = 本测试同时钉死两条入口不分叉）。
 *
 * 目标包 debug 变体; 收尾删表删偏移, 不留残余。
 */
@RunWith(AndroidJUnit4::class)
class TriggerEndpointAndroidTest {

    private val tag = "trig-${System.nanoTime()}"
    private lateinit var app: Application

    @Before
    fun setUp() {
        app = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as Application
        AppClock.reset()
    }

    @After
    fun tearDown() = runBlocking {
        val repo = SleepyApp.get().repository
        repo.getAllTables().filter { it.name.contains(tag) || it.name == "流体云触发测试" }
            .forEach { runCatching { repo.deleteTable(it.id) } }
        AppClock.reset()
    }

    @Test
    fun addSoonestCourse_viaHandler_landsCourseAtVirtualNowPlusDelay() = runBlocking {
        // 虚拟时间拨到 08:00 整, delayMin=2 → 课应落在 08:02（真实时间无关）。
        // atTime(8,0) 派生的 epoch 本就是整秒(上海偏移为整小时, nanos=0), 无需再钉。
        // 守卫断言必须截纳秒: AppClock 是 ticking clock(System.currentTimeMillis+offset),
        // setAbsolute 与读取之间真实时钟必 tick 出亚毫秒~毫秒级正偏移 —— 设备实跑
        // 2026-10-30 取证 expected:<08:00> but was:<08:00:00.001>。亚秒抖动不影响
        // DB 面断言: DebugTriggers 用 String.format("%02d:%02d") 截到分钟。
        val base = LocalTime.of(8, 0)
        val virtual = java.time.LocalDate.now(AppClock).atTime(base)
            .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        AppClock.setAbsolute(virtual)
        assertEquals("守卫: 锚定秒必须生效(截亚秒抖动)", base, LocalTime.now(AppClock).withNano(0))

        val resp = JSONObject(TriggerHandler.handle(app,
            """{"type":"add_soonest_course","delayMin":2,"courseName":"$tag 课","room":"$tag 楼"}"""))
        assertTrue("端点必须成功: $resp", resp.getBoolean("ok"))
        val result = resp.getJSONObject("data").getString("result")
        val tableId = result.substringAfter("tableId=").substringBefore(" ").toLong()

        val repo = SleepyApp.get().repository
        val table = repo.getAllTables().firstOrNull { it.id == tableId }
        assertNotNull("触发必须真的建表", table)
        val course = repo.getCourses(tableId).firstOrNull { it.courseName.contains(tag) }
        assertNotNull("课必须落库", course)
        assertEquals("ownTime 课必须钉在虚拟现在+delay", "08:02", course!!.startTime)
        assertEquals("08:47", course.endTime)
        assertTrue("course 必须 ownTime", course.ownTime)

        // 六能力名直调别名与语义别名都分派到同一实现 — type 校验负例
        val bad = JSONObject(TriggerHandler.handle(app, """{"type":"warp_drive"}"""))
        assertTrue("未知 type 必须 400", !bad.getBoolean("ok") && bad.getInt("code") == 400)
    }

    @Test
    fun debugTriggers_objectIsSingleImplementation_receiverAliasPaths() = runBlocking {
        // delete_non_default_tables 型走同一 DebugTriggers — 空跑必须成功且默认表存活
        val repo = SleepyApp.get().repository
        val defaultId = repo.getAllTables().firstOrNull { it.isDefault }?.id
        val resp = JSONObject(TriggerHandler.handle(app, """{"type":"delete_non_default_tables"}"""))
        assertTrue(resp.getBoolean("ok"))
        val still = repo.getAllTables().firstOrNull { it.isDefault }
        assertEquals("默认表必须存活", defaultId, still?.id)
        assertTrue("非默认表必须清零", repo.getAllTables().none { !it.isDefault })
    }
}
