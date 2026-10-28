package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.security.SecureRandom

/**
 * WakeUp 课程表分享口令协议 v6.1.70 — 拾光 GLOBAL_TOOLS/wake_up.js 的 Kotlin 等价实现。
 *
 * 出处: shiguang_warehouse `schools/resources/GLOBAL_TOOLS/wake_up.js`（致谢见 LicenseScreen）。
 * 常量冻结自 WakeUP v6.1.70 官渠 APK 提取的公开协议参数（厂商公开常量, 非用户凭证）。
 * 口令、签名与 token 不落日志。
 *
 * 纯函数层 — 与 vendor JS 的逐字节等价由 WakeUpV6170InvariantTest 用直接执行 vendor 脚本
 * 生成的金标向量锁定; 网络层在 WakeUpShareClient。
 */
internal object WakeUpShareProtocol {

    /** vendor WAKEUP_V6170 逐字冻结。 */
    data class Constants(
        val host: String,
        val antispamPath: String,
        val sharePath: String,
        val packageName: String,
        val versionName: String,
        val versionCode: Int,
        val channel: String,
        val publicToken: String,
        val signatureMd5: String,
        val magic: String,
        val signAKey: String,
        val keySalt: String,
    )

    val V6170 = Constants(
        // 2026-10-28 对真实服务端活体实测: 6.6.2 版 APK 逆向确认证书未变(signatureMd5 相同)、
        // signAKey/keySalt 仍明文在 libbaseutil.so rodata(magic 移入 native, JS 层不再显式);
        // 服务端不校验版本新旧——vc=450 与 vc=552/6.6.2 均返回 errNo=0, 且本常量组完整签名链
        // 被当前服务端接受(离线逐字节重放 + Kotlin 直连双模式, 见 WakeUpLiveCrossVerifyTest)。
        // 故不另开 V662 常量组。
        host = "https://api.wakeup.fun",
        antispamPath = "/pluto/app/antispam",
        sharePath = "/share_schedule/getv2",
        packageName = "com.suda.yzune.wakeupschedule",
        versionName = "6.1.70",
        versionCode = 450,
        channel = "100271a",
        publicToken = "1_XPXQH3c5HRPtFHkSwi3sCCURmT25QfxM",
        signatureMd5 = "318c6d4f74655d4f032fb0466bcfdfbc",
        magic = "8&%d*",
        signAKey = "@fG2SuLA",
        keySalt = "@#AIjd83#@6B",
    )

    /** vendor WAKEUP_DEVICE_DEFAULTS; androidId 为默认伪装值, 可被调用方替换。 */
    data class Device(
        val androidId: String = "0000000000000000",
        val sdk: String = "35",
        val device: String = "Pixel 7",
        val brand: String = "google",
        val screensize: String = "1080x2400",
        val abis: String = "arm64-v8a",
        val appBit: String = "64",
        val appId: String = "wakeup",
        val downloadType: String = "1",
        val nt: String = "wifi",
        val province: String = "",
        val city: String = "",
        val area: String = "",
        val deviceId: String = "",
        val operatorid: String = "",
        val adid: String = "",
        val did: String = "",
        val cuid: String = "",
    )

    /** vendor wakeupCreateDevice: adid 缺省时由 androidId 推导, cuid 恒由 androidId 推导。 */
    fun createDevice(androidId: String = "0000000000000000", adid: String = "", did: String = ""): Device =
        Device(
            androidId = androidId,
            adid = adid.ifEmpty { adidFromAndroidId(androidId) },
            did = did,
            cuid = cuidFromAndroidId(androidId),
        )

    val KEY_PATTERN = Regex("^[A-Za-z0-9_-]{8,200}$")

    private val JSON = Json { ignoreUnknownKeys = true }

    private val LABELLED_PATTERN =
        Regex("分享口令(?:为)?\\s*[「“\"]?\\s*([A-Za-z0-9_-]{1,200})\\s*[」”\"]?")
    private val QUOTED_PATTERN = Regex("[「“\"]\\s*([A-Za-z0-9_-]{8,200})\\s*[」”\"]")
    private val BARE_PATTERN = Regex("[A-Za-z0-9_-]{8,200}")

