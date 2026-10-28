package com.lingion.sleepy.data.jw

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * WakeUp 课程表分享口令在线客户端 — 拾光 GLOBAL_TOOLS/wake_up.js v6.1.70
 * wakeupDecodeShareCode / fetchAndParseData 的 Kotlin 等价实现。
 *
 * 出处: shiguang_warehouse `schools/resources/GLOBAL_TOOLS/wake_up.js`（致谢见 LicenseScreen）。
 * 协议细节(两段 POST、头部、超时 15s、错误文案)与 vendor 逐条对齐;
 * 纯函数层在 WakeUpShareProtocol / WakeUpV6170Crypto, 金标向量锁定。
 * 口令、签名与 token 不落日志。
 *
 * 用户入口: 「课表管理 → 添加课表 → 粘贴课表文本」粘贴口令或含「分享口令」的
 * 分享文案(AddTableScreen.buildImportPreview 的 looksLikeShareCode 窄窗判别);
 * 外部 app 分享文本经 ImportReceiverActivity(REGISTER text/plain)透传同一通路。
 */
internal object WakeUpShareClient {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000

    private val JSON = Json { ignoreUnknownKeys = true }

    /**
     * 判断粘贴文本是否为 WakeUP 分享口令形态(在线拉取通路唯一入口)。
     * 窄窗设计 — 既有离线格式(JSON 以 { 开头、ICS、HTML)不可能命中:
     *  1. 全文去空白后恰为裸口令(^[A-Za-z0-9_-]{8,200}$);
     *  2. 或含「分享口令」关键词且 validateKey 通过(extractKeyFromText 命中 labelled 分支)。
     */
    fun looksLikeShareCode(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (WakeUpShareProtocol.KEY_PATTERN.matches(trimmed)) return true
        return trimmed.contains("分享口令") && WakeUpShareProtocol.validateKey(trimmed) == null
    }

    /**
     * 主入口: 分享文本/口令 → 两段官方请求 → shareData 五段 → Sleepy 可解析的 WakeUp JSON。
     * 返回值直接喂 ScheduleParser.parse(parseWakeUpJson 分支)。
     */
    suspend fun fetchScheduleJson(
        input: String,
        device: WakeUpShareProtocol.Device = WakeUpShareProtocol.createDevice(),
        constants: WakeUpShareProtocol.Constants = WakeUpShareProtocol.V6170,
    ): String {
        val code = WakeUpShareProtocol.extractKeyFromText(input)
        WakeUpShareProtocol.validateKey(code)?.let { throw IllegalArgumentException(it) }
        val shareData = fetchRawShareData(code, device, constants)
        return buildScheduleJson(WakeUpShareProtocol.parseRawScheduleData(shareData))
    }

    /** vendor wakeupDecodeShareCode: antispam → signB → token → share → RC4 解密 → shareData 原文。 */
    internal suspend fun fetchRawShareData(
        code: String,
        device: WakeUpShareProtocol.Device,
        constants: WakeUpShareProtocol.Constants,
    ): String = withContext(Dispatchers.IO) {
        val commonParams = WakeUpShareProtocol.makeCommonParams(device, constants)
        val (signA, rand10) = WakeUpShareProtocol.makeSignA(device.cuid)
        val antispamText = postForm(
            constants.host + constants.antispamPath,
            WakeUpShareProtocol.antispamBody(signA, commonParams),
            device,
        )
        val antispamJson = runCatching { JSON.parseToJsonElement(antispamText) }
            .getOrElse { throw IllegalArgumentException("WakeUP antispam 响应格式无效。") }
        val signB = WakeUpShareProtocol.extractSignB(antispamJson)
        if (signB.isEmpty()) throw IllegalArgumentException("WakeUP antispam 响应未包含签名数据。")

        val token = WakeUpShareProtocol.tokenFromSignB(signB, rand10)
        val shareRequest = WakeUpShareProtocol.buildShareRequest(code, token, device, commonParams, constants = constants)
        val shareText = postForm(constants.host + constants.sharePath, shareRequest.body, device)
        val shareJson = runCatching { JSON.parseToJsonElement(shareText) }
            .getOrElse { throw IllegalArgumentException("WakeUP 课表响应格式无效。") }
        WakeUpShareProtocol.decodeSharePayload(shareJson, shareRequest.rc4Key)
    }

