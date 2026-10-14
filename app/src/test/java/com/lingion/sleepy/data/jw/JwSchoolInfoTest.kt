package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T13 — JwSchoolInfo status 字段语义测试（isSupported/isGrad/isPending/hasUrl）。
 */
class JwSchoolInfoTest {

    private fun school(status: String, url: String = "https://jw.example.edu.cn") = JwSchoolInfo(
        sortKey = "T", name = "测试大学", url = url, type = JwProtocol.TYPE_ZF_NEW,
        status = status, aliases = emptyList(), sortKeyFull = "ceshidaxue"
    )

    @Test
    fun `supported status is supported and clickable`() {
        val s = school(JwSchoolInfo.STATUS_SUPPORTED)
        assertTrue(s.isSupported)
        assertFalse(s.isGrad)
        assertFalse(s.isPending)
        assertTrue(s.hasUrl)
    }

    @Test
    fun `pending status is not supported`() {
        val s = school(JwSchoolInfo.STATUS_PENDING)
        assertFalse(s.isSupported)
        assertFalse(s.isGrad)
        assertTrue(s.isPending)
    }

    @Test
    fun `grad_supported is supported and grad`() {
        val s = school(JwSchoolInfo.STATUS_GRAD_SUPPORTED)
        assertTrue(s.isSupported)
        assertTrue(s.isGrad)
        assertFalse(s.isPending)
    }

    @Test
    fun `grad_pending is pending and grad`() {
        val s = school(JwSchoolInfo.STATUS_GRAD_PENDING)
        assertFalse(s.isSupported)
        assertTrue(s.isGrad)
        assertTrue(s.isPending)
    }

    @Test
    fun `legacy status is not supported`() {
        val s = school(JwSchoolInfo.STATUS_LEGACY)
        assertFalse(s.isSupported)
        assertFalse(s.isGrad)
        assertFalse(s.isPending)
    }

    @Test
    fun `blank url means not clickable`() {
        val s = school(JwSchoolInfo.STATUS_SUPPORTED, url = "")
        assertFalse("URL 为空的学校不可点", s.hasUrl)
        assertTrue(s.isSupported) // 但 status 仍是 supported
    }

    @Test
    fun `status constants match lifecycle vocabulary`() {
        // 前瞻闸: 5 个生命周期常量与 strings.xml/T9 词表一致
        assertEquals(
            setOf("supported", "pending", "grad_supported", "grad_pending", "legacy", "unverified", "grad_unverified"),
            setOf(
                JwSchoolInfo.STATUS_SUPPORTED, JwSchoolInfo.STATUS_PENDING,
                JwSchoolInfo.STATUS_GRAD_SUPPORTED, JwSchoolInfo.STATUS_GRAD_PENDING,
                JwSchoolInfo.STATUS_LEGACY, JwSchoolInfo.STATUS_UNVERIFIED, JwSchoolInfo.STATUS_GRAD_UNVERIFIED
            )
        )
    }

    @Test
    fun `enableFetch defaults false and copies preserve it`() {
        val s = JwSchoolInfo("T", "测试", "https://a.edu", JwProtocol.TYPE_ZF_NEW)
        assertFalse("enableFetch 默认 false", s.enableFetch)
        val enabled = s.copy(enableFetch = true)
        assertTrue(enabled.enableFetch)
    }

    @Test fun `unverified admission requires a web URL and declared protocol`() {
        val candidate = school(JwSchoolInfo.STATUS_UNVERIFIED)
        assertTrue(candidate.canImport)
        assertFalse(candidate.isSupported)
        assertFalse(candidate.copy(type = null).canImport)
        assertFalse(candidate.copy(type = "unknown_future_protocol").canImport)
        for (url in listOf("", "javascript:alert(1)", "file:///tmp/page", "https:///missing-host", "https://user@school.edu/")) {
            assertFalse(url, candidate.copy(url = url).canImport)
        }
        assertFalse(school(JwSchoolInfo.STATUS_PENDING).canImport)
        assertFalse(school("future_status").canImport)
        assertTrue(candidate.copy(status = JwSchoolInfo.STATUS_GRAD_UNVERIFIED).isGrad)
    }

    @Test fun `stable keys preserve legacy identity and survive metadata edits`() {
        val original = school(JwSchoolInfo.STATUS_SUPPORTED)
        assertEquals("T_测试大学", original.stableKey)
        val identified = original.copy(catalogId = "moe:123")
        assertEquals(identified.stableKey, identified.copy(name = "新校名", url = "https://new.edu/").stableKey)
    }
}
