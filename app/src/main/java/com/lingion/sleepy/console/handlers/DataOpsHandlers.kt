package com.lingion.sleepy.console.handlers

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.console.CatalogOverrideStore
import com.lingion.sleepy.console.ConsoleBootstrap
import com.lingion.sleepy.console.DebugGate
import com.lingion.sleepy.console.Envelope
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.jw.JwImportViewModel
import com.lingion.sleepy.data.jw.JwSchoolInfo
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.util.TimeTableUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

// Task 2 data-ops — 六个端点的 handler 面（SPEC §3 /v1/data/*）。
//
// 分层：
//  - ConsoleDataStore / PrefsStore / CatalogDeps / CourseDeps = 可注入接缝，
//    JVM 单测用 fake 直测 handler 纯逻辑（不碰真 HTTP / 真 Room / SleepyApp）；
//  - 纯 handler 函数零 Android 依赖，参数错误一律 err envelope，
//    绝不裸抛穿透到 500（控制器裁决 6）；
//  - DataOpsRuntime 把接缝桥到 repository / sleepy_prefs / assets 目录真实现。
//
// 红线落点：
//  - catalog set 在 release 解锁态（非 debug 构建）= 403，内存/文件都不碰（裁决 3）；
//  - prefs 值只认 String/Int/Boolean/Long 四类，其余按 String；console_ 前缀键
//    导出排除 + 整批拒写（裁决 4）；
//  - course 文本通路对齐 DebugImportReceiver 现语义：ScheduleParser 复用、新建表承载，
//    groupId 权威（sleepy-v1）走 KeepingGroups，其余走认亲 insertCourses。
//
// （注：本文件头刻意用行注释——KDoc 块注释可嵌套，正文里的 /v1/data/* 会开一个
//   内层块注释致文件级 Unclosed comment，2026-10-29 残骸核验定案。）

// ============================================================================
// 接缝类型
// ============================================================================

/**
 * data-ops 数据面接缝。方法面刻意收窄到六端点实际用到的 repository 公开 API —
 * 桥接层不做第二套业务逻辑。
 */
interface ConsoleDataStore {
    suspend fun getAllTables(): List<TimeTableEntity>
    suspend fun getTable(id: Long): TimeTableEntity?
    suspend fun getDefaultTable(): TimeTableEntity?
    suspend fun insertTable(table: TimeTableEntity): Long
    suspend fun updateTable(table: TimeTableEntity)
    suspend fun deleteTable(id: Long)
    suspend fun setDefault(id: Long)
    suspend fun getCourses(tableId: Long): List<CourseEntity>
    /** 认亲入库（repository.insertCourses 语义：全库同名继承 groupId，缺 groupId 生成 UUID）。 */
    suspend fun insertCourses(courses: List<CourseEntity>): List<Long>
    /** 绕过认亲、groupId 权威（repository.insertCoursesKeepingGroups 语义）。 */
    suspend fun insertCoursesKeepingGroups(courses: List<CourseEntity>): List<Long>
    suspend fun getPeriodTable(id: Long): PeriodTableEntity?
    suspend fun getAllPeriodTables(): List<PeriodTableEntity>
    suspend fun insertPeriodTable(period: PeriodTableEntity): Long
    /** repository.savePeriodTable：返回绑定课表数；handler 先行存在性校验。 */
    suspend fun savePeriodTable(period: PeriodTableEntity): Int
    suspend fun deletePeriodTable(id: Long): Boolean
    /** 课表绑定的有效作息（未绑定/悬空 → null，对齐 repository.effectivePeriodTable）。 */
    suspend fun effectivePeriodTable(tableId: Long): PeriodTableEntity?
}

/** sleepy_prefs 读写面。snapshot 含全部键（console_ 过滤是 handler 的职责，不是这层的）。 */
interface PrefsStore {
    fun snapshot(): Map<String, Any?>
    fun put(key: String, value: Any?)
    fun remove(key: String) {}
}

/** catalog 端点依赖：目录行来源 + 构建/解锁态（403 红线判据在 handler）。 */
class CatalogDeps(
    val base: suspend () -> List<JwSchoolInfo>,
    val debugBuild: () -> Boolean,
    val releaseUnlocked: () -> Boolean,
)

