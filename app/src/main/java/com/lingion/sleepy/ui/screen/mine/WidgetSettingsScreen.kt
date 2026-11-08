package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import com.lingion.sleepy.ui.component.liquidglass.GlassTonalButton as FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.lingion.sleepy.ui.component.liquidglass.GlassIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.lingion.sleepy.ui.component.liquidglass.GlassTopAppBar as TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.LocalNavExtraBottomPadding
import com.lingion.sleepy.ui.component.SectionHeader
import com.lingion.sleepy.ui.component.SettingToggleRow
import com.lingion.sleepy.ui.component.SettingsCard
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.widget.WidgetManagementViewModel
import com.lingion.sleepy.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 小组件设置：管理入口、全局外观、共享课表显示入口、高级选项、刷新动作。
 * 全局课表行为只在课表显示页设置，本页保留按作用范围说明的导航入口。
 * 偏好键、按实例配置与已放置组件的刷新条件保持不变。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    onBack: () -> Unit = {},
    onOpenWidgetManagement: () -> Unit = {},
    onOpenScheduleDisplay: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    var expanded by rememberSaveable { mutableStateOf(true) }
    var widgetColorless by remember { mutableStateOf(AppPrefs.isWidgetColorless(context)) }
    var widgetSeparator by remember { mutableStateOf(AppPrefs.isWidgetSeparator(context)) }
    var vertPunct by remember { mutableStateOf(AppPrefs.isVertPunctReplace(context)) }

    // 显示项变更后立即刷小组件(管线随板块自 GeneralSettingsScreen 平移)
    val widgetScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    fun refreshWidgets() {
        widgetScope.launch { WidgetUpdater.notifyDataChanged(context) }
    }

    // 已放置实例 = 页尾刷新按钮的存在条件(单一真源,同「添加或管理」页)
    val vm = remember { WidgetManagementViewModel() }
    val items by vm.state.collectAsState()

    // 去画廊/桌面增删小组件后回本页要重扫,否则按钮出现/消失态滞留
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.reload()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 刷新成功反馈: 按钮迁移自「我的」页,snackbar 链随迁
    val uiScope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val widgetsRefreshedMessage = stringResource(R.string.mine_refresh_widgets_done)

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mine_widget)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
        // 页尾余量含 LocalNavExtraBottomPadding(同「我的」页约定):
        // overlay 页 default≈0 无害; 若 Dock 形态下渲染,悬浮栏不压按钮
        val navExtra = LocalNavExtraBottomPadding.current
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + navExtra
            ),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            // 添加或管理桌面小组件: 跳「添加或管理」页(右上 ＋ 进画廊)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(SleepyTheme.shapes.large)
                        .background(colors.surfaceContainer)
                        .noRippleClickable(onOpenWidgetManagement)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.widget_manage_entry),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_widget_global_appearance),
                    expanded = expanded,
                    onToggle = { expanded = !expanded }
                ) {
                    SettingToggleRow(
                        label = stringResource(R.string.settings_widget_colorless),
                        subtitle = stringResource(R.string.settings_widget_colorless_sub),
                        checked = widgetColorless,
                        onCheckedChange = {
                            widgetColorless = it
                            AppPrefs.setWidgetColorless(context, it)
                            refreshWidgets()
                        }
                    )
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    SettingToggleRow(
                        label = stringResource(R.string.settings_widget_separator),
                        subtitle = stringResource(R.string.settings_widget_separator_sub),
                        checked = widgetSeparator,
                        onCheckedChange = {
                            widgetSeparator = it
                            AppPrefs.setWidgetSeparator(context, it)
                            refreshWidgets()
                        }
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(SleepyTheme.shapes.large)
                        .background(colors.surfaceContainer)
                        .noRippleClickable(onOpenScheduleDisplay)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_widget_shared_display),
                            style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.settings_widget_shared_display_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                        tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            item { SectionHeader(title = stringResource(R.string.settings_widget_advanced)) }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large)
                        .background(colors.surfaceContainer).padding(horizontal = 16.dp)
                ) {
                    SettingToggleRow(
                        label = stringResource(R.string.settings_widget_vert_punct_experimental),
                        subtitle = stringResource(R.string.settings_vert_punct_sub),
                        checked = vertPunct,
                        onCheckedChange = {
                            vertPunct = it
                            AppPrefs.setVertPunctReplace(context, it)
                            refreshWidgets()
                        }
                    )
                }
            }

            // 刷新所有小组件: 桌面已有已放置实例才出现(空桌面 = 无可刷, 按钮隐藏)
            item {
                if (items.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = {
                            uiScope.launch {
                                WidgetUpdater.notifyDataChanged(context)
                                snackbar.showSnackbar(widgetsRefreshedMessage)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight),
                        shape = SleepyTheme.Buttons.shape
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.mine_refresh_widgets))
                    }
                }
            }
        }
    }
}
