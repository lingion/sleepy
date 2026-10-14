package com.lingion.sleepy.ui.screen.today

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.widget.notification.DailyAgenda
import com.lingion.sleepy.widget.notification.loadDailyAgenda
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Notification destination for every reminder-enabled table on the requested calendar date. */
class DailyAgendaActivity : ComponentActivity() {
    companion object {
        fun pendingIntent(context: Context, date: LocalDate): PendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, DailyAgendaActivity::class.java).apply {
                data = Uri.parse(DailyAgendaRoute.identity(date))
                putExtra(DailyAgendaRoute.EXTRA_DATE, date.toString())
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val date = DailyAgendaRoute.parse(intent.getStringExtra(DailyAgendaRoute.EXTRA_DATE))
        if (date == null) { finish(); return }
        enableEdgeToEdge()
        var agenda by mutableStateOf<DailyAgenda?>(null)
        var failed by mutableStateOf(false)
        fun refresh() = lifecycleScope.launch {
            failed = false
            try {
                agenda = withContext(Dispatchers.IO) { loadDailyAgenda(this@DailyAgendaActivity, date).agenda }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failed = true
                android.util.Log.e("DailyAgenda", "Unable to load agenda", error)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { refresh().join() }
        }
        setContent {
            val dark = AppPrefs.isDarkMode(this, isSystemInDarkTheme())
            SleepyThemeProvider(darkTheme = dark, themeKey = AppPrefs.getThemeKey(this)) {
                DailyAgendaScreen(date, agenda, failed, { refresh() }, { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyAgendaScreen(date: LocalDate, agenda: DailyAgenda?, failed: Boolean, retry: () -> Unit, back: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.agenda_title)) }, navigationIcon = {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        })
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), style = MaterialTheme.typography.titleMedium) }
            when {
                failed -> item {
                    Text(stringResource(R.string.agenda_load_error))
                    TextButton(onClick = retry) { Text(stringResource(R.string.holiday_data_retry)) }
                }
                agenda == null -> item { Text(stringResource(R.string.loading)) }
                agenda.items.isEmpty() -> item { Text(stringResource(R.string.agenda_empty)) }
                else -> {
                    item { Text(stringResource(R.string.agenda_count, agenda.items.size, agenda.tableCount)) }
                    items(agenda.items) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.timeText(stringResource(R.string.agenda_time_unknown)), style = MaterialTheme.typography.labelLarge)
                                Text(item.first.course.courseName, style = MaterialTheme.typography.titleMedium)
                                Text(item.first.course.room.ifBlank { stringResource(R.string.notif_room_unknown) })
                                Text(item.sourceNames(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp)) }
        }
    }
}