/** course 文本通路的外部来源注入（文件/剪贴板/今天日期），对齐 DebugImportReceiver 语义。 */
class CourseDeps(
    val readFile: (String) -> String?,
    val readClipboard: (() -> String?)? = null,
    val today: () -> String = { LocalDate.now().toString() },
)

// ============================================================================
// 纯 handler 入口（JVM 直测面）— 参数错误一律 err envelope，绝不裸抛到 500
// ============================================================================

/** GET /v1/data/tables — 课表全量 + 每表课程 + 有效作息 + 全量节次表 + 默认表 id。 */
internal suspend fun handleTables(store: ConsoleDataStore): String {
    val tables = JSONArray()
    for (t in store.getAllTables()) {
        val courses = JSONArray()
        store.getCourses(t.id).forEach { courses.put(courseJson(it)) }
        val period = store.effectivePeriodTable(t.id)
        tables.put(
            tableJson(t)
                .put("courseCount", courses.length())
                .put("courses", courses)
                .put("periodTable", period?.let { periodJson(it) } ?: JSONObject.NULL)
        )
    }
    val periods = JSONArray()
    store.getAllPeriodTables().forEach { periods.put(periodJson(it)) }
    val data = JSONObject()
        .put("tables", tables)
        .put("periodTables", periods)
    val default = store.getDefaultTable()
    if (default == null) data.put("defaultTableId", JSONObject.NULL) else data.put("defaultTableId", default.id)
    return Envelope.ok(data.toString())
}

/** POST /v1/data/table — {op:"create"|"update"|"delete"|"setDefault", ...TimeTableEntity 字段} */
internal suspend fun handleTableOp(store: ConsoleDataStore, body: String): String {
    val o = parseBody(body) ?: return Envelope.err(400, "body must be a JSON object")
    when (o.optString("op", "")) {
        "create" -> {
            val name = o.optString("name", "").ifBlank { return Envelope.err(400, "create requires non-blank \"name\"") }
            val startDate = o.optString("startDate", "")
                .ifBlank { return Envelope.err(400, "create requires \"startDate\" (yyyy-MM-dd)") }
            if (o.has("maxWeek") && o.optInt("maxWeek", -1) <= 0) return Envelope.err(400, "\"maxWeek\" must be > 0")
            if (o.has("nodesPerDay") && o.optInt("nodesPerDay", -1) <= 0) return Envelope.err(400, "\"nodesPerDay\" must be > 0")
            val entity = TimeTableEntity(
                name = name,
                startDate = startDate,
                maxWeek = if (o.has("maxWeek")) o.getInt("maxWeek") else 20,
                nodesPerDay = if (o.has("nodesPerDay")) o.getInt("nodesPerDay") else 12,
                timeJson = o.optString("timeJson", TimeTableUtils.DEFAULT_TIME_JSON),
                color = o.optString("color", "#FF6750A4").ifBlank { "#FF6750A4" },
                isDefault = o.optBoolean("isDefault", false),
                reminderEnabled = if (o.has("reminderEnabled")) o.getBoolean("reminderEnabled") else true,
            )
            val id = store.insertTable(entity)
            return Envelope.ok(JSONObject().put("id", id).toString())
        }
        "update" -> {
            val id = o.optId() ?: return Envelope.err(400, "update requires numeric \"id\"")
            val current = store.getTable(id) ?: return Envelope.err(400, "no time table with id $id")
            val next = current.copy(
                name = if (o.has("name")) o.getString("name") else current.name,
                startDate = if (o.has("startDate")) o.getString("startDate") else current.startDate,
                maxWeek = if (o.has("maxWeek")) o.getInt("maxWeek") else current.maxWeek,
                nodesPerDay = if (o.has("nodesPerDay")) o.getInt("nodesPerDay") else current.nodesPerDay,
                timeJson = if (o.has("timeJson")) o.getString("timeJson") else current.timeJson,
                color = if (o.has("color")) o.getString("color") else current.color,
                reminderEnabled = if (o.has("reminderEnabled")) o.getBoolean("reminderEnabled") else current.reminderEnabled,
            )
            store.updateTable(next)
            return Envelope.ok(tableJson(next).toString())
        }
        "delete" -> {
            val id = o.optId() ?: return Envelope.err(400, "delete requires numeric \"id\"")
            store.getTable(id) ?: return Envelope.err(400, "no time table with id $id")
            store.deleteTable(id)
            return Envelope.ok(JSONObject().put("deleted", id).toString())
        }
        "setDefault" -> {
            val id = o.optId() ?: return Envelope.err(400, "setDefault requires numeric \"id\"")
            store.getTable(id) ?: return Envelope.err(400, "no time table with id $id")
            store.setDefault(id)
            return Envelope.ok(JSONObject().put("defaultTableId", id).toString())
        }
        else -> return Envelope.err(400, "op must be create|update|delete|setDefault, got: ${o.optString("op").ifBlank { "(missing)" }}")
    }
}

