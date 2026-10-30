package com.lingion.sleepy.debug.console

import android.app.Application
import com.lingion.sleepy.console.ConsoleBootstrap
import com.lingion.sleepy.console.Envelope
import com.lingion.sleepy.debug.DebugTriggers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * POST /v1/trigger — 事件触发端点（Task 5, SPEC §/v1/trigger）。
 *
 * 装配位置（preflight 裁决）：debug 源集。能力体 DebugTriggers 物理不存在于
 * release APK，release 解锁态调本端点 = 路由表没有该 path = 404（比 403 更强,
 * 红线 1"release 不含调试能力体"由编译期保证）。
 *
 * 语义：{type, ...payload} 分派到 DebugImportReceiver 的六能力, payload 对齐
 * 原 receiver extras 键名（courseName/room/startTime/teacher/delayMin）。
 * sync/import 型走 data-ops 已有通路, 此处 import_helper 保留 receiver 的
 * b64/path/clipboard 三源导入语义。
 */
object TriggerHandler {

    private val TYPES = setOf(
        "before_class_alarm", "fluid_cloud", "live_card", "notification",
        "sync", "import_helper",
        // 六能力名直调（功能最强: 语义别名）
        "restore_and_cleanup", "delete_non_default_tables", "clear_fluid_notification",
        "test_before_class", "schedule_before_class_now", "add_soonest_course",
    )

    fun registerAll(app: Application) {
        ConsoleBootstrap.addHandler("/v1/trigger", "POST", requiresToken = true) { req ->
            handle(app, req.body)
        }
    }

    /** 纯分派（androidTest 直调, 不起 HTTP）。 */
    fun handle(app: Application, body: String): String {
        val o = try {
            JSONObject(body)
        } catch (_: Exception) {
            return Envelope.err(400, "body must be a JSON object")
        }
        val type = o.optString("type")
        if (type !in TYPES) {
            return Envelope.err(400, "type must be one of ${TYPES.sorted()}")
        }
        fun str(key: String, default: String) = o.optString(key, default)
        return try {
            val summary = when (type) {
                // 语义别名层（SPEC §3 六 type）
                "before_class_alarm", "notification", "schedule_before_class_now" ->
                    runBlocking { DebugTriggers.scheduleBeforeClassNow(app) }
                "sync" ->
                    runBlocking { DebugTriggers.restoreAndCleanup(app) }
                "live_card" ->
                    DebugTriggers.clearFluidNotification(app)
                "fluid_cloud", "test_before_class" ->
                    DebugTriggers.testBeforeClass(
                        app,
                        courseName = str("courseName", "高等数学"),
                        startTime = str("startTime", "14:00"),
                        room = str("room", "A101"),
                        teacher = str("teacher", "张老师"),
                    )
                "restore_and_cleanup" ->
                    runBlocking { DebugTriggers.restoreAndCleanup(app) }
                "delete_non_default_tables" ->
                    runBlocking { DebugTriggers.deleteNonDefaultTables(app) }
                "clear_fluid_notification" ->
                    DebugTriggers.clearFluidNotification(app)
                "add_soonest_course" ->
                    runBlocking {
                        DebugTriggers.addSoonestCourse(
                            app,
                            delayMin = if (o.has("delayMin")) o.getInt("delayMin") else 5,
                            courseName = str("courseName", "流体云测试课"),
                            room = str("room", "A101"),
                        )
                    }
                "import_helper" -> importViaDataOps(app, o)
                else -> error("unreachable")
            }
            Envelope.ok(JSONObject().put("type", type).put("result", summary).toString())
        } catch (e: BadRequest) {
            Envelope.err(400, e.message ?: "bad trigger payload")
        } catch (t: Throwable) {
            Envelope.err(500, "trigger $type failed: ${t.message}")
        }
    }

    /** 参数级错误（区别于 500 执行失败）。 */
    class BadRequest(message: String) : Exception(message)

    /**
     * import_helper = receiver 的文本导入能力走 console 侧等价物：payload 支持
     * {text} 直给 或 {b64}；解析与落库语义对齐 ScheduleParser.parse（原 receiver）。
     */
    private fun importViaDataOps(app: Application, o: JSONObject): String {
        val text = when {
            o.has("text") -> o.getString("text")
            o.has("b64") -> String(
                android.util.Base64.decode(o.getString("b64"), android.util.Base64.DEFAULT),
                Charsets.UTF_8
            )
            else -> throw BadRequest("import_helper requires text or b64")
        }
        return runBlocking {
            val repo = com.lingion.sleepy.SleepyApp.get().repository
            val parseResult = com.lingion.sleepy.data.parser.ScheduleParser
                .parse(text, 0L).getOrThrow()
            val tableId = repo.insertTable(
                com.lingion.sleepy.data.entity.TimeTableEntity(
                    name = parseResult.tableName.ifBlank { "导入的课表" },
                    startDate = parseResult.startDate.ifBlank {
                        java.time.LocalDate.now(com.lingion.sleepy.util.AppClock).toString()
                    },
                    maxWeek = 20,
                    nodesPerDay = 13,
                    timeJson = com.lingion.sleepy.util.TimeTableUtils.DEFAULT_TIME_JSON,
                    color = "#FF6750A4",
                    isDefault = false
                )
            )
            val ids = repo.insertCourses(
                parseResult.courses.map { it.copy(id = 0, tableId = tableId) }
            )
            "tableId=$tableId inserted=${ids.size}"
        }
    }
}
