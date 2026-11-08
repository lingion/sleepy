package com.lingion.sleepy.ui.screen.mine

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.lingion.sleepy.ui.component.liquidglass.GlassIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwitchDefaults
import com.lingion.sleepy.ui.component.liquidglass.GlassSwitch as Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import com.lingion.sleepy.ui.component.liquidglass.GlassTopAppBar as TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.R
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.ui.component.SectionHeader
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.AppPrefs

/** Application language, performance and editing preferences. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppPrefs.getLanguage(context)) }
    val languages = listOf("zh-CN" to "简体中文", "zh-TW" to "繁體中文", "en" to "English", "ja" to "日本語", "es" to "Español")
    var undoDepth by remember { mutableStateOf(AppPrefs.getUndoDepth(context)) }
    var customDepth by rememberSaveable { mutableStateOf(if (undoDepth > 0) undoDepth.toString() else "5") }
    fun setUndoDepth(value: Int) {
        undoDepth = value
        AppPrefs.setUndoDepth(context, value)
        UndoManager.applyMaxDepth(value)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mine_general)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.dbgTag("general_back", role = "button")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 语言：从当前值锚点展开浮框 (分支裁定: 独立锚定窗口替代内联折叠) ──
            item {
                SectionHeader(title = stringResource(R.string.settings_language))
            }

            item {
                com.lingion.sleepy.ui.component.liquidglass.GlassLanguageMenu(
                    title = stringResource(R.string.settings_language),
                    selectedCode = language,
                    languages = languages,
                    onSelect = { code ->
                        if (language != code) {
                            language = code
                            AppPrefs.setLanguage(context, code)
                            (context as? android.app.Activity)?.recreate()
                        }
                    },
                )
            }

            item { SectionHeader(stringResource(R.string.settings_performance)) }
            // Keep the existing high-refresh switch and immediate application behavior.
            item {
                var highRefresh by remember { mutableStateOf(AppPrefs.isHighRefresh(context)) }
                val colors = MaterialTheme.colorScheme
                Row(
                    modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).background(colors.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_high_refresh),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = highRefresh,
                        onCheckedChange = { on ->
                            highRefresh = on
                            AppPrefs.setHighRefresh(context, on)
                            val activity = context as? android.app.Activity
                            if (activity == null) {
                                // 理论不可达 (本页只从 MainActivity 进入); 留日志防静默失败
                                Log.w("GeneralSettings", "high refresh toggle: context is not Activity, apply skipped")
                                return@Switch
                            }
                            com.lingion.sleepy.util.HighRefreshRate.apply(activity, on)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary),
                        modifier = Modifier.heightIn(max = 32.dp)
                            .dbgTag("general_high_refresh", text = stringResource(R.string.settings_high_refresh), role = "switch")
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.settings_editing)) }
            item {
                Column(modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).background(colors.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_undo_depth), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_undo_experiment), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text(stringResource(R.string.settings_undo_mode), style = MaterialTheme.typography.bodyMedium)
                    SegmentedSwitcher(
                        options = listOf(0 to stringResource(R.string.settings_undo_off), 1 to stringResource(R.string.settings_undo_limited), 2 to stringResource(R.string.settings_undo_unlimited)),
                        selected = when { undoDepth == 0 -> 0; undoDepth < 0 -> 2; else -> 1 },
                        onSelect = { mode ->
                            val next = when (mode) { 0 -> 0; 2 -> -1; else -> customDepth.toIntOrNull()?.takeIf { it > 0 } ?: 5 }
                            if (mode == 1) customDepth = next.toString()
                            setUndoDepth(next)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = colors.surfaceContainerHighest,
                    )
                    if (undoDepth > 0) {
                        TextField(
                            value = customDepth,
                            onValueChange = { value ->
                                if (value.length <= 6 && value.all { it.isDigit() }) {
                                    customDepth = value
                                    value.toIntOrNull()?.takeIf { it > 0 }?.let { setUndoDepth(it) }
                                }
                            },
                            label = { Text(stringResource(R.string.settings_undo_depth_label)) },
                            supportingText = { Text(stringResource(R.string.settings_undo_count_hint)) },
                            isError = customDepth.toIntOrNull()?.let { it > 0 } != true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = SleepyTheme.fieldShape,
                            colors = SleepyTheme.fieldColors()
                        )
                    }
                }
            }
        }
    }
}