/**
 * POST /v1/data/course — 两种 body（裁决 5）：
 *  a) `{tableId, courses:[CourseEntity 字段…]}` → 直插现表；全部自带非空 groupId
 *     视为权威走 KeepingGroups，否则认亲 insertCourses（缺组先按名补组，
 *     与 normalizedImportGroups 的"新名一组一 UUID"结果等价）；
 *  b) `{text|b64|path}` → 复用 ScheduleParser 现通路（与 DebugImportReceiver 同源语义），
 *     新建表承载：表名取解析结果/回退、startDate 缺失用今天、maxWeek 20、nodesPerDay 13、
 *     默认作息、isDefault=false（console 导入不抢默认表，对齐 receiver 的非 importNewTable 通路）。
 */
internal suspend fun handleCourse(store: ConsoleDataStore, deps: CourseDeps, body: String): String {
    val o = parseBody(body) ?: return Envelope.err(400, "body must be a JSON object")
    if (o.has("courses")) {
        val tableId = o.optId("tableId") ?: return Envelope.err(400, "entity path requires numeric \"tableId\"")
        store.getTable(tableId) ?: return Envelope.err(400, "no time table with id $tableId")
        val arr = o.optJSONArray("courses") ?: return Envelope.err(400, "\"courses\" must be an array")
        if (arr.length() == 0) return Envelope.err(400, "\"courses\" must not be empty")
        val courses = ArrayList<CourseEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: return Envelope.err(400, "courses[$i] must be an object")
            if (courseError(c) != null) {
                return Envelope.err(400, "courses[$i]: ${courseError(c)}")
            }
            courses.add(courseFromJson(c, tableId))
        }
        // 校验全通过才落库（失败批次零写入）。
        val ids = insertCourseBatch(store, courses)
        return Envelope.ok(
            JSONObject()
                .put("tableId", tableId)
                .put("inserted", ids.size)
                .put("courseIds", JSONArray(ids))
                .toString()
        )
    }
    val text = when {
        o.has("text") -> o.getString("text")
        o.has("b64") -> decodeB64(o.getString("b64"))
            ?: return Envelope.err(400, "\"b64\" is not valid base64")
        o.has("path") -> {
            val p = o.getString("path")
            if (p == "clipboard") {
                deps.readClipboard?.invoke()?.takeIf { it.isNotBlank() }
                    ?: return Envelope.err(400, "clipboard unavailable or empty in this build")
            } else {
                deps.readFile(p)
                    ?: return Envelope.err(400, "cannot read file: $p")
            }
        }
        else -> return Envelope.err(400, "body needs {tableId, courses:[…]} or {text|b64|path}")
    }
    val parsed = try {
        ScheduleParser.parse(text, 0L).getOrElse {
            return Envelope.err(400, "parse failed: ${it.message?.take(200) ?: it::class.simpleName}")
        }
    } catch (t: Throwable) {
        return Envelope.err(400, "parse failed: ${t::class.simpleName}")
    }
    if (parsed.courses.isEmpty()) {
        return Envelope.err(400, "parsed no courses from the given source")
    }
    val tableId = store.insertTable(
        TimeTableEntity(
            name = parsed.tableName.ifBlank { "导入的课表" },
            startDate = parsed.startDate.ifBlank { deps.today() },
            maxWeek = 20,
            nodesPerDay = 13,
            timeJson = TimeTableUtils.DEFAULT_TIME_JSON,
            color = "#FF6750A4",
            isDefault = false,
        )
    )
    val rows = parsed.courses.map { it.copy(id = 0, tableId = tableId) }
    val ids = when {
        rows.isEmpty() -> emptyList()
        // sleepy-v1 契约一：解析端已权威生成 groupId → 必须绕过认亲（§3.4）
        parsed.groupIdsAuthoritative -> store.insertCoursesKeepingGroups(rows)
        else -> insertCourseBatch(store, rows)
    }
    val data = JSONObject()
        .put("tableId", tableId)
        .put("inserted", ids.size)
        .put("droppedLines", JSONArray(parsed.droppedLines))
    if (parsed.warnings.isNotEmpty()) data.put("warnings", JSONArray(parsed.warnings))
    return Envelope.ok(data.toString())
}

