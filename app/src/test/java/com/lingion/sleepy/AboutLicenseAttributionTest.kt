package com.lingion.sleepy

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 关于页致谢 (about_license_body) 漂移回归测试。
 *
 * 规则: 新增 parser / 学校落地后必须在 zh-rCN / zh-rTW / en / ja / es 5 语同步追加致谢条目
 * (避免 HFUT 那次 en 漏写的历史漂移)。致谢条目用上游项目名 + author/license
 * 三元组作为最小识别单位, 实际文案与语序由 strings.xml 维护者负责。
 *
 * v1.0.50 用户令: 致谢按"跨校普适项目 / 单校项目"两类组织, 单校卡展开看明细。
 * 测试闸门覆盖每条 token 必须出现在每语 about_license_body 中 (与 LicenseScreen.kt
 * 的 attributionEntries + perSchoolEntries 同源)。"宁可错谢不可放过" = 一旦调研
 * 触达仓库, 必入 strings.xml 6 语 + LicenseScreen.kt + 本测试, 三处一致。
 */
class AboutLicenseAttributionTest {

    private val basePath: File = sequenceOf(
        File("app/src/main/res"),
        File("src/main/res")
    ).first { it.isDirectory }

    private fun readString(locale: String, key: String): String {
        val f = File(basePath, "$locale/strings.xml")
        val text = f.readText()
        val regex = Regex("""<string\s+name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        return regex.find(text)?.groupValues?.get(1)?.trim().orEmpty()
    }

    /** 单条致谢条目的最小识别 token。任何一个 token 缺失则视为漏致谢。 */
    private data class Attribution(val project: String, val licenseOrAuthor: String)

    // ----- 跨校普适项目 (Foundational) token 集 -----

    private val FOUNDATIONAL_ATTRIBUTIONS = listOf(
        Attribution("WakeUp", "Apache-2.0"),
        Attribution("WakeupSchedule_BUPT", "Apache-2.0"),
        Attribution("WakeupSchedule_Kotlin", "Apache-2.0"),
        Attribution("cqu.js", ""),
        Attribution("shiguang_warehouse", "MIT"),
        Attribution("zfn_api", "MPL-2.0"),
        Attribution("FlowCourse", "GPL-3.0"),
        Attribution("iwut", "AGPL-3.0"),
        Attribution("shangkeschedule", "Apache-2.0"),
    )

    // ----- 单校项目 (PerSchool) token 集: 每校 + 该校仓库 token -----

    private val PER_SCHOOL_ATTRIBUTIONS = listOf(
        // 合肥工业大学 HFUT
        Attribution("HFUT-Schedule", "MIT"),
        Attribution("HfutOpenApi", "BoynChan"),
        Attribution("hfut_schedule_hacker", "Aoi-cn"),
        Attribution("django-hfut-auth", "elonzh"),
        // 东南大学 SEU
        Attribution("SEUTimetable", "Apache-2.0"),
        Attribution("Aetik-yue/hormone", ""),
        Attribution("luzy99/SEUAutoLogin", ""),
        // 浙江大学 ZJU
        Attribution("zju-ical-py", "LGPL-2.1"),
        // 中国科学技术大学 USTC
        Attribution("USTC-timetable-to-ics", ""),
        // 四川大学 SCU
        Attribution("ScuTimetable", ""),
        // 东北大学 NEU
        Attribution("neu_wisedu2wakeup", "CreamPig233"),
        Attribution("PopulusYang/NeuTimetable", ""),
        Attribution("neucn/elise", ""),
        Attribution("RekaYOO/NEU-JWXT-Toolkit", ""),
        Attribution("PeterPtroc/neu-jwxt-to-wakeup", ""),
        Attribution("leavesvv-source/NEU-Timetable", ""),
        // 重庆大学 CQU
        Attribution("321CQU/pymycqu", ""),
        Attribution("BillYang2016/CQU-class2ics", ""),
        Attribution("haowang02/CourseMonitor", ""),
        Attribution("LengerHu/CQU_classtabletoics", ""),
        Attribution("Hagb/cqu_timetable_new", ""),
        Attribution("VayneDuan/CQU-Grade-Monitor", ""),
        Attribution("weearc/cm-http-api", ""),
        Attribution("barryZZJ/course_to_calander_converter", ""),
        // 武汉理工大学 WHUT
        Attribution("courseTable", "acm910"),
        // 电子科技大学 UESTC
        Attribution("MilLoong/UESTC-EAMS-Helper-App", ""),
        Attribution("MilLoong/UESTC-EAMS-Helper-Python", ""),
        Attribution("KaranocaVe/UESTCJWCWatchdog", ""),
        Attribution("whtsky/uestc-eams-cleartimeout-userscript", ""),
        Attribution("Sunmxt/UESTC-EAMS", ""),
        // 广东工业大学 GDUT
        Attribution("N0tExpectErr0r/GDUT-ClassTimeTable", ""),
        Attribution("Richard-Zheng/GDUT-Schedule-ng", ""),
        Attribution("StarArchive/gdut-course-frontend", ""),
        Attribution("StarArchive/gdut-course-backend", ""),
        Attribution("HoneQ7/GDUT_iOS_Timetable", ""),
        // 广东财经大学 GDUFE
        Attribution("jkgeekJack/Android-GDUFE-JWC-SDK", ""),
        Attribution("Kiteio/GDUFE-wrapper", ""),
        // 广东金融学院 GDUf
        Attribution("Kiteio/Punica", ""),
        Attribution("gduf-finmind", ""),
        // 广东外语外贸大学 GDUFS
        Attribution("yongjianzheng/Gdufszhushou", ""),
        Attribution("Crazioker/agency", ""),
        // 广东医科大学 GDMU (用户采集包确认 zf_new 协议, 无外部学生仓库)
        Attribution("GDMU", ""),
        // 长沙理工大学 CSUST
        Attribution("zHElEARN/CSUSTKit", ""),
        Attribution("CreaMakers/EduSpider", ""),
        Attribution("timeisthe/CSUSTDataGet", ""),
        Attribution("Julius-lq/EduAdminSystem", ""),
        Attribution("JS-CAUTION/csust-course-schedule", ""),
        // 北京邮电大学 BUPT
        Attribution("helium777/bupt-course-grab", ""),
        Attribution("JmPotato/BUPT-Grader", ""),
        Attribution("Seizzzz/Auto-Login-BUPT", ""),
        // 北京大学 PKU
        Attribution("zhongxinghong/PKUAutoElective", ""),
        Attribution("thezzisu/pku-elective", ""),
        Attribution("Hovennnnn/PKUAutoElective2023", ""),
        Attribution("Lihhan/AutoElective_4_PKU", ""),
        Attribution("AuYang261/PKU_Elective_Toolset", ""),
        // 北京化工大学 BUCT
        Attribution("MarkYangKp/ZhengFangJY", ""),
        // 北京林业大学 BJFU
        Attribution("Bloomberg2000/bjfu_course_ics_generator", ""),
        Attribution("Bloomberg2000/bjfu_util.py", ""),
        // 安徽大学 AHU
        Attribution("Tonyseth/AHU_JW_GPA_Calculator", ""),
        // 东北林业大学 NEFU
        Attribution("bboy-xp/nefu-crawler", ""),
        Attribution("heyMahalo/crouse_select", ""),
        // 东华大学 DHU
        Attribution("tk.dcmmcc", ""),
        Attribution("Bad-086/DHU_CourseMonitor", ""),
        // 云南财经大学 YNUFE
        Attribution("NINIYOYYO/ynufe-campus-app", ""),
        Attribution("MiaoWuNYA/ynufeRealLogin", ""),
        // 北京理工大学 BIT
        Attribution("BIT-Login", "BIT101-dev"),
        // 北京信息科技大学 BISTU
        Attribution("iBistu", "ProjektMing"),
        // 安徽建筑大学 AHU-JZ
        Attribution("JdaAssist", "CH4019"),
        // 重庆邮电大学移通学院 CQYTU
        Attribution("CQYTZFCheckScores", "xM3GAN"),
        // 华南农业大学 SCAU
        Attribution("ScheduleXParser_SCAU", "greyovo"),
        // 齐鲁工业大学 QLU
        Attribution("JW-spider", "Zhy423310825"),
        // 渤海大学 BHU
        Attribution("BohaiServiceDome", "joun233"),
        // 东北石油大学 NEPU
        Attribution("WeNEPU", "cutiechi"),
        // 南京理工大学 NUST
        Attribution("HeraldStudentCurriculum", "idailylife"),
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
    fun `all released locales list foundational attributions`() {
        for (locale in ALL_RELEASED_LOCALES) {
            checkAll(locale, FOUNDATIONAL_ATTRIBUTIONS)
        }
    }

    @Test
    fun `all released locales list per-school attributions`() {
        for (locale in ALL_RELEASED_LOCALES) {
            checkAll(locale, PER_SCHOOL_ATTRIBUTIONS)
        }
    }

    /** 自检: 统计 token 总数与跨校/单校分类 (commit 前打印日志, 漂移检测助手) */
    @Test
    fun `attribution coverage summary`() {
        val total = FOUNDATIONAL_ATTRIBUTIONS.size + PER_SCHOOL_ATTRIBUTIONS.size
        println("[ATTRIBUTION] foundational=${FOUNDATIONAL_ATTRIBUTIONS.size}, per-school=${PER_SCHOOL_ATTRIBUTIONS.size}, total=$total")
        assertTrue("必须覆盖至少 50 条致谢 token", total >= 50)
    }
}
