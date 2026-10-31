package com.lingion.sleepy.console.handlers

import com.lingion.sleepy.console.ConsoleBootstrap
import com.lingion.sleepy.console.Envelope
import com.lingion.sleepy.util.AppClock
import org.json.JSONObject

/**
 * /v1/clock — 虚拟时钟端点（SPEC §虚拟时钟, Task 4）。
 *
 * 语义分层：[applyCommand] 纯函数（nowMillis/realNowMillis 注入, JVM 直测）;
 * [registerAll] 桥到 AppClock 真身。requiresToken = true（Task 1 鉴权闸）。
 *
 * POST body：
 *  - {"mode":"offset","deltaMs":N}      相对当前虚拟时间平移（可负）
 *  - {"mode":"offset","dayOffset":N}    按整天平移（N×86400000）
 *  - {"mode":"absolute","epochMs":E}    虚拟"现在"钉到 E
 *  - {"mode":"absolute","dayOffset":N}  钉到 realNow + N 天（跨周/学期验证最常用）
 *  - {"mode":"reset"}                   回真实时间
 * GET → {virtualNowEpochMs, offsetMs, realNowEpochMs}。
 */
object ClockHandler {

    /** 返回 data-json 或 null（null = 400, message 在 second）。pair 第二元素错误信息。 */
    fun applyCommand(body: String, nowMillis: Long, realNowMillis: Long): Pair<String?, String?> {
        val o = try {
            JSONObject(body)
        } catch (_: Exception) {
            return null to "body must be a JSON object"
        }
        val mode = o.optString("mode")
        return when (mode) {
            "offset" -> {
                when {
                    o.has("deltaMs") -> {
                        val d = o.optLong("deltaMs")
                        AppClock.offsetBy(d)
                        ok(nowMillis = AppClock.millis()) to null
                    }
                    o.has("dayOffset") -> {
                        AppClock.offsetBy(o.optLong("dayOffset") * DAY_MS)
                        ok(nowMillis = AppClock.millis()) to null
                    }
                    else -> null to "offset requires deltaMs or dayOffset"
                }
            }
            "absolute" -> {
                when {
                    o.has("epochMs") -> {
                        AppClock.setAbsolute(o.optLong("epochMs"))
                        ok(nowMillis = AppClock.millis()) to null
                    }
                    o.has("dayOffset") -> {
                        AppClock.setAbsolute(realNowMillis + o.optLong("dayOffset") * DAY_MS)
                        ok(nowMillis = AppClock.millis()) to null
                    }
                    else -> null to "absolute requires epochMs or dayOffset"
                }
            }
            "reset" -> {
                AppClock.reset()
                ok(nowMillis = AppClock.millis()) to null
            }
            else -> null to "mode must be offset|absolute|reset"
        }
    }

    fun snapshotJson(nowMillis: Long, realNowMillis: Long, offsetMs: Long): String =
        JSONObject()
            .put("virtualNowEpochMs", nowMillis)
            .put("offsetMs", offsetMs)
            .put("realNowEpochMs", realNowMillis)
            .toString()

    private const val DAY_MS = 86_400_000L

    private fun ok(nowMillis: Long): String =
        snapshotJson(
            nowMillis = nowMillis,
            realNowMillis = System.currentTimeMillis(),
            offsetMs = AppClock.offsetMs
        )

    fun registerAll() {
        ConsoleBootstrap.addHandler("/v1/clock", "GET", requiresToken = true) {
            Envelope.ok(
                snapshotJson(
                    nowMillis = AppClock.millis(),
                    realNowMillis = System.currentTimeMillis(),
                    offsetMs = AppClock.offsetMs
                )
            )
        }
        ConsoleBootstrap.addHandler("/v1/clock", "POST", requiresToken = true) { req ->
            val (data, error) = applyCommand(req.body, AppClock.millis(), System.currentTimeMillis())
            if (data != null) Envelope.ok(data) else Envelope.err(400, error ?: "bad clock command")
        }
    }
}
