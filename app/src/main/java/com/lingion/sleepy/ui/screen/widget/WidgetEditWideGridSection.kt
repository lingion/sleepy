package com.lingion.sleepy.ui.screen.widget

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lingion.sleepy.R
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.widget.WeekGridWideLabelSide
import com.lingion.sleepy.widget.WeekGridWideMode
import com.lingion.sleepy.widget.WeekGridWidePreviewLoader
import com.lingion.sleepy.widget.WeekGridWideSettings
import com.lingion.sleepy.widget.WideGridPreviews
import com.lingion.sleepy.widget.WideGridPreviewStatus

object WidgetEditWideGridSection : WidgetEditSection {
    override val titleRes: Int = R.string.widget_wide_grid_layout
    fun appliesTo(receiverSimpleName: String?): Boolean = receiverSimpleName == "WeekGridWideWidgetProvider"

    @Composable override fun Content(scope: WidgetEditScope) {
        if (!appliesTo(scope.receiverSimpleName)) return
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val theme by remember(context) { AppPrefs.themeKeyFlow(context) }
            .collectAsState(initial = AppPrefs.getThemeKey(context))
        var previews by remember(scope.widgetId, scope.currentBinding, scope.wideGridSettings, scope.useAlias, theme, configuration) {
            mutableStateOf(WideGridPreviews(WideGridPreviewStatus.LOADING))
        }
        LaunchedEffect(scope.widgetId, scope.currentBinding, scope.wideGridSettings, scope.useAlias, theme, configuration, lifecycleOwner) {
            val renderContext = context.createConfigurationContext(Configuration(configuration))
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WeekGridWidePreviewLoader.observe(renderContext, scope.widgetId).collect { previews = it }
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.padding(top = 8.dp))
            WideGridShapeOptions(
                settings = scope.wideGridSettings,
                previews = previews,
                onModeChange = scope.onWideGridModeChange,
                onLabelSideChange = scope.onWideGridLabelSideChange,
            )
        }
    }
}

@Composable
internal fun WideGridShapeOptions(
    settings: WeekGridWideSettings,
    previews: WideGridPreviews,
    onModeChange: (WeekGridWideMode) -> Unit,
    onLabelSideChange: (WeekGridWideLabelSide) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val message = when (previews.status) {
            WideGridPreviewStatus.LOADING, WideGridPreviewStatus.STALE -> R.string.loading
            WideGridPreviewStatus.NO_TABLE -> R.string.widget_create_schedule
            WideGridPreviewStatus.EMPTY -> R.string.widget_wide_grid_empty
            WideGridPreviewStatus.UNAVAILABLE -> R.string.widget_wide_grid_unavailable
            WideGridPreviewStatus.ERROR -> R.string.widget_wide_grid_preview_error
            WideGridPreviewStatus.READY -> null
        }
        message?.let {
            Text(
                text = stringResource(it),
                modifier = Modifier.testTag("wide-preview-status"),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        for (mode in WeekGridWidePreviewLoader.displayOrder) {
            val label = stringResource(when (mode) {
                WeekGridWideMode.ORIGINAL -> R.string.widget_wide_grid_original
                WeekGridWideMode.VERTICAL -> R.string.widget_wide_grid_vertical
                WeekGridWideMode.HORIZONTAL -> R.string.widget_wide_grid_horizontal
            })
            val selected = settings.mode == mode
            Surface(
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wide-shape-${mode.name}")
                    .selectable(selected, role = Role.RadioButton, onClick = { onModeChange(mode) })
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, onClick = null)
                        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
                    }
                    val frame = previews.frames.firstOrNull { it.mode == mode }
                    if (frame != null) {
                        Image(
                            bitmap = frame.bitmap.asImageBitmap(),
                            contentDescription = label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(300f / 160f)
                                .testTag("wide-preview-${mode.name}"),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(300f / 160f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (previews.status == WideGridPreviewStatus.LOADING || previews.status == WideGridPreviewStatus.STALE) {
                                CircularProgressIndicator()
                            } else {
                                Text(stringResource(message ?: R.string.widget_wide_grid_preview_error))
                            }
                        }
                    }
                }
            }
        }
        if (settings.mode == WeekGridWideMode.HORIZONTAL) {
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                for (side in WeekGridWideLabelSide.entries) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wide-label-${side.name}")
                            .selectable(settings.labelSide == side, role = Role.RadioButton, onClick = { onLabelSideChange(side) })
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(settings.labelSide == side, onClick = null)
                        Text(
                            text = stringResource(if (side == WeekGridWideLabelSide.LEFT) R.string.widget_wide_grid_left else R.string.widget_wide_grid_right),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
