package com.lingion.sleepy.console

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.console.handlers.DataOpsRuntime
import com.lingion.sleepy.data.entity.TimeTableEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 2 data-ops 真 Room 集成（brief Step 4）：
 * 注入课程 → /v1/data/tables 读出含该课；设默认 → repository.getDefaultTable 变。
 *
 * 走真装配链：DataOpsRuntime.install(app) + CommandRouter.dispatch，不 mock。
 * 目标包是 debug 变体（applicationIdSuffix .debug 独立沙箱），收尾用 delete op
 * 清掉本用例创建的表，不留残余。
 */
@RunWith(AndroidJUnit4::class)
class DataOpsConsoleAndroidTest {

    private val tag = "at-${System.nanoTime()}"

    @Before
    fun setUp() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        CommandRouter.reset()
        ConsoleBootstrap.registerDefaultHandlers()
        DataOpsRuntime.install(app)
    }

    @Test
    fun injectCourseThenReadBackThroughTablesEndpoint() = runBlocking {
        val created = JSONObject(dispatch("/v1/data/table", "POST",
            """{"op":"create","name":"$tag","startDate":"2026-09-01"}"""))
        assertTrue(created.getBoolean("ok"))
        val tableId = created.getJSONObject("data").getLong("id")
        try {
            val inj = JSONObject(dispatch("/v1/data/course", "POST",
                """{"tableId":$tableId,"courses":[{"courseName":"$tag 高数","day":1,
                   "startNode":1,"step":2,"startWeek":1,"endWeek":16}]}"""))
            assertTrue(inj.getBoolean("ok"))
            assertEquals(1, inj.getJSONObject("data").getInt("inserted"))

            val read = JSONObject(dispatch("/v1/data/tables", "GET", ""))
            assertTrue(read.getBoolean("ok"))
            val tables = read.getJSONObject("data").getJSONArray("tables")
            val mine = (0 until tables.length())
                .map(tables::getJSONObject)
                .firstOrNull { it.getLong("id") == tableId }
            assertNotNull("注入的表必须能从 /v1/data/tables 读出", mine)
            val course = mine!!.getJSONArray("courses").getJSONObject(0)
            assertEquals("$tag 高数", course.getString("courseName"))
            assertTrue("真库认亲通路必须生成非空 groupId", course.getString("groupId").isNotBlank())
        } finally {
            JSONObject(dispatch("/v1/data/table", "POST", """{"op":"delete","id":$tableId}"""))
                .also { assertTrue("收尾删除必须成功", it.getBoolean("ok")) }
        }
    }

    @Test
    fun setDefaultViaConsole_changesRepositoryDefault() = runBlocking {
        val repo = SleepyApp.get().repository
        val originalDefault = repo.getDefaultTable()?.id
        val created = JSONObject(dispatch("/v1/data/table", "POST",
            """{"op":"create","name":"$tag","startDate":"2026-09-01","isDefault":false}"""))
        val tableId = created.getJSONObject("data").getLong("id")
        try {
            val sd = JSONObject(dispatch("/v1/data/table", "POST",
                """{"op":"setDefault","id":$tableId}"""))
            assertTrue(sd.getBoolean("ok"))
            assertEquals(tableId, repo.getDefaultTable()?.id)
        } finally {
            JSONObject(dispatch("/v1/data/table", "POST", """{"op":"delete","id":$tableId}"""))
            if (originalDefault != null) repo.setDefault(originalDefault)
        }
    }

    private suspend fun dispatch(path: String, method: String, body: String): String =
        CommandRouter.dispatch(path, method, ConsoleRequest(body = body))
}
