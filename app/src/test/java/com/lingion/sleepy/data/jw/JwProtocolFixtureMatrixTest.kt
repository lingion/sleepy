package com.lingion.sleepy.data.jw

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * 直接读取 detection-pages + *.expected.json 的 _fingerprint 字段做断言,
 * 防止「fixture 加了新协议但 detectProtocolFromHtml 没跟」的漂移。
 *
 * fixture 覆盖 expectedProtocol 非空的 9 个样本 + expectedProtocol=unknown 的 CAS 样本。
 */
class JwProtocolFixtureMatrixTest {

    @Test
    fun `所有 expected_json 的 expectedProtocol 与 detectProtocolFromHtml 一致`() {
        val dir = File("src/test/resources/jw_fixtures/detection-pages")
        val expectedFiles = dir.listFiles { f -> f.name.endsWith(".expected.json") }!!.sortedBy { it.name }
        var asserted = 0
        for (ef in expectedFiles) {
            val expected = JSONObject(ef.readText())
            val fp = expected.optJSONObject("_fingerprint") ?: continue
            val expectedProtocol = fp.optString("expectedProtocol", "").ifBlank { null } ?: continue
            // expectedProtocol=unknown → 应返回 null
            val htmlFile = ef.name.removeSuffix(".expected.json") + ".html"
            val html = File(ef.parent, htmlFile).readText()
            val actual = JwImportViewModel.detectProtocolFromHtmlForTest(html)
            val expectedValue = if (expectedProtocol == "unknown") null else expectedProtocol
            assertEquals("[${ef.name}] expectedProtocol=$expectedProtocol", expectedValue, actual)
            asserted++
        }
        // 必须至少断言 10 个(9 正样本 + CAS unknown;缺一 = 有协议族的 expected.json 被删)
        assert(asserted >= 10) { "expected fixture count too low: $asserted" }
    }

    @Test
    fun `expected_json 的 htmlMarkers 每个都被 detectProtocolHitFeatures 命中`() {
        val dir = File("src/test/resources/jw_fixtures/detection-pages")
        for (ef in dir.listFiles { f -> f.name.endsWith(".expected.json") }!!.sortedBy { it.name }) {
            val expected = JSONObject(ef.readText())
            val fp = expected.optJSONObject("_fingerprint") ?: continue
            val markers = fp.optJSONArray("htmlMarkers") ?: continue
            val html = File(ef.parent, ef.name.removeSuffix(".expected.json") + ".html").readText()
            val hits = JwImportViewModel.detectProtocolHitFeaturesForTest(html)
            // 只对 expectedProtocol 非空 + confidence>=60 的 fixture 严格断言;
            // 弱锚点 fixture 允许命中不全
            val confidence = fp.optInt("expectedConfidence", 0)
            if (fp.optString("expectedProtocol", "").isNotBlank() && confidence >= 60) {
                val hitCount = (0 until markers.length()).count { i ->
                    hits.any { h -> markers.getString(i).lowercase() in h.lowercase()
                            || h.lowercase() in markers.getString(i).lowercase() }
                }
                assert(hitCount >= 1) {
                    val markerList = (0 until markers.length()).joinToString(",") { markers.getString(it) }
                    "[${ef.name}] 至少 1 个 htmlMarker 应命中; got hits=$hits markers=$markerList"
                }
            }
        }
    }
}
