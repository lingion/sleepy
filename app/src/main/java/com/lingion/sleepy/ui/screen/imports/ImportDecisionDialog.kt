package com.lingion.sleepy.ui.screen.imports

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.imports.ImportConfiguration
import com.lingion.sleepy.data.imports.ImportPlan
import com.lingion.sleepy.data.imports.ImportSnapshot
import com.lingion.sleepy.data.imports.planImport
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.data.repository.ImportApplyResult
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.ui.theme.SleepyTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

private data class PreparedImportDecision(
    val serializedConfiguration: String,
    val snapshotVersion: Long,
    val source: ScheduleParser.ParseResult,
    val plan: ImportPlan
)

/** No writes occur until the single final action. The original source is never rewritten by drafts. */
@Composable
fun ImportDecisionDialog(
    source: ScheduleParser.ParseResult,
    initialTargetId: Long,
    onDismiss: () -> Unit,
    onImported: (Long) -> Unit,
    initialConfiguration: ImportConfiguration? = null,
    onConfigurationChange: (ImportConfiguration) -> Unit = {},
    onApplyingChange: (Boolean) -> Unit = {},
    repository: ScheduleRepository = SleepyApp.get().repository,
    importDraftId: String? = null
) {
    val context = LocalContext.current
    val defaultTableName = stringResource(R.string.id_default_table)
    val defaultPeriodName = stringResource(R.string.id_default_period)
    val loadFailure = stringResource(R.string.id_load_failed)
    val planFailure = stringResource(R.string.id_plan_failed)
    val applyFailure = stringResource(R.string.id_apply_failed)
    val staleMessage = stringResource(R.string.id_stale)
    val invalidMessage = stringResource(R.string.id_invalid)
    val noChangesMessage = stringResource(R.string.id_no_changes)
    val committedMessage = stringResource(R.string.id_applied)
    val cleanupWarning = stringResource(R.string.id_cleanup_warning)
    val importedCallback by rememberUpdatedState(onImported)
    val configurationCallback by rememberUpdatedState(onConfigurationChange)
    val applyingCallback by rememberUpdatedState(onApplyingChange)
    val dismissCallback by rememberUpdatedState(onDismiss)
    var savedConfiguration by rememberSaveable(source, initialTargetId) {
        mutableStateOf(initialConfiguration?.let(::encodeImportDecisionConfiguration).orEmpty())
    }
    val configuration = remember(savedConfiguration) { decodeImportDecisionConfiguration(savedConfiguration) }
    var snapshot by remember(source, repository) { mutableStateOf<ImportSnapshot?>(null) }
    var snapshotVersion by remember { mutableLongStateOf(0L) }
    var reloadRequest by remember { mutableIntStateOf(0) }
    var prepared by remember(source, repository) { mutableStateOf<PreparedImportDecision?>(null) }
    var loading by remember { mutableStateOf(true) }
    var planning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf(false) }
    val gate = remember(source) { ImportDecisionSubmitGate() }
    val submissionKey = rememberSaveable(source, initialTargetId) { java.util.UUID.randomUUID().toString() }
    val submissionModel: ImportSubmissionViewModel = viewModel(key = "import-$submissionKey")
    val submissionState by submissionModel.state.collectAsState()
    val isApplying = submissionState.applying
    LaunchedEffect(submissionState.applying) {
        applyingCallback(submissionState.applying)
    }
    LaunchedEffect(submissionState.result) {
        val result = submissionState.result ?: return@LaunchedEffect
        when (result) {
            is ImportApplyResult.Applied -> {
                gate.finish(committed = true)
                message = committedMessage
                if (result.warnings.isNotEmpty()) {
                    Toast.makeText(context, result.warnings.joinToString("\n"), Toast.LENGTH_LONG).show()
                }
                try { importedCallback(result.tableId) } catch (error: Exception) {
                    Log.e("Sleepy", "Import committed but completion callback failed", error)
                    message = cleanupWarning
                }
            }
            ImportApplyResult.StalePreview, ImportApplyResult.Invalid -> {
                gate.finish(committed = false)
                prepared = null
                snapshot = null
                message = if (result == ImportApplyResult.StalePreview) staleMessage else invalidMessage
                reloadRequest++
                submissionModel.consumeRetryableResult()
            }
            ImportApplyResult.NoChanges -> {
                gate.finish(committed = false)
                message = noChangesMessage
                submissionModel.consumeRetryableResult()
            }
            is ImportApplyResult.Failed -> {
                gate.finish(committed = false)
                message = if (result.message.isBlank()) applyFailure else "$applyFailure\n${result.message}"
                submissionModel.consumeRetryableResult()
            }
        }
    }

    val changeConfiguration: (ImportConfiguration) -> Unit = { updated ->
        if (!submissionModel.state.value.applying && !gate.applying && !gate.committed) {
            val encoded = encodeImportDecisionConfiguration(updated)
            if (savedConfiguration != encoded) {
                savedConfiguration = encoded
                prepared = null
                message = null
                try { configurationCallback(updated) } catch (error: Exception) {
                    Log.w("Sleepy", "Import draft callback failed", error)
                }
            }
        }
    }

    LaunchedEffect(repository, reloadRequest, source) {
        loading = true
        loadError = false
        prepared = null
        snapshot = null
        try {
            val loaded = withContext(Dispatchers.IO) { repository.loadImportSnapshot() }
            coroutineContext.ensureActive()
            snapshotVersion++
            snapshot = loaded
            if (decodeImportDecisionConfiguration(savedConfiguration) == null) {
                val seeded = createImportDecisionConfiguration(source, loaded, initialTargetId, defaultTableName, defaultPeriodName)
                savedConfiguration = encodeImportDecisionConfiguration(seeded)
                try { configurationCallback(seeded) } catch (error: Exception) {
                    Log.w("Sleepy", "Import draft callback failed", error)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            loadError = true
            message = loadFailure
            Log.e("Sleepy", "Import snapshot loading failed", error)
        } finally {
            loading = false
        }
    }

    // Both keys and the final identity check prevent a cancelled, older calculation being submitted.
    LaunchedEffect(source, savedConfiguration, snapshotVersion, snapshot) {
        prepared = null
        val currentSnapshot = snapshot ?: return@LaunchedEffect
        val currentConfiguration = decodeImportDecisionConfiguration(savedConfiguration) ?: return@LaunchedEffect
        val requestedConfiguration = savedConfiguration
        val requestedVersion = snapshotVersion
        planning = true
        try {
            val plan = withContext(Dispatchers.Default) { planImport(source, currentSnapshot, currentConfiguration) }
            coroutineContext.ensureActive()
            if (savedConfiguration == requestedConfiguration && snapshotVersion == requestedVersion && snapshot === currentSnapshot) {
                prepared = PreparedImportDecision(requestedConfiguration, requestedVersion, source, plan)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            message = planFailure
            Log.e("Sleepy", "Import planning failed", error)
        } finally {
            planning = false
        }
    }

    val currentPlan = prepared?.takeIf {
        it.serializedConfiguration == savedConfiguration && it.snapshotVersion == snapshotVersion && it.source == source
    }?.plan
    val submit: () -> Unit = submit@{
        val selected = prepared
        val current = selected != null && selected.serializedConfiguration == savedConfiguration &&
            selected.snapshotVersion == snapshotVersion && selected.source == source && selected.plan.canSubmit &&
            !loading && !planning
        if (submissionModel.state.value.applying || !gate.begin(current)) return@submit
        message = null
        try { applyingCallback(true) } catch (error: Exception) { Log.w("Sleepy", "Import applying callback failed", error) }
        submissionModel.submit(repository, selected!!.plan, importDraftId)
    }

    Dialog(onDismissRequest = { if (!submissionModel.state.value.applying && !gate.applying) dismissCallback() },
        properties = DialogProperties(dismissOnBackPress = !isApplying, dismissOnClickOutside = !isApplying,
            usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(12.dp)) {
            Surface(Modifier.fillMaxWidth().widthIn(max = 760.dp).heightIn(max = maxHeight),
                shape = SleepyTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.id_title), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.id_intro), style = MaterialTheme.typography.bodyMedium)
                    message?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (isApplying) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.id_applying))
                    } else if (loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.id_loading))
                    }
                    if (loadError) ImportDecisionSecondaryButton(stringResource(R.string.id_retry), { reloadRequest++ })
                    if (configuration != null && snapshot != null && !isApplying && !gate.committed) {
                        val loaded = snapshot!!
                        ImportDecisionSourceSection(source)
                        ImportDecisionDestinationSection(source, loaded, configuration, changeConfiguration)
                        ImportDecisionPolicySection(configuration, changeConfiguration)
                        ImportDecisionMetadataSection(source, loaded, configuration, currentPlan, changeConfiguration)
                        ImportDecisionScheduleSection(source, loaded, configuration, currentPlan, changeConfiguration)
                        if (planning) Text(stringResource(R.string.id_planning))
                        currentPlan?.let { plan ->
                            ImportDecisionPerItemSection(plan, changeConfiguration)
                            ImportDecisionPreviewSection(plan)
                        }
                    }
                    if (!gate.committed) Button(submit,
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("import-final-submit"),
                        enabled = currentPlan?.canSubmit == true && !loading && !planning && !isApplying,
                        shape = SleepyTheme.Buttons.shape) {
                        if (configuration == null) Text(stringResource(R.string.id_submit))
                        else Text(stringResource(when {
                            configuration.destination == com.lingion.sleepy.data.imports.ImportDestination.New && configuration.content == com.lingion.sleepy.data.imports.ImportContent.ImportOnly -> R.string.id_submit_new_import
                            configuration.destination == com.lingion.sleepy.data.imports.ImportDestination.New -> R.string.id_submit_new_merge
                            configuration.content == com.lingion.sleepy.data.imports.ImportContent.ImportOnly -> R.string.id_submit_existing_replace
                            currentPlan?.replacements?.isNotEmpty() == true -> R.string.id_submit_existing_merge_replace
                            else -> R.string.id_submit_existing
                        }, currentPlan?.finalTable?.name ?: configuration.name))
                    }
                    ImportDecisionSecondaryButton(stringResource(if (gate.committed) R.string.id_close else R.string.id_return),
                        { if (!submissionModel.state.value.applying && !gate.applying) dismissCallback() }, enabled = !isApplying,
                        modifier = Modifier.testTag("import-return"))
                    if (!gate.committed) Text(stringResource(R.string.id_return_help), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
