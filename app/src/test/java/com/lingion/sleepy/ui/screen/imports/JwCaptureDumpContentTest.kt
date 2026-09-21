package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.jw.JwSchoolInfo
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * 验证 buildZip 产出确实含五文件 + summary 字段 (不只是契约层)。
 *
 * 是源码扫描测试的补充 — 契约测试锁"必有这些代码",内容测试锁"代码运行起来确实产生这些文件"。
 */
class JwCaptureDumpContentTest {

    private fun stubSchool() = JwSchoolInfo(
        sortKey = "test/xju_post",
        name = "新疆大学",
        url = "https://yjspy.xju.edu.cn",
        type = "xju_post",
    )

    private fun stubResult() = FrameCaptureResult(
        selectedFramePath = listOf("(top)", "PageFrame"),
        html = "<html><body>孙冬璞 高级算法</body></html>",
        matchedAnchors = listOf("_dgdata"),
        courseCount = 12,
        status = FrameCaptureStatus.WRONG_PAGE,
        blockedFrames = emptyList(),
        retryCount = 3,
        maxDepthReached = 2,
        skippedFrames = emptyList(),
        diagnosticHint = "当前页面未检测到课表容器",
    )

    @Test
    fun buildZip_contains_all_five_files() {
        val bytes = JwCaptureDump.buildZip(
            ctx = androidContext(),
            school = stubSchool(),
            result = stubResult(),
            domInventoryJson = "{\"url\":\"https://yjspy.xju.edu.cn\",\"total\":3,\"items\":[]}",
        )
        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                names += e.name
                zis.closeEntry()
            }
        }
        assertTrue("must contain summary.txt", names.contains("summary.txt"))
        assertTrue("must contain netlog.txt", names.contains("netlog.txt"))
        assertTrue("must contain console.txt", names.contains("console.txt"))
        assertTrue("must contain dom-inventory.txt", names.contains("dom-inventory.txt"))
        assertTrue("must contain a frames/ html entry", names.any { it.startsWith("frames/") && it.endsWith(".html") })
    }

    @Test
    fun buildZip_summary_includes_capture_fields() {
        val bytes = JwCaptureDump.buildZip(
            ctx = androidContext(),
            school = stubSchool(),
            result = stubResult(),
            domInventoryJson = null,
        )
        val summaryText = readEntry(bytes, "summary.txt")
        for (key in listOf("status=", "matchedAnchors=", "selectedFramePath=",
            "retryCount=", "diagnosticHint=", "school=", "appVersion=", "WRONG_PAGE")) {
            assertTrue("summary.txt missing $key", summaryText.contains(key))
        }
        assertTrue("matchedAnchors must contain _dgdata", summaryText.contains("_dgdata"))
        assertTrue("selectedFramePath must contain PageFrame", summaryText.contains("PageFrame"))
    }

    @Test
    fun buildZip_dom_inventory_writes_null_fallback() {
        val bytes = JwCaptureDump.buildZip(
            ctx = androidContext(),
            school = stubSchool(),
            result = stubResult(),
            domInventoryJson = null,
        )
        val inventory = readEntry(bytes, "dom-inventory.txt")
        assertTrue("dom-inventory fallback must be valid JSON", inventory.contains("\"total\":0"))
    }

    @Test
    fun buildZip_includes_selected_frame_html() {
        val bytes = JwCaptureDump.buildZip(
            ctx = androidContext(),
            school = stubSchool(),
            result = stubResult(),
            domInventoryJson = null,
        )
        val html = readEntryStartsWith(bytes, "frames/")
        assertNotNull("must have a frames/ html entry", html)
        assertTrue("frame html must contain original outerHTML", html!!.contains("孙冬璞"))
        assertTrue("frame html must contain container content", html.contains("高级算法"))
    }

    private fun readEntry(bytes: ByteArray, name: String): String =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                if (e.name == name) return zis.readBytes().toString(Charsets.UTF_8)
                zis.closeEntry()
            }
            error("entry not found: $name")
        }

    @Test
    fun buildZip_contains_desktop_level_diagnostic_sections_and_manifest() {
        val bytes = JwCaptureDump.buildZip(
            ctx = androidContext(),
            school = stubSchool(),
            result = stubResult(),
            domInventoryJson = "{}",
            cookiesFull = "sid=full-cookie-value; uid=202501",
            storageJson = "{\"localStorage\":{\"token\":\"full-token\"}}",
            linksJson = "{\"links\":[\"https://xju.edu.cn/schedule\"],\"selects\":[{\"opts\":[{\"v\":\"2025-2026\"}]}]}",
        )
        val names = zipNames(bytes)
        assertTrue("desktop parity: INDEX.txt", names.contains("INDEX.txt"))
        assertTrue("desktop parity: full cookies", names.contains("cookies-full.txt"))
        assertTrue("desktop parity: storage", names.contains("5-storage/storage.json"))
        assertTrue("desktop parity: links/selects", names.contains("2-inline/links.json"))
        assertTrue("device environment", names.contains("env/device.txt"))
        assertTrue("manifest must list all files", readEntry(bytes, "INDEX.txt").contains("cookies-full.txt"))
        assertTrue("cookie value must remain unredacted", readEntry(bytes, "cookies-full.txt").contains("full-cookie-value"))
        assertTrue("storage value must remain unredacted", readEntry(bytes, "5-storage/storage.json").contains("full-token"))
    }

    @Test
    fun buildZip_writes_replayed_network_resource_bodies() {
        val replay = """[{"url":"https://yjspy.xju.edu.cn/static/app.js","status":200,"mime":"application/javascript","body":"window.__xju=\"full\""}]"""
        val bytes = JwCaptureDump.buildZip(
            ctx = null,
            school = stubSchool(),
            result = stubResult(),
            domInventoryJson = null,
            cookiesFull = null,
            storageJson = null,
            linksJson = null,
            resourceReplayJson = replay,
        )
        val names = zipNames(bytes)
        assertTrue("replayed network resources must be exported", names.any { it.startsWith("4-net-replay/") && it.endsWith(".body") })
        val body = readEntry(bytes, "4-net-replay/1.body")
        assertTrue("response body must remain unredacted", body.contains("window.__xju"))
    }

    @Test
    fun buildZip_records_failed_network_resource_capture_without_fabricating_body() {
        val replay = """[{"url":"https://cdn.example.test/app.wasm","status":0,"mime":"application/wasm","error":"cross-origin"}]"""
        val bytes = JwCaptureDump.buildZip(null, stubSchool(), stubResult(), null, null, null, null, replay)
        assertTrue("capture failure metadata must be retained", readEntry(bytes, "4-net-replay/manifest.json").contains("cross-origin"))
        assertTrue("failed capture must not fabricate a body", zipNames(bytes).none { it.endsWith(".body") })
    }

    @Test
    fun buildZip_runtime_summary_is_valid_json_and_har_is_valid_json() {
        val runtime = """
            {
              "live":[{
                "url":"https://yjspy.xju.edu.cn/api/schedule",
                "method":"POST",
                "status":200,
                "requestHeaders":{"Content-Type":"application/json"},
                "responseHeaders":{"Content-Type":"application/json"},
                "responseBody":"{\"ok\":true}"
              }],
              "replay":[{"url":"https://yjspy.xju.edu.cn/api/schedule","status":200}],
              "weeks":[{"week":1,"status":200}]
            }
        """.trimIndent()
        val bytes = JwCaptureDump.buildZip(
            ctx = null,
            school = stubSchool(),
            result = stubResult(),
            domInventoryJson = null,
            cookiesFull = null,
            storageJson = null,
            linksJson = null,
            resourceReplayJson = null,
            networkLiveJson = runtime,
        )
        val summary = org.json.JSONObject(readEntry(bytes, "6-logs/collection-summary.json"))
        assertTrue(summary.getInt("networkRecords") == 1)
        assertTrue(summary.getInt("replayRecords") == 1)
        assertTrue(summary.getInt("weekPlans") == 1)
        val har = org.json.JSONObject(readEntry(bytes, "6-logs/capture.har"))
        assertTrue(har.getJSONObject("log").getString("version") == "1.2")
        val entry = har.getJSONObject("log").getJSONArray("entries").getJSONObject(0)
        assertTrue(entry.has("time"))
        assertTrue(entry.has("cache"))
        assertTrue(entry.has("timings"))
        assertTrue(entry.getJSONObject("request").has("queryString"))
        assertTrue(entry.getJSONObject("response").has("redirectURL"))
        // HAR 1.2 spec: response.content.size is a REQUIRED number.
        val content = entry.getJSONObject("response").getJSONObject("content")
        assertTrue("HAR content.size (spec-required) must exist", content.has("size"))
        assertTrue(content.getLong("size") == content.getString("text").toByteArray(Charsets.UTF_8).size.toLong())
    }

    @Test
    fun buildZip_har_redirect_url_only_populated_for_3xx() {
        val runtime = """
            {
              "live":[
                {"url":"https://yjspy.xju.edu.cn/login","method":"GET","status":302,
                 "responseHeaders":{"Content-Type":"text/html"},"responseBody":"","finalUrl":"https://yjspy.xju.edu.cn/index"},
                {"url":"https://yjspy.xju.edu.cn/api/schedule","method":"GET","status":200,
                 "responseHeaders":{"Content-Type":"application/json"},"responseBody":"{}","finalUrl":""}
              ]
            }
        """.trimIndent()
        val bytes = JwCaptureDump.buildZip(
            ctx = null, school = stubSchool(), result = stubResult(),
            domInventoryJson = null, cookiesFull = null, storageJson = null,
            linksJson = null, resourceReplayJson = null, networkLiveJson = runtime,
        )
        val entries = org.json.JSONObject(readEntry(bytes, "6-logs/capture.har"))
            .getJSONObject("log").getJSONArray("entries")
        val redirect = entries.getJSONObject(0).getJSONObject("response")
        val plain = entries.getJSONObject(1).getJSONObject("response")
        assertTrue("redirectURL filled only when 3xx", redirect.getString("redirectURL") == "https://yjspy.xju.edu.cn/index")
        assertTrue("non-redirect entry must have empty redirectURL", plain.getString("redirectURL") == "")
    }

    @Test
    fun buildZip_collect_log_lists_every_exported_file() {
        val runtime = """
            {
              "live":[{"url":"https://yjspy.xju.edu.cn/a","method":"GET","status":200,
                       "responseHeaders":{"Content-Type":"text/plain"},"responseBody":"bodyA"}],
              "replay":[{"url":"https://yjspy.xju.edu.cn/b","status":200}],
              "weeks":[{"week":3,"status":200}]
            }
        """.trimIndent()
        val bytes = JwCaptureDump.buildZip(
            ctx = null, school = stubSchool(), result = stubResult(),
            domInventoryJson = null, cookiesFull = null, storageJson = null,
            linksJson = null, resourceReplayJson = null, networkLiveJson = runtime,
        )
        val collectLog = readEntry(bytes, "6-logs/collect-log.txt")
        for (line in listOf("summary.txt", "4-net-live/1.body", "4-net-replay-weeks/manifest.json", "6-logs/capture.har")) {
            assertTrue("collect-log.txt must record $line", collectLog.contains(line))
        }
    }

    @Test
    fun buildZip_writes_all_frame_snapshots_when_available() {
        val result = stubResult().copy(allFrames = listOf(
            "(top)" to "<html>top</html>",
            "(top)_PageFrame" to "<html>孙冬璞 frame</html>",
        ))
        val names = zipNames(JwCaptureDump.buildZip(null, stubSchool(), result, null))
        assertTrue("all frame html must be exported", names.count { it.startsWith("frames/") } >= 2)
    }

    @Test
    fun buildZip_week_replay_manifest_records_concurrency_mode() {
        val runtime = """
            {
              "weeks":[{"week":1,"status":200},{"week":2,"status":0,"error":"abort"}]
            }
        """.trimIndent()
        val bytes = JwCaptureDump.buildZip(
            ctx = null, school = stubSchool(), result = stubResult(),
            domInventoryJson = null, cookiesFull = null, storageJson = null,
            linksJson = null, resourceReplayJson = null, networkLiveJson = runtime,
        )
        val manifestText = readEntry(bytes, "4-net-replay-weeks/manifest.json")
        val manifest = org.json.JSONArray(manifestText)
        assertTrue(manifest.length() == 2)
        assertTrue(manifest.getJSONObject(0).getInt("week") == 1)
    }

    @Test
    fun buildZip_weeks_manifest_written_when_only_weeks_present() {
        val runtime = """{"weeks":[{"week":1,"status":200}]}"""
        val bytes = JwCaptureDump.buildZip(
            ctx = null, school = stubSchool(), result = stubResult(),
            domInventoryJson = null, cookiesFull = null, storageJson = null,
            linksJson = null, resourceReplayJson = null, networkLiveJson = runtime,
        )
        assertTrue(zipNames(bytes).contains("4-net-replay-weeks/manifest.json"))
    }

    /** 审计#2: 裸数组形态(exportJsNetwork 默认值)必须归一成 {live:[…]}, 不能静默清空。 */
    @Test
    fun buildZip_network_live_accepts_bare_array_shape() {
        val runtime = """[{"url":"https://yjspy.xju.edu.cn/a","method":"GET","status":200,"responseHeaders":{"Content-Type":"text/plain"},"responseBody":"bodyA"}]"""
        val bytes = JwCaptureDump.buildZip(
            ctx = null, school = stubSchool(), result = stubResult(),
            domInventoryJson = null, cookiesFull = null, storageJson = null,
            linksJson = null, resourceReplayJson = null, networkLiveJson = runtime,
        )
        val names = zipNames(bytes)
        assertTrue("bare-array live rows must be kept", names.contains("4-net-live/manifest.json"))
        assertTrue("response body must be written", names.contains("4-net-live/1.body"))
        val manifest = org.json.JSONArray(readEntry(bytes, "4-net-live/manifest.json"))
        assertTrue(manifest.length() == 1)
        assertTrue(readEntry(bytes, "4-net-live/1.body").contains("bodyA"))
    }

    /** 审计#1: 双引号包裹的 eval 结果(快照路径原样回传)必须解包后再解析。 */
    @Test
    fun buildZip_network_live_accepts_double_quoted_eval_result() {
        // evaluateJavascript returns the stringified result *as a quoted JSON string literal*:
        // e.g. input {"live":[…]} comes back as "{\"live\":[…]}" (leading + trailing quote).
        val inner = """{"live":[{"url":"https://yjspy.xju.edu.cn/b","method":"GET","status":200,"responseBody":"bodyB"}]}"""
        val runtime = "\"" + inner.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val bytes = JwCaptureDump.buildZip(
            ctx = null, school = stubSchool(), result = stubResult(),
            domInventoryJson = null, cookiesFull = null, storageJson = null,
            linksJson = null, resourceReplayJson = null, networkLiveJson = runtime,
        )
        assertTrue(zipNames(bytes).contains("4-net-live/manifest.json"))
        assertTrue(readEntry(bytes, "4-net-live/1.body").contains("bodyB"))
    }

    private fun zipNames(bytes: ByteArray): Set<String> = buildSet {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                add(e.name)
                zis.closeEntry()
            }
        }
    }

    private fun readEntryStartsWith(bytes: ByteArray, prefix: String): String? {
        val zis = ZipInputStream(ByteArrayInputStream(bytes))
        try {
            while (true) {
                val e = zis.nextEntry ?: break
                if (e.name.startsWith(prefix) && e.name.endsWith(".html")) {
                    return zis.readBytes().toString(Charsets.UTF_8)
                }
                zis.closeEntry()
            }
            return null
        } finally {
            zis.close()
        }
    }

    /** buildZip 不消费 ctx (纯逻辑), 直接传 null。 */
    private fun androidContext(): android.content.Context? = null
}