package com.lingion.sleepy.ui.screen.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R

object WidgetEditWideGridSection : WidgetEditSection {
    override val titleRes: Int = R.string.widget_wide_grid_layout
    fun appliesTo(receiverSimpleName: String?): Boolean = receiverSimpleName == "WeekGridWideWidgetProvider"

    @Composable override fun Content(scope: WidgetEditScope) {
        if (!appliesTo(scope.receiverSimpleName)) return
        Column {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
            Surface(onClick = scope.onOpenWideGridShapes, modifier = Modifier.fillMaxWidth().testTag("wide-shape-entry"),
                color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                Text(stringResource(R.string.widget_wide_grid_shapes), Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
