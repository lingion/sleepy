package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.ThemePreset
import com.lingion.sleepy.ui.theme.ThemePresets
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    themeMode: String = AppPrefs.THEME_MODE_SYSTEM,
    onThemeModeChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val colors = SleepyTheme.colors
    val currentKey by AppPrefs.themeKeyFlow(context).collectAsState(initial = AppPrefs.getThemeKey(context))
    var selectedMode by remember(themeMode) { mutableStateOf(themeMode) }
    // ★ 选主题/模式后立即刷小组件: 之前只写 SP 不刷 widget → Glance 小组件不跟主题变
    // (WeekGrid 靠系统 configuration change 兜底能变, Glance 不行 → 必须主动 notifyDataChanged)
    val widgetScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    fun refreshWidgets() {
        android.util.Log.e("Appearance", ">>> refreshWidgets() CALLED from AppearanceScreen")
        widgetScope.launch { com.lingion.sleepy.widget.WidgetUpdater.notifyDataChanged(context) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mine_appearance)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background, titleContentColor = colors.onBackground, navigationIconContentColor = colors.onBackground)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 主题颜色：先保留原版"跟随系统"卡片 ──
            item {
                SystemThemeCard(
                    selected = currentKey == ThemePresets.KEY_SYSTEM,
                    onClick = { AppPrefs.setThemeKey(context, ThemePresets.KEY_SYSTEM); refreshWidgets() }
                )
            }

            // ── 2 列预设主题色彩卡片 ──
            item {
                val presets = ThemePresets.all
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    presets.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { p -> Box(Modifier.weight(1f)) { PresetThemeCard(p, currentKey == p.key, onClick = { AppPrefs.setThemeKey(context, p.key); refreshWidgets() }) } }
                            if (row.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            }

            // ── 外观模式 toggle：浅色 / 深色 / 跟随系统 ──
            item {
                Text(stringResource(R.string.theme_appearance), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(8.dp))
                val modes = listOf(
                    AppPrefs.THEME_MODE_SYSTEM to stringResource(R.string.theme_mode_system),
                    AppPrefs.THEME_MODE_LIGHT to stringResource(R.string.theme_mode_light),
                    AppPrefs.THEME_MODE_DARK to stringResource(R.string.theme_mode_dark)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainer).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    modes.forEach { (mode, label) ->
                        val sel = mode == selectedMode
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (sel) colors.primary else colors.surfaceContainer).clickable {
                                if (mode != selectedMode) {
                                    selectedMode = mode; AppPrefs.setThemeMode(context, mode); onThemeModeChange(mode); refreshWidgets()
                                }
                            }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium), color = if (sel) colors.onPrimary else colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ── 以下复制自 ThemeColorScreen ──

@Composable
private fun SystemThemeCard(selected: Boolean, onClick: () -> Unit) {
    val colors = SleepyTheme.colors
    val borderColor = if (selected) colors.primary else colors.outline.copy(alpha = 0.18f)
    val borderWidth = if (selected) 2.dp else 1.dp
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        color = colors.surfaceContainer, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = colors.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.theme_system), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.theme_system_desc), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Outlined.Check, stringResource(R.string.selected), tint = colors.primary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PresetThemeCard(preset: ThemePreset, selected: Boolean, onClick: () -> Unit) {
    val colors = SleepyTheme.colors
    val scheme = if (colors.background.red < 0.5f) preset.light else preset.dark
    val borderColor = if (selected) colors.primary else colors.outline.copy(alpha = 0.18f)
    val borderWidth = if (selected) 2.dp else 1.dp
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        color = colors.surfaceContainer, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorSwatch(scheme.primary)
                ColorSwatch(scheme.secondary)
                ColorSwatch(scheme.tertiary)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(preset.nameRes), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = colors.onSurface, modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Outlined.Check, stringResource(R.string.selected), tint = colors.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(color))
}
