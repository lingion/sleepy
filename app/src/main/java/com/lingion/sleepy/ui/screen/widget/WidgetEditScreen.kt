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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.widget.WidgetEditViewModel

/**
 * Per-widget edit screen — composed of [WidgetEditSection] rows so future
 * per-widget settings (e.g. "show week number", "dim past periods") can
 * be added as additional sections without changing this screen's shape.
 *
 * Currently one section, [WidgetEditScheduleSection] — pick which schedule
 * this widget displays. Toggling the binding writes
 * [com.lingion.sleepy.widget.WidgetBindingStore] and triggers
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
    val colors = SleepyTheme.colors

    // To add a new section later, append here — the screen picks it up
    // automatically. Each section must implement WidgetEditSection.
    val sections: List<WidgetEditSection> = remember { listOf(WidgetEditScheduleSection) }
    val scope = remember(state.currentBinding, state.availableTables) {
        WidgetEditScope(
            widgetId = widgetId,
            currentBinding = state.currentBinding,
            availableTables = state.availableTables,
            onSelectTable = { vm.setBinding(it) }
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
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(sections.size) { idx ->
                    sections[idx].Content(scope)
                }
            }
        }
    }
}
