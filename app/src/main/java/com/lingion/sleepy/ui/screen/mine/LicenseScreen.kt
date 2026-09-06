package com.lingion.sleepy.ui.screen.mine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme

/**
 * 开源许可与致谢子页 (v1.0.46 用户令: 从关于页长卡分离; v1.0.50 用户令:
 * 致谢按"学校 / 跨校项目"两类组织, 单校卡展开看明细)。
 *
 * 关于页原样塞 GPL 正文 + 全部教务适配致谢, B 档 + 179 校 audit 落地后
 * 致谢条目越滚越长, 关于页被拖成一屏读不完。拆为独立二级页:
 *   - 关于页留一行入口 (标题+副题)
 *   - 本页承载全部内容 — 许可证区块 + 致谢区块 + 顶层卡列表
 *   - 顶层卡分两类:
 *       跨校项目 (Foundational) = WakeUp / WakeupSchedule_BUPT / cqu.js 等
 *         通用跨校适配参考, 单卡不可展开
 *       单校项目 (PerSchool) = 1 学校 1 卡, 默认收起, 用户点击展开看该校所
 *         参考的全部学生维护 GitHub 项目
 *   - 展开/收起状态用 mutableStateMapOf 按卡片 id 维护, 进入页面不重置
 *   - 布局与 HolidaySettingsScreen 同款: Scaffold + TopAppBar 返回 + LazyColumn
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(onBack: () -> Unit) {
    val colors = SleepyTheme.colors
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.license_page_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        },
        containerColor = colors.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- 许可证区块 ----
            item {
                LicenseCard {
                    Text(
                        text = stringResource(R.string.license_gpl_section),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.license_gpl_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // ---- 致谢导语区块 (可折叠: 默认收起, 点击展开看 about_license_body 全文) ----
            item {
                val bodyExpanded = expanded["__body__"] == true
                LicenseCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded["__body__"] = !bodyExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.license_attribution_section),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.license_attribution_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { expanded["__body__"] = !bodyExpanded }) {
                            Icon(
                                imageVector = if (bodyExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = if (bodyExpanded) "collapse" else "expand",
                                tint = colors.onSurfaceVariant
                            )
                        }
                    }
                    AnimatedVisibility(visible = bodyExpanded) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = stringResource(R.string.about_license_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ---- 跨校普适项目 (Foundational) ----
            item {
                SectionHeader(stringResource(R.string.license_foundational_section))
            }
            items(
                items = attributionEntries,
                key = { it.id }
            ) { entry ->
                AttributionCard(
                    title = entry.title,
                    subtitle = entry.meta,
                    description = entry.usage,
                    expanded = false,
                    onToggle = {}
                )
            }

            // ---- 按学校致谢 (PerSchool, 可展开) ----
            item {
                SectionHeader(stringResource(R.string.license_perschool_section))
            }
            items(
                items = perSchoolEntries,
                key = { it.id }
            ) { entry ->
                AttributionCard(
                    title = entry.title,
                    subtitle = null,
                    description = null,
                    expanded = expanded[entry.id] == true,
                    onToggle = { expanded[entry.id] = !(expanded[entry.id] ?: false) },
                    expandedContent = {
                        Column {
                            entry.usage.split("\n").forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

/** 一条顶层致谢卡: 跨校项目=不可展开, 单校=可展开。 */
@Composable
private fun AttributionCard(
    title: String,
    subtitle: String?,
    description: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    expandedContent: (@Composable () -> Unit)? = null
) {
    val colors = SleepyTheme.colors
    LicenseCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = expandedContent != null) { onToggle() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary
                    )
                }
            }
            if (expandedContent != null) {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "collapse" else "expand",
                        tint = colors.onSurfaceVariant
                    )
                }
            }
        }
        if (!expanded && !description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }
        if (expanded && expandedContent != null) {
            Spacer(modifier = Modifier.height(6.dp))
            AnimatedVisibility(visible = expanded) {
                expandedContent()
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val colors = SleepyTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = colors.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun LicenseCard(content: @Composable () -> Unit) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(16.dp)
    ) {
        content()
    }
}

/**
 * 顶层致谢条目 (跨校项目卡)。description 为可见文字段, 与原 BATCH_A/B/C/BATCH_D
 * 致谢条目等价, 用于满足 AboutLicenseAttributionTest 的串级漂移测试。
 * 项目名 / 作者 / license 名字符串是通用标识不翻译; description 文本是说明,
 * 与 strings.xml 的 about_license_body 必须保持一致 (写卡 = 用户界面补强)。
 */
private data class AttributionEntry(
    val id: String,
    val title: String,
    val meta: String,
    val usage: String
)

