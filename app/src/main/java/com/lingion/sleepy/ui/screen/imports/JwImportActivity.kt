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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.jw.JwCourse
import com.lingion.sleepy.data.jw.JwImportViewModel
import com.lingion.sleepy.data.jw.JwSchoolInfo
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.ui.component.DatePickerField
import com.lingion.sleepy.ui.component.TimeSlotEditor
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
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
 * HEU 走 QZ_CRAZY 协议；其他学校后续按 url 协议类型自动选 parser。
 */
class JwImportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val themeKey = androidx.compose.runtime.remember { mutableStateOf("default") }
            SleepyThemeProvider(darkTheme = dark, themeKey = themeKey.value) {
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
                            containerColor = colors.surface,
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
                                                tableName = getString(R.string.jw_import_title, school.name),
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
                                    Log.d("JwImport", "onHtmlCaptured htmlLen=${html.length} type=${sch.type} periods=${periods.size}")
                                    statusMsg = getString(R.string.import_parsing)
                                    scope.launch {
                                        try {
                                            val courses = jwViewModel.parseHtml(html, sch.type ?: "")
                                            Log.d("JwImport", "parseHtml returned ${courses.size} courses")
                                            if (courses.isEmpty()) {
                                                errorMsg = getString(R.string.jw_parse_empty)
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
                                containerColor = Color(0xFFFFEBEE)
                            )
                        ) {
                            Text(
                                text = msg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                color = Color(0xFFB71C1C)
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
