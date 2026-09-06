package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme

/**
 * 开源许可与致谢子页 (v1.0.46 用户令: 从关于页长卡分离)。
 *
 * 关于页原样塞 GPL 正文 + 全部教务适配致谢, B 档收录后致谢条目越滚越长,
 * 关于页被拖成一屏读不完。拆为独立二级页: 关于页留一行入口 (标题+副题),
 * 本页承载全部内容 — 许可证区块 + 逐条致谢卡 (项目名/协议/适配用途)。
 * 布局与 HolidaySettingsScreen 同款: Scaffold + TopAppBar 返回 + LazyColumn。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(onBack: () -> Unit) {
    val colors = SleepyTheme.colors

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

            // ---- 致谢区块 ----
            item {
                LicenseCard {
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
            }

            // 逐条致谢卡 — 每条 = 项目名 (作者, license) + 适配说明。
            // 条目清单与 AboutLicenseAttributionTest 的 BATCH_A/BATCH_B 对应:
            // 新收录学校落地时须同步在 strings 三语追加本条致谢 + 这里加一行。
            licenseAttributionEntries().forEach { entry ->
                item {
                    LicenseCard {
                        Text(
                            text = entry.project,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = entry.meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = entry.usage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 一条致谢: 项目名 / (作者, license) 元信息 / 适配用途说明。 */
private data class AttributionEntry(val project: String, val meta: String, val usage: String)

/**
 * 致谢条目。项目名与 license 是硬事实 (与 about_license_body 及回归测试一致),
 * 用途说明为中文短句 — 项目名/license/人名是通用标识不翻译, 说明文字其余
 * 语言经 about_license_body 全文兜底 (本页 usage 仅为增强可读性的补充行)。
 */
private fun licenseAttributionEntries(): List<AttributionEntry> = listOf(
    AttributionEntry(
        "WakeUp 课程表 (YZune)", "Apache-2.0",
        "JwCourse / JwParser 中间结构语义与强智系 HTML 解析的参考实现"
    ),
    AttributionEntry(
        "WakeupSchedule_BUPT (dIT8Zv)", "Apache-2.0",
        "十二个教务解析器的上游:强智全家族(qz/qz_with_node/qz_br/qz_crazy/qz_old)、老版正方、URP、青果、新正方、HNUST 与 Parser 设计"
    ),
    AttributionEntry(
        "WakeupSchedule_Kotlin (YZune)", "Apache-2.0",
        "经典金智 EAMS 导入实现 (TaskActivity 位图解析) 的参考"
    ),
    AttributionEntry(
        "时光课程表 cqu.js", "",
        "重庆大学门户 REST 协议 (session / 课表 / 作息三接口) 的分析依据"
    ),
    AttributionEntry(
        "HFUT-Schedule (Chiu-xaH)", "MIT",
        "合工大 EAMS5 全协议: course-table → schedule-table/datum 三段 fetch (六种 studentId 形态与登录态 302 重定向检测)"
    ),
    AttributionEntry(
        "HfutOpenApi (BoynChan)", "MIT",
        "合工大教务全接口封装参考: 与 HFUT-Schedule 形成多源印证, 用于交叉验证 studentId 形态谱"
    ),
    AttributionEntry(
        "hfut_schedule_hacker (Aoi-cn)", "无 LICENSE",
        "合工大课表小程序: 本轮调研虽未取到 studentId 直接证据, 但项目维护活跃, 列入参考以便后续核对"
    ),
    AttributionEntry(
        "django-hfut-auth (elonzh)", "MIT",
        "合工大身份认证后端参考: 倒排验证 supwisdom EAMS5 入口形态"
    ),
    AttributionEntry(
        "SEUTimetable (sakimidare)", "Apache-2.0",
        "东南大学 URP JSON 字段映射与 ZCMC 周次串解析语义"
    ),
    AttributionEntry(
        "zju-ical-py (Xecades)", "LGPL-2.1",
        "浙江大学本研课表 (UGR) kbList JSON 形态佐证"
    ),
    AttributionEntry(
        "USTC-timetable-to-ics (1970633640)", "",
        "中科大 studentTableVm activities 字段映射与周次串解析"
    ),
    AttributionEntry(
        "ScuTimetable (Z-P-J)", "",
        "四川大学 dateList / selectCourseList 协议形态与 day 映射"
    ),
    AttributionEntry(
        "neu_wisedu2wakeup (CreamPig233)", "",
        "东北大学 arrangedList 字段映射、教师提取与周次串括号处理参考"
    ),
    AttributionEntry(
        "shiguang_warehouse (XingHeYuZhuan)", "MIT",
        "武汉理工大学 kcbcxby 协议、经典金智 EAMS (hunnu/uestc/hpu) 与新正方网格视图 (zhengfang_01) 的协议形态参考"
    ),
    AttributionEntry(
        "iwut (TokenTeam)", "AGPL-3.0 · 仅参考协议形态",
        "武汉理工大学节次 DM 映射的协议佐证 (未引用代码)"
    ),
    AttributionEntry(
        "zfn_api (openschoolcn)", "MPL-2.0",
        "新正方 jwglxt kbList 接口形态交叉验证"
    ),
    AttributionEntry(
        "FlowCourse (jiaweiyaya)", "GPL-3.0",
        "新正方 kbList 主流形态与 jc 多形态交叉验证"
    ),
    // ---- 学校同学维护的项目 (2026-09 逐校验证的第一手协议证据) ----
    AttributionEntry(
        "BIT-Login (BIT101-dev)", "",
        "北京理工大学统一身份认证登录链路 (现行 jwapp 入口的证据源)"
    ),
    AttributionEntry(
        "iBistu (ProjektMing)", "",
        "北京信息科技大学金智 jwapp 课表 API 的证据源"
    ),
    AttributionEntry(
        "JdaAssist (CH4019)", "MIT",
        "安徽建筑大学教务入口 (https) 的佐证之一"
    ),
    AttributionEntry(
        "CQYTZFCheckScores (xM3GAN)", "Apache-2.0",
        "重庆邮电大学移通学院新正方 RSA 登录链路的证据源"
    ),
    AttributionEntry(
        "ScheduleXParser_SCAU (greyovo)", "",
        "华南农业大学新强智教务一键导入的协议参考"
    ),
    AttributionEntry(
        "JW-spider (Zhy423310825)", "",
        "齐鲁工业大学新正方接口链的证据源"
    ),
    AttributionEntry(
        "BohaiServiceDome (joun233)", "",
        "渤海大学老教务域 (2020) 的历史佐证"
    ),
    AttributionEntry(
        "courseTable (acm910)", "",
        "武汉理工大学课表结构的社区参考"
    ),
    AttributionEntry(
        "上课 shangkeschedule (qiqqqqq517)", "Apache-2.0",
        "1776 校学校登记表在名单交叉复核中的对照数据源"
    ),
    AttributionEntry(
        "WeNEPU (cutiechi)", "",
        "东北石油大学教务门户协议分析的参考"
    ),
    AttributionEntry(
        "HeraldStudentCurriculum (idailylife)", "",
        "南京理工大学课表查询接口的参考"
    ),
    AttributionEntry(
        "东华大学第三方课表工具 (tk.dcmmcc)", "",
        "东华大学教务协议分析的参考"
    )
)

@Composable
private fun LicenseCard(content: @Composable () -> Unit) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            .background(colors.surfaceContainer)
            .padding(20.dp)
    ) {
        content()
    }
}