/**
 * 直插通路的组归属决策（两种 body 共用）：
 * 全部行自带非空 groupId = agent 权威 → KeepingGroups；
 * 否则先按名补组（同名同 UUID，等价 normalizedImportGroups 无库命中时的产物）再走认亲。
 */
private suspend fun insertCourseBatch(store: ConsoleDataStore, rows: List<CourseEntity>): List<Long> =
    if (rows.all { it.groupId.isNotBlank() }) {
        store.insertCoursesKeepingGroups(rows)
    } else {
        val byName = LinkedHashMap<String, String>()
        store.insertCourses(
            rows.map { c ->
                if (c.groupId.isNotBlank()) c else c.copy(
                    groupId = byName.getOrPut(c.courseName.trim().lowercase()) { UUID.randomUUID().toString() }
                )
            }
        )
    }

/** POST /v1/data/timesetting — 节次时间/作息表增改删（复用 repository.savePeriodTable）。 */
internal suspend fun handleTimeSetting(store: ConsoleDataStore, body: String): String {
    val o = parseBody(body) ?: return Envelope.err(400, "body must be a JSON object")
    when (o.optString("op", "")) {
        "create" -> {
            val name = o.optString("name", "").ifBlank { return Envelope.err(400, "create requires non-blank \"name\"") }
            if (o.has("nodesPerDay") && o.optInt("nodesPerDay", -1) <= 0) return Envelope.err(400, "\"nodesPerDay\" must be > 0")
            val base = PeriodTableEntity(name = name)
            val entity = base.copy(
                nodesPerDay = if (o.has("nodesPerDay")) o.getInt("nodesPerDay") else base.nodesPerDay,
                timeJson = if (o.has("timeJson")) o.getString("timeJson") else base.timeJson,
                smartConfigJson = if (o.has("smartConfigJson")) o.getString("smartConfigJson") else base.smartConfigJson,
            )
            val id = store.insertPeriodTable(entity)
            return Envelope.ok(JSONObject().put("id", id).toString())
        }
        "update" -> {
            val id = o.optId() ?: return Envelope.err(400, "update requires numeric \"id\"")
            val current = store.getPeriodTable(id) ?: return Envelope.err(400, "no period table with id $id")
            val next = current.copy(
                name = if (o.has("name")) o.getString("name") else current.name,
                nodesPerDay = if (o.has("nodesPerDay")) o.getInt("nodesPerDay") else current.nodesPerDay,
                timeJson = if (o.has("timeJson")) o.getString("timeJson") else current.timeJson,
                smartConfigJson = if (o.has("smartConfigJson")) o.getString("smartConfigJson") else current.smartConfigJson,
            )
            // savePeriodTable 对"不存在"也返回 0 — 存在性已在上方校验, 这里的 0 只会是真的零绑定。
            val boundCount = store.savePeriodTable(next)
            return Envelope.ok(JSONObject().put("id", id).put("boundCount", boundCount).toString())
        }
        "delete" -> {
            val id = o.optId() ?: return Envelope.err(400, "delete requires numeric \"id\"")
            store.getPeriodTable(id) ?: return Envelope.err(400, "no period table with id $id")
            val deleted = store.deletePeriodTable(id)
            return if (deleted) Envelope.ok(JSONObject().put("deleted", id).toString())
            else Envelope.err(400, "delete refused (still bound?) for id $id")
        }
        else -> return Envelope.err(400, "op must be create|update|delete, got: ${o.optString("op").ifBlank { "(missing)" }}")
    }
}

