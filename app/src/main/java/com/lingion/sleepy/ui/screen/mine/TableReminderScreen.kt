package com.lingion.sleepy.ui.screen.mine

import android.widget.Toast
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import com.lingion.sleepy.ui.component.liquidglass.GlassIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.lingion.sleepy.ui.component.liquidglass.GlassSwitch as Switch
import androidx.compose.material3.Text
import com.lingion.sleepy.ui.component.liquidglass.GlassTopAppBar as TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.lingion.sleepy.ui.component.SettingsLayout
import com.lingion.sleepy.ui.component.SettingsCard
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassHost
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Database state remains authoritative; one in-flight write per timetable. */
class TableReminderViewModel internal constructor(
    val tables: Flow<List<TimeTableEntity>>,
    private val writeEnabled: suspend (Long, Boolean) -> Unit,
) : ViewModel() {
    constructor() : this(SleepyApp.get().repository.observeReminderTables(),
        { id, enabled -> SleepyApp.get().repository.setTableReminderEnabled(id, enabled) })

    private data class PendingWrite(val target: Boolean, val returned: Boolean = false)
    private var writes by mutableStateOf(emptyMap<Long, PendingWrite>())
    val pending: Set<Long> get() = writes.keys

    fun setEnabled(id: Long, enabled: Boolean, onFailure: () -> Unit) {
        if (id in writes) return
        val request = PendingWrite(enabled)
        writes = writes + (id to request)
        viewModelScope.launch {
            try {
                writeEnabled(id, enabled)
                if (writes[id] === request) writes = writes + (id to request.copy(returned = true))
            } catch (cancelled: CancellationException) {
                if (writes[id] === request) writes = writes - id
                throw cancelled
            } catch (_: Exception) {
                if (writes[id] === request) writes = writes - id
                onFailure()
            }
        }
    }

    /** A write returning does not imply that this UI has consumed Room's echo. */
    fun acknowledgeObservedTables(observed: List<TimeTableEntity>?) {
        if (observed == null || writes.isEmpty()) return
        val byId = observed.associateBy { it.id }
        writes = writes.filter { (id, request) ->
            val table = byId[id]
            // Deletion also releases a pending row; late write completion
            // checks request identity and cannot resurrect that transaction.
            table != null && (!request.returned || table.reminderEnabled != request.target)
        }
    }
}

@Composable
fun TableReminderScreen(onBack: () -> Unit, vm: TableReminderViewModel = viewModel()) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val masterEnabled = remember { AppPrefs.isReminderEnabled(context) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_table_reminder_scope)) },
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
                TableReminderContent(masterEnabled = masterEnabled, vm = vm)
            }
        }
    }
}

/** The page keeps this card in its existing slot, including while reminders are disabled. */
@Composable
internal fun TableReminderCard(
    masterEnabled: Boolean,
    vm: TableReminderViewModel = viewModel(),
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SettingsCard(
        title = stringResource(R.string.settings_table_reminder_scope),
        expanded = expanded,
        onToggle = { expanded = !expanded },
    ) {
        TableReminderContent(masterEnabled = masterEnabled, vm = vm)
    }
}

/** Shared by the inline accordion and the retained legacy destination; no nested scrolling. */
@Composable
internal fun TableReminderContent(
    masterEnabled: Boolean,
    vm: TableReminderViewModel,
) {
    // Read both values in the composition that acknowledges the rendered list.
    // A write can finish after Room's echo without emitting another table list.
    val tables = vm.tables.collectAsStateWithLifecycle(initialValue = null).value
    val pending = vm.pending
    SideEffect { vm.acknowledgeObservedTables(tables) }
    val appContext = LocalContext.current.applicationContext
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SettingsLayout.cardGap),
    ) {
        Text(
            stringResource(if (masterEnabled) R.string.table_reminder_subtitle else R.string.table_reminder_master_off),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        if (tables?.isEmpty() == true) {
            Text(stringResource(R.string.table_reminder_empty), color = colors.onSurfaceVariant)
        }
        tables.orEmpty().forEach { table ->
            key(table.id) {
                val enabled = table.id !in pending
                val interactions = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(SleepyTheme.shapes.large)
                        .background(colors.surfaceContainer)
                        .toggleable(
                            value = table.reminderEnabled, enabled = enabled, role = Role.Switch,
                            interactionSource = interactions, indication = LocalIndication.current,
                            onValueChange = { checked ->
                                vm.setEnabled(table.id, checked) {
                                    Toast.makeText(appContext, R.string.table_reminder_save_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        .padding(SettingsLayout.cardInset),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(table.name, modifier = Modifier.weight(1f), style = SettingsLayout.headingStyle)
                    Switch(checked = table.reminderEnabled, onCheckedChange = null, enabled = enabled,
                        interactionSource = if (LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null) interactions else null)
                }
            }
        }
    }
}
