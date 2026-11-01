package com.lingion.sleepy.console

import com.lingion.sleepy.console.ui.Bounds
import com.lingion.sleepy.console.ui.UiFingerprint
import com.lingion.sleepy.console.ui.UiNode
import com.lingion.sleepy.console.ui.UiRuntime
import com.lingion.sleepy.console.ui.UiSelector
import com.lingion.sleepy.console.ui.diffFingerprints
import com.lingion.sleepy.console.ui.fingerprintOf
import com.lingion.sleepy.console.handlers.UiOpsHandlers
import com.lingion.sleepy.console.ui.parseSelector
import com.lingion.sleepy.console.ui.selectNodes
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 3 ui-ops JVM 单测 — 纯逻辑面：selector 匹配优先级、多命中取 index、
 * UI 指纹 diff 结构、四端点 handler 信封（经 fake [UiRuntime]，不碰 Android）。
 */
class UiSelectorUnitTest {

    private fun node(
        tag: String? = null,
        text: String? = null,
        className: String? = null,
        role: String? = null,
        clickable: Boolean = false,
        reachable: Boolean = true,
        left: Int = 0,
        top: Int = 0,
    ) = UiNode(tag, text, className, role, clickable, reachable, Bounds(left, top, left + 100, top + 50))

    private val tree = listOf(
        node("settings_theme", "Theme", "SwitchRow", role = "switch", clickable = true),
        node("settings_theme", "Theme Mode", "SwitchRow", role = "switch", clickable = true, left = 0, top = 60),
        node("mine_about", "About", "ListRow", role = "button", clickable = true),
        node("course_cell_3_2", "高等数学", null, role = "text", clickable = true, left = 200, top = 100),
        node(tag = "plain_container", text = null, className = "Box", role = null, clickable = false),
    )

    // ---- fake runtime ------------------------------------------------------

    private open inner class FakeRuntime : UiRuntime {
        var nodes: List<UiNode> = emptyList()
        var activity: String? = "MainActivity"
        var focus: String? = null
        var textHash: String = "h0"
        val calls = mutableListOf<String>()
        var screenshotBytes: ByteArray? = byteArrayOf(1, 2, 3)

        override fun snapshotNodes(): List<UiNode> = nodes
        override fun activityName(): String? = activity
        override fun focusTag(): String? = focus
        override fun visibleTextHash(): String = textHash
        override fun tap(node: UiNode?, x: Int, y: Int, longPressMs: Long?, doubleTap: Boolean): Boolean {
            calls += "tap:$x,$y,${node?.tag},${longPressMs ?: 0},$doubleTap"
            return true
        }

        override fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean {
            calls += "swipe:$x1,$y1,$x2,$y2,$durationMs"
            return true
        }

        override fun scroll(direction: String, amount: Int, node: UiNode?): Boolean {
            calls += "scroll:$direction,$amount,${node?.tag}"
            return true
        }

        override fun inputText(text: String, node: UiNode?): Boolean {
            calls += "input:$text,${node?.tag}"
            return true
        }

        override fun systemKey(key: String, keyCode: Int): Boolean {
            calls += "key:$key,$keyCode"
            return true
        }

        override fun screenshotPng(): ByteArray? = screenshotBytes
    }

    private fun req(body: String) = ConsoleRequest(body = body)

    // ---- selector 匹配 -----------------------------------------------------

    @Test
    fun testTag_exact_match_first_axis_of_priority() {
        val hits = selectNodes(tree, UiSelector(testTag = "settings_theme"))
        assertEquals(2, hits.size)
        assertEquals(listOf(0, 1), hits.map { it.order })
    }

    @Test
    fun text_exact_vs_textContains_vs_className_vs_role() {
        assertEquals(1, selectNodes(tree, UiSelector(text = "Theme")).size)
        assertEquals(2, selectNodes(tree, UiSelector(textContains = "Theme")).size)
        assertEquals(2, selectNodes(tree, UiSelector(className = "SwitchRow")).size)
        assertEquals(1, selectNodes(tree, UiSelector(role = "button")).size)
        // className 末段匹配：全限定名可用短名命中
        assertEquals(1, selectNodes(tree, UiSelector(className = "listrow")).size)
    }

    @Test
    fun all_given_criteria_AND_together() {
        val hits = selectNodes(tree, UiSelector(textContains = "Theme", role = "switch", clickable = true))
        assertEquals(2, hits.size)
        val none = selectNodes(tree, UiSelector(testTag = "settings_theme", role = "button"))
        assertTrue(none.isEmpty())
    }

