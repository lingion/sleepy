package com.lingion.sleepy.ui.screen.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme

/** UI-only representation of an import draft; persistence belongs to the caller. */
data class ImportDraft(
    val id: String,
    val name: String,
    val details: String,
    val savedAt: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDraftSheet(
    drafts: List<ImportDraft>,
    onDismiss: () -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.import_drafts),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (drafts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.import_drafts_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(drafts, key = { it.id }) { draft ->
                        ImportDraftRow(
                            draft = draft,
                            onRestore = { onRestore(draft.id) },
                            onDelete = { onDelete(draft.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportDraftRow(
    draft: ImportDraft,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            .background(colors.surfaceContainer)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = draft.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.onSurface
            )
            Text(
                text = draft.details,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            draft.savedAt?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = stringResource(R.string.import_draft_saved_at, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        IconButton(onClick = onRestore) {
            Icon(
                imageVector = Icons.Outlined.Restore,
                contentDescription = stringResource(R.string.import_draft_restore),
                tint = colors.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.import_draft_delete),
                tint = colors.error,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