/** GET /v1/data/prefs — sleepy_prefs 扁平导出；console_ 前缀键排除（console 内部面，不外泄）。 */
internal fun handlePrefsGet(prefs: PrefsStore): String {
    val values = JSONObject()
    for ((k, v) in prefs.snapshot()) {
        if (k.startsWith(CONSOLE_KEY_PREFIX)) continue
        values.put(k, encodePrefsValue(v))
    }
    return Envelope.ok(JSONObject().put("values", values).toString())
}

/**
 * POST /v1/data/prefs — 部分更新 {key: value}。
 * 四类 String/Int/Boolean/Long；其余类型（Float/Set…）按 String（裁决 4）；
 * JSON null = 删除该键；console_ 前缀键拒写（400，整批拒绝、零写入）。
 */
internal fun handlePrefsPut(prefs: PrefsStore, body: String): String {
    val o = parseBody(body) ?: return Envelope.err(400, "body must be a JSON object")
    if (o.length() == 0) return Envelope.err(400, "empty body writes nothing")
    val blocked = o.keys().asSequence().filter { it.startsWith(CONSOLE_KEY_PREFIX) }.toList()
    if (blocked.isNotEmpty()) {
        return Envelope.err(400, "keys with prefix '$CONSOLE_KEY_PREFIX' are reserved for the console itself: $blocked")
    }
    val existing = prefs.snapshot()
    var applied = 0
    for (key in o.keys().asSequence().toList()) {
        val raw = o.get(key)
        when {
            raw === JSONObject.NULL -> {
                prefs.remove(key)
                applied++
            }
            raw is Boolean || raw is String -> {
                prefs.put(key, raw)
                applied++
            }
            raw is Number -> {
                // 已存在键保持原存储类型（Int 键塞 Long 会炸既有读取方）；新键按 JSON 自然类型。
                val v: Any = when (existing[key]) {
                    is Long -> raw.toLong()
                    is Int -> raw.toInt()
                    else -> if (raw is Int || raw is Long) raw else raw.toString()
                }
                prefs.put(key, v)
                applied++
            }
            else -> {
                prefs.put(key, raw.toString())
                applied++
            }
        }
    }
    return Envelope.ok(JSONObject().put("applied", applied).toString())
}

/**
 * POST /v1/data/catalog — {op:"get"|"set", name, status?, type?, url?}（裁决 3）。
 * 红线：release 解锁态（非 debug 构建）set 一律 403 — 先于一切写入判断，内存/文件都不碰；
 * 持久化仅 debug 构建（persist=debugBuild）。get 一律反映覆盖层（含 release 只读态的读）。
 */
internal suspend fun handleCatalog(deps: CatalogDeps, body: String): String {
    val o = parseBody(body) ?: return Envelope.err(400, "body must be a JSON object")
    val name = o.optString("name", "").ifBlank { return Envelope.err(400, "\"name\" is required") }
    when (o.optString("op", "")) {
        "get" -> {
            val row = CatalogOverrideStore.applyTo(deps.base()).find { it.name == name }
                ?: return Envelope.err(404, "no school named: $name")
            return Envelope.ok(catalogRowJson(row, CatalogOverrideStore.get(name) != null).toString())
        }
        "set" -> {
            val debugBuild = deps.debugBuild()
            if (!debugBuild && deps.releaseUnlocked()) {
                return Envelope.err(403, "catalog writes are disabled in release unlocked mode (read-only)")
            }
            val status = o.optNonBlank("status")
            val type = o.optNonBlank("type")
            val url = o.optNonBlank("url")
            if (status == null && type == null && url == null) {
                return Envelope.err(400, "set needs at least one of status/type/url")
            }
            val baseRow = deps.base().find { it.name == name }
                ?: return Envelope.err(404, "no school named: $name")
            val stored = CatalogOverrideStore.put(
                name = name,
                status = status,
                type = type,
                url = url,
                persist = debugBuild,
            )
            if (!stored) return Envelope.err(400, "override refused (empty patch or persist failure)")
            val merged = CatalogOverrideStore.applyTo(listOf(baseRow)).first()
            return Envelope.ok(
                catalogRowJson(merged, overridden = true)
                    .put("persisted", debugBuild)
                    .toString()
            )
        }
        else -> return Envelope.err(400, "op must be get|set, got: ${o.optString("op").ifBlank { "(missing)" }}")
    }
}

