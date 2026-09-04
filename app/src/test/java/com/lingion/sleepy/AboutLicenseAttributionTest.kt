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
        Attribution("neu_wisedu2wakeup", ""),       // CreamPig233/neu_wisedu2wakeup (无 license)
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

    /** 全部发布语言: 致谢漂移曾只查 3 语, en/es/ja 漏整批 B 档致谢而闸门放行 */
    private val ALL_RELEASED_LOCALES = listOf(
        "values", "values-zh-rCN", "values-zh-rTW", "values-en", "values-ja", "values-es"
    )

    @Test
    fun `all released locales list all batch A attributions`() {
        for (locale in ALL_RELEASED_LOCALES) {
            checkAll(locale, BATCH_A_ATTRIBUTIONS)
        }
    }

    @Test
    fun `all released locales list all batch B first wave attributions`() {
        for (locale in ALL_RELEASED_LOCALES) {
            checkAll(locale, BATCH_B_FIRST_WAVE_ATTRIBUTIONS)
        }
    }
}