    /** vendor validateKey: 合法返回 null, 否则返回错误文案(6 语资源接入前保持 vendor 中文文案)。 */
    fun validateKey(input: String?): String? {
        val key = extractKeyFromText(input)
        if (key.isEmpty()) return "输入不能为空！"
        if (!KEY_PATTERN.matches(key)) {
            return "未检测到有效的 WakeUP 分享口令，请粘贴完整分享文本或口令。"
        }
        return null
    }

    /** vendor extractKeyFromText 三分支: labelled → quoted → 最后一个裸候选。 */
    fun extractKeyFromText(value: String?): String {
        val text = value?.trim() ?: ""
        if (text.isEmpty()) return ""
        LABELLED_PATTERN.find(text)?.groupValues?.get(1)?.let { return it }
        QUOTED_PATTERN.find(text)?.groupValues?.get(1)?.let { return it }
        return BARE_PATTERN.findAll(text).lastOrNull()?.value ?: text
    }

    fun cuidFromAndroidId(androidId: String): String =
        WakeUpV6170Crypto.md5Upper("com.baidu$androidId") + "|0"

    /** vendor wakeupAdidFromAndroidId: md5 前 16 hex XOR 后 16, 拼 8 位校验和到 md5 全文后。 */
    fun adidFromAndroidId(androidId: String): String {
        val prefix = WakeUpV6170Crypto.md5Hex("alpha.beta$androidId")
        val folded = java.math.BigInteger(prefix.substring(0, 16), 16)
            .xor(java.math.BigInteger(prefix.substring(16), 16))
        val checksum = folded.shiftRight(32).xor(folded)
            .and(java.math.BigInteger("FFFFFFFF", 16))
        return prefix + checksum.toString(16).padStart(8, '0')
    }

    private const val RAND10_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val secureRandom = SecureRandom()

    /** vendor wakeupGenerateRand10 (SecureRandom 模 62; 模偏差与 vendor byte%62 同构)。 */
    fun generateRand10(): String {
        val bytes = ByteArray(10).also { secureRandom.nextBytes(it) }
        return String(CharArray(10) { i ->
            RAND10_ALPHABET[(bytes[i].toInt() and 0xFF) % RAND10_ALPHABET.length]
        })
    }

    fun makeCommonParams(device: Device, constants: Constants = V6170): List<Pair<String, String>> =
        listOf(
            "area" to device.area, "screensize" to device.screensize, "cuid" to device.cuid, "os" to "android",
            "city" to device.city, "abis" to device.abis, "channel" to constants.channel, "appBit" to device.appBit,
            "vc" to constants.versionCode.toString(), "deviceId" to device.deviceId, "token" to constants.publicToken,
            "adid" to device.adid, "province" to device.province, "pkgName" to constants.packageName,
            "appId" to device.appId, "download_type" to device.downloadType, "vcname" to constants.versionName,
            "sdk" to device.sdk, "device" to device.device, "brand" to device.brand, "operatorid" to device.operatorid,
        )

    /** vendor wakeupMakeSignA: 返回 signA 与生成它的 rand10(token 校验要用同一 rand10)。 */
    fun makeSignA(
        cuid: String,
        rand10: String = generateRand10(),
        constants: Constants = V6170,
    ): Pair<String, String> {
        val plain = "${constants.magic}##$rand10##${constants.signatureMd5}##$cuid"
        val signA = WakeUpV6170Crypto.nativeHexEncode(
            WakeUpV6170Crypto.desEncrypt(plain, constants.signAKey)
        )
        return signA to rand10
    }

    /** vendor wakeupTokenFromSignB: DES 解密后前 10 字符必须等于 rand10, token = text[12..21]。 */
    fun tokenFromSignB(signB: String, rand10: String): String {
        val plain = WakeUpV6170Crypto.desDecrypt(
            WakeUpV6170Crypto.nativeHexDecode(signB), "${rand10.substring(0, 5)}#G4"
        )
        val text = WakeUpV6170Crypto.bytesToLatin1(plain)
        if (text.length < 22 || text.substring(0, 10) != rand10) {
            throw IllegalArgumentException("WakeUP antispam token 校验失败。")
        }
        return text.substring(12, 22)
    }

