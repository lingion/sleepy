package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T5 新增: 验证 JwImportViewModel.parseZfNewBridgeResult 的三层语义拆分。
 * 不依赖 Android, 纯 JVM。
 */
class JwImportViewModelBridgeTest {

    @Test
    fun `parse ok payload extracts kbList JSON`() {
        val ok = org.json.JSONObject().apply {
            put("ok", true)
            put("data", "{\"kbList\":[{\"kcmc\":\"高等数学\",\"xqj\":1,\"jc\":\"1-2\",\"zcd\":\"1-16周\",\"cdmc\":\"教一101\",\"xm\":\"张老师\"}]}")
            put("xnm", "2024")
            put("xqm", "3")
            put("emptySemester", false)
            put("format", "zf_new")
        }.toString()
        val (ok2, payload, kind) = JwImportViewModel.parseZfNewBridgeResult(ok)
        assertTrue("ok 应为 true", ok2)
        assertEquals("kind 应为空", "", kind)
        assertTrue("payload 含 kbList", payload.contains("\"kbList\""))
        // 进一步喂给 JwNewZfParser 验证 T4 已修好 jc='1-2'
        val courses = JwNewZfParser(payload).generateCourseList()
        assertEquals("应解出 1 门课", 1, courses.size)
        assertEquals("高等数学", courses[0].name)
        assertEquals(1, courses[0].day)
        assertEquals(1, courses[0].startNode)
        assertEquals(2, courses[0].endNode)
        assertEquals("张老师", courses[0].teacher)
        assertEquals("教一101", courses[0].room)
    }

    @Test
    fun `parse session expired raw returns SESSION_EXPIRED`() {
        val raw = """{"ok":false,"kind":"SESSION_EXPIRED","err":"会话已过期","format":"zf_new"}"""
        val (ok, payload, kind) = JwImportViewModel.parseZfNewBridgeResult(raw)
        assertFalse("ok 应为 false", ok)
        assertEquals("SESSION_EXPIRED", kind)
        assertTrue("payload 应含 err 信息", payload.contains("会话已过期"))
    }

    @Test
    fun `parse empty semester raw returns ok with empty kbList`() {
        val raw = """{"ok":true,"data":"{\"kbList\":[]}","emptySemester":true,"format":"zf_new"}"""
        val (ok, payload, kind) = JwImportViewModel.parseZfNewBridgeResult(raw)
        assertTrue("ok 应为 true (有合法空 JSON)", ok)
        // 调用方应再调 JwNewZfParser 验证 courses.isEmpty()
        val courses = JwNewZfParser(payload).generateCourseList()
        assertEquals("应解出 0 门课 (kbList=[])", 0, courses.size)
    }

    @Test
    fun `parse format error on malformed raw returns FORMAT_ERROR`() {
        val raw = "not-json-at-all"
        val (ok, payload, kind) = JwImportViewModel.parseZfNewBridgeResult(raw)
        assertFalse(ok)
        assertEquals("FORMAT_ERROR", kind)
    }

    @Test
    fun `parse raw without format field returns OTHER`() {
        val raw = """{"ok":true,"data":"{\"foo\":1}"}"""
        val (ok, payload, kind) = JwImportViewModel.parseZfNewBridgeResult(raw)
        assertFalse("缺 format 应返 ok=false", ok)
        assertEquals("OTHER", kind)
    }

    @Test
    fun `zf_new fetch js constant contains path fingerprint and kbList sniff`() {
        // JS 契约静态校验: 关键锚点必须在
        val js = com.lingion.sleepy.ui.screen.imports.ZF_NEW_FETCH_JS
        assertTrue(js.contains("/jwglxt/"))
        assertTrue(js.contains("/kbcx/"))
        assertTrue(js.contains("xskbcx_cxXsgrkb.html"))
        assertTrue(js.contains("kbList"))
        assertTrue(js.contains("SESSION_EXPIRED"))
        assertTrue(js.contains("NOT_ON_TIMETABLE"))
        assertTrue(js.contains("parseZfNewBridgeResult") || js.contains("format:'zf_new'"))
        // 2026-09 新版正方裸 /kbcx/ (广东医科等): pathPrefix 推导后 API 不再硬编 /jwglxt
        assertTrue("apiPath 必须通过 pathPrefix 拼接, 不能硬编 /jwglxt/kbcx",
            js.contains("pathPrefix + '/kbcx/xskbcx_cxXsgrkb.html"))
    }
}