    @Test
    fun multiple_hits_index_picks_nth_in_traversal_order() {
        val hits = selectNodes(tree, UiSelector(className = "SwitchRow", index = 1))
        assertEquals(1, hits.size)
        assertEquals("Theme Mode", hits[0].text)
    }

    @Test
    fun index_out_of_range_yields_empty() {
        assertTrue(selectNodes(tree, UiSelector(role = "switch", index = 5)).isEmpty())
        assertTrue(selectNodes(tree, UiSelector(role = "switch", index = -1)).isEmpty())
    }

    @Test
    fun parse_selector_from_json_all_keys() {
        val o = JSONObject("""{"testTag":"a","text":"b","textContains":"c","className":"d","role":"e","clickable":true,"index":2}""")
        val s = parseSelector(o)
        assertEquals(UiSelector("a", "b", "c", "d", "e", true, 2), s)
        assertNull(parseSelector(JSONObject("{}")))
        assertNull(parseSelector(JSONObject("""{"index":3}"""))) // 只有 index 不算选择器
    }

    // ---- 指纹 diff ---------------------------------------------------------

    @Test
    fun fingerprint_tags_sorted_unique() {
        val fp = fingerprintOf("A", tree, "settings_theme", "h1")
        assertEquals("A", fp.activity)
        assertEquals(listOf("course_cell_3_2", "mine_about", "plain_container", "settings_theme"), fp.tags)
        assertEquals("settings_theme", fp.focus)
        assertEquals("h1", fp.textHash)
    }

    @Test
    fun diff_reports_appeared_disappeared_focus_text_change() {
        val before = fingerprintOf("A", tree, null, "h0")
        val after = fingerprintOf("A", listOf(tree[0], tree[3]), "course_cell_3_2", "h1")
        val d = diffFingerprints(before, after)
        assertEquals(0, d.getJSONArray("appeared").length())
        assertEquals(listOf("mine_about", "plain_container"), d.optJSONArray("disappeared").orEmptyList())
        assertEquals("course_cell_3_2", d.getJSONObject("focus").getString("after"))
        assertTrue(d.getBoolean("textChanged"))
    }