    /** vendor wakeupGetRc4Key — 变形链逐字移植: 17+反/15-17/前15反、首尾交换 3 次、拼 md5(自身)、前 60 位对折交换。 */
    fun getRc4Key(token: String, constants: Constants = V6170): String {
        val first = WakeUpV6170Crypto.md5Hex(constants.keySalt)
        val second = WakeUpV6170Crypto.md5Hex(constants.versionCode.toString())
        val raw = WakeUpV6170Crypto.md5Hex("[$token]@")
        val reversed = raw.substring(17).reversed() + raw.substring(15, 17) + raw.substring(0, 15).reversed()
        val chars = (first + second + reversed).toCharArray()
        for (i in 0 until 3) {
            val right = chars.size - 1 - i
            val tmp = chars[i]; chars[i] = chars[right]; chars[right] = tmp
        }
        val joined = String(chars)
        val output = (joined + WakeUpV6170Crypto.md5Hex(joined)).toCharArray()
        for (i in 0 until 60) {
            val right = output.size - 1 - i
            val tmp = output[i]; output[i] = output[right]; output[right] = tmp
        }
        return String(output)
    }

    /** vendor wakeupGetSign。 */
    fun getSign(base64Params: String, token: String, constants: Constants = V6170): String =
        WakeUpV6170Crypto.md5Hex(
            "${constants.magic}[${WakeUpV6170Crypto.md5Hex(token)}]@$base64Params"
        )

    /** vendor wakeupBuildShareRequest 的排序签名串(独立暴露以便向量锁定)。 */
    fun signBase64(
        dataValue: String,
        commonParams: List<Pair<String, String>>,
        did: String,
        nt: String,
        serverTime: Long,
        kakorr: Long,
    ): String {
        val items = mutableListOf("data=$dataValue")
        items += commonParams.map { (key, value) -> "$key=$value" }
        if (did.isNotEmpty()) items += "did=$did"
        items += "nt=$nt"
        items += "_t_=$serverTime"
        items += "kakorrhaphiophobia=$kakorr"
        // JS Array.prototype.sort 默认按 UTF-16 码元序 — Kotlin char.code 序与之等价的子集(ASCII 键值)
        return WakeUpV6170Crypto.bytesToBase64(
            items.sorted().joinToString("").toByteArray(Charsets.UTF_8)
        )
    }

    data class ShareRequest(val rc4Key: String, val body: String)

    /** vendor wakeupBuildShareRequest: nowMs 注入以便向量回归可复现。 */
    fun buildShareRequest(
        code: String,
        token: String,
        device: Device,
        commonParams: List<Pair<String, String>>,
        nowMs: Long = System.currentTimeMillis(),
        constants: Constants = V6170,
    ): ShareRequest {
        val rc4Key = getRc4Key(token, constants)
        val dataValue = WakeUpV6170Crypto.bytesToBase64(
            WakeUpV6170Crypto.rc4("key=${WakeUpV6170Crypto.androidQuote(code)}", rc4Key)
        )
        val serverTime = nowMs / 1000
        val sign = getSign(
            signBase64(dataValue, commonParams, device.did, device.nt, serverTime, nowMs),
            token, constants
        )
        val extras = if (device.did.isNotEmpty()) listOf("did" to device.did) else emptyList()
        val body = "&" + WakeUpV6170Crypto.formEncode(
            listOf("data" to dataValue) + commonParams + extras + listOf("nt" to device.nt)
        ) + "&sign=$sign&_t_=$serverTime&kakorrhaphiophobia=$nowMs"
        return ShareRequest(rc4Key, body)
    }

    /** vendor wakeupAntispamBody。 */
    fun antispamBody(signA: String, commonParams: List<Pair<String, String>>): String =
        WakeUpV6170Crypto.formEncode(listOf("data" to signA) + commonParams) + "&"

