package com.lingion.sleepy.data.jw

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * WakeUP 分享协议活体交叉验证 — 6.6.2 逆向战役收尾(2026-10-28)。
 *
 * 两模式, 全部环境变量闸, CI 无变量 = Assume 跳过:
 *  1. SLEEPY_WAKEUP_XINPUT=<path>: 离线重放 — 对账 vendor JS 在真实 api.wakeup.fun
 *     上取回的完整握手产物(signA/rand10/signB/token/rc4Key/两段请求体/课表响应),
 *     Kotlin 输出必须与 JS 逐字节相等; 服务端对假口令验签通过(errNo=0)后下发
 *     解密为空串 shareData 的载荷, 两侧必须落在同一成功空串分支(逐字节同)。
 *  2. SLEEPY_WAKEUP_LIVE=1: 真活体 — Kotlin 客户端直接向真实服务端跑
 *     antispam 全链(makeSignA → POST → extractSignB → tokenFromSignB)并
 *     用假口令走完签名分享请求; 签名链任何一处与服务端不一致都会以
 *     校验异常或 errNo=6 路径暴露。口令/签名/token 不落日志。
 *
 * 产出 xinput.json 的采集器: tools/wakeup-live-verify.mjs(node vm 载 vendor 原文,
 * 默认读 assets/third_party/shiguang/js/GLOBAL_TOOLS/wake_up.js, 可 SLEEPY_WAKEUP_VENDOR_JS 覆盖)。
 */
class WakeUpLiveCrossVerifyTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadXinput(): JsonObject? {
        val path = System.getenv("SLEEPY_WAKEUP_XINPUT") ?: return null
        val file = File(path)
        if (!file.isFile) return null
        return json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
    }

    private fun str(root: JsonObject, key: String): String =
        root[key]!!.jsonPrimitive.content

    @Test
    fun `离线重放 真实服务器握手产物 Kotlin与vendorJS逐字节相等`() {
        val x = loadXinput()
        assumeTrue("未提供 SLEEPY_WAKEUP_XINPUT, 跳过活体产物重放", x != null)
        requireNotNull(x)

        val rand10 = str(x, "rand10")
        val signA = str(x, "signA")
        val signB = str(x, "signB")
        val token = str(x, "token")
        val rc4Key = str(x, "rc4Key")
        val code = str(x, "code")
        val nowMs = str(x, "nowMs").toLong()

        // 设备与公共参数按 JS 侧 dump 原样重建
        val dj = x["device"]!!.jsonObject
        val device = WakeUpShareProtocol.Device(
            androidId = str(dj, "androidId"), sdk = str(dj, "sdk"), device = str(dj, "device"),
            brand = str(dj, "brand"), screensize = str(dj, "screensize"), abis = str(dj, "abis"),
            appBit = str(dj, "appBit"), appId = str(dj, "appId"), downloadType = str(dj, "downloadType"),
            nt = str(dj, "nt"), province = str(dj, "province"), city = str(dj, "city"),
            area = str(dj, "area"), deviceId = str(dj, "deviceId"), operatorid = str(dj, "operatorid"),
            adid = str(dj, "adid"), did = str(dj, "did"), cuid = str(dj, "cuid"),
        )
        val common = (x["commonParams"]!!.jsonArray).map { pair ->
            val arr = pair.jsonArray
            arr[0].jsonPrimitive.content to arr[1].jsonPrimitive.content
        }

        // 1) signA: 同 rand10 同 cuid 必须逐字节复现 vendor DES+nativeHexEncode 输出
        val (kSignA, kRand10) = WakeUpShareProtocol.makeSignA(device.cuid, rand10)
        assertEquals("vendor signA 复现", signA, kSignA)
        assertEquals("rand10 注入回显", rand10, kRand10)

        // 2) antispam 请求体逐字节
        assertEquals(
            "antispam body 逐字节", str(x, "antispamBody"),
            WakeUpShareProtocol.antispamBody(kSignA, common)
        )

        // 3) 真实 signB → token: 服务端下发的签名, Kotlin DES 解密链必须解出同一 token
        assertEquals("tokenFromSignB 对真实响应", token, WakeUpShareProtocol.tokenFromSignB(signB, rand10))

        // 4) rc4Key 对真实 token
        assertEquals("getRc4Key", rc4Key, WakeUpShareProtocol.getRc4Key(token))

        // 5) 分享请求体逐字节(真实 nowMs + 假口令 + 真实 token 的全签名链)
        val req = WakeUpShareProtocol.buildShareRequest(code, token, device, common, nowMs)
        assertEquals("share body 逐字节", str(x, "shareBody"), req.body)
        assertEquals("share rc4Key", rc4Key, req.rc4Key)

        // 6) 假口令的真实载荷: 服务端验签通过(errNo=0)后 data.data 解密得到
        //    shareData=""(空串)。vendor 侧原样返回空串, Kotlin 必须逐字节一致。
        val shareJson = json.parseToJsonElement(str(x, "shareJson"))
        val jsShareData = (x["jsShareData"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
            ?: throw AssertionError("采集器未产出 jsShareData")
        val kotlinShareData = WakeUpShareProtocol.decodeSharePayload(shareJson, rc4Key)
        assertEquals("两侧 shareData 逐字节相等", jsShareData, kotlinShareData)
    }

    @Test
    fun `活体握手 Kotlin直连真实服务端 全链通过`() = runBlocking {
        assumeTrue("未设 SLEEPY_WAKEUP_LIVE, 跳过活体直连", System.getenv("SLEEPY_WAKEUP_LIVE") == "1")
        val constants = WakeUpShareProtocol.V6170
        val device = WakeUpShareProtocol.createDevice()
        val commonParams = WakeUpShareProtocol.makeCommonParams(device)

        // Stage 1: antispam 真请求 — 签名链任何偏差都会在校验或异常路径暴露
        val (signA, rand10) = WakeUpShareProtocol.makeSignA(device.cuid)
        val antispamText = javaFetch(constants.host + constants.antispamPath, WakeUpShareProtocol.antispamBody(signA, commonParams), device)
        val antispamJson = json.parseToJsonElement(antispamText)
        val signB = WakeUpShareProtocol.extractSignB(antispamJson)
        assertTrue("服务端未返回 signB", signB.isNotEmpty())
        val token = WakeUpShareProtocol.tokenFromSignB(signB, rand10) // rand10 自检: 解不出即抛
        assertEquals("服务端 token 长度", 10, token.length)

        // Stage 2: 假口令走完整签名分享请求。真机实测形态(2026-10-28): 服务端验签
        // 通过返回 errNo=0, data.data 解密为 {"shareData":""} → decodeSharePayload
        // 成功返回空串; 签名被拒则 errNo=6、data 为空对象 → 落「未包含加密数据」。
        // 断言: 必须走成功空串分支 = Kotlin 签名链被真实服务端接受。
        val req = WakeUpShareProtocol.buildShareRequest("ZzQwFakeProbeCode1234567890", token, device, commonParams, constants = constants)
        val shareText = javaFetch(constants.host + constants.sharePath, req.body, device)
        val shareJson = json.parseToJsonElement(shareText)
        val shareData = runCatching { WakeUpShareProtocol.decodeSharePayload(shareJson, req.rc4Key) }
            .getOrElse { throw AssertionError("服务端拒绝 Kotlin 签名链: ${it.message}", it) }
        assertEquals("假口令应答形态(签名已验通)", "", shareData)
    }

    /** 与 WakeUpShareClient.postForm 相同的头部与超时(该对象 private, 测试内以 HttpURLConnection 复刻)。 */
    private fun javaFetch(url: String, body: String, device: WakeUpShareProtocol.Device): String {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("na__zyb_source__", "wakeup")
            setRequestProperty("zyb-cuid", device.cuid)
            setRequestProperty("zyb-adid", device.adid)
            if (device.did.isNotEmpty()) setRequestProperty("zyb-did", device.did)
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            check(code in 200..299) { "HTTP $code" }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
