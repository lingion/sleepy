package com.lingion.sleepy.ui.screen.imports

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.jw.JwCourse
import com.lingion.sleepy.data.jw.JwImportViewModel
import com.lingion.sleepy.data.jw.JwParseDiagnostics
import com.lingion.sleepy.data.jw.JwSchoolInfo
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.ui.component.DatePickerField
import com.lingion.sleepy.ui.component.TimeSlotEditor
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import com.lingion.sleepy.R

/**
 * 教务直连导入主屏
 *
 * 流程：学校选择 → WebView 登录抓 HTML → 解析 → 复用 ImportScreen 现有预览 → 落库
 *
 * HEU 走 WISEDU 金智教务协议；其他学校按学校配置的协议类型选择 parser。
 */
class JwImportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 跟随 app 主题设置(此前硬编码 default+只跟系统深色,选春绿/海蓝后此页不跟随)
            val systemDark = isSystemInDarkTheme()
            val dark = remember(systemDark) {
                AppPrefs.isDarkMode(this@JwImportActivity, systemDark)
            }
            val themeKey by AppPrefs.themeKeyFlow(this@JwImportActivity)
                .collectAsState(initial = AppPrefs.getThemeKey(this@JwImportActivity))
            SleepyThemeProvider(darkTheme = dark, themeKey = themeKey) {
                val jwViewModel: JwImportViewModel = viewModel()
                val scheduleViewModel: ScheduleViewModel = viewModel()
                val scope = rememberCoroutineScope()

                var selectedSchool by remember { mutableStateOf<JwSchoolInfo?>(null) }
                var stage by remember { mutableStateOf<Stage>(Stage.SelectSchool) }
                var errorMsg by remember { mutableStateOf<String?>(null) }
                var statusMsg by remember { mutableStateOf<String?>(null) }
                var importFinished by remember { mutableStateOf(false) }
                // 解析后的课程暂存 + 配置确认状态
                var parsedCourses by remember { mutableStateOf<List<JwCourse>>(emptyList()) }
                var parsedSchool by remember { mutableStateOf<JwSchoolInfo?>(null) }
                var configStartDate by remember { mutableStateOf("") }
                var configTimeJson by remember { mutableStateOf("") }
                var configRows by remember { mutableStateOf(emptyList<TimeTableUtils.TimeSlotRow>()) }
                // 用户可改的导入课表名; 初值 = "教务导入 - {学校名}"; 留空 = 沿用初值
                var configTableName by remember(parsedSchool) {
                    mutableStateOf(
                        parsedSchool?.let { getString(R.string.jw_import_title, it.name) } ?: ""
                    )
                }

                when {
                    importFinished -> {
                        LaunchedEffect(Unit) { finish() }
                    }

                    stage is Stage.ConfigureConfirm && parsedCourses.isNotEmpty() -> {
                        val school = parsedSchool
                        if (school == null) {
                            stage = Stage.WebViewLogin
                            parsedCourses = emptyList()
                        } else {
                        val colors = SleepyTheme.colors
                        var confirmError by remember { mutableStateOf<String?>(null) }
                        AlertDialog(
                            onDismissRequest = {
                                stage = Stage.WebViewLogin
                                parsedCourses = emptyList()
                            },
                            title = {
                                Column {
                                    Text(getString(R.string.jw_config_title), color = colors.onSurface)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "${parsedCourses.size} ${getString(R.string.import_courses)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 360.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    DatePickerField(
                                        value = configStartDate,
                                        onValueChange = { configStartDate = it },
                                        label = getString(R.string.import_week_start),
                                        modifier = Modifier.fillMaxWidth(),
                                        isError = confirmError != null
                                    )
                                    // 用户可改的导入课表名 — 教务直连此前无任何命名入口,
                                    // 硬编码成 "教务导入 - {学校名}" 后用户改名要进课表管理.
                                    // 此次把命名入口放到导入前, 落库前最后一次修改机会.
                                    TextField(
                                        value = configTableName,
                                        onValueChange = { configTableName = it },
                                        label = { Text(getString(R.string.jw_table_name_label)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (confirmError != null) {
                                        Text(text = confirmError!!, color = colors.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                    TimeSlotEditor(
                                        rows = configRows,
                                        onRowsChange = { newRows ->
                                            configRows = newRows
                                            configTimeJson = TimeTableUtils.buildTimeJsonFromRows(newRows)
                                        }
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (configStartDate.isBlank() || !Regex("""^\d{4}-\d{2}-\d{2}$""").matches(configStartDate)) {
                                        confirmError = getString(R.string.start_date_format)
                                        return@TextButton
                                    }
                                    val emptyRows = configRows.filter { it.start.isBlank() || it.end.isBlank() }
                                    if (emptyRows.isNotEmpty()) {
                                        confirmError = getString(R.string.slot_time_required, emptyRows.first().node)
                                        return@TextButton
                                    }
                                    val invalidRows = configRows.filter {
                                        !Regex("""^\d{2}:\d{2}$""").matches(it.start) || !Regex("""^\d{2}:\d{2}$""").matches(it.end) || it.start >= it.end
                                    }
                                    if (invalidRows.isNotEmpty()) {
                                        confirmError = getString(R.string.slot_time_invalid, invalidRows.first().node)
                                        return@TextButton
                                    }
                                    confirmError = null
                                    configTimeJson = TimeTableUtils.buildTimeJsonFromRows(configRows)
                                    // 落库
                                    statusMsg = getString(R.string.import_parsing)
                                    scope.launch {
                                        try {
                                            val maxNode = configRows.maxOfOrNull { it.node } ?: 0
                                            val tableId = jwViewModel.importAsNewTable(
                                                courses = parsedCourses,
                                                tableName = configTableName.ifBlank {
                                                    getString(R.string.jw_import_title, school.name)
                                                },
                                                startDate = configStartDate,
                                                timeJson = configTimeJson,
                                                nodesPerDay = maxNode
                                            )
                                            Log.d("JwImport", "importAsNewTable tableId=$tableId courses=${parsedCourses.size}")
                                            statusMsg = getString(R.string.jw_import_success, parsedCourses.size)
                                            importFinished = true
                                        } catch (e: Exception) {
                                            Log.e("JwImport", "import failed", e)
                                            errorMsg = getString(R.string.jw_parse_failed, e.message ?: "")
                                            statusMsg = null
                                        }
                                    }
                                }) {
                                    Text(getString(R.string.jw_config_confirm))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    stage = Stage.WebViewLogin
                                    parsedCourses = emptyList()
                                }) {
                                    Text(getString(R.string.back))
                                }
                            }
                        )
                        } // end else (school != null)
                    }

                    stage is Stage.SelectSchool -> {
                        SchoolSelectScreen(
                            onSchoolSelected = { school ->
                                if (school.url.isBlank()) {
                                    errorMsg = getString(R.string.jw_no_url)
                                    return@SchoolSelectScreen
                                }
                                selectedSchool = school
                                stage = Stage.WebViewLogin
                            },
                            onBack = { finish() }
                        )
                    }

                    stage is Stage.WebViewLogin -> {
                        val school = selectedSchool
                        if (school == null) {
                            stage = Stage.SelectSchool
                        } else {
                            JwWebViewLoginScreen(
                                school = school,
                                onHtmlCaptured = { html, sch, periods ->
                                    // T6 双层判定：sch.type 已知直接用；空 → HTML/URL 组合兜底
                                    val rawType = sch.type
                                    val effectiveType = rawType?.takeIf { it.isNotBlank() }
                                        ?: jwViewModel.detectProtocol(html, sch.url.ifBlank { null })
                                    Log.d("JwImport", "onHtmlCaptured htmlLen=${html.length} rawType=$rawType effectiveType=$effectiveType periods=${periods.size}")
                                    statusMsg = getString(R.string.import_parsing)
                                    scope.launch {
                                        try {
                                            val courses = jwViewModel.parseHtml(html, effectiveType ?: "")
                                            Log.d("JwImport", "parseHtml returned ${courses.size} courses")
                                            if (courses.isEmpty()) {
                                                // T9 诊断壳: classify 拿精确分类再选文案
                                                val diag = try {
                                                    JwParseDiagnostics.classify(
                                                        html = html, url = "", school = sch,
                                                        parsersAttempted = jwViewModel.lastDiagAttempts
                                                    )
                                                } catch (e: Exception) { null }
                                                errorMsg = if (diag != null) {
                                                    DiagMapper.mapImpl(diag, sch, this@JwImportActivity)
                                                } else {
                                                    getString(R.string.jw_err_empty_semester)
                                                }
                                                statusMsg = null
                                                return@launch
                                            }
                                            // 不直接落库，进配置确认页
                                            parsedCourses = courses
                                            parsedSchool = sch
                                            // 根据课程实际节次数生成行；
                                            // 如果 WebView 抓到 periods 则预填，否则空行让用户填
                                            val maxNode = courses.maxOf { maxOf(it.startNode, it.endNode) }
                                            val periodMap = periods.associate { it.first to (it.second to it.third) }
                                            configRows = (1..maxNode).map { node ->
                                                val filled = periodMap[node]
                                                TimeTableUtils.TimeSlotRow(
                                                    node = node,
                                                    start = filled?.first ?: "",
                                                    end = filled?.second ?: ""
                                                )
                                            }
                                            configStartDate = ""
                                            configTimeJson = ""
                                            stage = Stage.ConfigureConfirm
                                            statusMsg = null
                                        } catch (e: Exception) {
                                            Log.e("JwImport", "parseHtml failed", e)
                                            errorMsg = getString(R.string.jw_parse_failed, e.message ?: "") + getString(R.string.jw_parse_failed_hint)
                                            statusMsg = null
                                        }
                                    }
                                },
                                onCaptureError = { status, hint ->
                                    Log.w("JwImport", "capture failed status=$status hint=$hint")
                                    errorMsg = when (status) {
                                        FrameCaptureStatus.CROSS_DOMAIN_IFRAME_BLOCKED -> getString(R.string.jw_err_cross_domain_iframe, hint)
                                        FrameCaptureStatus.CONTAINER_EMPTY_AFTER_DELAY -> getString(R.string.jw_err_container_empty_after_delay)
                                        FrameCaptureStatus.IFRAME_NAV_PENDING          -> getString(R.string.jw_err_iframe_nav_pending)
                                        FrameCaptureStatus.WRONG_PAGE                  -> getString(R.string.jw_err_wrong_page)
                                        FrameCaptureStatus.SESSION_EXPIRED             -> getString(R.string.jw_err_session_expired)
                                        else                                           -> getString(R.string.jw_parse_empty)
                                    }
                                    statusMsg = null
                                },
                                onBack = { stage = Stage.SelectSchool }
                            )
                        }
                    }
                }

                // 错误与状态提示：直接显示在中央 errorMsg + 底部 statusMsg
                errorMsg?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = SleepyTheme.colors.errorContainer
                            )
                        ) {
                            Text(
                                text = msg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                color = SleepyTheme.colors.onErrorContainer
                            )
                        }
                    }
                }
                statusMsg?.let { msg ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Snackbar(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(msg)
                        }
                    }
                }
            }
        }
    }

    private sealed class Stage {
        object SelectSchool : Stage()
        object WebViewLogin : Stage()
        object ConfigureConfirm : Stage()
    }
}