// ============================================================================
// JSON 形状与校验（handler 私有）
// ============================================================================

private const val CONSOLE_KEY_PREFIX = "console_"

private fun parseBody(body: String): JSONObject? = try {
    JSONObject(body.trim())
} catch (_: Exception) {
    null
}

private fun JSONObject.optId(key: String = "id"): Long? =
    if (has(key) && opt(key) is Number) getLong(key) else null

private fun JSONObject.optNonBlank(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).ifBlank { null } else null

/** JVM 单测与设备端共用 java.util.Base64（android.util.Base64 在 JVM 是 stub，会炸）。 */
private fun decodeB64(s: String): String? = try {
    String(Base64.getDecoder().decode(s), Charsets.UTF_8)
} catch (_: Throwable) {
    null
}

/**
 * CourseEntity JSON → 实体。前置条件：[courseError] 返回 null（校验与构造分步，整批先校验后落库）。
 * groupId 原样保留（空白 = 未声明组）——补组/认亲/绕过认亲的决策只归
 * [insertCourseBatch]，这里提前生成 UUID 会让"全部自带组"判定恒真、认亲通路永不可达。
 */
private fun courseFromJson(c: JSONObject, tableId: Long): CourseEntity = CourseEntity(
    groupId = c.optString("groupId", ""),
    tableId = tableId,
    courseName = c.getString("courseName"),
    teacher = c.optString("teacher", ""),
    room = c.optString("room", ""),
    note = c.optString("note", ""),
    alias = c.optString("alias", ""),
    day = c.getInt("day"),
    startNode = c.getInt("startNode"),
    step = c.getInt("step"),
    startWeek = c.getInt("startWeek"),
    endWeek = c.getInt("endWeek"),
    type = if (c.has("type")) c.getInt("type") else 0,
    color = c.optString("color", "").ifBlank { "#FF6750A4" },
    colorMode = if (c.has("colorMode")) c.getInt("colorMode") else 0,
    ownTime = c.optBoolean("ownTime", false),
    isIrregularNode = c.optBoolean("isIrregularNode", false),
    isIrregularTime = c.optBoolean("isIrregularTime", false),
    startTime = c.optString("startTime", ""),
    endTime = c.optString("endTime", ""),
    credit = c.optDouble("credit", 0.0).toFloat(),
    level = if (c.has("level")) c.getInt("level") else 0,
    fixedDate = if (c.has("fixedDate") && !c.isNull("fixedDate")) c.getString("fixedDate") else null,
)

/** 课程行校验，返回首个问题描述；合法返回 null。错误文案点名坏字段（测试锁这条）。 */
private fun courseError(c: JSONObject): String? {
    if (c.optString("courseName", "").isBlank()) return "courseName is required"
    val day = if (c.has("day")) c.optInt("day", -1) else -1
    if (day !in 1..7) return "day must be 1..7 (got ${if (c.has("day")) c.opt("day") else "(missing)"})"
    if (!c.has("startNode") || c.optInt("startNode", -1) < 1) return "startNode must be >= 1"
    if (!c.has("step") || c.optInt("step", -1) < 1) return "step must be >= 1"
    val sw = if (c.has("startWeek")) c.optInt("startWeek", -1) else -1
    if (sw < 1) return "startWeek must be >= 1"
    val ew = if (c.has("endWeek")) c.optInt("endWeek", -1) else -1
    if (ew < sw) return "endWeek must be >= startWeek (got $ew < $sw)"
    if (c.has("type") && c.optInt("type", -1) !in 0..3) return "type must be 0..3 (got ${c.opt("type")})"
    return null
}

private fun tableJson(t: TimeTableEntity): JSONObject = JSONObject()
    .put("id", t.id)
    .put("name", t.name)
    .put("startDate", t.startDate)
    .put("maxWeek", t.maxWeek)
    .put("nodesPerDay", t.nodesPerDay)
    .put("timeJson", t.timeJson)
    .put("color", t.color)
    .put("isDefault", t.isDefault)
    .put("smartConfigJson", t.smartConfigJson)
    .put("createdAt", t.createdAt)
    .put("periodTableId", t.periodTableId ?: JSONObject.NULL)
    .put("preBindSnapshotJson", t.preBindSnapshotJson)
    .put("reminderEnabled", t.reminderEnabled)

