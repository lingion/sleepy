package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import kotlinx.coroutines.launch

/** A single-choice entry with independent Material and Liquid Glass presentations. */
@Composable
fun SingleChoiceDropdown(
    title: String,
    selectedId: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedLabel: String? = null,
    tagPrefix: String = "single_choice",
    onDismissed: (String?) -> Unit = {},
) {
    if (LocalUiStyle.current == UiStyle.LiquidGlass) {
        GlassChoiceMenu(
            title = title,
            selectedId = selectedId,
            options = options,
            onSelect = onSelect,
            modifier = modifier,
            enabled = enabled,
            selectedLabel = selectedLabel,
            tagPrefix = tagPrefix,
            onDismissed = onDismissed,
        )
        return
    }
    MaterialSingleChoiceDropdown(
        title, selectedId, options, onSelect, modifier, enabled, selectedLabel, tagPrefix, onDismissed,
    )
}

/** One accepted close owns both its result and its eventual popup-disposal callback. */
private class MaterialChoiceSession {
    var closing = false
    var pickedId: String? = null
    var popupComposed = false
    var completionQueued = false
}

@Composable
private fun MaterialSingleChoiceDropdown(
    title: String,
    selectedId: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    selectedLabel: String?,
    tagPrefix: String,
    onDismissed: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<MaterialChoiceSession?>(null) }
    val scope = rememberCoroutineScope()
    val select by rememberUpdatedState(onSelect)
    val dismissed by rememberUpdatedState(onDismissed)
    val currentOptions by rememberUpdatedState(options)
    val canOpen = enabled && options.isNotEmpty()
    val currentlyEnabled by rememberUpdatedState(canOpen)

    fun completeAfterDisposal(active: MaterialChoiceSession) {
        if (active.completionQueued) return
        active.completionQueued = true
        scope.launch {
            // Material retains its Popup during its own exit transition. This
            // starts only after that content is disposed, then lets removal draw.
            withFrameNanos { }
            if (session === active && active.closing) {
                session = null
                dismissed(active.pickedId)
            }
        }
    }

    fun close(pickedId: String?) {
        val active = session ?: return
        if (active.closing) return
        active.pickedId = pickedId
        active.closing = true
        expanded = false
        if (pickedId != null) select(pickedId)
    }

    fun open() {
        if (currentlyEnabled && session == null) {
            session = MaterialChoiceSession()
            expanded = true
        }
    }

    LaunchedEffect(canOpen) {
        if (!canOpen) close(null)
    }

    val label = selectedLabel ?: options.firstOrNull { it.first == selectedId }?.second ?: selectedId
    val buttonHeight = ButtonDefaults.MediumContainerHeight
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title.isNotEmpty()) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { shouldOpen ->
                if (shouldOpen) open() else close(null)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = { if (expanded) close(null) else open() },
                    shapes = ButtonDefaults.shapes(),
                    enabled = canOpen,
                    contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight, hasEndIcon = true),
                    modifier = Modifier.fillMaxWidth().heightIn(min = buttonHeight)
                        // Retain official focus and visible-window sizing without
                        // installing a second click recognizer over the button.
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = false)
                        .dbgTag("${tagPrefix}_card", text = label, role = "button", clickable = canOpen),
                ) {
                    Text(
                        label,
                        modifier = Modifier.weight(1f).dbgTag("${tagPrefix}_value", text = label, clickable = false),
                        style = ButtonDefaults.textStyleFor(buttonHeight),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(buttonHeight)))
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expanded,
                        modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
                    )
                }

                DropdownMenuPopup(
                    expanded = expanded,
                    onDismissRequest = { close(null) },
                    modifier = Modifier.exposedDropdownSize(matchAnchorWidth = true)
                        .dbgTag("${tagPrefix}_menu", clickable = false)
                        .semantics { paneTitle = title.ifEmpty { label } },
                ) {
                    val active = session
                    if (active != null) {
                        DisposableEffect(active) {
                            active.popupComposed = true
                            onDispose {
                                active.popupComposed = false
                                if (active.closing) completeAfterDisposal(active)
                            }
                        }
                    }
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShape(0, 1),
                        // The popup's official exposedDropdownSize supplies a
                        // finite visible-window height, including keyboard changes.
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    ) {
                        options.forEachIndexed { index, (id, text) ->
                            key(id) {
                                SelectableDropdownMenuItem(
                                    selected = id == (active?.pickedId ?: selectedId),
                                    onClick = {
                                        // Bind the action to the popup that created it; a
                                        // retained callback must never close a later session.
                                        if (active != null && session === active && !active.closing &&
                                            currentlyEnabled && currentOptions.any { it.first == id }
                                        ) close(id)
                                    },
                                    text = { Text(text) },
                                    shapes = MenuDefaults.itemShape(index, options.size),
                                    enabled = expanded && canOpen,
                                    modifier = Modifier.dbgTag("$tagPrefix#$id", text = text, role = "radio", clickable = expanded && canOpen),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    SideEffect {
        // An entry can become disabled before its first popup composition.
        // There is no popup to await in that case, but the same once-only
        // completion path still releases the session on the following frame.
        session?.takeIf { it.closing && !it.popupComposed }?.let(::completeAfterDisposal)
    }
}
