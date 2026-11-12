package com.lingion.sleepy.ui.screen.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.lingion.sleepy.ui.component.liquidglass.GlassIconButton as IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.lingion.sleepy.ui.component.liquidglass.GlassTopAppBar as TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lingion.sleepy.ui.component.SettingsLayout
import androidx.compose.foundation.layout.PaddingValues
import com.lingion.sleepy.R
import com.lingion.sleepy.widget.WidgetEditViewModel

/**
 * Per-widget edit screen — composed of [WidgetEditSection] rows so future
 * per-widget settings (e.g. "show week number", "dim past periods") can
 * be added as additional sections without changing this screen's shape.
 *
 * Currently two sections: [WidgetEditScheduleSection] (pick which schedule
 * this widget displays) and [WidgetEditAliasSection] (issue#26 — original
 * name vs alias). Toggling either writes
 * [com.lingion.sleepy.widget.WidgetBindingStore] / AppPrefs and triggers
 * [com.lingion.sleepy.widget.WidgetUpdater.notifyDataChanged] so all 9
 * widget receivers re-read their binding and redraw.
 */
@Composable
fun WidgetEditScreen(
    widgetId: Int,
    onBack: () -> Unit
) {
    val vm = remember(widgetId) { WidgetEditViewModel(widgetId) }
    val state by vm.state.collectAsState()
    val colors = MaterialTheme.colorScheme

    // To add a new section later, append here — the screen picks it up
    // automatically. Each section must implement WidgetEditSection.
    val sections: List<WidgetEditSection> = remember(state.receiverSimpleName) {
        buildList {
            add(WidgetEditScheduleSection)
            add(WidgetEditAliasSection)
            if (WidgetEditCompactWindowSection.appliesTo(state.receiverSimpleName)) add(WidgetEditCompactWindowSection)
            if (WidgetEditWideGridSection.appliesTo(state.receiverSimpleName)) add(WidgetEditWideGridSection)
            if (state.receiverSimpleName?.contains("WeekGrid") != true) add(WidgetEditScrollSection)
        }
    }
    val scope = remember(widgetId, vm, state.currentBinding, state.availableTables, state.useAlias,
        state.receiverSimpleName, state.scrollEnabled, state.compactTodayFirst, state.wideGridSettings) {
        WidgetEditScope(
            widgetId = widgetId,
            currentBinding = state.currentBinding,
            availableTables = state.availableTables,
            onSelectTable = { vm.setBinding(it) },
            useAlias = state.useAlias,
            onUseAliasChange = { vm.setUseAlias(it) },
            receiverSimpleName = state.receiverSimpleName,
            scrollEnabled = state.scrollEnabled,
            onScrollEnabledChange = { vm.setScrollEnabled(it) },
            compactTodayFirst = state.compactTodayFirst,
            onCompactTodayFirstChange = { vm.setCompactTodayFirst(it) },
            wideGridSettings = state.wideGridSettings,
            onWideGridLabelSideChange = { vm.setWideGridLabelSide(it) },
        )
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_edit_title)) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(SettingsLayout.pageInset),
                verticalArrangement = Arrangement.spacedBy(SettingsLayout.sectionGap)
            ) {
                items(sections.size) { idx ->
                    sections[idx].Content(scope)
                }
            }
        }
    }
}
