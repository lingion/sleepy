package com.lingion.sleepy

import org.junit.Assert.assertFalse
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
        // 北京航空航天大学 BUAA
        Attribution("APassbyDreg/BUAA_JW_Utils", ""),
        Attribution("SE2020-TopUnderstanding/BUAA-Campus-Tools-Backend", ""),
        Attribution("fondoger/buaa-teacher-evaluation", "MIT"),
        Attribution("Cauchy1412/BUAAGetCourse", ""),
        Attribution("KKRainbow/JWOneShotEval", ""),
        // 中国科学院大学 UCAS (issue #18, SOP cross-verified 2026-09-07, 4 仓全量纳入)
        Attribution("ldiex/UCAS_Course_Schedule_Convertor", ""),
        Attribution("Hurray0/UCAS_GET_Course", ""),
        Attribution("cld378632668/ucas_course_tool", ""),
        Attribution("GentleCP/UCAS-Helper", ""),
        // 2026-09-09 SEP SSO 登录链跨仓验证 (issue #18 回复: XRW 修复) 触达 2 仓
        Attribution("wirsbf/TraintimePda-UCAS", ""),
        Attribution("tbjuechen/sep-api", ""),
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
        // 北京交通大学 BJTU (issue #19, SOP cross-verified 2026-09-09, 24 候选全量纳入)
        Attribution("bjtu_mis_Android", "wan300"),
        Attribution("BJTU-MIS-HarmonyOS", "Anyes666"),
        Attribution("BJTUselfService", "HFDLYS"),
        Attribution("bjtu-cli", "fish2lab"),
        Attribution("BJTUselfService-macOS", "fish2lab"),
        Attribution("BJTU-course-assistant", "s1y4x1"),
        Attribution("ZiuChen/userscript", "MIT"),
        Attribution("BJTU-iCalendar-Generator", "ymzhang-cs"),
        Attribution("bjtu-timetable", "Moliseeee"),
        Attribution("BJTU-course-autoget-program", "hyskr"),
        Attribution("BjtuCoursePlatform", "57Darling02"),
        Attribution("bjtuDean", "jlytwhx"),
        Attribution("bjtubox_python", "jlytwhx"),
        Attribution("Campus-Mate", "Orien233"),
        Attribution("BJTU-STU-MCP", "ymzhang-cs"),
        Attribution("CourseRobber", "xschur"),
        Attribution("Futuremind-BJTU", ""),
        Attribution("BJTU_ezRate", "Yukikasu"),
        Attribution("bjtu_teaching_assessment", "xxxand"),
        Attribution("BJTU-script", "Coconut00"),
        Attribution("BJTU-CC", "aooxin"),
        Attribution("CourseTable", "etherealviator"),
        Attribution("ZF-Assistant", "mcdona1d"),
        // 江苏海洋大学 JOU (2026-09-09 SOP cross-verified, 10 候选全量纳入; 酱海带为闭源参考仅协议分析)
        Attribution("jianghaidai", "sunjingquan"),
        Attribution("JOU-Campus-Guide", "sunjingquan"),
        Attribution("jou_course_bot", "brodamndamn"),
        Attribution("Wehhit-server", "dengjj"),
        Attribution("Wehhit", "zqy1"),
        Attribution("Grain", "LeeReindeer"),
        Attribution("finance", "GeorgeLeoo"),
        Attribution("finance-server", "GeorgeLeoo"),
        Attribution("JStore", "GeorgeLeoo"),
        Attribution("RSSHub jou", "MIT"),
        Attribution("北交大iCalender课表生成", "Greasy Fork"),
        // 燕山大学 YSU / boya_pp 多校 SaaS 调研 (全量触达仓库, 含非协议反例)
        Attribution("LzBsA", "github.com/LzBsA"),
        Attribution("qnxg/hnu_query", "AGPL-3.0"),
        Attribution("qnxg/weihuda_backend", "qnxg"),
        Attribution("heriec/suda-yjs-shedule", "heriec"),
        Attribution("dlutor/chaoxingbook", "MIT"),
    )

    // ----- 贡献者 (Contributors) token 集: 直接提交代码并合入的开发者, 与上游参考仓库致谢区分 -----
    // v1.0.53 用户令: 收录 PR #29 作者 jim139129 (NEU 教务导入修复, 已随 v1.0.52 发布)。
    // 2026-09-09 定稿: 区块**不枚举**具体 PR/issue 编号 (贡献者提交到 1000 条时静态文案必漏,
    // 也没有读者价值) — 只放姓名 + GitHub 主页链接, GitHub 即永远完整的清单; 条目实体在
    // LicenseScreen.kt 的 contributorEntries。本测试锁两点: 6 语 header 齐全 + 源码含主页链接。

    private val CONTRIBUTOR_ATTRIBUTIONS = listOf(
        Attribution("jim139129", "github.com/jim139129"),
    )

    /** 贡献者区块标题, 6 语各有一条 string。 */
    private fun readContributorHeader(locale: String): String =
        readString(locale, "license_contributor_section")

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

    // ----- 贡献者区块闸门 (v1.0.53 用户令: PR #29 作者 jim139129 入贡献者) -----

    /** 贡献者条目实体在 LicenseScreen.kt, 源码 token 断言; 6 语 header 齐全闸门。 */
    @Test
    fun `contributor section header present in all released locales`() {
        for (locale in ALL_RELEASED_LOCALES) {
            val header = readContributorHeader(locale)
            assertTrue("locale=$locale 缺少 license_contributor_section header", header.isNotBlank())
        }
    }

    @Test
    fun `license screen lists code contributors with pr tokens`() {
        val src = File(basePath.parentFile, "java/com/lingion/sleepy/ui/screen/mine/LicenseScreen.kt")
            .readText()
        assertTrue("LicenseScreen.kt 未引用贡献者区块标题 license_contributor_section",
            src.contains("R.string.license_contributor_section"))
        for (c in CONTRIBUTOR_ATTRIBUTIONS) {
            assertTrue("LicenseScreen.kt 致谢贡献者漏写 ${c.project}",
                src.contains(c.project))
            if (c.licenseOrAuthor.isNotEmpty()) {
                assertTrue("LicenseScreen.kt 漏写 ${c.project} 的 PR 标记 ${c.licenseOrAuthor}",
                    src.contains(c.licenseOrAuthor))
            }
        }
    }

    /**
     * 贡献者条目形态闸门 (2026-09-09 定稿): 区块**禁止枚举**具体 PR/issue 编号 —
     * 贡献者提交到 1000 条时静态文案必漏必烂 (枚举 = GitHub 页面的劣化复制品),
     * 只放姓名 + GitHub 主页链接, GitHub 即永远完整的清单。
     * 本测试反向锁: 主页链接必须在, 枚举 token 禁止回归。
     */
    @Test
    fun `contributor entry links github profile instead of enumerating`() {
        val src = File(basePath.parentFile, "java/com/lingion/sleepy/ui/screen/mine/LicenseScreen.kt")
            .readText()
        val region = src.substringAfter("private val contributorEntries")
        assertTrue(
            "LicenseScreen.kt 贡献者条目必须含 GitHub 主页链接 github.com/jim139129",
            region.contains("github.com/jim139129")
        )
        for (t in listOf("PR ×", "issue ×", "#13", "#16", "#29", "#8 ", "#9 ")) {
            assertFalse(
                "贡献者条目禁止枚举编号 (链接外指才是可缩放形态), 发现回归 token: $t",
                region.contains(t)
            )
        }
    }

    /** 自检: 统计 token 总数与跨校/单校/贡献者分类 (commit 前打印日志, 漂移检测助手) */
    @Test
    fun `attribution coverage summary`() {
        val total = FOUNDATIONAL_ATTRIBUTIONS.size + PER_SCHOOL_ATTRIBUTIONS.size + CONTRIBUTOR_ATTRIBUTIONS.size
        println("[ATTRIBUTION] foundational=${FOUNDATIONAL_ATTRIBUTIONS.size}, per-school=${PER_SCHOOL_ATTRIBUTIONS.size}, contributors=${CONTRIBUTOR_ATTRIBUTIONS.size}, total=$total")
        assertTrue("必须覆盖至少 50 条致谢 token", total >= 50)
    }
}
