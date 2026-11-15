package com.lingion.sleepy.ui.nav

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.liquidglass.GlassIconButton
import com.lingion.sleepy.ui.component.liquidglass.GlassTopAppBar

/** The destination is already on the stack before any database work starts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewEditorDestination(
    requestId: String,
    @StringRes title: Int,
    creationFor: (restoredId: Long?) -> EditorCreation,
    onDiscard: (EditorCreation) -> Unit,
    onSaved: () -> Unit,
    content: @Composable (Long) -> Unit,
) {
    var createdId by rememberSaveable(requestId) { mutableStateOf<Long?>(null) }
    var saveDelivered by rememberSaveable(requestId) { mutableStateOf(false) }
    val creation = remember(requestId) { creationFor(createdId) }
    val state by creation.state.collectAsState()
    val contentId = when (val value = state) {
        is EditorCreationState.Ready -> value.id
        is EditorCreationState.Saved -> value.id
        else -> null
    }
    val savedId = (state as? EditorCreationState.Saved)?.id
    val active = LocalNavigationEntryActive.current
    val currentOnSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(creation) {
        // Give the new page its first frame; no fixed-duration navigation delay.
        withFrameNanos { }
        creation.start()
    }
    SideEffect { contentId?.let { createdId = it } }
    LaunchedEffect(savedId, active) {
        if (savedId != null && active && !saveDelivered) {
            saveDelivered = true
            currentOnSaved()
        }
    }
    BackHandler { onDiscard(creation) }
    if (contentId != null) {
        content(contentId)
    } else {
        EditorInitializingPage(title, onBack = { onDiscard(creation) }, failed = state is EditorCreationState.Failed)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorInitializingPage(@StringRes title: Int, onBack: () -> Unit, failed: Boolean = false) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GlassTopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    GlassIconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (failed) {
                Text(stringResource(R.string.error_unknown), color = MaterialTheme.colorScheme.error)
            } else {
                CircularProgressIndicator()
            }
        }
    }
}
