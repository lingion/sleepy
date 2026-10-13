package com.lingion.sleepy.ui.screen.mine

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Database state remains authoritative; one in-flight write per timetable. */
class TableReminderViewModel : ViewModel() {
    private val repository = SleepyApp.get().repository
    val tables = repository.observeAllTables()
    var pending by mutableStateOf(emptySet<Long>())
        private set

    fun setEnabled(id: Long, enabled: Boolean, onFailure: () -> Unit) {
        if (id in pending) return
        pending = pending + id
        viewModelScope.launch {
            try {
                repository.setTableReminderEnabled(id, enabled)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onFailure()
            } finally {
                pending = pending - id
            }
        }
    }
}

@Composable
fun TableReminderScreen(onBack: () -> Unit, vm: TableReminderViewModel = viewModel()) {
    val tables by vm.tables.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val appContext = context.applicationContext
    val colors = MaterialTheme.colorScheme
    val masterEnabled = remember { AppPrefs.isReminderEnabled(context) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.table_reminder_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(if (masterEnabled) R.string.table_reminder_subtitle else R.string.table_reminder_master_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
            if (tables?.isEmpty() == true) {
                item { Text(stringResource(R.string.table_reminder_empty), color = colors.onSurfaceVariant) }
            }
            items(tables.orEmpty(), key = { it.id }) { table ->
                val enabled = table.id !in vm.pending
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(SleepyTheme.shapes.large)
                        .background(colors.surfaceContainer)
                        .toggleable(
                            value = table.reminderEnabled, enabled = enabled, role = Role.Switch,
                            onValueChange = { checked ->
                                vm.setEnabled(table.id, checked) {
                                    Toast.makeText(appContext, R.string.table_reminder_save_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(table.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = table.reminderEnabled, onCheckedChange = null, enabled = enabled)
                }
            }
        }
    }
}
