package com.lingion.sleepy.ui.screen.widget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.widget.*

/** All three choices remain in the same scrollable page, not only the selected image. */
@Composable
internal fun WidgetWideGridShapeScreen(
    settings: WeekGridWideSettings,
    previews: WideGridPreviews,
    onBack: () -> Unit,
    onModeChange: (WeekGridWideMode) -> Unit,
    onLabelSideChange: (WeekGridWideLabelSide) -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.widget_wide_grid_shapes)) }, navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("wide-shape-back")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val message = when (previews.status) {
                WideGridPreviewStatus.LOADING, WideGridPreviewStatus.STALE -> R.string.loading
                WideGridPreviewStatus.NO_TABLE -> R.string.widget_create_schedule
                WideGridPreviewStatus.EMPTY -> R.string.widget_wide_grid_empty
                WideGridPreviewStatus.UNAVAILABLE -> R.string.widget_wide_grid_unavailable
                WideGridPreviewStatus.ERROR -> R.string.widget_wide_grid_preview_error
                WideGridPreviewStatus.READY -> null
            }
            message?.let { Text(stringResource(it), Modifier.testTag("wide-preview-status"), style = MaterialTheme.typography.bodyMedium) }
            for (mode in WeekGridWidePreviewLoader.displayOrder) {
                val label = stringResource(when (mode) {
                    WeekGridWideMode.ORIGINAL -> R.string.widget_wide_grid_original
                    WeekGridWideMode.VERTICAL -> R.string.widget_wide_grid_vertical
                    WeekGridWideMode.HORIZONTAL -> R.string.widget_wide_grid_horizontal
                })
                val selected = settings.mode == mode
                Surface(color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().testTag("wide-shape-${mode.name}")
                        .selectable(selected, role = Role.RadioButton, onClick = { onModeChange(mode) }).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected, onClick = null)
                            Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
                        }
                        val frame = previews.frames.firstOrNull { it.mode == mode }
                        if (frame != null) {
                            Image(frame.bitmap.asImageBitmap(), label,
                                Modifier.fillMaxWidth().aspectRatio(300f / 160f).testTag("wide-preview-${mode.name}"),
                                contentScale = ContentScale.Fit)
                        } else {
                            Box(Modifier.fillMaxWidth().aspectRatio(300f / 160f), contentAlignment = Alignment.Center) {
                                if (previews.status == WideGridPreviewStatus.LOADING || previews.status == WideGridPreviewStatus.STALE) {
                                    CircularProgressIndicator()
                                } else Text(stringResource(message ?: R.string.widget_wide_grid_preview_error))
                            }
                        }
                    }
                }
            }
            if (settings.mode == WeekGridWideMode.HORIZONTAL) {
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    for (side in WeekGridWideLabelSide.entries) {
                        Row(Modifier.fillMaxWidth().testTag("wide-label-${side.name}")
                            .selectable(settings.labelSide == side, role = Role.RadioButton, onClick = { onLabelSideChange(side) })
                            .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(settings.labelSide == side, onClick = null)
                            Text(stringResource(if (side == WeekGridWideLabelSide.LEFT) R.string.widget_wide_grid_left else R.string.widget_wide_grid_right),
                                Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
