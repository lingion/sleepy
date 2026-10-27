package com.lingion.sleepy.data.jw

import com.lingion.sleepy.data.parser.ScheduleParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WakeUp 分享口令协议 v6.1.70 Kotlin 移植的金标向量回归 — 教务跨仓验证 SOP 跨语言 invariant 铁律。
 *
 * vectors.json 由直接执行拾光 GLOBAL_TOOLS/wake_up.js（node vm 沙箱, 确定性桩:
 * crypto.getRandomValues 固定字节、Date.now=1730000000000）生成, 每个断言锁定
 * Kotlin 输出与 vendor JS 逐字节相等。出处: shiguang_warehouse
 * `schools/resources/GLOBAL_TOOLS/wake_up.js`（致谢见 LicenseScreen）。
 */
class WakeUpV6170InvariantTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val vectors: JsonObject = json.parseToJsonElement(
        requireNotNull(
            javaClass.classLoader?.getResourceAsStream("jw/wakeup/vectors.json")
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ) { "缺少金标向量 jw/wakeup/vectors.json" }
    ).jsonObject

    private fun arr(key: String): JsonArray = vectors[key]!!.jsonArray
    private fun obj(key: String): JsonObject = vectors[key]!!.jsonObject

    // ---- 常量冻结 ----

    @Test
    fun `constants match vendor WAKEUP_V6170 verbatim`() {
        val expected = obj("constants")
        val actual = WakeUpShareProtocol.V6170
        assertEquals(expected["host"]!!.jsonPrimitive.content, actual.host)
        assertEquals(expected["antispamPath"]!!.jsonPrimitive.content, actual.antispamPath)
        assertEquals(expected["sharePath"]!!.jsonPrimitive.content, actual.sharePath)
        assertEquals(expected["packageName"]!!.jsonPrimitive.content, actual.packageName)
        assertEquals(expected["versionName"]!!.jsonPrimitive.content, actual.versionName)
        assertEquals(expected["versionCode"]!!.jsonPrimitive.int, actual.versionCode)
        assertEquals(expected["channel"]!!.jsonPrimitive.content, actual.channel)
        assertEquals(expected["publicToken"]!!.jsonPrimitive.content, actual.publicToken)
        assertEquals(expected["signatureMd5"]!!.jsonPrimitive.content, actual.signatureMd5)
        assertEquals(expected["magic"]!!.jsonPrimitive.content, actual.magic)
        assertEquals(expected["signAKey"]!!.jsonPrimitive.content, actual.signAKey)
        assertEquals(expected["keySalt"]!!.jsonPrimitive.content, actual.keySalt)
    }

    // ---- MD5 ----

    @Test
    fun `md5Hex matches vendor golden pairs`() {
        for (pair in arr("md5Hex")) {
            val (input, expected) = pair.jsonArray
            assertEquals(expected.jsonPrimitive.content, WakeUpV6170Crypto.md5Hex(input.jsonPrimitive.content))
        }
    }

    @Test
    fun `md5Upper matches vendor golden pair`() {
        for (pair in arr("md5Upper")) {
            val (input, expected) = pair.jsonArray
            assertEquals(expected.jsonPrimitive.content, WakeUpV6170Crypto.md5Upper(input.jsonPrimitive.content))
        }
    }

    @Test
    fun `cuid and adid derive from androidId exactly as vendor`() {
        val ids = obj("deviceWithIds")
        assertEquals(ids["cuid"]!!.jsonPrimitive.content, WakeUpShareProtocol.cuidFromAndroidId("0000000000000000"))
        assertEquals(ids["adid"]!!.jsonPrimitive.content, WakeUpShareProtocol.adidFromAndroidId("0000000000000000"))
        val device = WakeUpShareProtocol.createDevice()
        assertEquals(ids["cuid"]!!.jsonPrimitive.content, device.cuid)
        assertEquals(ids["adid"]!!.jsonPrimitive.content, device.adid)
    }

    // ---- nativeHex 编解码 ----

    @Test
    fun `nativeHex encode matches vendor and roundtrips`() {
        val bytes = ByteArray(33) { ((it * 53 + 7) and 0xFF).toByte() }
        assertEquals(obj("nativeHex")["hex"]!!.jsonPrimitive.content, WakeUpV6170Crypto.nativeHexEncode(bytes))
        val decoded = WakeUpV6170Crypto.nativeHexDecode(obj("nativeHex")["hex"]!!.jsonPrimitive.content)
        assertEquals(obj("nativeHex")["roundtrip"]!!.jsonPrimitive.content, decoded.toHexLowerString())
        assertTrue(decoded.contentEquals(bytes))
    }

    @Test
    fun `nativeHexDecode drops ragged tail group like vendor slice`() {
        // vendor: text.slice(0, Math.floor(len/4)*4) — 残组静默丢弃
        val full = WakeUpV6170Crypto.nativeHexEncode(ByteArray(2) { (it + 1).toByte() })
        assertEquals(
            WakeUpV6170Crypto.nativeHexDecode(full).toHexLowerString(),
            WakeUpV6170Crypto.nativeHexDecode(full + "0").toHexLowerString(),
        )
    }

    // ---- 私有 DES ----

    @Test
    fun `des encrypt and decrypt match vendor golden cases`() {
        for (case in arr("des")) {
            val o = case.jsonObject
            val plain = o["plain"]!!.jsonPrimitive.content
            val key = o["key"]!!.jsonPrimitive.content
            val cipher = WakeUpV6170Crypto.desEncrypt(plain, key)
            assertEquals(o["cipherHex"]!!.jsonPrimitive.content, cipher.toHexLowerString())
            assertEquals(plain, WakeUpV6170Crypto.bytesToUtf8(WakeUpV6170Crypto.desDecrypt(cipher, key)))
        }
    }

    @Test
    fun `signA matches vendor for fixed rand10`() {
        val fixture = obj("signA")
        val (signA, echoed) = WakeUpShareProtocol.makeSignA(
            WakeUpShareProtocol.createDevice().cuid, fixture["rand10"]!!.jsonPrimitive.content,
        )
        assertEquals(fixture["signA"]!!.jsonPrimitive.content, signA)
        assertEquals(fixture["rand10"]!!.jsonPrimitive.content, echoed)
    }

    @Test
    fun `tokenFromSignB recovers vendor token and rejects rand10 mismatch`() {
        val fixture = obj("signB")
        val rand10 = vectors["fixRand10"]!!.jsonPrimitive.content
        assertEquals(fixture["token"]!!.jsonPrimitive.content, WakeUpShareProtocol.tokenFromSignB(fixture["cipherNativeHex"]!!.jsonPrimitive.content, rand10))
        // 前 10 字符校验失败必须抛(与 vendor throw 同)
        assertNotEquals(rand10, "WRONGRAND1")
        runCatching { WakeUpShareProtocol.tokenFromSignB(fixture["cipherNativeHex"]!!.jsonPrimitive.content, "ABCDEFGHIX") }
            .onSuccess { throw AssertionError("rand10 不符却未抛错: $it") }
    }

    // ---- RC4 与签名链 ----

    @Test
    fun `rc4 cases match vendor`() {
        for (case in arr("rc4Cases")) {
            val (data, key, expectedB64) = case.jsonArray
            assertEquals(
                expectedB64.jsonPrimitive.content,
                WakeUpV6170Crypto.bytesToBase64(
                    WakeUpV6170Crypto.rc4(data.jsonPrimitive.content, key.jsonPrimitive.content)
                ),
            )
        }
    }

    @Test
    fun `getRc4Key matches vendor transform chain`() {
        val fixture = obj("rc4Key")
        assertEquals(
            fixture["key"]!!.jsonPrimitive.content,
            WakeUpShareProtocol.getRc4Key(fixture["token"]!!.jsonPrimitive.content),
        )
    }

    @Test
    fun `getSign matches vendor`() {
        val b64 = WakeUpV6170Crypto.bytesToBase64("a=1b=2".toByteArray(Charsets.UTF_8))
        assertEquals(
            vectors["sign"]!!.jsonPrimitive.content,
            WakeUpShareProtocol.getSign(b64, vectors["token10"]!!.jsonPrimitive.content),
        )
    }

    @Test
    fun `buildShareRequest body matches vendor byte for byte`() {
        val fixture = obj("buildShareRequest")
        val device = WakeUpShareProtocol.createDevice()
        val request = WakeUpShareProtocol.buildShareRequest(
            code = fixture["code"]!!.jsonPrimitive.content,
            token = vectors["token10"]!!.jsonPrimitive.content,
            device = device,
            commonParams = WakeUpShareProtocol.makeCommonParams(device),
            nowMs = vectors["nowMs"]!!.jsonPrimitive.long,
        )
        assertEquals(fixture["rc4Key"]!!.jsonPrimitive.content, request.rc4Key)
        assertEquals(fixture["body"]!!.jsonPrimitive.content, request.body)
    }

    @Test
    fun `commonParams order and values match vendor list`() {
        val expected = arr("commonParams").map {
            val pair = it.jsonArray
            pair[0].jsonPrimitive.content to pair[1].jsonPrimitive.content
        }
        assertEquals(expected, WakeUpShareProtocol.makeCommonParams(WakeUpShareProtocol.createDevice()))
    }

    @Test
    fun `antispamBody follows vendor concatenation shape`() {
        val body = WakeUpShareProtocol.antispamBody(
            "SIGN A±", listOf("k1" to "v 1", "k2" to "k"),
        )
        assertEquals("data=SIGN+A%C2%B1&k1=v+1&k2=k&", body)
    }

    // ---- form 编码 ----

    @Test
    fun `androidQuote matches vendor golden pairs`() {
        for (pair in arr("formEncode")) {
            val (input, expected) = pair.jsonArray
            assertEquals(expected.jsonPrimitive.content, WakeUpV6170Crypto.androidQuote(input.jsonPrimitive.content))
        }
    }

    // ---- 口令提取与校验 ----

    @Test
    fun `extractKey and validateKey match vendor table`() {
        for (row in arr("extractKey")) {
            val (input, extracted, verdict) = row.jsonArray
            val text = input.jsonPrimitive.content
            assertEquals("extractKeyFromText: $text", extracted.jsonPrimitive.content, WakeUpShareProtocol.extractKeyFromText(text))
            val error = WakeUpShareProtocol.validateKey(text)
            if (verdict is JsonPrimitive && verdict.boolean()) {
                assertNull("validateKey should pass: $text", error)
            } else {
                assertEquals("validateKey message: $text", verdict.jsonPrimitive.content, error)
            }
        }
    }

    @Test
    fun `validateKey rejects empty input with vendor message`() {
        assertEquals("输入不能为空！", WakeUpShareProtocol.validateKey(null))
        assertEquals("输入不能为空！", WakeUpShareProtocol.validateKey("   "))
        assertEquals("", WakeUpShareProtocol.extractKeyFromText(null))
    }

    /**
     * 跨语言 invariant 铁律: Kotlin Regex.pattern 与 vendor wake_up.js 的 regex literal
     * (去掉 /u /g 标志后的源码字面量)逐字符相等 — 期望串逐字抄自
     * shiguang_warehouse GLOBAL_TOOLS/wake_up.js L48/L62/L65/L68。
     */
    @Test
    fun `key regexes are character-identical to vendor literals`() {
        // wake_up.js:48  /^[A-Za-z0-9_-]{8,200}$/
        assertEquals("^[A-Za-z0-9_-]{8,200}$", WakeUpShareProtocol.KEY_PATTERN.pattern)
        // wake_up.js:62  /分享口令(?:为)?\s*[「“"]?\s*([A-Za-z0-9_-]{1,200})\s*[」”"]?/u
        // 字符类三成员 = U+300C + U+201C + ASCII 双引号(关侧 U+300D + U+201D + ASCII), 逐字节抄自 vendor
        assertEquals(
            "分享口令(?:为)?\\s*[「“\"]?\\s*([A-Za-z0-9_-]{1,200})\\s*[」”\"]?",
            field("LABELLED_PATTERN"),
        )
        // wake_up.js:65  /[「“"]\s*([A-Za-z0-9_-]{8,200})\s*[」”"]/u
        assertEquals("[「“\"]\\s*([A-Za-z0-9_-]{8,200})\\s*[」”\"]", field("QUOTED_PATTERN"))
        // wake_up.js:68  /[A-Za-z0-9_-]{8,200}/g
        assertEquals("[A-Za-z0-9_-]{8,200}", field("BARE_PATTERN"))
    }

    private fun field(name: String): String =
        WakeUpShareProtocol.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }
            .let { (it.get(WakeUpShareProtocol) as Regex).pattern }

    // ---- extractSignB 容错取串 ----

    @Test
    fun `extractSignB matches vendor fallback chain`() {
        val inputs = listOf(
            json.parseToJsonElement("""{"data":{"data":"STR"}}"""),
            json.parseToJsonElement("""{"data":"STR2"}"""),
            json.parseToJsonElement("""{"result":{"data":"STR3"}}"""),
            JsonNull,
            json.parseToJsonElement("""{"data":5}"""),
        )
        val expected = arr("extractSignB").map { it.jsonPrimitive.content }
        assertEquals(expected, inputs.map { WakeUpShareProtocol.extractSignB(it) })
    }

    // ---- shareData 五段解析 ----

    private fun shareParts(): WakeUpShareProtocol.ShareParts =
        WakeUpShareProtocol.parseRawScheduleData(vectors["shareData"]!!.jsonPrimitive.content)

    @Test
    fun `parseRawScheduleData rejects short input with vendor message`() {
        val error = runCatching { WakeUpShareProtocol.parseRawScheduleData("{\"a\":1}\n{}") }
            .exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals("数据格式不完整，预期至少包含 5 个部分。", error!!.message)
    }

    @Test
    fun `timeSlots double-filter matches vendor`() {
        val parts = shareParts()
        val nodes = WakeUpShareProtocol.normalizeNodes(parts.uiConfig)
        assertEquals(setOf(1, 2, 3, 4, 5), nodes)
        val slots = WakeUpShareProtocol.convertTimeSlots(parts.timeSlotsRaw, nodes)
        val expected = obj("shareDataPartsParsed")["timeSlots"]!!.jsonArray
        assertEquals(expected.size, slots.size)
        for ((i, triple) in slots.withIndex()) {
            val o = expected[i].jsonObject
            assertEquals(o["number"]!!.jsonPrimitive.int, triple.first)
            assertEquals(o["startTime"]!!.jsonPrimitive.content, triple.second)
            assertEquals(o["endTime"]!!.jsonPrimitive.content, triple.third)
        }
    }

    @Test
    fun `normalizeNodes expands scalar and defaults empty`() {
        assertEquals(setOf(1, 2, 3), WakeUpShareProtocol.normalizeNodes(
            buildJsonObject { put("nodes", JsonPrimitive(3)) }))
        assertEquals(emptySet<Int>(), WakeUpShareProtocol.normalizeNodes(
            buildJsonObject { put("nodes", JsonPrimitive(0)) }))
        assertEquals(emptySet<Int>(), WakeUpShareProtocol.normalizeNodes(buildJsonObject {}))
    }

    @Test
    fun `convertToCourseJsonModel matches vendor golden courses`() {
        val expected = obj("shareDataPartsParsed")["courses"]!!.jsonArray
        val actual = WakeUpShareProtocol.convertToCourseJsonModel(shareParts())
        assertEquals(expected.map { json.encodeToString(JsonObject.serializer(), it.jsonObject) },
            actual.map { json.encodeToString(JsonObject.serializer(), it) })
    }

    @Test
    fun `date conversion samples match vendor JS Date subset`() {
        for (row in arr("dateConvertSamples")) {
            val (input, expected) = row.jsonArray
            val actual = WakeUpShareProtocol.convertToSemesterStartDate(
                if (input is JsonNull) null else input.jsonPrimitive.content)
            if (expected is JsonNull) {
                assertNull("expected null for '${input}'", actual)
            } else {
                assertEquals(expected.jsonPrimitive.content, actual)
            }
        }
    }

    @Test
    fun `courseConfig shape fields survive the port`() {
        val parts = shareParts()
        val shape = obj("courseConfigShape")
        assertEquals(shape["semesterStartDate"]!!.jsonPrimitive.content,
            WakeUpShareProtocol.convertToSemesterStartDate(parts.uiConfig["startDate"]!!.jsonPrimitive.content))
        assertEquals(shape["semesterTotalWeeks"]!!.jsonPrimitive.int, parts.uiConfig["maxWeek"]!!.jsonPrimitive.int)
        assertEquals(shape["defaultClassDuration"]!!.jsonPrimitive.int, parts.baseConfig["courseLen"]!!.jsonPrimitive.int)
        assertEquals(shape["defaultBreakDuration"]!!.jsonPrimitive.int, parts.baseConfig["theBreakLen"]!!.jsonPrimitive.int)
    }

    // ---- RC4 解密双层解包 ----

    @Test
    fun `decodeSharePayload unwraps both data shapes like vendor`() {
        val rc4Key = WakeUpShareProtocol.getRc4Key(vectors["token10"]!!.jsonPrimitive.content)
        val inner = buildJsonObject { put("shareData", JsonPrimitive(vectors["shareData"]!!.jsonPrimitive.content)) }
        val encrypted = WakeUpV6170Crypto.bytesToBase64(
            WakeUpV6170Crypto.rc4(inner.toString(), rc4Key)
        )
        val flat = json.parseToJsonElement("""{"code":0,"data":"$encrypted"}""")
        assertEquals(vectors["shareData"]!!.jsonPrimitive.content, WakeUpShareProtocol.decodeSharePayload(flat, rc4Key))
        val nested = json.parseToJsonElement("""{"code":0,"data":{"data":"$encrypted"}}""")
        assertEquals(vectors["shareData"]!!.jsonPrimitive.content, WakeUpShareProtocol.decodeSharePayload(nested, rc4Key))
    }

    @Test
    fun `decodeSharePayload throws vendor messages`() {
        val rc4Key = WakeUpShareProtocol.getRc4Key(vectors["token10"]!!.jsonPrimitive.content)
        assertEquals("WakeUP 课表响应未包含加密数据。",
            runCatching { WakeUpShareProtocol.decodeSharePayload(json.parseToJsonElement("""{"code":1}"""), rc4Key) }
                .exceptionOrNull()!!.message)
        assertEquals("WakeUP 课表响应解密失败。",
            runCatching { WakeUpShareProtocol.decodeSharePayload(json.parseToJsonElement("""{"data":"not-base64!!"}"""), rc4Key) }
                .exceptionOrNull()!!.message)
    }

    // ---- 在线判别窄窗 + 端到端离线链路 ----

    @Test
    fun `looksLikeShareCode opens only on share-code shapes`() {
        assertTrue(WakeUpShareClient.looksLikeShareCode("ZX9kQ3r7Lp"))
        assertTrue(WakeUpShareClient.looksLikeShareCode("【来自WakeUp课程表】分享口令为「Zx9kQ3r7LpTt」 快去查看吧"))
        // 离线四通路不可能命中: JSON / ICS / HTML / 裸短文本
        assertTrue(!WakeUpShareClient.looksLikeShareCode("""{"courses":[],"tableInfo":{}}"""))
        assertTrue(!WakeUpShareClient.looksLikeShareCode("BEGIN:VCALENDAR\nBEGIN:VEVENT\nEND:VEVENT\nEND:VCALENDAR"))
        assertTrue(!WakeUpShareClient.looksLikeShareCode("<table><tr><td>高数</td></tr></table>"))
        assertTrue(!WakeUpShareClient.looksLikeShareCode(""))
        assertTrue(!WakeUpShareClient.looksLikeShareCode("短"))
    }

    @Test
    fun `end to end offline chain feeds ScheduleParser with three courses`() {
        val scheduleJson = WakeUpShareClient.buildScheduleJson(shareParts())
        val result = ScheduleParser.parse(scheduleJson, 42L).getOrThrow()
        assertEquals("WakeUP 分享课表", result.tableName)
        assertEquals("2025-09-01", result.startDate)
        assertEquals(3, result.courses.size)
        val first = result.courses[0]
        assertEquals("高等数学", first.courseName)
        assertEquals("张老师", first.teacher)
        assertEquals("A101", first.room)
        assertEquals(1, first.day)
        assertEquals(1, first.startNode)
        assertEquals(2, first.step)
        assertEquals(1, first.startWeek)
        assertEquals(8, first.endWeek)
        assertEquals(42L, first.tableId)
        // 双过滤后节次 1/2/5 收割, 声明 nodesPerDay=5
        assertEquals(5, result.nodesPerDay)
        assertTrue(result.timeJson.contains("08:00"))
        assertTrue(result.timeJson.contains("14:55"))
        // 双周课与类型透传
        val english = result.courses[1]
        assertEquals("大学英语", english.courseName)
        assertEquals(2, english.startWeek)
        assertEquals(2, english.type)
    }

    @Test
    fun `buildScheduleJson omits startDate when semester date unusable`() {
        val parts = WakeUpShareProtocol.parseRawScheduleData(
            vectors["shareData"]!!.jsonPrimitive.content
                .replace("\"startDate\":\"2025/9/1\"", "\"startDate\":\"2025/13/45\"")
        )
        val root = Json.parseToJsonElement(WakeUpShareClient.buildScheduleJson(parts)).jsonObject
        assertTrue(!root.containsKey("startDate"))
    }

    private fun JsonPrimitive.boolean(): Boolean = content == "true"
}