/**
 * T9: 诊断结果 → 用户文案映射。
 * Activity 实例走 [mapImpl] 带 Context 拉 strings.xml；
 * 纯 JVM 单测走 [mapForTest]，context=null 时用静态拼接（VPN/hint 类提示不依赖资源）。
 */
@androidx.annotation.VisibleForTesting
internal object DiagMapper {

    @JvmStatic
    fun mapForTest(diag: JwParseDiagnostics.Result, school: com.lingion.sleepy.data.jw.JwSchoolInfo): String =
        mapImpl(diag, school, context = null)

    /** 内部实现 — context 非空时用 strings 资源, 为 null 时用内置兜底文案 */
    fun mapImpl(
        diag: JwParseDiagnostics.Result,
        school: com.lingion.sleepy.data.jw.JwSchoolInfo,
        context: android.content.Context?
    ): String {
        fun str(resId: Int, vararg args: Any): String =
            context?.getString(resId, *args)
                ?: when (resId) {
                    R.string.jw_diag_session_expired ->
                        "${school.name} 的会话已过期或未登录。请重新登录后停留到「个人课表」页再点抓取"
                    R.string.jw_diag_no_container ->
                        "${school.name} 的页面未找到课表容器。可能原因：①抓取时机过早课表未加载；②页面为图片课表或跨域 iframe；③教务系统已升级"
                    R.string.jw_diag_header_no_node ->
                        "${school.name} 的课表缺少逐节行头。可能为图片课表或组头合并"
                    R.string.jw_diag_image_cells ->
                        "${school.name} 的课表单元格为图片，无法识别。请改用文件导入或手动添加课程"
                    R.string.jw_diag_empty_semester ->
                        "${school.name} 的页面声明本学期暂无课程。请确认已选对学期"
                    R.string.jw_diag_wrong_protocol ->
                        "${school.name} 的学校标注协议与实际页面不一致。请反馈开发者"
                    else ->
                        "${school.name} 解析结果为空。诊断特征：${diag.matchedFeatures.take(5).joinToString("/")}"
                }
        val catResId = when (diag.category) {
            JwParseDiagnostics.Category.SESSION_EXPIRED -> R.string.jw_diag_session_expired
            JwParseDiagnostics.Category.NO_TABLE_CONTAINER -> R.string.jw_diag_no_container
            JwParseDiagnostics.Category.HEADER_NO_NODE -> R.string.jw_diag_header_no_node
            JwParseDiagnostics.Category.IMAGE_OR_EMPTY_CELLS -> R.string.jw_diag_image_cells
            JwParseDiagnostics.Category.EMPTY_SEMESTER -> R.string.jw_diag_empty_semester
            JwParseDiagnostics.Category.WRONG_PROTOCOL -> R.string.jw_diag_wrong_protocol
            JwParseDiagnostics.Category.UNKNOWN_EMPTY -> R.string.jw_diag_unknown_empty
        }
        val base = str(catResId, school.name)
        // 特殊学校 hint: 临沂大学(校园网限制) / 强智系(会话踢下线) / B 档 985 校(校外多须 VPN/WebVPN)
        val schoolHint = when {
            school.url.contains("jwgl.lyu.edu.cn") ||
            school.url.contains("jwxt.lyu.edu.cn") ||
            // v1.0.46 B 档新 985 校: 教务域名普遍校外受限, 0 课兜底文案易被误读为学期选错
            school.url.contains("jwxt.neu.edu.cn") ||        // 东北大学
            school.url.contains("newxk.urp.seu.edu.cn") ||   // 东南大学
            school.url.contains("xsjw2018.jw.scut.edu.cn") ||// 华南理工大学
            school.url.contains("jxglstu.hfut.edu.cn") ||    // 合肥工业大学
            school.url.contains("zdbk.zju.edu.cn") ||        // 浙江大学
            school.url.contains("jw.ustc.edu.cn") ||         // 中国科学技术大学
            school.url == "https://scu.edu.cn/" ||           // 四川大学 (条目 URL 即门户域)
            school.url.contains("jwms.bit.edu.cn") ||        // 北京理工大学 (legacy URL 兼容)
            school.url.contains("jxzxehallapp.bit.edu.cn") || // 北京理工大学 (现行 URL)
            school.url.contains("csujwc.its.csu.edu.cn") ||  // 中南大学
            school.url.contains("jwxt.whut.edu.cn") ||       // 武汉理工大学 (登录后偶发限流, 提示换网络)
            // 2026-09 211 批量收录: 海外探测超时率高/域名校内受限的新校, 0 课兜底文案易误读为学期选错
            school.url.contains("jwxt.scnu.edu.cn") ||       // 华南师范大学
            school.url.contains("hdjw.hnu.edu.cn") ||        // 湖南大学 (Njw2017)
            school.url.contains("jw.ruc.edu.cn") ||          // 中国人民大学 (Njw2017)
            school.url.contains("jw.dhu.edu.cn") ||          // 东华大学
            school.url.contains("jw.ahu.edu.cn") ||          // 安徽大学 (supwisdom 新版)
            school.url.contains("jwxt.cumtb.edu.cn") ||      // 矿大北京 (supwisdom 新版)
            school.url.contains("jwxt.ybu.edu.cn") ||        // 延边大学
            school.url.contains("jwgl.shzu.edu.cn") ||       // 石河子大学
            school.url.contains("eams.uestc.edu.cn") ||      // 电子科技大学 (经典 EAMS, 202 鉴权)
            school.url.contains("eams.sufe.edu.cn") ||       // 上海财经大学 (经典 EAMS)
            school.url.contains("jwglnew.hunnu.edu.cn") ||   // 湖南师范大学 (经典 EAMS, iframe)
            school.url.contains("aao-eas.nuaa.edu.cn") ||    // 南京航空航天大学 (经典 EAMS)
            school.url.contains("jwgl.gzhmu.edu.cn") ->      // 广州医科大学 (强智, 教务域公网不解析, 校外须 VPN/WebVPN)
                "该校教务系统仅校内可访问。若在校外，请先连接校园网或 VPN 后再试"
            school.type in setOf(
                com.lingion.sleepy.data.jw.JwProtocol.TYPE_QZ,
                com.lingion.sleepy.data.jw.JwProtocol.TYPE_QZ_CRAZY,
                com.lingion.sleepy.data.jw.JwProtocol.TYPE_QZ_BR,
                com.lingion.sleepy.data.jw.JwProtocol.TYPE_QZ_WITH_NODE,
                com.lingion.sleepy.data.jw.JwProtocol.TYPE_QZ_OLD
            ) -> "若强智教务长时间无法加载，多为会话被踢或校外网络限制，请重新登录或换网络"
            else -> ""
        }
        val hintPart = if (schoolHint.isNotBlank()) "\n\n$schoolHint" else ""
        // UNKNOWN_EMPTY 应回显诊断特征
        return if (diag.category == JwParseDiagnostics.Category.UNKNOWN_EMPTY) {
            "$base（${diag.matchedFeatures.take(5).joinToString("/")}）$hintPart"
        } else {
            base + hintPart
        }
    }
}
