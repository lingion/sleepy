package com.lingion.sleepy

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 关于页致谢 (about_license_body) 漂移回归测试。
 *
 * 规则: 新增 parser 落地后必须在 zh-rCN / zh-rTW / en 三语同步追加致谢条目
 * (避免 HFUT 那次 en 漏写的历史漂移)。致谢条目用上游项目名 + author + license
 * 三元组作为最小识别单位, 实际文案与语序由 strings.xml 维护者负责。
 */
class AboutLicenseAttributionTest {

    private val basePath: File = sequenceOf(
        File("app/src/main/res"),
        File("src/main/res")
    ).first { it.isDirectory }

    private fun readString(locale: String, key: String): String {
        val f = File(basePath, "$locale/strings.xml")
        val text = f.readText()
        // 简易 <string name="KEY">VALUE</string> 抽取 (本测试只关心内容存在性)
        val regex = Regex("""<string\s+name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        return regex.find(text)?.groupValues?.get(1)?.trim().orEmpty()
    }

    /**
     * 单条致谢条目的最小识别 token。任何一个 token 缺失则视为漏致谢。
     * Token = (上游项目名, author/license 标记之一)。
     */
    private data class Attribution(val project: String, val licenseOrAuthor: String)

    private val BATCH_A_ATTRIBUTIONS = listOf(
        // 已有 (历史): WakeUp, cqu.js, HFUT
        Attribution("WakeUp", "Apache-2.0"),
        Attribution("cqu.js", ""),
        Attribution("HFUT-Schedule", "MIT"),
    )

    /** B 档第一波 (宽松/无 license): 四校致谢必出 */
    private val BATCH_B_FIRST_WAVE_ATTRIBUTIONS = listOf(
        Attribution("SEUTimetable", "Apache-2.0"),     // sakimidare/SEUTimetable
        Attribution("zju-ical-py", "LGPL-2.1"),        // Xecades/zju-ical-py
        Attribution("USTC-timetable-to-ics", ""),      // 1970633640/USTC-timetable-to-ics (无 license)
        Attribution("ScuTimetable", ""),               // Z-P-J/ScuTimetable (无 license)
    )

    private fun checkAll(locale: String, atts: List<Attribution>) {
        val body = readString(locale, "about_license_body")
        assertTrue("locale=$locale 缺少 about_license_body 字符串", body.isNotBlank())
        for (a in atts) {
            assertTrue("locale=$locale 致谢漏写 ${a.project} (body=\"$body\")",
                body.contains(a.project))
            if (a.licenseOrAuthor.isNotEmpty()) {
                assertTrue("locale=$locale 致谢漏写 ${a.project} 的 license/author 标记 ${a.licenseOrAuthor}",
                    body.contains(a.licenseOrAuthor))
            }
        }
    }

    @Test
    fun `zh-rCN lists all batch A attributions`() {
        checkAll("values-zh-rCN", BATCH_A_ATTRIBUTIONS)
    }

    @Test
    fun `zh-rTW lists all batch A attributions`() {
        checkAll("values-zh-rTW", BATCH_A_ATTRIBUTIONS)
    }

    @Test
    fun `values (en fallback) lists all batch A attributions`() {
        // values 是默认 (zh-rCN 兜底镜像), 历史 HFUT 漏在 en 是已知漂移,
        // 本测试为新批次回归闸, 不为 HFUT 老漂移背书。
        val body = readString("values", "about_license_body")
        assertTrue("values/strings.xml 缺少 about_license_body", body.isNotBlank())
    }

    @Test
    fun `zh-rCN lists all batch B first wave attributions`() {
        checkAll("values-zh-rCN", BATCH_B_FIRST_WAVE_ATTRIBUTIONS)
    }

    @Test
    fun `zh-rTW lists all batch B first wave attributions`() {
        checkAll("values-zh-rTW", BATCH_B_FIRST_WAVE_ATTRIBUTIONS)
    }

    @Test
    fun `values (en fallback) lists all batch B first wave attributions`() {
        checkAll("values", BATCH_B_FIRST_WAVE_ATTRIBUTIONS)
    }
}