private fun periodJson(p: PeriodTableEntity): JSONObject = JSONObject()
    .put("id", p.id)
    .put("name", p.name)
    .put("nodesPerDay", p.nodesPerDay)
    .put("timeJson", p.timeJson)
    .put("smartConfigJson", p.smartConfigJson)
    .put("createdAt", p.createdAt)
    .put("updatedAt", p.updatedAt)

private fun courseJson(c: CourseEntity): JSONObject = JSONObject()
    .put("id", c.id)
    .put("groupId", c.groupId)
    .put("tableId", c.tableId)
    .put("courseName", c.courseName)
    .put("teacher", c.teacher)
    .put("room", c.room)
    .put("note", c.note)
    .put("alias", c.alias)
    .put("day", c.day)
    .put("startNode", c.startNode)
    .put("step", c.step)
    .put("startWeek", c.startWeek)
    .put("endWeek", c.endWeek)
    .put("type", c.type)
    .put("color", c.color)
    .put("colorMode", c.colorMode)
    .put("ownTime", c.ownTime)
    .put("isIrregularNode", c.isIrregularNode)
    .put("isIrregularTime", c.isIrregularTime)
    .put("startTime", c.startTime)
    .put("endTime", c.endTime)
    .put("credit", c.credit.toDouble())
    .put("level", c.level)
    .put("fixedDate", c.fixedDate ?: JSONObject.NULL)

private fun catalogRowJson(s: JwSchoolInfo, overridden: Boolean): JSONObject = JSONObject()
    .put("name", s.name)
    .put("sortKey", s.sortKey)
    .put("url", s.url)
    .put("type", s.type ?: JSONObject.NULL)
    .put("status", s.status)
    .put("enableFetch", s.enableFetch)
    .put("overridden", overridden)

/** prefs 值编码：String/Int/Boolean/Long 四类原样，其余（Float/Set…）一律 String（裁决 4）。 */
private fun encodePrefsValue(v: Any?): Any = when (v) {
    null -> JSONObject.NULL
    is String, is Boolean, is Int, is Long -> v
    else -> v.toString()
}

// ============================================================================
// 注册通道
// ============================================================================

/**
 * data-ops 六端点（七条路由，prefs 双方法）注册到 CommandRouter。
 * 走 ConsoleBootstrap.addHandler 覆盖式通道（Task 1 扩展点）— debug 源集增强
 * 有权替换默认实现，重复装配不炸。requiresToken 全 true：Task 1 鉴权闸不放松。
 */
object DataOpsHandlers {

    fun registerAll(
        store: suspend () -> ConsoleDataStore,
        prefs: () -> PrefsStore,
        catalog: () -> CatalogDeps,
        course: () -> CourseDeps,
    ) {
        ConsoleBootstrap.addHandler("/v1/data/tables", "GET", requiresToken = true) {
            handleTables(store())
        }
        ConsoleBootstrap.addHandler("/v1/data/table", "POST", requiresToken = true) { req ->
            handleTableOp(store(), req.body)
        }
        ConsoleBootstrap.addHandler("/v1/data/course", "POST", requiresToken = true) { req ->
            handleCourse(store(), course(), req.body)
        }
        ConsoleBootstrap.addHandler("/v1/data/timesetting", "POST", requiresToken = true) { req ->
            handleTimeSetting(store(), req.body)
        }
        ConsoleBootstrap.addHandler("/v1/data/prefs", "GET", requiresToken = true) {
            handlePrefsGet(prefs())
        }
        ConsoleBootstrap.addHandler("/v1/data/prefs", "POST", requiresToken = true) { req ->
            handlePrefsPut(prefs(), req.body)
        }
        ConsoleBootstrap.addHandler("/v1/data/catalog", "POST", requiresToken = true) { req ->
            handleCatalog(catalog(), req.body)
        }
    }
}

// ============================================================================
// 运行时桥接（真 Room / 真 sleepy_prefs / assets 目录）
// ============================================================================

/**
 * 装配点：ConsoleBootstrap.start() 在 registerDefaultHandlers() 之后调 [install]，
 * 把六个端点接到进程内真数据面。debug 构建解锁持久化目录文件；
 * release 解锁态只读（set 通路被 handler 403 拦死，init 仍装载既有覆盖层供读）。
 */