/** 跨校普适项目 (单卡, 不可展开)。 */
private val attributionEntries: List<AttributionEntry> = listOf(
    AttributionEntry(
        "foundational-wakeup", "WakeUp 课程表 (YZune)", "Apache-2.0",
        "JwCourse / JwParser 中间结构语义与强智系 HTML 解析的参考实现"
    ),
    AttributionEntry(
        "foundational-wakeup-bupt", "WakeupSchedule_BUPT (dIT8Zv)", "Apache-2.0",
        "十二个教务解析器的上游: 强智全家族 (qz/qz_with_node/qz_br/qz_crazy/qz_old)、老版正方、URP、青果、新正方、HNUST 与 Parser 设计"
    ),
    AttributionEntry(
        "foundational-wakeup-kotlin", "WakeupSchedule_Kotlin (YZune)", "Apache-2.0",
        "经典金智 EAMS 导入实现 (TaskActivity 位图解析) 的参考"
    ),
    AttributionEntry(
        "foundational-cqu-js", "时光课程表 cqu.js", "",
        "重庆大学门户 REST 协议 (session / 课表 / 作息三接口) 的分析依据"
    ),
    AttributionEntry(
        "foundational-shiguang", "shiguang_warehouse (XingHeYuZhuan)", "MIT",
        "武汉理工大学 kcbcxby 协议、经典金智 EAMS (hunnu/uestc/hpu) 与新正方网格视图 (zhengfang_01) 的协议形态参考"
    ),
    AttributionEntry(
        "foundational-zfn", "zfn_api (openschoolcn)", "MPL-2.0",
        "新正方 jwglxt kbList 接口形态交叉验证"
    ),
    AttributionEntry(
        "foundational-flow", "FlowCourse (jiaweiyaya)", "GPL-3.0",
        "新正方 kbList 主流形态与 jc 多形态交叉验证"
    ),
    AttributionEntry(
        "foundational-iwut", "iwut (TokenTeam)", "AGPL-3.0 · 仅参考协议形态",
        "武汉理工大学节次 DM 映射的协议佐证 (未引用代码)"
    ),
    AttributionEntry(
        "foundational-shangkeschedule", "「上课」shangkeschedule (qiqqqqq517)", "Apache-2.0",
        "1776 校学校登记表在名单交叉复核中的对照数据源"
    )
)

/**
 * 按学校聚合: 每校一条卡, 展开后看到该校所参考的所有 GitHub 项目。
 * 项目名 / 作者 / license 是通用标识, 不做翻译 (与测试 token 一致)。
 * 用法: \n 分隔的字符串, 每行 = 一个仓库 + (作者, license)。
 */
private data class PerSchoolEntry(val id: String, val title: String, val usage: String)