    /** vendor wakeupExtractSignB 的容错取串(仅非空字符串字段命中; 数字/对象/null 走降级)。 */
    fun extractSignB(root: JsonElement?): String {
        if (root !is JsonObject) return ""
        fun nonEmptyString(el: JsonElement?): String? =
            (el as? JsonPrimitive)
                ?.takeUnless { it is JsonNull }
                ?.takeIf { it.isString && it.content.isNotEmpty() }
                ?.content
        val candidate = nonEmptyString(root["data"])
            ?: (root["data"] as? JsonObject)?.let { nonEmptyString(it["data"]) }
            ?: nonEmptyString((root["result"] as? JsonObject)?.get("data"))
        return candidate ?: ""
    }

    /** vendor 双层解包后 RC4 解密, 返回 shareData 原文; 失败抛 IllegalArgumentException(中文文案与 vendor 同)。 */
    fun decodeSharePayload(shareJsonRoot: JsonElement, rc4Key: String): String {
        fun nonEmptyString(el: JsonElement?): String? =
            (el as? JsonPrimitive)
                ?.takeUnless { it is JsonNull }
                ?.takeIf { it.isString && it.content.isNotEmpty() }
                ?.content
        val dataField = (shareJsonRoot as? JsonObject)?.get("data")
        // vendor: data 为对象取其 data 字段, 否则 data 本身; 非字符串/空串 → 无加密数据
        val encrypted = (dataField as? JsonObject)?.let { nonEmptyString(it["data"]) }
            ?: nonEmptyString(dataField)
            ?: throw IllegalArgumentException("WakeUP 课表响应未包含加密数据。")
        val decrypted = runCatching {
            JSON.parseToJsonElement(rc4DecryptToString(encrypted, rc4Key))
        }.getOrElse { throw IllegalArgumentException("WakeUP 课表响应解密失败。") }
        // vendor typeof shareData !== "string" 判定 — 空串仍是字符串, 交由 parseRawScheduleData 报段数不足
        val shareData = ((decrypted as? JsonObject)?.get("shareData") as? JsonPrimitive)
            ?.takeUnless { it is JsonNull }
            ?.takeIf { it.isString }
            ?.content
            ?: throw IllegalArgumentException("WakeUP 解密数据中未找到 shareData。")
        return shareData
    }

    /** vendor wakeupBytesToUtf8(wakeupRc4(wakeupBase64ToBytes(encrypted), rc4Key)): base64→bytes→rc4→utf8。 */
    fun rc4DecryptToString(encryptedBase64: String, rc4Key: String): String =
        WakeUpV6170Crypto.bytesToUtf8(
            WakeUpV6170Crypto.rc4Raw(
                WakeUpV6170Crypto.base64ToBytes(encryptedBase64), rc4Key
            )
        )

    private fun JsonElement?.contentOrNullSafe(): String? =
        (this as? JsonPrimitive)
            ?.takeUnless { it is JsonNull }
            ?.content?.takeIf { it.isNotEmpty() }

    // ---- shareData 五段解析与课程转换 (vendor parseRawScheduleData / convertToCourseJsonModel) ----

    data class ShareParts(
        val baseConfig: JsonObject,
        val timeSlotsRaw: JsonArray,
        val uiConfig: JsonObject,
        val coursesRaw: JsonArray,
        val courseDetailRaw: JsonArray,
    )

    fun parseRawScheduleData(rawData: String): ShareParts {
        val parts = rawData.trim().split("\n")
        if (parts.size < 5) throw IllegalArgumentException("数据格式不完整，预期至少包含 5 个部分。")
        return ShareParts(
            baseConfig = parts[0].let { JSON.parseToJsonElement(it).jsonObject },
            timeSlotsRaw = parts[1].let { JSON.parseToJsonElement(it).jsonArray },
            uiConfig = parts[2].let { JSON.parseToJsonElement(it).jsonObject },
            coursesRaw = parts[3].let { JSON.parseToJsonElement(it).jsonArray },
            courseDetailRaw = parts[4].let { JSON.parseToJsonElement(it).jsonArray },
        )
    }

    /** vendor fetchAndParseData 的 uiConfig.nodes 归一(数组 / 正整数展开 / 否则空)。 */
    fun normalizeNodes(uiConfig: JsonObject): Set<Int> {
        val raw = uiConfig["nodes"]
        if (raw is JsonArray) {
            return raw.mapNotNull { it.jsonPrimitive.intOrNull }.toSet()
        }
        val asNumber = raw?.jsonPrimitive?.intOrNull
        if (asNumber != null && asNumber > 0) return (1..asNumber).toSet()
        return emptySet()
    }

