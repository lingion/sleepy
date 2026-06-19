package com.lingion.sleepy.ui.screen.imports

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.jw.JwImportViewModel
import com.lingion.sleepy.data.jw.JwSchoolInfo
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

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

                when {
                    importFinished -> {
                        LaunchedEffect(Unit) { finish() }
                    }

                    stage is Stage.SelectSchool -> {
                        SchoolSelectScreen(
                            onSchoolSelected = { school ->
                                if (school.url.isBlank()) {
                                    errorMsg = "该学校暂未配置教务 URL"
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
                                onHtmlCaptured = { html, sch ->
                                    statusMsg = "解析中…"
                                    scope.launch {
                                        try {
                                            val courses = jwViewModel.parseHtml(html, sch.type ?: "")
                                            if (courses.isEmpty()) {
                                                errorMsg = "解析结果为空，请确认已到达「个人课表」页（而非首页/登录页）"
                                                statusMsg = null
                                                return@launch
                                            }
                                            // 落库：直接进新课表
                                            val tableId = jwViewModel.importAsNewTable(
                                                courses = courses,
                                                tableName = "教务导入 - ${sch.name}",
                                                startDate = null
                                            )
                                            statusMsg = "成功导入 ${courses.size} 门课程"
                                            importFinished = true
                                        } catch (e: Exception) {
                                            errorMsg = "解析失败: ${e.message}\n\n提示：HEU 需在浏览器中登录后导航到『个人课表』页面再点导入"
                                            statusMsg = null
                                        }
                                    }
                                },
                                onBack = { stage = Stage.SelectSchool }
                            )
                        }
                    }
                }

                // 错误与状态提示（用 Snackbar 替代弹窗，简化为内嵌 Box）
                if (errorMsg != null) {
                    LaunchedEffect(errorMsg) {
                        // 简单实现：自动清除
                        kotlinx.coroutines.delay(4000)
                        errorMsg = null
                    }
                }
            }
        }
    }

    private sealed class Stage {
        object SelectSchool : Stage()
        object WebViewLogin : Stage()
    }
}