private val perSchoolEntries: List<PerSchoolEntry> = listOf(
    PerSchoolEntry(
        "school-hfut", "合肥工业大学 HFUT",
        "HFUT-Schedule (Chiu-xaH, MIT)\nHfutOpenApi (BoynChan, MIT)\nhfut_schedule_hacker (Aoi-cn)\ndjango-hfut-auth (elonzh, MIT)"
    ),
    PerSchoolEntry(
        "school-seu", "东南大学 SEU",
        "SEUTimetable (sakimidare, Apache-2.0)\nAetik-yue/hormone (SEU SSO 入口)\nluzy99/SEUAutoLogin"
    ),
    PerSchoolEntry(
        "school-zju", "浙江大学 ZJU",
        "zju-ical-py (Xecades, LGPL-2.1)"
    ),
    PerSchoolEntry(
        "school-ustc", "中国科学技术大学 USTC",
        "USTC-timetable-to-ics (1970633640)"
    ),
    PerSchoolEntry(
        "school-scu", "四川大学 SCU",
        "ScuTimetable (Z-P-J)"
    ),
    PerSchoolEntry(
        "school-neu", "东北大学 NEU",
        "neu_wisedu2wakeup (CreamPig233)\nPopulusYang/NeuTimetable\nneucn/elise\nRekaYOO/NEU-JWXT-Toolkit\nPeterPtroc/neu-jwxt-to-wakeup\nleavesvv-source/NEU-Timetable"
    ),
    PerSchoolEntry(
        "school-cqu", "重庆大学 CQU",
        "时光课程表 cqu.js (茵符草)\n321CQU/pymycqu\nBillYang2016/CQU-class2ics\nhaowang02/CourseMonitor\nLengerHu/CQU_classtabletoics\nHagb/cqu_timetable_new\nVayneDuan/CQU-Grade-Monitor\nweearc/cm-http-api\nbarryZZJ/course_to_calander_converter"
    ),
    PerSchoolEntry(
        "school-whut", "武汉理工大学 WHUT",
        "courseTable (acm910)"
    ),
    PerSchoolEntry(
        "school-uestc", "电子科技大学 UESTC",
        "MilLoong/UESTC-EAMS-Helper-App\nMilLoong/UESTC-EAMS-Helper-Python\nKaranocaVe/UESTCJWCWatchdog\nwhtsky/uestc-eams-cleartimeout-userscript\nSunmxt/UESTC-EAMS"
    ),
    PerSchoolEntry(
        "school-gdut", "广东工业大学 GDUT",
        "N0tExpectErr0r/GDUT-ClassTimeTable\nRichard-Zheng/GDUT-Schedule-ng\nStarArchive/gdut-course-frontend\nStarArchive/gdut-course-backend\nHoneQ7/GDUT_iOS_Timetable"
    ),
    PerSchoolEntry(
        "school-gdufe", "广东财经大学 GDUFE",
        "jkgeekJack/Android-GDUFE-JWC-SDK-1.0.0\nKiteio/GDUFE-wrapper"
    ),
    PerSchoolEntry(
        "school-gduf", "广东金融学院 GDUf",
        "Kiteio/Punica\ngduf-finmind"
    ),
    PerSchoolEntry(
        "school-gdufs", "广东外语外贸大学 GDUFS",
        "yongjianzheng/Gdufszhushou\nCrazioker/agency"
    ),
    PerSchoolEntry(
        "school-csust", "长沙理工大学 CSUST",
        "zHElEARN/CSUSTKit\nCreaMakers/EduSpider\ntimeisthe/CSUSTDataGet\nJulius-lq/EduAdminSystem\nJS-CAUTION/csust-course-schedule"
    ),
    PerSchoolEntry(
        "school-bupt", "北京邮电大学 BUPT",
        "helium777/bupt-course-grab\nJmPotato/BUPT-Grader\nSeizzzz/Auto-Login-BUPT"
    ),
    PerSchoolEntry(
        "school-pku", "北京大学 PKU",
        "zhongxinghong/PKUAutoElective\nthezzisu/pku-elective\nHovennnnn/PKUAutoElective2023\nLihhan/AutoElective_4_PKU\nAuYang261/PKU_Elective_Toolset"
    ),
    PerSchoolEntry(
        "school-buct", "北京化工大学 BUCT",
        "MarkYangKp/ZhengFangJY"
    ),
    PerSchoolEntry(
        "school-bjfu", "北京林业大学 BJFU",
        "Bloomberg2000/bjfu_course_ics_generator\nBloomberg2000/bjfu_util.py"
    ),
    PerSchoolEntry(
        "school-ahu", "安徽大学 AHU",
        "Tonyseth/AHU_JW_GPA_Calculator"
    ),
    PerSchoolEntry(
        "school-nefu", "东北林业大学 NEFU",
        "bboy-xp/nefu-crawler\nheyMahalo/crouse_select"
    ),
    PerSchoolEntry(
        "school-dhu", "东华大学 DHU",
        "tk.dcmmcc\nBad-086/DHU_CourseMonitor"
    ),
    PerSchoolEntry(
        "school-ynufe", "云南财经大学 YNUFE",
        "NINIYOYYO/ynufe-campus-app\nMiaoWuNYA/ynufeRealLogin"
    ),
    PerSchoolEntry(
        "school-bit", "北京理工大学 BIT",
        "BIT-Login (BIT101-dev)"
    ),
    PerSchoolEntry(
        "school-bistu", "北京信息科技大学 BISTU",
        "iBistu (ProjektMing)"
    ),
    PerSchoolEntry(
        "school-ahujz", "安徽建筑大学 AHU-JZ",
        "JdaAssist (CH4019, MIT)"
    ),
    PerSchoolEntry(
        "school-cqytu", "重庆邮电大学移通学院 CQYTU",
        "CQYTZFCheckScores (xM3GAN, Apache-2.0)"
    ),
    PerSchoolEntry(
        "school-scau", "华南农业大学 SCAU",
        "ScheduleXParser_SCAU (greyovo)"
    ),
    PerSchoolEntry(
        "school-qlu", "齐鲁工业大学 QLU",
        "JW-spider (Zhy423310825)"
    ),
    PerSchoolEntry(
        "school-bhu", "渤海大学 BHU",
        "BohaiServiceDome (joun233)"
    ),
    PerSchoolEntry(
        "school-nepu", "东北石油大学 NEPU",
        "WeNEPU (cutiechi)"
    ),
    PerSchoolEntry(
        "school-nust", "南京理工大学 NUST",
        "HeraldStudentCurriculum (idailylife)"
    )
)
