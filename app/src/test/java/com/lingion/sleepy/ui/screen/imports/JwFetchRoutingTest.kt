package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.jw.JwProtocol
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JwFetchRoutingTest {
    @Test fun `declared ZF works before a page URL is available`() {
        assertTrue(shouldUseZfNewFetch(JwProtocol.TYPE_ZF_NEW, ""))
    }

    @Test fun `ZF page fingerprints work without a declared type`() {
        listOf("/jwglxt/", "/kbcx/", "/JWGLXT/", "/KBCX/").forEach { path ->
            assertTrue(path, shouldUseZfNewFetch(null, "https://jw.example.edu.cn${path}index"))
        }
    }

    @Test fun `WebVPN paths retain ZF fallback`() {
        listOf(
            "https://vpn.example.edu.cn/http/7f3a9c/index",
            "https://vpn.example.edu.cn/webvpn/jw.example.edu.cn/jwglxt/kbcx/index",
        ).forEach { url -> assertTrue(url, shouldUseZfNewFetch(null, url)) }
    }

    @Test fun `declared IEAS cannot be claimed by ZF path fingerprints`() {
        assertFalse(shouldUseZfNewFetch(JwProtocol.TYPE_QZ_IEAS,
            "https://vpn.example.edu.cn/http/7f3a9c/kbcx/index"))
    }

    @Test fun `IEAS page overrides declared ZF including WebVPN`() {
        listOf(
            "https://jw.example.edu.cn/ieas2.1/kbcx/index",
            "https://vpn.example.edu.cn/http/7f3a9c/IEAS2.1/kbcx/index",
        ).forEach { url -> assertFalse(url, shouldUseZfNewFetch(JwProtocol.TYPE_ZF_NEW, url)) }
    }

    @Test fun `unrelated pages stay on the frame capture path`() {
        listOf("", "/xskbcx.aspx", "/jsxsd/xskb/xskb_list.do", "/jwapp/index").forEach { path ->
            assertFalse(path, shouldUseZfNewFetch(JwProtocol.TYPE_ZF, "https://jw.example.edu.cn$path"))
        }
    }
}
