package com.lingion.sleepy.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Settings-only tokens; previews and the app's global typography keep their own styles. */
object SettingsLayout {
    val pageInset = 16.dp
    val cardInset = 16.dp
    val cardGap = 12.dp
    val sectionGap = 24.dp
    val headerGap = 8.dp
    val descriptionGap = 4.dp
    val controlGap = 12.dp
    val minRowHeight = 48.dp

    val headingStyle @Composable get() = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    val itemStyle @Composable get() = MaterialTheme.typography.bodyMedium
    val descriptionStyle @Composable get() = MaterialTheme.typography.bodySmall
}

/** The parent supplies cardGap; this adds only the remaining section gap. */
@Composable
fun SettingsSection(
    title: String,
    first: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = if (first) 0.dp else SettingsLayout.sectionGap - SettingsLayout.cardGap),
        verticalArrangement = Arrangement.spacedBy(SettingsLayout.headerGap),
    ) {
        SectionHeader(title)
        content()
    }
}

fun LazyListScope.settingsSection(
    @StringRes titleRes: Int,
    first: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    item { SettingsSection(stringResource(titleRes), first, content) }
}