    private fun org.json.JSONArray?.orEmptyList(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    // ---- tree handler ------------------------------------------------------

    @Test
    fun tree_handler_returns_activity_nodes_and_dump() {
        val rt = FakeRuntime().apply { nodes = tree; textHash = "h9" }
        val out = JSONObject(UiOpsHandlers.handleTree(rt))
        assertTrue(out.getBoolean("ok"))
        val data = out.getJSONObject("data")
        assertEquals("MainActivity", data.getString("activity"))
        assertEquals(5, data.getJSONArray("nodes").length())
        val n0 = data.getJSONArray("nodes").getJSONObject(0)
        assertEquals("settings_theme", n0.getString("tag"))
        // JSONObject.toString 键序不保证，逐字段断言
        val b = n0.getJSONObject("bounds")
        assertEquals(0, b.getInt("l"))
        assertEquals(0, b.getInt("t"))
        assertEquals(100, b.getInt("r"))
        assertEquals(50, b.getInt("b"))
        assertEquals("h9", data.getJSONObject("fingerprint").getString("textHash"))
        assertTrue(data.getJSONArray("dump").getString(0).contains("tag=settings_theme"))
    }

    // ---- find handler ------------------------------------------------------

    @Test
    fun find_handler_returns_hits_with_center_and_clickable() {
        val rt = FakeRuntime().apply { nodes = tree }
        val out = JSONObject(UiOpsHandlers.handleFind(req("""{"textContains":"数学"}"""), rt))
        val hits = out.getJSONObject("data").getJSONArray("hits")
        assertEquals(1, hits.length())
        val h = hits.getJSONObject(0)
        assertEquals("course_cell_3_2", h.getString("tag"))
        assertEquals(250, h.getJSONObject("center").getInt("x"))
        assertEquals(125, h.getJSONObject("center").getInt("y"))
        assertTrue(h.getBoolean("clickable"))
        assertTrue(h.getBoolean("reachable"))
    }

    @Test
    fun find_handler_rejects_empty_selector() {
        val rt = FakeRuntime()
        val out = JSONObject(UiOpsHandlers.handleFind(req("{}"), rt))
        assertFalse(out.getBoolean("ok"))
        assertEquals(400, out.getInt("code"))
    }

    @Test
    fun find_handler_rejects_non_json_body() {
        val rt = FakeRuntime()
        val out = JSONObject(UiOpsHandlers.handleFind(req("not json"), rt))
        assertFalse(out.getBoolean("ok"))
        assertEquals(400, out.getInt("code"))
    }

    @Test
    fun find_handler_no_match_is_ok_with_empty_hits() {
        val rt = FakeRuntime().apply { nodes = tree }
        val out = JSONObject(UiOpsHandlers.handleFind(req("""{"testTag":"nope"}"""), rt))
        assertTrue(out.getBoolean("ok"))
        assertEquals(0, out.getJSONObject("data").getJSONArray("hits").length())
    }

    // ---- action handler ----------------------------------------------------

    @Test
    fun action_tap_by_selector_uses_first_hit_center_and_returns_diff() {
        val rt = FakeRuntime().apply { nodes = tree }
        val out = JSONObject(UiOpsHandlers.handleAction(req("""{"action":"tap","selector":{"testTag":"mine_about"}}"""), rt))
        assertTrue(out.getBoolean("ok"))
        assertEquals(listOf("tap:50,25,mine_about,0,false"), rt.calls)
        val data = out.getJSONObject("data")
        assertEquals(1, data.getInt("matched"))
        assertTrue(data.getJSONObject("diff").has("appeared"))
    }

    @Test
    fun action_tap_multiple_hits_index_selects_target() {
        val rt = FakeRuntime().apply { nodes = tree }
        UiOpsHandlers.handleAction(req("""{"action":"tap","selector":{"className":"SwitchRow","index":1}}"""), rt)
        assertEquals(listOf("tap:50,85,settings_theme,0,false"), rt.calls)
    }

    @Test
    fun action_tap_by_coordinates_without_selector() {
        val rt = FakeRuntime().apply { nodes = tree }
        val out = JSONObject(UiOpsHandlers.handleAction(req("""{"action":"tap","x":30,"y":40}"""), rt))
        assertTrue(out.getBoolean("ok"))
        assertEquals(listOf("tap:30,40,null,0,false"), rt.calls)
        assertEquals(0, out.getJSONObject("data").getInt("matched"))
    }

    @Test
    fun action_longtap_and_doubletap_reach_runtime() {
        val rt = FakeRuntime().apply { nodes = tree }
        UiOpsHandlers.handleAction(req("""{"action":"longTap","x":10,"y":10,"durationMs":800}"""), rt)
        UiOpsHandlers.handleAction(req("""{"action":"doubleTap","x":11,"y":11}"""), rt)
        assertEquals(listOf("tap:10,10,null,800,false", "tap:11,11,null,0,true"), rt.calls)
    }

    @Test
    fun action_swipe_direction_from_point_computes_end() {
        val rt = FakeRuntime().apply { nodes = tree }
        UiOpsHandlers.handleAction(req("""{"action":"swipe","x":100,"y":200,"direction":"up","amount":300,"durationMs":150}"""), rt)
        assertEquals(listOf("swipe:100,200,100,-100,150"), rt.calls)
    }

    @Test
    fun action_swipe_to_explicit_end_wins_over_direction() {
        val rt = FakeRuntime().apply { nodes = tree }
        UiOpsHandlers.handleAction(req("""{"action":"drag","x":0,"y":0,"toX":50,"toY":60}"""), rt)
        assertEquals(listOf("swipe:0,0,50,60,120"), rt.calls)
    }

    @Test
    fun action_swipe_requires_start() {
        val rt = FakeRuntime()
        val out = JSONObject(UiOpsHandlers.handleAction(req("""{"action":"swipe","direction":"up"}"""), rt))
        assertFalse(out.getBoolean("ok"))
        assertEquals(400, out.getInt("code"))
    }

    @Test
    fun action_swipe_rejects_bad_direction() {
        val rt = FakeRuntime()
        val out = JSONObject(UiOpsHandlers.handleAction(req("""{"action":"swipe","x":1,"y":1,"direction":"diagonal"}"""), rt))
        assertFalse(out.getBoolean("ok"))
        assertEquals(400, out.getInt("code"))
    }

    @Test
    fun action_scroll_directions_forwarded() {
        val rt = FakeRuntime().apply { nodes = tree }
        UiOpsHandlers.handleAction(req("""{"action":"scroll","direction":"down","amount":400,"selector":{"testTag":"settings_theme"}}"""), rt)
        assertEquals(listOf("scroll:down,400,settings_theme"), rt.calls)
    }

    @Test
    fun action_scroll_rejects_bad_direction() {
        val rt = FakeRuntime()
        val out = JSONObject(UiOpsHandlers.handleAction(req("""{"action":"scroll","direction":"sideways"}"""), rt))
        assertFalse(out.getBoolean("ok"))
        assertEquals(400, out.getInt("code"))
    }

    @Test
    fun action_input_text_to_selector_or_focus() {
        val rt = FakeRuntime().apply { nodes = tree }
        UiOpsHandlers.handleAction(req("""{"action":"input","text":"hello","selector":{"testTag":"course_cell_3_2"}}"""), rt)
        UiOpsHandlers.handleAction(req("""{"action":"input","text":"world"}"""), rt)
        assertEquals(listOf("input:hello,course_cell_3_2", "input:world,null"), rt.calls)
    }

    @Test
    fun action_input_requires_text() {
        val rt = FakeRuntime()
        val out = JSONObject(UiOpsHandlers.handleAction(req("""{"action":"input"}"""), rt))
        assertFalse(out.getBoolean("ok"))
        assertEquals(400, out.getInt("code"))
    }

    @Test
    fun action_system_keys_back_home_recents() {
        val rt = FakeRuntime()
        UiOpsHandlers.handleAction(req("""{"action":"back"}"""), rt)
        UiOpsHandlers.handleAction(req("""{"action":"home"}"""), rt)
        UiOpsHandlers.handleAction(req("""{"action":"recents"}"""), rt)
        UiOpsHandlers.handleAction(req("""{"action":"key","keyCode":24}"""), rt)
        assertEquals(listOf("key:back,4", "key:home,3", "key:recents,187", "key:key,24"), rt.calls)
    }

    @Test
    fun action_unknown_action_or_missing_selector_target_fails() {
        val rt = FakeRuntime().apply { nodes = tree }
        val bad = JSONObject(UiOpsHandlers.handleAction(req("""{"action":"fly"}"""), rt))
        assertFalse(bad.getBoolean("ok"))
        assertEquals(400, bad.getInt("code"))
        val noHit = JSONObject(UiOpsHandlers.handleAction(req("""{"action":"tap","selector":{"testTag":"ghost"}}"""), rt))
        assertFalse(noHit.getBoolean("ok"))
        assertEquals(404, noHit.getInt("code"))
        val noTarget = JSONObject(UiOpsHandlers.handleAction(req("""{"action":"tap"}"""), rt))
        assertFalse(noTarget.getBoolean("ok"))
        assertEquals(400, noTarget.getInt("code"))
    }

    @Test
    fun action_fingerprint_diff_reflects_state_change() {
        val base = FakeRuntime().apply { nodes = tree; textHash = "before" }
        // tap 落点即状态变化：theme 第二节点消失 + 文本哈希变
        val mutating = object : UiRuntime by base {
            override fun tap(node: UiNode?, x: Int, y: Int, longPressMs: Long?, doubleTap: Boolean): Boolean {
                base.nodes = listOf(tree[0], tree[3])
                base.textHash = "after"
                return true
            }
        }
        val out = JSONObject(UiOpsHandlers.handleAction(req("""{"action":"tap","selector":{"testTag":"mine_about"}}"""), mutating))
        assertTrue(out.getBoolean("ok"))
        val diff = out.getJSONObject("data").getJSONObject("diff")
        assertEquals(listOf("mine_about", "plain_container"), diff.optJSONArray("disappeared").orEmptyList())
        assertTrue(diff.getBoolean("textChanged"))
    }

    // ---- screenshot handler ------------------------------------------------

    @Test
    fun screenshot_handler_returns_base64_png_meta() {
        val rt = FakeRuntime()
        val out = JSONObject(UiOpsHandlers.handleScreenshot(rt))
        assertTrue(out.getBoolean("ok"))
        val data = out.getJSONObject("data")
        assertEquals("png", data.getString("format"))
        assertEquals(3, data.getInt("bytes"))
        assertTrue(data.getString("image").isNotEmpty())
    }

    @Test
    fun screenshot_handler_errors_when_capture_unavailable() {
        val rt = FakeRuntime().apply { screenshotBytes = null }
        val out = JSONObject(UiOpsHandlers.handleScreenshot(rt))
        assertFalse(out.getBoolean("ok"))
        assertEquals(500, out.getInt("code"))
    }
}
