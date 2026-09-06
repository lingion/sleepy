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
        // 2026-09-06 HFUT 跨仓验证补全: 三个被参考过的仓库无论正反面一律致谢
        Attribution("HfutOpenApi", "BoynChan"),
        Attribution("hfut_schedule_hacker", "Aoi-cn"),
        Attribution("django-hfut-auth", "elonzh"),
    )

    /** B 档第一波 (宽松/无 license): 四校致谢必出 */
    private val BATCH_B_FIRST_WAVE_ATTRIBUTIONS = listOf(
        Attribution("SEUTimetable", "Apache-2.0"),     // sakimidare/SEUTimetable
        Attribution("zju-ical-py", "LGPL-2.1"),        // Xecades/zju-ical-py
        Attribution("USTC-timetable-to-ics", ""),      // 1970633640/USTC-timetable-to-ics (无 license)
        Attribution("ScuTimetable", ""),               // Z-P-J/ScuTimetable (无 license)
        Attribution("neu_wisedu2wakeup", ""),       // CreamPig233/neu_wisedu2wakeup (无 license)
    )

    /** 2026-09-05 全量补齐: 源码头注释里引用过的其余上游, body 与逐条卡都要有 */
    private val BATCH_C_FULL_SWEEP_ATTRIBUTIONS = listOf(
        Attribution("WakeupSchedule_BUPT", "Apache-2.0"),   // dIT8Zv — 12 parser 上游
        Attribution("shiguang_warehouse", "MIT"),           // XingHeYuZhuan — whut/classicEams/zf_new
        Attribution("iwut", "AGPL-3.0"),                    // TokenTeam — WHUT 节次映射 (仅形态)
        Attribution("zfn_api", "MPL-2.0"),                  // openschoolcn — 新正方 kbList
        Attribution("FlowCourse", "GPL-3.0"),               // jiaweiyaya — kbList 交叉验证
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

    /** 2026-09-05 学生项目批次: 逐校验证的第一手证据源 + 名单对照数据源 ("宁可错谢不放过") */
    private val BATCH_D_STUDENT_PROJECTS = listOf(
        Attribution("BIT-Login", "BIT101-dev"),
        Attribution("iBistu", "ProjektMing"),
        Attribution("JdaAssist", "CH4019"),
        Attribution("CQYTZFCheckScores", "xM3GAN"),
        Attribution("ScheduleXParser_SCAU", "greyovo"),
        Attribution("JW-spider", "Zhy423310825"),
        Attribution("BohaiServiceDome", ""),
        Attribution("courseTable", "acm910"),
        Attribution("shangkeschedule", "Apache-2.0"),
        Attribution("WeNEPU", "cutiechi"),
        Attribution("HeraldStudentCurriculum", "idailylife"),
        Attribution("tk.dcmmcc", ""),
    )

    @Test
    fun `all released locales list batch D student project attributions`() {
        for (locale in ALL_RELEASED_LOCALES) {
            checkAll(locale, BATCH_D_STUDENT_PROJECTS)
        }
    }

    @Test
    fun `all released locales list batch C full sweep attributions`() {
        for (locale in ALL_RELEASED_LOCALES) {
            checkAll(locale, BATCH_C_FULL_SWEEP_ATTRIBUTIONS)
        }
    }
}