    /** vendor timeSlots 双过滤(00:00 端点 + node 白名单)。 */
    fun convertTimeSlots(timeSlotsRaw: JsonArray, validNodes: Set<Int>): List<Triple<Int, String, String>> =
        timeSlotsRaw.mapNotNull { el ->
            val slot = el as? JsonObject ?: return@mapNotNull null
            val start = slot["startTime"]?.jsonPrimitive?.content ?: ""
            val end = slot["endTime"]?.jsonPrimitive?.content ?: ""
            val node = slot["node"]?.jsonPrimitive?.intOrNull
            if (start == "00:00" || end == "00:00" || node == null || node !in validNodes) return@mapNotNull null
            Triple(node, start, end)
        }

    /** vendor convertToSemesterStartDate: 斜杠转横线, 宽松 yyyy-M-d 解析, 非法 null。 */
    /** vendor convertToSemesterStartDate: JS new Date 宽松解析的等价子集 —
     *  斜杠转横线后按 Y-M-D 优先、M-D-Y 回退(锁定向量 '2025/9/1'→2025-09-01、'9/1/2025'→2025-09-01);
     *  越界(2025/13/45)与空串 → null(与 JS Invalid Date→null 一致)。 */
    fun convertToSemesterStartDate(rawDate: String?): String? {
        val text = rawDate?.trim()?.takeIf { it.isNotEmpty() }?.replace('/', '-') ?: return null
        val m = Regex("""^(\d{1,4})-(\d{1,2})-(\d{1,4})T?""").find(text) ?: return null
        val (a, b, c) = m.destructured
        return if (a.length == 4) {
            runCatching {
                LocalDate.of(a.toInt(), b.toInt(), c.toInt())
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
            }.getOrNull()
        } else {
            runCatching {
                LocalDate.of(c.toInt(), a.toInt(), b.toInt())
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
            }.getOrNull()
        }
    }

    /** vendor convertToCourseJsonModel: coursesRaw id→courseName 映射 + detail 展开。
     *  id 以 JSON 字面量 content 作键 — 数字与字符串 id 的命中行为与 JS 对象键一致。 */
    fun convertToCourseJsonModel(parts: ShareParts): List<JsonObject> {
        val courseMap = mutableMapOf<String, String>()
        for (el in parts.coursesRaw) {
            val course = el as? JsonObject ?: continue
            val id = (course["id"] as? JsonPrimitive)
                ?.takeUnless { it is JsonNull }
                ?.content ?: continue
            courseMap[id] = course["courseName"]?.jsonPrimitive?.contentOrNullSafe() ?: ""
        }
        return parts.courseDetailRaw.mapNotNull { el ->
            val detail = el as? JsonObject ?: return@mapNotNull null
            val idElement = (detail["id"] as? JsonPrimitive)
                ?.takeUnless { it is JsonNull } ?: return@mapNotNull null
            val courseInfo = courseMap[idElement.content] ?: return@mapNotNull null
            val day = detail["day"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val startNode = detail["startNode"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val step = detail["step"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val startWeek = detail["startWeek"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val endWeek = detail["endWeek"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val type = detail["type"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val weeks = buildList {
                for (i in startWeek..endWeek) {
                    if (type == 0 || (type == 1 && i % 2 != 0) || (type == 2 && i % 2 == 0)) add(i)
                }
            }
            buildJsonObject {
                put("name", JsonPrimitive(courseInfo))
                put("teacher", JsonPrimitive(detail["teacher"]?.jsonPrimitive?.contentOrNullSafe() ?: ""))
                put("position", JsonPrimitive(detail["room"]?.jsonPrimitive?.contentOrNullSafe() ?: ""))
                put("day", JsonPrimitive(day))
                put("startSection", JsonPrimitive(startNode))
                put("endSection", JsonPrimitive(startNode + step - 1))
                put("weeks", kotlinx.serialization.json.JsonArray(weeks.map { JsonPrimitive(it) }))
            }
        }
    }
}