    /** vendor wakeupPostForm: 头部与 15s 超时逐条对齐; 非 2xx 抛 vendor 中文文案。 */
    private fun postForm(url: String, body: String, device: WakeUpShareProtocol.Device): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            instanceFollowRedirects = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("na__zyb_source__", "wakeup")
            setRequestProperty("zyb-cuid", device.cuid)
            setRequestProperty("zyb-adid", device.adid)
            if (device.did.isNotEmpty()) setRequestProperty("zyb-did", device.did)
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("WakeUP 服务请求失败（HTTP $code）。")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ---- shareData → Sleepy 可解析的 WakeUp JSON (vendor fetchAndParseData 输出形态) ----

    /**
     * 五段 ShareParts → {"name","startDate","courses":[...],"tableInfo":{"timeList","nodesPerDay"}}。
     * 字段名按 ScheduleParser.parseWakeUpJson 消费面: courses 用 startNode/step/startWeek/endWeek/type
     * (vendor CourseJsonModel 的 startSection/endSection/weeks 与之一一对应, 无信息损失);
     * tableInfo.timeList 用 vendor saveTimeSlots 的 {node,startTime,endTime} 形态。
     * startDate 为 null 时省略字段 — parseWakeUpJson 自带当日兜底, 不伪造 1970
     * (与 JS 端 epoch 行为的记录偏差: Sleepy 语义里 null 学期起点应回落到今天而非纪元)。
     */
    internal fun buildScheduleJson(parts: WakeUpShareProtocol.ShareParts): String {
        val validNodes = WakeUpShareProtocol.normalizeNodes(parts.uiConfig)
        val timeList = WakeUpShareProtocol.convertTimeSlots(parts.timeSlotsRaw, validNodes)
        val courses = parts.courseDetailRaw.mapNotNull { el ->
            val detail = el as? JsonObject ?: return@mapNotNull null
            val id = (detail["id"] as? JsonPrimitive)
                ?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.content
                ?: return@mapNotNull null
            val courseName = parts.coursesRaw
                .mapNotNull { (it as? JsonObject) }
                .firstOrNull { (it["id"] as? JsonPrimitive)?.content == id }
                ?.get("courseName")?.jsonPrimitive?.contentOrNullSafe2()
                ?: return@mapNotNull null
            val day = detail["day"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val startNode = detail["startNode"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val step = detail["step"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val startWeek = detail["startWeek"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val endWeek = detail["endWeek"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val type = detail["type"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            buildJsonObject {
                put("name", JsonPrimitive(courseName))
                put("teacher", JsonPrimitive(detail["teacher"].contentOrNullSafe2() ?: ""))
                put("position", JsonPrimitive(detail["room"].contentOrNullSafe2() ?: ""))
                put("day", JsonPrimitive(day))
                put("startNode", JsonPrimitive(startNode))
                put("step", JsonPrimitive(step))
                put("startWeek", JsonPrimitive(startWeek))
                put("endWeek", JsonPrimitive(endWeek))
                put("type", JsonPrimitive(type))
            }
        }
        val startDate = WakeUpShareProtocol.convertToSemesterStartDate(
            parts.uiConfig["startDate"]?.jsonPrimitive?.contentOrNullSafe2()
        )
        val maxWeek = parts.uiConfig["maxWeek"]?.jsonPrimitive?.intOrNull
        val root = buildJsonObject {
            put("name", JsonPrimitive("WakeUP 分享课表"))
            if (startDate != null) put("startDate", JsonPrimitive(startDate))
            put("courses", JsonArray(courses))
            put(
                "tableInfo",
                buildJsonObject {
                    put(
                        "timeList",
                        buildJsonArray {
                            timeList.forEach { (node, start, end) ->
                                add(
                                    buildJsonObject {
                                        put("node", JsonPrimitive(node))
                                        put("startTime", JsonPrimitive(start))
                                        put("endTime", JsonPrimitive(end))
                                    }
                                )
                            }
                        }
                    )
                    validNodes.maxOrNull()?.let { put("nodesPerDay", JsonPrimitive(it)) }
                    maxWeek?.let { put("maxWeek", JsonPrimitive(it)) }
                }
            )
        }
        return root.toString()
    }

    private fun JsonElement?.contentOrNullSafe2(): String? =
        (this as? JsonPrimitive)
            ?.takeUnless { it is kotlinx.serialization.json.JsonNull }
            ?.content?.takeIf { it.isNotEmpty() }
}
