package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File

/**
 * T6 双层协议识别 — 参数化测试。
 *
 * 数据源:
 *  - URL 样本: src/test/resources/jw_fixtures/detection-pages + *.expected.json 的 urlMarkers
 *              + jw_fixtures/adversarial/fp-url-confusion + *.case.json 的 inputUrl
 *              + jw_fixtures/adversarial/webvpn-urls + *.txt 形态
 *  - HTML 样本: jw_fixtures/detection-pages + *.html (22 个)
 *              + jw_fixtures/adversarial/fp-url-confusion + *.html (7 个)
 */
@RunWith(Parameterized::class)
class JwProtocolDetectionTest(
    private val caseName: String,
    private val url: String?,          // null = 本 case 只测 HTML
    private val htmlFile: String?,     // null = 本 case 只测 URL;相对 src/test/resources
    private val expectedFromUrl: String?,   // detectProtocolFromUrl 期望值;null = 期望 null
    private val expectedFromHtml: String?,  // detectProtocolFromHtml 期望值;null = 期望 null
) {

    companion object {
        private const val ROOT = "src/test/resources"

        private fun loadHtml(relPath: String): String =
            File("$ROOT/$relPath").readText(Charsets.UTF_8)

        private const val DP = "jw_fixtures/detection-pages"
        private const val FP = "jw_fixtures/adversarial/fp-url-confusion"

        @JvmStatic
        @Parameters(name = "{0}")
        fun data(): Collection<Array<Any?>> {
            val c = mutableListOf<Array<Any?>>()

            // ============ 1. URL 正样本 ============
            c += arrayOf("url.wisedu.jwapp", "https://jwgl.hrbeu.edu.cn/jwapp/sys/wdkb/modules/xskcb/xskcb.do",
                null, "wisedu", null)
            c += arrayOf("url.zfnew.jwglxt", "http://jwgl.sjzc.edu.cn/jwglxt/xtgl/login_slogin.html",
                null, "zf_new", null)
            // B1 关键回归: /xtgl 无尾斜杠
            c += arrayOf("url.zfnew.xtgl-no-slash", "https://jwgl.example.edu.cn/xtgl",
                null, "zf_new", null)
            c += arrayOf("url.zfnew.xtgl-slash", "http://jwgl.hebtu.edu.cn/xtgl/",
                null, "zf_new", null)
            c += arrayOf("url.zfnew.kbcx", "https://jwgl.example.edu.cn/jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151",
                null, "zf_new", null)
            c += arrayOf("url.zf.default2", "https://jw.usts.edu.cn/default2.aspx", null, "zf", null)
            // 必须不被 xskbcx_cx 误吸
            c += arrayOf("url.zf.xskbcx-aspx", "https://jw.usts.edu.cn/xskbcx.aspx", null, "zf", null)
            c += arrayOf("url.qz.jsxsd", "http://jiaowu.tjutcm.edu.cn/jsxsd/xskb/xskb_list.do", null, "qz", null)
            c += arrayOf("url.qz.jxd-no-slash", "http://jwxt.lyu.edu.cn/jxd", null, "qz", null)
            c += arrayOf("url.qz.logon", "http://jwgl.sdust.edu.cn/Logon.do?method=logon", null, "qz", null)
            c += arrayOf("url.qz.verifycode", "http://jwgl.sdust.edu.cn/verifycode.servlet", null, "qz", null)
            c += arrayOf("url.urp.xkaction", "https://bkjw.example.edu.cn/xkAction.do?actionType=6", null, "urp", null)
            c += arrayOf("url.urpnew.thisSemester",
                "https://jwxt.imu.edu.cn/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback",
                null, "urp_new", null)
            c += arrayOf("url.urpnew.courseSelect", "https://urpjw.example.edu.cn/courseSelect", null, "urp_new", null)
            c += arrayOf("url.cf.gdut", "https://jxfw.gdut.edu.cn/xsgrkbcx!xsAllKbList.action", null, "cf", null)
            c += arrayOf("url.cf.smu", "https://zhjw.smu.edu.cn/new/xskb/list", null, "cf", null)
            c += arrayOf("url.pku.elective", "https://elective.pku.edu.cn/elective2008/syllabusV2/", null, "pku", null)
            c += arrayOf("url.bnuz.es", "https://es.bnuz.edu.cn/default.aspx", null, "bnuz", null)
            c += arrayOf("url.hnust.kdjw", "http://kdjw.hnust.cn:8080/kdjw", null, "hnust", null)
            // 2026 起教务迁 .edu.cn 域（老 hnust.cn:8080 全 404），host 前缀 kdjw/xxjw 不变
            c += arrayOf("url.hnust.kdjw-edu", "https://kdjw.hnust.edu.cn/", null, "hnust", null)
            c += arrayOf("url.hnust.xxjw-edu", "https://xxjw.hnust.edu.cn/", null, "hnust", null)

            // ============ 2. HTML 正样本(detection-pages) ============
            c += arrayOf("html.wisedu.login", null, "$DP/wisedu-login.html", null, "wisedu")
            c += arrayOf("html.zfnew.login", null, "$DP/zf-new-login.html", null, "zf_new")
            c += arrayOf("html.zf.login", null, "$DP/zf-old-login.html", null, "zf")
            c += arrayOf("html.qz.login", null, "$DP/qz-base-login.html", null, "qz")
            c += arrayOf("html.urpnew.login", null, "$DP/urp-new-login.html", null, "urp_new")
            c += arrayOf("html.urp.login", null, "$DP/urp-old-login.html", null, "urp")
            c += arrayOf("html.cf.login", null, "$DP/cf-chengfang-login.html", null, "cf")
            c += arrayOf("html.pku.login", null, "$DP/pku-elective-login.html", null, "pku")
            c += arrayOf("html.bnuz.login", null, "$DP/bnuz-es-login.html", null, "bnuz")

            // 课表页 HTML 也必须判对
            c += arrayOf("html.zfnew.kblist", null, "$DP/zf-new-xskbcx-kblist.html", null, "zf_new")
            c += arrayOf("html.zfnew.kbgrid", null, "$DP/zf-new-kbgrid-view.html", null, "zf_new")
            c += arrayOf("html.zf.blacktab", null, "$DP/zf-old-xskbcx-blacktab.html", null, "zf")
            c += arrayOf("html.zf.courses", null, "$DP/zf-old-xskbcx-courses.html", null, "zf")
            c += arrayOf("html.qz.xskb", null, "$DP/qz-base-xskb-courses.html", null, "qz")
            c += arrayOf("html.urpnew.courses", null, "$DP/urp-new-schedule-courses.html", null, "urp_new")
            c += arrayOf("html.bnuz.table1", null, "$DP/bnuz-es-table1.html", null, "bnuz")
            c += arrayOf("html.cf.kbxx", null, "$DP/cf-chengfang-kbxx-empty.html", null, "cf")
            c += arrayOf("html.pku.datagrid", null, "$DP/pku-elective-datagrid.html", null, "pku")

            // ============ 3. fp-url-confusion 7 负样本 ============
            // 3a) CAS 网关 — URL 与 HTML 都必须 null
            c += arrayOf("fp.cas-gateway",
                "https://cas.example.edu.cn/authserver/login?service=https://jwgl.example.edu.cn/jwglxt/xtgl/login_slogin.html",
                "$FP/cas-authserver-gateway.html", null, null)
            // 3b) qzu 子域 + jwglxt — jwglxt 优先,绝不回退强智
            c += arrayOf("fp.qzu-subdomain-jwglxt-wins",
                "https://qzu.example.edu.cn/jwglxt/xtgl/login_slogin.html",
                "$FP/qzu-subdomain-portal.html", "zf_new", "zf_new")
            // 3c) urp host + 老 displayTag — 删裸 urp → URL null;HTML displaytag → urp
            c += arrayOf("fp.urp-old-displaytag",
                "https://urp.cqupt.edu.cn/courseTable/show",
                "$FP/urp-old-displaytag.html", null, "urp")
            // 3d) default2 与 jwglxt 同链 — jwglxt 优先级高
            c += arrayOf("fp.default2-and-jwglxt-precedence",
                "https://jwgl.example.edu.cn/jwglxt/kbcx/xskbcx_cxXsKb.html?from=default2.aspx&gnmkdm=N2151",
                "$FP/default2-and-jwglxt.html", "zf_new", "zf_new")
            // 3e) xskbcx.aspx + HTML 是老正方结构 — 双层都 zf
            c += arrayOf("fp.xskbcx-aspx-old",
                "https://jw.usts.edu.cn/xskbcx.aspx",
                "$FP/xskbcx-aspx-old.html", "zf", "zf")
            // 3f) 新 API 直连 URL + HTML 是新正方 — 双层都 zf_new
            c += arrayOf("fp.xskbcx-cxxskb-new",
                "https://jwgl.example.edu.cn/jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151",
                "$FP/xskbcx-cxxskb-new.html", "zf_new", "zf_new")
            // 3g) /xtgl 无尾斜杠 + HTML 新正方登录页 — 双层都 zf_new
            c += arrayOf("fp.xtgl-no-slash",
                "https://jwgl.example.edu.cn/xtgl",
                "$FP/xtgl-no-slash.html", "zf_new", "zf_new")

            // ============ 4. webvpn-urls 20 形态 ============
            c += arrayOf("webvpn.zfold-webhost", "https://webvpn.example.edu.cn/webvpn/jw.example.edu.cn/xskbcx.aspx",
                null, "zf", null)
            c += arrayOf("webvpn.zfnew-webhost",
                "https://webvpn.example.edu.cn/webvpn/jwgl.example.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151",
                null, "zf_new", null)
            c += arrayOf("webvpn.zfnew-httphex",
                "https://webvpn.example.edu.cn/http/7f3a9c/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151",
                null, "zf_new", null)
            c += arrayOf("webvpn.zfnew-hexhost",
                "https://http-jwgl-example-edu-cn-80.webvpn.example.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151",
                null, "zf_new", null)
            c += arrayOf("webvpn.qz-webhost", "https://webvpn.example.edu.cn/webvpn/jwxt.example.edu.cn/jsxsd/xskb/xskb_list.do",
                null, "qz", null)
            c += arrayOf("webvpn.qz-httphex", "https://webvpn.example.edu.cn/http/c41d07/jwxt.example.edu.cn/jsxsd/xskb/xskb_list.do",
                null, "qz", null)
            c += arrayOf("webvpn.qz-hexhost", "https://http-jwxt-example-edu-cn-80.webvpn.example.edu.cn/jsxsd/xskb/xskb_list.do",
                null, "qz", null)
            c += arrayOf("webvpn.wisedu-webhost",
                "https://webvpn.example.edu.cn/webvpn/jwgl.example.edu.cn/jwapp/sys/wdkb/modules/xskcb/xskcb.do",
                null, "wisedu", null)
            c += arrayOf("webvpn.wisedu-httphex",
                "https://webvpn.example.edu.cn/http/9c1b2d/jwapp/sys/wdkb/modules/xskcb/xskcb.do",
                null, "wisedu", null)
            c += arrayOf("webvpn.urpnew-webhost",
                "https://webvpn.example.edu.cn/webvpn/urpjw.example.edu.cn/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback",
                null, "urp_new", null)
            c += arrayOf("webvpn.urpnew-httphex",
                "https://webvpn.example.edu.cn/http/5e9a83/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback",
                null, "urp_new", null)
            c += arrayOf("webvpn.urpold-webhost", "https://webvpn.example.edu.cn/webvpn/bkjw.example.edu.cn/xkAction.do?actionType=6",
                null, "urp", null)
            c += arrayOf("webvpn.urpold-httphex", "https://webvpn.example.edu.cn/http/8d2f6a/xkAction.do?actionType=6",
                null, "urp", null)
            // cf/pku/bnuz webvpn 形态 — URL 层判型必须返回 null(case.json 明确):
            //   路径重写形态下青果/北大/北师珠无可靠 URL 指纹,判定必须由 HTML 层接管
            c += arrayOf("webvpn.cf-webhost-null", "https://webvpn.example.edu.cn/webvpn/jxfw.example.edu.cn/xsgrkbcx!xsAllKbList.action",
                null, null, null)
            c += arrayOf("webvpn.cf-httphex-null", "https://webvpn.example.edu.cn/http/e55b19/xsgrkbcx!xsAllKbList.action",
                null, null, null)
            c += arrayOf("webvpn.pku-webhost-null", "https://webvpn.example.edu.cn/webvpn/elective.pku.edu.cn/elective2008/",
                null, null, null)
            c += arrayOf("webvpn.pku-httphex-null", "https://webvpn.example.edu.cn/http/3a7c50/elective2008/",
                null, null, null)
            c += arrayOf("webvpn.bnuz-webhost-null", "https://webvpn.example.edu.cn/webvpn/es.bnuz.edu.cn/default.aspx",
                null, null, null)
            c += arrayOf("webvpn.bnuz-httphex-null", "https://webvpn.example.edu.cn/http/6f0d84/default.aspx",
                null, null, null)

            // ============ 5. CAS 网关 HTML 单独负样本 ============
            c += arrayOf("html.cas-gateway-null", null, "$DP/cas-gateway.html", null, null)

            return c
        }
    }

    @Test
    fun `detectProtocolFromUrl 判型符合期望`() {
        if (url == null) return
        val actual = JwImportViewModel.detectProtocolFromUrlForTest(url)
        assertEquals("[$caseName] URL 层判型", expectedFromUrl, actual)
    }

    @Test
    fun `detectProtocolFromHtml 判型符合期望`() {
        if (htmlFile == null) return
        val html = loadHtml(htmlFile)
        val actual = JwImportViewModel.detectProtocolFromHtmlForTest(html)
        assertEquals("[$caseName] HTML 层判型", expectedFromHtml, actual)
    }

    @Test
    fun `detectProtocol 组合判型与期望一致`() {
        if (url == null && htmlFile == null) return
        val html = htmlFile?.let { loadHtml(it) } ?: ""
        val effective = JwImportViewModel.detectProtocolForTest(html, url)
        // 组合判型: URL 层命中则返回 URL 结果;否则 HTML 层;否则 null
        val expected = expectedFromUrl ?: expectedFromHtml
        assertEquals("[$caseName] 组合判型", expected, effective)
    }
}
