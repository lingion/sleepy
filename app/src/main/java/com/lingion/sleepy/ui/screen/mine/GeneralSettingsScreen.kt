package com.lingion.sleepy.ui.screen.mine

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.ui.component.SettingsLayout
import com.lingion.sleepy.R
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.ui.component.settingsSection
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.ui.component.sleepyExpandSpecs
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.AppPrefs

/** Application language, performance and editing preferences. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppPrefs.getLanguage(context)) }
    var languageExpanded by rememberSaveable { mutableStateOf(false) }
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
            verticalArrangement = Arrangement.spacedBy(SettingsLayout.cardGap)
        ) {
            // The language card shows the current language until expanded.
            settingsSection(R.string.settings_language, first = true) {
                val currentLabel = languages.firstOrNull { it.first == language }?.second ?: language
                Column(
                    modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).background(colors.surfaceContainer)
                ) {
                    // 折叠头: 当前语言值 + 展开箭头(2026-09-21: 组标题已示「语言」, 卡标题禁两遍, 改显当前值)
                    Row(
                        modifier = Modifier.fillMaxWidth().noRippleClickable { languageExpanded = !languageExpanded }
                            .dbgTag("general_language_card", text = currentLabel, role = "button")
                            .heightIn(min = SettingsLayout.minRowHeight)
                            .padding(horizontal = SettingsLayout.cardInset, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentLabel,
                                style = SettingsLayout.headingStyle,
                                color = colors.onSurface
                            )
                        }
                        // MD3E (Task 7a): chevron 旋转走 defaultSpatial (收稳不弹, 与展开家族同拍)
                        val langArrowRotation by animateFloatAsState(
                            targetValue = if (languageExpanded) 180f else 0f,
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
                            label = "language-arrow"
                        )
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.rotate(langArrowRotation)
                        )
                    }
                    // MD3E (Task 7a): 旧全限定名一并转正; 展开家族 = 尺寸 fastSpatial
                    // + 淡入淡出 motionScheme
                    val expandSpecs = sleepyExpandSpecs()
                    AnimatedVisibility(
                        visible = languageExpanded,
                        enter = expandSpecs.enter,
                        exit = expandSpecs.exit,
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            languages.forEach { (code, label) ->
                                val selected = language == code
                                Row(
                                    modifier = Modifier.fillMaxWidth().noRippleClickable {
                                        language = code
                                        AppPrefs.setLanguage(context, code)
                                        (context as? android.app.Activity)?.recreate()
                                    }
                                        .dbgTag("general_language#$code", text = label, role = "radio")
                                        .heightIn(min = SettingsLayout.minRowHeight).padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = label, style = SettingsLayout.itemStyle, color = if (selected) colors.primary else colors.onSurface)
                                    if (selected) Icon(Icons.Outlined.Check, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                                }
                                if (code != languages.last().first) HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                            }
                        }
                    }
                }
            }

            // Keep the existing high-refresh switch and immediate application behavior.
            settingsSection(R.string.settings_performance) {
                var highRefresh by remember { mutableStateOf(AppPrefs.isHighRefresh(context)) }
                val colors = MaterialTheme.colorScheme
                Row(
                    modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).background(colors.surfaceContainer)
                        .padding(horizontal = SettingsLayout.cardInset, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_high_refresh),
                        style = SettingsLayout.itemStyle,
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
                        modifier = Modifier.dbgTag("general_high_refresh", text = stringResource(R.string.settings_high_refresh), role = "switch")
                    )
                }
            }

            settingsSection(R.string.settings_editing) {
                Column(modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).background(colors.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(SettingsLayout.descriptionGap)) {
                        Text(stringResource(R.string.settings_undo_depth), style = SettingsLayout.headingStyle)
                        Text(stringResource(R.string.settings_undo_experiment), style = SettingsLayout.descriptionStyle, color = colors.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.settings_undo_mode), style = SettingsLayout.itemStyle)
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