object DataOpsRuntime {

    fun install(app: Application) {
        CatalogOverrideStore.init(app)
        val repo = SleepyAppRepository()
        val prefs = AppPrefsStore(app)
        DataOpsHandlers.registerAll(
            store = { repo },
            prefs = { prefs },
            catalog = {
                CatalogDeps(
                    base = { loadCatalog(app) },
                    debugBuild = { DebugGate.debugBuild },
                    releaseUnlocked = { DebugGate.isReleaseUnlocked },
                )
            },
            course = {
                CourseDeps(
                    readFile = { path ->
                        val f = File(path)
                        if (f.exists() && f.canRead()) runCatching { f.readText(Charsets.UTF_8) }.getOrNull() else null
                    },
                    readClipboard = {
                        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.primaryClip?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.coerceToText(app)?.toString()
                    },
                    today = { LocalDate.now().toString() },
                )
            },
        )
    }

    /** assets 目录行 + 覆盖层套用（与 JwImportViewModel.loadSchools 同一链路口径）。 */
    private suspend fun loadCatalog(app: Application): List<JwSchoolInfo> = try {
        val text = app.assets.open("schools.json").bufferedReader().use { it.readText() }
        CatalogOverrideStore.applyTo(JwImportViewModel.parseSchoolsJson(text))
    } catch (_: Throwable) {
        emptyList()
    }
}

/** [ConsoleDataStore] → [ScheduleRepository] 直桥：零业务逻辑，只解引用 SleepyApp。 */
private class SleepyAppRepository : ConsoleDataStore {
    private val repo: ScheduleRepository get() = SleepyApp.get().repository

    override suspend fun getAllTables() = repo.getAllTables()
    override suspend fun getTable(id: Long) = repo.getTable(id)
    override suspend fun getDefaultTable() = repo.getDefaultTable()
    override suspend fun insertTable(table: TimeTableEntity) = repo.insertTable(table)
    override suspend fun updateTable(table: TimeTableEntity) = repo.updateTable(table)
    override suspend fun deleteTable(id: Long) = repo.deleteTable(id)
    override suspend fun setDefault(id: Long) = repo.setDefault(id)
    override suspend fun getCourses(tableId: Long) = repo.getCourses(tableId)
    override suspend fun insertCourses(courses: List<CourseEntity>) = repo.insertCourses(courses)
    override suspend fun insertCoursesKeepingGroups(courses: List<CourseEntity>) =
        repo.insertCoursesKeepingGroups(courses)
    override suspend fun getPeriodTable(id: Long) = repo.getPeriodTable(id)
    override suspend fun getAllPeriodTables() = repo.getAllPeriodTables()
    override suspend fun insertPeriodTable(period: PeriodTableEntity) = repo.insertPeriodTable(period)
    override suspend fun savePeriodTable(period: PeriodTableEntity) = repo.savePeriodTable(period)
    override suspend fun deletePeriodTable(id: Long) = repo.deletePeriodTable(id)
    override suspend fun effectivePeriodTable(tableId: Long) = repo.effectivePeriodTable(tableId)
}

/**
 * [PrefsStore] → `sleepy_prefs` 直桥（与 AppPrefs 同一文件）。
 * commit() 同步落盘：控制面请求收敛在 NanoHTTPD worker 的 runBlocking 里，
 * 同步语义避免"写返回 200 但文件还没落"的读竞态。
 */
private class AppPrefsStore(app: Application) : PrefsStore {
    private val sp = app.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    override fun snapshot(): Map<String, Any?> = LinkedHashMap(sp.all)

    override fun put(key: String, value: Any?) {
        val e = sp.edit()
        when (value) {
            is String -> e.putString(key, value)
            is Boolean -> e.putBoolean(key, value)
            is Int -> e.putInt(key, value)
            is Long -> e.putLong(key, value)
            // 四类之外的类型在 handler 层已 string 化；这里兜底不丢写
            null -> e.remove(key)
            else -> e.putString(key, value.toString())
        }
        e.commit()
    }

    override fun remove(key: String) {
        sp.edit().remove(key).commit()
    }

    companion object {
        const val PREFS_FILE = "sleepy_prefs"
    }
}
