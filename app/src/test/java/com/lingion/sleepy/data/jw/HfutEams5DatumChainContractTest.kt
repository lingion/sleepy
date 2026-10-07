package com.lingion.sleepy.data.jw

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-09-11 合肥工业大学 datum HTTP 500 修复的 WebView contract test:
 * 锁定 EAMS5_FETCH_JS v2 四段链 (course-table → info/<sid> → get-data → POST datum)。
 * 根因: 参考仓 Chiu-xaH/HFUT-Schedule JxglstuService.kt 注释明言 datum 必须携带
 * 前序步骤取到的 lessonIds, 空数组直接 500 — v1 简化体从未真正可用。
 * Pattern: HfutPortalEams5WebViewContractTest - read source text, lock JS literal.
 */
class HfutEams5DatumChainContractTest {

    private fun loadSourceFile(path: String): String {
        for (p in listOf("src/main/java/$path", "app/src/main/java/$path")) {
            val f = java.io.File(p)
            if (f.isFile) return f.readText(Charsets.UTF_8)
        }
        error("source file $path should exist")
    }

    private fun extractEams5FetchJs(src: String): String {
        val marker = "private const val EAMS5_FETCH_JS = \"\"\""
        val start = src.indexOf(marker)
        assertTrue("EAMS5_FETCH_JS must exist", start >= 0)
        val bodyStart = start + marker.length
        val bodyEnd = src.indexOf("\"\"\"", bodyStart)
        assertTrue("EAMS5_FETCH_JS must close", bodyEnd > bodyStart)
        return src.substring(bodyStart, bodyEnd)
    }

    private fun webViewSource(): String =
        loadSourceFile("com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt")

    private fun js(): String = extractEams5FetchJs(webViewSource())

    @Test
    fun `v2 chain fetches info page for bizTypeId`() {
        val js = js()
        assertTrue("must GET for-std/course-table/info/<sid>", js.contains("/for-std/course-table/info/"))
        assertTrue("must parse bizTypeId from info html", js.contains("bizTypeId\\s*[=:]"))
    }

    @Test
    fun `v2 chain fetches get-data with bizTypeId and dataId`() {
        val js = js()
        assertTrue("must GET get-data endpoint", js.contains("/for-std/course-table/get-data?bizTypeId=' + biz"))
        assertTrue("get-data must pass dataId=sid", js.contains("&dataId=' + sid"))
        assertTrue("get-data must pass semesterId when known", js.contains("&semesterId=' + sem"))
    }

    @Test
    fun `v2 chain reads lessonIds from get-data response`() {
        val js = js()
        assertTrue("must read top-level lessonIds", js.contains("j.lessonIds"))
        assertTrue("must accept result-wrapped lessonIds", js.contains("j.result && j.result.lessonIds"))
    }

    @Test
    fun `studentId falls back to redirect final url info path`() {
        // 参考仓 dev 分支 (ZIP 实测): 302 Location = /for-std/course-table/info/<studentId>
        // 我们的 fetch 等价物 = r.url → ctx.finalUrl, HTML 正则失败时必须走这条兜底
        val js = js()
        assertTrue(
            "must fall back to finalUrl info path",
            js.contains("/for-std\\/course-table\\/info\\/(\\d+)/.exec(ctx.finalUrl)"),
        )
    }

    @Test
    fun `v2 datum body carries lessonIds and numeric studentId`() {
        val js = js()
        assertTrue("datum body must send parsed ids", js.contains("lessonIds: ids"))
        assertTrue("datum body must keep weekIndex", js.contains("weekIndex: ''"))
        assertTrue(
            "pure-digit studentId must be sent as Number (reference repo sends int)",
            js.contains("/^\\d+\$/.test(sid) ? Number(sid) : sid")
        )
    }

    @Test
    fun `v2 chain keeps v1 empty-lessonIds fallback for other schools`() {
        val js = js()
        // bizTypeId 解析不到 → 跳过 get-data, ids 留空数组 = v1 行为 (保护 cumtb 等共用校)
        assertTrue("must short-circuit get-data via Promise.resolve fallback", js.contains("Promise.resolve('')"))
        assertTrue("get-data must only run when biz parsed", js.contains("var chain = biz"))
    }

    @Test
    fun `v3 chain extracts selected semester option before get-data`() {
        val js = js()
        assertTrue("must inspect selected semester option", js.contains("var extractedSemester = extractSemesterId(html)"))
        assertTrue("must read selected option value", js.contains("selectedValue = options[oi].match"))
        assertTrue("must send extracted semester to get-data", js.contains("var sem = extractedSemester"))
    }

    @Test
    fun `v2 datum error message keeps original prefix`() {
        // 反馈截图错误串 'POST schedule-table/datum 失败 HTTP 500' 的前缀必须保留, 便于用户比对
        assertTrue(js().contains("POST schedule-table/datum 失败 HTTP ' + r.status"))
    }
}
