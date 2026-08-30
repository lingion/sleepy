package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JwFetchProtocolTest {
    private val heu = JwSchoolInfo("H", "HRBEU", url = "https://jwgl.hrbeu.edu.cn/jwapp/", type = JwProtocol.TYPE_WISEDU)
    private val webvpnHeu = heu.copy(url = "https://webvpn.hrbeu.edu.cn/http/9c1b2d/jwapp/sys/wdkb/modules/xskcb/xskcb.do")
    private val zfnew = JwSchoolInfo("L", "临沂大学", url = "https://jwgl.lyu.edu.cn/jwglxt", type = JwProtocol.TYPE_ZF_NEW)
    private val zfnewWebvpn = zfnew.copy(url = "https://webvpn.lyu.edu.cn/http/7f3a9c/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151")
    private val qz = JwSchoolInfo("J", "JSNU", url = "https://jwxt.jsnu.edu.cn/jsxsd/", type = JwProtocol.TYPE_QZ)
    private val qzWebvpn = qz.copy(url = "https://webvpn.example.edu.cn/webvpn/jwxt.jsnu.edu.cn/jsxsd/xskb/xskb_list.do")
    private val zfold = JwSchoolInfo("X", "XUST", url = "https://jw.xust.edu.cn/xskbcx.aspx", type = JwProtocol.TYPE_ZF)

    @Test fun `wisedu HRBEU direct`() {
        val url = "https://jwgl.hrbeu.edu.cn/jwapp/sys/wdkb/modules/xskcb/xskcb.do"
        assertEquals(FetchKind.WISEDU, JwFetchProtocol.pick(heu, url))
    }

    @Test fun `wisedu HRBEU WebVPN httphex path`() {
        val url = "https://webvpn.hrbeu.edu.cn/http/9c1b2d/jwapp/sys/wdkb/modules/xskcb/xskcb.do"
        assertEquals("hostname 改了但路径 /jwapp/ 在 — 仍应选 WISEDU",
            FetchKind.WISEDU, JwFetchProtocol.pick(webvpnHeu, url))
    }

    @Test fun `wisedu HRBEU WebVPN webhost path`() {
        val url = "https://webvpn.hrbeu.edu.cn/webvpn/jwgl.hrbeu.edu.cn/jwapp/sys/wdkb/modules/xskcb/xskcb.do"
        assertEquals(FetchKind.WISEDU, JwFetchProtocol.pick(webvpnHeu, url))
    }

    @Test fun `zf_new picks even without URL yet by school type`() {
        val url = "https://jwgl.lyu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"
        assertEquals(FetchKind.ZF_NEW, JwFetchProtocol.pick(zfnew, url))
    }

    @Test fun `zf_new gnmkdm extracted from URL`() {
        val url = "https://jwgl.lyu.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N253508"
        assertEquals("N253508", JwFetchProtocol.extractGnmkdm(url, "N2151"))
    }

    @Test fun `zf_new gnmkdm falls back to default N2151`() {
        val url = "https://jwgl.lyu.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkb.html"
        assertEquals("N2151", JwFetchProtocol.extractGnmkdm(url, "N2151"))
    }

    @Test fun `zf_new WebVPN httphex still picked`() {
        val url = "https://webvpn.lyu.edu.cn/http/7f3a9c/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151"
        assertEquals(FetchKind.ZF_NEW, JwFetchProtocol.pick(zfnewWebvpn, url))
    }

    @Test fun `qz WebVPN webhost still picked`() {
        // qz WebVPN 形态: host 段包含 jsxsd... 按 enableFetch 默认策略, JSNU 未启用 → null (走 outerHTML)
        val url = "https://webvpn.example.edu.cn/webvpn/jwxt.jsnu.edu.cn/jsxsd/xskb/xskb_list.do"
        assertNull("未启用 enableFetch 的 QZ 默认走 outerHTML", JwFetchProtocol.pick(qzWebvpn, url))
    }

    @Test fun `qz disabled by default unless enableFetch true`() {
        val url = "https://jwxt.jsnu.edu.cn/jsxsd/xskb/xskb_list.do"
        assertNull("默认 QZ 走 outerHTML,避免给未验证学校强行 fetch 出 0 课",
            JwFetchProtocol.pick(qz, url))
        val qzEnabled = qz.copy(enableFetch = true)
        assertEquals(FetchKind.QZ, JwFetchProtocol.pick(qzEnabled, url))
    }

    @Test fun `zfold always returns null no fetch for old zf`() {
        val url = "https://jw.xust.edu.cn/xskbcx.aspx"
        assertNull("老正方 xskbcx.aspx 服务端直出 HTML,fetch 零收益",
            JwFetchProtocol.pick(zfold, url))
    }

    @Test fun `pathSegment returns the protocol path without leading slash`() {
        assertEquals("jwapp",  JwFetchProtocol.pathSegment(FetchKind.WISEDU))
        assertEquals("jwglxt", JwFetchProtocol.pathSegment(FetchKind.ZF_NEW))
        assertEquals("jsxsd",  JwFetchProtocol.pathSegment(FetchKind.QZ))
    }

    @Test fun `stripWebvpnPrefix removes http hex and webhost variants`() {
        assertEquals("/jwapp/sys/wdkb/modules/xskcb/xskcb.do",
            JwFetchProtocol.stripWebvpnPrefix("/http/9c1b2d/jwapp/sys/wdkb/modules/xskcb/xskcb.do"))
        assertEquals("/jwglxt/kbcx/xskbcx_cxXsgrkb.html",
            JwFetchProtocol.stripWebvpnPrefix("/webvpn/jwgl.lyu.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkb.html"))
        assertEquals("/jwapp/sys/wdkb/modules/xskcb/xskcb.do",
            JwFetchProtocol.stripWebvpnPrefix("/jwapp/sys/wdkb/modules/xskcb/xskcb.do"))
    }
}
