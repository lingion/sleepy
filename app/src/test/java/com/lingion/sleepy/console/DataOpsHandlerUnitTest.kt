package com.lingion.sleepy.console

import com.lingion.sleepy.console.handlers.CatalogDeps
import com.lingion.sleepy.console.handlers.ConsoleDataStore
import com.lingion.sleepy.console.handlers.CourseDeps
import com.lingion.sleepy.console.handlers.DataOpsHandlers
import com.lingion.sleepy.console.handlers.PrefsStore
import com.lingion.sleepy.console.handlers.handleCatalog
import com.lingion.sleepy.console.handlers.handleCourse
import com.lingion.sleepy.console.handlers.handlePrefsGet
import com.lingion.sleepy.console.handlers.handlePrefsPut
import com.lingion.sleepy.console.handlers.handleTableOp
import com.lingion.sleepy.console.handlers.handleTables
import com.lingion.sleepy.console.handlers.handleTimeSetting
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.jw.JwSchoolInfo
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Task 2 data-ops 单元测试 — 六个 data 端点的 handler 纯逻辑面（不依赖真 HTTP / 真 Room）。
 *
 * 锁三件事（SPEC §6 + 控制器裁决）：
 *  1. 参数校验全部返回 err envelope，禁抛异常穿透到 500
 *  2. catalog 覆盖层 set/get/持久化重载；release 解锁态 set = 403
 *  3. prefs 导出 map 形状（String/Int/Boolean/Long 四类，未知类型当 String；console_ 前缀排除）
 * Room 真库集成在 androidTest（DataOpsConsoleAndroidTest）。
 */
class DataOpsHandlerUnitTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() {
        DebugGate.reset()
        TokenStore.reset()
        CommandRouter.reset()
        CatalogOverrideStore.resetForTest()
    }

    @After
    fun tearDown() {
        val sys = { System.currentTimeMillis() }
        DebugGate.reset(forTestNow = sys)
        TokenStore.reset(passphrase = "", forTestNow = sys)
        CommandRouter.reset()
        CatalogOverrideStore.resetForTest()
    }

    // ---- helpers ----

    private fun school(
        name: String,
        url: String = "https://$name.example.edu",
        type: String? = "qz",
        status: String = JwSchoolInfo.STATUS_SUPPORTED,
    ) = JwSchoolInfo(sortKey = "#", name = name, url = url, type = type, status = status)

    private fun courseDeps(
        file: String? = null,
        clipboard: String? = null,
        today: String = "2026-09-01",
    ) = CourseDeps(
        readFile = { if (file != null && it == file) File(fileContent(file)).readText() else null },
        readClipboard = { clipboard },
        today = { today },
    )

    private fun fileContent(f: String) = f

    private fun writeTemp(name: String, text: String): File =
        File(tmp.root, name).apply { writeText(text) }

    // ---- catalog: get/set/持久化重载/403 ----

    @Test
    fun catalog_get_reflects_override_after_set() = runBlocking {
        CatalogOverrideStore.initForTest(File(tmp.root, "overrides.json"))
        val deps = CatalogDeps(
            base = { listOf(school("A")) },
            debugBuild = { true },
            releaseUnlocked = { false },
        )
        handleCatalog(deps, """{"op":"get","name":"A"}""").let { body ->
            val o = JSONObject(body)
            assertTrue(o.getBoolean("ok"))
            assertEquals("https://A.example.edu", o.getJSONObject("data").getString("url"))
        }
        val setBody = handleCatalog(deps, """{"op":"set","name":"A","url":"https://new.example"}""")
        assertTrue(JSONObject(setBody).getBoolean("ok"))

        handleCatalog(deps, """{"op":"get","name":"A"}""").let { body ->
            val data = JSONObject(body).getJSONObject("data")
            assertEquals("https://new.example", data.getString("url"))
            assertTrue("覆盖行必须标记 overridden", data.getBoolean("overridden"))
        }
    }

    @Test
    fun catalog_set_persists_file_and_reloads_into_memory() {
        val f = File(tmp.root, "overrides.json")
        CatalogOverrideStore.initForTest(f)
        val ok = CatalogOverrideStore.put(
            name = "B",
            status = JwSchoolInfo.STATUS_PENDING,
            type = null,
            url = null,
            persist = true,
        )
        assertTrue(ok)
        assertTrue("debug 允许写盘后文件必须存在", f.exists())
        // 模拟进程重启：清内存后从文件重载
        CatalogOverrideStore.clearMemoryForTest()
        assertNull(CatalogOverrideStore.get("B"))
        CatalogOverrideStore.reload()
        val row = CatalogOverrideStore.get("B")
        assertNotNull("重载后必须找回覆盖", row)
        assertEquals(JwSchoolInfo.STATUS_PENDING, row!!.status)
    }

    @Test
    fun catalog_set_release_unlocked_returns_403_and_touches_nothing() = runBlocking {
        val f = File(tmp.root, "overrides.json")
        CatalogOverrideStore.initForTest(f)
        val deps = CatalogDeps(
            base = { listOf(school("A")) },
            debugBuild = { false },
            releaseUnlocked = { true },
        )
        val body = handleCatalog(deps, """{"op":"set","name":"A","url":"https://evil.example"}""")
        val o = JSONObject(body)
        assertFalse("release 解锁态 set 必须拒绝", o.getBoolean("ok"))
        assertEquals("403 红线", 403, o.getInt("code"))
        assertNull("被拒的 set 绝不能落内存", CatalogOverrideStore.get("A"))
        assertFalse("被拒的 set 绝不能落盘", f.exists())
    }

    @Test
    fun catalog_set_debug_build_persists() = runBlocking {
        val f = File(tmp.root, "overrides.json")
        CatalogOverrideStore.initForTest(f)
        val deps = CatalogDeps(
            base = { listOf(school("A")) },
            debugBuild = { true },
            releaseUnlocked = { false },
        )
        val body = handleCatalog(deps, """{"op":"set","name":"A","status":"pending"}""")
        val data = JSONObject(body).getJSONObject("data")
        assertTrue(data.getBoolean("persisted"))
        assertTrue(f.exists())
    }

    @Test
    fun catalog_get_unknown_name_returns_404_envelope() = runBlocking {
        CatalogOverrideStore.initForTest(File(tmp.root, "overrides.json"))
        val deps = CatalogDeps(base = { listOf(school("A")) }, debugBuild = { true }, releaseUnlocked = { false })
        val o = JSONObject(handleCatalog(deps, """{"op":"get","name":"Nope"}"""))
        assertFalse(o.getBoolean("ok"))
        assertEquals(404, o.getInt("code"))
    }

    @Test
    fun catalog_missing_op_or_name_returns_400() = runBlocking {
        CatalogOverrideStore.initForTest(File(tmp.root, "overrides.json"))
        val deps = CatalogDeps(base = { listOf(school("A")) }, debugBuild = { true }, releaseUnlocked = { false })
        assertEquals(400, JSONObject(handleCatalog(deps, """{"name":"A"}""")).getInt("code"))
        assertEquals(400, JSONObject(handleCatalog(deps, """{"op":"get"}""")).getInt("code"))
        assertEquals(400, JSONObject(handleCatalog(deps, """{"op":"frobnicate","name":"A"}""")).getInt("code"))
        assertEquals(400, JSONObject(handleCatalog(deps, """not-json""")).getInt("code"))
        assertEquals("set 不给任何可改字段 = 无意义", 400,
            JSONObject(handleCatalog(deps, """{"op":"set","name":"A"}""")).getInt("code"))
    }

    @Test
    fun catalog_apply_to_overlays_base_rows() {
        CatalogOverrideStore.initForTest(File(tmp.root, "overrides.json"))
        CatalogOverrideStore.put("A", status = JwSchoolInfo.STATUS_PENDING, type = null, url = "https://x.example", persist = false)
        val applied = CatalogOverrideStore.applyTo(listOf(school("A"), school("C")))
        val a = applied.first { it.name == "A" }
        assertEquals(JwSchoolInfo.STATUS_PENDING, a.status)
        assertEquals("https://x.example", a.url)
        assertEquals("未覆盖字段保持原值", "qz", a.type)
        val c = applied.first { it.name == "C" }
        assertEquals("未覆盖行原样透传", JwSchoolInfo.STATUS_SUPPORTED, c.status)
    }

    // ---- prefs: 导出形状 / 部分更新 / console_ 排除 ----

    @Test
    fun prefs_export_keeps_four_types_and_stringifies_unknown() {
        val prefs = FakePrefs(
            linkedMapOf(
                "dark_mode" to false,
                "theme_key" to "ocean",
                "before_class_minutes" to 10,
                "calendar_import_target_id" to 1234567890123L,
                "grid_scale" to 1.15f,
                "console_internal" to "secret",
            )
        )
        val data = JSONObject(handlePrefsGet(prefs)).getJSONObject("data")
        assertFalse(data.getJSONObject("values").getBoolean("dark_mode"))
        assertEquals("ocean", data.getJSONObject("values").getString("theme_key"))
        assertEquals(10, data.getJSONObject("values").getInt("before_class_minutes"))
        assertEquals(1234567890123L, data.getJSONObject("values").getLong("calendar_import_target_id"))
        assertEquals("Float 属未知四类 → 按 String", "1.15", data.getJSONObject("values").getString("grid_scale"))
        assertFalse("console_ 前缀键必须排除", data.getJSONObject("values").has("console_internal"))
    }

    @Test
    fun prefs_put_partial_update_applies_only_given_keys() {
        val prefs = FakePrefs(linkedMapOf("a" to "1", "b" to 2))
        val o = JSONObject(handlePrefsPut(prefs, """{"a":"9","reminder_master":true,"n":7,"big":9000000000000}"""))
        assertTrue(o.getBoolean("ok"))
        assertEquals(4, o.getJSONObject("data").getInt("applied"))
        assertEquals("9", prefs.map["a"])
        assertEquals("原键不受影响", 2, prefs.map["b"])
        assertEquals(true, prefs.map["reminder_master"])
        assertEquals(7, prefs.map["n"])
        assertEquals(9000000000000L, prefs.map["big"])
    }

    @Test
    fun prefs_put_rejects_console_prefix_and_bad_payloads() {
        val prefs = FakePrefs(linkedMapOf())
        val blocked = JSONObject(handlePrefsPut(prefs, """{"console_token":"x","ok_key":"y"}"""))
        assertFalse(blocked.getBoolean("ok"))
        assertEquals(400, blocked.getInt("code"))
        assertFalse("被 400 拒的批次不能写进任何键", prefs.map.containsKey("ok_key"))
        assertEquals(400, JSONObject(handlePrefsPut(prefs, "")).getInt("code"))
        assertEquals(400, JSONObject(handlePrefsPut(prefs, """[]""")).getInt("code"))
        assertEquals(400, JSONObject(handlePrefsPut(prefs, """{}""")).getInt("code"))
    }

    // ---- table: op 参数校验缺字段返回 err 不抛异常 ----

    @Test
    fun table_create_requires_name_and_startDate() = runBlocking {
        val store = FakeDataStore()
        assertEquals(400, JSONObject(handleTableOp(store, """{"op":"create"}""")).getInt("code"))
        assertEquals(400, JSONObject(handleTableOp(store, """{"op":"create","name":"X"}""")).getInt("code"))
        val o = JSONObject(handleTableOp(store, """{"op":"create","name":"X","startDate":"2026-09-01"}"""))
        assertTrue(o.getBoolean("ok"))
        val id = o.getJSONObject("data").getLong("id")
        assertEquals("X", store.tables.getValue(id).name)
    }

    @Test
    fun table_unknown_op_and_bad_json_return_400() = runBlocking {
        val store = FakeDataStore()
        assertEquals(400, JSONObject(handleTableOp(store, """{"op":"frobnicate"}""")).getInt("code"))
        assertEquals(400, JSONObject(handleTableOp(store, """{}""")).getInt("code"))
        assertEquals(400, JSONObject(handleTableOp(store, """garbage""")).getInt("code"))
    }

    @Test
    fun table_update_delete_setdefault_validate_id_exists() = runBlocking {
        val store = FakeDataStore()
        val id = store.insertTable(TimeTableEntity(name = "T", startDate = "2026-09-01"))
        // 缺 id / id 不存在 → 400
        assertEquals(400, JSONObject(handleTableOp(store, """{"op":"update","name":"N"}""")).getInt("code"))
        assertEquals(400, JSONObject(handleTableOp(store, """{"op":"delete","id":999}""")).getInt("code"))
        assertEquals(400, JSONObject(handleTableOp(store, """{"op":"setDefault","id":999}""")).getInt("code"))
        // update 合并字段
        val upd = JSONObject(handleTableOp(store, """{"op":"update","id":$id,"name":"Renamed","maxWeek":22}"""))
        assertTrue(upd.getBoolean("ok"))
        assertEquals("Renamed", store.tables.getValue(id).name)
        assertEquals(22, store.tables.getValue(id).maxWeek)
        assertEquals("未提供字段保持", "2026-09-01", store.tables.getValue(id).startDate)
        // setDefault 切换默认
        val second = store.insertTable(TimeTableEntity(name = "T2", startDate = "2026-09-01"))
        val sd = JSONObject(handleTableOp(store, """{"op":"setDefault","id":$second}"""))
        assertEquals(second, sd.getJSONObject("data").getLong("defaultTableId"))
        assertEquals(second, store.defaultId)
        // delete 成功
        val del = JSONObject(handleTableOp(store, """{"op":"delete","id":$second}"""))
        assertTrue(del.getBoolean("ok"))
        assertFalse(store.tables.containsKey(second))
    }

    @Test
    fun table_create_rejects_bad_numbers() = runBlocking {
        val store = FakeDataStore()
        assertEquals(400, JSONObject(handleTableOp(store,
            """{"op":"create","name":"X","startDate":"2026-09-01","maxWeek":0}""")).getInt("code"))
        assertEquals(400, JSONObject(handleTableOp(store,
            """{"op":"create","name":"X","startDate":"2026-09-01","nodesPerDay":-1}""")).getInt("code"))
    }

    // ---- course: 两种 body ----

    @Test
    fun course_entity_path_inserts_into_existing_table() = runBlocking {
        val store = FakeDataStore()
        val id = store.insertTable(TimeTableEntity(name = "T", startDate = "2026-09-01"))
        val body = """
            {"tableId":$id,"courses":[
              {"courseName":"高等数学","teacher":"张三","room":"A101","day":1,
               "startNode":1,"step":2,"startWeek":1,"endWeek":16,"type":0,"color":"#FF6750A4"},
              {"courseName":"大学英语","day":2,"startNode":3,"step":1,"startWeek":1,"endWeek":10}
            ]}
        """.trimIndent()
        val o = JSONObject(handleCourse(store, courseDeps(), body))
        assertTrue(o.getBoolean("ok"))
        assertEquals(2, o.getJSONObject("data").getInt("inserted"))
        val inserted = store.getCourses(id)
        assertEquals(2, inserted.size)
        assertEquals("高等数学", inserted[0].courseName)
        assertEquals(id, inserted[0].tableId)
        assertEquals("缺 color 用默认", "#FF6750A4", inserted[1].color)
        assertTrue("缺 groupId 自动生成", inserted[0].groupId.isNotBlank())
    }

    @Test
    fun course_entity_path_validates_fields_and_table() = runBlocking {
        val store = FakeDataStore()
        val id = store.insertTable(TimeTableEntity(name = "T", startDate = "2026-09-01"))
        // 表不存在
        assertEquals(400, JSONObject(handleCourse(store, courseDeps(), """{"tableId":999,"courses":[]}""")).getInt("code"))
        // courses 缺失/空
        assertEquals(400, JSONObject(handleCourse(store, courseDeps(), """{"tableId":$id}""")).getInt("code"))
        assertEquals(400, JSONObject(handleCourse(store, courseDeps(), """{"tableId":$id,"courses":[]}""")).getInt("code"))
        // day 越界
        val badDay = """{"tableId":$id,"courses":[{"courseName":"X","day":8,"startNode":1,"step":1,"startWeek":1,"endWeek":2}]}"""
        val o = JSONObject(handleCourse(store, courseDeps(), badDay))
        assertFalse(o.getBoolean("ok"))
        assertEquals(400, o.getInt("code"))
        assertTrue("错误信息必须指出坏字段", o.getString("error").contains("day"))
        // endWeek < startWeek
        assertEquals(400, JSONObject(handleCourse(store, courseDeps(),
            """{"tableId":$id,"courses":[{"courseName":"X","day":1,"startNode":1,"step":1,"startWeek":5,"endWeek":2}]}""")).getInt("code"))
        // 非法 JSON
        assertEquals(400, JSONObject(handleCourse(store, courseDeps(), """{{""")).getInt("code"))
        assertTrue("校验失败不得写入", store.courses.isEmpty())
    }

    @Test
    fun course_text_path_creates_table_like_debug_receiver() = runBlocking {
        val store = FakeDataStore()
        val text = "高等数学\t张三\tA101\t1\t1-2\t1-16\t0\n大学英语\t李四\tB202\t2\t3-4\t1-16\t0"
        val o = JSONObject(handleCourse(store, courseDeps(today = "2026-09-01"),
            JSONObject().put("text", text).toString()))
        assertTrue(o.getBoolean("ok"))
        val data = o.getJSONObject("data")
        val tableId = data.getLong("tableId")
        assertEquals(2, data.getInt("inserted"))
        val t = store.tables.getValue(tableId)
        assertTrue("新表名取解析结果或回退", t.name.isNotBlank())
        assertEquals(2, store.getCourses(tableId).size)
        assertEquals("注入课程全部挂在新表下", true, store.getCourses(tableId).all { it.tableId == tableId })
    }

    @Test
    fun course_b64_path_and_missing_source() = runBlocking {
        val store = FakeDataStore()
        val text = "高等数学\t张三\tA101\t1\t1-2\t1-16\t0"
        val b64 = java.util.Base64.getEncoder().encodeToString(text.toByteArray())
        val o = JSONObject(handleCourse(store, courseDeps(), """{"b64":"$b64"}"""))
        assertTrue(o.getBoolean("ok"))
        assertEquals(1, o.getJSONObject("data").getInt("inserted"))
        // 无来源
        assertEquals(400, JSONObject(handleCourse(store, courseDeps(), """{}""")).getInt("code"))
        // b64 非法
        assertEquals(400, JSONObject(handleCourse(store, courseDeps(), """{"b64":"!!!not base64!!!"}""")).getInt("code"))
        // path 读不到
        assertEquals(400, JSONObject(handleCourse(store, courseDeps(), """{"path":"/nope/missing.txt"}""")).getInt("code"))
        // 解析失败（空内容）
        assertEquals(400, JSONObject(handleCourse(store, courseDeps(), """{"text":"   "}""")).getInt("code"))
    }

    @Test
    fun course_path_reads_file_and_clipboard() = runBlocking {
        val store = FakeDataStore()
        val f = writeTemp("sched.txt", "高等数学\t张三\tA101\t1\t1-2\t1-16\t0")
        val o = JSONObject(handleCourse(store, courseDeps(file = f.absolutePath),
            JSONObject().put("path", f.absolutePath).toString()))
        assertTrue(o.getBoolean("ok"))
        assertEquals(1, o.getJSONObject("data").getInt("inserted"))

        val clip = JSONObject(handleCourse(store, courseDeps(clipboard = "大学英语\t李四\tB202\t2\t3-4\t1-16\t0"),
            """{"path":"clipboard"}"""))
        assertTrue(clip.getBoolean("ok"))
        assertEquals(1, clip.getJSONObject("data").getInt("inserted"))

        val noClipDeps = CourseDeps(readFile = { null }, readClipboard = null)
        val noClip = JSONObject(handleCourse(FakeDataStore(), noClipDeps, """{"path":"clipboard"}"""))
        assertFalse("无剪贴板能力时明确报错不裸抛", noClip.getBoolean("ok"))
        assertEquals(400, noClip.getInt("code"))
    }

    // ---- timesetting ----

    @Test
    fun timesetting_create_requires_name() = runBlocking {
        val store = FakeDataStore()
        assertEquals(400, JSONObject(handleTimeSetting(store, """{"op":"create"}""")).getInt("code"))
        assertEquals(400, JSONObject(handleTimeSetting(store, """{"op":"nope"}""")).getInt("code"))
        assertEquals(400, JSONObject(handleTimeSetting(store, """junk""")).getInt("code"))
        val o = JSONObject(handleTimeSetting(store, """{"op":"create","name":"春季作息","nodesPerDay":10}"""))
        assertTrue(o.getBoolean("ok"))
        val id = o.getJSONObject("data").getLong("id")
        assertEquals("春季作息", store.periods.getValue(id).name)
        assertEquals(10, store.periods.getValue(id).nodesPerDay)
    }

    @Test
    fun timesetting_update_and_delete_validate_existence() = runBlocking {
        val store = FakeDataStore()
        val id = store.insertPeriodTable(PeriodTableEntity(name = "P", nodesPerDay = 12))
        // 不存在 → 400
        assertEquals(400, JSONObject(handleTimeSetting(store,
            """{"op":"update","id":999,"nodesPerDay":8}""")).getInt("code"))
        assertEquals(400, JSONObject(handleTimeSetting(store,
            """{"op":"delete","id":999}""")).getInt("code"))
        // update 合并 timeJson
        val newTime = """[{"node":1,"start":"08:20","end":"09:05"}]"""
        val upd = JSONObject(handleTimeSetting(store,
            """{"op":"update","id":$id,"timeJson":${JSONObject.quote(newTime)}}"""))
        assertTrue(upd.getBoolean("ok"))
        assertEquals(newTime, store.periods.getValue(id).timeJson)
        // delete
        val del = JSONObject(handleTimeSetting(store, """{"op":"delete","id":$id}"""))
        assertTrue(del.getBoolean("ok"))
        assertFalse(store.periods.containsKey(id))
    }

    // ---- /v1/data/tables 聚合形状 ----

    @Test
    fun tables_endpoint_aggregates_tables_courses_and_period() = runBlocking {
        val store = FakeDataStore()
        val periodId = store.insertPeriodTable(PeriodTableEntity(name = "作息A", nodesPerDay = 11))
        val tableId = store.insertTable(
            TimeTableEntity(name = "T", startDate = "2026-09-01", periodTableId = periodId)
        )
        store.insertCourses(listOf(
            CourseEntity(groupId = "g1", tableId = tableId, courseName = "高数",
                day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#FF000000")
        ))
        val o = JSONObject(handleTables(store))
        assertTrue(o.getBoolean("ok"))
        val data = o.getJSONObject("data")
        assertEquals(tableId, data.getLong("defaultTableId"))
        val t0 = data.getJSONArray("tables").getJSONObject(0)
        assertEquals("T", t0.getString("name"))
        assertEquals("高数", t0.getJSONArray("courses").getJSONObject(0).getString("courseName"))
        assertEquals("作息A", t0.getJSONObject("periodTable").getString("name"))
        assertEquals("课行必须全字段回显", 2, t0.getJSONArray("courses").getJSONObject(0).getInt("step"))
    }

    // ---- registration surface ----

    @Test
    fun register_all_mounts_six_data_endpoints_on_router() {
        DataOpsHandlers.registerAll(
            store = { FakeDataStore() },
            prefs = { FakePrefs(linkedMapOf()) },
            catalog = { CatalogDeps(base = { emptyList() }, debugBuild = { true }, releaseUnlocked = { false }) },
            course = { courseDeps() },
        )
        val paths = CommandRouter.registeredPaths()
        assertTrue(paths.containsAll(listOf(
            "/v1/data/tables", "/v1/data/table", "/v1/data/course",
            "/v1/data/timesetting", "/v1/data/prefs", "/v1/data/catalog",
        )))
        assertTrue("data 端点全部要 token（Task1 闸不放松）",
            CommandRouter.requiresTokenFor("/v1/data/tables", "GET") &&
                CommandRouter.requiresTokenFor("/v1/data/table", "POST") &&
                CommandRouter.requiresTokenFor("/v1/data/catalog", "POST") &&
                CommandRouter.requiresTokenFor("/v1/data/prefs", "POST"))
        // prefs 同路径双方法
        assertTrue(CommandRouter.hasRoute("/v1/data/prefs", "GET"))
        assertTrue(CommandRouter.hasRoute("/v1/data/prefs", "POST"))
    }

    @Test
    fun dispatch_through_router_reaches_handler() = runBlocking {
        val store = FakeDataStore()
        store.insertTable(TimeTableEntity(name = "只此一张", startDate = "2026-09-01"))
        CommandRouter.register("/v1/data/tables", "GET", requiresToken = true) { handleTables(store) }
        val o = JSONObject(CommandRouter.dispatch("/v1/data/tables", "GET", ConsoleRequest()))
        assertTrue(o.getBoolean("ok"))
        assertEquals("只此一张",
            o.getJSONObject("data").getJSONArray("tables").getJSONObject(0).getString("name"))
    }

    // ---- fakes ----

    private class FakePrefs(initial: LinkedHashMap<String, Any?>) : PrefsStore {
        val map: LinkedHashMap<String, Any?> = initial
        override fun snapshot(): Map<String, Any?> = LinkedHashMap(map)
        override fun put(key: String, value: Any?) {
            map[key] = value
        }
    }

    private class FakeDataStore : ConsoleDataStore {
        val tables = LinkedHashMap<Long, TimeTableEntity>()
        val courses = mutableListOf<CourseEntity>()
        val periods = LinkedHashMap<Long, PeriodTableEntity>()
        var defaultId: Long? = null
        private var seq = 0L

        override suspend fun getAllTables(): List<TimeTableEntity> = tables.values.toList()
        override suspend fun getTable(id: Long): TimeTableEntity? = tables[id]
        override suspend fun getDefaultTable(): TimeTableEntity? = tables[defaultId]
        override suspend fun insertTable(table: TimeTableEntity): Long {
            val id = ++seq
            tables[id] = table.copy(id = id)
            if (table.isDefault || tables.size == 1) defaultId = id
            return id
        }

        override suspend fun updateTable(table: TimeTableEntity) {
            tables[table.id] = table
        }

        override suspend fun deleteTable(id: Long) {
            tables.remove(id)
            courses.removeAll { it.tableId == id }
            if (defaultId == id) defaultId = tables.keys.lastOrNull()
        }

        override suspend fun setDefault(id: Long) {
            defaultId = id
        }

        override suspend fun getCourses(tableId: Long): List<CourseEntity> =
            courses.filter { it.tableId == tableId }

        override suspend fun insertCourses(courses: List<CourseEntity>): List<Long> =
            courses.map { val id = ++seq; this.courses.add(it.copy(id = id)); id }

        override suspend fun insertCoursesKeepingGroups(courses: List<CourseEntity>): List<Long> =
            insertCourses(courses)

        override suspend fun getPeriodTable(id: Long): PeriodTableEntity? = periods[id]
        override suspend fun getAllPeriodTables(): List<PeriodTableEntity> = periods.values.toList()
        override suspend fun insertPeriodTable(period: PeriodTableEntity): Long {
            val id = ++seq
            periods[id] = period.copy(id = id)
            return id
        }

        override suspend fun savePeriodTable(period: PeriodTableEntity): Int {
            if (!periods.containsKey(period.id)) return -1
            periods[period.id] = period
            return 0
        }

        override suspend fun deletePeriodTable(id: Long): Boolean = periods.remove(id) != null

        override suspend fun effectivePeriodTable(tableId: Long): PeriodTableEntity? =
            tables[tableId]?.periodTableId?.let { periods[it] }
    }
}
